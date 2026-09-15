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
import KycReviews from './pages/KycReviews'
import { AuthProvider, RequireAdmin } from './auth'
import { ToastProvider } from './components/ToastProvider'
import AdminLayout from './components/AdminLayout'

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          
          <Route path="/dashboard" element={
            <RequireAdmin>
              <AdminLayout>
                <Dashboard />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/merchants" element={
            <RequireAdmin>
              <AdminLayout>
                <Merchants />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/merchants/:id" element={
            <RequireAdmin>
              <AdminLayout>
                <MerchantDetail />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/kyc-reviews" element={
            <RequireAdmin>
              <AdminLayout>
                <KycReviews />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/submissions" element={
            <RequireAdmin>
              <AdminLayout>
                <Submissions />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/connect-supabase" element={
            <RequireAdmin>
              <AdminLayout>
                <ConnectSupabase />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/settings" element={
            <RequireAdmin>
              <AdminLayout>
                <SystemSettings />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/gateway-settings" element={
            <RequireAdmin>
              <AdminLayout>
                <GatewaySettings />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/support" element={
            <RequireAdmin>
              <AdminLayout>
                <SupportHelpdesk />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/analytics" element={
            <RequireAdmin>
              <AdminLayout>
                <PaymentAnalytics />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/health" element={
            <RequireAdmin>
              <AdminLayout>
                <SystemHealth />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/mfs-patterns" element={
            <RequireAdmin>
              <AdminLayout>
                <MfsRegexManager />
              </AdminLayout>
            </RequireAdmin>
          } />
          
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </ToastProvider>
    </AuthProvider>
  )
}
