import React, { useEffect, useState, useMemo } from 'react';
import { adminSupabase } from '../adminSupabaseClient';
import { Link } from 'react-router-dom';

interface MerchantRecord {
  id: string;
  business_name: string;
  email?: string;
  phone?: string;
  business_type?: string;
  website?: string;
  default_number?: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING_VERIFICATION';
  subscription_tier: 'STARTER' | 'PRO' | 'ENTERPRISE';
  kyc_status?: string;
  kyc_rejection_reason?: string;
  nid_number?: string;
  created_at?: string;
}

export default function Merchants() {
  const [rows, setRows] = useState<MerchantRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING_KYC' | 'VERIFIED_KYC' | 'SUSPENDED'>('ALL');
  const [showAddModal, setShowAddModal] = useState(false);

  // New Merchant Form
  const [newBizName, setNewBizName] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newBizType, setNewBizType] = useState('RETAIL');
  const [newTier, setNewTier] = useState<'STARTER' | 'PRO' | 'ENTERPRISE'>('STARTER');
  const [saving, setSaving] = useState(false);

  const loadMerchants = async () => {
    let merchantsList: MerchantRecord[] = [];

    try {
      const { data, error } = await adminSupabase
        .from('merchants')
        .select('*')
        .order('created_at', { ascending: false });
      if (!error && data) {
        merchantsList = data as MerchantRecord[];
      }
    } catch (e) {
      console.warn('[Merchants] Supabase fetch warning:', e);
    }

    setRows(merchantsList);
    setLoading(false);
  };

  useEffect(() => {
    loadMerchants();

    const params = new URLSearchParams(window.location.search);
    if (params.get('filter') === 'pending') {
      setStatusFilter('PENDING_KYC');
    }

    // Subscribe to realtime updates on merchants table
    const channel = adminSupabase
      .channel('merchants_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'merchants' }, () => {
        loadMerchants();
      })
      .subscribe();

    return () => {
      adminSupabase.removeChannel(channel);
    };
  }, []);

  // Filtered rows
  const filteredRows = useMemo(() => {
    let result = rows;
    if (statusFilter === 'PENDING_KYC') {
      result = result.filter(r => r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW');
    } else if (statusFilter === 'VERIFIED_KYC') {
      result = result.filter(r => r.kyc_status === 'VERIFIED');
    } else if (statusFilter === 'SUSPENDED') {
      result = result.filter(r => r.status === 'SUSPENDED');
    }

    if (!search.trim()) return result;
    const q = search.toLowerCase();
    return result.filter(
      r =>
        (r.business_name || '').toLowerCase().includes(q) ||
        (r.email || '').toLowerCase().includes(q) ||
        (r.phone || '').includes(q) ||
        (r.nid_number || '').includes(q) ||
        (r.kyc_status || '').toLowerCase().includes(q) ||
        (r.subscription_tier || '').toLowerCase().includes(q)
    );
  }, [rows, search, statusFilter]);

  // Create new merchant in Admin Supabase
  const handleCreateMerchant = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newBizName.trim()) return alert('Business name is required');
    setSaving(true);
    try {
      const randomBytes = new Uint8Array(24);
      window.crypto.getRandomValues(randomBytes);
      const secureSecret = 'whsec_' + Array.from(randomBytes).map(b => b.toString(16).padStart(2, '0')).join('');

      const { error } = await adminSupabase.from('merchants').insert({
        business_name: newBizName.trim(),
        email: newEmail.trim() || null,
        phone: newPhone.trim() || null,
        business_type: newBizType,
        subscription_tier: newTier,
        status: 'ACTIVE',
        webhook_secret: secureSecret,
      });

      if (error) throw new Error(error.message);
      setShowAddModal(false);
      setNewBizName('');
      setNewEmail('');
      setNewPhone('');
      await loadMerchants();
    } catch (err: any) {
      alert('Failed to register merchant: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  // Toggle Merchant Status
  const handleToggleStatus = async (merchantId: string, currentStatus: string) => {
    const nextStatus = currentStatus === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    if (!window.confirm(`Are you sure you want to mark this merchant as ${nextStatus}?`)) return;

    try {
      const { error } = await adminSupabase
        .from('merchants')
        .update({ status: nextStatus, updated_at: new Date().toISOString() })
        .eq('id', merchantId);

      if (error) throw new Error(error.message);
      await loadMerchants();
    } catch (err: any) {
      alert('Failed to update status: ' + err.message);
    }
  };

  return (
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>🏪 Merchant Registry ({rows.length})</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Manage registered merchants, subscription tiers, and API access permissions.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="button" onClick={() => setShowAddModal(true)} style={{ background: '#10B981' }}>
            ➕ Add Merchant
          </button>
          <Link to="/dashboard">
            <button className="button" style={{ background: '#64748B' }}>Back</button>
          </Link>
        </div>
      </div>

      {/* Search & Filter Bar */}
      <div className="card" style={{ marginBottom: 16, padding: 12 }}>
        <div style={{ display: 'flex', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
          {[
            { id: 'ALL', label: `All (${rows.length})` },
            { id: 'PENDING_KYC', label: `⏳ Pending KYC (${rows.filter(r => r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW').length})`, alert: true },
            { id: 'VERIFIED_KYC', label: `✅ Verified KYC (${rows.filter(r => r.kyc_status === 'VERIFIED').length})` },
            { id: 'SUSPENDED', label: `🚫 Suspended (${rows.filter(r => r.status === 'SUSPENDED').length})` },
          ].map(tab => (
            <button
              key={tab.id}
              className="button"
              type="button"
              onClick={() => setStatusFilter(tab.id as any)}
              style={{
                fontSize: 12,
                padding: '4px 12px',
                background: statusFilter === tab.id ? '#4F46E5' : '#F1F5F9',
                color: statusFilter === tab.id ? '#FFFFFF' : '#334155',
                border: tab.alert && rows.some(r => r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW') ? '1px solid #F59E0B' : '1px solid transparent',
                fontWeight: statusFilter === tab.id ? 700 : 500,
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <input
          className="input"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="🔍 Search merchants by business name, email, phone, NID, or tier..."
        />
      </div>

      {/* Merchants Table */}
      <div className="card">
        {loading ? (
          <div style={{ padding: 20, textAlign: 'center', color: '#64748B' }}>Loading merchants registry...</div>
        ) : filteredRows.length === 0 ? (
          <div style={{ padding: 30, textAlign: 'center', color: '#94A3B8' }}>No merchants match your search.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Business Name</th>
                <th>Contact</th>
                <th>Type</th>
                <th>Status</th>
                <th>KYC</th>
                <th>Tier</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredRows.map(r => (
                <tr key={r.id}>
                  <td>
                    <strong style={{ fontSize: 14, color: '#1E293B' }}>{r.business_name}</strong>
                    <div style={{ fontSize: 10, color: '#94A3B8', fontFamily: 'monospace' }}>ID: {r.id.slice(0, 8)}...</div>
                  </td>
                  <td>
                    <div>{r.email || '--'}</div>
                    <div style={{ fontSize: 11, color: '#64748B' }}>{r.phone || '--'}</div>
                  </td>
                  <td><span style={{ fontSize: 11, background: '#F1F5F9', padding: '2px 6px', borderRadius: 4 }}>{r.business_type || 'RETAIL'}</span></td>
                  <td>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
                      background: r.status === 'SUSPENDED' ? '#FEF2F2' : '#ECFDF5',
                      color: r.status === 'SUSPENDED' ? '#EF4444' : '#065F46'
                    }}>
                      {r.status || 'ACTIVE'}
                    </span>
                  </td>
                  <td>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
                      background: r.kyc_status === 'VERIFIED' ? '#ECFDF5' :
                                  r.kyc_status === 'REJECTED' ? '#FEF2F2' :
                                  r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW' ? '#FEF3C7' : '#F1F5F9',
                      color: r.kyc_status === 'VERIFIED' ? '#065F46' :
                             r.kyc_status === 'REJECTED' ? '#991B1B' :
                             r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW' ? '#92400E' : '#64748B',
                      border: r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW' ? '1px solid #F59E0B' : 'none',
                    }}>
                      {r.kyc_status === 'VERIFIED' ? '✅ VERIFIED' :
                       r.kyc_status === 'REJECTED' ? '❌ REJECTED' :
                       r.kyc_status === 'PENDING' || r.kyc_status === 'PENDING_REVIEW' ? '⏳ PENDING' : '⚪ UNVERIFIED'}
                    </span>
                    {r.nid_number && (
                      <div style={{ fontSize: 10, color: '#64748B', fontFamily: 'monospace', marginTop: 2 }}>
                        NID: {r.nid_number}
                      </div>
                    )}
                    {r.kyc_status === 'REJECTED' && r.kyc_rejection_reason && (
                      <div style={{ fontSize: 10, color: '#B91C1C', marginTop: 3, maxWidth: 180, whiteSpace: 'normal', lineHeight: 1.2 }} title={r.kyc_rejection_reason}>
                        <strong>Reason:</strong> {r.kyc_rejection_reason}
                      </div>
                    )}
                  </td>
                  <td>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 700,
                      background: r.subscription_tier === 'ENTERPRISE' ? '#F3E8FF' : r.subscription_tier === 'PRO' ? '#E0F2FE' : '#F1F5F9',
                      color: r.subscription_tier === 'ENTERPRISE' ? '#7E22CE' : r.subscription_tier === 'PRO' ? '#0369A1' : '#475569'
                    }}>
                      {r.subscription_tier || 'STARTER'}
                    </span>
                  </td>
                  <td style={{ fontSize: 11, color: '#64748B' }}>
                    {r.created_at ? new Date(r.created_at).toLocaleDateString() : '--'}
                  </td>
                  <td style={{ display: 'flex', gap: 6 }}>
                    <Link to={`/merchants/${r.id}`}>
                      <button className="button" style={{ padding: '4px 10px', fontSize: 11, background: '#4F46E5' }}>View</button>
                    </Link>
                    <button
                      className="button"
                      onClick={() => handleToggleStatus(r.id, r.status)}
                      style={{ padding: '4px 10px', fontSize: 11, background: r.status === 'SUSPENDED' ? '#10B981' : '#EF4444' }}
                    >
                      {r.status === 'SUSPENDED' ? 'Activate' : 'Suspend'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Add Merchant Modal */}
      {showAddModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
          <div className="card" style={{ maxWidth: 480, width: '100%', background: 'white' }}>
            <h3 style={{ marginTop: 0 }}>➕ Register New Merchant</h3>
            <form onSubmit={handleCreateMerchant}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Business Name *</label>
                  <input className="input" value={newBizName} onChange={e => setNewBizName(e.target.value)} placeholder="e.g. DreamMart Store" required />
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Email Address</label>
                  <input className="input" type="email" value={newEmail} onChange={e => setNewEmail(e.target.value)} placeholder="owner@merchant.com" />
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Phone Number</label>
                  <input className="input" value={newPhone} onChange={e => setNewPhone(e.target.value)} placeholder="+8801700000000" />
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Business Type</label>
                  <select className="input" value={newBizType} onChange={e => setNewBizType(e.target.value)}>
                    <option value="RETAIL">Retail & E-commerce</option>
                    <option value="SERVICES">Digital Services</option>
                    <option value="EDUCATION">Education & Courses</option>
                    <option value="DONATION">Non-Profit & Donation</option>
                  </select>
                </div>
                <div>
                  <label style={{ fontSize: 11, fontWeight: 700, display: 'block', marginBottom: 4 }}>Subscription Tier</label>
                  <select className="input" value={newTier} onChange={e => setNewTier(e.target.value as any)}>
                    <option value="STARTER">STARTER</option>
                    <option value="PRO">PRO</option>
                    <option value="ENTERPRISE">ENTERPRISE</option>
                  </select>
                </div>
              </div>
              <div style={{ marginTop: 18, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" className="button" onClick={() => setShowAddModal(false)} style={{ background: '#64748B' }}>Cancel</button>
                <button type="submit" className="button" disabled={saving} style={{ background: '#10B981' }}>
                  {saving ? 'Creating...' : 'Create Merchant'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
