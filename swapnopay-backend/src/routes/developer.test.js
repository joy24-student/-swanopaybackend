import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { developerRouter } from './developer.js'

function createTestApp() {
  const app = express()
  app.use(express.json())
  const mockIo = {
    to: () => ({ emit: () => {} }),
    emit: () => {},
    engine: { clientsCount: 3 }
  }
  const heartbeatMap = new Map()
  heartbeatMap.set('test-merchant-1', { ts: Date.now(), socketId: 's1' })
  app.use('/v1/developer', developerRouter(mockIo, heartbeatMap))
  return app
}

async function request(app, method, path, body = null, headers = {}) {
  const server = app.listen(0)
  const port = server.address().port
  const url = `http://127.0.0.1:${port}${path}`

  try {
    const options = {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    }
    if (body) {
      options.body = JSON.stringify(body)
    }
    const res = await fetch(url, options)
    const json = await res.json().catch(() => null)
    return { status: res.status, headers: res.headers, body: json }
  } finally {
    server.close()
  }
}

test('SwapnoPay Developer Console & Real Enterprise Router Suite', async (t) => {
  const app = createTestApp()

  await t.test('1. POST /v1/developer/auth/sandbox-guest provisions instant sandbox merchant & keys', async () => {
    const res = await request(app, 'POST', '/v1/developer/auth/sandbox-guest', {
      name: 'Acme Test Merchant'
    })

    assert.equal(res.status, 200)
    assert.equal(res.body.ok, true)
    assert.equal(res.body.is_guest, true)
    assert.ok(res.body.merchant.id)
    assert.equal(res.body.merchant.business_name, 'Acme Test Merchant')
    assert.ok(res.body.keys.secret_key.startsWith('sk_sandbox_'))
    assert.ok(res.body.keys.webhook_secret.startsWith('whsec_'))
    assert.equal(res.body.config.bkash_enabled, true)
  })

  await t.test('2. POST /v1/developer/auth/login authenticates via API Key', async () => {
    // First provision a guest
    const guestRes = await request(app, 'POST', '/v1/developer/auth/sandbox-guest', {
      name: 'Key Auth Store'
    })
    const key = guestRes.body.keys.secret_key

    const res = await request(app, 'POST', '/v1/developer/auth/login', {
      api_key: key
    })

    assert.equal(res.status, 200)
    assert.equal(res.body.ok, true)
    assert.equal(res.body.auth_type, 'api_key')
    assert.equal(res.body.merchant.business_name, 'Key Auth Store')
    assert.equal(res.body.token, key)
  })

  await t.test('3. POST /v1/developer/auth/login validates required email/password', async () => {
    const res = await request(app, 'POST', '/v1/developer/auth/login', {})
    assert.equal(res.status, 400)
    assert.equal(res.body.ok, false)
    assert.match(res.body.error, /Email and password are required/i)
  })

  await t.test('4. GET /v1/developer/profile returns merchant credentials & settings', async () => {
    const guestRes = await request(app, 'POST', '/v1/developer/auth/sandbox-guest', {
      name: 'Profile Merchant'
    })
    const merchantId = guestRes.body.merchant.id

    const res = await request(app, 'GET', `/v1/developer/profile?merchant_id=${merchantId}`)
    assert.equal(res.status, 200)
    assert.equal(res.body.ok, true)
    assert.equal(res.body.merchant_id, merchantId)
    assert.ok(res.body.keys)
    assert.ok(res.body.config)
  })

  await t.test('5. POST /v1/developer/keys/regenerate revokes and issues fresh active keys', async () => {
    const res = await request(app, 'POST', '/v1/developer/keys/regenerate', {
      merchant_id: 'test-merchant-regen-123',
      merchant_name: 'Regen Test Store'
    })

    assert.equal(res.status, 201)
    assert.equal(res.body.ok, true)
    assert.ok(res.body.api_key.startsWith('sp_live_') || res.body.api_key.startsWith('sk_live_'))
    assert.ok(res.body.sandbox_key.startsWith('sk_sandbox_'))
    assert.ok(res.body.key_preview.includes('****'))
  })

  await t.test('6. POST /v1/developer/webhook-config updates merchant callback destination', async () => {
    const res = await request(app, 'POST', '/v1/developer/webhook-config', {
      merchant_id: 'test-merchant-wh-123',
      webhook_url: 'https://mystore.com/api/v1/swapnopay-callback'
    })

    assert.equal(res.status, 200)
    assert.equal(res.body.ok, true)
    assert.equal(res.body.webhook_url, 'https://mystore.com/api/v1/swapnopay-callback')
  })

  await t.test('7. POST /v1/developer/sandbox/order creates real order registered in ledger', async () => {
    const res = await request(app, 'POST', '/v1/developer/sandbox/order', {
      merchant_id: 'merchant-test-uuid',
      amount: 2450.00,
      payment_method: 'bKash',
      cus_phone: '01712345678',
      callback_url: 'https://mystore.com/api/callback'
    })

    assert.equal(res.status, 201)
    assert.equal(res.body.ok, true)
    assert.ok(res.body.order_id)
    assert.ok(res.body.tran_id.startsWith('SWP-SBX-'))
    assert.equal(res.body.amount, 2450.00)
    assert.equal(res.body.payment_method, 'bKash')
    assert.equal(res.body.status, 'PENDING')
    assert.ok(res.body.checkout_url.includes(res.body.order_id))
  })

  await t.test('8. POST /v1/developer/sandbox/simulate-mfs generates carrier SMS & HMAC webhook', async () => {
    // 1. Create order
    const orderRes = await request(app, 'POST', '/v1/developer/sandbox/order', {
      merchant_id: 'merchant-test-uuid',
      amount: 1500.00,
      payment_method: 'bKash',
      cus_phone: '01712345678'
    })
    const orderId = orderRes.body.order_id

    // 2. Simulate bKash SMS
    const simRes = await request(app, 'POST', '/v1/developer/sandbox/simulate-mfs', {
      order_id: orderId,
      merchant_id: 'merchant-test-uuid',
      amount: 1500.00,
      payment_method: 'bKash',
      customer_phone: '01712345678'
    })

    assert.equal(simRes.status, 200)
    assert.equal(simRes.body.ok, true)
    assert.equal(simRes.body.status, 'PAID')
    assert.ok(simRes.body.trx_id.startsWith('BL'))
    assert.match(simRes.body.sms_body, /You have received Tk 1500\.00 from 01712345678/i)
    assert.match(simRes.body.sms_body, new RegExp(simRes.body.trx_id))
    assert.ok(simRes.body.webhook_delivery)
    assert.ok(simRes.body.webhook_delivery.signature.includes('v1='))
  })

  await t.test('9. POST /v1/developer/sandbox/simulate-mfs handles TIMEOUT and APPEAL scenarios', async () => {
    // Timeout
    const timeoutRes = await request(app, 'POST', '/v1/developer/sandbox/simulate-mfs', {
      scenario: 'TIMEOUT_FAIL',
      amount: 1200.00
    })
    assert.equal(timeoutRes.status, 200)
    assert.equal(timeoutRes.body.status, 'EXPIRED')

    // Dispute Appeal
    const appealRes = await request(app, 'POST', '/v1/developer/sandbox/simulate-mfs', {
      scenario: 'CUSTOMER_APPEAL',
      amount: 1200.00
    })
    assert.equal(appealRes.status, 200)
    assert.equal(appealRes.body.status, 'APPEALED')
    assert.ok(appealRes.body.appeal_id)
    assert.ok(appealRes.body.trx_id.startsWith('TXN'))
  })

  await t.test('10. POST /v1/developer/sandbox/dispatch-webhook generates signed HMAC test webhook', async () => {
    const res = await request(app, 'POST', '/v1/developer/sandbox/dispatch-webhook', {
      target_url: 'https://example.com/webhook',
      event_type: 'payment.success',
      secret_key: 'sk_sandbox_test_secret_key'
    })

    // example.com might return 200, 404, or abort/timeout in test environment, but the response structure must be valid
    assert.equal(res.status, 200)
    assert.ok(res.body.signature.startsWith('t='))
    assert.ok(res.body.signature.includes(',v1='))
    assert.equal(res.body.headers['x-swapnopay-event'], 'payment.success')
    assert.equal(res.body.payload.event, 'payment.success')
    assert.ok(res.body.latency_ms >= 0)
  })

  await t.test('11. GET /v1/developer/telemetry returns platform metrics & live counts', async () => {
    const res = await request(app, 'GET', '/v1/developer/telemetry')
    assert.equal(res.status, 200)
    assert.equal(res.body.ok, true)
    assert.ok(res.body.api_volume_24h >= 142)
    assert.ok(res.body.success_rate.includes('%'))
    assert.equal(res.body.avg_latency_ms, 38)
    assert.ok(res.body.devices_online >= 1)
    assert.equal(res.body.system_status, 'OPERATIONAL')
  })
})
