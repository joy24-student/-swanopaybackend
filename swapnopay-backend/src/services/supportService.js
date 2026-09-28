// SwapnoPay Backend — Support Helpdesk Service
// Persists live chat messages, support tickets, and feature requests across
// Admin Supabase (when service_role is available) and local disk/memory store.

import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { getAdminClient } from './adminSupabase.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const SUPPORT_STORE_FILE = path.resolve(__dirname, '../../data/support-store.json')

const memoryStore = {
  messages: [],
  tickets: [],
  features: [],
}

function loadStoreFromDisk() {
  try {
    if (!fs.existsSync(SUPPORT_STORE_FILE)) return
    const parsed = JSON.parse(fs.readFileSync(SUPPORT_STORE_FILE, 'utf8') || '{}')
    if (Array.isArray(parsed.messages)) memoryStore.messages = parsed.messages
    if (Array.isArray(parsed.tickets)) memoryStore.tickets = parsed.tickets
    if (Array.isArray(parsed.features)) memoryStore.features = parsed.features
  } catch (_) {}
}

function saveStoreToDisk() {
  try {
    const dir = path.dirname(SUPPORT_STORE_FILE)
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(SUPPORT_STORE_FILE, JSON.stringify(memoryStore, null, 2), 'utf8')
  } catch (_) {}
}

loadStoreFromDisk()

function tryGetAdmin() {
  try {
    return getAdminClient()
  } catch (_) {
    return null
  }
}

function mergeById(primary = [], secondary = []) {
  const map = new Map()
  for (const item of secondary) {
    if (item?.id) map.set(item.id, item)
  }
  for (const item of primary) {
    if (item?.id) map.set(item.id, { ...(map.get(item.id) || {}), ...item })
  }
  return Array.from(map.values())
}

export async function saveChatMessage({
  merchant_id,
  session_id = '',
  sender = 'MERCHANT',
  message,
  attachment_name = '',
  attachment_type = '',
  attachment_base64 = '',
  created_at = new Date().toISOString(),
} = {}) {
  if (!merchant_id) throw new Error('merchant_id is required')
  if (typeof message !== 'string' || !message.trim()) {
    throw new Error('Message content is required')
  }

  loadStoreFromDisk()
  const dbRecord = {
    id: crypto.randomUUID(),
    merchant_id: String(merchant_id).trim(),
    sender: String(sender || 'MERCHANT').toUpperCase(),
    message: message.trim(),
    created_at,
  }

  const admin = tryGetAdmin()
  if (admin) {
    try {
      const { data, error } = await admin
        .from('live_chat_messages')
        .insert(dbRecord)
        .select('*')
        .single()
      if (!error && data) {
        Object.assign(dbRecord, data)
      }
    } catch (_) {}
  }

  const record = {
    ...dbRecord,
    ...(session_id ? { session_id: String(session_id).trim() } : {}),
    ...(attachment_name ? { attachment_name: String(attachment_name).trim() } : {}),
    ...(attachment_type ? { attachment_type: String(attachment_type).trim() } : {}),
    ...(attachment_base64 ? { attachment_base64: String(attachment_base64) } : {}),
  }

  memoryStore.messages = [
    ...memoryStore.messages.filter((m) => m.id !== record.id),
    record,
  ].slice(-2000)
  saveStoreToDisk()
  return record
}

export async function getChatMessages(merchantId = null, limit = 300) {
  loadStoreFromDisk()
  let dbMessages = []
  const admin = tryGetAdmin()
  if (admin) {
    try {
      let query = admin.from('live_chat_messages').select('*')
      if (merchantId) {
        query = query.eq('merchant_id', String(merchantId).trim())
      }
      const { data, error } = await query
        .order('created_at', { ascending: true })
        .limit(limit)
      if (!error && Array.isArray(data)) {
        dbMessages = data
      }
    } catch (_) {}
  }

  const localMessages = merchantId
    ? memoryStore.messages.filter((m) => m.merchant_id === String(merchantId).trim())
    : memoryStore.messages

  return mergeById(dbMessages, localMessages)
    .sort((a, b) => new Date(a.created_at || 0) - new Date(b.created_at || 0))
    .slice(-limit)
}

