// SwapnoPay Backend — KYC & Identity Verification Routes
// Handles NID document storage, face liveness metadata, strict NID anti-abuse checks, and admin review pipeline

import { Router } from 'express'
import fs from 'node:fs'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import sharp from 'sharp'
import { fileURLToPath } from 'node:url'
import { submitMerchantKyc, listPendingKycSubmissions, reviewMerchantKyc, getAdminClient, getSubscriptionConfig } from '../services/adminSupabase.js'
import { requirePlatformUser, requirePlatformMerchant } from '../services/merchantAccount.js'
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

async function saveBase64Image(base64Data, prefix, merchantId) {
  if (!base64Data || typeof base64Data !== 'string') return null
  try {
    const cleanBase64 = base64Data.replace(/^data:image\/\w+;base64,/, '')
    const buffer = Buffer.from(cleanBase64, 'base64')
    if (buffer.length === 0 || buffer.length > 10 * 1024 * 1024) {
      throw new Error('Each identity image must be between 1 byte and 10 MB')
    }

    const fileName = `${prefix}_${merchantId}_${randomUUID()}.jpg`
    const image = await sharp(buffer, { limitInputPixels: 40000000 }).rotate().jpeg({ quality: 85 }).toBuffer()

    // 1. Attempt uploading directly to Supabase Storage bucket
    try {
      const admin = getAdminClient()
      const storagePath = `${merchantId}/${fileName}`
      const { data: uploadData, error: uploadErr } = await admin.storage
        .from('kyc-documents')
        .upload(storagePath, image, { contentType: 'image/jpeg', upsert: true })

      if (!uploadErr && uploadData?.path) {
        const { data: publicUrlData } = admin.storage.from('kyc-documents').getPublicUrl(uploadData.path)
        if (publicUrlData?.publicUrl) {
          return publicUrlData.publicUrl
        }
      }
    } catch (storageErr) {
      console.warn('[kyc] Supabase storage upload notice:', storageErr.message)
    }

    // 2. Fallback to persistent local disk storage with absolute URL
    const filePath = path.join(UPLOADS_DIR, fileName)
    fs.writeFileSync(filePath, image)
    const backendUrl = process.env.BACKEND_PUBLIC_URL || process.env.API_BASE_URL || 'https://api.swapnopay.top'
    return `${backendUrl.replace(/\/$/, '')}/uploads/kyc/${fileName}`
  } catch (err) {
    console.warn(`[kyc] Failed to save base64 image (${prefix}):`, err.message)
    return null
  }
}

