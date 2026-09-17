import React, { useEffect, useState } from 'react';
import { adminSupabase, reviewMerchantIdentity } from '../adminSupabaseClient';
import { useParams, Link } from 'react-router-dom';
import {
  User,
  Key,
  ShieldCheck,
  Check,
  X,
  FileText,
  Maximize2,
  CreditCard,
  Link2,
  Activity,
  ArrowLeft,
  Building,
  Save,
  AlertTriangle,
} from 'lucide-react';

export default function MerchantDetail() {
  const { id } = useParams();
  const [merchant, setMerchant] = useState<any | null>(null);
  const [apiKeys, setApiKeys] = useState<any[]>([]);
  const [recentEvents, setRecentEvents] = useState<any[]>([]);
  const [publicEndpoint, setPublicEndpoint] = useState('');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [testResult, setTestResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [kycProcessing, setKycProcessing] = useState(false);
  const [kycFeedback, setKycFeedback] = useState<string | null>(null);
  const [previewImage, setPreviewImage] = useState<string | null>(null);
  const [rejectionReasonInput, setRejectionReasonInput] = useState('');
  const [showRejectBox, setShowRejectBox] = useState(false);

  useEffect(() => {
    if (!id) return;
    (async () => {
      let mData: any = null;

      // 1. Load merchant from Admin Supabase
      try {
        const { data } = await adminSupabase
          .from('merchants')
          .select('*')
          .eq('id', id)
          .single();
        if (data) mData = data;
      } catch (e) {
        console.warn('[MerchantDetail] Supabase fetch error:', e);
      }

      setMerchant(mData);

      // 2. Load merchant API keys from Admin Supabase
      try {
        const { data: keys } = await adminSupabase
          .from('platform_api_keys')
          .select('*')
          .eq('merchant_id', id);
        if (keys) setApiKeys(keys);
      } catch (e) {
        console.warn('[MerchantDetail] API keys fetch error:', e);
      }

      // 3. Load recent payment events for this merchant
      try {
        const { data: events } = await adminSupabase
          .from('payment_events')
          .select('*')
          .eq('merchant_id', id)
          .order('recorded_at', { ascending: false })
          .limit(10);
        if (events) setRecentEvents(events);
      } catch (e) {
        console.warn('[MerchantDetail] Events fetch error:', e);
      }

      // 4. Load connection settings from Admin Supabase
      try {
        const { data: conn } = await adminSupabase
          .from('merchant_connections')
          .select('*')
          .eq('merchant_id', id)
          .single();
        if (conn) {
          setPublicEndpoint(conn.public_endpoint || '');
          setWebhookUrl(conn.webhook_url || '');
        }
      } catch (e) {
        console.warn('[MerchantDetail] Connections fetch error:', e);
      }

      setLoading(false);
    })();
  }, [id]);

  const handleSaveConnection = async () => {
    if (!id) return;
    setTestResult('Saving connections...');
    try {
      // Upsert into Admin Supabase merchant_connections
      const { error } = await adminSupabase
        .from('merchant_connections')
        .upsert({
          merchant_id: id,
          public_endpoint: publicEndpoint.trim() || null,
          webhook_url: webhookUrl.trim() || null,
          updated_at: new Date().toISOString(),
        }, { onConflict: 'merchant_id' });

      if (error) throw error;

      setTestResult('Connection settings saved successfully.');
    } catch (err: any) {
      setTestResult('Save error: ' + err.message);
    }
  };

  const formatDocUrl = (url: string | null | undefined) => {
    if (!url) return null;
    if (url.startsWith('http') || url.startsWith('data:')) return url;
    const backend = (import.meta as any).env?.VITE_BACKEND_URL || 'https://api.swapnopay.top';
    return `${backend.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
  };

  const handleReviewKyc = async (action: 'APPROVE' | 'REJECT') => {
    if (!id) return;
    if (action === 'REJECT' && !rejectionReasonInput.trim()) {
      alert('Please provide a reason for rejecting the KYC verification.');
      return;
    }
    setKycProcessing(true);
    setKycFeedback(null);
    try {
      const now = new Date().toISOString();
      const nextKycStatus = action === 'APPROVE' ? 'VERIFIED' : 'REJECTED';
      const updateData: any = {
        kyc_status: nextKycStatus,
        kyc_reviewed_at: now,
        kyc_reviewed_by: 'ADMIN',
        updated_at: now,
      };
      if (action === 'APPROVE') {
        updateData.status = 'ACTIVE';
      } else {
        updateData.kyc_rejection_reason = rejectionReasonInput.trim();
      }

      // Update Supabase
      const { error } = await adminSupabase
        .from('merchants')
        .update(updateData)
        .eq('id', id);

      if (error) throw error;

      // Also call backend review endpoint if available (use helper to include auth)
      try {
        await reviewMerchantIdentity(id, action, rejectionReasonInput.trim());
      } catch (e) {
      } catch (e: any) {
        console.warn('[MerchantDetail] backend KYC review failed:', e?.message || e)
      }

      setMerchant((prev: any) => ({ ...prev, ...updateData }));
      setKycFeedback(`KYC verification successfully ${action === 'APPROVE' ? 'approved and verified' : 'rejected'}.`);
      setShowRejectBox(false);
      setRejectionReasonInput('');
    } catch (err: any) {
      setKycFeedback('KYC update error: ' + err.message);
    } finally {
      setKycProcessing(false);
    }
  };

  if (loading || !merchant) {
    return <div className="container"><div className="card">Loading merchant profile...</div></div>;
  }

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>Merchant: {merchant.business_name || merchant.title || merchant.name}</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 12 }}>ID: {merchant.id}</p>
        </div>
        <div>
          <Link to="/merchants"><button className="button">Back to List</button></Link>
        </div>
      </div>

      {/* Overview Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 14, marginBottom: 14 }}>
        <div className="card">
          <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
            <User size={16} color="var(--brand-primary)" />
            Merchant Profile
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, fontSize: 13 }}>
            <div><strong>Business Name:</strong> {merchant.business_name || merchant.name || '--'}</div>
            <div><strong>Email:</strong> {merchant.email || '--'}</div>
            <div><strong>Phone:</strong> {merchant.phone || '--'}</div>
            <div><strong>Business Type:</strong> {merchant.business_type || 'RETAIL'}</div>
            <div><strong>Subscription Tier:</strong> <span style={{ fontWeight: 700, color: '#7E22CE' }}>{merchant.subscription_tier || 'STARTER'}</span></div>
            <div><strong>Status:</strong> <span style={{ fontWeight: 700, color: merchant.status === 'SUSPENDED' ? 'var(--danger)' : 'var(--success)' }}>{merchant.status || 'ACTIVE'}</span></div>
            <div><strong>Webhook Secret:</strong> <code style={{ fontSize: 11 }}>{merchant.webhook_secret || '--'}</code></div>
            <div><strong>Created:</strong> {merchant.created_at ? new Date(merchant.created_at).toLocaleDateString() : '--'}</div>
          </div>
        </div>

        <div className="card">
          <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
            <Key size={16} color="var(--brand-primary)" />
            Active API Keys ({apiKeys.length})
          </h3>
          {apiKeys.length === 0 ? (
            <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>No API keys generated yet for this merchant.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {apiKeys.map(k => (
                <div key={k.id} style={{ padding: 8, background: 'var(--bg-subtle)', borderRadius: 6, border: '1px solid var(--border-default)', fontSize: 12 }}>
                  <div style={{ fontWeight: 700 }}>{k.label}</div>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}><code>{k.key_preview}</code></div>
                  <div style={{ fontSize: 10, color: k.revoked ? 'var(--danger)' : 'var(--success)', fontWeight: 700 }}>{k.revoked ? 'REVOKED' : 'ACTIVE'}</div>
                </div>
              ))}
            </div>
          )}
          <Link to="/gateway-settings">
            <button className="btn btn-secondary btn-sm" style={{ marginTop: 10, width: '100%' }}>Manage API Keys</button>
          </Link>
        </div>
      </div>

      {/* ── KYC Identity & Biometric Verification Card ── */}
      <div className="card" style={{ marginBottom: 14, border: merchant.kyc_status === 'PENDING' ? '1.5px solid var(--warning)' : undefined }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 14 }}>
          <div>
            <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              <ShieldCheck size={18} color="var(--brand-primary)" />
              KYC Identity & Biometric Verification
              <span className={`status-pill ${
                merchant.kyc_status === 'VERIFIED' ? 'success' :
                merchant.kyc_status === 'REJECTED' ? 'danger' :
                merchant.kyc_status === 'PENDING' ? 'warning' : 'neutral'
              }`} style={{ fontSize: 11 }}>
                <span className="status-dot" />
                {merchant.kyc_status === 'VERIFIED' ? 'VERIFIED' :
                 merchant.kyc_status === 'REJECTED' ? 'REJECTED' :
                 merchant.kyc_status === 'PENDING' ? 'PENDING REVIEW' : 'UNVERIFIED'}
              </span>
            </h3>
            <p style={{ margin: '4px 0 0', fontSize: 12, color: 'var(--text-secondary)' }}>
              Bangladesh NID Card Document OCR & ML Kit Biometric Live Face Verification
            </p>
          </div>

          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {merchant.kyc_status !== 'VERIFIED' && (
              <button
                className="btn btn-primary btn-sm"
                disabled={kycProcessing}
                onClick={() => handleReviewKyc('APPROVE')}
              >
                <Check size={13} />
                {kycProcessing ? 'Processing...' : 'Approve KYC'}
              </button>
            )}
            {merchant.kyc_status !== 'REJECTED' && !showRejectBox && (
              <button
                className="btn btn-danger btn-sm"
                disabled={kycProcessing}
                onClick={() => setShowRejectBox(true)}
              >
                <X size={13} />
                Reject KYC
              </button>
            )}
          </div>
        </div>

        {kycFeedback && (
          <div style={{ padding: '8px 12px', background: 'var(--bg-subtle)', borderRadius: 6, marginBottom: 12, fontSize: 12, fontWeight: 600, border: '1px solid var(--border-default)' }}>
            {kycFeedback}
          </div>
        )}

        {showRejectBox && (
          <div style={{ padding: 12, background: 'var(--danger-subtle)', border: '1px solid var(--danger-border)', borderRadius: 8, marginBottom: 14 }}>
            <h4 style={{ margin: '0 0 8px', color: 'var(--danger)', fontSize: 13 }}>Specify KYC Rejection Reason</h4>
            <input
              className="input"
              value={rejectionReasonInput}
              onChange={e => setRejectionReasonInput(e.target.value)}
              placeholder="e.g. Blurry NID card image, numbers unreadable, or face mismatch..."
              style={{ marginBottom: 8, background: 'var(--bg-surface)' }}
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="btn btn-danger btn-sm"
                disabled={kycProcessing}
                onClick={() => handleReviewKyc('REJECT')}
              >
                Confirm Rejection
              </button>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setShowRejectBox(false)}
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* NID Data Fields */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, padding: 12, background: 'var(--bg-subtle)', borderRadius: 8, border: '1px solid var(--border-default)', marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>National ID Number</div>
            <div style={{ fontSize: 15, fontWeight: 800, color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', marginTop: 2 }}>
              {merchant.nid_number || '--'}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>Name on NID</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', marginTop: 2 }}>
              {merchant.nid_name || merchant.business_name || '--'}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>Date of Birth</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', marginTop: 2 }}>
              {merchant.nid_dob || '--'}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>Submitted At</div>
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 2 }}>
              {merchant.kyc_submitted_at ? new Date(merchant.kyc_submitted_at).toLocaleString() : '--'}
            </div>
          </div>
          {merchant.kyc_rejection_reason && (
            <div style={{ gridColumn: '1 / -1', color: 'var(--danger)', fontSize: 12, background: 'var(--danger-subtle)', padding: 8, borderRadius: 6, border: '1px solid var(--danger-border)' }}>
              <strong>Rejection Reason:</strong> {merchant.kyc_rejection_reason}
            </div>
          )}
        </div>

        {/* Document Photos Inspection Gallery */}
        <div>
          <h4 style={{ margin: '0 0 10px', fontSize: 13, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: 7 }}>
            <FileText size={15} color="var(--brand-primary)" />
            Document & Biometric Scan Evidence
          </h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
            {/* Front Document */}
            <div style={{ border: '1px solid var(--border-default)', borderRadius: 8, overflow: 'hidden', background: 'var(--bg-surface)' }}>
              <div style={{ padding: '8px 10px', background: 'var(--bg-subtle)', fontSize: 12, fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                <span>NID Front Document</span>
                <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>Card Front</span>
              </div>
              <div style={{ height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0F172A', cursor: 'pointer', overflow: 'hidden' }}
                   onClick={() => formatDocUrl(merchant.nid_front_url) && setPreviewImage(formatDocUrl(merchant.nid_front_url))}>
                {formatDocUrl(merchant.nid_front_url) ? (
                  <img src={formatDocUrl(merchant.nid_front_url)!} alt="NID Front" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                ) : (
                  <div style={{ color: 'var(--text-muted)', fontSize: 12, textAlign: 'center', padding: 10 }}>
                    No Front Document Uploaded
                  </div>
                )}
              </div>
              {formatDocUrl(merchant.nid_front_url) && (
                <div style={{ padding: 6, textAlign: 'center' }}>
                  <button className="btn btn-secondary btn-sm" style={{ fontSize: 11 }} onClick={() => setPreviewImage(formatDocUrl(merchant.nid_front_url))}>
                    <Maximize2 size={11} />
                    View Fullscreen
                  </button>
                </div>
              )}
            </div>

            {/* Back Document */}
            <div style={{ border: '1px solid var(--border-default)', borderRadius: 8, overflow: 'hidden', background: 'var(--bg-surface)' }}>
              <div style={{ padding: '8px 10px', background: 'var(--bg-subtle)', fontSize: 12, fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                <span>NID Back Document</span>
                <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>Card Back</span>
              </div>
              <div style={{ height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0F172A', cursor: 'pointer', overflow: 'hidden' }}
                   onClick={() => formatDocUrl(merchant.nid_back_url) && setPreviewImage(formatDocUrl(merchant.nid_back_url))}>
                {formatDocUrl(merchant.nid_back_url) ? (
                  <img src={formatDocUrl(merchant.nid_back_url)!} alt="NID Back" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                ) : (
                  <div style={{ color: 'var(--text-muted)', fontSize: 12, textAlign: 'center', padding: 10 }}>
                    No Back Document Uploaded
                  </div>
                )}
              </div>
              {formatDocUrl(merchant.nid_back_url) && (
                <div style={{ padding: 6, textAlign: 'center' }}>
                  <button className="btn btn-secondary btn-sm" style={{ fontSize: 11 }} onClick={() => setPreviewImage(formatDocUrl(merchant.nid_back_url))}>
                    <Maximize2 size={11} />
                    View Fullscreen
                  </button>
                </div>
              )}
            </div>

            {/* Face Biometric Selfie */}
            <div style={{ border: '1px solid var(--border-default)', borderRadius: 8, overflow: 'hidden', background: 'var(--bg-surface)' }}>
              <div style={{ padding: '8px 10px', background: 'var(--bg-subtle)', fontSize: 12, fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                <span>Biometric Face Scan</span>
                <span style={{ fontSize: 10, color: 'var(--success)', fontWeight: 700 }}>ML Kit Live</span>
              </div>
              <div style={{ height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0F172A', cursor: 'pointer', overflow: 'hidden' }}
                   onClick={() => formatDocUrl(merchant.face_photo_url) && setPreviewImage(formatDocUrl(merchant.face_photo_url))}>
                {formatDocUrl(merchant.face_photo_url) ? (
                  <img src={formatDocUrl(merchant.face_photo_url)!} alt="Biometric Face Selfie" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                ) : (
                  <div style={{ color: 'var(--text-muted)', fontSize: 12, textAlign: 'center', padding: 10 }}>
                    No Face Scan Captured
                  </div>
                )}
              </div>
              {formatDocUrl(merchant.face_photo_url) && (
                <div style={{ padding: 6, textAlign: 'center' }}>
                  <button className="btn btn-secondary btn-sm" style={{ fontSize: 11 }} onClick={() => setPreviewImage(formatDocUrl(merchant.face_photo_url))}>
                    <Maximize2 size={11} />
                    View Fullscreen
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Image Fullscreen Preview Modal */}
        {previewImage && (
          <div
            style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999, padding: 20 }}
            onClick={() => setPreviewImage(null)}
          >
            <div style={{ maxWidth: '90vw', maxHeight: '90vh', position: 'relative' }} onClick={e => e.stopPropagation()}>
              <img src={previewImage} alt="Document Preview" style={{ maxWidth: '100%', maxHeight: '85vh', borderRadius: 8, boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.5)' }} />
              <button
                className="btn btn-danger btn-icon"
                style={{ position: 'absolute', top: -12, right: -12, borderRadius: '50%', width: 34, height: 34 }}
                onClick={() => setPreviewImage(null)}
              >
                <X size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Recent Payments */}
      <div className="card" style={{ marginBottom: 14 }}>
        <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
          <CreditCard size={16} color="var(--brand-primary)" />
          Recent Payment Events ({recentEvents.length})
        </h3>
        {recentEvents.length === 0 ? (
          <p style={{ fontSize: 12, color: 'var(--text-muted)' }}>No payment transactions recorded for this merchant yet.</p>
        ) : (
          <div className="enterprise-table-container">
            <table className="enterprise-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Order ID</th>
                  <th>Amount</th>
                  <th>Method</th>
                  <th>Status</th>
                  <th>Trx ID</th>
                </tr>
              </thead>
              <tbody>
                {recentEvents.map(e => (
                  <tr key={e.id}>
                    <td style={{ fontSize: 11 }}>{new Date(e.recorded_at).toLocaleString()}</td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>#{e.order_id?.slice(0, 10)}...</td>
                    <td style={{ fontWeight: 700 }}>৳{Number(e.amount || 0).toLocaleString()}</td>
                    <td>{e.payment_method || 'MFS'}</td>
                    <td>
                      <span className={`status-pill ${e.status === 'PAID' ? 'success' : 'danger'}`}>
                        <span className="status-dot" />
                        {e.status}
                      </span>
                    </td>
                    <td style={{ fontFamily: 'var(--font-mono)', fontSize: 11 }}>{e.trx_id || '--'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Connections & Webhooks */}
      <div className="card">
        <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
          <Link2 size={16} color="var(--brand-primary)" />
          Connection Settings & Webhook Relays
        </h3>
        <p style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
          Configure custom callback endpoints and webhook listeners for this merchant.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Public Checkout Endpoint (GET)</label>
            <input
              className="input"
              value={publicEndpoint}
              onChange={e => setPublicEndpoint(e.target.value)}
              placeholder="https://merchant-store.com/checkout/form"
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Merchant Webhook Listener URL (POST)</label>
            <input
              className="input"
              value={webhookUrl}
              onChange={e => setWebhookUrl(e.target.value)}
              placeholder="https://merchant-store.com/api/webhooks/swapnopay"
            />
            <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>
              SwapnoPay backend sends HMAC-SHA256 signed payment payloads to this URL on successful payment verification.
            </p>
          </div>

          <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
            <button className="btn btn-primary btn-sm" onClick={handleSaveConnection}>
              <Save size={13} />
              Save Connection Settings
            </button>
            {webhookUrl && (
              <button
                className="btn btn-secondary btn-sm"
                onClick={async () => {
                  setTestResult('Sending test webhook ping...');
                  try {
                    const res = await fetch(webhookUrl, {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ event: 'TEST_PING', merchant_id: id, timestamp: new Date().toISOString() })
                    });
                    setTestResult(`Webhook response HTTP ${res.status}`);
                  } catch (err: any) {
                    setTestResult('Test webhook error: ' + err.message);
                  }
                }}
              >
                <Activity size={13} />
                Test Webhook Ping
              </button>
            )}
          </div>

          {testResult && (
            <div style={{ padding: 10, background: 'var(--bg-subtle)', borderRadius: 6, fontSize: 12, fontWeight: 600, border: '1px solid var(--border-default)' }}>
              {testResult}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
