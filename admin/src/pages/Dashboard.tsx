import React, { useEffect, useState } from 'react'
import { adminSupabase, fetchGatewayConfig, GATEWAY_CONFIG_ID } from '../adminSupabaseClient'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'

export default function Dashboard() {
  const [merchantCount, setMerchantCount] = useState<number | null>(null)
  const [pendingKycCount, setPendingKycCount] = useState<number>(0)
  const [gatewayConfig, setGatewayConfig] = useState<any>(null)
  const { signOut } = useAuth()

  useEffect(() => {
    (async () => {
      try {
        const { count, error } = await adminSupabase
          .from('merchant_gateway_settings')
          .select('*', { count: 'exact', head: true })
        if (!error && count !== null) {
          setMerchantCount(count)
        }
      } catch (e) {
        console.error('[Dashboard] merchant count error:', e)
      }

      try {
        const { count } = await adminSupabase
          .from('merchants')
          .select('*', { count: 'exact', head: true })
          .in('kyc_status', ['PENDING', 'PENDING_REVIEW'])
        if (count !== null) setPendingKycCount(count)
      } catch (e) {}
    })()
  }, [])

  useEffect(() => {
    fetchGatewayConfig()
      .then(row => row && setGatewayConfig(row))
      .catch(err => console.error('[Dashboard] gateway config:', err.message))

    const channel = adminSupabase
      .channel('dashboard_gateway_config')
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'gateway_config', filter: `id=eq.${GATEWAY_CONFIG_ID}` },
        (payload) => setGatewayConfig(payload.new))
      .subscribe()
    return () => { adminSupabase.removeChannel(channel) }
  }, [])

  const enabledMethods: Record<string, boolean> = {
    bKash:  gatewayConfig?.bkash_enabled  ?? true,
    Nagad:  gatewayConfig?.nagad_enabled  ?? true,
    Rocket: gatewayConfig?.rocket_enabled ?? true,
    Upay:   gatewayConfig?.upay_enabled   ?? true,
  }
  const methodColors: Record<string, string> = { bKash: '#E2125A', Nagad: '#EC5A24', Rocket: '#8C3494', Upay: '#10B981' }

  const navCards = [
    { to: '/merchants?filter=pending', icon: '🛡️', title: 'KYC Reviews', desc: `${pendingKycCount} pending NID & biometric reviews`, color: '#D97706' },
    { to: '/analytics', icon: '📊', title: 'Payment Analytics', desc: 'Realtime transaction stream, revenue & volume', color: '#10B981' },
    { to: '/gateway-settings', icon: '⚙️', title: 'Payment Gateway', desc: 'Methods, URLs, API keys, limits & fees', color: '#4F46E5' },
    { to: '/health', icon: '📡', title: 'System Health', desc: 'Realtime diagnostics, DB latency & active sockets', color: '#0284C7' },
    { to: '/mfs-patterns', icon: '📱', title: 'MFS Regex Patterns', desc: 'Manage & test SMS parsing patterns (bKash, Nagad)', color: '#D97706' },
    { to: '/merchants', icon: '🏪', title: 'Merchants Registry', desc: `${merchantCount ?? '…'} registered merchants`, color: '#0891B2' },
    { to: '/settings', icon: '📋', title: 'System CMS', desc: 'FAQs, guides, video tutorials, support contacts', color: '#7C3AED' },
    { to: '/submissions', icon: '📄', title: 'Submissions', desc: 'Form payment submissions', color: '#059669' },
    { to: '/connect-supabase', icon: '⚡', title: 'Supabase OAuth', desc: 'Provision & manage merchant databases', color: '#3ECF8E' },
    { to: '/support', icon: '🎫', title: 'Helpdesk & Appeals', desc: 'Support tickets & merchant appeals', color: '#DC2626' },
  ]

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>🏦 SwapnoPay Admin</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>Platform Owner Control Panel</p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to="/gateway-settings">
            <button className="button" style={{ background: '#4F46E5' }}>⚙️ Gateway</button>
          </Link>
          <button className="button" style={{ background: '#64748B' }} onClick={() => signOut()}>Sign Out</button>
        </div>
      </div>

      {/* Maintenance Warning */}
      {gatewayConfig?.maintenance_mode && (
        <div style={{ padding: '12px 16px', background: '#FEF3C7', border: '1px solid #F59E0B', borderRadius: 8, marginBottom: 16, fontWeight: 600, color: '#92400E' }}>
          🚧 MAINTENANCE MODE is ACTIVE — checkout is disabled for all customers.{' '}
          <Link to="/gateway-settings" style={{ color: '#B45309' }}>Disable it →</Link>
        </div>
      )}

      {/* Stats Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(160px,1fr))', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Total Merchants', value: merchantCount ?? '…', color: '#4F46E5', link: '/merchants' },
          { label: 'Pending KYC', value: pendingKycCount, color: pendingKycCount > 0 ? '#D97706' : '#10B981', link: '/merchants?filter=pending' },
          { label: 'Gateway Status', value: gatewayConfig?.maintenance_mode ? '🚧 Maintenance' : '✅ Active', color: '#10B981' },
          { label: 'Min Amount', value: gatewayConfig ? `৳${gatewayConfig.min_amount}` : '…', color: '#0891B2' },
          { label: 'Max Amount', value: gatewayConfig ? `৳${(gatewayConfig.max_amount || 0).toLocaleString()}` : '…', color: '#7C3AED' },
        ].map(({ label, value, color, link }: any) => (
          <div key={label} className="card" style={{ padding: '14px 16px', margin: 0 }}>
            <div style={{ fontSize: 11, color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 1 }}>{label}</div>
            <div style={{ fontSize: 22, fontWeight: 800, color, marginTop: 4 }}>
              {link ? <Link to={link} style={{ color, textDecoration: 'none' }}>{value}</Link> : value}
            </div>
          </div>
        ))}
      </div>

      {/* Payment Methods Status */}
      {gatewayConfig && (
        <div className="card" style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <h3 style={{ margin: 0 }}>💳 Payment Methods</h3>
            <Link to="/gateway-settings"><button className="button" style={{ fontSize: 11, padding: '6px 12px', background: '#4F46E5' }}>Manage</button></Link>
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            {(['bKash', 'Nagad', 'Rocket', 'Upay'] as const).map(m => (
              <div key={m} style={{ padding: '8px 14px', borderRadius: 8, border: `1px solid ${enabledMethods[m] ? methodColors[m] : '#E2E8F0'}`, background: enabledMethods[m] ? `${methodColors[m]}15` : '#F8FAFC' }}>
                <span style={{ fontWeight: 700, fontSize: 13, color: enabledMethods[m] ? methodColors[m] : '#94A3B8' }}>{m}</span>
                <span style={{ fontSize: 10, marginLeft: 6, color: enabledMethods[m] ? '#10B981' : '#EF4444' }}>{enabledMethods[m] ? '●' : '○'}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Navigation Cards Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(240px,1fr))', gap: 14 }}>
        {navCards.map(({ to, icon, title, desc, color }) => (
          <Link key={to} to={to} style={{ textDecoration: 'none' }}>
            <div className="card" style={{ margin: 0, padding: '18px 20px', cursor: 'pointer', borderLeft: `4px solid ${color}`, transition: 'transform 0.15s, box-shadow 0.15s' }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.transform = 'translateY(-2px)'; (e.currentTarget as HTMLElement).style.boxShadow = '0 8px 24px rgba(0,0,0,0.1)' }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.transform = ''; (e.currentTarget as HTMLElement).style.boxShadow = '' }}>
              <div style={{ fontSize: 24, marginBottom: 6 }}>{icon}</div>
              <div style={{ fontWeight: 700, fontSize: 15, color: '#1E293B' }}>{title}</div>
              <div style={{ fontSize: 12, color: '#64748B', marginTop: 4 }}>{desc}</div>
              <div style={{ marginTop: 10, fontSize: 12, color, fontWeight: 700 }}>Open →</div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