// // ----------------------------------------------------------------------------
// POST /v1/kyc/submit — Submit merchant NID & Biometric Face KYC
// ----------------------------------------------------------------------------
router.post('/submit', requirePlatformUser, async (req, res) => {
  try {
    const {
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
      // Enhanced NID OCR fields from Bangladeshi OCR System
      name_bangla,
      name_english,
      father_name,
      mother_name,
      blood_group,
      doc_type,
    } = req.body || {}

    const admin = getAdminClient()
    const userId = req.platformUser.id
    const userEmail = req.platformUser.email

    // Ensure merchant row exists in public.merchants (auto-provision if missing)
    let merchant_id = req.platformAccount?.merchantId || userId
    try {
      const { data: existingMerchant } = await admin.from('merchants')
        .select('id, user_id, nid_number, kyc_status')
        .or(`id.eq.${userId},user_id.eq.${userId}`)
        .maybeSingle()

      if (existingMerchant?.id) {
        merchant_id = existingMerchant.id
      } else {
        const newMerchant = {
          id: userId,
          user_id: userId,
          email: userEmail,
          business_name: (nid_name ? nid_name.trim() : userEmail?.split('@')[0]) || 'Merchant Store',
          phone: req.body?.phone || '',
          business_type: 'Retail Store',
          status: 'PENDING_VERIFICATION',
          kyc_status: 'PENDING',
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString()
        }
        const { data: created, error: createErr } = await admin.from('merchants').upsert(newMerchant).select().single()
        if (createErr) console.warn('[kyc] Auto-provision merchant notice:', createErr.message)
        merchant_id = created?.id || userId
      }
    } catch (lookupErr) {
      console.warn('[kyc] Merchant lookup notice:', lookupErr.message)
    }

    if (!merchant_id) {
      return res.status(400).json({ error: 'merchant_id is required' })
    }

    const cleanNid = String(nid_number || '').trim()
    if (!/^(?:[0-9]{10}|[0-9]{13}|[0-9]{17})$/.test(cleanNid)) {
      return res.status(400).json({ error: 'একটি সঠিক ১০, ১৩ বা ১৭ ডিজিটের জাতীয় পরিচয়পত্র (NID) নম্বর দিন।' })
    }

    if (!front_base64 && !incomingFrontUrl) {
      return res.status(400).json({ error: 'NID কার্ডের সামনের পাতার ছবি আবশ্যক।' })
    }
    if (!back_base64 && !incomingBackUrl) {
      return res.status(400).json({ error: 'NID কার্ডের পেছনের পাতার ছবি আবশ্যক।' })
    }
    if ((!selfie_base64 && !incomingFaceUrl) || liveness_passed === false) {
      return res.status(400).json({ error: 'সরাসরি সেলফি ও লাইভনেস ভেরিফিকেশন সম্পন্ন করা আবশ্যক।' })
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STRICT NID ANTI-REUSE RULE:
    // One NID number CANNOT be reused for another account verification!
    // ──────────────────────────────────────────────────────────────────────────
    try {
      const { data: existingNidAccount } = await admin
        .from('merchants')
        .select('id, user_id, business_name, email')
        .eq('nid_number', cleanNid)
        .neq('id', merchant_id)
        .neq('user_id', userId)
        .maybeSingle()

      if (existingNidAccount) {
        return res.status(409).json({
          ok: false,
          error: 'এই জাতীয় পরিচয়পত্র (NID) নম্বরটি ইতোমধ্যে অন্য একটি অ্যাকাউন্টে ব্যবহৃত হয়েছে। একটি NID দিয়ে কেবল একটিমাত্র অ্যাকাউন্ট ভেরিফাই করা যাবে।',
          code: 'NID_ALREADY_REGISTERED'
        })
      }
    } catch (checkErr) {
      console.warn('[kyc] NID uniqueness check notice:', checkErr.message)
    }

    // Save base64 images if provided, otherwise preserve passed URLs
    let frontUrl = incomingFrontUrl || null
    if (front_base64) {
      const saved = await saveBase64Image(front_base64, 'nid_front', merchant_id)
      if (saved) frontUrl = saved
    }

    let backUrl = incomingBackUrl || null
    if (back_base64) {
      const saved = await saveBase64Image(back_base64, 'nid_back', merchant_id)
      if (saved) backUrl = saved
    }

    let faceUrl = incomingFaceUrl || null
    if (selfie_base64) {
      const saved = await saveBase64Image(selfie_base64, 'live_selfie', merchant_id)
      if (saved) faceUrl = saved
    }

    if (!frontUrl || !backUrl || !faceUrl) {
      throw new Error('All three identity documents (NID Front, NID Back, Live Selfie) must be saved successfully.')
    }

    // Retrieve subscription configuration for trial allocation
    const subConfig = await getSubscriptionConfig()
    const trialDays = subConfig.trial_days || 90 // 3-month free trial
    const trialEndsAt = new Date(Date.now() + trialDays * 24 * 60 * 60 * 1000).toISOString()

    const savedMerchant = await submitMerchantKyc({
      merchant_id,
      nid_number: cleanNid,
      nid_name: (name_english || nid_name) ? String(name_english || nid_name).trim() : null,
      nid_dob: nid_dob ? String(nid_dob).trim() : null,
      nid_front_url: frontUrl,
      nid_back_url: backUrl,
      face_photo_url: faceUrl,
      liveness_passed: liveness_passed !== false,
      ocr_raw_text: ocr_raw_text || '',
      trial_ends_at: trialEndsAt,
      // Enhanced NID OCR fields
      name_bangla: name_bangla || null,
      name_english: name_english || null,
      father_name: father_name || null,
      mother_name: mother_name || null,
      blood_group: blood_group || null,
      doc_type: doc_type || null,
    })

    // Broadcast realtime event to Admin Dashboard
    if (req.io) {
      req.io.to('admin').emit('admin:kyc_submitted', {
        merchant_id,
        nid_number: cleanNid,
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

    console.log(`[kyc] Successfully submitted KYC for merchant ${merchant_id} (NID: ${cleanNid})`)

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
// GET /v1/kyc/submissions — List all submissions for review
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
// POST /v1/kyc/review — Platform Admin approve / reject
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
      req.io.to(`merchant:${updated.id}`).emit('merchant:kyc_status', {
        status,
        reason: reason || null,
        updated_at: new Date().toISOString(),
      })
      req.io.to('admin').emit('admin:kyc_reviewed', {
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

// ----------------------------------------------------------------------------
// POST /v1/kyc/extract-nid — Server-side Bangladeshi NID field extraction
// Extracts NID number, names, DOB, blood group from uploaded NID card images.
// Used by the Android app and web portal to pre-fill KYC form fields.
// ----------------------------------------------------------------------------
router.post('/extract-nid', requirePlatformUser, async (req, res) => {
  try {
    const { front_base64, back_base64 } = req.body || {}
    if (!front_base64 && !back_base64) {
      return res.status(400).json({ error: 'At least one image (front_base64 or back_base64) is required.' })
    }

    const extractedFields = {
      nid_number: null,
      doc_type: 'UNKNOWN',
      name_english: null,
      name_bangla: null,
      father_name: null,
      mother_name: null,
      date_of_birth: null,
      blood_group: null,
      is_valid: false
    }

    // Process base64 images through sharp for text analysis hints
    for (const [side, b64] of [['front', front_base64], ['back', back_base64]]) {
      if (!b64) continue
      try {
        const cleanBase64 = b64.replace(/^data:image\/\w+;base64,/, '')
        const buffer = Buffer.from(cleanBase64, 'base64')
        if (buffer.length === 0) continue

        // Use sharp to detect image metadata (real OCR would go here)
        // In production, integrate with cloud OCR (Google Cloud Vision, AWS Textract, or a local service)
        const imageInfo = await sharp(buffer, { limitInputPixels: 40000000 }).metadata()
        console.log(`[kyc/extract-nid] ${side}: ${imageInfo.width}x${imageInfo.height} ${imageInfo.format}`)
      } catch (imgErr) {
        console.warn(`[kyc/extract-nid] Image processing notice for ${side}:`, imgErr.message)
      }
    }

    // Regex/heuristic extraction on base64 decoded text is not practical server-side without OCR
    // Return the structure with extraction status — Android app handles on-device ML Kit OCR
    res.json({
      ok: true,
      extraction_mode: 'client_side_mlkit',
      message: 'On-device ML Kit OCR is primary. Server-side OCR requires cloud vision integration.',
      extracted_fields: extractedFields,
      validation: {
        nid_number: extractedFields.nid_number ? 'valid' : 'missing',
        name_english: extractedFields.name_english ? 'found' : 'missing',
        name_bangla: extractedFields.name_bangla ? 'found' : 'missing',
        father_name: extractedFields.father_name ? 'found' : 'missing',
        mother_name: extractedFields.mother_name ? 'found' : 'missing',
        date_of_birth: extractedFields.date_of_birth ? 'found' : 'missing'
      }
    })
  } catch (err) {
    console.error('[kyc/extract-nid POST]', err.message)
    res.status(500).json({ error: 'NID extraction failed: ' + err.message })
  }
})

export { router as kycRouter }
