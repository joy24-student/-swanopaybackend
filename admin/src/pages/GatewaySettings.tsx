import React, { useEffect, useState, useCallback } from 'react';
import {
  adminSupabase,
  fetchGatewayConfig,
  updateGatewayConfig,
  fetchApiKeys,
  GATEWAY_CONFIG_ID,
} from '../adminSupabaseClient';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import {
  CreditCard,
  Link2,
  Sliders,
  Key,
  Mail,
  AlertTriangle,
  Activity,
  Save,
  Check,
  RefreshCw,
  Copy,
  Trash2,
  ExternalLink,
  ShieldAlert,
} from 'lucide-react';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────
interface EnabledMethods {
  bKash: boolean;
  Nagad: boolean;
  Rocket: boolean;
  Upay: boolean;
}

interface GatewayConfig {
  enabled_methods: EnabledMethods;
  default_success_url: string;
  default_fail_url: string;
  default_cancel_url: string;
  min_amount: number;
  max_amount: number;
  daily_limit_per_merchant: number;
  payment_timeout_seconds: number;
  processing_timeout_seconds: number;
  customer_receipts_enabled: boolean;
  merchant_receipts_enabled: boolean;
  gateway_fee_percent: number;
  gateway_fee_fixed: number;
  maintenance_mode: boolean;
  maintenance_message: string;
  last_updated?: number | null;
}

interface ApiKeyRecord {
  id: string;
  merchant_id: string;
  merchant_name: string;
  label: string;
  key_preview: string;
  revoked: boolean;
  created_at: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Default Config
// ─────────────────────────────────────────────────────────────────────────────
const DEFAULT_CONFIG: GatewayConfig = {
  enabled_methods: { bKash: true, Nagad: true, Rocket: true, Upay: true },
  default_success_url: 'https://pay.swapnopay.top/success',
  default_fail_url: 'https://pay.swapnopay.top/failed',
  default_cancel_url: 'https://pay.swapnopay.top/cancelled',
  min_amount: 10,
  max_amount: 500000,
  daily_limit_per_merchant: 10000000,
  payment_timeout_seconds: 600,
  processing_timeout_seconds: 300,
  customer_receipts_enabled: true,
  merchant_receipts_enabled: true,
  gateway_fee_percent: 0,
  gateway_fee_fixed: 0,
  maintenance_mode: false,
  maintenance_message: '',
  last_updated: null,
};

type TabKey = 'methods' | 'urls' | 'limits' | 'keys' | 'receipts' | 'maintenance' | 'health';

// ─────────────────────────────────────────────────────────────────────────────
// Component
// ─────────────────────────────────────────────────────────────────────────────
export default function GatewaySettings() {
  const [config, setConfig] = useState<GatewayConfig>(DEFAULT_CONFIG);
  const [activeTab, setActiveTab] = useState<TabKey>('methods');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [statusMsg, setStatusMsg] = useState('');

  // API Key Management state
  const [apiKeys, setApiKeys] = useState<ApiKeyRecord[]>([]);
  const [newKeyMerchantId, setNewKeyMerchantId] = useState('');
  const [newKeyMerchantName, setNewKeyMerchantName] = useState('');
  const [newKeyLabel, setNewKeyLabel] = useState('Default API Key');
  const [generatedKey, setGeneratedKey] = useState<string | null>(null);
  const [keyLoading, setKeyLoading] = useState(false);

  // Auth & Backend URL for API calls
  const defaultBackend =
    ((import.meta as any).env?.VITE_BACKEND_URL as string) ||
    (typeof window !== 'undefined' && window.location.hostname.includes('swapnopay.top') ? window.location.origin : 'https://api.swapnopay.top');
  const storedBackend = localStorage.getItem('swapnopay_backend_url');
  const backendUrl = (storedBackend && !storedBackend.includes('pay.swapnopay.top')) ? storedBackend : defaultBackend;
  const [adminSecret, setAdminSecret] = useState(sessionStorage.getItem('swapnopay_admin_secret') || '');

  const getAuthHeaders = useCallback(() => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (session?.access_token) {
      headers['Authorization'] = `Bearer ${session.access_token}`;
    } else if (adminSecret) {
      headers['X-Admin-Secret'] = adminSecret;
    }
    return headers;
  }, [session, adminSecret]);

