import { escapeHtml, normalizedEmail, safeHeader } from './security.js'

let cachedToken = null
let tokenExpiresAt = 0

async function accessToken(config) {
  if (cachedToken && Date.now() < tokenExpiresAt - 60_000) return cachedToken
  const body = new URLSearchParams({
    client_id: config.clientId,
    client_secret: config.clientSecret,
    refresh_token: config.refreshToken,
    grant_type: 'refresh_token'
  })
  const result = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body,
    signal: AbortSignal.timeout(10_000)
  })
  const payload = await result.json().catch(() => ({}))
  if (!result.ok || !payload.access_token) throw new Error(`Gmail OAuth refresh failed (${result.status})`)
  cachedToken = payload.access_token
  tokenExpiresAt = Date.now() + Math.max(60, Number(payload.expires_in || 3600)) * 1000
  return cachedToken
}

function encodeHeader(value) {
  return `=?UTF-8?B?${Buffer.from(safeHeader(value), 'utf8').toString('base64')}?=`
}

function messageHtml(event, recipientRole, publicOrigin) {
  const merchant = event.merchant
  const order = event.order
  const heading = recipientRole === 'merchant' ? 'Payment received' : 'Payment successful'
  const verification = order.verification === 'ATOMIC_SMS_MATCH' ? 'Automatically verified' : 'Verified by merchant review'
  const merchantRows = recipientRole === 'merchant' ? `
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Customer</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.customer_name || 'Customer')}</td></tr>
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Customer phone</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.customer_phone || 'N/A')}</td></tr>
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Product</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.product_name || 'Payment')}</td></tr>` : ''
  return `<!doctype html><html><body style="margin:0;background:#f8fafc;color:#0f172a;font-family:Arial,sans-serif">
    <div style="max-width:620px;margin:24px auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden">
      <div style="background:#111827;color:#fff;padding:24px"><div style="color:#f59e0b;font-weight:700">SwapnoPay</div><h1 style="margin:8px 0 0;font-size:24px">${heading}</h1></div>
      <div style="padding:24px"><p>Payment for <strong>${escapeHtml(merchant.business_name)}</strong> has been verified in the merchant database.</p>
      <table style="width:100%;border-collapse:collapse">
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Amount</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right;font-weight:700">BDT ${Number(order.amount).toFixed(2)}</td></tr>
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Order reference</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.transaction_id)}</td></tr>
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Provider TrxID</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.provider_transaction_id || 'N/A')}</td></tr>
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Method</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.payment_method)}</td></tr>
        ${merchantRows}
        <tr><td style="padding:10px;border-bottom:1px solid #eee">Paid at</td><td style="padding:10px;border-bottom:1px solid #eee;text-align:right">${escapeHtml(order.paid_at)}</td></tr>
        <tr><td style="padding:10px">Verification</td><td style="padding:10px;text-align:right">${verification}</td></tr>
      </table>
      <p style="margin-top:22px;color:#475569">Receipt ID: ${escapeHtml(event.event_id)}</p>
      <p style="font-size:12px;color:#64748b">This fixed transactional receipt was sent by SwapnoPay Payments. Never share OTP, PIN, or passwords by email.</p></div>
    </div></body></html>`
}

function rawMessage(config, to, subject, html, messageId) {
  const boundary = `swapnopay_${messageId.replace(/[^a-z0-9]/gi, '')}`
  const text = html.replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
  const lines = [
    `From: ${encodeHeader(config.fromName)} <${config.fromEmail}>`,
    `To: ${to}`,
    `Subject: ${encodeHeader(subject)}`,
    `Message-ID: <${messageId}@swapnopay.top>`,
    'MIME-Version: 1.0',
    `Content-Type: multipart/alternative; boundary="${boundary}"`,
    '', `--${boundary}`, 'Content-Type: text/plain; charset=UTF-8',
    'Content-Transfer-Encoding: base64', '', Buffer.from(text, 'utf8').toString('base64'),
    `--${boundary}`, 'Content-Type: text/html; charset=UTF-8',
    'Content-Transfer-Encoding: base64', '', Buffer.from(html, 'utf8').toString('base64'),
    `--${boundary}--`, ''
  ]
  return Buffer.from(lines.join('\r\n'), 'utf8').toString('base64url')
}

export async function sendReceipt(config, event, recipientRole) {
  const to = normalizedEmail(recipientRole === 'merchant' ? event.merchant.receipt_email : event.order.customer_email)
  if (!to) throw new Error(`Invalid ${recipientRole} receipt address`)
  const subject = recipientRole === 'merchant'
    ? `[PAID] ${safeHeader(event.order.transaction_id, 120)} — BDT ${Number(event.order.amount).toFixed(2)}`
    : `Payment receipt from ${safeHeader(event.merchant.business_name, 120)}`
  const stableId = `${event.project_ref}.${event.event_id}.${recipientRole}`
  const raw = rawMessage(config, to, subject, messageHtml(event, recipientRole, config.publicOrigin), stableId)
  const token = await accessToken(config)
  const result = await fetch('https://gmail.googleapis.com/gmail/v1/users/me/messages/send', {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify({ raw }),
    signal: AbortSignal.timeout(15_000)
  })
  const payload = await result.json().catch(() => ({}))
  if (!result.ok || !payload.id) throw new Error(`Gmail send failed (${result.status})`)
  return String(payload.id)
}
