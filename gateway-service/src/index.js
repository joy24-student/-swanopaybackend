import fs from 'node:fs'
import express from 'express'
import helmet from 'helmet'
import { rateLimit } from 'express-rate-limit'
import pg from 'pg'
import { apiKeyDigest, isProjectRef, isUuid, normalizedEmail, payloadDigest, safeHeader } from './security.js'
import { sendReceipt } from './gmail.js'

const required = ['DATABASE_URL', 'API_KEY_PEPPER', 'GMAIL_CLIENT_ID', 'GMAIL_CLIENT_SECRET', 'GMAIL_REFRESH_TOKEN', 'GMAIL_FROM_EMAIL']
for (const name of required) if (!process.env[name]) throw new Error(`${name} is required`)
if (process.env.API_KEY_PEPPER.length < 32) throw new Error('API_KEY_PEPPER must be at least 32 characters')

const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: true } : false,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000
})
await pool.query(fs.readFileSync(new URL('../sql/001_gateway_receipts.sql', import.meta.url), 'utf8'))

const gmail = {
  clientId: process.env.GMAIL_CLIENT_ID,
  clientSecret: process.env.GMAIL_CLIENT_SECRET,
  refreshToken: process.env.GMAIL_REFRESH_TOKEN,
  fromEmail: normalizedEmail(process.env.GMAIL_FROM_EMAIL),
  fromName: safeHeader(process.env.GMAIL_FROM_NAME || 'SwapnoPay Payments', 100),
  publicOrigin: process.env.PUBLIC_RECEIPT_ORIGIN || 'https://pay.swapnopay.top'
}
if (!gmail.fromEmail) throw new Error('GMAIL_FROM_EMAIL is invalid')

const app = express()
app.set('trust proxy', Number(process.env.TRUST_PROXY_HOPS || 1))
app.disable('x-powered-by')
app.use(helmet({ contentSecurityPolicy: false }))
app.use(express.json({ limit: '48kb', strict: true }))
app.use(rateLimit({ windowMs: 60_000, limit: 120, standardHeaders: 'draft-8', legacyHeaders: false }))

app.get('/healthz', async (_request, res) => {
  try {
    await pool.query('select 1')
    res.json({ ok: true, service: 'swapnopay-gateway', mail_provider: 'gmail-api' })
  } catch {
    res.status(503).json({ ok: false })
  }
})