  // ── Load config from Admin Supabase (with realtime subscription) ──
  useEffect(() => {
    // Initial fetch
    fetchGatewayConfig()
      .then(row => {
        if (row) setConfig({
          ...DEFAULT_CONFIG,
          enabled_methods: {
            bKash:  row.bkash_enabled  ?? true,
            Nagad:  row.nagad_enabled  ?? true,
            Rocket: row.rocket_enabled ?? true,
            Upay:   row.upay_enabled   ?? true,
          },
          default_success_url:  row.default_success_url  || DEFAULT_CONFIG.default_success_url,
          default_fail_url:     row.default_fail_url     || DEFAULT_CONFIG.default_fail_url,
          default_cancel_url:   row.default_cancel_url   || DEFAULT_CONFIG.default_cancel_url,
          min_amount:           row.min_amount            ?? DEFAULT_CONFIG.min_amount,
          max_amount:           row.max_amount            ?? DEFAULT_CONFIG.max_amount,
          daily_limit_per_merchant: row.daily_limit_per_merchant ?? DEFAULT_CONFIG.daily_limit_per_merchant,
          payment_timeout_seconds:    row.payment_timeout_seconds    ?? DEFAULT_CONFIG.payment_timeout_seconds,
          processing_timeout_seconds: row.processing_timeout_seconds ?? DEFAULT_CONFIG.processing_timeout_seconds,
          gateway_fee_percent: row.gateway_fee_percent ?? 0,
          gateway_fee_fixed:   row.gateway_fee_fixed   ?? 0,
          customer_receipts_enabled: row.customer_receipts_enabled ?? true,
          merchant_receipts_enabled: row.merchant_receipts_enabled ?? true,
          maintenance_mode:    row.maintenance_mode    ?? false,
          maintenance_message: row.maintenance_message || '',
          last_updated: row.updated_at ? new Date(row.updated_at).getTime() : null,
        });
      })
      .catch(err => console.error('[GatewaySettings] load error:', err.message))
      .finally(() => setLoading(false));

    // Realtime subscription
    const channel = adminSupabase
      .channel('gateway_config_changes')
      .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'gateway_config', filter: `id=eq.${GATEWAY_CONFIG_ID}` },
        (payload) => {
          const row = payload.new as any;
          setConfig(prev => ({
            ...prev,
            enabled_methods: {
              bKash:  row.bkash_enabled  ?? prev.enabled_methods.bKash,
              Nagad:  row.nagad_enabled  ?? prev.enabled_methods.Nagad,
              Rocket: row.rocket_enabled ?? prev.enabled_methods.Rocket,
              Upay:   row.upay_enabled   ?? prev.enabled_methods.Upay,
            },
            default_success_url:  row.default_success_url  || prev.default_success_url,
            default_fail_url:     row.default_fail_url     || prev.default_fail_url,
            default_cancel_url:   row.default_cancel_url   || prev.default_cancel_url,
            min_amount:           row.min_amount            ?? prev.min_amount,
            max_amount:           row.max_amount            ?? prev.max_amount,
            daily_limit_per_merchant: row.daily_limit_per_merchant ?? prev.daily_limit_per_merchant,
            payment_timeout_seconds:    row.payment_timeout_seconds    ?? prev.payment_timeout_seconds,
            processing_timeout_seconds: row.processing_timeout_seconds ?? prev.processing_timeout_seconds,
            gateway_fee_percent: row.gateway_fee_percent ?? prev.gateway_fee_percent,
            gateway_fee_fixed:   row.gateway_fee_fixed   ?? prev.gateway_fee_fixed,
            customer_receipts_enabled: row.customer_receipts_enabled ?? prev.customer_receipts_enabled,
            merchant_receipts_enabled: row.merchant_receipts_enabled ?? prev.merchant_receipts_enabled,
            maintenance_mode:    row.maintenance_mode    ?? prev.maintenance_mode,
            maintenance_message: row.maintenance_message ?? prev.maintenance_message,
            last_updated: row.updated_at ? new Date(row.updated_at).getTime() : prev.last_updated,
          }));
        })
      .subscribe();

