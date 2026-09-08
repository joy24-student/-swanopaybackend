// Admin Panel — Supabase Auth Context
// Replaces Firebase Auth entirely. Uses Admin Supabase project for auth.
// Admin access is verified by checking the admin_users table.

import React, { createContext, useContext, useEffect, useState } from 'react'
import { Session, User } from '@supabase/supabase-js'
import { adminSupabase } from './adminSupabaseClient'
import { Navigate } from 'react-router-dom'

interface AuthContextType {
  user: User | null
  session: Session | null
  isAdmin: boolean
  loading: boolean
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  session: null,
  isAdmin: false,
  loading: true,
  signOut: async () => {},
})

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [session, setSession] = useState<Session | null>(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const [loading, setLoading] = useState(true)

  async function checkAdminRole(u: User | null) {
    if (!u) { setIsAdmin(false); return }
    try {
      const { data } = await adminSupabase
        .from('admin_users')
        .select('id')
        .eq('id', u.id)
        .maybeSingle()
      setIsAdmin(!!data)
    } catch {
      setIsAdmin(false)
    }
  }

  useEffect(() => {
    // Restore session
    adminSupabase.auth.getSession().then(({ data: { session: s } }) => {
      setSession(s)
      setUser(s?.user ?? null)
      checkAdminRole(s?.user ?? null).finally(() => setLoading(false))
    })

    // Subscribe to auth changes
    const { data: { subscription } } = adminSupabase.auth.onAuthStateChange((_event, s) => {
      setSession(s)
      setUser(s?.user ?? null)
      checkAdminRole(s?.user ?? null)
    })
    return () => subscription.unsubscribe()
  }, [])

  async function signOut() {
    await adminSupabase.auth.signOut()
  }

  return (
    <AuthContext.Provider value={{ user, session, isAdmin, loading, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() { return useContext(AuthContext) }

export function RequireAdmin({ children }: { children: React.ReactElement | any }) {
  const { user, isAdmin, loading } = useAuth()
  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', background: '#F8FAFC' }}>
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 32, marginBottom: 12 }}>⚡</div>
        <div style={{ color: '#64748B', fontWeight: 600 }}>Checking authentication...</div>
      </div>
    </div>
  )
  if (!user) return <Navigate to="/login" replace />
  if (!isAdmin) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
      <div className="card" style={{ maxWidth: 400, textAlign: 'center' }}>
        <div style={{ fontSize: 40, marginBottom: 12 }}>🔒</div>
        <h2>Unauthorized</h2>
        <p style={{ color: '#64748B' }}>Your account is not registered as an admin. Contact the platform owner.</p>
        <button className="button" onClick={() => adminSupabase.auth.signOut()} style={{ marginTop: 12 }}>
          Sign Out
        </button>
      </div>
    </div>
  )
  return children
}

export default AuthContext
