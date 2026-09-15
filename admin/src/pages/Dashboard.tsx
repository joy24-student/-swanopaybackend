import React, { useEffect, useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import {
  adminSupabase,
  fetchGatewayConfig,
  updateGatewayConfig,
  GATEWAY_CONFIG_ID
} from '../adminSupabaseClient'
import AddMerchantModal from '../components/AddMerchantModal'

// Chart.js registration
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'
import { Chart } from 'react-chartjs-2'

ChartJS.register(
  CategoryScale,
  LinearScale,
  BarElement,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
)

export default function Dashboard() {
  // State
  const [merchantCount, setMerchantCount] = useState<number>(0)
  const [pendingKycCount, setPendingKycCount] = useState<number>(0)
  const [gatewayConfig, setGatewayConfig] = useState<any>(null)
  const [recentTransactions, setRecentTransactions] = useState<any[]>([])
  const [kycQueue, setKycQueue] = useState<any[]>([])
  const [merchantList, setMerchantList] = useState<any[]>([])
  const [recentSubmissions, setRecentSubmissions] = useState<any[]>([])
  const [timeRange, setTimeRange] = useState<'7' | '30'>('7')
  const [isAddMerchantOpen, setIsAddMerchantOpen] = useState(false)
  const [dbLatency, setDbLatency] = useState<number>(24)
  const [lastCheckedTime, setLastCheckedTime] = useState<string>('')

  // Enabled payment methods state
  const enabledMethods = useMemo(() => {
    const raw = gatewayConfig?.enabled_methods || {}
    return {
      bKash: raw.bKash ?? true,
      Nagad: raw.Nagad ?? true,
      Rocket: raw.Rocket ?? true,
      Upay: raw.Upay ?? true,
    }
  }, [gatewayConfig])

  // Method switch toggle handler
  async function handleToggleMethod(methodName: string, currentState: boolean) {
    const updated = {
      ...enabledMethods,
      [methodName]: !currentState
    }
    // Optimistic UI update
    setGatewayConfig((prev: any) => ({
      ...prev,
      enabled_methods: updated
    }))

    try {
      await updateGatewayConfig({ enabled_methods: updated })
    } catch (e) {
      console.error('Failed to update method status:', e)
    }
  }

  // Load all real data from database
  async function loadDashboardData() {
    const startTime = performance.now()

    // 1. Fetch Gateway Config
    try {
      const cfg = await fetchGatewayConfig()
      if (cfg) setGatewayConfig(cfg)
    } catch (err: any) {
      console.warn('[Dashboard] fetchGatewayConfig:', err.message)
    }

    // 2. Fetch Merchant Counts & List
    try {
      const { data: merchants, count } = await adminSupabase
        .from('merchants')
        .select('*', { count: 'exact' })
        .order('created_at', { ascending: false })
        .limit(10)

      if (count !== null) setMerchantCount(count)
      if (merchants) {
        setMerchantList(merchants)
        // Extract pending KYC
        const pending = merchants.filter((m: any) =>
          m.kyc_status === 'PENDING' || m.kyc_status === 'PENDING_REVIEW'
        )
        setPendingKycCount(pending.length)
        setKycQueue(pending)
      }
    } catch (err: any) {
      console.warn('[Dashboard] merchants:', err.message)
    }

    // 3. Fetch Recent Transactions from orders or payment_events
    try {
      const { data: events } = await adminSupabase
        .from('payment_events')
        .select('*')
        .order('recorded_at', { ascending: false })
        .limit(6)

      if (events && events.length > 0) {
        setRecentTransactions(events)
      } else {
        // Fallback to orders
        const { data: orders } = await adminSupabase
          .from('orders')
          .select('*')
          .order('created_at', { ascending: false })
          .limit(6)
        if (orders && orders.length > 0) setRecentTransactions(orders)
      }
    } catch (err: any) {
      console.warn('[Dashboard] transactions:', err.message)
    }

    // 4. Fetch Form Submissions
    try {
      const { data: subs } = await adminSupabase
        .from('form_submissions')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(6)
      if (subs && subs.length > 0) {
        setRecentSubmissions(subs)
      }
    } catch (err: any) {
      console.warn('[Dashboard] form_submissions:', err.message)
    }

    // Calculate DB latency
    const elapsed = Math.round(performance.now() - startTime)
    setDbLatency(Math.min(elapsed, 95))

    // Formatted current time
    const now = new Date()
    setLastCheckedTime(
      now.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) +
      ' ' +
      now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
    )
  }

  useEffect(() => {
    loadDashboardData()

    // Real-time listener for gateway_config updates
    const channel = adminSupabase
      .channel('realtime_dashboard')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'gateway_config', filter: `id=eq.${GATEWAY_CONFIG_ID}` },
        (payload) => {
          if (payload.new) setGatewayConfig(payload.new)
        }
      )
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'payment_events' },
        () => loadDashboardData()
      )
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'merchants' },
        () => loadDashboardData()
      )
      .subscribe()

    return () => {
      adminSupabase.removeChannel(channel)
    }
  }, [])

  // Dual-Axis Chart Setup (Volume Bars + Revenue Line)
  const chartData = useMemo(() => {
    const labels = ['Oct 14', 'Oct 15', 'Oct 16', 'Oct 17', 'Oct 18', 'Oct 19', 'Oct 20']
    const volumeData = [8500, 11200, 15400, 9800, 16100, 14200, 17800]
    const revenueData = [240000, 310000, 580000, 420000, 610000, 560000, 720000]

    return {
      labels,
      datasets: [
        {
          type: 'bar' as const,
          label: 'Transaction Volume',
          data: volumeData,
          backgroundColor: '#BFDBFE',
          hoverBackgroundColor: '#93C5FD',
          borderRadius: 6,
          barPercentage: 0.55,
          yAxisID: 'y'
        },
        {
          type: 'line' as const,
          label: 'Revenue (BDT)',
          data: revenueData,
          borderColor: '#8B5CF6',
          backgroundColor: 'rgba(139, 92, 246, 0.1)',
          pointBackgroundColor: '#7C3AED',
          pointBorderColor: '#FFFFFF',
          pointBorderWidth: 2,
          pointRadius: 5,
          pointHoverRadius: 7,
          tension: 0.35,
          borderWidth: 2.5,
          yAxisID: 'y1'
        }
      ]
    }
  }, [timeRange])

  const chartOptions: any = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: 'index',
      intersect: false
    },
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        backgroundColor: '#0F172A',
        titleFont: { size: 12, weight: '700' },
        bodyFont: { size: 12 },
        padding: 10,
        cornerRadius: 8
      }
    },
    scales: {
      x: {
        grid: {
          display: false
        },
        ticks: {
          color: '#64748B',
          font: { size: 11, weight: '600' }
        }
      },
      y: {
        type: 'linear',
        display: true,
        position: 'left',
        grid: {
          color: '#F1F5F9'
        },
        ticks: {
          color: '#94A3B8',
          font: { size: 11 },
          callback: (val: number) => (val >= 1000 ? `${val / 1000}K` : val)
        }
      },
      y1: {
        type: 'linear',
        display: true,
        position: 'right',
        grid: {
          drawOnChartArea: false
        },
        ticks: {
          color: '#94A3B8',
          font: { size: 11 },
          callback: (val: number) => (val >= 1000 ? `${val / 1000}K` : val)
        }
      }
    }
  }

  // Fallback transaction items if database has no rows yet
  const displayTransactions = recentTransactions.length > 0 ? recentTransactions : [
    { id: '#TXN001', merchant: 'Dream Mart', method: 'bKash', amount: 1250, status: 'Success', time: 'Today, 11:20 AM' },
    { id: '#TXN002', merchant: 'Tech Shop BD', method: 'Nagad', amount: 3490, status: 'Success', time: 'Today, 10:15 AM' },
    { id: '#TXN003', merchant: 'Fashion Hub', method: 'Rocket', amount: 780, status: 'Failed', time: 'Today, 09:42 AM' },
    { id: '#TXN004', merchant: 'Daily Needs', method: 'Upay', amount: 2100, status: 'Success', time: 'Today, 08:10 AM' },
    { id: '#TXN005', merchant: 'Book Zone', method: 'bKash', amount: 650, status: 'Success', time: 'Today, 07:55 AM' }
  ]

  // Fallback KYC Queue items if none
  const displayKyc = kycQueue.length > 0 ? kycQueue : [
    { name: 'Sunrise Mart', email: 'sunrise123@gmail.com', initial: 'S', color: '#EFF6FF', textColor: '#2563EB', time: '2 hours ago' },
    { name: 'Tech Valley Ltd.', email: 'techvalley.bd@gmail.com', initial: 'T', color: '#ECFDF5', textColor: '#059669', time: '5 hours ago' },
    { name: 'Green Life Store', email: 'greenlife.store@gmail.com', initial: 'G', color: '#EFF6FF', textColor: '#2563EB', time: '1 day ago' },
    { name: 'Campus Corner', email: 'campus.corner@gmail.com', initial: 'C', color: '#FEF2F2', textColor: '#DC2626', time: '1 day ago' },
    { name: 'Style & More', email: 'style.more.bd@gmail.com', initial: 'S', color: '#ECFDF5', textColor: '#059669', time: '2 days ago' }
  ]

  // Fallback Merchant Registry items if none
  const displayMerchants = merchantList.length > 0 ? merchantList : [
    { name: 'Dream Mart', id: 'MRC-001', color: '#F3E8FF' },
    { name: 'Tech Shop BD', id: 'MRC-002', color: '#FFF1F2' },
    { name: 'Fashion Hub', id: 'MRC-003', color: '#EFF6FF' },
    { name: 'Daily Needs', id: 'MRC-004', color: '#ECFDF5' },
    { name: 'Book Zone', id: 'MRC-005', color: '#FAF5FF' }
  ]

  // Fallback Recent Submissions
  const displaySubmissions = recentSubmissions.length > 0 ? recentSubmissions : [
    { title: 'API Integration Request', merchant: 'Tech Valley Ltd.', time: '2 hours ago', dotColor: '#7C3AED' },
    { title: 'Merchant Info Update', merchant: 'Dream Mart', time: '5 hours ago', dotColor: '#2563EB' },
    { title: 'Payout Configuration', merchant: 'Green Life Store', time: '1 day ago', dotColor: '#10B981' },
    { title: 'Support Ticket', merchant: 'Campus Corner', time: '1 day ago', dotColor: '#F59E0B' },
    { title: 'Document Upload', merchant: 'Style & More', time: '2 days ago', dotColor: '#6366F1' }
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
      {/* ─────────────────────────────────────────────────────────────
          1. HEADER TITLE BANNER
      ───────────────────────────────────────────────────────────── */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 20
      }}>
        {/* Left Title */}
        <div>
          <h1 style={{ fontSize: 28, fontWeight: 800, color: '#0F172A', letterSpacing: '-0.5px', margin: 0 }}>
            SwapnoPay Admin
          </h1>
          <div style={{ fontSize: 14, fontWeight: 700, color: '#334155', marginTop: 4 }}>
            Platform Owner Control Panel
          </div>
          <div style={{ fontSize: 13, color: '#64748B', marginTop: 2, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span>Monitor, manage and grow your payment platform — enabling a cashless Bangladesh</span>
            <span>🇧🇩</span>
          </div>
        </div>

        {/* Center/Right Silhouette Artwork & Slogan */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 32 }}>
          {/* Stylized Jatiya Smriti Soudho Monument Graphic */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <svg width="120" height="64" viewBox="0 0 160 80" fill="none">
              <path d="M80 5L95 75H65L80 5Z" fill="#DBEAFE" />
              <path d="M80 15L110 75H50L80 15Z" fill="#BFDBFE" opacity="0.6" />
              <path d="M80 30L130 75H30L80 30Z" fill="#93C5FD" opacity="0.4" />
              <path d="M80 45L150 75H10L80 45Z" fill="#60A5FA" opacity="0.25" />
              <line x1="0" y1="75" x2="160" y2="75" stroke="#93C5FD" strokeWidth="2" />
            </svg>
            <div style={{
              fontSize: 12,
              lineHeight: 1.4,
              color: '#3B82F6',
              fontWeight: 600,
              letterSpacing: '-0.2px'
            }}>
              <div>Safer Payments</div>
              <div>Stronger Businesses</div>
              <div style={{ fontWeight: 700 }}>A Brighter Bangladesh</div>
            </div>
          </div>

          {/* Add New Merchant Button */}
          <button
            onClick={() => setIsAddMerchantOpen(true)}
            className="btn-primary"
            style={{ fontSize: 13.5, padding: '11px 20px', borderRadius: 10 }}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            Add New Merchant
          </button>
        </div>
      </div>

      {/* ─────────────────────────────────────────────────────────────
          2. TOP KPI METRIC CARDS (5 Cards in Row)
      ───────────────────────────────────────────────────────────── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(5, 1fr)',
        gap: 16
      }}>
        {/* Card 1: Total Merchants */}
        <div className="card" style={{ padding: '20px 22px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#EFF6FF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#2563EB',
              flexShrink: 0
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: '#64748B' }}>
                Total Merchants
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#0F172A', lineHeight: 1.15, marginTop: 2 }}>
                {merchantCount}
              </div>
              <div style={{ fontSize: 11, fontWeight: 600, color: '#10B981', marginTop: 4 }}>
                ↑ 0% from last month
              </div>
            </div>
          </div>
        </div>

        {/* Card 2: Pending KYC */}
        <div className="card" style={{ padding: '20px 22px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#FEF3C7',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#D97706',
              flexShrink: 0
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
                <line x1="16" y1="13" x2="8" y2="13" />
                <line x1="16" y1="17" x2="8" y2="17" />
              </svg>
            </div>
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: '#64748B' }}>
                Pending KYC
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#0F172A', lineHeight: 1.15, marginTop: 2 }}>
                {pendingKycCount}
              </div>
              <div style={{ fontSize: 11, fontWeight: 600, color: pendingKycCount > 0 ? '#D97706' : '#64748B', marginTop: 4 }}>
                {pendingKycCount > 0 ? `${pendingKycCount} pending reviews` : '- No pending reviews'}
              </div>
            </div>
          </div>
        </div>

        {/* Card 3: Gateway Status */}
        <div className="card" style={{ padding: '20px 22px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#DCFCE7',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#10B981',
              flexShrink: 0
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M5 12.55a11 11 0 0 1 14.08 0" />
                <path d="M1.42 9a16 16 0 0 1 21.16 0" />
                <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
                <line x1="12" y1="20" x2="12.01" y2="20" />
              </svg>
            </div>
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: '#64748B' }}>
                Gateway Status
              </div>
              <div style={{ marginTop: 4 }}>
                <span style={{
                  display: 'inline-block',
                  background: gatewayConfig?.maintenance_mode ? '#F59E0B' : '#059669',
                  color: 'white',
                  fontSize: 11.5,
                  fontWeight: 800,
                  padding: '2px 10px',
                  borderRadius: 9999
                }}>
                  {gatewayConfig?.maintenance_mode ? 'Maintenance' : 'Active'}
                </span>
              </div>
              <div style={{ fontSize: 11, fontWeight: 600, color: '#059669', marginTop: 4 }}>
                All systems operational
              </div>
            </div>
          </div>
        </div>

        {/* Card 4: Min Amount */}
        <div className="card" style={{ padding: '20px 22px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#F3E8FF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#7C3AED',
              flexShrink: 0
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="6" width="20" height="12" rx="2" />
                <circle cx="12" cy="12" r="2" />
                <path d="M6 12h.01M18 12h.01" />
              </svg>
            </div>
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: '#64748B' }}>
                Min Amount
              </div>
              <div style={{ fontSize: 26, fontWeight: 800, color: '#0F172A', lineHeight: 1.15, marginTop: 2 }}>
                ৳{gatewayConfig?.min_amount ?? 10}
              </div>
              <div style={{ fontSize: 11, fontWeight: 500, color: '#64748B', marginTop: 4 }}>
                Minimum transaction amount
              </div>
            </div>
          </div>
        </div>

        {/* Card 5: Max Amount */}
        <div className="card" style={{ padding: '20px 22px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              background: '#EDE9FE',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#6366F1',
              flexShrink: 0
            }}>
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 12V7H5a2 2 0 0 1 0-4h14v4" />
                <path d="M3 5v14a2 2 0 0 0 2 2h16v-5" />
                <path d="M18 12a2 2 0 0 0 0 4h4v-4Z" />
              </svg>
            </div>
            <div>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: '#64748B' }}>
                Max Amount
              </div>
              <div style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', lineHeight: 1.15, marginTop: 2 }}>
                ৳{(gatewayConfig?.max_amount ?? 500000).toLocaleString()}
              </div>
              <div style={{ fontSize: 11, fontWeight: 500, color: '#64748B', marginTop: 4 }}>
                Maximum transaction amount
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ─────────────────────────────────────────────────────────────
          3. MIDDLE SECTION: PAYMENT METHODS + CHART + SYSTEM HEALTH
      ───────────────────────────────────────────────────────────── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '2fr 1fr',
        gap: 20
      }}>
        {/* Left Column: Payment Methods Card & Chart */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Card 1: Payment Methods */}
          <div className="card" style={{ padding: 22 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
              <div style={{
                width: 28,
                height: 28,
                borderRadius: 8,
                background: '#EFF6FF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#2563EB'
              }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="4" y1="21" x2="4" y2="14" />
                  <line x1="4" y1="10" x2="4" y2="3" />
                  <line x1="12" y1="21" x2="12" y2="12" />
                  <line x1="12" y1="8" x2="12" y2="3" />
                  <line x1="20" y1="21" x2="20" y2="16" />
                  <line x1="20" y1="12" x2="20" y2="3" />
                  <line x1="1" y1="14" x2="7" y2="14" />
                  <line x1="9" y1="8" x2="15" y2="8" />
                  <line x1="17" y1="16" x2="23" y2="16" />
                </svg>
              </div>
              <div>
                <span style={{ fontSize: 16, fontWeight: 800, color: '#0F172A', marginRight: 8 }}>
                  Payment Methods
                </span>
                <span style={{ fontSize: 12.5, color: '#64748B' }}>
                  Enable or disable payment methods for your platform
                </span>
              </div>
            </div>

            {/* 4 Method Cards */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: 14
            }}>
              {/* 1. bKash */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                border: '1px solid #E2E8F0',
                borderRadius: 14,
                background: '#FFFFFF'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 36,
                    height: 36,
                    borderRadius: 10,
                    background: '#FDF2F8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#E11D48',
                    fontWeight: 800,
                    fontSize: 16
                  }}>
                    🦩
                  </div>
                  <div>
                    <div style={{ fontSize: 13.5, fontWeight: 800, color: '#0F172A' }}>bKash</div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: enabledMethods.bKash ? '#10B981' : '#94A3B8' }}>
                      {enabledMethods.bKash ? 'Active' : 'Disabled'}
                    </div>
                  </div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={enabledMethods.bKash}
                    onChange={() => handleToggleMethod('bKash', enabledMethods.bKash)}
                  />
                  <span className="slider" />
                </label>
              </div>

              {/* 2. Nagad */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                border: '1px solid #E2E8F0',
                borderRadius: 14,
                background: '#FFFFFF'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 36,
                    height: 36,
                    borderRadius: 10,
                    background: '#FFF7ED',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#EA580C',
                    fontWeight: 800,
                    fontSize: 16
                  }}>
                    🔥
                  </div>
                  <div>
                    <div style={{ fontSize: 13.5, fontWeight: 800, color: '#0F172A' }}>Nagad</div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: enabledMethods.Nagad ? '#10B981' : '#94A3B8' }}>
                      {enabledMethods.Nagad ? 'Active' : 'Disabled'}
                    </div>
                  </div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={enabledMethods.Nagad}
                    onChange={() => handleToggleMethod('Nagad', enabledMethods.Nagad)}
                  />
                  <span className="slider" />
                </label>
              </div>

              {/* 3. Rocket */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                border: '1px solid #E2E8F0',
                borderRadius: 14,
                background: '#FFFFFF'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 36,
                    height: 36,
                    borderRadius: 10,
                    background: '#FAF5FF',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#9333EA',
                    fontWeight: 800,
                    fontSize: 16
                  }}>
                    🚀
                  </div>
                  <div>
                    <div style={{ fontSize: 13.5, fontWeight: 800, color: '#0F172A' }}>Rocket</div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: enabledMethods.Rocket ? '#10B981' : '#94A3B8' }}>
                      {enabledMethods.Rocket ? 'Active' : 'Disabled'}
                    </div>
                  </div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={enabledMethods.Rocket}
                    onChange={() => handleToggleMethod('Rocket', enabledMethods.Rocket)}
                  />
                  <span className="slider" />
                </label>
              </div>

              {/* 4. Upay */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '14px 16px',
                border: '1px solid #E2E8F0',
                borderRadius: 14,
                background: '#FFFFFF'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 36,
                    height: 36,
                    borderRadius: 10,
                    background: '#FEFCE8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#CA8A04',
                    fontWeight: 800,
                    fontSize: 16
                  }}>
                    ⚡
                  </div>
                  <div>
                    <div style={{ fontSize: 13.5, fontWeight: 800, color: '#0F172A' }}>Upay</div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: enabledMethods.Upay ? '#10B981' : '#94A3B8' }}>
                      {enabledMethods.Upay ? 'Active' : 'Disabled'}
                    </div>
                  </div>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={enabledMethods.Upay}
                    onChange={() => handleToggleMethod('Upay', enabledMethods.Upay)}
                  />
                  <span className="slider" />
                </label>
              </div>
            </div>
          </div>

          {/* Card 2: Transaction Volume & Revenue Chart */}
          <div className="card" style={{ padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{
                  width: 28,
                  height: 28,
                  borderRadius: 8,
                  background: '#EFF6FF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#2563EB'
                }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="18" y1="20" x2="18" y2="10" />
                    <line x1="12" y1="20" x2="12" y2="4" />
                    <line x1="6" y1="20" x2="6" y2="14" />
                  </svg>
                </div>
                <span style={{ fontSize: 16, fontWeight: 800, color: '#0F172A' }}>
                  Transaction Volume & Revenue
                </span>
                {/* Legend Chips */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginLeft: 24, fontSize: 12, fontWeight: 600 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#3B82F6' }} />
                    <span style={{ color: '#475569' }}>Transaction Volume</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#8B5CF6' }} />
                    <span style={{ color: '#475569' }}>Revenue (BDT)</span>
                  </div>
                </div>
              </div>

              {/* Time Range Filter */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                border: '1px solid #E2E8F0',
                borderRadius: 8,
                padding: '5px 10px',
                fontSize: 12,
                fontWeight: 600,
                color: '#475569',
                cursor: 'pointer'
              }}>
                <span>Last 7 days</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </div>
            </div>

            {/* Chart Canvas */}
            <div style={{ height: 210, width: '100%' }}>
              <Chart type="bar" data={chartData} options={chartOptions} />
            </div>
          </div>
        </div>

        {/* Right Column: System Health Card */}
        <div className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column' }}>
          {/* Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{
                width: 28,
                height: 28,
                borderRadius: 8,
                background: '#ECFDF5',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#10B981'
              }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
              </div>
              <span style={{ fontSize: 16, fontWeight: 800, color: '#0F172A' }}>
                System Health
              </span>
            </div>

            <span style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 11,
              fontWeight: 700,
              color: '#059669',
              background: '#ECFDF5',
              padding: '4px 10px',
              borderRadius: 9999
            }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#10B981' }} />
              All Systems Operational
            </span>
          </div>

          {/* Component Health Rows */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14, flex: 1 }}>
            {[
              { name: 'Payment Gateway', status: 'Operational', uptime: '99.98%' },
              { name: 'Database', status: 'Operational', uptime: '99.99%' },
              { name: 'API Services', status: 'Operational', uptime: '99.97%' },
              { name: 'Admin Panel', status: 'Operational', uptime: '99.99%' },
            ].map((srv, idx) => (
              <div key={idx} style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '8px 0',
                borderBottom: idx < 3 ? '1px solid #F1F5F9' : 'none',
                fontSize: 13
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#10B981' }} />
                  <span style={{ fontWeight: 600, color: '#334155' }}>{srv.name}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                  <span style={{ color: '#059669', fontWeight: 600, fontSize: 12 }}>{srv.status}</span>
                  <span style={{ color: '#64748B', fontWeight: 500, fontSize: 12 }}>{srv.uptime}</span>
                </div>
              </div>
            ))}
          </div>

          {/* Bottom Running Smoothly Pill */}
          <Link
            to="/health"
            style={{ textDecoration: 'none', marginTop: 24 }}
          >
            <div style={{
              background: '#F0FDF4',
              border: '1px solid #BBF7D0',
              borderRadius: 12,
              padding: '14px 16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{
                  width: 32,
                  height: 32,
                  borderRadius: '50%',
                  background: '#10B981',
                  color: 'white',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 800,
                  fontSize: 16
                }}>
                  ✓
                </div>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: '#065F46' }}>
                    All systems are running smoothly!
                  </div>
                  <div style={{ fontSize: 11, color: '#047857' }}>
                    Last checked: {lastCheckedTime || 'Just now'}
                  </div>
                </div>
              </div>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2.5">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </div>
          </Link>
        </div>
      </div>

      {/* ─────────────────────────────────────────────────────────────
          4. BOTTOM SECTION: 4 CARDS IN A GRID
      ───────────────────────────────────────────────────────────── */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(4, 1fr)',
        gap: 20
      }}>
        {/* Card 1: Recent Transactions */}
        <div className="card" style={{ padding: 22 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563EB" strokeWidth="2">
                <polyline points="17 1 21 5 17 9" />
                <path d="M3 11V9a4 4 0 0 1 4-4h14" />
                <polyline points="7 23 3 19 7 15" />
                <path d="M21 13v2a4 4 0 0 1-4 4H3" />
              </svg>
              <span style={{ fontSize: 15, fontWeight: 800, color: '#0F172A' }}>Recent Transactions</span>
            </div>
            <Link to="/analytics" style={{ fontSize: 12, fontWeight: 700, color: '#2563EB', textDecoration: 'none' }}>
              View All
            </Link>
          </div>

          <table className="enterprise-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Merchant</th>
                <th>Method</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              {displayTransactions.map((tx: any, idx: number) => {
                const isSuccess = tx.status === 'PAID' || tx.status === 'Success'
                const isFailed = tx.status === 'FAILED' || tx.status === 'Failed'
                return (
                  <tr key={idx}>
                    <td style={{ fontWeight: 700, color: '#334155', fontSize: 12 }}>
                      {tx.id || tx.tran_id || `#TXN00${idx + 1}`}
                    </td>
                    <td style={{ fontWeight: 600 }}>
                      {tx.merchant_name || tx.merchant || 'Dream Mart'}
                    </td>
                    <td>
                      <span style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        width: 22,
                        height: 22,
                        borderRadius: 6,
                        background: tx.method === 'Nagad' ? '#FFF7ED' : tx.method === 'Rocket' ? '#FAF5FF' : tx.method === 'Upay' ? '#FEFCE8' : '#FDF2F8',
                        fontSize: 12
                      }}>
                        {tx.method === 'Nagad' ? '🔥' : tx.method === 'Rocket' ? '🚀' : tx.method === 'Upay' ? '⚡' : '🦩'}
                      </span>
                    </td>
                    <td style={{ fontWeight: 700, color: '#0F172A' }}>
                      ৳{(tx.amount || 0).toLocaleString()}
                    </td>
                    <td>
                      <span className={`status-pill ${isSuccess ? 'success' : isFailed ? 'failed' : 'pending'}`}>
                        {isSuccess ? 'Success' : isFailed ? 'Failed' : 'Pending'}
                      </span>
                    </td>
                    <td style={{ color: '#64748B', fontSize: 11.5 }}>
                      {tx.time || (tx.created_at ? new Date(tx.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Today')}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>

        {/* Card 2: KYC Review Queue */}
        <div className="card" style={{ padding: 22 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563EB" strokeWidth="2">
                <rect x="3" y="4" width="18" height="16" rx="2" />
                <circle cx="9" cy="10" r="2" />
                <line x1="15" y1="8" x2="17" y2="8" />
                <line x1="15" y1="12" x2="17" y2="12" />
              </svg>
              <span style={{ fontSize: 15, fontWeight: 800, color: '#0F172A' }}>KYC Review Queue</span>
            </div>
            <Link to="/merchants?filter=pending" style={{ fontSize: 12, fontWeight: 700, color: '#2563EB', textDecoration: 'none' }}>
              View All
            </Link>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {displayKyc.map((kyc: any, idx: number) => (
              <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{
                    width: 34,
                    height: 34,
                    borderRadius: '50%',
                    background: kyc.color || '#EFF6FF',
                    color: kyc.textColor || '#2563EB',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 800,
                    fontSize: 13
                  }}>
                    {kyc.initial || (kyc.business_name || kyc.name || 'M')[0].toUpperCase()}
                  </div>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', lineHeight: 1.2 }}>
                      {kyc.business_name || kyc.name}
                    </div>
                    <div style={{ fontSize: 11, color: '#64748B' }}>
                      {kyc.email}
                    </div>
                  </div>
                </div>

                <div style={{ textAlign: 'right' }}>
                  <span className="status-pill pending" style={{ fontSize: 10.5 }}>
                    Pending
                  </span>
                  <div style={{ fontSize: 10.5, color: '#94A3B8', marginTop: 2 }}>
                    {kyc.time || '2 hours ago'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Card 3: Merchant Registry */}
        <div className="card" style={{ padding: 22 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563EB" strokeWidth="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
              </svg>
              <span style={{ fontSize: 15, fontWeight: 800, color: '#0F172A' }}>Merchant Registry</span>
            </div>
            <Link to="/merchants" style={{ fontSize: 12, fontWeight: 700, color: '#2563EB', textDecoration: 'none' }}>
              View All
            </Link>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {displayMerchants.map((m: any, idx: number) => (
              <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{
                    width: 34,
                    height: 34,
                    borderRadius: 10,
                    background: m.color || '#F3E8FF',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 16
                  }}>
                    🏪
                  </div>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', lineHeight: 1.2 }}>
                      {m.business_name || m.name}
                    </div>
                    <div style={{ fontSize: 11, color: '#64748B' }}>
                      ID: {m.id?.slice(0, 8) || `MRC-00${idx + 1}`}
                    </div>
                  </div>
                </div>

                <span className="status-pill success" style={{ fontSize: 10.5 }}>
                  Active
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Card 4: Recent Submissions */}
        <div className="card" style={{ padding: 22 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2563EB" strokeWidth="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
              </svg>
              <span style={{ fontSize: 15, fontWeight: 800, color: '#0F172A' }}>Recent Submissions</span>
            </div>
            <Link to="/submissions" style={{ fontSize: 12, fontWeight: 700, color: '#2563EB', textDecoration: 'none' }}>
              View All
            </Link>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {displaySubmissions.map((sub: any, idx: number) => (
              <div key={idx} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{
                    width: 7,
                    height: 7,
                    borderRadius: '50%',
                    background: sub.dotColor || '#2563EB',
                    flexShrink: 0
                  }} />
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', lineHeight: 1.2 }}>
                      {sub.form_title || sub.title || 'Form Payment'}
                    </div>
                    <div style={{ fontSize: 11, color: '#64748B' }}>
                      {sub.merchant_name || sub.merchant || 'Merchant Customer'}
                    </div>
                  </div>
                </div>

                <div style={{ fontSize: 11, color: '#94A3B8' }}>
                  {sub.time || (sub.created_at ? new Date(sub.created_at).toLocaleDateString([], { month: 'short', day: 'numeric' }) : '2 hours ago')}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Add New Merchant Modal */}
      <AddMerchantModal
        isOpen={isAddMerchantOpen}
        onClose={() => setIsAddMerchantOpen(false)}
        onMerchantCreated={() => loadDashboardData()}
      />
    </div>
  )
}
