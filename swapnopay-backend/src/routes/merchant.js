import { Router } from 'express'
import { getAdminClient, getShowcaseConfig } from '../services/adminSupabase.js'
import { requireData, requirePlatformUser, requirePlatformMerchant } from '../services/merchantAccount.js'
import {
  saveChatMessage,
  getChatMessages,
  saveSupportTicket,
  updateSupportTicket,
  getSupportTickets,
  saveFeatureRequest,
  updateFeatureRequest,
  getFeatureRequests,
} from '../services/supportService.js'

export const merchantRouter = Router()

// Public system config & notices — accessible to all app clients without requiring active session
merchantRouter.get('/system-config', async (_req, res) => {
  try {
    let config = await getShowcaseConfig('system_config')
    if (!config) {
      const { data } = await getAdminClient().from('showcase_config').select('value')
        .eq('key', 'system_config').maybeSingle()
      config = data?.value
    }
    if (!config) return res.status(503).json({ error: 'Official support settings have not been configured' })
    res.json({ ok: true, config })
  } catch (error) { res.status(503).json({ error: error.message }) }
})

// Public / Direct Live Chat endpoints for merchant mobile app & admin helpdesk sync
merchantRouter.get('/support/chat', async (req, res) => {
  try {
    const rawId = req.headers['x-merchant-id'] || req.query.merchant_id || req.headers['x-device-id'] || null
    const merchantId = (!rawId || rawId === 'ALL') ? null : rawId
    const messages = await getChatMessages(merchantId, 300)
    res.json({ ok: true, messages: messages || [] })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

merchantRouter.post('/support/chat', async (req, res) => {
  try {
    const body = req.body || {}
    const merchantId = req.headers['x-merchant-id'] || body.merchant_id || req.headers['x-device-id']
    if (!merchantId) {
      return res.status(400).json({ error: 'Merchant identifier is required' })
    }
    const msg = body.message
    if (typeof msg !== 'string' || !msg.trim()) {
      return res.status(400).json({ error: 'Message content is required' })
    }
    const saved = await saveChatMessage({
      merchant_id: merchantId,
      session_id: body.session_id || '',
      sender: body.sender || 'MERCHANT',
      message: msg.trim(),
      attachment_name: body.attachment_name || '',
      attachment_type: body.attachment_type || '',
      attachment_base64: body.attachment_base64 || '',
    })
    res.status(201).json({ ok: true, record: saved })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

// Public / Direct Support Tickets endpoints for merchant mobile app & admin helpdesk sync
merchantRouter.get('/support/tickets', async (req, res) => {
  try {
    const rawId = req.headers['x-merchant-id'] || req.query.merchant_id || req.headers['x-device-id'] || null
    const merchantId = (!rawId || rawId === 'ALL') ? null : rawId
    const tickets = await getSupportTickets(merchantId, 200)
    res.json({ ok: true, tickets: tickets || [] })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

merchantRouter.post('/support/tickets', async (req, res) => {
  try {
    const body = req.body || {}
    const merchantId = req.headers['x-merchant-id'] || body.merchant_id || req.headers['x-device-id'] || 'default_merchant'
    const subject = body.subject
    const description = body.description
    if (typeof subject !== 'string' || !subject.trim() || typeof description !== 'string' || !description.trim()) {
      return res.status(400).json({ error: 'Subject and description are required' })
    }
    const saved = await saveSupportTicket({
      merchant_id: merchantId,
      business_name: body.business_name || 'My Business',
      email: body.email || null,
      phone: body.phone || null,
      category: body.category || 'GENERAL',
      subject: subject.trim(),
      description: description.trim(),
      status: 'OPEN',
    })
    res.status(201).json({ ok: true, record: saved })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

const handleUpdateTicket = async (req, res) => {
  try {
    const ticketId = req.params.id
    const updated = await updateSupportTicket(ticketId, req.body || {})
    res.json({ ok: true, record: updated })
  } catch (error) {
    res.status(500).json({ error: error.message })
  }
}
merchantRouter.patch('/support/tickets/:id', handleUpdateTicket)
merchantRouter.post('/support/tickets/:id', handleUpdateTicket)

// Public / Direct Feature Requests endpoints
merchantRouter.get('/support/features', async (req, res) => {
  try {
    const rawId = req.headers['x-merchant-id'] || req.query.merchant_id || null
    const merchantId = (!rawId || rawId === 'ALL') ? null : rawId
    const features = await getFeatureRequests(merchantId, 200)
    res.json({ ok: true, features: features || [] })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

merchantRouter.post('/support/features', async (req, res) => {
  try {
    const body = req.body || {}
    const merchantId = req.headers['x-merchant-id'] || body.merchant_id || req.headers['x-device-id'] || 'default_merchant'
    const title = body.title || body.subject
    const description = body.description
    if (typeof title !== 'string' || !title.trim() || typeof description !== 'string' || !description.trim()) {
      return res.status(400).json({ error: 'Title and description are required' })
    }
    const saved = await saveFeatureRequest({
      merchant_id: merchantId,
      business_name: body.business_name || 'My Business',
      email: body.email || null,
      phone: body.phone || null,
      category: body.category || 'GENERAL',
      priority: body.priority || 'MEDIUM',
      title: title.trim(),
      description: description.trim(),
      status: 'PENDING',
    })
    res.status(201).json({ ok: true, record: saved })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

const handleUpdateFeature = async (req, res) => {
  try {
    const featureId = req.params.id
    const updated = await updateFeatureRequest(featureId, req.body || {})
    res.json({ ok: true, record: updated })
  } catch (error) {
    res.status(500).json({ error: error.message })
  }
}
merchantRouter.patch('/support/features/:id', handleUpdateFeature)
merchantRouter.post('/support/features/:id', handleUpdateFeature)

// Account & Data Deletion endpoint (Merchant In-App / Self-Serve Google Play Compliance)
const handleAccountDelete = async (req, res) => {
  try {
    const rawId = req.headers['x-merchant-id'] || req.body?.merchant_id || req.query.merchant_id || null
    let merchantId = rawId
    
    // Also check Authorization header token if present
    const bearer = req.headers.authorization?.replace(/^Bearer\s+/i, '')
    if (!merchantId && bearer) {
      try {
        const { getAdminClient } = await import('../services/adminSupabase.js')
        const { data: { user } } = await getAdminClient().auth.getUser(bearer)
        if (user?.id) merchantId = user.id
      } catch (_) {}
    }

    if (!merchantId) {
      return res.status(400).json({ error: 'Merchant ID or valid authorization is required to delete account' })
    }

    const { deleteMerchantAccountPermanently } = await import('../services/adminSupabase.js')
    const result = await deleteMerchantAccountPermanently(merchantId)
    console.log(`[merchant/account] Permanent account deletion completed for merchant: ${merchantId}`)
    res.json(result)
  } catch (error) {
    console.error('[merchant/account delete error]', error.message)
    res.status(500).json({ error: error.message || 'Failed to delete merchant account' })
  }
}
merchantRouter.delete('/account', handleAccountDelete)
merchantRouter.post('/account/delete', handleAccountDelete)

merchantRouter.use(requirePlatformUser)
merchantRouter.use(requirePlatformMerchant)

merchantRouter.get('/support', async (req, res) => {
  try {
    const id = req.platformAccount.merchantId
    const [tickets, messages] = await Promise.all([
      getSupportTickets(id, 100),
      getChatMessages(id, 200),
    ])
    res.json({ ok: true, tickets: tickets || [], messages: messages || [] })
  } catch (error) { res.status(503).json({ error: error.message }) }
})

merchantRouter.post('/support/messages', async (req, res) => {
  try {
    const body = req.body || {}
    const merchant = req.platformAccount.merchant
    if (typeof body.message !== 'string' || !body.message.trim() || body.message.length > 10000) {
      return res.status(400).json({ error: 'A message of 1–10000 characters is required' })
    }
    const saved = await saveChatMessage({
      merchant_id: merchant.id,
      sender: 'MERCHANT',
      message: body.message.trim(),
    })
    return res.status(201).json({ ok: true, record: saved })
  } catch (error) { res.status(503).json({ error: error.message }) }
})

