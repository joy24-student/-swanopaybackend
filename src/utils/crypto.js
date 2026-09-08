// SwapnoPay Backend — Crypto Utilities
// API key generation and HMAC-SHA256 signing/verification

import { createHmac, createHash, randomBytes } from 'node:crypto'

const PEPPER = process.env.API_KEY_PEPPER || ''

/**
 * Generate a new raw API key in the format:
 *   sp_live_<32-random-hex-chars>
 */
export function generateRawApiKey() {
  const rand = randomBytes(24).toString('hex')
  return `sp_live_${rand}`
}

/**
 * Compute a peppered HMAC-SHA256 digest of a raw API key.
 * The digest is what's stored in Firebase — the raw key is shown once only.
 */
export function apiKeyDigest(rawKey) {
  if (!PEPPER || PEPPER.length < 32) {
    throw new Error('API_KEY_PEPPER must be set and at least 32 characters')
  }
  return createHmac('sha256', PEPPER).update(rawKey).digest('hex')
}

/**
 * Verify an HMAC-SHA256 signature on a webhook payload.
 * The signature must be hex-encoded.
 *
 * @param {string} secret  — merchant's webhook_secret
 * @param {string} body    — raw JSON string of the request body
 * @param {string} signature — value from X-Signature header
 * @returns {boolean}
 */
export function verifyWebhookSignature(secret, body, signature) {
  if (!secret || !body || !signature) return false
  const expected = createHmac('sha256', secret).update(body).digest('hex')
  // Constant-time comparison to prevent timing attacks
  if (expected.length !== signature.length) return false
  let diff = 0
  for (let i = 0; i < expected.length; i++) {
    diff |= expected.charCodeAt(i) ^ signature.charCodeAt(i)
  }
  return diff === 0
}

/**
 * Compute a SHA-256 hash of arbitrary data (used for idempotency checks).
 */
export function sha256(data) {
  return createHash('sha256').update(String(data)).digest('hex')
}

/**
 * Generate a random webhook secret (for process-sms, payment-receipt webhooks).
 */
export function generateWebhookSecret(prefix = 'wh') {
  return `${prefix}_${randomBytes(24).toString('hex')}`
}
