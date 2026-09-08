import React, { useState } from 'react'
import { adminSupabase } from '../adminSupabaseClient'
import { useNavigate } from 'react-router-dom'

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nav = useNavigate()

  async function doEmailLogin() {
    setLoading(true)
    setError(null)
    try {
      const { error: authErr } = await adminSupabase.auth.signInWithPassword({ email, password })
      if (authErr) throw authErr
      nav('/dashboard')
    } catch (e: any) {
      setError(e.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  async function doGoogleLogin() {
    setLoading(true)
    setError(null)
    try {
      const { error: authErr } = await adminSupabase.auth.signInWithOAuth({
        provider: 'google',
        options: { redirectTo: window.location.origin + '/dashboard' }
      })
      if (authErr) throw authErr
    } catch (e: any) {
      setError(e.message || 'Google login failed')
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #0F172A 0%, #1E293B 50%, #0F172A 100%)',
      padding: 16
    }}>
      <div style={{
        maxWidth: 420,
        width: '100%',
        background: 'white',
        borderRadius: 16,
        padding: '40px 32px',
        boxShadow: '0 25px 50px rgba(0,0,0,0.25)'
      }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ fontSize: 36, marginBottom: 8 }}>⚡</div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>SwapnoPay Admin</h1>
          <p style={{ color: '#64748B', fontSize: 13, marginTop: 6 }}>Sign in to your platform control panel</p>
        </div>

        {error && (
          <div style={{
            padding: '10px 14px',
            background: '#FEF2F2',
            border: '1px solid #FECACA',
            borderRadius: 8,
            color: '#DC2626',
            fontSize: 13,
            fontWeight: 600,
            marginBottom: 16
          }}>
            {error}
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <input
            className="input"
            type="email"
            placeholder="Email address"
            value={email}
            onChange={e => setEmail(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && doEmailLogin()}
            style={{ padding: '12px 14px', borderRadius: 10, fontSize: 14 }}
          />
          <input
            className="input"
            type="password"
            placeholder="Password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && doEmailLogin()}
            style={{ padding: '12px 14px', borderRadius: 10, fontSize: 14 }}
          />
          <button
            className="button"
            onClick={doEmailLogin}
            disabled={loading}
            style={{
              background: '#4F46E5',
              padding: '12px 0',
              borderRadius: 10,
              fontWeight: 700,
              fontSize: 14,
              opacity: loading ? 0.6 : 1,
              cursor: loading ? 'not-allowed' : 'pointer'
            }}
          >
            {loading ? 'Signing in...' : 'Sign in with Email'}
          </button>
        </div>

        <div style={{
          display: 'flex', alignItems: 'center', gap: 12,
          margin: '20px 0', color: '#94A3B8', fontSize: 12
        }}>
          <div style={{ flex: 1, height: 1, background: '#E2E8F0' }} />
          <span>OR</span>
          <div style={{ flex: 1, height: 1, background: '#E2E8F0' }} />
        </div>

        <button
          onClick={doGoogleLogin}
          disabled={loading}
          style={{
            width: '100%',
            padding: '12px 0',
            borderRadius: 10,
            border: '1px solid #E2E8F0',
            background: 'white',
            cursor: loading ? 'not-allowed' : 'pointer',
            fontWeight: 600,
            fontSize: 14,
            color: '#1E293B',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 10
          }}
        >
          <svg width="18" height="18" viewBox="0 0 48 48">
            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
          </svg>
          Sign in with Google
        </button>

        <p style={{ textAlign: 'center', marginTop: 20, fontSize: 11, color: '#94A3B8' }}>
          SwapnoPay Platform — Secured by Supabase Auth
        </p>
      </div>
    </div>
  )
}