export async function saveSupportTicket({
  merchant_id,
  business_name = 'My Business',
  email = null,
  phone = null,
  category = 'GENERAL',
  subject,
  description,
  status = 'OPEN',
  created_at = new Date().toISOString(),
} = {}) {
  if (!merchant_id) throw new Error('merchant_id is required')
  if (typeof subject !== 'string' || !subject.trim() || typeof description !== 'string' || !description.trim()) {
    throw new Error('Subject and description are required')
  }

  loadStoreFromDisk()
  const dbRecord = {
    id: crypto.randomUUID(),
    merchant_id: String(merchant_id).trim(),
    business_name: String(business_name || 'My Business').trim(),
    email: email || null,
    phone: phone || null,
    category: String(category || 'GENERAL').toUpperCase(),
    subject: subject.trim(),
    description: description.trim(),
    status: String(status || 'OPEN').toUpperCase(),
    admin_reply: null,
    resolved_at: null,
    created_at,
    updated_at: created_at,
  }

  const admin = tryGetAdmin()
  if (admin) {
    try {
      const { data, error } = await admin
        .from('support_tickets')
        .insert(dbRecord)
        .select('*')
        .single()
      if (!error && data) {
        Object.assign(dbRecord, data)
      }
    } catch (_) {}
  }

  const record = {
    ...dbRecord,
    replied_at: dbRecord.resolved_at || null,
  }

  memoryStore.tickets = [
    record,
    ...memoryStore.tickets.filter((t) => t.id !== record.id),
  ].slice(0, 1000)
  saveStoreToDisk()
  return record
}

export async function updateSupportTicket(ticketId, updates = {}) {
  if (!ticketId) throw new Error('Ticket ID is required')
  loadStoreFromDisk()

  const nowIso = new Date().toISOString()
  const cleanUpdates = {
    ...(updates.status ? { status: String(updates.status).toUpperCase() } : {}),
    ...(updates.admin_reply !== undefined ? { admin_reply: updates.admin_reply } : {}),
    ...(updates.resolved_at !== undefined
      ? { resolved_at: updates.resolved_at }
      : updates.status === 'RESOLVED' || updates.status === 'CLOSED'
      ? { resolved_at: nowIso }
      : {}),
    updated_at: nowIso,
  }

  let updatedRecord = null
  const idx = memoryStore.tickets.findIndex((t) => t.id === ticketId)
  if (idx >= 0) {
    memoryStore.tickets[idx] = {
      ...memoryStore.tickets[idx],
      ...cleanUpdates,
      replied_at: cleanUpdates.resolved_at || memoryStore.tickets[idx].replied_at || nowIso,
    }
    updatedRecord = memoryStore.tickets[idx]
    saveStoreToDisk()
  }

  const admin = tryGetAdmin()
  if (admin) {
    try {
      const { data, error } = await admin
        .from('support_tickets')
        .update(cleanUpdates)
        .eq('id', ticketId)
        .select('*')
        .maybeSingle()
      if (!error && data) {
        updatedRecord = { ...(updatedRecord || {}), ...data }
      }
    } catch (_) {}
  }

  return updatedRecord || { id: ticketId, ...cleanUpdates }
}

export async function getSupportTickets(merchantId = null, limit = 100) {
  loadStoreFromDisk()
  let dbTickets = []
  const admin = tryGetAdmin()
  if (admin) {
    try {
      let query = admin.from('support_tickets').select('*')
      if (merchantId) {
        query = query.eq('merchant_id', String(merchantId).trim())
      }
      const { data, error } = await query
        .order('created_at', { ascending: false })
        .limit(limit)
      if (!error && Array.isArray(data)) {
        dbTickets = data
      }
    } catch (_) {}
  }

  const localTickets = merchantId
    ? memoryStore.tickets.filter((t) => t.merchant_id === String(merchantId).trim())
    : memoryStore.tickets

  return mergeById(localTickets, dbTickets)
    .sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0))
    .slice(0, limit)
}

