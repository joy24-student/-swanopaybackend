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
  const { session } = useAuth();
  const backendUrl = localStorage.getItem('swapnopay_backend_url') || ((import.meta as any).env?.VITE_BACKEND_URL as string) || 'https://pay.swapnopay.top';
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
      setStatusMsg('✅ Gateway configuration saved to admin Supabase and synced to all checkout widgets!');
      setTimeout(() => setStatusMsg(''), 5000);
    } catch (err: any) {
      setStatusMsg('❌ Failed to save: ' + err.message);
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
    return <div className="container"><div className="card">Loading gateway configuration...</div></div>;
  }

  const tabs: { key: TabKey; label: string }[] = [
    { key: 'methods', label: '💳 Payment Methods' },
    { key: 'urls', label: '🔗 Redirect URLs' },
    { key: 'limits', label: '📊 Limits & Fees' },
    { key: 'keys', label: '🔑 API Keys' },
    { key: 'receipts', label: '📧 Receipts' },
    { key: 'maintenance', label: '🚧 Maintenance' },
    { key: 'health', label: '📡 Backend Health' },
  ];

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>⚙️ Payment Gateway Settings</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Controls payment methods, redirect URLs, limits, and API keys — synced to all checkout widgets in real-time.
          </p>
          {config.last_updated && (
            <p style={{ margin: '4px 0 0', color: '#94A3B8', fontSize: 11 }}>
              Last saved: {new Date(config.last_updated).toLocaleString()}
            </p>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to="/settings"><button className="button" style={{ background: '#7C3AED' }}>📋 System CMS</button></Link>
          <Link to="/dashboard"><button className="button" style={{ background: '#64748B' }}>Dashboard</button></Link>
        </div>
      </div>

      {/* Status Banner */}
      {statusMsg && (
        <div style={{
          padding: '12px 16px',
          background: statusMsg.startsWith('✅') ? '#ECFDF5' : '#FEF2F2',
          border: `1px solid ${statusMsg.startsWith('✅') ? '#10B981' : '#EF4444'}`,
          borderRadius: 8, color: statusMsg.startsWith('✅') ? '#065F46' : '#991B1B',
          marginBottom: 16, fontWeight: 600,
        }}>
          {statusMsg}
        </div>
      )}

      {/* Maintenance Banner */}
      {config.maintenance_mode && (
        <div style={{ padding: '10px 16px', background: '#FEF3C7', border: '1px solid #F59E0B', borderRadius: 8, marginBottom: 16, fontWeight: 600, color: '#92400E' }}>
          🚧 MAINTENANCE MODE ACTIVE — checkout widget is showing maintenance message to customers
        </div>
      )}

      {/* Tab Nav */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
        {tabs.map(tab => (
          <button key={tab.key} onClick={() => setActiveTab(tab.key)} className="button"
            style={{ background: activeTab === tab.key ? '#4F46E5' : '#E2E8F0', color: activeTab === tab.key ? 'white' : '#1E293B', fontWeight: 'bold', padding: '8px 14px', fontSize: 12.5 }}>
            {tab.label}
          </button>
        ))}
      </div>

      <form onSubmit={handleSave}>
        {/* ── PAYMENT METHODS TAB ── */}
        {activeTab === 'methods' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0 }}>💳 Enable / Disable Payment Methods</h3>
            <p style={{ fontSize: 12, color: '#64748B', marginBottom: 16 }}>
              Disabled methods are hidden from the checkout widget in real-time.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
              {(['bKash', 'Nagad', 'Rocket', 'Upay'] as const).map(method => {
                const enabled = config.enabled_methods[method];
                const colors: Record<string, string> = { bKash: '#E2125A', Nagad: '#EC5A24', Rocket: '#8C3494', Upay: '#10B981' };
                return (
                  <div key={method} onClick={() => setConfig({ ...config, enabled_methods: { ...config.enabled_methods, [method]: !enabled } })}
                    style={{ padding: 16, border: `2px solid ${enabled ? colors[method] : '#E2E8F0'}`, borderRadius: 12, background: enabled ? `${colors[method]}10` : '#F8FAFC',
                      cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'space-between', transition: 'all 0.2s' }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15, color: enabled ? colors[method] : '#64748B' }}>{method}</div>
                      <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 2 }}>
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
            <p style={{ fontSize: 11, color: '#94A3B8', marginTop: 12 }}>
              💡 Changes are saved to admin Supabase and synced to all checkout widgets.
            </p>
          </div>
        )}

        {/* ── REDIRECT URLS TAB ── */}
        {activeTab === 'urls' && (
          <div className="card" style={{ marginBottom: 18 }}>
            <h3 style={{ marginTop: 0 }}>🔗 Default Redirect URLs</h3>
            <p style={{ fontSize: 12, color: '#64748B', marginBottom: 16 }}>
              These are the fallback URLs used when a merchant hasn't configured their own. Must be HTTPS.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              {[
                { key: 'default_success_url', label: '✅ Success URL — redirected after PAID', placeholder: 'https://pay.swapnopay.top/success' },
                { key: 'default_fail_url', label: '❌ Failure URL — redirected on failed/expired payment', placeholder: 'https://pay.swapnopay.top/failed' },
                { key: 'default_cancel_url', label: '🚫 Cancel URL — redirected when customer cancels', placeholder: 'https://pay.swapnopay.top/cancelled' },
              ].map(({ key, label, placeholder }) => (
                <div key={key}>
                  <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>{label}</label>
                  <input className="input" value={(config as any)[key]} onChange={e => setConfig({ ...config, [key]: e.target.value })} placeholder={placeholder} />
                  <p style={{ fontSize: 10, color: '#94A3B8', margin: '4px 0 0' }}>
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
            <h3 style={{ marginTop: 0 }}>📊 Transaction Limits & Gateway Fees</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Minimum Amount (BDT)</label>
                <input className="input" type="number" value={config.min_amount} onChange={e => setConfig({ ...config, min_amount: Number(e.target.value) })} min={1} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Maximum Amount (BDT)</label>
                <input className="input" type="number" value={config.max_amount} onChange={e => setConfig({ ...config, max_amount: Number(e.target.value) })} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Daily Limit Per Merchant (BDT)</label>
                <input className="input" type="number" value={config.daily_limit_per_merchant} onChange={e => setConfig({ ...config, daily_limit_per_merchant: Number(e.target.value) })} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Payment Timeout (seconds)</label>
                <input className="input" type="number" value={config.payment_timeout_seconds} onChange={e => setConfig({ ...config, payment_timeout_seconds: Number(e.target.value) })} min={60} max={3600} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Processing Verification Timeout (seconds)</label>
                <input className="input" type="number" value={config.processing_timeout_seconds} onChange={e => setConfig({ ...config, processing_timeout_seconds: Number(e.target.value) })} min={30} max={600} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Gateway Fee % (e.g., 1.5 = 1.5%)</label>
                <input className="input" type="number" step="0.01" value={config.gateway_fee_percent} onChange={e => setConfig({ ...config, gateway_fee_percent: Number(e.target.value) })} min={0} max={10} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4, color: '#334155' }}>Gateway Fixed Fee (BDT)</label>
                <input className="input" type="number" step="0.01" value={config.gateway_fee_fixed} onChange={e => setConfig({ ...config, gateway_fee_fixed: Number(e.target.value) })} min={0} />
              </div>
            </div>
          </div>
        )}

        {/* ── API KEYS TAB ── */}
        {activeTab === 'keys' && (
          <div>
            <div className="card" style={{ marginBottom: 16 }}>
              <h3 style={{ marginTop: 0 }}>🔑 Generate Merchant API Key</h3>
              <p style={{ fontSize: 12, color: '#64748B' }}>
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
              <button type="button" className="button" onClick={handleGenerateKey} disabled={keyLoading}
                style={{ background: '#10B981', marginTop: 12 }}>
                {keyLoading ? 'Generating...' : '🔑 Generate New API Key'}
              </button>

              {generatedKey && (
                <div style={{ marginTop: 14, padding: 14, background: '#FFFBEB', border: '2px solid #F59E0B', borderRadius: 10 }}>
                  <p style={{ fontSize: 12, fontWeight: 700, color: '#92400E', margin: '0 0 8px' }}>
                    ⚠️ COPY THIS KEY NOW — it will never be shown again!
                  </p>
                  <code style={{ display: 'block', padding: '10px 14px', background: '#FEF3C7', borderRadius: 8, fontSize: 13, wordBreak: 'break-all', color: '#78350F', fontWeight: 700 }}>
                    {generatedKey}
                  </code>
                  <button type="button" onClick={() => { navigator.clipboard.writeText(generatedKey); alert('API key copied!'); }}
                    style={{ marginTop: 8, padding: '6px 12px', background: '#F59E0B', border: 'none', borderRadius: 6, cursor: 'pointer', fontWeight: 700, fontSize: 12 }}>
                    📋 Copy to Clipboard
                  </button>
                </div>
              )}
            </div>

            {/* Key List */}
            <div className="card" style={{ marginBottom: 16 }}>
              <h3 style={{ marginTop: 0 }}>📋 All API Keys ({apiKeys.length})</h3>
              {apiKeys.length === 0 ? (
                <p style={{ color: '#94A3B8', fontSize: 13 }}>No API keys generated yet.</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {apiKeys.map(key => (
                    <div key={key.id} style={{ padding: 12, border: `1px solid ${key.revoked ? '#FEE2E2' : '#E2E8F0'}`, borderRadius: 8, background: key.revoked ? '#FEF2F2' : 'white', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 13, color: key.revoked ? '#EF4444' : '#1E293B' }}>
                          {key.label} {key.revoked && <span style={{ background: '#FEE2E2', padding: '2px 6px', borderRadius: 4, fontSize: 10 }}>REVOKED</span>}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748B' }}>Merchant: {key.merchant_name} ({key.merchant_id})</div>
                        <div style={{ fontSize: 11, color: '#94A3B8' }}>Key: <code>{key.key_preview}</code> • Created: {new Date(key.created_at).toLocaleDateString()}</div>
                      </div>
                      {!key.revoked && (
                        <button type="button" onClick={() => handleRevokeKey(key.id)}
                          style={{ background: '#FEE2E2', color: '#EF4444', border: 'none', padding: '6px 12px', borderRadius: 6, cursor: 'pointer', fontSize: 11, fontWeight: 700 }}>
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
            <h3 style={{ marginTop: 0 }}>📧 Email Receipt Settings</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {[
                { key: 'customer_receipts_enabled', label: '📨 Send receipt to customer email after payment', description: 'Customer receives a payment confirmation email via the gateway-service.' },
                { key: 'merchant_receipts_enabled', label: '📬 Send receipt to merchant email after payment', description: 'Merchant receives a payment notification email via the gateway-service.' },
              ].map(({ key, label, description }) => {
                const enabled = (config as any)[key] as boolean;
                return (
                  <div key={key} style={{ padding: 14, border: '1px solid #E2E8F0', borderRadius: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 13 }}>{label}</div>
                      <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{description}</div>
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
            <h3 style={{ marginTop: 0 }}>🚧 Maintenance Mode</h3>
            <p style={{ fontSize: 12, color: '#64748B' }}>
              When enabled, the checkout widget displays a maintenance message instead of the payment form.
            </p>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 14, border: `2px solid ${config.maintenance_mode ? '#EF4444' : '#E2E8F0'}`, borderRadius: 10, marginBottom: 14 }}>
              <div>
                <div style={{ fontWeight: 700, color: config.maintenance_mode ? '#EF4444' : '#1E293B' }}>
                  🚧 Maintenance Mode is {config.maintenance_mode ? 'ACTIVE' : 'OFF'}
                </div>
                <div style={{ fontSize: 11, color: '#64748B' }}>Disables all payment processing for all merchants</div>
              </div>
              <div onClick={() => setConfig({ ...config, maintenance_mode: !config.maintenance_mode })}
                style={{ width: 50, height: 26, borderRadius: 13, background: config.maintenance_mode ? '#EF4444' : '#CBD5E1', position: 'relative', cursor: 'pointer', transition: 'background 0.2s' }}>
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
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
            <button type="submit" className="button" disabled={saving}
              style={{ background: '#10B981', padding: '12px 24px', fontSize: 14, fontWeight: 'bold' }}>
              {saving ? 'Saving...' : '💾 Save & Broadcast to All Checkout Widgets'}
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

  const statusColor = (v: string) => v === 'ok' ? '#10B981' : '#EF4444';

  return (
    <div className="card" style={{ marginBottom: 18 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <h3 style={{ margin: 0 }}>📡 SwapnoPay Backend Health</h3>
        <button type="button" className="button" onClick={checkHealth} disabled={loading} style={{ background: '#4F46E5' }}>
          {loading ? 'Checking...' : '🔄 Check Now'}
        </button>
      </div>
      <div style={{ fontSize: 12, color: '#64748B', marginBottom: 10 }}>Backend URL: <code>{backendUrl}</code></div>
      {health && !health.error && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[
            { label: 'SwapnoPay Backend', value: health.backend?.ok ? 'ok' : 'error', detail: `Socket.io clients: ${health.backend?.socket_io_clients ?? 'N/A'}` },
            { label: 'Admin Supabase Database', value: health.services?.supabase || 'ok' },
            { label: 'Gateway Receipt Service', value: health.services?.gateway_service || 'unknown' },
          ].map(({ label, value, detail }) => (
            <div key={label} style={{ padding: '10px 14px', border: '1px solid #E2E8F0', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <span style={{ fontWeight: 700, fontSize: 13 }}>{label}</span>
                {detail && <span style={{ fontSize: 11, color: '#94A3B8', marginLeft: 8 }}>{detail}</span>}
              </div>
              <span style={{ fontWeight: 700, color: statusColor(value), textTransform: 'uppercase', fontSize: 12 }}>{value}</span>
            </div>
          ))}
        </div>
      )}
      {health?.error && <div style={{ color: '#EF4444', fontSize: 13 }}>❌ {health.error}</div>}
      {!health && !loading && <p style={{ color: '#94A3B8', fontSize: 13 }}>Click "Check Now" to test connectivity.</p>}
    </div>
  );
}
