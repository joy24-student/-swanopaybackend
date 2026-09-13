// SwapnoPay Backend � KYC & Identity Verification Routes
// Handles NID document storage, face liveness metadata, and admin review pipeline

import { Router } from 'express'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { submitMerchantKyc, listPendingKycSubmissions, reviewMerchantKyc } from '../services/adminSupabase.js'
import { requireAdminSecret } from '../middleware/auth.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const router = Router()

// Upload directory for KYC images
const UPLOADS_DIR = path.join(__dirname, '../../uploads/kyc')
try {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true })
} catch (e) {
  console.warn('[kyc-routes] Uploads dir creation notice:', e.message)
}

function saveBase64Image(base64Data, prefix, merchantId) {
  if (!base64Data || typeof base64Data !== 'string') return null
  try {
    const cleanBase64 = base64Data.replace(/^data:image\/\w+;base64,/, '')
    const buffer = Buffer.from(cleanBase64, 'base64')
    if (buffer.length === 0) return null

    const fileName = `${prefix}_${merchantId}_${Date.now()}.jpg`
    const filePath = path.join(UPLOADS_DIR, fileName)
    fs.writeFileSync(filePath, buffer)
    return `/uploads/kyc/${fileName}`
  } catch (err) {
    console.warn(`[kyc] Failed to save base64 image (${prefix}):`, err.message)
    return null
  }
}

// ----------------------------------------------------------------------------
// POST /v1/kyc/submit � Submit merchant NID & Biometric Face KYC
// ----------------------------------------------------------------------------
router.post('/submit', async (req, res) => {
  try {
    const {
      merchant_id,
      nid_number,
      nid_name,
      nid_dob,
      nid_front_url: incomingFrontUrl,
      nid_back_url: incomingBackUrl,
      face_photo_url: incomingFaceUrl,
      front_base64,
      back_base64,
      selfie_base64,
      liveness_passed,
      ocr_raw_text,
    } = req.body || {}

    if (!merchant_id) {
      return res.status(400).json({ error: 'merchant_id is required' })
    }
    if (!nid_number || String(nid_number).trim().length < 10) {
      return res.status(400).json({ error: 'A valid NID number (at least 10 digits) is required' })
    }
    if (!incomingFaceUrl && !selfie_base64) {
      return res.status(400).json({ error: 'Live biometric face verification is required. NID documents cannot be submitted without face verification.' })
    }

    // Save base64 images if provided, otherwise preserve passed URLs
    let frontUrl = incomingFrontUrl
    if (front_base64) {
      const saved = saveBase64Image(front_base64, 'nid_front', merchant_id)
      if (saved) frontUrl = saved
    }

    let backUrl = incomingBackUrl
    if (back_base64) {
      const saved = saveBase64Image(back_base64, 'nid_back', merchant_id)
      if (saved) backUrl = saved
    }

    let faceUrl = incomingFaceUrl
    if (selfie_base64) {
      const saved = saveBase64Image(selfie_base64, 'live_selfie', merchant_id)
      if (saved) faceUrl = saved
    }

    const savedMerchant = await submitMerchantKyc({
      merchant_id,
      nid_number: String(nid_number).trim(),
      nid_name: nid_name ? String(nid_name).trim() : null,
      nid_dob: nid_dob ? String(nid_dob).trim() : null,
      nid_front_url: frontUrl,
      nid_back_url: backUrl,
      face_photo_url: faceUrl,
      liveness_passed: liveness_passed !== false,
      ocr_raw_text: ocr_raw_text || '',
    })

    // Broadcast realtime event to Admin Dashboard
    if (req.io) {
      req.io.emit('admin:kyc_submitted', {
        merchant_id,
        nid_number: String(nid_number).trim(),
        nid_name,
        submitted_at: new Date().toISOString(),
        front_url: frontUrl,
        back_url: backUrl,
        face_url: faceUrl,
      })
      // Broadcast to merchant room
      req.io.to(`merchant:${merchant_id}`).emit('merchant:kyc_status', {
        status: 'PENDING',
        message: 'KYC submitted and pending administrative verification',
      })
    }

    console.log(`[kyc] ? Successfully submitted KYC for merchant ${merchant_id} (NID: ${nid_number})`)

    res.json({
      ok: true,
      message: 'KYC submitted successfully. Verification status is now PENDING review.',
      merchant: savedMerchant,
      documents: {
        front_url: frontUrl,
        back_url: backUrl,
        face_url: faceUrl,
      },
    })
  } catch (err) {
    console.error('[kyc/submit POST]', err.message)
    res.status(500).json({ error: 'Failed to submit KYC: ' + err.message })
  }
})

// ----------------------------------------------------------------------------
// GET /v1/kyc/submissions � List all submissions for review
// ----------------------------------------------------------------------------
router.get('/submissions', requireAdminSecret, async (_req, res) => {
  try {
    const list = await listPendingKycSubmissions()
    res.json({ ok: true, count: list.length, submissions: list })
  } catch (err) {
    console.error('[kyc/submissions GET]', err.message)
    res.status(500).json({ error: 'Failed to fetch KYC submissions: ' + err.message })
  }
})

// ----------------------------------------------------------------------------
// POST /v1/kyc/review � Platform Admin approve / reject
// ----------------------------------------------------------------------------
router.post('/review', requireAdminSecret, async (req, res) => {
  try {
    const { merchant_id, action, reason, reviewed_by } = req.body || {}
    if (!merchant_id) return res.status(400).json({ error: 'merchant_id is required' })
    if (!action || !['APPROVE', 'REJECT', 'VERIFY'].includes(action.toUpperCase())) {
      return res.status(400).json({ error: 'Action must be APPROVE or REJECT' })
    }

    const updated = await reviewMerchantKyc(merchant_id, {
      action: action.toUpperCase(),
      reason,
      reviewed_by: reviewed_by || 'ADMIN',
    })

    const status = (action.toUpperCase() === 'REJECT') ? 'REJECTED' : 'VERIFIED'

    if (req.io) {
      req.io.to(`merchant:${merchant_id}`).emit('merchant:kyc_status', {
        status,
        reason: reason || null,
        updated_at: new Date().toISOString(),
      })
      req.io.emit('admin:kyc_reviewed', {
        merchant_id,
        status,
        reviewed_at: new Date().toISOString(),
      })
    }

    res.json({
      ok: true,
      message: `Merchant KYC has been ${status.toLowerCase()} successfully.`,
      merchant: updated,
    })
  } catch (err) {
    console.error('[kyc/review POST]', err.message)
    res.status(500).json({ error: 'Failed to review KYC: ' + err.message })
  }
})

export { router as kycRouter }
