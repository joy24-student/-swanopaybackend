import React, { useEffect, useState, useMemo } from 'react';
import { adminSupabase, fetchPaymentEvents } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';
import DetailDrawer from '../components/DetailDrawer';
import {
  DollarSign,
  CheckCircle2,
  TrendingUp,
  BarChart3,
  XCircle,
  Download,
  Search,
  Filter,
  RefreshCw,
  CreditCard,
  Layers,
  Clock,
  ExternalLink,
  Copy,
  Check,
  Building,
  User,
  Hash,
  Activity,
} from 'lucide-react';

interface PaymentEvent {
  id: string;
  order_id: string;
  tran_id?: string;
  trx_id?: string;
  status: 'PAID' | 'FAILED' | 'CANCELLED' | 'PENDING';
  amount: number;
  currency: string;
  payment_method?: string;
  sender_number?: string;
  payment_time?: string;
  merchant_id?: string;
  merchant_name?: string;
  project_ref?: string;
  cus_name?: string;
  cus_email?: string;
  product_name?: string;
  recorded_at: string;
}

export default function PaymentAnalytics() {
  const [events, setEvents] = useState<PaymentEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [methodFilter, setMethodFilter] = useState<string>('ALL');
  const [selectedEvent, setSelectedEvent] = useState<PaymentEvent | null>(null);
  const [copiedId, setCopiedId] = useState(false);

  // Initial fetch + realtime subscription
  const loadData = () => {
    setLoading(true);
    fetchPaymentEvents(200)
      .then(data => setEvents(data as PaymentEvent[]))
      .catch(err => console.error('[Analytics] fetch error:', err.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();

    const channel = adminSupabase
      .channel('payment_events_realtime')
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'payment_events' },
        payload => {
          const newEvent = payload.new as PaymentEvent;
          setEvents(prev => [newEvent, ...prev.slice(0, 199)]);
        }
      )
      .subscribe();

    return () => {
      adminSupabase.removeChannel(channel);
    };
  }, []);

  // Filtered events calculation
  const filteredEvents = useMemo(() => {
    return events.filter(e => {
      if (statusFilter !== 'ALL' && e.status !== statusFilter) return false;
      if (methodFilter !== 'ALL' && e.payment_method !== methodFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesOrder = e.order_id?.toLowerCase().includes(q);
        const matchesTran = e.tran_id?.toLowerCase().includes(q);
        const matchesTrx = e.trx_id?.toLowerCase().includes(q);
        const matchesCustomer =
          e.cus_name?.toLowerCase().includes(q) || e.cus_email?.toLowerCase().includes(q);
        const matchesMerchant = e.merchant_name?.toLowerCase().includes(q);
        if (!matchesOrder && !matchesTran && !matchesTrx && !matchesCustomer && !matchesMerchant) {
          return false;
        }
      }
      return true;
    });
  }, [events, statusFilter, methodFilter, searchQuery]);

  // Aggregate metrics calculation
  const stats = useMemo(() => {
    const paid = events.filter(e => e.status === 'PAID');
    const failed = events.filter(e => e.status === 'FAILED');
    const totalVolume = paid.reduce((sum, e) => sum + Number(e.amount || 0), 0);
    const successRate = events.length > 0 ? ((paid.length / events.length) * 100).toFixed(1) : '0';
    const avgTx = paid.length > 0 ? (totalVolume / paid.length).toFixed(2) : '0.00';

    return {
      totalTransactions: events.length,
      paidCount: paid.length,
      failedCount: failed.length,
      totalVolumeBDT: totalVolume.toLocaleString('en-US', { minimumFractionDigits: 2 }),
      successRate,
      avgTxBDT: Number(avgTx).toLocaleString('en-US', { minimumFractionDigits: 2 }),
    };
  }, [events]);

  // Export to CSV
  const handleExportCSV = () => {
    if (filteredEvents.length === 0) return alert('No events to export');
    const headers = [
      'Recorded At',
      'Order ID',
      'Merchant',
      'Status',
      'Amount BDT',
      'Method',
      'Trx ID',
      'Customer Name',
      'Customer Email',
    ];
    const rows = filteredEvents.map(e => [
      new Date(e.recorded_at).toLocaleString(),
      e.order_id,
      `"${e.merchant_name || 'Merchant'}"`,
      e.status,
      e.amount,
      e.payment_method || 'MFS',
      e.trx_id || '',
      `"${e.cus_name || ''}"`,
      e.cus_email || '',
    ]);

    const csvContent =
      'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute(
      'download',
      `swapnopay_analytics_${new Date().toISOString().slice(0, 10)}.csv`
    );
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const copyOrderId = (idStr: string) => {
    navigator.clipboard.writeText(idStr);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PAID':
        return (
          <span className="status-pill success">
            <span className="status-dot" />
            PAID
          </span>
        );
      case 'FAILED':
        return (
          <span className="status-pill danger">
            <span className="status-dot" />
            FAILED
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="status-pill warning">
            <span className="status-dot" />
            CANCELLED
          </span>
        );
      default:
        return (
          <span className="status-pill neutral">
            <span className="status-dot" />
            PENDING
          </span>
        );
    }
  };

  const getMethodBadge = (method?: string) => {
    const m = method || 'MFS';
    const colorMap: Record<string, { bg: string; text: string; border: string }> = {
      bKash: { bg: '#FDF2F7', text: '#BE185D', border: '#FBCFE8' },
      Nagad: { bg: '#FFF7ED', text: '#C2410C', border: '#FFEDD5' },
      Rocket: { bg: '#FAF5FF', text: '#7E22CE', border: '#F3E8FF' },
      Upay: { bg: '#ECFDF5', text: '#047857', border: '#A7F3D0' },
    };
    const c = colorMap[m] || { bg: 'var(--bg-muted)', text: 'var(--text-secondary)', border: 'var(--border-default)' };

    return (
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          padding: '2px 8px',
          borderRadius: 6,
          fontSize: 11,
          fontWeight: 700,
          background: c.bg,
          color: c.text,
          border: `1px solid ${c.border}`,
        }}
      >
        <CreditCard size={11} />
        {m}
      </span>
    );
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Header Section */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)', margin: 0, letterSpacing: '-0.02em' }}>
              Payment Analytics & Ledger
            </h1>
            <span className="status-pill success" style={{ fontSize: 11 }}>
              <span className="status-dot" />
              Live Stream
            </span>
          </div>
          <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 13 }}>
            Real-time multi-gateway transaction flow, success rates, and customer audit ledger.
          </p>
        </div>

        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <button
            className="btn btn-secondary btn-sm"
            onClick={loadData}
            title="Refresh transactions"
            disabled={loading}
          >
            <RefreshCw size={13} className={loading ? 'spin' : ''} />
            Refresh
          </button>
          <button className="btn btn-secondary btn-sm" onClick={handleExportCSV}>
            <Download size={13} />
            Export CSV
          </button>
          <Link to="/dashboard" className="btn btn-primary btn-sm">
            Overview Dashboard
          </Link>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))' }}>
        {/* Card 1: Total Volume */}
        <div className="kpi-card">
          <div className="kpi-top">
            <span className="kpi-label">Total Volume (BDT)</span>
            <div className="kpi-icon-wrap" style={{ background: 'var(--success-subtle)', color: 'var(--success)' }}>
              <DollarSign size={15} />
            </div>
          </div>
          <div className="kpi-value" style={{ color: 'var(--text-primary)' }}>
            ৳{stats.totalVolumeBDT}
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--text-muted)' }}>
            Across {stats.paidCount} settled orders
          </div>
        </div>

        {/* Card 2: Successful Payments */}
        <div className="kpi-card">
          <div className="kpi-top">
            <span className="kpi-label">Successful Transactions</span>
            <div className="kpi-icon-wrap" style={{ background: 'rgba(16, 185, 129, 0.1)', color: '#10B981' }}>
              <CheckCircle2 size={15} />
            </div>
          </div>
          <div className="kpi-value" style={{ color: '#10B981' }}>
            {stats.paidCount}
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--text-muted)' }}>
            Total confirmed payments
          </div>
        </div>

        {/* Card 3: Success Rate */}
        <div className="kpi-card">
          <div className="kpi-top">
            <span className="kpi-label">Platform Success Rate</span>
            <div className="kpi-icon-wrap" style={{ background: 'rgba(79, 70, 229, 0.1)', color: '#4F46E5' }}>
              <TrendingUp size={15} />
            </div>
          </div>
          <div className="kpi-value" style={{ color: '#4F46E5' }}>
            {stats.successRate}%
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--text-muted)' }}>
            Based on {stats.totalTransactions} recorded events
          </div>
        </div>

        {/* Card 4: Average Transaction */}
        <div className="kpi-card">
          <div className="kpi-top">
            <span className="kpi-label">Avg Transaction</span>
            <div className="kpi-icon-wrap" style={{ background: 'rgba(124, 58, 237, 0.1)', color: '#7C3AED' }}>
              <BarChart3 size={15} />
            </div>
          </div>
          <div className="kpi-value" style={{ color: 'var(--text-primary)' }}>
            ৳{stats.avgTxBDT}
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--text-muted)' }}>
            Mean value per successful order
          </div>
        </div>

        {/* Card 5: Failed Payments */}
        <div className="kpi-card">
          <div className="kpi-top">
            <span className="kpi-label">Failed Payments</span>
            <div className="kpi-icon-wrap" style={{ background: 'var(--danger-subtle)', color: 'var(--danger)' }}>
              <XCircle size={15} />
            </div>
          </div>
          <div className="kpi-value" style={{ color: 'var(--danger)' }}>
            {stats.failedCount}
          </div>
          <div className="kpi-subtext" style={{ color: 'var(--text-muted)' }}>
            Errors, timeouts, or cancellations
          </div>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="card" style={{ padding: '14px 18px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(240px, 2fr) minmax(160px, 1fr) minmax(160px, 1fr)', gap: 14 }}>
          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              SEARCH TRANSACTIONS
            </label>
            <div style={{ position: 'relative' }}>
              <Search
                size={14}
                style={{
                  position: 'absolute',
                  left: 11,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: 'var(--text-muted)',
                }}
              />
              <input
                className="input"
                style={{ paddingLeft: 32 }}
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder="Search Order ID, Trx ID, Customer name, Merchant..."
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              STATUS FILTER
            </label>
            <select
              className="select"
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
            >
              <option value="ALL">All Statuses</option>
              <option value="PAID">PAID (Settled)</option>
              <option value="FAILED">FAILED</option>
              <option value="CANCELLED">CANCELLED</option>
              <option value="PENDING">PENDING</option>
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 6 }}>
              GATEWAY METHOD
            </label>
            <select
              className="select"
              value={methodFilter}
              onChange={e => setMethodFilter(e.target.value)}
            >
              <option value="ALL">All Methods</option>
              <option value="bKash">bKash</option>
              <option value="Nagad">Nagad</option>
              <option value="Rocket">Rocket</option>
              <option value="Upay">Upay</option>
            </select>
          </div>
        </div>
      </div>

      {/* Main Transactions Table Card */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid var(--border-default)',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: 'var(--text-primary)' }}>
              Transaction Stream ({filteredEvents.length})
            </h3>
            <p style={{ margin: '2px 0 0', fontSize: 12, color: 'var(--text-secondary)' }}>
              Click any transaction row to inspect complete payload and customer details
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              Showing {filteredEvents.length} of {events.length}
            </span>
          </div>
        </div>

        {loading ? (
          <div style={{ padding: '60px 20px', textAlign: 'center', color: 'var(--text-secondary)' }}>
            <RefreshCw size={24} className="spin" style={{ margin: '0 auto 12px', display: 'block', color: 'var(--brand-primary)' }} />
            <div style={{ fontSize: 13, fontWeight: 600 }}>Loading payment stream...</div>
          </div>
        ) : filteredEvents.length === 0 ? (
          <div style={{ padding: '60px 20px', textAlign: 'center', color: 'var(--text-muted)' }}>
            <Layers size={32} style={{ margin: '0 auto 10px', display: 'block', opacity: 0.4 }} />
            <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-primary)' }}>No matching payment records found</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>Try clearing search terms or selecting a different status filter.</div>
          </div>
        ) : (
          <div className="enterprise-table-container">
            <table className="enterprise-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Order Reference</th>
                  <th>Merchant</th>
                  <th>Amount</th>
                  <th>Method</th>
                  <th>Status</th>
                  <th>Provider Trx ID</th>
                  <th>Customer</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredEvents.map(e => (
                  <tr
                    key={e.id}
                    onClick={() => setSelectedEvent(e)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td style={{ whiteSpace: 'nowrap', fontSize: 12, color: 'var(--text-secondary)' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <Clock size={12} color="var(--text-muted)" />
                        {new Date(e.recorded_at).toLocaleDateString([], { month: 'short', day: 'numeric' })}{' '}
                        <span style={{ color: 'var(--text-muted)', fontSize: 11 }}>
                          {new Date(e.recorded_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </span>
                      </div>
                    </td>
                    <td>
                      <span className="mono-badge">
                        {e.order_id ? `#${e.order_id.slice(0, 12)}` : '--'}
                      </span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: 13 }}>
                        {e.merchant_name || 'Direct Merchant'}
                      </div>
                      {e.product_name && (
                        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                          {e.product_name}
                        </div>
                      )}
                    </td>
                    <td>
                      <span style={{ fontWeight: 700, fontSize: 13.5, color: 'var(--text-primary)' }}>
                        ৳{Number(e.amount || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}
                      </span>
                    </td>
                    <td>{getMethodBadge(e.payment_method)}</td>
                    <td>{getStatusBadge(e.status)}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11.5, color: 'var(--text-secondary)' }}>
                      {e.trx_id || '—'}
                    </td>
                    <td>
                      <div style={{ fontSize: 12.5, fontWeight: 500, color: 'var(--text-primary)' }}>
                        {e.cus_name || 'Customer'}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                        {e.cus_email || e.sender_number || '—'}
                      </div>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={evt => {
                          evt.stopPropagation();
                          setSelectedEvent(e);
                        }}
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Slide-over Detail Drawer */}
      <DetailDrawer
        isOpen={Boolean(selectedEvent)}
        onClose={() => setSelectedEvent(null)}
        title="Transaction Inspector"
        subtitle={selectedEvent ? `Order #${selectedEvent.order_id}` : ''}
        badge={selectedEvent ? getStatusBadge(selectedEvent.status) : undefined}
        tabs={[
          {
            key: 'overview',
            label: 'Overview',
            content: selectedEvent && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                {/* Highlight Banner */}
                <div
                  style={{
                    padding: 18,
                    borderRadius: 'var(--radius-md)',
                    background: 'var(--bg-subtle)',
                    border: '1px solid var(--border-default)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Transaction Amount
                    </div>
                    <div style={{ fontSize: 26, fontWeight: 800, color: 'var(--text-primary)', marginTop: 4 }}>
                      ৳{Number(selectedEvent.amount || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}{' '}
                      <span style={{ fontSize: 13, color: 'var(--text-secondary)', fontWeight: 500 }}>
                        {selectedEvent.currency || 'BDT'}
                      </span>
                    </div>
                  </div>
                  {getMethodBadge(selectedEvent.payment_method)}
                </div>

                {/* Key Attributes */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                  <div style={{ padding: 12, borderRadius: 8, background: 'var(--bg-subtle)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 3 }}>Gateway Method</div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>
                      {selectedEvent.payment_method || 'MFS Wallet'}
                    </div>
                  </div>
                  <div style={{ padding: 12, borderRadius: 8, background: 'var(--bg-subtle)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 3 }}>Provider Trx ID</div>
                    <div style={{ fontSize: 12, fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--brand-primary)' }}>
                      {selectedEvent.trx_id || 'N/A'}
                    </div>
                  </div>
                </div>

                {/* Order Information */}
                <div>
                  <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-secondary)', marginBottom: 8, textTransform: 'uppercase' }}>
                    Order Details
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                      <span style={{ color: 'var(--text-secondary)' }}>Order ID</span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <code style={{ fontSize: 12 }}>{selectedEvent.order_id}</code>
                        <button
                          className="btn-ghost"
                          style={{ padding: 2, cursor: 'pointer', border: 'none' }}
                          onClick={() => copyOrderId(selectedEvent.order_id)}
                          title="Copy Order ID"
                        >
                          {copiedId ? <Check size={13} color="var(--success)" /> : <Copy size={13} />}
                        </button>
                      </div>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                      <span style={{ color: 'var(--text-secondary)' }}>Internal Tran ID</span>
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-primary)' }}>
                        {selectedEvent.tran_id || 'None'}
                      </span>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                      <span style={{ color: 'var(--text-secondary)' }}>Product</span>
                      <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                        {selectedEvent.product_name || 'Custom Checkout'}
                      </span>
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                      <span style={{ color: 'var(--text-secondary)' }}>Timestamp</span>
                      <span style={{ color: 'var(--text-primary)' }}>
                        {new Date(selectedEvent.recorded_at).toLocaleString()}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            ),
          },
          {
            key: 'customer',
            label: 'Customer & Merchant',
            content: selectedEvent && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                {/* Merchant Block */}
                <div style={{ padding: 14, borderRadius: 8, background: 'var(--bg-subtle)', border: '1px solid var(--border-default)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <Building size={16} color="var(--brand-primary)" />
                    <span style={{ fontWeight: 700, fontSize: 13 }}>Merchant Organization</span>
                  </div>
                  <div style={{ fontSize: 13, display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <div><strong>Name:</strong> {selectedEvent.merchant_name || 'N/A'}</div>
                    <div><strong>ID:</strong> <code style={{ fontSize: 11 }}>{selectedEvent.merchant_id || 'Platform Direct'}</code></div>
                  </div>
                </div>

                {/* Customer Block */}
                <div style={{ padding: 14, borderRadius: 8, background: 'var(--bg-subtle)', border: '1px solid var(--border-default)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                    <User size={16} color="var(--brand-primary)" />
                    <span style={{ fontWeight: 700, fontSize: 13 }}>Customer Details</span>
                  </div>
                  <div style={{ fontSize: 13, display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div><strong>Name:</strong> {selectedEvent.cus_name || 'Unspecified'}</div>
                    <div><strong>Email:</strong> {selectedEvent.cus_email || 'Unspecified'}</div>
                    <div><strong>Masked Sender Phone:</strong> {selectedEvent.sender_number || 'N/A'}</div>
                  </div>
                </div>
              </div>
            ),
          },
          {
            key: 'payload',
            label: 'Raw JSON',
            content: selectedEvent && (
              <pre
                style={{
                  background: 'var(--bg-subtle)',
                  padding: 14,
                  borderRadius: 8,
                  fontSize: 12,
                  fontFamily: 'var(--font-mono)',
                  color: 'var(--text-primary)',
                  overflowX: 'auto',
                  border: '1px solid var(--border-default)',
                }}
              >
                {JSON.stringify(selectedEvent, null, 2)}
              </pre>
            ),
          },
        ]}
      />
    </div>
  );
}
