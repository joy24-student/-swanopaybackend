// SwapnoPay Backend — Merchant App PIN Routes
// POST /v1/pin/set            — Set or change PIN hash (authenticated merchant)
// GET  /v1/pin/sync           — Fetch PIN hash + reset flag (authenticated merchant)
// POST /v1/pin/request-reset  — Merchant requests admin to clear their PIN
// POST /v1/pin/admin-clear    — Admin clears a merchant PIN (admin auth only)
//
// SECURITY: Raw PINs are NEVER sent to this server.
//           Only SHA-256 hex hashes are transmitted and stored.

import { Router } from 'express'
import { requirePlatformUser, lookupMerchantInAdminDb } from '../services/merchantAccount.js'
import { requireMerchantOrAdminAuth } from '../middleware/auth.js'
import {
  setPinHash,
  getPinHash,
  clearPinHash,
  requestPinReset,
} from '../services/adminSupabase.js'

export const pinRouter = Router()

// All PIN endpoints require a valid platform auth token
pinRouter.use(requirePlatformUser)

function isValidSha256Hex(str) {
  return typeof str === 'string' && /^[0-9a-f]{64}$/i.test(str)
}

// POST /v1/pin/set
// Body: { pin_hash: "<sha256-hex>" }
pinRouter.post('/set', async (req, res) => {
  try {
    const user = req.platformUser
    if (!user?.id) return res.status(401).json({ error: 'Merchant not authenticated' })

    let merchantId = user.id
    try {
      const lookup = await lookupMerchantInAdminDb(user.email, user.id)
      if (lookup?.merchantId) {
        merchantId = lookup.merchantId
      }
    } catch (_) {}

    const { pin_hash } = req.body || {}
    if (!pin_hash) return res.status(400).json({ error: 'pin_hash is required' })
    if (!isValidSha256Hex(pin_hash)) {
      return res.status(400).json({ error: 'pin_hash must be a valid SHA-256 hex string (64 chars)' })
    }

    const result = await setPinHash(merchantId, pin_hash, user.email, user.id)
    return res.json({
      ok: true,
      message: 'PIN updated successfully',
      pin_set: !!result?.app_pin_hash,
      pin_reset_requested: result?.pin_reset_requested || false,
    })
  } catch (err) {
    console.error('[pin] /set error:', err.message)
    return res.status(500).json({ error: err.message })
  }
})

// GET /v1/pin/sync
// Called after every successful password login to fetch cloud PIN hash.
pinRouter.get('/sync', async (req, res) => {
  try {
    const user = req.platformUser
    if (!user?.id) return res.status(401).json({ error: 'Merchant not authenticated' })

    let merchantId = user.id
    try {
      const lookup = await lookupMerchantInAdminDb(user.email, user.id)
      if (lookup?.merchantId) {
        merchantId = lookup.merchantId
      }
    } catch (_) {}

    const data = await getPinHash(merchantId, user.email, user.id)
    return res.json({
      ok: true,
      pin_hash: data?.app_pin_hash || null,
      pin_set: !!data?.app_pin_hash,
      pin_reset_requested: data?.pin_reset_requested || false,
    })
  } catch (err) {
    console.error('[pin] /sync error:', err.message)
    return res.status(500).json({ error: err.message })
  }
})

// POST /v1/pin/request-reset
// Merchant requests admin to clear their PIN.
pinRouter.post('/request-reset', async (req, res) => {
  try {
    const user = req.platformUser
    if (!user?.id) return res.status(401).json({ error: 'Merchant not authenticated' })

    let merchantId = user.id
    try {
      const lookup = await lookupMerchantInAdminDb(user.email, user.id)
      if (lookup?.merchantId) {
        merchantId = lookup.merchantId
      }
    } catch (_) {}

    await requestPinReset(merchantId, user.email, user.id)
    return res.json({
      ok: true,
      message: 'PIN reset request sent to admin. Your PIN will be cleared once the admin approves.',
    })
  } catch (err) {
    console.error('[pin] /request-reset error:', err.message)
    return res.status(500).json({ error: err.message })
  }
})

// POST /v1/pin/admin-clear
// Admin-only: immediately clear a merchant PIN hash.
// Body: { merchant_id: "..." }
pinRouter.post('/admin-clear', requireMerchantOrAdminAuth, async (req, res) => {
  try {
    if (!req.isAdmin) {
      return res.status(403).json({ error: 'Admin access required to clear another merchant PIN' })
    }
    const { merchant_id } = req.body || {}
    if (!merchant_id) return res.status(400).json({ error: 'merchant_id is required' })
    const result = await clearPinHash(merchant_id)
    return res.json({
      ok: true,
      message: 'Merchant PIN cleared. Merchant must set a new PIN on next login.',
      merchant_id: result?.id,
      pin_set: false,
    })
  } catch (err) {
    console.error('[pin] /admin-clear error:', err.message)
    return res.status(500).json({ error: err.message })
  }
})
