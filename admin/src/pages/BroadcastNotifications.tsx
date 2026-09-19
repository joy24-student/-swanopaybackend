import React, { useState, useEffect, useMemo } from 'react'
import {
  Megaphone,
  Send,
  Bell,
  AlertTriangle,
  Info,
  CheckCircle2,
  XCircle,
  Smartphone,
  Users,
  RefreshCw,
  Trash2,
  Search,
  Sparkles,
  Layers,
  ArrowRight,
  ShieldAlert,
  Sliders,
  Check
} from 'lucide-react'
import {
  adminSupabase,
  broadcastMerchantNotification,
  fetchBroadcastHistory,
  deleteBroadcastBatch,
  BroadcastPayload,
  BroadcastHistoryItem,
  NotificationType,
  NotificationSeverity
} from '../adminSupabaseClient'
import { useNotifications } from '../components/ToastProvider'

interface MerchantOption {
  id: string
  business_name: string
  status?: string
  email?: string
}

export default function BroadcastNotifications() {
  const { addToast } = useNotifications()

  // Form State
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState('')
  const [type, setType] = useState<NotificationType>('ANNOUNCEMENT')
  const [severity, setSeverity] = useState<NotificationSeverity>('INFO')
  const [targetMode, setTargetMode] = useState<'ALL' | 'ACTIVE' | 'SINGLE'>('ALL')
  const [selectedMerchantId, setSelectedMerchantId] = useState<string>('')
  const [updateBanner, setUpdateBanner] = useState(false)
  const [isSending, setIsSending] = useState(false)

  // Data State
  const [merchants, setMerchants] = useState<MerchantOption[]>([])
  const [history, setHistory] = useState<BroadcastHistoryItem[]>([])
  const [loadingHistory, setLoadingHistory] = useState(true)
  const [historySearch, setHistorySearch] = useState('')
  const [historyFilter, setHistoryFilter] = useState<'ALL' | NotificationSeverity>('ALL')
  const [deletingBatchId, setDeletingBatchId] = useState<string | null>(null)
  const [merchantSearch, setMerchantSearch] = useState('')

  // Load merchants list for single target selector
  const loadMerchants = async () => {
    try {
      const { data, error } = await adminSupabase
        .from('merchants')
        .select('id, business_name, status, email')
        .order('business_name', { ascending: true })

      if (!error && data) {
        setMerchants(data as MerchantOption[])
        if (data.length > 0 && !selectedMerchantId) {
          setSelectedMerchantId(data[0].id)
        }
      }
    } catch (err) {
      console.warn('[BroadcastNotifications] Could not load merchants:', err)
    }
  }

  // Load broadcast history
  const loadHistory = async () => {
    setLoadingHistory(true)
    try {
      const items = await fetchBroadcastHistory(50)
      setHistory(items)
    } catch (err: any) {
      addToast('error', 'Failed to load broadcast history: ' + err.message)
    } finally {
      setLoadingHistory(false)
    }
  }

  useEffect(() => {
    loadMerchants()
    loadHistory()
  }, [])

  // Filtered merchants for dropdown
  const filteredMerchants = useMemo(() => {
    if (!merchantSearch.trim()) return merchants
    const q = merchantSearch.toLowerCase()
    return merchants.filter(
      m =>
        m.business_name?.toLowerCase().includes(q) ||
        m.email?.toLowerCase().includes(q) ||
        m.id.toLowerCase().includes(q)
    )
  }, [merchants, merchantSearch])

  // Filtered history
  const filteredHistory = useMemo(() => {
    return history.filter(item => {
      const matchesSearch =
        !historySearch.trim() ||
        item.title.toLowerCase().includes(historySearch.toLowerCase()) ||
        item.message.toLowerCase().includes(historySearch.toLowerCase())
      const matchesFilter = historyFilter === 'ALL' || item.severity === historyFilter
      return matchesSearch && matchesFilter
    })
  }, [history, historySearch, historyFilter])

  // Handle Form Submit
  const handleSendBroadcast = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) {
      addToast('error', 'Please enter a notification title.')
      return
    }
    if (!message.trim()) {
      addToast('error', 'Please enter a notification message body.')
      return
    }
    if (targetMode === 'SINGLE' && !selectedMerchantId) {
      addToast('error', 'Please choose a target merchant.')
      return
    }

    const target = targetMode === 'SINGLE' ? selectedMerchantId : targetMode

    const targetLabel =
      targetMode === 'ALL'
        ? `all ${merchants.length || 'registered'} merchants`
        : targetMode === 'ACTIVE'
        ? 'all active merchants'
        : `merchant "${merchants.find(m => m.id === selectedMerchantId)?.business_name || selectedMerchantId.slice(0, 8)}"`

    const confirmed = window.confirm(
      `Broadcast "${title.trim()}" to ${targetLabel}?\n\nThis will write to merchant notification feeds immediately.`
    )
    if (!confirmed) return

    setIsSending(true)
    try {
      const payload: BroadcastPayload = {
        title: title.trim(),
        message: message.trim(),
        type,
        severity,
        target,
        updateBanner,
      }

      const res = await broadcastMerchantNotification(payload)
      if (res.ok) {
        addToast(
          'success',
          `Broadcast sent successfully to ${res.recipients_count} merchant${res.recipients_count === 1 ? '' : 's'}!`
        )
        // Reset form
        setTitle('')
        setMessage('')
        setUpdateBanner(false)
        // Refresh history
        await loadHistory()
      } else {
        throw new Error(res.warning || 'Unknown dispatch failure')
      }
    } catch (err: any) {
      addToast('error', 'Failed to send broadcast: ' + err.message)
    } finally {
      setIsSending(false)
    }
  }

  // Handle Delete Broadcast
  const handleDeleteBatch = async (batchId: string) => {
    if (!window.confirm('Are you sure you want to recall/delete this broadcast from merchant feeds?')) {
      return
    }
    setDeletingBatchId(batchId)
    try {
      await deleteBroadcastBatch(batchId)
      addToast('success', 'Broadcast batch recalled and deleted successfully.')
      setHistory(prev => prev.filter(item => item.batch_id !== batchId))
    } catch (err: any) {
      addToast('error', 'Failed to recall broadcast: ' + err.message)
    } finally {
      setDeletingBatchId(null)
    }
  }

  // Severity visual helpers
  const getSeverityBadgeClass = (sev: NotificationSeverity) => {
    switch (sev) {
      case 'SUCCESS':
        return 'badge badge-success'
      case 'WARNING':
        return 'badge badge-warning'
      case 'ERROR':
        return 'badge badge-danger'
      default:
        return 'badge badge-primary'
    }
  }

  const getSeverityColor = (sev: NotificationSeverity) => {
    switch (sev) {
      case 'SUCCESS':
        return '#10b981'
      case 'WARNING':
        return '#f59e0b'
      case 'ERROR':
        return '#ef4444'
      default:
        return '#3b82f6'
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* ──────────────── Top Header ──────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                background: 'linear-gradient(135deg, rgba(245, 197, 24, 0.2), rgba(245, 197, 24, 0.05))',
                border: '1px solid rgba(245, 197, 24, 0.3)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--brand-primary)',
              }}
            >
              <Megaphone size={20} />
            </div>
            <div>
              <h1 style={{ fontSize: 24, fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
                Broadcast Notifications
              </h1>
              <p style={{ margin: '3px 0 0', fontSize: 13.5, color: 'var(--text-secondary)' }}>
                Dispatch push notices, announcements, and critical alerts directly to Merchant Apps
              </p>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              padding: '6px 14px',
              borderRadius: 'var(--radius-md)',
              background: 'var(--bg-subtle)',
              border: '1px solid var(--border-default)',
              fontSize: 13,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <Users size={15} color="var(--brand-primary)" />
            <span>
              <strong>{merchants.length}</strong> Registered Merchants
            </span>
          </div>

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => {
              loadMerchants()
              loadHistory()
            }}
            disabled={loadingHistory}
            title="Refresh list"
          >
            <RefreshCw size={14} className={loadingHistory ? 'spin' : ''} />
            Refresh
          </button>
        </div>
      </div>

      {/* ──────────────── Main Grid: Composer & Mobile App Preview ──────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.35fr) minmax(320px, 0.85fr)', gap: 24, alignItems: 'start' }}>
        {/* Left Column: Notification Composer */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Send size={16} color="var(--brand-primary)" />
                Compose Broadcast Message
              </h3>
              <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
                Select target audience, notification tone, and message content
              </p>
            </div>
          </div>

          <form onSubmit={handleSendBroadcast}>
            <div className="card-body" style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
              {/* 1. Target Audience */}
              <div>
                <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>Target Audience</span>
                  <span style={{ fontSize: 12, fontWeight: 400, color: 'var(--text-tertiary)' }}>
                    {targetMode === 'ALL'
                      ? `Reaches all ${merchants.length} merchants`
                      : targetMode === 'ACTIVE'
                      ? `Reaches active merchants only`
                      : `Reaches 1 targeted merchant`}
                  </span>
                </label>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 12 }}>
                  <button
                    type="button"
                    className={`btn btn-sm ${targetMode === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setTargetMode('ALL')}
                    style={{ justifyContent: 'center' }}
                  >
                    All Merchants
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${targetMode === 'ACTIVE' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setTargetMode('ACTIVE')}
                    style={{ justifyContent: 'center' }}
                  >
                    Active Only
                  </button>
                  <button
                    type="button"
                    className={`btn btn-sm ${targetMode === 'SINGLE' ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setTargetMode('SINGLE')}
                    style={{ justifyContent: 'center' }}
                  >
                    Single Merchant
                  </button>
                </div>

                {targetMode === 'SINGLE' && (
                  <div
                    style={{
                      padding: 12,
                      background: 'var(--bg-subtle)',
                      border: '1px solid var(--border-default)',
                      borderRadius: 'var(--radius-md)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 8,
                    }}
                  >
                    <div style={{ display: 'flex', gap: 8 }}>
                      <div style={{ position: 'relative', flex: 1 }}>
                        <Search size={14} style={{ position: 'absolute', left: 10, top: 11, color: 'var(--text-tertiary)' }} />
                        <input
                          className="input"
                          placeholder="Filter merchant name or email..."
                          value={merchantSearch}
                          onChange={e => setMerchantSearch(e.target.value)}
                          style={{ paddingLeft: 30, fontSize: 12.5 }}
                        />
                      </div>
                    </div>
                    <select
                      className="input"
                      value={selectedMerchantId}
                      onChange={e => setSelectedMerchantId(e.target.value)}
                    >
                      {filteredMerchants.length === 0 ? (
                        <option value="">No matching merchants found</option>
                      ) : (
                        filteredMerchants.map(m => (
                          <option key={m.id} value={m.id}>
                            {m.business_name || 'Unnamed'} ({m.email || m.id.slice(0, 8)}) {m.status ? `— [${m.status}]` : ''}
                          </option>
                        ))
                      )}
                    </select>
                  </div>
                )}
              </div>

              {/* 2. Notification Type & Severity */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14 }}>
                <div>
                  <label className="form-label">Category / Type</label>
                  <select
                    className="input"
                    value={type}
                    onChange={e => setType(e.target.value as NotificationType)}
                  >
                    <option value="ANNOUNCEMENT">📢 Announcement (General update)</option>
                    <option value="ALERT">⚠️ Alert (Urgent attention)</option>
                    <option value="SYSTEM">⚙️ System (Maintenance & platform)</option>
                    <option value="PROMOTION">🎁 Promotion (Offer / Cash-back)</option>
                    <option value="INFO">ℹ️ Info (Standard notice)</option>
                  </select>
                </div>

                <div>
                  <label className="form-label">Severity Level</label>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
                    {(['INFO', 'SUCCESS', 'WARNING', 'ERROR'] as NotificationSeverity[]).map(sev => {
                      const isSel = severity === sev
                      return (
                        <button
                          key={sev}
                          type="button"
                          onClick={() => setSeverity(sev)}
                          style={{
                            padding: '8px 4px',
                            borderRadius: 'var(--radius-sm)',
                            border: `1px solid ${isSel ? getSeverityColor(sev) : 'var(--border-default)'}`,
                            background: isSel ? `${getSeverityColor(sev)}15` : 'var(--bg-subtle)',
                            color: isSel ? getSeverityColor(sev) : 'var(--text-secondary)',
                            fontWeight: isSel ? 700 : 500,
                            fontSize: 11.5,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: 4,
                            transition: 'all 0.15s ease',
                          }}
                        >
                          {isSel && <Check size={12} />}
                          {sev}
                        </button>
                      )
                    })}
                  </div>
                </div>
              </div>

              {/* 3. Notification Title */}
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label className="form-label">Notification Title</label>
                  <span style={{ fontSize: 11, color: title.length > 200 ? 'var(--status-danger)' : 'var(--text-tertiary)' }}>
                    {title.length} / 255
                  </span>
                </div>
                <input
                  className="input"
                  placeholder="e.g. Scheduled Gateway Maintenance Tonight"
                  value={title}
                  onChange={e => setTitle(e.target.value)}
                  maxLength={255}
                  required
                />
                <span className="form-hint">Brief, impactful headline displayed on the merchant notification card.</span>
              </div>

              {/* 4. Notification Message */}
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label className="form-label">Message Body</label>
                  <span style={{ fontSize: 11, color: message.length > 800 ? 'var(--status-warning)' : 'var(--text-tertiary)' }}>
                    {message.length} chars
                  </span>
                </div>
                <textarea
                  className="input"
                  rows={4}
                  placeholder="Provide comprehensive details about this update, expected schedules, or required merchant actions..."
                  value={message}
                  onChange={e => setMessage(e.target.value)}
                  style={{ minHeight: 90 }}
                  required
                />
                <span className="form-hint">Detailed message shown when merchants tap or view the notification.</span>
              </div>

              {/* 5. Dual Channel Delivery Switch */}
              <div
                style={{
                  padding: 14,
                  borderRadius: 'var(--radius-md)',
                  background: updateBanner ? 'rgba(245, 197, 24, 0.08)' : 'var(--bg-subtle)',
                  border: `1px solid ${updateBanner ? 'rgba(245, 197, 24, 0.3)' : 'var(--border-default)'}`,
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 12,
                  cursor: 'pointer',
                }}
                onClick={() => setUpdateBanner(!updateBanner)}
              >
                <input
                  type="checkbox"
                  checked={updateBanner}
                  onChange={e => setUpdateBanner(e.target.checked)}
                  style={{ marginTop: 3, cursor: 'pointer', accentColor: 'var(--brand-primary)' }}
                />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>
                    Also update live Dashboard Announcement Banner (system_notice)
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                    Displays this message as a prominent marquee banner across the top of the Merchant Dashboard in real time.
                  </div>
                </div>
              </div>
            </div>

            <div className="card-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => {
                  setTitle('')
                  setMessage('')
                  setUpdateBanner(false)
                }}
                disabled={isSending}
              >
                Clear Form
              </button>

              <button
                type="submit"
                className="btn btn-primary"
                disabled={isSending || !title.trim() || !message.trim()}
                style={{ minWidth: 160, justifyContent: 'center' }}
              >
                {isSending ? (
                  <>
                    <RefreshCw size={14} className="spin" />
                    Broadcasting...
                  </>
                ) : (
                  <>
                    <Send size={15} />
                    Send Broadcast Now
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Right Column: Live Android Merchant App Preview */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div className="card">
            <div className="card-header">
              <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Smartphone size={16} color="var(--brand-primary)" />
                Merchant App Feed Preview
              </h3>
              <span className="badge badge-subtle" style={{ fontSize: 11 }}>
                Live Mockup
              </span>
            </div>

            <div className="card-body" style={{ background: '#090a0f', borderRadius: '0 0 var(--radius-lg) var(--radius-lg)', padding: 18 }}>
              {/* Phone Frame Header */}
              <div
                style={{
                  background: '#13151f',
                  padding: '10px 14px',
                  borderRadius: '10px 10px 0 0',
                  borderBottom: '1px solid #232738',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Bell size={14} color="#f5c518" />
                  <span style={{ fontSize: 12.5, fontWeight: 700, color: '#ffffff' }}>Notifications</span>
                </div>
                <div style={{ display: 'flex', gap: 4 }}>
                  <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 4, background: '#f5c518', color: '#000', fontWeight: 700 }}>
                    All
                  </span>
                  <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 4, background: '#1e2235', color: '#94a3b8' }}>
                    Unread (1)
                  </span>
                </div>
              </div>

              {/* Simulated Live Top Banner (if updateBanner is checked) */}
              {updateBanner && (
                <div
                  style={{
                    background: 'linear-gradient(90deg, #382a0b, #1f1a08)',
                    borderLeft: '3px solid #f5c518',
                    padding: '8px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    fontSize: 11,
                    color: '#fef08a',
                  }}
                >
                  <Sparkles size={13} color="#f5c518" />
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    <strong>Notice:</strong> {message.trim() || 'System announcement active...'}
                  </span>
                </div>
              )}

              {/* Simulated Notification Card */}
              <div
                style={{
                  background: '#161926',
                  border: `1px solid ${getSeverityColor(severity)}40`,
                  borderLeft: `4px solid ${getSeverityColor(severity)}`,
                  padding: 14,
                  borderRadius: '0 0 10px 10px',
                  marginTop: 10,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {severity === 'WARNING' ? (
                      <AlertTriangle size={15} color="#f59e0b" />
                    ) : severity === 'ERROR' ? (
                      <ShieldAlert size={15} color="#ef4444" />
                    ) : severity === 'SUCCESS' ? (
                      <CheckCircle2 size={15} color="#10b981" />
                    ) : (
                      <Info size={15} color="#3b82f6" />
                    )}
                    <span
                      style={{
                        fontSize: 10,
                        fontWeight: 700,
                        padding: '1px 6px',
                        borderRadius: 4,
                        background: `${getSeverityColor(severity)}25`,
                        color: getSeverityColor(severity),
                        letterSpacing: 0.5,
                      }}
                    >
                      {type}
                    </span>
                  </div>
                  <span style={{ fontSize: 10, color: '#64748b' }}>Just now</span>
                </div>

                <div>
                  <h4 style={{ margin: '0 0 4px', fontSize: 13, fontWeight: 700, color: '#ffffff' }}>
                    {title.trim() || 'Notification Title'}
                  </h4>
                  <p
                    style={{
                      margin: 0,
                      fontSize: 11.5,
                      color: '#cbd5e1',
                      lineHeight: 1.4,
                      whiteSpace: 'pre-line',
                    }}
                  >
                    {message.trim() || 'Notification body text will appear here exactly as rendered on merchant Android devices.'}
                  </p>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 4, borderTop: '1px solid #232738', paddingTop: 6 }}>
                  <span style={{ fontSize: 9.5, color: '#64748b' }}>
                    Target: {targetMode === 'ALL' ? 'All Merchants' : targetMode === 'ACTIVE' ? 'Active Merchants' : '1 Merchant'}
                  </span>
                  <span style={{ fontSize: 10, color: '#f5c518', fontWeight: 600 }}>Mark Read</span>
                </div>
              </div>

              {/* Helper explanation below preview */}
              <div style={{ marginTop: 14, padding: 10, borderRadius: 6, background: '#11131c', border: '1px dashed #232738' }}>
                <p style={{ margin: 0, fontSize: 11, color: '#94a3b8', lineHeight: 1.4 }}>
                  💡 Broadcasts sync immediately into the merchant app's local Room database upon opening or pull-to-refresh, updating unread badges and notification tab feeds.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ──────────────── Bottom Section: Broadcast History & Delivery Log ──────────────── */}
      <div className="card">
        <div className="card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Layers size={16} color="var(--brand-primary)" />
              Broadcast History & Delivery Log
            </h3>
            <p style={{ margin: '3px 0 0', fontSize: 12.5, color: 'var(--text-secondary)' }}>
              Previously dispatched notifications, audience counts, and recall options
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {/* Severity Filter */}
            <select
              className="input"
              value={historyFilter}
              onChange={e => setHistoryFilter(e.target.value as any)}
              style={{ padding: '6px 12px', fontSize: 12.5, width: 'auto' }}
            >
              <option value="ALL">All Severities</option>
              <option value="INFO">Info</option>
              <option value="SUCCESS">Success</option>
              <option value="WARNING">Warning</option>
              <option value="ERROR">Error</option>
            </select>

            {/* Search Filter */}
            <div style={{ position: 'relative', width: 220 }}>
              <Search size={14} style={{ position: 'absolute', left: 10, top: 10, color: 'var(--text-tertiary)' }} />
              <input
                className="input"
                placeholder="Search history..."
                value={historySearch}
                onChange={e => setHistorySearch(e.target.value)}
                style={{ paddingLeft: 30, paddingRight: 10, fontSize: 12.5 }}
              />
            </div>
          </div>
        </div>

        <div className="table-responsive">
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 150 }}>Sent Time</th>
                <th style={{ width: 110 }}>Type</th>
                <th style={{ width: 90 }}>Severity</th>
                <th>Title & Message Excerpt</th>
                <th style={{ width: 110, textAlign: 'center' }}>Recipients</th>
                <th style={{ width: 90, textAlign: 'center' }}>Read</th>
                <th style={{ width: 90, textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loadingHistory ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: 32, color: 'var(--text-secondary)' }}>
                    <RefreshCw size={18} className="spin" style={{ display: 'inline', marginRight: 8 }} />
                    Loading broadcast history...
                  </td>
                </tr>
              ) : filteredHistory.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: 36, color: 'var(--text-secondary)' }}>
                    <Megaphone size={28} style={{ opacity: 0.3, marginBottom: 8 }} />
                    <div>No broadcast notifications found matching current filters.</div>
                  </td>
                </tr>
              ) : (
                filteredHistory.map(item => (
                  <tr key={item.batch_id}>
                    <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                      {new Date(item.created_at).toLocaleDateString()} {new Date(item.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>

                    <td>
                      <span
                        style={{
                          fontSize: 11,
                          fontWeight: 600,
                          padding: '2px 8px',
                          borderRadius: 4,
                          background: 'var(--bg-subtle)',
                          border: '1px solid var(--border-default)',
                        }}
                      >
                        {item.type}
                      </span>
                    </td>

                    <td>
                      <span className={getSeverityBadgeClass(item.severity)}>
                        {item.severity}
                      </span>
                    </td>

                    <td>
                      <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-primary)' }}>
                        {item.title}
                      </div>
                      <div
                        style={{
                          fontSize: 12,
                          color: 'var(--text-secondary)',
                          maxWidth: 460,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          marginTop: 2,
                        }}
                      >
                        {item.message}
                      </div>
                    </td>

                    <td style={{ textAlign: 'center' }}>
                      <span
                        style={{
                          fontSize: 12,
                          fontWeight: 700,
                          padding: '2px 8px',
                          borderRadius: 10,
                          background: 'rgba(245, 197, 24, 0.1)',
                          color: 'var(--brand-primary)',
                        }}
                      >
                        {item.recipients_count}
                      </span>
                    </td>

                    <td style={{ textAlign: 'center', fontSize: 12, color: 'var(--text-secondary)' }}>
                      {item.read_count}
                    </td>

                    <td style={{ textAlign: 'right' }}>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => handleDeleteBatch(item.batch_id)}
                        disabled={deletingBatchId === item.batch_id}
                        title="Recall and delete broadcast"
                        style={{ padding: '5px 8px' }}
                      >
                        {deletingBatchId === item.batch_id ? (
                          <RefreshCw size={13} className="spin" />
                        ) : (
                          <Trash2 size={13} />
                        )}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