    return () => { adminSupabase.removeChannel(channel); };
  }, []);

  // ── Load API keys from admin Supabase ──
  useEffect(() => {
    fetchApiKeys()
      .then(list => setApiKeys(list as ApiKeyRecord[]))
      .catch(err => console.error('[GatewaySettings] keys load error:', err.message));

    // Realtime subscription for API keys
    const channel = adminSupabase
      .channel('api_keys_changes')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'platform_api_keys' },
        () => fetchApiKeys().then(list => setApiKeys(list as ApiKeyRecord[])).catch(() => {}))
      .subscribe();
    return () => { adminSupabase.removeChannel(channel); };
  }, []);

  // ── Save config to admin Supabase ──
  const handleSave = useCallback(async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setSaving(true);
    setStatusMsg('Saving gateway configuration to admin database...');
    try {
      await updateGatewayConfig({
        bkash_enabled:  config.enabled_methods.bKash,
        nagad_enabled:  config.enabled_methods.Nagad,
        rocket_enabled: config.enabled_methods.Rocket,
        upay_enabled:   config.enabled_methods.Upay,
        default_success_url:  config.default_success_url,
        default_fail_url:     config.default_fail_url,
        default_cancel_url:   config.default_cancel_url,
        min_amount:           config.min_amount,
        max_amount:           config.max_amount,
        daily_limit_per_merchant: config.daily_limit_per_merchant,
        payment_timeout_seconds:    config.payment_timeout_seconds,
        processing_timeout_seconds: config.processing_timeout_seconds,
        gateway_fee_percent: config.gateway_fee_percent,
        gateway_fee_fixed:   config.gateway_fee_fixed,
        customer_receipts_enabled: config.customer_receipts_enabled,
        merchant_receipts_enabled: config.merchant_receipts_enabled,
        maintenance_mode:    config.maintenance_mode,
        maintenance_message: config.maintenance_message,
      });
      setStatusMsg('SUCCESS: Gateway configuration saved to admin Supabase and synced to all checkout widgets.');
      setTimeout(() => setStatusMsg(''), 5000);
    } catch (err: any) {
      setStatusMsg('ERROR: Failed to save: ' + err.message);
    } finally {
      setSaving(false);
    }
  }, [config]);

  // ── Generate API Key via backend ──
  const handleGenerateKey = useCallback(async () => {
    if (!newKeyMerchantId.trim()) {
      alert('Merchant ID is required to generate a key');
      return;
    }
    if (!session?.access_token && !adminSecret) {
      alert('Admin authorization required. Please log in or enter an admin secret.');
      return;
    }
    setKeyLoading(true);
    setGeneratedKey(null);
    try {
      const res = await fetch(`${backendUrl}/v1/admin/keys/generate`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          merchant_id: newKeyMerchantId.trim(),
          merchant_name: newKeyMerchantName.trim(),
          label: newKeyLabel.trim(),
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Key generation failed');
      setGeneratedKey(data.api_key);
      setNewKeyMerchantId('');
      setNewKeyMerchantName('');
      setNewKeyLabel('Default API Key');
    } catch (err: any) {
      alert('Key generation error: ' + err.message);
    } finally {
      setKeyLoading(false);
    }
  }, [backendUrl, getAuthHeaders, session, adminSecret, newKeyMerchantId, newKeyMerchantName, newKeyLabel]);

  // ── Revoke API Key via backend ──
  const handleRevokeKey = useCallback(async (keyId: string) => {
    if (!session?.access_token && !adminSecret) return alert('Admin authentication required');
    if (!window.confirm('Revoke this API key? This cannot be undone.')) return;
    try {
      const res = await fetch(`${backendUrl}/v1/admin/keys/revoke`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ key_id: keyId }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || 'Revoke failed');
    } catch (err: any) {
      alert('Revoke error: ' + err.message);
    }
  }, [backendUrl, getAuthHeaders, session, adminSecret]);

  if (loading) {
    return (
      <div className="card" style={{ padding: '60px 20px', textAlign: 'center', color: 'var(--text-secondary)' }}>
        <RefreshCw size={24} className="spin" style={{ margin: '0 auto 12px', display: 'block', color: 'var(--brand-primary)' }} />
        <div style={{ fontSize: 13, fontWeight: 600 }}>Loading gateway configuration...</div>
      </div>
    );
  }

  const tabs: { key: TabKey; label: string; icon: any }[] = [
    { key: 'methods', label: 'Payment Methods', icon: CreditCard },
    { key: 'urls', label: 'Redirect URLs', icon: Link2 },
    { key: 'limits', label: 'Limits & Fees', icon: Sliders },
    { key: 'keys', label: 'API Keys', icon: Key },
    { key: 'receipts', label: 'Receipts', icon: Mail },
    { key: 'maintenance', label: 'Maintenance', icon: AlertTriangle },
    { key: 'health', label: 'Backend Health', icon: Activity },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)', margin: 0, letterSpacing: '-0.02em' }}>
              Payment Gateway Settings
            </h1>
            <span className="status-pill info" style={{ fontSize: 11 }}>
              <span className="status-dot" />
              Live Sync
            </span>
          </div>
          <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 13 }}>
            Controls active payment methods, fallback redirect endpoints, limits, and merchant API keys.
          </p>
          {config.last_updated && (
            <p style={{ margin: '4px 0 0', color: 'var(--text-muted)', fontSize: 11 }}>
              Last saved: {new Date(config.last_updated).toLocaleString()}
            </p>
          )}
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <Link to="/settings" className="btn btn-secondary btn-sm">
            System CMS
          </Link>
          <Link to="/dashboard" className="btn btn-primary btn-sm">
            Overview Dashboard
          </Link>
        </div>
      </div>

      {/* Status Banner */}
      {statusMsg && (
        <div
          style={{
            padding: '12px 16px',
            background: statusMsg.startsWith('SUCCESS:') ? 'var(--success-subtle)' : 'var(--danger-subtle)',
            border: `1px solid ${statusMsg.startsWith('SUCCESS:') ? 'var(--success)' : 'var(--danger)'}`,
            borderRadius: 'var(--radius-md)',
            color: statusMsg.startsWith('SUCCESS:') ? 'var(--success)' : 'var(--danger)',
            fontWeight: 600,
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          {statusMsg.startsWith('SUCCESS:') ? <Check size={16} /> : <AlertTriangle size={16} />}
          {statusMsg.replace(/^(SUCCESS:|ERROR:)\s*/, '')}
        </div>
      )}

      {/* Maintenance Banner */}
      {config.maintenance_mode && (
        <div
          style={{
            padding: '12px 16px',
            background: 'var(--warning-subtle)',
            border: '1px solid var(--warning)',
            borderRadius: 'var(--radius-md)',
            fontWeight: 600,
            color: 'var(--warning)',
            fontSize: 13,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
          }}
        >
          <AlertTriangle size={16} />
          Maintenance mode is active — checkout widgets are currently displaying a maintenance message to customers.
        </div>
      )}

      {/* Tab Nav */}
      <div
        style={{
          display: 'flex',
          gap: 6,
          borderBottom: '1px solid var(--border-default)',
          paddingBottom: 2,
          overflowX: 'auto',
        }}
      >
        {tabs.map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 7,
                padding: '9px 14px',
                fontSize: 13,
                fontWeight: isActive ? 600 : 500,
                color: isActive ? 'var(--brand-primary)' : 'var(--text-secondary)',
                background: isActive ? 'var(--brand-subtle)' : 'transparent',
                border: 'none',
                borderBottom: isActive ? '2px solid var(--brand-primary)' : '2px solid transparent',
                borderRadius: '6px 6px 0 0',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                transition: 'all var(--transition-fast)',
              }}
            >
              <Icon size={14} />
              {tab.label}
            </button>
          );
        })}
      </div>

      <form onSubmit={handleSave}>
        {/* ── PAYMENT METHODS TAB ── */}
        {activeTab === 'methods' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <CreditCard size={17} color="var(--brand-primary)" />
              Enable / Disable Payment Methods
            </h3>
            <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 16 }}>
              Disabled methods are hidden from the checkout widget in real-time.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
              {(['bKash', 'Nagad', 'Rocket', 'Upay'] as const).map(method => {
                const enabled = config.enabled_methods[method];
                const colors: Record<string, string> = { bKash: '#E2125A', Nagad: '#EC5A24', Rocket: '#8C3494', Upay: '#10B981' };
                return (
                  <div key={method} onClick={() => setConfig({ ...config, enabled_methods: { ...config.enabled_methods, [method]: !enabled } })}
                    style={{ padding: 16, border: `2px solid ${enabled ? colors[method] : 'var(--border-default)'}`, borderRadius: 12, background: enabled ? `${colors[method]}10` : 'var(--bg-subtle)',
                      cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'space-between', transition: 'all 0.2s' }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15, color: enabled ? colors[method] : 'var(--text-secondary)' }}>{method}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                        {method === 'bKash' ? 'bKash Send Money / Merchant' : method === 'Nagad' ? 'Nagad Send Money' : method === 'Rocket' ? 'DBBL Rocket' : 'Upay Wallet'}
                      </div>
                    </div>
                    <div style={{ width: 44, height: 24, borderRadius: 12, background: enabled ? colors[method] : '#CBD5E1', position: 'relative', transition: 'background 0.2s' }}>
                      <div style={{ width: 18, height: 18, borderRadius: '50%', background: 'white', position: 'absolute', top: 3, left: enabled ? 23 : 3, transition: 'left 0.2s', boxShadow: '0 1px 3px rgba(0,0,0,0.3)' }} />
                    </div>
                  </div>
                );
              })}
            </div>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 14 }}>
              Changes are saved to admin database and broadcast to all checkout widgets.
            </p>
          </div>
        )}

        {/* ── REDIRECT URLS TAB ── */}
        {activeTab === 'urls' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Link2 size={17} color="var(--brand-primary)" />
              Default Redirect URLs
            </h3>
            <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 16 }}>
              These are the fallback URLs used when a merchant hasn't configured their own. Must be HTTPS.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {[
                { key: 'default_success_url', label: 'Success URL (Redirected after PAID)', placeholder: 'https://pay.swapnopay.top/success' },
                { key: 'default_fail_url', label: 'Failure URL (Redirected on failed/expired payment)', placeholder: 'https://pay.swapnopay.top/failed' },
                { key: 'default_cancel_url', label: 'Cancel URL (Redirected when customer cancels)', placeholder: 'https://pay.swapnopay.top/cancelled' },
              ].map(({ key, label, placeholder }) => (
                <div key={key}>
                  <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>{label}</label>
                  <input className="input" value={(config as any)[key]} onChange={e => setConfig({ ...config, [key]: e.target.value })} placeholder={placeholder} />
                  <p style={{ fontSize: 11, color: 'var(--text-muted)', margin: '4px 0 0' }}>
                    SwapnoPay backend appends: <code>?status=PAID&order_id=...&trx_id=...</code>
                  </p>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── LIMITS & FEES TAB ── */}
        {activeTab === 'limits' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Sliders size={17} color="var(--brand-primary)" />
              Transaction Limits & Gateway Fees
            </h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Minimum Amount (BDT)</label>
                <input className="input" type="number" value={config.min_amount} onChange={e => setConfig({ ...config, min_amount: Number(e.target.value) })} min={1} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Maximum Amount (BDT)</label>
                <input className="input" type="number" value={config.max_amount} onChange={e => setConfig({ ...config, max_amount: Number(e.target.value) })} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Daily Limit Per Merchant (BDT)</label>
                <input className="input" type="number" value={config.daily_limit_per_merchant} onChange={e => setConfig({ ...config, daily_limit_per_merchant: Number(e.target.value) })} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Payment Timeout (seconds)</label>
                <input className="input" type="number" value={config.payment_timeout_seconds} onChange={e => setConfig({ ...config, payment_timeout_seconds: Number(e.target.value) })} min={60} max={3600} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Processing Verification Timeout (seconds)</label>
                <input className="input" type="number" value={config.processing_timeout_seconds} onChange={e => setConfig({ ...config, processing_timeout_seconds: Number(e.target.value) })} min={30} max={600} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Gateway Fee % (e.g., 1.5 = 1.5%)</label>
                <input className="input" type="number" step="0.01" value={config.gateway_fee_percent} onChange={e => setConfig({ ...config, gateway_fee_percent: Number(e.target.value) })} min={0} max={10} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: 'var(--text-primary)' }}>Gateway Fixed Fee (BDT)</label>
                <input className="input" type="number" step="0.01" value={config.gateway_fee_fixed} onChange={e => setConfig({ ...config, gateway_fee_fixed: Number(e.target.value) })} min={0} />
              </div>
            </div>
          </div>
        )}

        {/* ── API KEYS TAB ── */}
        {activeTab === 'keys' && (
          <div>
            <div className="card" style={{ marginBottom: 16 }}>
              <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Key size={17} color="var(--brand-primary)" />
                Generate Merchant API Key
              </h3>
              <p style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                Keys are generated by the SwapnoPay backend. The raw key is shown ONCE — store it immediately. Only the digest is stored in Supabase.
              </p>

              {/* Backend config row */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>SwapnoPay Backend URL</label>
                  <input className="input" value={backendUrl} onChange={e => localStorage.setItem('swapnopay_backend_url', e.target.value)} placeholder="http://localhost:4000" />
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Admin Secret (stored locally only)</label>
                  <input className="input" type="password" value={adminSecret} onChange={e => localStorage.setItem('swapnopay_admin_secret', e.target.value)} placeholder="ADMIN_SECRET value" />
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Merchant ID *</label>
                  <input className="input" value={newKeyMerchantId} onChange={e => setNewKeyMerchantId(e.target.value)} placeholder="UUID from merchant Supabase" />
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Merchant Name</label>
                  <input className="input" value={newKeyMerchantName} onChange={e => setNewKeyMerchantName(e.target.value)} placeholder="DreamMart Store" />
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Key Label</label>
                  <input className="input" value={newKeyLabel} onChange={e => setNewKeyLabel(e.target.value)} placeholder="Default API Key" />
                </div>
              </div>
              <button type="button" className="btn btn-primary" onClick={handleGenerateKey} disabled={keyLoading}
                style={{ marginTop: 14 }}>
                <Key size={14} />
                {keyLoading ? 'Generating...' : 'Generate New API Key'}
              </button>

              {generatedKey && (
                <div style={{ marginTop: 14, padding: 14, background: 'var(--warning-subtle)', border: '1px solid var(--warning)', borderRadius: 10 }}>
                  <p style={{ fontSize: 12, fontWeight: 700, color: 'var(--warning)', margin: '0 0 8px', display: 'flex', alignItems: 'center', gap: 6 }}>
                    <AlertTriangle size={15} />
                    Copy this API key now — it cannot be displayed again!
                  </p>
                  <code style={{ display: 'block', padding: '10px 14px', background: 'var(--bg-surface)', borderRadius: 8, fontSize: 13, wordBreak: 'break-all', color: 'var(--text-primary)', fontWeight: 700, border: '1px solid var(--border-default)' }}>
                    {generatedKey}
                  </code>
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => { navigator.clipboard.writeText(generatedKey); alert('API key copied to clipboard.'); }}
                    style={{ marginTop: 10 }}>
                    <Copy size={13} />
                    Copy to Clipboard
                  </button>
                </div>
              )}
            </div>

            {/* Key List */}
            <div className="card" style={{ marginBottom: 16 }}>
              <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Key size={17} color="var(--brand-primary)" />
                All API Keys ({apiKeys.length})
              </h3>
              {apiKeys.length === 0 ? (
                <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>No API keys generated yet.</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {apiKeys.map(key => (
                    <div key={key.id} style={{ padding: 12, border: `1px solid ${key.revoked ? 'var(--danger-border, #FEE2E2)' : 'var(--border-default)'}`, borderRadius: 8, background: key.revoked ? 'var(--danger-subtle)' : 'var(--bg-surface)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 13, color: key.revoked ? 'var(--danger)' : 'var(--text-primary)' }}>
                          {key.label} {key.revoked && <span className="status-pill danger" style={{ marginLeft: 6, fontSize: 10 }}>REVOKED</span>}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Merchant: {key.merchant_name} ({key.merchant_id})</div>
                        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Key: <code>{key.key_preview}</code> • Created: {new Date(key.created_at).toLocaleDateString()}</div>
                      </div>
                      {!key.revoked && (
                        <button type="button" className="btn btn-danger btn-sm" onClick={() => handleRevokeKey(key.id)}>
                          Revoke
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* ── RECEIPTS TAB ── */}
        {activeTab === 'receipts' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Mail size={17} color="var(--brand-primary)" />
              Email Receipt Settings
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {[
                { key: 'customer_receipts_enabled', label: 'Send receipt to customer email after payment', description: 'Customer receives a payment confirmation email via the gateway service.' },
                { key: 'merchant_receipts_enabled', label: 'Send receipt to merchant email after payment', description: 'Merchant receives a payment notification email via the gateway service.' },
              ].map(({ key, label, description }) => {
                const enabled = (config as any)[key] as boolean;
                return (
                  <div key={key} style={{ padding: 14, border: '1px solid var(--border-default)', borderRadius: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 13 }}>{label}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>{description}</div>
                    </div>
                    <div onClick={() => setConfig({ ...config, [key]: !enabled })}
                      style={{ width: 44, height: 24, borderRadius: 12, background: enabled ? '#10B981' : '#CBD5E1', position: 'relative', cursor: 'pointer', transition: 'background 0.2s' }}>
                      <div style={{ width: 18, height: 18, borderRadius: '50%', background: 'white', position: 'absolute', top: 3, left: enabled ? 23 : 3, transition: 'left 0.2s', boxShadow: '0 1px 3px rgba(0,0,0,0.3)' }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* ── MAINTENANCE TAB ── */}
        {activeTab === 'maintenance' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertTriangle size={17} color="var(--warning)" />
              Maintenance Mode
            </h3>
            <p style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              When enabled, the checkout widget displays a maintenance message instead of the payment form.
            </p>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 14, border: `2px solid ${config.maintenance_mode ? 'var(--danger)' : 'var(--border-default)'}`, borderRadius: 10, marginBottom: 14 }}>
              <div>
                <div style={{ fontWeight: 700, color: config.maintenance_mode ? 'var(--danger)' : 'var(--text-primary)' }}>
                  Maintenance Mode is {config.maintenance_mode ? 'ACTIVE' : 'OFF'}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>Disables all payment processing for all merchants</div>
              </div>
              <div onClick={() => setConfig({ ...config, maintenance_mode: !config.maintenance_mode })}
                style={{ width: 50, height: 26, borderRadius: 13, background: config.maintenance_mode ? 'var(--danger)' : '#CBD5E1', position: 'relative', cursor: 'pointer', transition: 'background 0.2s' }}>
                <div style={{ width: 20, height: 20, borderRadius: '50%', background: 'white', position: 'absolute', top: 3, left: config.maintenance_mode ? 27 : 3, transition: 'left 0.2s', boxShadow: '0 1px 3px rgba(0,0,0,0.3)' }} />
              </div>
            </div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Maintenance Message (shown to customers)</label>
            <textarea className="input" value={config.maintenance_message} onChange={e => setConfig({ ...config, maintenance_message: e.target.value })}
              placeholder="We are currently undergoing scheduled maintenance. Payment processing will resume shortly."
              style={{ minHeight: 80, fontFamily: 'inherit' }} />
          </div>
        )}

        {/* ── HEALTH TAB ── */}
        {activeTab === 'health' && (
          <BackendHealthPanel backendUrl={backendUrl} getAuthHeaders={getAuthHeaders} />
        )}

        {/* Save Button (not shown for keys/health tabs) */}
        {activeTab !== 'keys' && activeTab !== 'health' && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={saving}
              style={{ padding: '10px 22px', fontSize: 13.5 }}
            >
              {saving ? (
                <>
                  <RefreshCw size={14} className="spin" />
                  Saving & Broadcasting...
                </>
              ) : (
                <>
                  <Save size={14} />
                  Save & Broadcast to Checkout Widgets
                </>
              )}
            </button>
          </div>
        )}
      </form>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-component: Backend Health Panel
// ─────────────────────────────────────────────────────────────────────────────
function BackendHealthPanel({ backendUrl, getAuthHeaders }: { backendUrl: string; getAuthHeaders: () => Record<string, string> }) {
  const [health, setHealth] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const checkHealth = async () => {
    setLoading(true);
    setHealth(null);
    try {
      const [backendRes, adminRes] = await Promise.allSettled([
        fetch(`${backendUrl}/healthz`),
        fetch(`${backendUrl}/v1/admin/health`, { headers: getAuthHeaders() }),
      ]);
      const backendData = backendRes.status === 'fulfilled' && backendRes.value.ok ? await backendRes.value.json() : { error: 'unreachable' };
      const adminData = adminRes.status === 'fulfilled' && adminRes.value.ok ? await adminRes.value.json() : { error: 'unreachable' };
      setHealth({ backend: backendData, services: adminData });
    } catch {
      setHealth({ error: 'Check failed' });
    } finally {
      setLoading(false);
    }
  };

  const statusColor = (v: string) => v === 'ok' ? 'var(--success)' : 'var(--danger)';

  return (
    <div className="card" style={{ marginBottom: 18 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Activity size={17} color="var(--brand-primary)" />
          SwapnoPay Backend Health
        </h3>
        <button type="button" className="btn btn-primary btn-sm" onClick={checkHealth} disabled={loading}>
          <RefreshCw size={13} className={loading ? 'spin' : ''} />
          {loading ? 'Checking...' : 'Check Now'}
        </button>
      </div>
      <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginBottom: 10 }}>Backend URL: <code>{backendUrl}</code></div>
      {health && !health.error && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[
            { label: 'SwapnoPay Backend', value: health.backend?.ok ? 'ok' : 'error', detail: `Socket.io clients: ${health.backend?.socket_io_clients ?? 'N/A'}` },
            { label: 'Admin Supabase Database', value: health.services?.supabase || 'ok' },
            { label: 'Gateway Receipt Service', value: health.services?.gateway_service || 'unknown' },
          ].map(({ label, value, detail }) => (
            <div key={label} style={{ padding: '10px 14px', border: '1px solid var(--border-default)', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <span style={{ fontWeight: 700, fontSize: 13 }}>{label}</span>
                {detail && <span style={{ fontSize: 11, color: 'var(--text-muted)', marginLeft: 8 }}>{detail}</span>}
              </div>
              <span style={{ fontWeight: 700, color: statusColor(value), textTransform: 'uppercase', fontSize: 12 }}>{value}</span>
            </div>
          ))}
        </div>
      )}
      {health?.error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, display: 'flex', alignItems: 'center', gap: 6 }}>
          <AlertTriangle size={15} />
          {health.error}
        </div>
      )}
      {!health && !loading && <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Click "Check Now" to test connectivity.</p>}
    </div>
  );
}
