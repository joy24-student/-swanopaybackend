import React, { useEffect, useState } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { useParams, Link } from 'react-router-dom';

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

      setTestResult('✅ Connection settings saved successfully');
    } catch (err: any) {
      setTestResult('❌ Save error: ' + err.message);
    }
  };

  const formatDocUrl = (url: string | null | undefined) => {
    if (!url) return null;
    if (url.startsWith('http') || url.startsWith('data:')) return url;
    const backend = (import.meta as any).env?.VITE_BACKEND_URL || 'https://pay.swapnopay.top';
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

      // Also call backend review endpoint if available
      try {
        const backendUrl = (import.meta as any).env?.VITE_BACKEND_URL || 'https://pay.swapnopay.top';
        await fetch(`${backendUrl}/v1/admin/kyc/${id}/review`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ action, reason: rejectionReasonInput.trim() }),
        });
      } catch (_e) {}

      setMerchant((prev: any) => ({ ...prev, ...updateData }));
      setKycFeedback(`✅ KYC verification successfully ${action === 'APPROVE' ? 'approved & verified' : 'rejected'}.`);
      setShowRejectBox(false);
      setRejectionReasonInput('');
    } catch (err: any) {
      setKycFeedback('❌ KYC update error: ' + err.message);
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
          <h3 style={{ marginTop: 0 }}>📋 Merchant Profile</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, fontSize: 13 }}>
            <div><strong>Business Name:</strong> {merchant.business_name || merchant.name || '--'}</div>
            <div><strong>Email:</strong> {merchant.email || '--'}</div>
            <div><strong>Phone:</strong> {merchant.phone || '--'}</div>
            <div><strong>Business Type:</strong> {merchant.business_type || 'RETAIL'}</div>
            <div><strong>Subscription Tier:</strong> <span style={{ fontWeight: 700, color: '#7E22CE' }}>{merchant.subscription_tier || 'STARTER'}</span></div>
            <div><strong>Status:</strong> <span style={{ fontWeight: 700, color: merchant.status === 'SUSPENDED' ? '#EF4444' : '#10B981' }}>{merchant.status || 'ACTIVE'}</span></div>
            <div><strong>Webhook Secret:</strong> <code style={{ fontSize: 11 }}>{merchant.webhook_secret || '--'}</code></div>
            <div><strong>Created:</strong> {merchant.created_at ? new Date(merchant.created_at).toLocaleDateString() : '--'}</div>
          </div>
        </div>

        <div className="card">
          <h3 style={{ marginTop: 0 }}>🔑 Active API Keys ({apiKeys.length})</h3>
          {apiKeys.length === 0 ? (
            <p style={{ fontSize: 12, color: '#94A3B8' }}>No API keys generated yet for this merchant.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {apiKeys.map(k => (
                <div key={k.id} style={{ padding: 8, background: '#F8FAFC', borderRadius: 6, border: '1px solid #E2E8F0', fontSize: 12 }}>
                  <div style={{ fontWeight: 700 }}>{k.label}</div>
                  <div style={{ fontSize: 11, color: '#64748B' }}><code>{k.key_preview}</code></div>
                  <div style={{ fontSize: 10, color: k.revoked ? '#EF4444' : '#10B981' }}>{k.revoked ? 'REVOKED' : 'ACTIVE'}</div>
                </div>
              ))}
            </div>
          )}
          <Link to="/gateway-settings">
            <button className="button" style={{ fontSize: 11, marginTop: 10, background: '#4F46E5', width: '100%' }}>Manage API Keys →</button>
          </Link>
        </div>
      </div>

      {/* ── KYC Identity & Biometric Verification Card ── */}
      <div className="card" style={{ marginBottom: 14, border: merchant.kyc_status === 'PENDING' ? '1.5px solid #F59E0B' : undefined }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 14 }}>
          <div>
            <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
              🛡️ KYC Identity & Biometric Verification
              <span style={{
                padding: '3px 10px', borderRadius: 20, fontSize: 11, fontWeight: 800,
                background: merchant.kyc_status === 'VERIFIED' ? '#ECFDF5' :
                            merchant.kyc_status === 'REJECTED' ? '#FEF2F2' :
                            merchant.kyc_status === 'PENDING' ? '#FEF3C7' : '#F1F5F9',
                color: merchant.kyc_status === 'VERIFIED' ? '#065F46' :
                       merchant.kyc_status === 'REJECTED' ? '#991B1B' :
                       merchant.kyc_status === 'PENDING' ? '#92400E' : '#64748B',
                border: merchant.kyc_status === 'PENDING' ? '1px solid #F59E0B' : 'none',
              }}>
                {merchant.kyc_status === 'VERIFIED' ? '✅ VERIFIED' :
                 merchant.kyc_status === 'REJECTED' ? '❌ REJECTED' :
                 merchant.kyc_status === 'PENDING' ? '⏳ PENDING REVIEW' : '⚪ UNVERIFIED'}
              </span>
            </h3>
            <p style={{ margin: '4px 0 0', fontSize: 12, color: '#64748B' }}>
              Real Bangladesh NID Card Document OCR & ML Kit Biometric Live Face Verification
            </p>
          </div>

          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            {merchant.kyc_status !== 'VERIFIED' && (
              <button
                className="button"
                disabled={kycProcessing}
                onClick={() => handleReviewKyc('APPROVE')}
                style={{ background: '#10B981', fontWeight: 700 }}
              >
                {kycProcessing ? 'Processing...' : '✅ Approve KYC'}
              </button>
            )}
            {merchant.kyc_status !== 'REJECTED' && !showRejectBox && (
              <button
                className="button"
                disabled={kycProcessing}
                onClick={() => setShowRejectBox(true)}
                style={{ background: '#EF4444', fontWeight: 700 }}
              >
                ❌ Reject KYC
              </button>
            )}
          </div>
        </div>

        {kycFeedback && (
          <div style={{ padding: '8px 12px', background: '#F8FAFC', borderRadius: 6, marginBottom: 12, fontSize: 12, fontWeight: 600, border: '1px solid #E2E8F0' }}>
            {kycFeedback}
          </div>
        )}

        {showRejectBox && (
          <div style={{ padding: 12, background: '#FEF2F2', border: '1px solid #FCA5A5', borderRadius: 8, marginBottom: 14 }}>
            <h4 style={{ margin: '0 0 8px', color: '#991B1B', fontSize: 13 }}>Specify KYC Rejection Reason</h4>
            <input
              className="input"
              value={rejectionReasonInput}
              onChange={e => setRejectionReasonInput(e.target.value)}
              placeholder="e.g. Blurry NID card image, numbers unreadable, or face mismatch..."
              style={{ marginBottom: 8, background: '#FFFFFF' }}
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="button"
                style={{ background: '#EF4444', fontSize: 12 }}
                disabled={kycProcessing}
                onClick={() => handleReviewKyc('REJECT')}
              >
                Confirm Rejection
              </button>
              <button
                className="button"
                style={{ background: '#64748B', fontSize: 12 }}
                onClick={() => setShowRejectBox(false)}
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* NID Data Fields */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, padding: 12, background: '#F8FAFC', borderRadius: 8, border: '1px solid #E2E8F0', marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 11, color: '#64748B', textTransform: 'uppercase', fontWeight: 700 }}>National ID Number</div>
            <div style={{ fontSize: 15, fontWeight: 800, color: '#1E293B', fontFamily: 'monospace', marginTop: 2 }}>
              {merchant.nid_number || '--'}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: '#64748B', textTransform: 'uppercase', fontWeight: 700 }}>Name on NID</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#1E293B', marginTop: 2 }}>
              {merchant.nid_name || merchant.business_name || '--'}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: '#64748B', textTransform: 'uppercase', fontWeight: 700 }}>Date of Birth</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#1E293B', marginTop: 2 }}>
              {merchant.nid_dob || '--'}
            </div>
          </div>
          <div>
            <div style={{ fontSize: 11, color: '#64748B', textTransform: 'uppercase', fontWeight: 700 }}>Submitted At</div>
            <div style={{ fontSize: 13, color: '#475569', marginTop: 2 }}>
              {merchant.kyc_submitted_at ? new Date(merchant.kyc_submitted_at).toLocaleString() : '--'}
            </div>
          </div>
          {merchant.kyc_rejection_reason && (
            <div style={{ gridColumn: '1 / -1', color: '#B91C1C', fontSize: 12, background: '#FEF2F2', padding: 8, borderRadius: 6 }}>
              <strong>Rejection Reason:</strong> {merchant.kyc_rejection_reason}
            </div>
          )}
        </div>

        {/* Document Photos Inspection Gallery */}
        <div>
          <h4 style={{ margin: '0 0 10px', fontSize: 13, color: '#334155' }}>📸 Document & Biometric Scan Evidence</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14 }}>
            {/* Front Document */}
            <div style={{ border: '1px solid #E2E8F0', borderRadius: 8, overflow: 'hidden', background: '#FFFFFF' }}>
              <div style={{ padding: '8px 10px', background: '#F1F5F9', fontSize: 12, fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                <span>NID Front Document</span>
                <span style={{ fontSize: 10, color: '#64748B' }}>Card Front</span>
              </div>
              <div style={{ height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0F172A', cursor: 'pointer', overflow: 'hidden' }}
                   onClick={() => formatDocUrl(merchant.nid_front_url) && setPreviewImage(formatDocUrl(merchant.nid_front_url))}>
                {formatDocUrl(merchant.nid_front_url) ? (
                  <img src={formatDocUrl(merchant.nid_front_url)!} alt="NID Front" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                ) : (
                  <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', padding: 10 }}>
                    📄 No Front Document Uploaded
                  </div>
                )}
              </div>
              {formatDocUrl(merchant.nid_front_url) && (
                <div style={{ padding: 6, textAlign: 'center' }}>
                  <button className="button" style={{ fontSize: 11, padding: '2px 8px', background: '#64748B' }} onClick={() => setPreviewImage(formatDocUrl(merchant.nid_front_url))}>
                    🔍 View Fullscreen
                  </button>
                </div>
              )}
            </div>

            {/* Back Document */}
            <div style={{ border: '1px solid #E2E8F0', borderRadius: 8, overflow: 'hidden', background: '#FFFFFF' }}>
              <div style={{ padding: '8px 10px', background: '#F1F5F9', fontSize: 12, fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                <span>NID Back Document</span>
                <span style={{ fontSize: 10, color: '#64748B' }}>Card Back</span>
              </div>
              <div style={{ height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0F172A', cursor: 'pointer', overflow: 'hidden' }}
                   onClick={() => formatDocUrl(merchant.nid_back_url) && setPreviewImage(formatDocUrl(merchant.nid_back_url))}>
                {formatDocUrl(merchant.nid_back_url) ? (
                  <img src={formatDocUrl(merchant.nid_back_url)!} alt="NID Back" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                ) : (
                  <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', padding: 10 }}>
                    📄 No Back Document Uploaded
                  </div>
                )}
              </div>
              {formatDocUrl(merchant.nid_back_url) && (
                <div style={{ padding: 6, textAlign: 'center' }}>
                  <button className="button" style={{ fontSize: 11, padding: '2px 8px', background: '#64748B' }} onClick={() => setPreviewImage(formatDocUrl(merchant.nid_back_url))}>
                    🔍 View Fullscreen
                  </button>
                </div>
              )}
            </div>

            {/* Face Biometric Selfie */}
            <div style={{ border: '1px solid #E2E8F0', borderRadius: 8, overflow: 'hidden', background: '#FFFFFF' }}>
              <div style={{ padding: '8px 10px', background: '#F1F5F9', fontSize: 12, fontWeight: 700, display: 'flex', justifyContent: 'space-between' }}>
                <span>Biometric Face Scan</span>
                <span style={{ fontSize: 10, color: '#10B981', fontWeight: 700 }}>ML Kit Live</span>
              </div>
              <div style={{ height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0F172A', cursor: 'pointer', overflow: 'hidden' }}
                   onClick={() => formatDocUrl(merchant.face_photo_url) && setPreviewImage(formatDocUrl(merchant.face_photo_url))}>
                {formatDocUrl(merchant.face_photo_url) ? (
                  <img src={formatDocUrl(merchant.face_photo_url)!} alt="Biometric Face Selfie" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                ) : (
                  <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', padding: 10 }}>
                    🤳 No Face Scan Captured
                  </div>
                )}
              </div>
              {formatDocUrl(merchant.face_photo_url) && (
                <div style={{ padding: 6, textAlign: 'center' }}>
                  <button className="button" style={{ fontSize: 11, padding: '2px 8px', background: '#64748B' }} onClick={() => setPreviewImage(formatDocUrl(merchant.face_photo_url))}>
                    🔍 View Fullscreen
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
                className="button"
                style={{ position: 'absolute', top: -12, right: -12, background: '#EF4444', borderRadius: '50%', width: 36, height: 36, padding: 0, fontSize: 16, fontWeight: 700 }}
                onClick={() => setPreviewImage(null)}
              >
                ✕
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Recent Payments */}
      <div className="card" style={{ marginBottom: 14 }}>
        <h3 style={{ marginTop: 0 }}>💳 Recent Payment Events ({recentEvents.length})</h3>
        {recentEvents.length === 0 ? (
          <p style={{ fontSize: 12, color: '#94A3B8' }}>No payment transactions recorded for this merchant yet.</p>
        ) : (
          <table className="table">
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
                  <td style={{ fontFamily: 'monospace', fontSize: 11 }}>#{e.order_id?.slice(0, 10)}...</td>
                  <td style={{ fontWeight: 700 }}>৳{Number(e.amount || 0).toLocaleString()}</td>
                  <td>{e.payment_method || 'MFS'}</td>
                  <td>
                    <span style={{
                      padding: '2px 6px', borderRadius: 4, fontSize: 10, fontWeight: 700,
                      background: e.status === 'PAID' ? '#ECFDF5' : '#FEF2F2',
                      color: e.status === 'PAID' ? '#065F46' : '#991B1B'
                    }}>
                      {e.status}
                    </span>
                  </td>
                  <td style={{ fontFamily: 'monospace', fontSize: 11 }}>{e.trx_id || '--'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Connections & Webhooks */}
      <div className="card">
        <h3 style={{ marginTop: 0 }}>🔗 Connection Settings & Webhook Relays</h3>
        <p style={{ fontSize: 12, color: '#64748B' }}>
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
            <p style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>
              SwapnoPay backend sends HMAC-SHA256 signed payment payloads to this URL on successful payment verification.
            </p>
          </div>

          <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
            <button className="button" onClick={handleSaveConnection} style={{ background: '#10B981' }}>Save Connection Settings</button>
            {webhookUrl && (
              <button
                className="button"
                style={{ background: '#4F46E5' }}
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
                ⚡ Test Webhook Ping
              </button>
            )}
          </div>

          {testResult && (
            <div style={{ padding: 10, background: '#F8FAFC', borderRadius: 6, fontSize: 12, fontWeight: 600, border: '1px solid #E2E8F0' }}>
              {testResult}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
