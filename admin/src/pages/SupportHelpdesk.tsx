import React, { useEffect, useState } from 'react'
import { adminSupabase } from '../adminSupabaseClient'
import { Link } from 'react-router-dom'

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

  // 1. Fetch & Subscribe Tickets
  const fetchTickets = async () => {
    try {
      const { data, error } = await adminSupabase
        .from('support_tickets')
        .select('*')
        .order('created_at', { ascending: false })
      if (!error && data) {
        setTickets(data as SupportTicket[])
      }
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
      const { data, error } = await adminSupabase
        .from('feature_requests')
        .select('*')
        .order('created_at', { ascending: false })
      if (!error && data) {
        setFeatureRequests(data as FeatureRequest[])
      }
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
      const { data, error } = await adminSupabase
        .from('live_chat_messages')
        .select('*')
        .order('created_at', { ascending: true })
      if (!error && data) {
        setChatMessages(data as ChatMessage[])
        // Default select first merchant if none selected
        if (!selectedMerchantId && data.length > 0) {
          const merchants = Array.from(new Set(data.map((m: any) => m.merchant_id)))
          if (merchants.length > 0) setSelectedMerchantId(merchants[0] as string)
        }
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

      const { error } = await adminSupabase
        .from('support_tickets')
        .update(updates)
        .eq('id', ticket.id)

      if (error) throw error

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

      const { error } = await adminSupabase
        .from('feature_requests')
        .update(updates)
        .eq('id', feature.id)

      if (error) throw error

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
      const { error } = await adminSupabase
        .from('live_chat_messages')
        .insert({
          merchant_id: selectedMerchantId,
          sender: 'PLATFORM_OWNER',
          message: msgText,
          created_at: new Date().toISOString(),
        })

      if (error) throw error
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
    <div className="container">
      {/* Header */}
      <div className="header">
        <div>
          <h1>🎫 Helpdesk & Merchant Support</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Real-time support tickets, feature requests, and live merchant chat.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to="/settings">
            <button className="button" style={{ background: '#7C3AED' }}>📋 System CMS</button>
          </Link>
          <Link to="/dashboard">
            <button className="button" style={{ background: '#64748B' }}>Dashboard</button>
          </Link>
        </div>
      </div>

      {/* Action Banner */}
      {ticketActionMsg && (
        <div style={{
          padding: '10px 16px', background: ticketActionMsg.startsWith('Error') ? '#FEF2F2' : '#ECFDF5',
          border: `1px solid ${ticketActionMsg.startsWith('Error') ? '#EF4444' : '#10B981'}`,
          borderRadius: 8, color: ticketActionMsg.startsWith('Error') ? '#991B1B' : '#065F46',
          marginBottom: 16, fontWeight: 600, fontSize: 13
        }}>
          {ticketActionMsg}
        </div>
      )}

      {/* Tab Navigation */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {[
          { key: 'tickets', label: `🎫 Support Tickets (${tickets.filter(t => t.status === 'OPEN').length} Open)` },
          { key: 'live_chat', label: `💬 Live Merchant Chat (${merchantIds.length} Threads)` },
          { key: 'feature_requests', label: `💡 Feature Requests (${featureRequests.length})` },
        ].map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key as any)}
            className="button"
            style={{
              background: activeTab === tab.key ? '#4F46E5' : '#E2E8F0',
              color: activeTab === tab.key ? 'white' : '#1E293B',
              fontWeight: 'bold', padding: '8px 16px', fontSize: 13
            }}
          >
            {tab.label}
          </button>
        ))}
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
              <h3 style={{ marginTop: 0 }}>📋 Support Tickets ({filteredTickets.length})</h3>
              {ticketsLoading ? (
                <div style={{ padding: 20, textAlign: 'center', color: '#64748B' }}>Loading support tickets...</div>
              ) : filteredTickets.length === 0 ? (
                <div style={{ padding: 30, textAlign: 'center', color: '#94A3B8' }}>No tickets match the selected filter.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {filteredTickets.map(t => (
                    <div
                      key={t.id}
                      onClick={() => setSelectedTicket(t)}
                      style={{
                        padding: 12, borderRadius: 8, cursor: 'pointer',
                        border: `1px solid ${selectedTicket?.id === t.id ? '#4F46E5' : '#E2E8F0'}`,
                        background: selectedTicket?.id === t.id ? '#EEF2FF' : 'white',
                        transition: 'all 0.15s'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong style={{ fontSize: 14, color: '#1E293B' }}>{t.subject}</strong>
                        <span style={{
                          padding: '2px 6px', borderRadius: 4, fontSize: 10, fontWeight: 700,
                          background: t.status === 'OPEN' ? '#FEF2F2' : t.status === 'IN_PROGRESS' ? '#FEF3C7' : '#ECFDF5',
                          color: t.status === 'OPEN' ? '#EF4444' : t.status === 'IN_PROGRESS' ? '#D97706' : '#065F46'
                        }}>
                          {t.status}
                        </span>
                      </div>
                      <div style={{ fontSize: 11, color: '#64748B', marginTop: 4 }}>
                        Merchant: {t.business_name || t.merchant_id || 'Guest'} • Category: {t.category}
                      </div>
                      <div style={{ fontSize: 10, color: '#94A3B8', marginTop: 2 }}>
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
                  <h3 style={{ margin: 0 }}>🔍 Ticket Detail</h3>
                  <button onClick={() => setSelectedTicket(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 16 }}>✕</button>
                </div>
                <div style={{ fontSize: 13, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div><strong>Subject:</strong> {selectedTicket.subject}</div>
                  <div><strong>Category:</strong> <span style={{ background: '#F1F5F9', padding: '2px 6px', borderRadius: 4, fontSize: 11 }}>{selectedTicket.category}</span></div>
                  <div><strong>Merchant:</strong> {selectedTicket.business_name || '--'} ({selectedTicket.email || '--'})</div>
                  <div><strong>Status:</strong> <span style={{ fontWeight: 700 }}>{selectedTicket.status}</span></div>
                  <div>
                    <strong>Description:</strong>
                    <div style={{ padding: 10, background: '#F8FAFC', borderRadius: 6, border: '1px solid #E2E8F0', marginTop: 4, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                      {selectedTicket.description}
                    </div>
                  </div>

                  {selectedTicket.admin_reply && (
                    <div>
                      <strong>Previous Admin Reply:</strong>
                      <div style={{ padding: 10, background: '#ECFDF5', borderRadius: 6, border: '1px solid #10B981', marginTop: 4, fontSize: 12, color: '#065F46' }}>
                        {selectedTicket.admin_reply}
                      </div>
                    </div>
                  )}

                  <div style={{ marginTop: 10 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4 }}>Admin Response Message</label>
                    <textarea
                      className="input"
                      value={adminReplyText}
                      onChange={e => setAdminReplyText(e.target.value)}
                      placeholder="Type your resolution or support reply here..."
                      style={{ minHeight: 70, fontFamily: 'inherit' }}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
                    <button className="button" onClick={() => handleUpdateTicketStatus(selectedTicket, 'IN_PROGRESS')} style={{ background: '#D97706', fontSize: 11 }}>
                      Mark In Progress
                    </button>
                    <button className="button" onClick={() => handleUpdateTicketStatus(selectedTicket, 'RESOLVED')} style={{ background: '#10B981', fontSize: 11 }}>
                      ✅ Resolve Ticket
                    </button>
                    <button className="button" onClick={() => handleUpdateTicketStatus(selectedTicket, 'CLOSED')} style={{ background: '#64748B', fontSize: 11 }}>
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
            <h4 style={{ marginTop: 0, marginBottom: 10 }}>💬 Active Threads</h4>
            {chatLoading ? (
              <div style={{ fontSize: 12, color: '#64748B' }}>Loading chats...</div>
            ) : merchantIds.length === 0 ? (
              <div style={{ fontSize: 12, color: '#94A3B8' }}>No live chat threads yet.</div>
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
                        border: `1px solid ${selectedMerchantId === mId ? '#4F46E5' : '#E2E8F0'}`,
                        background: selectedMerchantId === mId ? '#EEF2FF' : 'white',
                      }}
                    >
                      <div style={{ fontWeight: 700, fontSize: 12, color: '#1E293B' }}>Merchant: {mId.slice(0, 12)}...</div>
                      {lastMsg && (
                        <div style={{ fontSize: 11, color: '#64748B', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginTop: 2 }}>
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
                <div style={{ paddingBottom: 10, borderBottom: '1px solid #E2E8F0', marginBottom: 10 }}>
                  <strong style={{ fontSize: 14 }}>💬 Conversation with Merchant <code>{selectedMerchantId}</code></strong>
                  <span style={{ fontSize: 11, color: '#10B981', marginLeft: 10 }}>● Connected via Supabase Realtime</span>
                </div>

                {/* Messages Box */}
                <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 10, paddingRight: 6 }}>
                  {activeMerchantMessages.length === 0 ? (
                    <div style={{ color: '#94A3B8', fontSize: 12, textAlign: 'center', marginTop: 40 }}>No messages in this chat yet.</div>
                  ) : (
                    activeMerchantMessages.map(msg => {
                      const isOwner = msg.sender === 'PLATFORM_OWNER'
                      return (
                        <div
                          key={msg.id}
                          style={{
                            alignSelf: isOwner ? 'flex-end' : 'flex-start',
                            maxWidth: '75%', padding: '8px 12px', borderRadius: 10,
                            background: isOwner ? '#4F46E5' : '#F1F5F9',
                            color: isOwner ? 'white' : '#1E293B',
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
                  <button className="button" onClick={handleSendChatReply} style={{ background: '#4F46E5', width: 90 }}>
                    Send
                  </button>
                </div>
              </>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#94A3B8' }}>
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
            <span style={{ fontSize: 12, fontWeight: 700, color: '#64748B' }}>Filter Status:</span>
            {['ALL', 'PENDING', 'UNDER_REVIEW', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'].map(st => (
              <button
                key={st}
                onClick={() => setFeatureFilter(st)}
                style={{
                  padding: '4px 10px', borderRadius: 6, border: 'none', cursor: 'pointer', fontSize: 11, fontWeight: 700,
                  background: featureFilter === st ? '#4F46E5' : '#F1F5F9',
                  color: featureFilter === st ? 'white' : '#475569'
                }}
              >
                {st}
              </button>
            ))}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: selectedFeature ? '1fr 1fr' : '1fr', gap: 14 }}>
            {/* Feature List */}
            <div className="card">
              <h3 style={{ marginTop: 0 }}>💡 Merchant Feature Requests ({filteredFeatures.length})</h3>
              {featuresLoading ? (
                <div style={{ padding: 20, textAlign: 'center', color: '#64748B' }}>Loading feature requests...</div>
              ) : filteredFeatures.length === 0 ? (
                <div style={{ padding: 30, textAlign: 'center', color: '#94A3B8' }}>No feature requests match the selected filter.</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {filteredFeatures.map(f => (
                    <div
                      key={f.id}
                      onClick={() => setSelectedFeature(f)}
                      style={{
                        padding: 12, borderRadius: 8, cursor: 'pointer',
                        border: `1px solid ${selectedFeature?.id === f.id ? '#4F46E5' : '#E2E8F0'}`,
                        background: selectedFeature?.id === f.id ? '#EEF2FF' : 'white',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <strong style={{ fontSize: 14, color: '#1E293B' }}>{f.title}</strong>
                        <span style={{
                          padding: '2px 6px', borderRadius: 4, fontSize: 10, fontWeight: 700,
                          background: f.status === 'COMPLETED' ? '#ECFDF5' : f.status === 'PLANNED' ? '#E0F2FE' : '#F1F5F9',
                          color: f.status === 'COMPLETED' ? '#065F46' : f.status === 'PLANNED' ? '#0369A1' : '#475569'
                        }}>
                          {f.status}
                        </span>
                      </div>
                      <div style={{ fontSize: 11, color: '#64748B', marginTop: 4 }}>
                        Category: {f.category} • Priority: <span style={{ fontWeight: 700 }}>{f.priority}</span>
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
                  <h3 style={{ margin: 0 }}>🔍 Feature Request Detail</h3>
                  <button onClick={() => setSelectedFeature(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 16 }}>✕</button>
                </div>
                <div style={{ fontSize: 13, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div><strong>Title:</strong> {selectedFeature.title}</div>
                  <div><strong>Category:</strong> {selectedFeature.category}</div>
                  <div><strong>Priority:</strong> {selectedFeature.priority}</div>
                  <div><strong>Status:</strong> {selectedFeature.status}</div>
                  <div>
                    <strong>Description:</strong>
                    <div style={{ padding: 10, background: '#F8FAFC', borderRadius: 6, border: '1px solid #E2E8F0', marginTop: 4, fontSize: 12 }}>
                      {selectedFeature.description}
                    </div>
                  </div>

                  {selectedFeature.admin_notes && (
                    <div>
                      <strong>Admin Notes:</strong>
                      <div style={{ padding: 10, background: '#EFF6FF', borderRadius: 6, border: '1px solid #3B82F6', marginTop: 4, fontSize: 12 }}>
                        {selectedFeature.admin_notes}
                      </div>
                    </div>
                  )}

                  <div style={{ marginTop: 10 }}>
                    <label style={{ display: 'block', fontSize: 11, fontWeight: 700, marginBottom: 4 }}>Admin Roadmap Note</label>
                    <textarea
                      className="input"
                      value={featureAdminNotes}
                      onChange={e => setFeatureAdminNotes(e.target.value)}
                      placeholder="Add notes for merchant or dev team..."
                      style={{ minHeight: 60, fontFamily: 'inherit' }}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}>
                    <button className="button" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'UNDER_REVIEW')} style={{ background: '#D97706', fontSize: 11 }}>
                      Under Review
                    </button>
                    <button className="button" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'PLANNED')} style={{ background: '#0284C7', fontSize: 11 }}>
                      Plan for Release
                    </button>
                    <button className="button" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'COMPLETED')} style={{ background: '#10B981', fontSize: 11 }}>
                      ✅ Complete Feature
                    </button>
                    <button className="button" onClick={() => handleUpdateFeatureStatus(selectedFeature, 'REJECTED')} style={{ background: '#EF4444', fontSize: 11 }}>
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
