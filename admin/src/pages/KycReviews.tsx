import React, { useEffect, useState } from 'react'
import { adminSupabase } from '../adminSupabaseClient'
import { Link } from 'react-router-dom'

interface KycRecord {
  id: string
  user_id?: string
  business_name: string
  email?: string
  phone?: string
  nid_number?: string
  nid_name?: string
  nid_dob?: string
  nid_front_url?: string
  nid_back_url?: string
  face_photo_url?: string
  kyc_status: 'UNVERIFIED' | 'PENDING' | 'PENDING_REVIEW' | 'VERIFIED' | 'REJECTED'
  kyc_submitted_at?: string
  kyc_reviewed_at?: string
  kyc_rejection_reason?: string
  status?: string
}

export default function KycReviews() {
  const [submissions, setSubmissions] = useState<KycRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'VERIFIED' | 'REJECTED'>('PENDING')
  const [search, setSearch] = useState('')
  const [selectedMerchant, setSelectedMerchant] = useState<KycRecord | null>(null)
  const [previewImage, setPreviewImage] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [isRejecting, setIsRejecting] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [actionMessage, setActionMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  // Fetch KYC records from merchants table
  async function loadKycRecords() {
    try {
      const { data, error } = await adminSupabase
        .from('merchants')
        .select('*')
        .order('kyc_submitted_at', { ascending: false, nullsFirst: false })

      if (!error && data) {
        setSubmissions(data as KycRecord[])
        // Auto-select first pending record if none selected
        if (!selectedMerchant && data.length > 0) {
          const firstPending = data.find((m: any) => m.kyc_status === 'PENDING' || m.kyc_status === 'PENDING_REVIEW')
          setSelectedMerchant(firstPending || data[0])
        }
      }
    } catch (err: any) {
      console.error('[KycReviews] fetch error:', err.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadKycRecords()

    // Real-time subscription to merchants table
    const channel = adminSupabase
      .channel('kyc_reviews_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'merchants' }, () => {
        loadKycRecords()
      })
      .subscribe()

    return () => {
      adminSupabase.removeChannel(channel)
    }
  }, [])

  // Format document URL
  function formatImageUrl(url?: string | null) {
    if (!url) return null
    if (url.startsWith('http') || url.startsWith('data:')) return url
    const backend = (import.meta as any).env?.VITE_BACKEND_URL || 'https://pay.swapnopay.top'
    return `${backend.replace(/\/$/, '')}/${url.replace(/^\//, '')}`
  }

  // Handle Approve
  async function handleApprove(merchantId: string) {
    setActionLoading(true)
    setActionMessage(null)
    const now = new Date().toISOString()

    try {
      const { error } = await adminSupabase
        .from('merchants')
        .update({
          kyc_status: 'VERIFIED',
          status: 'ACTIVE',
          kyc_reviewed_at: now,
          kyc_reviewed_by: 'ADMIN',
          kyc_rejection_reason: null,
          updated_at: now
        })
        .eq('id', merchantId)

      if (error) throw error

      setActionMessage({ type: 'success', text: 'KYC Approved! Merchant is now Verified and Active.' })
      await loadKycRecords()

      if (selectedMerchant?.id === merchantId) {
        setSelectedMerchant(prev => prev ? { ...prev, kyc_status: 'VERIFIED', status: 'ACTIVE' } : null)
      }
    } catch (err: any) {
      setActionMessage({ type: 'error', text: err.message || 'Failed to approve KYC.' })
    } finally {
      setActionLoading(false)
    }
  }

  // Handle Reject
  async function handleReject(merchantId: string) {
    if (!rejectReason.trim()) {
      alert('Please specify a rejection reason to inform the merchant on their app.')
      return
    }

    setActionLoading(true)
    setActionMessage(null)
    const now = new Date().toISOString()

    try {
      const { error } = await adminSupabase
        .from('merchants')
        .update({
          kyc_status: 'REJECTED',
          kyc_rejection_reason: rejectReason.trim(),
          kyc_reviewed_at: now,
          kyc_reviewed_by: 'ADMIN',
          updated_at: now
        })
        .eq('id', merchantId)

      if (error) throw error

      setActionMessage({ type: 'success', text: 'KYC Rejected. Reason communicated to the merchant app.' })
      setIsRejecting(false)
      setRejectReason('')
      await loadKycRecords()

      if (selectedMerchant?.id === merchantId) {
        setSelectedMerchant(prev => prev ? { ...prev, kyc_status: 'REJECTED', kyc_rejection_reason: rejectReason.trim() } : null)
      }
    } catch (err: any) {
      setActionMessage({ type: 'error', text: err.message || 'Failed to reject KYC.' })
    } finally {
      setActionLoading(false)
    }
  }

  // Filtered submissions
  const filteredList = submissions.filter(m => {
    if (filter === 'PENDING' && !(m.kyc_status === 'PENDING' || m.kyc_status === 'PENDING_REVIEW')) return false
    if (filter === 'VERIFIED' && m.kyc_status !== 'VERIFIED') return false
    if (filter === 'REJECTED' && m.kyc_status !== 'REJECTED') return false

    if (search.trim()) {
      const q = search.toLowerCase()
      return (
        (m.business_name || '').toLowerCase().includes(q) ||
        (m.email || '').toLowerCase().includes(q) ||
        (m.phone || '').includes(q) ||
        (m.nid_number || '').includes(q)
      )
    }
    return true
  })

  const pendingCount = submissions.filter(m => m.kyc_status === 'PENDING' || m.kyc_status === 'PENDING_REVIEW').length
  const verifiedCount = submissions.filter(m => m.kyc_status === 'VERIFIED').length
  const rejectedCount = submissions.filter(m => m.kyc_status === 'REJECTED').length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Top Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <h1 style={{ fontSize: 26, fontWeight: 800, color: '#0F172A', letterSpacing: '-0.5px', margin: 0 }}>
            🛡️ KYC & Identity Verification
          </h1>
          <p style={{ fontSize: 13, color: '#64748B', marginTop: 4 }}>
            Review merchant NID identity cards, biometric selfie scans, and approve/reject platform verification.
          </p>
        </div>

        {/* Quick Filter Tabs */}
        <div style={{ display: 'flex', background: '#F1F5F9', padding: 4, borderRadius: 10, gap: 4 }}>
          {[
            { id: 'PENDING', label: `Pending (${pendingCount})` },
            { id: 'VERIFIED', label: `Verified (${verifiedCount})` },
            { id: 'REJECTED', label: `Rejected (${rejectedCount})` },
            { id: 'ALL', label: `All (${submissions.length})` }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setFilter(tab.id as any)}
              style={{
                border: 'none',
                background: filter === tab.id ? 'white' : 'transparent',
                color: filter === tab.id ? '#2563EB' : '#64748B',
                fontWeight: filter === tab.id ? 700 : 500,
                fontSize: 12.5,
                padding: '7px 14px',
                borderRadius: 8,
                cursor: 'pointer',
                boxShadow: filter === tab.id ? '0 2px 4px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease'
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Action Banner Message */}
      {actionMessage && (
        <div style={{
          padding: '12px 18px',
          borderRadius: 12,
          background: actionMessage.type === 'success' ? '#ECFDF5' : '#FEF2F2',
          border: `1px solid ${actionMessage.type === 'success' ? '#A7F3D0' : '#FECACA'}`,
          color: actionMessage.type === 'success' ? '#065F46' : '#991B1B',
          fontWeight: 600,
          fontSize: 13.5,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span>{actionMessage.text}</span>
          <button onClick={() => setActionMessage(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit' }}>✕</button>
        </div>
      )}

      {/* Main Two-Column View */}
      <div style={{ display: 'grid', gridTemplateColumns: '360px 1fr', gap: 24, alignItems: 'start' }}>
        {/* Left Column: Submissions Queue */}
        <div className="card" style={{ padding: 18, maxHeight: 'calc(100vh - 200px)', display: 'flex', flexDirection: 'column' }}>
          {/* Search Input */}
          <div style={{ marginBottom: 14 }}>
            <input
              type="text"
              placeholder="Search by name, email, NID..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: 8,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                outline: 'none',
                boxSizing: 'border-box'
              }}
            />
          </div>

          {/* Queue List */}
          <div style={{ overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
            {loading ? (
              <div style={{ textAlign: 'center', padding: 24, color: '#64748B', fontSize: 13 }}>Loading verification queue...</div>
            ) : filteredList.length === 0 ? (
              <div style={{ textAlign: 'center', padding: 32, color: '#94A3B8', fontSize: 13 }}>
                No KYC submissions found in this category.
              </div>
            ) : (
              filteredList.map(m => {
                const isSelected = selectedMerchant?.id === m.id
                const isPending = m.kyc_status === 'PENDING' || m.kyc_status === 'PENDING_REVIEW'
                const isVerified = m.kyc_status === 'VERIFIED'
                return (
                  <div
                    key={m.id}
                    onClick={() => { setSelectedMerchant(m); setIsRejecting(false); }}
                    style={{
                      padding: '12px 14px',
                      borderRadius: 12,
                      background: isSelected ? '#EFF6FF' : '#FFFFFF',
                      border: `1px solid ${isSelected ? '#93C5FD' : '#F1F5F9'}`,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
                      <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
                        {m.business_name}
                      </div>
                      <span className={`status-pill ${isVerified ? 'success' : isPending ? 'pending' : 'failed'}`} style={{ fontSize: 10 }}>
                        {m.kyc_status}
                      </span>
                    </div>

                    <div style={{ fontSize: 12, color: '#64748B', marginBottom: 6 }}>
                      {m.email || m.phone || 'No contact email'}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 11, color: '#94A3B8' }}>
                      <span>NID: {m.nid_number || 'Not provided'}</span>
                      <span>{m.kyc_submitted_at ? new Date(m.kyc_submitted_at).toLocaleDateString([], { month: 'short', day: 'numeric' }) : 'Recent'}</span>
                    </div>
                  </div>
                )
              })
            )}
          </div>
        </div>

        {/* Right Column: Detailed Document & Verification Inspector */}
        {selectedMerchant ? (
          <div className="card" style={{ padding: 28 }}>
            {/* Merchant Header Bar */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', paddingBottom: 20, borderBottom: '1px solid #F1F5F9', marginBottom: 24 }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: 0 }}>
                    {selectedMerchant.business_name}
                  </h2>
                  <span className={`status-pill ${selectedMerchant.kyc_status === 'VERIFIED' ? 'success' : selectedMerchant.kyc_status === 'REJECTED' ? 'failed' : 'pending'}`}>
                    {selectedMerchant.kyc_status}
                  </span>
                </div>
                <div style={{ fontSize: 13, color: '#64748B', marginTop: 4 }}>
                  Merchant ID: <code style={{ color: '#2563EB', fontWeight: 600 }}>{selectedMerchant.id}</code>
                </div>
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', gap: 10 }}>
                {selectedMerchant.kyc_status !== 'VERIFIED' && (
                  <button
                    onClick={() => handleApprove(selectedMerchant.id)}
                    disabled={actionLoading}
                    style={{
                      background: '#10B981',
                      color: 'white',
                      border: 'none',
                      padding: '10px 18px',
                      borderRadius: 10,
                      fontWeight: 700,
                      fontSize: 13,
                      cursor: actionLoading ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6
                    }}
                  >
                    ✓ Approve & Verify
                  </button>
                )}

                {selectedMerchant.kyc_status !== 'REJECTED' && !isRejecting && (
                  <button
                    onClick={() => setIsRejecting(true)}
                    disabled={actionLoading}
                    style={{
                      background: '#FEF2F2',
                      color: '#DC2626',
                      border: '1px solid #FECACA',
                      padding: '10px 18px',
                      borderRadius: 10,
                      fontWeight: 700,
                      fontSize: 13,
                      cursor: actionLoading ? 'not-allowed' : 'pointer'
                    }}
                  >
                    ✕ Reject
                  </button>
                )}
              </div>
            </div>

            {/* Rejection Input Box */}
            {isRejecting && (
              <div style={{
                background: '#FEF2F2',
                border: '1px solid #FECACA',
                borderRadius: 14,
                padding: 16,
                marginBottom: 24
              }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: '#991B1B', marginBottom: 6 }}>
                  Specify Rejection Reason (Sent to Mobile App):
                </div>
                <input
                  type="text"
                  placeholder="e.g. NID image is blurry, name mismatch, or expired document"
                  value={rejectReason}
                  onChange={e => setRejectReason(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: 8,
                    border: '1px solid #F87171',
                    fontSize: 13,
                    outline: 'none',
                    marginBottom: 10,
                    boxSizing: 'border-box'
                  }}
                />
                <div style={{ display: 'flex', gap: 10 }}>
                  <button
                    onClick={() => handleReject(selectedMerchant.id)}
                    disabled={actionLoading}
                    style={{
                      background: '#DC2626',
                      color: 'white',
                      border: 'none',
                      padding: '8px 16px',
                      borderRadius: 8,
                      fontWeight: 700,
                      fontSize: 12.5,
                      cursor: 'pointer'
                    }}
                  >
                    Confirm Rejection
                  </button>
                  <button
                    onClick={() => setIsRejecting(false)}
                    style={{
                      background: '#FFFFFF',
                      border: '1px solid #CBD5E1',
                      padding: '8px 16px',
                      borderRadius: 8,
                      fontWeight: 600,
                      fontSize: 12.5,
                      cursor: 'pointer'
                    }}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            {/* Merchant Extracted Info */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: 16,
              background: '#F8FAFC',
              borderRadius: 14,
              padding: 18,
              marginBottom: 28
            }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>NID Number</div>
                <div style={{ fontSize: 15, fontWeight: 800, color: '#0F172A', marginTop: 4, fontFamily: 'monospace' }}>
                  {selectedMerchant.nid_number || '—'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>NID Full Name</div>
                <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', marginTop: 4 }}>
                  {selectedMerchant.nid_name || '—'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Date of Birth</div>
                <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', marginTop: 4 }}>
                  {selectedMerchant.nid_dob || '—'}
                </div>
              </div>

              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Submission Date</div>
                <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', marginTop: 4 }}>
                  {selectedMerchant.kyc_submitted_at ? new Date(selectedMerchant.kyc_submitted_at).toLocaleString() : '—'}
                </div>
              </div>
            </div>

            {/* Document Previews (Front, Back, Face) */}
            <h3 style={{ fontSize: 16, fontWeight: 800, color: '#0F172A', marginBottom: 14 }}>
              Submitted Verification Documents & Biometrics
            </h3>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 20 }}>
              {/* Document 1: NID Front */}
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden', background: '#FFFFFF' }}>
                <div style={{ padding: '10px 14px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', fontSize: 12.5, fontWeight: 700, color: '#334155' }}>
                  📄 NID Front Card
                </div>
                <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F1F5F9', position: 'relative' }}>
                  {formatImageUrl(selectedMerchant.nid_front_url) ? (
                    <img
                      src={formatImageUrl(selectedMerchant.nid_front_url)!}
                      alt="NID Front"
                      onClick={() => setPreviewImage(formatImageUrl(selectedMerchant.nid_front_url))}
                      style={{ width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }}
                    />
                  ) : (
                    <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', padding: 20 }}>
                      No front card image uploaded
                    </div>
                  )}
                </div>
              </div>

              {/* Document 2: NID Back */}
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden', background: '#FFFFFF' }}>
                <div style={{ padding: '10px 14px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', fontSize: 12.5, fontWeight: 700, color: '#334155' }}>
                  📄 NID Back Card
                </div>
                <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F1F5F9', position: 'relative' }}>
                  {formatImageUrl(selectedMerchant.nid_back_url) ? (
                    <img
                      src={formatImageUrl(selectedMerchant.nid_back_url)!}
                      alt="NID Back"
                      onClick={() => setPreviewImage(formatImageUrl(selectedMerchant.nid_back_url))}
                      style={{ width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }}
                    />
                  ) : (
                    <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', padding: 20 }}>
                      No back card image uploaded
                    </div>
                  )}
                </div>
              </div>

              {/* Document 3: Biometric Face Selfie */}
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden', background: '#FFFFFF' }}>
                <div style={{ padding: '10px 14px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', fontSize: 12.5, fontWeight: 700, color: '#334155' }}>
                  🤳 Live Biometric Face Scan
                </div>
                <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F1F5F9', position: 'relative' }}>
                  {formatImageUrl(selectedMerchant.face_photo_url) ? (
                    <img
                      src={formatImageUrl(selectedMerchant.face_photo_url)!}
                      alt="Face Selfie"
                      onClick={() => setPreviewImage(formatImageUrl(selectedMerchant.face_photo_url))}
                      style={{ width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }}
                    />
                  ) : (
                    <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', padding: 20 }}>
                      No face photo uploaded
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="card" style={{ padding: 48, textAlign: 'center', color: '#94A3B8' }}>
            Select a merchant submission from the left queue to inspect documents.
          </div>
        )}
      </div>

      {/* Lightbox Zoom Modal */}
      {previewImage && (
        <div
          onClick={() => setPreviewImage(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.85)',
            backdropFilter: 'blur(5px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: 24,
            cursor: 'zoom-out'
          }}
        >
          <img
            src={previewImage}
            alt="Enlarged Document Preview"
            style={{
              maxWidth: '90vw',
              maxHeight: '85vh',
              borderRadius: 14,
              boxShadow: '0 25px 50px rgba(0,0,0,0.5)',
              objectFit: 'contain'
            }}
          />
        </div>
      )}
    </div>
  )
}
