import crypto from 'node:crypto'

export function apiKeyDigest(apiKey, pepper) {
  return crypto.createHmac('sha256', pepper).update(apiKey, 'utf8').digest('hex')
}

export function payloadDigest(value) {
  return crypto.createHash('sha256').update(JSON.stringify(value), 'utf8').digest('hex')
}

export function generateApiKey() {
  return `sp_receipt_${crypto.randomBytes(32).toString('base64url')}`
}

export function isUuid(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(value || ''))
}

export function isProjectRef(value) {
  return /^[a-z0-9][a-z0-9-]{4,62}$/.test(String(value || ''))
}

export function normalizedEmail(value) {
  const email = String(value || '').trim().toLowerCase()
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : ''
}

export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character])
}

export function safeHeader(value, max = 200) {
  return String(value || '').replace(/[\r\n]/g, ' ').trim().slice(0, max)
}