app.post('/v1/payment-receipts', async (request, res) => {
  const bearer = String(request.headers.authorization || '').match(/^Bearer\s+(.+)$/i)?.[1] || ''
  if (!bearer.startsWith('sp_receipt_') || bearer.length > 128) return res.status(401).json({ error: 'Unauthorized' })
  const digest = apiKeyDigest(bearer, process.env.API_KEY_PEPPER)
  const connectionResult = await pool.query(
    'select id, project_ref, merchant_id, daily_email_limit from merchant_gateway_connections where api_key_digest=$1 and enabled=true',
    [digest]
  )
  const connection = connectionResult.rows[0]
  if (!connection) return res.status(401).json({ error: 'Unauthorized' })

  const event = request.body || {}
  const validationError = validateEvent(event, connection)
  if (validationError) return res.status(422).json({ error: validationError })
  if (String(request.headers['idempotency-key'] || '') !== `${event.project_ref}:${event.event_id}`) {
    return res.status(400).json({ error: 'Idempotency-Key does not match the receipt event' })
  }
  const digestOfPayload = payloadDigest(event)
  const client = await pool.connect()
  let delivery
  try {
    await client.query('begin')
    await client.query(
      `insert into payment_receipt_deliveries
        (connection_id,project_ref,merchant_id,event_id,order_id,transaction_id,payload_digest)
       values ($1,$2,$3,$4,$5,$6,$7) on conflict (project_ref,event_id) do nothing`,
      [connection.id, event.project_ref, event.merchant.id, event.event_id, event.order.id, event.order.transaction_id, digestOfPayload]
    )
    const selected = await client.query(
      'select * from payment_receipt_deliveries where project_ref=$1 and event_id=$2 for update',
      [event.project_ref, event.event_id]
    )
    delivery = selected.rows[0]
    if (delivery.payload_digest !== digestOfPayload) {
      await client.query('rollback')
      return res.status(409).json({ error: 'Idempotency key was reused with a different payload' })
    }
    if (delivery.status === 'SENT') {
      await client.query('commit')
      return res.json({ ok: true, idempotent: true, event_id: event.event_id })
    }
    if (delivery.status === 'PROCESSING' && delivery.claimed_at && Date.now() - new Date(delivery.claimed_at).getTime() < 120_000) {
      await client.query('rollback')
      return res.status(409).set('Retry-After', '30').json({ error: 'Receipt is already processing' })
    }
    const quota = await client.query(
      `select coalesce(sum(
         (customer_message_id is not null)::integer +
         (merchant_message_id is not null and merchant_message_id is distinct from customer_message_id)::integer
       ),0)::integer as count from payment_receipt_deliveries
       where connection_id=$1 and created_at >= date_trunc('day', now())`, [connection.id]
    )
    const customerRecipient = normalizedEmail(event.order.customer_email)
    const merchantRecipient = normalizedEmail(event.merchant.receipt_email)
    const intendedMessages = (customerRecipient && !delivery.customer_message_id ? 1 : 0) +
      (merchantRecipient && merchantRecipient !== customerRecipient && !delivery.merchant_message_id ? 1 : 0)
    if (quota.rows[0].count + intendedMessages > connection.daily_email_limit) {
      await client.query('rollback')
      return res.status(429).set('Retry-After', '3600').json({ error: 'Daily transactional email quota reached' })
    }
    const updated = await client.query(
      `update payment_receipt_deliveries set status='PROCESSING',attempts=attempts+1,claimed_at=now(),updated_at=now(),last_error=null
       where id=$1 returning *`, [delivery.id]
    )
    delivery = updated.rows[0]
    await client.query('commit')
  } catch (error) {
    await client.query('rollback').catch(() => {})
    console.error('Receipt claim failed', error)
    return res.status(503).json({ error: 'Receipt service temporarily unavailable' })
  } finally {
    client.release()
  }

  try {
    let customerMessageId = delivery.customer_message_id
    let merchantMessageId = delivery.merchant_message_id
    const customerEmail = normalizedEmail(event.order.customer_email)
    const merchantEmail = normalizedEmail(event.merchant.receipt_email)
    if (customerEmail && !customerMessageId) {
      customerMessageId = await sendReceipt(gmail, event, 'customer')
      await pool.query('update payment_receipt_deliveries set customer_message_id=$1,updated_at=now() where id=$2', [customerMessageId, delivery.id])
    }
    if (merchantEmail && merchantEmail !== customerEmail && !merchantMessageId) {
      merchantMessageId = await sendReceipt(gmail, event, 'merchant')
      await pool.query('update payment_receipt_deliveries set merchant_message_id=$1,updated_at=now() where id=$2', [merchantMessageId, delivery.id])
    } else if (merchantEmail === customerEmail) {
      merchantMessageId = customerMessageId
    }
    await pool.query(
      `update payment_receipt_deliveries set status='SENT',customer_message_id=$1,merchant_message_id=$2,
       delivered_at=now(),updated_at=now() where id=$3`, [customerMessageId || null, merchantMessageId || null, delivery.id]
    )
    return res.json({ ok: true, event_id: event.event_id, customer_sent: Boolean(customerMessageId), merchant_sent: Boolean(merchantMessageId) })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Receipt delivery failed'
    console.error('Receipt delivery failed', event.event_id, message)
    await pool.query(
      `update payment_receipt_deliveries set status='FAILED',last_error=$1,updated_at=now() where id=$2`,
      [message.slice(0, 1000), delivery.id]
    ).catch(() => {})
    return res.status(502).json({ error: 'Transactional email provider failed' })
  }
})

app.use((_request, res) => res.status(404).json({ error: 'Not found' }))
app.use((error, _request, res, _next) => {
  console.error('Unhandled request error', error)
  res.status(error?.type === 'entity.too.large' ? 413 : 500).json({ error: 'Request failed' })
})

function validateEvent(event, connection) {
  if (event.schema_version !== 1 || event.source !== 'merchant_database' || event.event_type !== 'PAYMENT_PAID') return 'Unsupported receipt event'
  if (!isProjectRef(event.project_ref) || event.project_ref !== connection.project_ref) return 'Project does not match this connection'
  if (!isUuid(event.event_id) || !isUuid(event?.merchant?.id) || !isUuid(event?.order?.id)) return 'Invalid event identifiers'
  if (String(event.merchant.id) !== String(connection.merchant_id)) return 'Merchant does not match this connection'
  if (!/^[A-Za-z0-9_-]{1,120}$/.test(String(event.order.transaction_id || ''))) return 'Invalid transaction reference'
  if (!Number.isFinite(Number(event.order.amount)) || Number(event.order.amount) <= 0 || Number(event.order.amount) > 10_000_000) return 'Invalid paid amount'
  if (event.order.currency !== 'BDT') return 'Unsupported currency'
  if (!['ATOMIC_SMS_MATCH', 'MERCHANT_APPROVED_APPEAL'].includes(event.order.verification)) return 'Unsupported verification source'
  const paidAt = Date.parse(event.order.paid_at)
  if (!Number.isFinite(paidAt) || paidAt > Date.now() + 300_000 || paidAt < Date.now() - 90 * 86_400_000) return 'Invalid paid timestamp'
  if (!normalizedEmail(event.order.customer_email) && !normalizedEmail(event.merchant.receipt_email)) return 'No valid receipt recipient'
  return ''
}

const port = Number(process.env.PORT || 8080)
const server = app.listen(port, '0.0.0.0', () => console.log(`SwapnoPay gateway service listening on ${port}`))
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close(() => pool.end().finally(() => process.exit(0))))
