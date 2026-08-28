import React, { useEffect, useState } from 'react'
import { ref, onValue, set, update } from 'firebase/database'
import { collection, getDocs, query, orderBy, setDoc, doc } from 'firebase/firestore'
import { rtdb, db } from '../firebaseConfig'
import { Link } from 'react-router-dom'

interface SupportTicket {
  id: string
  merchantId?: string
  businessName?: string
  email?: string
  phone?: string
  category: string
  subject: string
  description: string
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
  createdAt: number
  adminReply?: string
  resolvedAt?: number
}

interface FeatureRequest {
  id: string
  merchantId?: string
  businessName?: string
  email?: string
  phone?: string
  title: string
  category: string
  description: string
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'PENDING' | 'UNDER_REVIEW' | 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED'
  adminNotes?: string
  timestamp: number
}

interface ChatMessage {
  id: string
  merchantId: string
  sender: 'MERCHANT' | 'PLATFORM_OWNER' | 'AI_SUPPORT'
  message: string
  timestamp: number
}

export default function SupportHelpdesk() {
  const [activeTab, setActiveTab] = useState<'tickets' | 'live_chat' | 'feature_requests'>('tickets')
  const [tickets, setTickets] = useState<SupportTicket[]>([])
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null)
  const [adminReplyText, setAdminReplyText] = useState('')
  const [ticketActionMsg, setTicketActionMsg] = useState('')

  // Feature Requests state
  const [featureRequests, setFeatureRequests] = useState<FeatureRequest[]>([])
  const [featureFilter, setFeatureFilter] = useState<string>('ALL')
  const [selectedFeature, setSelectedFeature] = useState<FeatureRequest | null>(null)
  const [featureAdminNotes, setFeatureAdminNotes] = useState<string>('')

  // Live chat state
  const [chatThreads, setChatThreads] = useState<Record<string, ChatMessage[]>>({})
  const [selectedMerchantId, setSelectedMerchantId] = useState<string>('')
  const [chatReplyInput, setChatReplyInput] = useState('')

  // 0. Listen to Feature Requests
  useEffect(() => {
    try {
      const featRef = ref(rtdb, 'platform_owner/feature_requests')
      const unsub = onValue(featRef, (snapshot) => {
        if (snapshot.exists()) {
          const val = snapshot.val()
          const list: FeatureRequest[] = Object.keys(val).map((k) => ({
            id: k,
            ...val[k]
          }))
          list.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
          setFeatureRequests(list)
        } else {
          setFeatureRequests([])
        }
      })
      return () => unsub()
    } catch (e) {
      console.error("Feature requests listener error:", e)
    }
  }, [])

  // 1. Listen to Realtime Support Tickets
  useEffect(() => {
    try {
      const ticketsRef = ref(rtdb, 'platform_owner/tickets')
      const unsub = onValue(ticketsRef, (snapshot) => {
        if (snapshot.exists()) {
          const val = snapshot.val()
          const list: SupportTicket[] = Object.keys(val).map((k) => ({
            id: k,
            ...val[k]
          }))
          list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
          setTickets(list)
        } else {
          fetchFirestoreTickets()
        }
      }, (err) => {
        console.warn("RTDB tickets listener error, trying Firestore:", err)
        fetchFirestoreTickets()
      })

      return () => unsub()
    } catch (e) {
      console.error(e)
      fetchFirestoreTickets()
    }
  }, [])

  const fetchFirestoreTickets = async () => {
    try {
      const q = query(collection(db, 'support_tickets'), orderBy('createdAt', 'desc'))
      const snap = await getDocs(q)
      const list = snap.docs.map(d => ({ id: d.id, ...d.data() } as SupportTicket))
      if (list.length > 0) setTickets(list)
    } catch (e) {
      console.error("Firestore tickets error:", e)
    }
  }

  // 2. Listen to Live Support Chats
  useEffect(() => {
    try {
      const chatsRef = ref(rtdb, 'platform_owner/live_chats')
      const unsub = onValue(chatsRef, (snapshot) => {
        if (snapshot.exists()) {
          const val = snapshot.val()
          const threads: Record<string, ChatMessage[]> = {}
          Object.keys(val).forEach((merchantId) => {
            const mChats = val[merchantId]
            const msgs: ChatMessage[] = Object.keys(mChats).map(msgId => ({
              id: msgId,
              ...mChats[msgId]
            }))
            msgs.sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0))
            threads[merchantId] = msgs
          })
          setChatThreads(threads)
          if (!selectedMerchantId && Object.keys(threads).length > 0) {
            setSelectedMerchantId(Object.keys(threads)[0])
          }
        }
      })
      return () => unsub()
    } catch (e) {
      console.error("Live chats listener error:", e)
    }
  }, [selectedMerchantId])

  // Update Ticket Status & Send Reply
  const handleUpdateTicketStatus = async (ticket: SupportTicket, newStatus: SupportTicket['status']) => {
    setTicketActionMsg('Updating status...')
    try {
      const updates: any = { status: newStatus }
      if (adminReplyText.trim()) {
        updates.adminReply = adminReplyText.trim()
      }
      if (newStatus === 'RESOLVED' || newStatus === 'CLOSED') {
        updates.resolvedAt = Date.now()
      }

      await update(ref(rtdb, platform_owner/tickets/), updates)

      if (ticket.merchantId) {
        await update(ref(rtdb, merchants//support_tickets/), updates)
      }

      try {
        await setDoc(doc(db, 'support_tickets', ticket.id), { ...ticket, ...updates }, { merge: true })
      } catch (_) {}

      setTicketActionMsg(Ticket # marked as !)
      setSelectedTicket(prev => prev ? { ...prev, ...updates } : null)
      setAdminReplyText('')
      setTimeout(() => setTicketActionMsg(''), 4000)
    } catch (err: any) {
      setTicketActionMsg(Error: )
    }
  }

  // Send Admin Chat Reply
  const handleSendChatReply = async () => {
    if (!selectedMerchantId || !chatReplyInput.trim()) return
    const msgText = chatReplyInput.trim()
    setChatReplyInput('')
    try {
      const newMsgId = msg_
      const chatPayload: ChatMessage = {
        id: newMsgId,
        merchantId: selectedMerchantId,
        sender: 'PLATFORM_OWNER',
        message: msgText,
        timestamp: Date.now()
      }

      await set(ref(rtdb, platform_owner/live_chats//), chatPayload)
      await set(ref(rtdb, merchants//support_chat/), chatPayload)
    } catch (e: any) {
      console.error("Chat send error:", e)
      alert("Failed to send chat reply: " + e.message)
    }
  }

  // Update Feature Request Status & Notes
  const handleUpdateFeatureStatus = async (feature: FeatureRequest, newStatus: FeatureRequest['status']) => {
    setTicketActionMsg('Updating feature request...')
    try {
      const updates: any = { status: newStatus }
      if (featureAdminNotes.trim()) {
        updates.adminNotes = featureAdminNotes.trim()
      }

      await update(ref(rtdb, `platform_owner/feature_requests/${feature.id}`), updates)

      if (feature.merchantId) {
        await update(ref(rtdb, `merchants/${feature.merchantId}/feature_requests/${feature.id}`), updates)
      }

      setTicketActionMsg(`Feature '${feature.title}' status updated to ${newStatus}!`)
      setSelectedFeature(prev => prev ? { ...prev, ...updates } : null)
      setFeatureAdminNotes('')
      setTimeout(() => setTicketActionMsg(''), 4000)
    } catch (err: any) {
      setTicketActionMsg(`Error: ${err.message}`)
    }
  }

  const filteredTickets = tickets.filter(t => statusFilter === 'ALL' || t.status === statusFilter)
  const filteredFeatures = featureRequests.filter(f => featureFilter === 'ALL' || f.status === featureFilter)

  return (
    <div className="container">
      <div className="header">
        <div>
          <h1>Support & Helpdesk</h1>
          <p style={{ margin: 0, color: '#64748B', fontSize: 13 }}>
            Manage merchant support tickets, incoming feature requests, and real-time live support chat
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to="/settings"><button className="button" style={{ background: '#0F172A' }}>⚙️ System Links & Config</button></Link>
          <Link to="/dashboard"><button className="button" style={{ background: '#64748B' }}>Dashboard</button></Link>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
        <button
          onClick={() => setActiveTab('tickets')}
          className="button"
          style={{
            background: activeTab === 'tickets' ? '#4F46E5' : '#E2E8F0',
            color: activeTab === 'tickets' ? 'white' : '#1E293B',
            fontWeight: 'bold',
            padding: '10px 18px'
          }}
        >
          🎫 Support Tickets ({tickets.filter(t => t.status === 'OPEN').length} Open)
        </button>
        <button
          onClick={() => setActiveTab('feature_requests')}
          className="button"
          style={{
            background: activeTab === 'feature_requests' ? '#4F46E5' : '#E2E8F0',
            color: activeTab === 'feature_requests' ? 'white' : '#1E293B',
            fontWeight: 'bold',
            padding: '10px 18px'
          }}
        >
          💡 Feature Requests ({featureRequests.filter(f => f.status === 'PENDING' || f.status === 'UNDER_REVIEW').length} New)
        </button>
        <button
          onClick={() => setActiveTab('live_chat')}
          className="button"
          style={{
            background: activeTab === 'live_chat' ? '#4F46E5' : '#E2E8F0',
            color: activeTab === 'live_chat' ? 'white' : '#1E293B',
            fontWeight: 'bold',
            padding: '10px 18px'
          }}
        >
          💬 Live Chat Console ({Object.keys(chatThreads).length} Active)
        </button>
      </div>

      {ticketActionMsg && (
        <div style={{ padding: '10px 14px', background: '#ECFDF5', border: '1px solid #10B981', borderRadius: 8, color: '#065F46', marginBottom: 14, fontWeight: 600 }}>
          {ticketActionMsg}
        </div>
      )}

      {activeTab === 'tickets' ? (
        <div style={{ display: 'grid', gridTemplateColumns: selectedTicket ? '1fr 1fr' : '1fr', gap: 16 }}>
          <div className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ margin: 0 }}>Merchant Tickets</h3>
              <div style={{ display: 'flex', gap: 6 }}>
                {['ALL', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'].map(status => (
                  <button
                    key={status}
                    onClick={() => setStatusFilter(status)}
                    style={{
                      padding: '4px 8px',
                      fontSize: 11,
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      background: statusFilter === status ? '#3B82F6' : 'white',
                      color: statusFilter === status ? 'white' : '#475569',
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    {status}
                  </button>
                ))}
              </div>
            </div>

            {filteredTickets.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: '#94A3B8' }}>No support tickets found for this filter.</div>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Ticket</th>
                    <th>Merchant</th>
                    <th>Category</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTickets.map(t => (
                    <tr
                      key={t.id}
                      style={{ background: selectedTicket?.id === t.id ? '#F1F5F9' : 'transparent', cursor: 'pointer' }}
                      onClick={() => setSelectedTicket(t)}
                    >
                      <td><strong>#{t.id.slice(-6)}</strong></td>
                      <td>
                        <div>{t.businessName || t.merchantId || 'Merchant'}</div>
                        <div style={{ fontSize: 11, color: '#64748B' }}>{t.phone || t.email}</div>
                      </td>
                      <td><span style={{ background: '#EFF6FF', color: '#1D4ED8', padding: '2px 6px', borderRadius: 4, fontSize: 11 }}>{t.category}</span></td>
                      <td>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: 6,
                          fontSize: 11,
                          fontWeight: 700,
                          background: t.status === 'OPEN' ? '#FEF2F2' : t.status === 'IN_PROGRESS' ? '#FFFBEB' : '#ECFDF5',
                          color: t.status === 'OPEN' ? '#DC2626' : t.status === 'IN_PROGRESS' ? '#D97706' : '#059669'
                        }}>
                          {t.status}
                        </span>
                      </td>
                      <td style={{ fontSize: 12 }}>{new Date(t.createdAt).toLocaleDateString()}</td>
                      <td>
                        <button
                          className="button"
                          style={{ padding: '4px 8px', fontSize: 11, background: '#3B82F6' }}
                          onClick={(e) => { e.stopPropagation(); setSelectedTicket(t); }}
                        >
                          View & Reply
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {selectedTicket && (
            <div className="card" style={{ border: '2px solid #6366F1' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3 style={{ margin: 0 }}>Ticket #{selectedTicket.id.slice(-6)}</h3>
                <button
                  onClick={() => setSelectedTicket(null)}
                  style={{ background: 'none', border: 'none', fontSize: 18, cursor: 'pointer', color: '#64748B' }}
                >
                  ✕
                </button>
              </div>

              <div style={{ marginTop: 12, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 13, background: '#F8FAFC', padding: 10, borderRadius: 8 }}>
                <div><strong>Merchant:</strong> {selectedTicket.businessName || selectedTicket.merchantId}</div>
                <div><strong>Category:</strong> {selectedTicket.category}</div>
                <div><strong>Phone:</strong> {selectedTicket.phone || '--'}</div>
                <div><strong>Email:</strong> {selectedTicket.email || '--'}</div>
                <div><strong>Submitted:</strong> {new Date(selectedTicket.createdAt).toLocaleString()}</div>
                <div>
                  <strong>Status: </strong>
                  <span style={{
                    fontWeight: 'bold',
                    color: selectedTicket.status === 'OPEN' ? '#DC2626' : selectedTicket.status === 'IN_PROGRESS' ? '#D97706' : '#059669'
                  }}>
                    {selectedTicket.status}
                  </span>
                </div>
              </div>

              <div style={{ marginTop: 14 }}>
                <h4 style={{ margin: '0 0 6px 0', fontSize: 14 }}>Subject: {selectedTicket.subject}</h4>
                <div style={{ background: '#F1F5F9', padding: 12, borderRadius: 8, fontSize: 13, whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>
                  {selectedTicket.description}
                </div>
              </div>

              {selectedTicket.adminReply && (
                <div style={{ marginTop: 12 }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: 13, color: '#047857' }}>Previous Admin Resolution/Reply:</h4>
                  <div style={{ background: '#ECFDF5', padding: 10, borderRadius: 8, fontSize: 12.5, color: '#065F46' }}>
                    {selectedTicket.adminReply}
                  </div>
                </div>
              )}

              <div style={{ marginTop: 16 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, marginBottom: 4 }}>
                  Admin Reply / Resolution Note:
                </label>
                <textarea
                  value={adminReplyText}
                  onChange={(e) => setAdminReplyText(e.target.value)}
                  placeholder="Enter response to merchant (will be delivered to merchant app & email)..."
                  style={{ width: '100%', minHeight: 70, padding: 8, borderRadius: 8, border: '1px solid #CBD5E1', fontFamily: 'inherit', fontSize: 13 }}
                />
              </div>

              <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button
                  className="button"
                  style={{ background: '#F59E0B' }}
                  onClick={() => handleUpdateTicketStatus(selectedTicket, 'IN_PROGRESS')}
                >
                  Mark In Progress
                </button>
                <button
                  className="button"
                  style={{ background: '#10B981' }}
                  onClick={() => handleUpdateTicketStatus(selectedTicket, 'RESOLVED')}
                >
                  Resolve Ticket
                </button>
                <button
                  className="button"
                  style={{ background: '#64748B' }}
                  onClick={() => handleUpdateTicketStatus(selectedTicket, 'CLOSED')}
                >
                  Close Ticket
                </button>
                <button
                  className="button"
                  style={{ background: '#EF4444' }}
                  onClick={() => handleUpdateTicketStatus(selectedTicket, 'OPEN')}
                >
                  Re-Open
                </button>
              </div>
            </div>
          )}
        </div>
      ) : activeTab === 'feature_requests' ? (
        <div style={{ display: 'grid', gridTemplateColumns: selectedFeature ? '1fr 1fr' : '1fr', gap: 16 }}>
          <div className="card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ margin: 0 }}>Merchant Feature Requests</h3>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {['ALL', 'PENDING', 'UNDER_REVIEW', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'].map(status => (
                  <button
                    key={status}
                    onClick={() => setFeatureFilter(status)}
                    style={{
                      padding: '4px 8px',
                      fontSize: 11,
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      background: featureFilter === status ? '#4F46E5' : 'white',
                      color: featureFilter === status ? 'white' : '#475569',
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    {status}
                  </button>
                ))}
              </div>
            </div>

            {filteredFeatures.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center', color: '#94A3B8' }}>No feature requests found for this filter.</div>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>Feature Title</th>
                    <th>Merchant</th>
                    <th>Category</th>
                    <th>Priority</th>
                    <th>Status</th>
                    <th>Date</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredFeatures.map(f => (
                    <tr
                      key={f.id}
                      style={{ background: selectedFeature?.id === f.id ? '#F1F5F9' : 'transparent', cursor: 'pointer' }}
                      onClick={() => { setSelectedFeature(f); setFeatureAdminNotes(f.adminNotes || '') }}
                    >
                      <td><strong>{f.title}</strong></td>
                      <td>{f.businessName || f.merchantId?.slice(-6) || 'Merchant'}</td>
                      <td><span className="badge" style={{ background: '#EEF2FF', color: '#4338CA' }}>{f.category}</span></td>
                      <td>
                        <span className="badge" style={{
                          background: f.priority === 'CRITICAL' ? '#FEE2E2' : f.priority === 'HIGH' ? '#FEF3C7' : f.priority === 'MEDIUM' ? '#EFF6FF' : '#F0FDF4',
                          color: f.priority === 'CRITICAL' ? '#DC2626' : f.priority === 'HIGH' ? '#D97706' : f.priority === 'MEDIUM' ? '#2563EB' : '#16A34A',
                          fontWeight: 'bold'
                        }}>
                          {f.priority}
                        </span>
                      </td>
                      <td>
                        <span className="badge" style={{
                          background: f.status === 'COMPLETED' ? '#ECFDF5' : f.status === 'PLANNED' || f.status === 'IN_PROGRESS' ? '#EFF6FF' : f.status === 'REJECTED' ? '#FEF2F2' : '#FEF3C7',
                          color: f.status === 'COMPLETED' ? '#059669' : f.status === 'PLANNED' || f.status === 'IN_PROGRESS' ? '#2563EB' : f.status === 'REJECTED' ? '#DC2626' : '#D97706',
                          fontWeight: 'bold'
                        }}>
                          {f.status}
                        </span>
                      </td>
                      <td style={{ fontSize: 12 }}>{new Date(f.timestamp).toLocaleDateString()}</td>
                      <td>
                        <button
                          className="button"
                          style={{ padding: '4px 8px', fontSize: 11, background: '#4F46E5' }}
                          onClick={(e) => { e.stopPropagation(); setSelectedFeature(f); setFeatureAdminNotes(f.adminNotes || '') }}
                        >
                          Review & Update
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {selectedFeature && (
            <div className="card" style={{ border: '2px solid #8B5CF6' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h3 style={{ margin: 0 }}>Feature: {selectedFeature.title}</h3>
                <button
                  onClick={() => setSelectedFeature(null)}
                  style={{ background: 'none', border: 'none', fontSize: 18, cursor: 'pointer', color: '#64748B' }}
                >
                  ✕
                </button>
              </div>

              <div style={{ marginTop: 12, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 13, background: '#F8FAFC', padding: 10, borderRadius: 8 }}>
                <div><strong>Merchant:</strong> {selectedFeature.businessName || selectedFeature.merchantId}</div>
                <div><strong>Category:</strong> {selectedFeature.category}</div>
                <div><strong>Priority:</strong> <span style={{ fontWeight: 'bold' }}>{selectedFeature.priority}</span></div>
                <div><strong>Submitted:</strong> {new Date(selectedFeature.timestamp).toLocaleString()}</div>
                <div><strong>Email:</strong> {selectedFeature.email || '--'}</div>
                <div>
                  <strong>Status: </strong>
                  <span style={{ fontWeight: 'bold', color: '#4F46E5' }}>{selectedFeature.status}</span>
                </div>
              </div>

              <div style={{ marginTop: 14 }}>
                <h4 style={{ margin: '0 0 6px 0', fontSize: 14 }}>Use Case & Description:</h4>
                <div style={{ background: '#F1F5F9', padding: 12, borderRadius: 8, fontSize: 13, whiteSpace: 'pre-wrap', lineHeight: 1.5 }}>
                  {selectedFeature.description}
                </div>
              </div>

              <div style={{ marginTop: 14 }}>
                <h4 style={{ margin: '0 0 6px 0', fontSize: 14 }}>Admin Roadmap Notes:</h4>
                <textarea
                  value={featureAdminNotes}
                  onChange={(e) => setFeatureAdminNotes(e.target.value)}
                  placeholder="Enter internal roadmap notes or response..."
                  style={{ width: '100%', minHeight: 70, padding: 8, borderRadius: 8, border: '1px solid #CBD5E1', fontFamily: 'inherit', fontSize: 13 }}
                />
              </div>

              <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button
                  className="button"
                  style={{ background: '#3B82F6' }}
                  onClick={() => handleUpdateFeatureStatus(selectedFeature, 'UNDER_REVIEW')}
                >
                  Mark Under Review
                </button>
                <button
                  className="button"
                  style={{ background: '#8B5CF6' }}
                  onClick={() => handleUpdateFeatureStatus(selectedFeature, 'PLANNED')}
                >
                  Accept & Plan
                </button>
                <button
                  className="button"
                  style={{ background: '#F59E0B' }}
                  onClick={() => handleUpdateFeatureStatus(selectedFeature, 'IN_PROGRESS')}
                >
                  In Progress
                </button>
                <button
                  className="button"
                  style={{ background: '#10B981' }}
                  onClick={() => handleUpdateFeatureStatus(selectedFeature, 'COMPLETED')}
                >
                  Mark Completed
                </button>
                <button
                  className="button"
                  style={{ background: '#EF4444' }}
                  onClick={() => handleUpdateFeatureStatus(selectedFeature, 'REJECTED')}
                >
                  Reject
                </button>
              </div>
            </div>
          )}
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>
          <div className="card" style={{ padding: 12 }}>
            <h3 style={{ margin: '0 0 10px 0', fontSize: 15 }}>Merchant Threads</h3>
            {Object.keys(chatThreads).length === 0 ? (
              <div style={{ padding: 16, color: '#94A3B8', fontSize: 12 }}>No active chat sessions.</div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {Object.keys(chatThreads).map(mId => {
                  const lastMsg = chatThreads[mId].slice(-1)[0]
                  const isSelected = selectedMerchantId === mId
                  return (
                    <div
                      key={mId}
                      onClick={() => setSelectedMerchantId(mId)}
                      style={{
                        padding: '10px 12px',
                        borderRadius: 8,
                        background: isSelected ? '#EFF6FF' : '#F8FAFC',
                        border: isSelected ? '1.5px solid #3B82F6' : '1px solid #E2E8F0',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ fontWeight: 'bold', fontSize: 13, color: '#1E293B' }}>Merchant #{mId.slice(-6)}</div>
                      <div style={{ fontSize: 11, color: '#64748B', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', marginTop: 2 }}>
                        {lastMsg ? ${lastMsg.sender === 'PLATFORM_OWNER' ? 'You: ' : ''} : 'No messages'}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          <div className="card" style={{ display: 'flex', flexDirection: 'column', height: 520 }}>
            <div style={{ paddingBottom: 10, borderBottom: '1px solid #E2E8F0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 16 }}>Live Chat with Merchant #{selectedMerchantId.slice(-6) || 'None'}</h3>
                <span style={{ fontSize: 11, color: '#10B981' }}>● Connected via Firebase Realtime Database</span>
              </div>
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: '12px 0', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {(!selectedMerchantId || !chatThreads[selectedMerchantId] || chatThreads[selectedMerchantId].length === 0) ? (
                <div style={{ margin: 'auto', color: '#94A3B8', fontSize: 13 }}>Select a thread to view live support chat.</div>
              ) : (
                chatThreads[selectedMerchantId].map((msg) => {
                  const isMe = msg.sender === 'PLATFORM_OWNER'
                  const isAi = msg.sender === 'AI_SUPPORT'
                  return (
                    <div
                      key={msg.id}
                      style={{
                        alignSelf: isMe ? 'flex-end' : 'flex-start',
                        maxWidth: '75%',
                        background: isMe ? '#4F46E5' : isAi ? '#F1F5F9' : '#FFFFFF',
                        color: isMe ? '#FFFFFF' : '#0F172A',
                        border: isMe ? 'none' : '1px solid #E2E8F0',
                        borderRadius: 12,
                        padding: '10px 14px',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
                      }}
                    >
                      <div style={{ fontSize: 10.5, fontWeight: 'bold', color: isMe ? '#E0E7FF' : isAi ? '#6366F1' : '#0284C7', marginBottom: 2 }}>
                        {isMe ? 'Platform Admin (You)' : isAi ? 'AI Support Bot' : 'Merchant'}
                      </div>
                      <div style={{ fontSize: 13, lineHeight: 1.4 }}>{msg.message}</div>
                      <div style={{ fontSize: 9.5, textAlign: 'right', color: isMe ? '#CBD5E1' : '#94A3B8', marginTop: 4 }}>
                        {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </div>
                  )
                })
              )}
            </div>

            <div style={{ display: 'flex', gap: 8, paddingTop: 10, borderTop: '1px solid #E2E8F0' }}>
              <input
                type="text"
                placeholder="Type reply to merchant..."
                value={chatReplyInput}
                onChange={(e) => setChatReplyInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') handleSendChatReply() }}
                style={{ flex: 1, padding: '10px 14px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 13 }}
              />
              <button
                className="button"
                onClick={handleSendChatReply}
                style={{ background: '#4F46E5', fontWeight: 'bold', padding: '10px 20px' }}
              >
                Send Reply
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
