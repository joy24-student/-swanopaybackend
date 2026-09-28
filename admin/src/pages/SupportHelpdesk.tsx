import React, { useEffect, useState } from 'react'
import { adminSupabase } from '../adminSupabaseClient'
import { Link } from 'react-router-dom'
import {
  LifeBuoy,
  Ticket,
  MessageSquare,
  Lightbulb,
  Check,
  X,
  Send,
  Search,
  Settings,
  RefreshCw,
  Clock,
  User,
} from 'lucide-react'

interface SupportTicket {
  id: string
  merchant_id?: string
  business_name?: string
  email?: string
  phone?: string
  category: string
  subject: string
  description: string
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
  created_at: string
  admin_reply?: string
  resolved_at?: string
}

interface FeatureRequest {
  id: string
  merchant_id?: string
  business_name?: string
  email?: string
  phone?: string
  title: string
  category: string
  description: string
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'PENDING' | 'UNDER_REVIEW' | 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED'
  admin_notes?: string
  created_at: string
}

interface ChatMessage {
  id: string
  merchant_id: string
  sender: 'MERCHANT' | 'PLATFORM_OWNER' | 'AI_SUPPORT'
  message: string
  created_at: string
}

export default function SupportHelpdesk() {
  const [activeTab, setActiveTab] = useState<'tickets' | 'live_chat' | 'feature_requests'>('tickets')

  // Tickets state
  const [tickets, setTickets] = useState<SupportTicket[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null)
  const [adminReplyText, setAdminReplyText] = useState('')
  const [ticketActionMsg, setTicketActionMsg] = useState('')
  const [ticketsLoading, setTicketsLoading] = useState(true)

  // Feature Requests state
  const [featureRequests, setFeatureRequests] = useState<FeatureRequest[]>([])
  const [featureFilter, setFeatureFilter] = useState<string>('ALL')
  const [selectedFeature, setSelectedFeature] = useState<FeatureRequest | null>(null)
  const [featureAdminNotes, setFeatureAdminNotes] = useState<string>('')
  const [featuresLoading, setFeaturesLoading] = useState(true)

  // Live Chat state
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [selectedMerchantId, setSelectedMerchantId] = useState<string>('')
  const [chatReplyInput, setChatReplyInput] = useState('')
  const [chatLoading, setChatLoading] = useState(true)

  const getBackendBase = () => {
    if ((import.meta as any).env?.VITE_BACKEND_URL) {
      return ((import.meta as any).env.VITE_BACKEND_URL as string).replace(/\/$/, '')
    }
    if (typeof window !== 'undefined') {
      if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        return 'http://localhost:4000'
      }
    }
    return 'https://api.swapnopay.top'
  }

  const getAdminHeaders = (): Record<string, string> => {
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    }
    const masterSecret = typeof sessionStorage !== 'undefined'
      ? (sessionStorage.getItem('swapnopay_admin_secret') || localStorage.getItem('swapnopay_admin_secret'))
      : null
    if (masterSecret) {
      headers['X-Admin-Secret'] = masterSecret
    }
    return headers
  }

  // 1. Fetch & Subscribe Tickets
  const fetchTickets = async () => {
    try {
      let sbTickets: SupportTicket[] = []
      let apiTickets: SupportTicket[] = []

      // Fetch from Supabase
      try {
        const { data, error } = await adminSupabase
          .from('support_tickets')
          .select('*')
          .order('created_at', { ascending: false })
        if (!error && Array.isArray(data)) {
          sbTickets = data as SupportTicket[]
        }
      } catch (sbErr) {
        console.warn('[SupportHelpdesk] Supabase tickets fetch notice:', sbErr)
      }

      // Fetch from Backend API
      try {
        const base = getBackendBase()
        const res = await fetch(`${base}/v1/merchant/support/tickets?merchant_id=ALL`, {
          headers: getAdminHeaders()
        })
        if (res.ok) {
          const json = await res.json()
          if (json.ok && Array.isArray(json.tickets)) {
            apiTickets = json.tickets
          }
        }
      } catch (apiErr) {
        console.warn('[SupportHelpdesk] Backend API tickets fetch notice:', apiErr)
      }

      // Merge results by id, preferring newest data
      const ticketMap = new Map<string, SupportTicket>()
      for (const t of apiTickets) {
        if (t && t.id) ticketMap.set(t.id, t)
      }
      for (const t of sbTickets) {
        if (t && t.id) {
          const existing = ticketMap.get(t.id)
          if (!existing) {
            ticketMap.set(t.id, t)
          } else {
            ticketMap.set(t.id, {
              ...existing,
              ...t,
              admin_reply: t.admin_reply || existing.admin_reply,
              resolved_at: t.resolved_at || existing.resolved_at,
              status: t.status || existing.status,
            })
          }
        }
      }

      const merged = Array.from(ticketMap.values()).sort(
        (a, b) => new Date(b.created_at || 0).getTime() - new Date(a.created_at || 0).getTime()
      )
      setTickets(merged)
    } catch (e) {
      console.error('[SupportHelpdesk] Tickets fetch error:', e)
    } finally {
      setTicketsLoading(false)
    }
  }

  useEffect(() => {
    fetchTickets()

    const channel = adminSupabase
      .channel('tickets_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'support_tickets' }, () => {
        fetchTickets()
      })
      .subscribe()

    return () => { adminSupabase.removeChannel(channel) }
  }, [])

  // 2. Fetch & Subscribe Feature Requests
  const fetchFeatureRequests = async () => {
    try {
      let sbFeatures: FeatureRequest[] = []
      let apiFeatures: FeatureRequest[] = []

      // Fetch from Supabase
      try {
        const { data, error } = await adminSupabase
          .from('feature_requests')
          .select('*')
          .order('created_at', { ascending: false })
        if (!error && Array.isArray(data)) {
          sbFeatures = data as FeatureRequest[]
        }
      } catch (sbErr) {
        console.warn('[SupportHelpdesk] Supabase feature requests fetch notice:', sbErr)
      }

      // Fetch from Backend API
      try {
        const base = getBackendBase()
        const res = await fetch(`${base}/v1/merchant/support/features?merchant_id=ALL`, {
          headers: getAdminHeaders()
        })
        if (res.ok) {
          const json = await res.json()
          if (json.ok && Array.isArray(json.features)) {
            apiFeatures = json.features
          }
        }
      } catch (apiErr) {
        console.warn('[SupportHelpdesk] Backend API feature requests fetch notice:', apiErr)
      }

      const featureMap = new Map<string, FeatureRequest>()
      for (const f of apiFeatures) {
        if (f && f.id) featureMap.set(f.id, f)
      }
      for (const f of sbFeatures) {
        if (f && f.id) {
          const existing = featureMap.get(f.id)
          if (!existing) {
            featureMap.set(f.id, f)
          } else {
            featureMap.set(f.id, {
              ...existing,
              ...f,
              admin_notes: f.admin_notes || existing.admin_notes,
              status: f.status || existing.status,
            })
          }
        }
      }

      const merged = Array.from(featureMap.values()).sort(
        (a, b) => new Date(b.created_at || 0).getTime() - new Date(a.created_at || 0).getTime()
      )
      setFeatureRequests(merged)
    } catch (e) {
      console.error('[SupportHelpdesk] Feature requests fetch error:', e)
    } finally {
      setFeaturesLoading(false)
    }
  }

  useEffect(() => {
    fetchFeatureRequests()

    const channel = adminSupabase
      .channel('features_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'feature_requests' }, () => {
        fetchFeatureRequests()
      })
      .subscribe()

    return () => { adminSupabase.removeChannel(channel) }
  }, [])

  // 3. Fetch & Subscribe Live Chat Messages
  const fetchChatMessages = async () => {
    try {
      let sbMessages: ChatMessage[] = []
      let apiMessages: ChatMessage[] = []

      // Fetch from Supabase
      try {
        const { data, error } = await adminSupabase
          .from('live_chat_messages')
          .select('*')
          .order('created_at', { ascending: true })
        if (!error && Array.isArray(data)) {
          sbMessages = data as ChatMessage[]
        }
      } catch (sbErr) {
        console.warn('[SupportHelpdesk] Supabase chat fetch notice:', sbErr)
      }

      // Fetch from Backend API
      try {
        const base = getBackendBase()
        const res = await fetch(`${base}/v1/merchant/support/chat?merchant_id=ALL`, {
          headers: getAdminHeaders()
        })
        if (res.ok) {
          const json = await res.json()
          if (json.ok && Array.isArray(json.messages)) {
            apiMessages = json.messages
          }
        }
      } catch (apiErr) {
        console.warn('[SupportHelpdesk] Backend API chat fetch notice:', apiErr)
      }

      const msgMap = new Map<string, ChatMessage>()
      for (const m of apiMessages) {
        if (m && m.id) msgMap.set(m.id, m)
      }
      for (const m of sbMessages) {
        if (m && m.id) msgMap.set(m.id, m)
      }

      const merged = Array.from(msgMap.values()).sort(
        (a, b) => new Date(a.created_at || 0).getTime() - new Date(b.created_at || 0).getTime()
      )
      setChatMessages(merged)

      // Default select first merchant if none selected
      if (!selectedMerchantId && merged.length > 0) {
        const merchants = Array.from(new Set(merged.map((m: any) => m.merchant_id)))
        if (merchants.length > 0) setSelectedMerchantId(merchants[0] as string)
      }
    } catch (e) {
      console.error('[SupportHelpdesk] Chat fetch error:', e)
    } finally {
      setChatLoading(false)
    }
  }

  useEffect(() => {
    fetchChatMessages()

    const channel = adminSupabase
      .channel('live_chat_realtime')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'live_chat_messages' }, () => {
        fetchChatMessages()
      })
      .subscribe()

    return () => { adminSupabase.removeChannel(channel) }
  }, [])

  // Ticket status update
  const handleUpdateTicketStatus = async (ticket: SupportTicket, newStatus: SupportTicket['status']) => {
    setTicketActionMsg('Updating status...')
    try {
      const updates: any = {
        status: newStatus,
        admin_reply: adminReplyText.trim() || ticket.admin_reply || null,
        resolved_at: (newStatus === 'RESOLVED' || newStatus === 'CLOSED') ? new Date().toISOString() : null,
      }

      // 1. Sync to Backend API
      try {
        const base = getBackendBase()
        await fetch(`${base}/v1/merchant/support/tickets/${ticket.id}`, {
          method: 'PATCH',
          headers: getAdminHeaders(),
          body: JSON.stringify(updates),
        })
      } catch (apiErr) {
        console.warn('[SupportHelpdesk] Backend ticket update notice:', apiErr)
      }

      // 2. Sync to Supabase
      try {
        await adminSupabase
          .from('support_tickets')
          .update(updates)
          .eq('id', ticket.id)
      } catch (sbErr) {
        console.warn('[SupportHelpdesk] Supabase ticket update notice:', sbErr)
      }

      setTicketActionMsg(`Ticket #${ticket.id.slice(0, 8)} marked as ${newStatus}!`)
      setSelectedTicket(prev => prev ? { ...prev, ...updates } : null)
      setAdminReplyText('')
      await fetchTickets()
      setTimeout(() => setTicketActionMsg(''), 4000)
    } catch (err: any) {
      setTicketActionMsg(`Error: ${err.message}`)
    }
  }

  // Feature request update
  const handleUpdateFeatureStatus = async (feature: FeatureRequest, newStatus: FeatureRequest['status']) => {
    setTicketActionMsg('Updating feature request...')
    try {
      const updates: any = {
        status: newStatus,
        admin_notes: featureAdminNotes.trim() || feature.admin_notes || null,
      }

      // 1. Sync to Backend API
      try {
        const base = getBackendBase()
        await fetch(`${base}/v1/merchant/support/features/${feature.id}`, {
          method: 'PATCH',
          headers: getAdminHeaders(),
          body: JSON.stringify(updates),
        })
      } catch (apiErr) {
        console.warn('[SupportHelpdesk] Backend feature update notice:', apiErr)
      }

      // 2. Sync to Supabase
      try {
        await adminSupabase
          .from('feature_requests')
          .update(updates)
          .eq('id', feature.id)
      } catch (sbErr) {
        console.warn('[SupportHelpdesk] Supabase feature update notice:', sbErr)
      }

      setTicketActionMsg(`Feature request marked as ${newStatus}!`)
      setSelectedFeature(prev => prev ? { ...prev, ...updates } : null)
      setFeatureAdminNotes('')
      await fetchFeatureRequests()
      setTimeout(() => setTicketActionMsg(''), 4000)
    } catch (err: any) {
      setTicketActionMsg(`Error: ${err.message}`)
    }
  }

  // Send live chat reply
  const handleSendChatReply = async () => {
    if (!selectedMerchantId || !chatReplyInput.trim()) return
    const msgText = chatReplyInput.trim()
    setChatReplyInput('')
    try {
      const payload = {
        merchant_id: selectedMerchantId,
        sender: 'PLATFORM_OWNER',
        message: msgText,
        created_at: new Date().toISOString(),
      }

      // 1. Send to Backend API
      try {
        const base = getBackendBase()
        await fetch(`${base}/v1/merchant/support/chat`, {
          method: 'POST',
          headers: getAdminHeaders(),
          body: JSON.stringify(payload),
        })
      } catch (apiErr) {
        console.warn('[SupportHelpdesk] Backend chat send notice:', apiErr)
      }

      // 2. Send to Supabase
      try {
        await adminSupabase
          .from('live_chat_messages')
          .insert(payload)
      } catch (sbErr) {
        console.warn('[SupportHelpdesk] Supabase chat send notice:', sbErr)
      }

      await fetchChatMessages()
    } catch (e: any) {
      alert('Failed to send chat reply: ' + e.message)
    }
  }

  // Group chat messages by merchant_id
  const merchantIds: string[] = Array.from(new Set(chatMessages.map(m => m.merchant_id)))
  const activeMerchantMessages = chatMessages.filter(m => m.merchant_id === selectedMerchantId)

  // Filtered tickets
  const filteredTickets = tickets.filter(t => statusFilter === 'ALL' || t.status === statusFilter)

  // Filtered features
  const filteredFeatures = featureRequests.filter(f => featureFilter === 'ALL' || f.status === featureFilter)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)', margin: 0, letterSpacing: '-0.02em', display: 'flex', alignItems: 'center', gap: 8 }}>
              <LifeBuoy size={22} color="var(--brand-primary)" />
              Helpdesk & Merchant Support
            </h1>
          </div>
          <p style={{ margin: 0, color: 'var(--text-secondary)', fontSize: 13 }}>
            Real-time support tickets, feature requests, and live merchant chat.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            onClick={() => {
              if (activeTab === 'tickets') fetchTickets()
              else if (activeTab === 'live_chat') fetchChatMessages()
              else fetchFeatureRequests()
            }}
            className="btn btn-secondary btn-sm"
          >
            <RefreshCw size={13} />
            Refresh
          </button>
          <Link to="/settings" className="btn btn-secondary btn-sm">
            <Settings size={13} />
            System CMS
          </Link>
          <Link to="/dashboard" className="btn btn-primary btn-sm">
            Overview Dashboard
          </Link>
        </div>
      </div>

      {/* Action Banner */}
      {ticketActionMsg && (
        <div style={{
          padding: '10px 16px',
          background: ticketActionMsg.startsWith('Error') ? 'var(--danger-subtle)' : 'var(--success-subtle)',
          border: `1px solid ${ticketActionMsg.startsWith('Error') ? 'var(--danger-border)' : 'var(--success)'}`,
          borderRadius: 8,
          color: ticketActionMsg.startsWith('Error') ? 'var(--danger)' : 'var(--success)',
          fontWeight: 600, fontSize: 13
        }}>
          {ticketActionMsg}
        </div>
      )}

      {/* Tab Navigation */}
      <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border-default)', paddingBottom: 2 }}>
        {[
          { key: 'tickets', label: `Support Tickets (${tickets.filter(t => t.status === 'OPEN').length} Open)`, icon: Ticket },
          { key: 'live_chat', label: `Live Merchant Chat (${merchantIds.length} Threads)`, icon: MessageSquare },
          { key: 'feature_requests', label: `Feature Requests (${featureRequests.length})`, icon: Lightbulb },
        ].map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key as any)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 7,
                padding: '9px 14px',
                fontSize: 13,
                fontWeight: isActive ? 600 : 500,
                color: isActive ? 'var(--brand-primary)' : 'var(--text-secondary)',
                background: isActive ? 'var(--brand-subtle)' : 'transparent',
                border: 'none',
                borderBottom: isActive ? '2px solid var(--brand-primary)' : '2px solid transparent',
                borderRadius: '6px 6px 0 0',
                cursor: 'pointer',
                whiteSpace: 'nowrap',
                transition: 'all var(--transition-fast)',
              }}
            >
              <Icon size={14} />
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* ── TAB 1: SUPPORT TICKETS ── */}
      {activeTab === 'tickets' && (
        <div>
          {/* Status Filter */}
          <div className="card" style={{ marginBottom: 14, padding: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: '#64748B' }}>Filter Status:</span>
            {['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].map(st => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                style={{
                  padding: '4px 10px', borderRadius: 6, border: 'none', cursor: 'pointer', fontSize: 11, fontWeight: 700,
                  background: statusFilter === st ? '#4F46E5' : '#F1F5F9',
                  color: statusFilter === st ? 'white' : '#475569'
                }}
              >
                {st}
              </button>
            ))}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: selectedTicket ? '1fr 1fr' : '1fr', gap: 14 }}>
            {/* Ticket List */}
            <div className="card">
              <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Ticket size={16} color="var(--brand-primary)" />
                Support Tickets ({filteredTickets.length})
              </h3>
              {ticketsLoading ? (
                <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-secondary)' }}>Loading support tickets...</div>
              ) : filteredTickets.length === 0 ? (
                <div style={{ padding: 30, textAlign: 'center', color: 'var(--text-muted)' }}>No tickets match the selected filter.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {filteredTickets.map(t => (
                    <div
                      key={t.id}
                      onClick={() => setSelectedTicket(t)}
                      style={{
                        padding: 12, borderRadius: 8, cursor: 'pointer',
                        border: `1px solid ${selectedTicket?.id === t.id ? 'var(--brand-primary)' : 'var(--border-default)'}`,
                        background: selectedTicket?.id === t.id ? 'var(--brand-subtle)' : 'var(--bg-surface)',
                        transition: 'all 0.15s'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong style={{ fontSize: 14, color: 'var(--text-primary)' }}>{t.subject}</strong>
                        <span className={`status-pill ${t.status === 'OPEN' ? 'danger' : t.status === 'IN_PROGRESS' ? 'warning' : 'success'}`}>
                          <span className="status-dot" />
                          {t.status}
                        </span>
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
                        Merchant: {t.business_name || t.merchant_id || 'Guest'} • Category: {t.category}
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 2 }}>
                        Submitted: {new Date(t.created_at).toLocaleString()}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Ticket Inspector Detail */}
            {selectedTicket && (
              <div className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Ticket size={16} color="var(--brand-primary)" />
                    Ticket Details
                  </h3>
                  <button className="btn-ghost btn-icon" onClick={() => setSelectedTicket(null)}>
                    <X size={15} />
                  </button>
                </div>
                <div style={{ fontSize: 13, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div><strong>Subject:</strong> {selectedTicket.subject}</div>
                  <div><strong>Category:</strong> <span style={{ background: 'var(--bg-subtle)', padding: '2px 6px', borderRadius: 4, fontSize: 11 }}>{selectedTicket.category}</span></div>
                  <div><strong>Merchant:</strong> {selectedTicket.business_name || '--'} ({selectedTicket.email || '--'})</div>
                  <div><strong>Status:</strong> <span style={{ fontWeight: 700 }}>{selectedTicket.status}</span></div>
                  <div>
                    <strong>Description:</strong>
                    <div style={{ padding: 10, background: 'var(--bg-subtle)', borderRadius: 6, border: '1px solid var(--border-default)', marginTop: 4, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                      {selectedTicket.description}
                    </div>
                  </div>

                  {selectedTicket.admin_reply && (
                    <div>
                      <strong>Previous Admin Reply:</strong>
                      <div style={{ padding: 10, background: 'var(--success-subtle)', borderRadius: 6, border: '1px solid var(--success)', marginTop: 4, fontSize: 12, color: 'var(--success)' }}>
                        {selectedTicket.admin_reply}
                      </div>
                    </div>
                  )}

                  <div style={{ marginTop: 10 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4 }}>Admin Response Message</label>
                    <textarea
                      className="textarea"
                      value={adminReplyText}
                      onChange={e => setAdminReplyText(e.target.value)}
                      placeholder="Type your resolution or support reply here..."
                      style={{ minHeight: 70, fontFamily: 'inherit' }}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
                    <button className="btn btn-secondary btn-sm" onClick={() => handleUpdateTicketStatus(selectedTicket, 'IN_PROGRESS')}>
                      Mark In Progress
                    </button>
                    <button className="btn btn-primary btn-sm" onClick={() => handleUpdateTicketStatus(selectedTicket, 'RESOLVED')}>
                      <Check size={13} />
                      Resolve Ticket
                    </button>
                    <button className="btn btn-secondary btn-sm" onClick={() => handleUpdateTicketStatus(selectedTicket, 'CLOSED')}>
                      Close Ticket
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── TAB 2: LIVE MERCHANT CHAT ── */}
      {activeTab === 'live_chat' && (
        <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 14 }}>
          {/* Thread List */}
          <div className="card" style={{ padding: 12 }}>
            <h4 style={{ marginTop: 0, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 7 }}>
              <MessageSquare size={15} color="var(--brand-primary)" />
              Active Threads
            </h4>
            {chatLoading ? (
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Loading chats...</div>
            ) : merchantIds.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>No live chat threads yet.</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {merchantIds.map(mId => {
                  const lastMsg = chatMessages.filter(m => m.merchant_id === mId).slice(-1)[0]
                  return (
                    <div
                      key={mId}
                      onClick={() => setSelectedMerchantId(mId)}
                      style={{
                        padding: 10, borderRadius: 8, cursor: 'pointer',
                        border: `1px solid ${selectedMerchantId === mId ? 'var(--brand-primary)' : 'var(--border-default)'}`,
                        background: selectedMerchantId === mId ? 'var(--brand-subtle)' : 'var(--bg-surface)',
                      }}
                    >
                      <div style={{ fontWeight: 700, fontSize: 12, color: 'var(--text-primary)' }}>Merchant: {mId.slice(0, 12)}...</div>
                      {lastMsg && (
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginTop: 2 }}>
                          {lastMsg.sender === 'PLATFORM_OWNER' ? 'You: ' : ''}{lastMsg.message}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Chat Window */}
          <div className="card" style={{ display: 'flex', flexDirection: 'column', height: 500 }}>
            {selectedMerchantId ? (
              <>
                <div style={{ paddingBottom: 10, borderBottom: '1px solid var(--border-default)', marginBottom: 10 }}>
                  <strong style={{ fontSize: 14, display: 'inline-flex', alignItems: 'center', gap: 7 }}>
                    <MessageSquare size={15} color="var(--brand-primary)" />
                    Conversation with Merchant <code>{selectedMerchantId}</code>
                  </strong>
                  <span style={{ fontSize: 11, color: 'var(--success)', marginLeft: 10 }}>● Live Connected</span>
                </div>

                {/* Messages Box */}
                <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10, paddingRight: 6 }}>
                  {activeMerchantMessages.length === 0 ? (
                    <div style={{ color: 'var(--text-muted)', fontSize: 12, textAlign: 'center', marginTop: 40 }}>No messages in this chat yet.</div>
                  ) : (
                    activeMerchantMessages.map(msg => {
                      const isOwner = msg.sender === 'PLATFORM_OWNER'
                      return (
                        <div
                          key={msg.id}
                          style={{
                            alignSelf: isOwner ? 'flex-end' : 'flex-start',
                            maxWidth: '75%', padding: '8px 12px', borderRadius: 10,
                            background: isOwner ? 'var(--brand-primary)' : 'var(--bg-subtle)',
                            color: isOwner ? '#FFFFFF' : 'var(--text-primary)',
                            fontSize: 13,
                          }}
                        >
                          <div>{msg.message}</div>
                          <div style={{ fontSize: 10, opacity: 0.7, marginTop: 4, textAlign: 'right' }}>
                            {new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </div>
                        </div>
                      )
                    })
                  )}
                </div>

                {/* Reply Input */}
                <div style={{ marginTop: 10, display: 'flex', gap: 8 }}>
                  <input
                    className="input"
                    value={chatReplyInput}
                    onChange={e => setChatReplyInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSendChatReply()}
                    placeholder="Type a message to merchant..."
                  />
                  <button className="btn btn-primary btn-sm" onClick={handleSendChatReply} style={{ minWidth: 80 }}>
                    <Send size={13} />
                    Send
                  </button>
                </div>
              </>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)' }}>
                Select a merchant thread to start chatting.
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── TAB 3: FEATURE REQUESTS ── */}
      {activeTab === 'feature_requests' && (
        <div>
          <div className="card" style={{ marginBottom: 14, padding: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-secondary)' }}>Filter Status:</span>
            {['ALL', 'PENDING', 'UNDER_REVIEW', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'].map(st => (
              <button
                key={st}
                onClick={() => setFeatureFilter(st)}
                style={{
                  padding: '4px 10px', borderRadius: 6, border: 'none', cursor: 'pointer', fontSize: 11, fontWeight: 700,
                  background: featureFilter === st ? 'var(--brand-primary)' : 'var(--bg-subtle)',
                  color: featureFilter === st ? 'white' : 'var(--text-secondary)'
                }}
              >
                {st}
              </button>
            ))}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: selectedFeature ? '1fr 1fr' : '1fr', gap: 14 }}>
            {/* Feature List */}
            <div className="card">
              <h3 style={{ marginTop: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                <Lightbulb size={16} color="var(--brand-primary)" />
                Merchant Feature Requests ({filteredFeatures.length})
              </h3>
              {featuresLoading ? (
                <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-secondary)' }}>Loading feature requests...</div>
              ) : filteredFeatures.length === 0 ? (
                <div style={{ padding: 30, textAlign: 'center', color: 'var(--text-muted)' }}>No feature requests match the selected filter.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {filteredFeatures.map(f => (
                    <div
                      key={f.id}
                      onClick={() => setSelectedFeature(f)}
                      style={{
                        padding: 12, borderRadius: 8, cursor: 'pointer',
                        border: `1px solid ${selectedFeature?.id === f.id ? 'var(--brand-primary)' : 'var(--border-default)'}`,
                        background: selectedFeature?.id === f.id ? 'var(--brand-subtle)' : 'var(--bg-surface)',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong style={{ fontSize: 14, color: 'var(--text-primary)' }}>{f.title}</strong>
                        <span className={`status-pill ${f.status === 'COMPLETED' ? 'success' : f.status === 'PLANNED' ? 'info' : 'neutral'}`}>
                          <span className="status-dot" />
                          {f.status}
                        </span>
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
                        Merchant: {f.business_name || f.merchant_id || 'Guest'} • Category: {f.category} • Priority: <span style={{ fontWeight: 700 }}>{f.priority}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Feature Detail Inspector */}
            {selectedFeature && (
              <div className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Lightbulb size={16} color="var(--brand-primary)" />
                    Feature Request Details
                  </h3>
                  <button className="btn-ghost btn-icon" onClick={() => setSelectedFeature(null)}>
                    <X size={15} />
                  </button>
                </div>
                <div style={{ fontSize: 13, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div><strong>Title:</strong> {selectedFeature.title}</div>
                  <div><strong>Category:</strong> {selectedFeature.category}</div>
                  <div><strong>Merchant:</strong> {selectedFeature.business_name || selectedFeature.merchant_id || '--'} {selectedFeature.email ? `(${selectedFeature.email})` : ''}</div>
                  <div><strong>Priority:</strong> {selectedFeature.priority}</div>
                  <div><strong>Status:</strong> {selectedFeature.status}</div>
                  <div>
                    <strong>Description:</strong>
                    <div style={{ padding: 10, background: 'var(--bg-subtle)', borderRadius: 6, border: '1px solid var(--border-default)', marginTop: 4, fontSize: 12 }}>
                      {selectedFeature.description}
                    </div>
                  </div>

                  {selectedFeature.admin_notes && (
                    <div>
                      <strong>Admin Notes:</strong>
                      <div style={{ padding: 10, background: 'var(--brand-subtle)', borderRadius: 6, border: '1px solid var(--border-default)', marginTop: 4, fontSize: 12 }}>
                        {selectedFeature.admin_notes}
                      </div>
                    </div>
                  )}

                  <div style={{ marginTop: 10 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4 }}>Admin Roadmap Note</label>
                    <textarea
                      className="textarea"
                      value={featureAdminNotes}
                      onChange={e => setFeatureAdminNotes(e.target.value)}
                      placeholder="Add notes for merchant or dev team..."
                      style={{ minHeight: 60, fontFamily: 'inherit' }}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
                    <button className="btn btn-secondary btn-sm" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'UNDER_REVIEW')}>
                      Under Review
                    </button>
                    <button className="btn btn-secondary btn-sm" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'PLANNED')}>
                      Plan for Release
                    </button>
                    <button className="btn btn-primary btn-sm" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'COMPLETED')}>
                      <Check size={13} />
                      Complete Feature
                    </button>
                    <button className="btn btn-danger btn-sm" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'REJECTED')}>
                      Reject
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
