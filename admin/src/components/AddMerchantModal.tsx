import React, { useState } from 'react'
import { X, UserPlus, Loader2 } from 'lucide-react'
import { adminSupabase } from '../adminSupabaseClient'

interface AddMerchantModalProps {
  isOpen: boolean
  onClose: () => void
  onMerchantCreated: () => void
}

export default function AddMerchantModal({ isOpen, onClose, onMerchantCreated }: AddMerchantModalProps) {
  const [businessName, setBusinessName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [businessType, setBusinessType] = useState('E-commerce')
  const [defaultNumber, setDefaultNumber] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!isOpen) return null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!businessName.trim() || !email.trim()) {
      setError('Business name and email are required.')
      return
    }

    setLoading(true)
    setError(null)

    try {
      // Generate random webhook secret
      const randomBytes = new Uint8Array(24)
      crypto.getRandomValues(randomBytes)
      const webhookSecret = 'whsec_' + Array.from(randomBytes).map(b => b.toString(16).padStart(2, '0')).join('')

      const { data, error: insertError } = await adminSupabase
        .from('merchants')
        .insert({
          business_name: businessName.trim(),
          email: email.trim().toLowerCase(),
          phone: phone.trim() || null,
          business_type: businessType,
          default_number: defaultNumber.trim() || null,
          webhook_secret: webhookSecret,
          kyc_status: 'ACTIVE'
        })
        .select()
        .single()

      if (insertError) throw insertError

      // If default payment number was specified, register it in merchant_numbers
      if (defaultNumber.trim() && data?.id) {
        await adminSupabase.from('merchant_numbers').insert({
          merchant_id: data.id,
          number: defaultNumber.trim(),
          type: 'bKash',
          account_type: 'Merchant',
          is_default: true,
          active: true
        })
      }

      onMerchantCreated()
      onClose()
      setBusinessName('')
      setEmail('')
      setPhone('')
      setDefaultNumber('')
    } catch (err: any) {
      setError(err.message || 'Failed to create merchant.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(15, 23, 42, 0.6)',
      backdropFilter: 'blur(4px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 100,
      padding: 16
    }}>
      <div style={{
        background: 'white',
        borderRadius: 20,
        maxWidth: 520,
        width: '100%',
        padding: 32,
        boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)',
        position: 'relative'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <div>
            <h2 style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', margin: 0 }}>Add New Merchant</h2>
            <p style={{ fontSize: 13, color: '#64748B', marginTop: 4 }}>Register a merchant onto your payment platform</p>
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

        {error && (
          <div style={{
            padding: '10px 14px',
            background: '#FEF2F2',
            border: '1px solid #FECACA',
            borderRadius: 10,
            color: '#DC2626',
            fontSize: 13,
            fontWeight: 600,
            marginBottom: 16
          }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
              Business / Store Name *
            </label>
            <input
              type="text"
              required
              placeholder="e.g. Dhaka Fashion Hub"
              value={businessName}
              onChange={e => setBusinessName(e.target.value)}
              style={{
                width: '100%',
                padding: '10px 14px',
                border: '1px solid #CBD5E1',
                borderRadius: 10,
                fontSize: 13.5,
                outline: 'none',
                boxSizing: 'border-box'
              }}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
                Merchant Email *
              </label>
              <input
                type="email"
                required
                placeholder="merchant@example.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  border: '1px solid #CBD5E1',
                  borderRadius: 10,
                  fontSize: 13.5,
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
                Phone Number
              </label>
              <input
                type="text"
                placeholder="+8801700000000"
                value={phone}
                onChange={e => setPhone(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  border: '1px solid #CBD5E1',
                  borderRadius: 10,
                  fontSize: 13.5,
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
                Business Category
              </label>
              <select
                value={businessType}
                onChange={e => setBusinessType(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  border: '1px solid #CBD5E1',
                  borderRadius: 10,
                  fontSize: 13.5,
                  outline: 'none',
                  boxSizing: 'border-box',
                  background: 'white'
                }}
              >
                <option value="E-commerce">E-commerce</option>
                <option value="Retail">Retail Store</option>
                <option value="Digital Services">Digital Services</option>
                <option value="Food & Grocery">Food & Grocery</option>
                <option value="Subscription">Subscription</option>
              </select>
            </div>
            <div>
              <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
                Default MFS Number
              </label>
              <input
                type="text"
                placeholder="017XXXXXXXX (bKash/Nagad)"
                value={defaultNumber}
                onChange={e => setDefaultNumber(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  border: '1px solid #CBD5E1',
                  borderRadius: 10,
                  fontSize: 13.5,
                  outline: 'none',
                  boxSizing: 'border-box'
                }}
              />
            </div>
          </div>

          <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                flex: 1,
                padding: '12px 0',
                background: '#F1F5F9',
                border: 'none',
                borderRadius: 10,
                fontWeight: 700,
                color: '#475569',
                cursor: 'pointer'
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                flex: 2,
                padding: '12px 0',
                background: '#2563EB',
                border: 'none',
                borderRadius: 10,
                fontWeight: 700,
                color: 'white',
                cursor: loading ? 'not-allowed' : 'pointer',
                opacity: loading ? 0.7 : 1,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8
              }}
            >
              {loading ? (
                <>
                  <Loader2 size={16} className="spin" />
                  <span>Creating...</span>
                </>
              ) : (
                <>
                  <UserPlus size={16} />
                  <span>Register Merchant</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
