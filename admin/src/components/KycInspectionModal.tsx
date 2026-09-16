import React, { useState } from 'react'
import { X, FileText, Camera, Check, Loader2 } from 'lucide-react'
import { adminSupabase, reviewMerchantIdentity } from '../adminSupabaseClient'

interface KycInspectionModalProps {
  merchant: any | null
  isOpen: boolean
  onClose: () => void
  onActionComplete: () => void
}

export default function KycInspectionModal({ merchant, isOpen, onClose, onActionComplete }: KycInspectionModalProps) {
  const [rejectReason, setRejectReason] = useState('')
  const [isRejecting, setIsRejecting] = useState(false)
  const [loading, setLoading] = useState(false)
  const [previewImage, setPreviewImage] = useState<string | null>(null)

  if (!isOpen || !merchant) return null

  function formatImageUrl(url?: string | null) {
    if (!url) return null
    if (url.startsWith('http') || url.startsWith('data:')) return url
    const backend = (import.meta as any).env?.VITE_BACKEND_URL || 'https://api.swapnopay.top'
    return `${backend.replace(/\/$/, '')}/${url.replace(/^\//, '')}`
  }

  async function handleApprove() {
    setLoading(true)
    const now = new Date().toISOString()
    try {
      await reviewMerchantIdentity(merchant.id, 'APPROVE')

      onActionComplete()
      onClose()
    } catch (err: any) {
      alert('Failed to approve KYC: ' + err.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleReject() {
    if (!rejectReason.trim()) {
      alert('Please specify a rejection reason.')
      return
    }
    setLoading(true)
    const now = new Date().toISOString()
    try {
      await reviewMerchantIdentity(merchant.id, 'REJECT', rejectReason.trim())

      onActionComplete()
      onClose()
    } catch (err: any) {
      alert('Failed to reject KYC: ' + err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(15, 23, 42, 0.7)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 200,
      padding: 16
    }}>
      <div style={{
        background: 'white',
        borderRadius: 20,
        maxWidth: 720,
        width: '100%',
        padding: 28,
        boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)',
        maxHeight: '90vh',
        overflowY: 'auto'
      }}>
        {/* Modal Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #F1F5F9', paddingBottom: 16, marginBottom: 20 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: 0 }}>
                {merchant.business_name || merchant.name}
              </h2>
              <span className={`status-pill ${merchant.kyc_status === 'VERIFIED' ? 'success' : merchant.kyc_status === 'REJECTED' ? 'failed' : 'pending'}`}>
                {merchant.kyc_status || 'PENDING'}
              </span>
            </div>
            <p style={{ fontSize: 12.5, color: '#64748B', marginTop: 4 }}>
              Email: {merchant.email || '—'} | NID: {merchant.nid_number || '—'}
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close modal"
            style={{
              background: '#F1F5F9',
              border: 'none',
              borderRadius: '50%',
              width: 32,
              height: 32,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              color: '#64748B'
            }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Rejection box if open */}
        {isRejecting && (
          <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 12, padding: 14, marginBottom: 18 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: '#991B1B', marginBottom: 6 }}>
              Reason for Rejection:
            </div>
            <input
              type="text"
              placeholder="e.g. Blurry NID card, name mismatch with bank account..."
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              style={{ width: '100%', padding: '9px 12px', borderRadius: 8, border: '1px solid #F87171', fontSize: 13, outline: 'none', boxSizing: 'border-box', marginBottom: 10 }}
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={handleReject}
                disabled={loading}
                style={{ background: '#DC2626', color: 'white', border: 'none', padding: '8px 14px', borderRadius: 8, fontWeight: 700, fontSize: 12, cursor: 'pointer' }}
              >
                Confirm Reject
              </button>
              <button
                onClick={() => setIsRejecting(false)}
                style={{ background: 'white', border: '1px solid #CBD5E1', padding: '8px 14px', borderRadius: 8, fontWeight: 600, fontSize: 12, cursor: 'pointer' }}
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* Document Images */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 24 }}>
          {/* Front */}
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ padding: '8px 12px', background: '#F8FAFC', fontSize: 11.5, fontWeight: 700, color: '#475569', display: 'flex', alignItems: 'center', gap: 6 }}>
              <FileText size={14} color="#64748B" />
              <span>NID Front</span>
            </div>
            <div style={{ height: 160, background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {formatImageUrl(merchant.nid_front_url) ? (
                <img
                  src={formatImageUrl(merchant.nid_front_url)!}
                  alt="NID Front"
                  onClick={() => setPreviewImage(formatImageUrl(merchant.nid_front_url))}
                  style={{ width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }}
                />
              ) : (
                <span style={{ fontSize: 11, color: '#94A3B8' }}>No Front Card</span>
              )}
            </div>
          </div>

          {/* Back */}
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ padding: '8px 12px', background: '#F8FAFC', fontSize: 11.5, fontWeight: 700, color: '#475569', display: 'flex', alignItems: 'center', gap: 6 }}>
              <FileText size={14} color="#64748B" />
              <span>NID Back</span>
            </div>
            <div style={{ height: 160, background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {formatImageUrl(merchant.nid_back_url) ? (
                <img
                  src={formatImageUrl(merchant.nid_back_url)!}
                  alt="NID Back"
                  onClick={() => setPreviewImage(formatImageUrl(merchant.nid_back_url))}
                  style={{ width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }}
                />
              ) : (
                <span style={{ fontSize: 11, color: '#94A3B8' }}>No Back Card</span>
              )}
            </div>
          </div>

          {/* Face */}
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ padding: '8px 12px', background: '#F8FAFC', fontSize: 11.5, fontWeight: 700, color: '#475569', display: 'flex', alignItems: 'center', gap: 6 }}>
              <Camera size={14} color="#64748B" />
              <span>Face Selfie</span>
            </div>
            <div style={{ height: 160, background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {formatImageUrl(merchant.face_photo_url) ? (
                <img
                  src={formatImageUrl(merchant.face_photo_url)!}
                  alt="Selfie"
                  onClick={() => setPreviewImage(formatImageUrl(merchant.face_photo_url))}
                  style={{ width: '100%', height: '100%', objectFit: 'contain', cursor: 'zoom-in' }}
                />
              ) : (
                <span style={{ fontSize: 11, color: '#94A3B8' }}>No Face Photo</span>
              )}
            </div>
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button
            onClick={onClose}
            style={{ padding: '10px 18px', background: '#F1F5F9', border: 'none', borderRadius: 10, fontWeight: 700, color: '#475569', cursor: 'pointer' }}
          >
            Close
          </button>
          {!isRejecting && merchant.kyc_status !== 'REJECTED' && (
            <button
              onClick={() => setIsRejecting(true)}
              style={{ padding: '10px 18px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, fontWeight: 700, color: '#DC2626', cursor: 'pointer' }}
            >
              Reject with Reason
            </button>
          )}
          {merchant.kyc_status !== 'VERIFIED' && (
            <button
              onClick={handleApprove}
              disabled={loading}
              style={{
                padding: '10px 22px',
                background: '#10B981',
                border: 'none',
                borderRadius: 10,
                fontWeight: 700,
                color: 'white',
                cursor: loading ? 'not-allowed' : 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6
              }}
            >
              {loading ? (
                <>
                  <Loader2 size={16} className="spin" />
                  <span>Verifying...</span>
                </>
              ) : (
                <>
                  <Check size={16} />
                  <span>Approve & Activate</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>

      {/* Image Preview Lightbox */}
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
            alt="Enlarged Document"
            style={{ maxWidth: '90vw', maxHeight: '85vh', borderRadius: 12, objectFit: 'contain' }}
          />
        </div>
      )}
    </div>
  )
}
