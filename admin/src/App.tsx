import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Merchants from './pages/Merchants'
import MerchantDetail from './pages/MerchantDetail'
import Submissions from './pages/Submissions'
import ConnectSupabase from './pages/ConnectSupabase'
import { AuthProvider, RequireAdmin } from './auth'

export default function App(){
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login/>} />
        <Route path="/dashboard" element={<RequireAdmin><Dashboard/></RequireAdmin>} />
        <Route path="/merchants" element={<RequireAdmin><Merchants/></RequireAdmin>} />
        <Route path="/merchants/:id" element={<RequireAdmin><MerchantDetail/></RequireAdmin>} />
        <Route path="/submissions" element={<RequireAdmin><Submissions/></RequireAdmin>} />
        <Route path="/connect-supabase" element={<RequireAdmin><ConnectSupabase/></RequireAdmin>} />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </AuthProvider>
  )
}
