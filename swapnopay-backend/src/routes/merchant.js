import { Router } from 'express'
import { getAdminClient } from '../services/adminSupabase.js'
import { requireData, requirePlatformUser, requirePlatformMerchant } from '../services/merchantAccount.js'
import {
  saveChatMessage,
  getChatMessages,
  saveSupportTicket,
  getSupportTickets,
  saveFeatureRequest,
  getFeatureRequests,
} from '../services/supportService.js'

export const merchantRouter = Router()

// Public system config & notices — accessible to all app clients without requiring active session
merchantRouter.get('/system-config', async (_req, res) => {
  try {
    const row = requireData(await getAdminClient().from('showcase_config').select('value')
      .eq('key', 'system_config').maybeSingle(), 'Load official support settings')
    if (!row) return res.status(503).json({ error: 'Official support settings have not been configured' })
    res.json({ ok: true, config: row.value })
  } catch (error) { res.status(503).json({ error: error.message }) }
})

// Public / Direct Live Chat endpoints for merchant mobile app & admin helpdesk sync
merchantRouter.get('/support/chat', async (req, res) => {
  try {
    const merchantId = req.headers['x-merchant-id'] || req.query.merchant_id || req.headers['x-device-id']
    if (!merchantId) {
      return res.status(400).json({ error: 'Merchant identifier is required' })
    }
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
    const merchantId = req.headers['x-merchant-id'] || req.query.merchant_id || req.headers['x-device-id']
    if (!merchantId) {
      return res.status(400).json({ error: 'Merchant identifier is required' })
    }
    const tickets = await getSupportTickets(merchantId, 100)
    res.json({ ok: true, tickets: tickets || [] })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

merchantRouter.post('/support/tickets', async (req, res) => {
  try {
    const body = req.body || {}
    const merchantId = req.headers['x-merchant-id'] || body.merchant_id || req.headers['x-device-id']
    if (!merchantId) {
      return res.status(400).json({ error: 'Merchant identifier is required' })
    }
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

// Public / Direct Feature Requests endpoints
merchantRouter.get('/support/features', async (req, res) => {
  try {
    const merchantId = req.headers['x-merchant-id'] || req.query.merchant_id || null
    const features = await getFeatureRequests(merchantId, 100)
    res.json({ ok: true, features: features || [] })
  } catch (error) {
    res.status(503).json({ error: error.message })
  }
})

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

for (const [route] of [['messages', 'live_chat_messages'], ['features', 'feature_requests']]) {
  merchantRouter.post(`/support/${route}`, async (req, res) => {
    try {
      const body = req.body || {}
      const merchant = req.platformAccount.merchant
      if (route === 'messages') {
        if (typeof body.message !== 'string' || !body.message.trim() || body.message.length > 10000) {
          return res.status(400).json({ error: 'A message of 1–10000 characters is required' })
        }
        const saved = await saveChatMessage({
          merchant_id: merchant.id,
          sender: 'MERCHANT',
          message: body.message.trim(),
        })
        return res.status(201).json({ ok: true, record: saved })
      } else {
        const title = body.title || body.subject
        if (typeof title !== 'string' || !title.trim() || typeof body.description !== 'string' || !body.description.trim()) {
          return res.status(400).json({ error: 'Title and description are required' })
        }
        const saved = await saveFeatureRequest({
          merchant_id: merchant.id,
          business_name: merchant.business_name,
          email: merchant.email,
          category: body.category || 'GENERAL',
          priority: body.priority || 'MEDIUM',
          title: title.trim(),
          description: body.description.trim(),
        })
        return res.status(201).json({ ok: true, record: saved })
      }
    } catch (error) { res.status(503).json({ error: error.message }) }
  })
}
