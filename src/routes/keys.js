// SwapnoPay Backend — API Key Management Routes
// Accessible by platform Admin or authenticated Merchant owners
//
// POST /v1/admin/keys/generate  — generate a new signed API key for a merchant
// POST /v1/admin/keys/revoke    — revoke an existing key
// POST /v1/admin/keys/validate  — validate an API key and return merchant details
// GET  /v1/admin/keys           — list keys (filtered by merchant_id if specified)

import { Router } from 'express'
import { v4 as uuidv4 } from 'uuid'
import { requireMerchantOrAdminAuth } from '../middleware/auth.js'
import {
  storeApiKeyRecord,
  revokeApiKeyRecord,
  listApiKeyRecords,
  validateApiKey,
} from '../services/adminSupabase.js'
import { generateRawApiKey, apiKeyDigest } from '../utils/crypto.js'

const router = Router()
router.use(requireMerchantOrAdminAuth)

// ────────────────────────────────────────────────────────────────────────────
// POST /v1/admin/keys/generate
// Body: { merchant_id, merchant_name?, label? }
// Returns the raw key ONCE — only the digest is stored in admin Supabase
// ────────────────────────────────────────────────────────────────────────────
router.post('/generate', async (req, res) => {
  const { merchant_id, merchant_name, label } = req.body || {}

  if (!merchant_id || typeof merchant_id !== 'string') {
    return res.status(400).json({ error: 'merchant_id is required' })
  }

  // Non-admins can only generate keys for their own merchant_id
  if (!req.isAdmin && req.merchantUser && req.merchantUser.id !== merchant_id) {
    return res.status(403).json({ error: 'Cannot generate keys for another merchant' })
  }

  try {
    const rawKey = generateRawApiKey()
    const digest = apiKeyDigest(rawKey)
    const keyId = uuidv4()

    const record = {
      id: keyId,
      merchant_id: merchant_id.trim(),
      merchant_name: (merchant_name || 'Unknown Merchant').trim().slice(0, 100),
      label: (label || 'Default API Key').trim().slice(0, 80),
      digest,
      key_preview: rawKey.slice(0, 14) + '****',
    }

    await storeApiKeyRecord(record)
    console.log(`[keys/generate] New API key generated for merchant: ${merchant_id}`)

    res.status(201).json({
      ok: true,
      key_id: keyId,
      api_key: rawKey,           // Shown ONCE — must be saved immediately
      key_preview: record.key_preview,
      merchant_id,
      label: record.label,
      message: '⚠️ Save this API key now — it will NOT be shown again.',
    })
  } catch (err) {
    console.error('[keys/generate]', err.message)
    res.status(500).json({ error: 'Failed to generate API key: ' + err.message })
  }
})

// ────────────────────────────────────────────────────────────────────────────
// POST /v1/admin/keys/revoke
// Body: { key_id }
// ────────────────────────────────────────────────────────────────────────────
router.post('/revoke', async (req, res) => {
  const { key_id } = req.body || {}
  if (!key_id || typeof key_id !== 'string') {
    return res.status(400).json({ error: 'key_id is required' })
  }
  try {
    await revokeApiKeyRecord(key_id)
    console.log(`[keys/revoke] API key revoked: ${key_id}`)
    res.json({ ok: true, key_id, revoked: true })
  } catch (err) {
    console.error('[keys/revoke]', err.message)
    res.status(500).json({ error: 'Failed to revoke API key: ' + err.message })
  }
})

// ────────────────────────────────────────────────────────────────────────────
// POST /v1/admin/keys/validate
// Body: { api_key }
// Validates an API key and returns the merchant it belongs to
// ────────────────────────────────────────────────────────────────────────────
router.post('/validate', async (req, res) => {
  const { api_key } = req.body || {}
  if (!api_key || typeof api_key !== 'string') {
    return res.status(400).json({ ok: false, error: 'api_key is required' })
  }

  try {
    const digest = apiKeyDigest(api_key.trim())
    const record = await validateApiKey(digest)

    if (!record) {
      return res.status(401).json({ ok: false, valid: false, error: 'Invalid or revoked API key' })
    }

    res.json({
      ok: true,
      valid: true,
      key_id: record.id,
      merchant_id: record.merchant_id,
      merchant_name: record.merchant_name,
      label: record.label,
    })
  } catch (err) {
    console.error('[keys/validate]', err.message)
    res.status(500).json({ ok: false, error: 'API key validation error: ' + err.message })
  }
})

// ────────────────────────────────────────────────────────────────────────────
// GET /v1/admin/keys
// Returns key records (digest is never sent to the client)
// Supports ?merchant_id=<id> filter
// ────────────────────────────────────────────────────────────────────────────
router.get('/', async (req, res) => {
  try {
    let keys = await listApiKeyRecords()

    const targetMerchantId = req.query.merchant_id || (!req.isAdmin && req.merchantUser ? req.merchantUser.id : null)
    if (targetMerchantId) {
      keys = keys.filter(k => k.merchant_id === targetMerchantId)
    }

    res.json({ ok: true, count: keys.length, keys })
  } catch (err) {
    console.error('[keys/list]', err.message)
    res.status(500).json({ error: 'Failed to list API keys: ' + err.message })
  }
})

export { router as keysRouter }
