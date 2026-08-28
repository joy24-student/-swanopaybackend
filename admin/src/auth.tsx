import React, { createContext, useContext, useEffect, useState } from 'react'
import { onAuthStateChanged, getIdTokenResult, signOut as fbSignOut } from 'firebase/auth'
import { auth } from './firebaseConfig'
import { Navigate } from 'react-router-dom'

const AuthContext = createContext<any>(null)

export function AuthProvider({ children }: { children: React.ReactNode }){
  const [user,setUser]=useState<any|null>(null)
  const [isAdmin,setIsAdmin]=useState(false)
  const [loading,setLoading]=useState(true)

  useEffect(()=>{
    const unsub = onAuthStateChanged(auth, async u => {
      if(!u){ setUser(null); setIsAdmin(false); setLoading(false); return }
      setUser(u)
      try{
        const idr = await getIdTokenResult(u)
        setIsAdmin(Boolean((idr.claims as any).admin))
      }catch(e){ setIsAdmin(false) }
      setLoading(false)
    })
    return unsub
  },[])

  async function signOut(){ await fbSignOut(auth) }

  return <AuthContext.Provider value={{ user, isAdmin, loading, signOut }}>{children}</AuthContext.Provider>
}

export function useAuth(){ return useContext(AuthContext) }

export function RequireAdmin({ children }: { children: JSX.Element }){
  const { user, isAdmin, loading } = useAuth()
  if(loading) return <div className="container"><div className="card">Checking auth...</div></div>
  if(!user) return <Navigate to="/login" replace />
  if(!isAdmin) return <div className="container"><div className="card">Unauthorized — admin access required.</div></div>
  return children
}

export default AuthContext
