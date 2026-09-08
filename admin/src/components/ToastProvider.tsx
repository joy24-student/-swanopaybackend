import React, { createContext, useContext, useEffect, useState } from 'react'
import { adminSupabase } from '../adminSupabaseClient'

export interface AdminNotification {
  id: string
  title: string
  message: string
  type: 'PAYMENT_EVENT' | 'TICKET_CREATED' | 'SYSTEM_ALERT'
  read: boolean
  metadata?: any
  created_at: string
}

interface ToastContextType {
  notifications: AdminNotification[]
  unreadCount: number
  markAsRead: (id: string) => Promise<void>
  markAllAsRead: () => Promise<void>
  dismissToast: (id: string) => void
}

const ToastContext = createContext<ToastContextType>({
  notifications: [],
  unreadCount: 0,
  markAsRead: async () => {},
  markAllAsRead: async () => {},
  dismissToast: () => {},
})

export const useNotifications = () => useContext(ToastContext)

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [notifications, setNotifications] = useState<AdminNotification[]>([])
  const [activeToasts, setActiveToasts] = useState<AdminNotification[]>([])
  const [showNotificationCenter, setShowNotificationCenter] = useState(false)

  // Fetch initial unread notifications
  const fetchNotifications = async () => {
    try {
      const { data, error } = await adminSupabase
        .from('admin_notifications')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(20)
      if (!error && data) {
        setNotifications(data as AdminNotification[])
      }
    } catch (e) {
      console.error('[ToastProvider] fetch error:', e)
    }
  }

  useEffect(() => {
    fetchNotifications()

    // Subscribe to realtime inserts on admin_notifications table
    const channel = adminSupabase
      .channel('admin_notifications_realtime')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'admin_notifications' }, (payload) => {
        const newNotif = payload.new as AdminNotification
        setNotifications(prev => [newNotif, ...prev])
        setActiveToasts(prev => [newNotif, ...prev])

        // Auto dismiss toast after 6 seconds
        setTimeout(() => {
          setActiveToasts(prev => prev.filter(t => t.id !== newNotif.id))
        }, 6000)
      })
      .subscribe()

    return () => { adminSupabase.removeChannel(channel) }
  }, [])

  const markAsRead = async (id: string) => {
    try {
      await adminSupabase.from('admin_notifications').update({ read: true }).eq('id', id)
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n))
    } catch (e) {
      console.error('[ToastProvider] markAsRead error:', e)
    }
  }

  const markAllAsRead = async () => {
    try {
      await adminSupabase.from('admin_notifications').update({ read: true }).eq('read', false)
      setNotifications(prev => prev.map(n => ({ ...n, read: true })))
    } catch (e) {
      console.error('[ToastProvider] markAllAsRead error:', e)
    }
  }

  const dismissToast = (id: string) => {
    setActiveToasts(prev => prev.filter(t => t.id !== id))
  }

  const unreadCount = notifications.filter(n => !n.read).length

  return (
    <ToastContext.Provider value={{ notifications, unreadCount, markAsRead, markAllAsRead, dismissToast }}>
      {children}

      {/* Floating Bell & Notification Center Toggle Button */}
      <div style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 9999 }}>
        <button
          onClick={() => setShowNotificationCenter(prev => !prev)}
          style={{
            width: 52, height: 52, borderRadius: 26, background: '#4F46E5', color: 'white',
            border: 'none', boxShadow: '0 4px 14px rgba(79, 70, 229, 0.4)', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22,
            position: 'relative'
          }}
          title="Notification Center"
        >
          🔔
          {unreadCount > 0 && (
            <span style={{
              position: 'absolute', top: -2, right: -2, background: '#EF4444', color: 'white',
              borderRadius: 10, padding: '2px 6px', fontSize: 11, fontWeight: 800, border: '2px solid white'
            }}>
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>
      </div>

      {/* Notification Center Modal Drawer */}
      {showNotificationCenter && (
        <div style={{
          position: 'fixed', bottom: 80, right: 20, width: 360, maxHeight: 480,
          background: 'white', borderRadius: 12, boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
          zIndex: 9999, border: '1px solid #E2E8F0', overflow: 'hidden', display: 'flex', flexDirection: 'column'
        }}>
          <div style={{ padding: '12px 16px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <strong style={{ fontSize: 14, color: '#1E293B' }}>🔔 Admin Notifications ({unreadCount} unread)</strong>
            {unreadCount > 0 && (
              <button onClick={markAllAsRead} style={{ background: 'none', border: 'none', color: '#4F46E5', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                Mark all read
              </button>
            )}
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: 10, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {notifications.length === 0 ? (
              <div style={{ padding: 20, textAlign: 'center', color: '#94A3B8', fontSize: 12 }}>No notifications recorded yet.</div>
            ) : (
              notifications.map(n => (
                <div
                  key={n.id}
                  onClick={() => markAsRead(n.id)}
                  style={{
                    padding: 10, borderRadius: 8, cursor: 'pointer',
                    background: n.read ? '#F8FAFC' : '#EEF2FF',
                    border: `1px solid ${n.read ? '#E2E8F0' : '#818CF8'}`,
                    transition: 'all 0.15s'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontWeight: 700, fontSize: 12, color: n.read ? '#475569' : '#1E293B' }}>{n.title}</span>
                    <span style={{ fontSize: 9, color: '#94A3B8' }}>{new Date(n.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                  </div>
                  <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{n.message}</div>
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {/* Real-time Toast Banners (Top Right Popup) */}
      <div style={{ position: 'fixed', top: 20, right: 20, zIndex: 10000, display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 380, width: '100%', pointerEvents: 'none' }}>
        {activeToasts.map(toast => (
          <div
            key={toast.id}
            style={{
              pointerEvents: 'auto', padding: '12px 16px', borderRadius: 10, background: '#1E293B', color: 'white',
              boxShadow: '0 8px 24px rgba(0,0,0,0.25)', borderLeft: `5px solid ${toast.type === 'PAYMENT_EVENT' ? '#10B981' : toast.type === 'TICKET_CREATED' ? '#F59E0B' : '#EF4444'}`,
              display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10,
              animation: 'slideIn 0.3s ease-out'
            }}
          >
            <div>
              <div style={{ fontWeight: 700, fontSize: 13 }}>{toast.title}</div>
              <div style={{ fontSize: 12, opacity: 0.9, marginTop: 2 }}>{toast.message}</div>
            </div>
            <button
              onClick={() => dismissToast(toast.id)}
              style={{ background: 'none', border: 'none', color: '#94A3B8', fontSize: 16, cursor: 'pointer', padding: 0 }}
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
