import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Merchants from './pages/Merchants'
import MerchantDetail from './pages/MerchantDetail'
import Submissions from './pages/Submissions'
import ConnectSupabase from './pages/ConnectSupabase'
import SystemSettings from './pages/SystemSettings'
import GatewaySettings from './pages/GatewaySettings'
import SupportHelpdesk from './pages/SupportHelpdesk'
import PaymentAnalytics from './pages/PaymentAnalytics'
import SystemHealth from './pages/SystemHealth'
import MfsRegexManager from './pages/MfsRegexManager'
import { AuthProvider, RequireAdmin } from './auth'
import { ToastProvider } from './components/ToastProvider'

export default function App(){
  return (
    <AuthProvider>
      <ToastProvider>
        <Routes>
          <Route path="/login" element={<Login/>} />
          <Route path="/dashboard" element={<RequireAdmin><Dashboard/></RequireAdmin>} />
          <Route path="/merchants" element={<RequireAdmin><Merchants/></RequireAdmin>} />
          <Route path="/merchants/:id" element={<RequireAdmin><MerchantDetail/></RequireAdmin>} />
          <Route path="/submissions" element={<RequireAdmin><Submissions/></RequireAdmin>} />
          <Route path="/connect-supabase" element={<RequireAdmin><ConnectSupabase/></RequireAdmin>} />
          <Route path="/settings" element={<RequireAdmin><SystemSettings/></RequireAdmin>} />
          <Route path="/gateway-settings" element={<RequireAdmin><GatewaySettings/></RequireAdmin>} />
          <Route path="/support" element={<RequireAdmin><SupportHelpdesk/></RequireAdmin>} />
          <Route path="/analytics" element={<RequireAdmin><PaymentAnalytics/></RequireAdmin>} />
          <Route path="/health" element={<RequireAdmin><SystemHealth/></RequireAdmin>} />
          <Route path="/mfs-patterns" element={<RequireAdmin><MfsRegexManager/></RequireAdmin>} />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </ToastProvider>
    </AuthProvider>
  )
}