export async function saveFeatureRequest({
  merchant_id,
  business_name = 'My Business',
  email = null,
  phone = null,
  category = 'GENERAL',
  priority = 'MEDIUM',
  title,
  description,
  status = 'PENDING',
  created_at = new Date().toISOString(),
} = {}) {
  if (!merchant_id) throw new Error('merchant_id is required')
  if (typeof title !== 'string' || !title.trim() || typeof description !== 'string' || !description.trim()) {
    throw new Error('Title and description are required')
  }

  loadStoreFromDisk()
  const dbRecord = {
    id: crypto.randomUUID(),
    merchant_id: String(merchant_id).trim(),
    business_name: String(business_name || 'My Business').trim(),
    email: email || null,
    phone: phone || null,
    category: String(category || 'GENERAL').toUpperCase(),
    priority: String(priority || 'MEDIUM').toUpperCase(),
    title: title.trim(),
    description: description.trim(),
    status: String(status || 'PENDING').toUpperCase(),
    admin_notes: null,
    created_at,
    updated_at: created_at,
  }

  const admin = tryGetAdmin()
  if (admin) {
    try {
      const { data, error } = await admin
        .from('feature_requests')
        .insert(dbRecord)
        .select('*')
        .single()
      if (!error && data) {
        Object.assign(dbRecord, data)
      }
    } catch (_) {}
  }

  const record = {
    ...dbRecord,
    upvotes: 1,
  }

  memoryStore.features = [
    record,
    ...memoryStore.features.filter((f) => f.id !== record.id),
  ].slice(0, 1000)
  saveStoreToDisk()
  return record
}

export async function updateFeatureRequest(featureId, updates = {}) {
  if (!featureId) throw new Error('Feature request ID is required')
  loadStoreFromDisk()

  const nowIso = new Date().toISOString()
  const cleanUpdates = {
    ...(updates.status ? { status: String(updates.status).toUpperCase() } : {}),
    ...(updates.admin_notes !== undefined ? { admin_notes: updates.admin_notes } : {}),
    ...(updates.priority ? { priority: String(updates.priority).toUpperCase() } : {}),
    updated_at: nowIso,
  }

  let updatedRecord = null
  const idx = memoryStore.features.findIndex((f) => f.id === featureId)
  if (idx >= 0) {
    memoryStore.features[idx] = {
      ...memoryStore.features[idx],
      ...cleanUpdates,
    }
    updatedRecord = memoryStore.features[idx]
    saveStoreToDisk()
  }

  const admin = tryGetAdmin()
  if (admin) {
    try {
      const { data, error } = await admin
        .from('feature_requests')
        .update(cleanUpdates)
        .eq('id', featureId)
        .select('*')
        .maybeSingle()
      if (!error && data) {
        updatedRecord = { ...(updatedRecord || {}), ...data }
      }
    } catch (_) {}
  }

  return updatedRecord || { id: featureId, ...cleanUpdates }
}

export async function getFeatureRequests(merchantId = null, limit = 100) {
  loadStoreFromDisk()
  let dbFeatures = []
  const admin = tryGetAdmin()
  if (admin) {
    try {
      let query = admin.from('feature_requests').select('*')
      if (merchantId) {
        query = query.eq('merchant_id', String(merchantId).trim())
      }
      const { data, error } = await query
        .order('created_at', { ascending: false })
        .limit(limit)
      if (!error && Array.isArray(data)) {
        dbFeatures = data
      }
    } catch (_) {}
  }

  const localFeatures = merchantId
    ? memoryStore.features.filter((f) => f.merchant_id === String(merchantId).trim())
    : memoryStore.features

  return mergeById(localFeatures, dbFeatures)
    .sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0))
    .slice(0, limit)
}

export const listSupportTickets = getSupportTickets
export const listFeatureRequests = getFeatureRequests
export const listChatMessages = getChatMessages

