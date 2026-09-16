import { Router } from 'express'
import { getAdminClient } from '../services/adminSupabase.js'
import { requireData, requirePlatformUser, requirePlatformMerchant } from '../services/merchantAccount.js'

export const merchantRouter = Router()
merchantRouter.use(requirePlatformUser)
merchantRouter.get('/system-config', async (_req, res) => {
  try {
    const row = requireData(await getAdminClient().from('showcase_config').select('value')
      .eq('key', 'system_config').maybeSingle(), 'Load official support settings')
    if (!row) return res.status(503).json({ error: 'Official support settings have not been configured' })
    res.json({ ok: true, config: row.value })
  } catch (error) { res.status(503).json({ error: error.message }) }
})
merchantRouter.use(requirePlatformMerchant)
merchantRouter.get('/support', async (req, res) => {
  try {
    const admin = getAdminClient()
    const id = req.platformAccount.merchantId
    const tickets = requireData(await admin.from('support_tickets').select('*').eq('merchant_id', id)
      .order('created_at', { ascending: false }).limit(100), 'Load support tickets')
    const messages = requireData(await admin.from('live_chat_messages').select('*').eq('merchant_id', id)
      .order('created_at', { ascending: false }).limit(200), 'Load support replies')
    res.json({ ok: true, tickets, messages: (messages || []).reverse() })
  } catch (error) { res.status(503).json({ error: error.message }) }
})
for (const [route, table] of [['tickets', 'support_tickets'], ['messages', 'live_chat_messages'], ['features', 'feature_requests']]) {
  merchantRouter.post(`/support/${route}`, async (req, res) => {
    try {
      const body = req.body || {}
      const merchant = req.platformAccount.merchant
      const row = { merchant_id: merchant.id }
      if (route === 'messages') {
        if (typeof body.message !== 'string' || !body.message.trim() || body.message.length > 10000) {
          return res.status(400).json({ error: 'A message of 1–10000 characters is required' })
        }
        Object.assign(row, { sender: 'MERCHANT', message: body.message.trim() })
      } else {
        const title = route === 'tickets' ? 'subject' : 'title'
        if (typeof body[title] !== 'string' || !body[title].trim() || typeof body.description !== 'string' || !body.description.trim()) {
          return res.status(400).json({ error: 'Title and description are required' })
        }
        Object.assign(row, { business_name: merchant.business_name, email: merchant.email,
          category: body.category || 'GENERAL', [title]: body[title].trim(), description: body.description.trim() })
        if (route === 'tickets') row.phone = merchant.phone
        else row.priority = body.priority || 'MEDIUM'
      }
      const saved = requireData(await getAdminClient().from(table).insert(row).select('*').single(), 'Save support request')
      res.status(201).json({ ok: true, record: saved })
    } catch (error) { res.status(503).json({ error: error.message }) }
  })
}
