import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { createServer } from 'node:http'
import { developerRouter } from './developer.js'

function createTestServer() {
  const app = express()
  app.use(express.json())
  app.use('/v1/developer', developerRouter(null, new Map()))
  const server = createServer(app)
  return { app, server }
}

test('Enterprise Developer Console & Real Sandbox Suite', async (t) => {
  const { server } = createTestServer()
  await new Promise((resolve) => server.listen(0, resolve))
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}/v1/developer`

  t.after(() => {
    server.close()
  })

  let guestMerchantId = ''
  let guestApiKey = ''
  let createdOrderId = ''

  await t.test('1. POST /v1/developer/auth/sandbox-guest creates real guest sandbox session', async () => {
    const res = await fetch(`${baseUrl}/auth/sandbox-guest`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Acme Test Corp' })
    })

    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.is_guest, true)
    assert.ok(data.merchant?.id)
    assert.ok(data.keys?.secret_key)
    assert.ok(data.keys.secret_key.startsWith('sk_sandbox_'))
    assert.ok(data.config?.receiving_numbers?.bKash)

    guestMerchantId = data.merchant.id
    guestApiKey = data.keys.secret_key
  })

  await t.test('2. POST /v1/developer/auth/login allows login via API Key', async () => {
    const res = await fetch(`${baseUrl}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ api_key: guestApiKey })
    })

    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.auth_type, 'api_key')
    assert.equal(data.merchant.id, guestMerchantId)
    assert.equal(data.keys.secret_key, guestApiKey)
  })

  await t.test('3. GET /v1/developer/profile returns credentials and settings', async () => {
    const res = await fetch(`${baseUrl}/profile?merchant_id=${guestMerchantId}`)
    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.merchant_id, guestMerchantId)
    assert.ok(data.keys?.public_key)
  })

  await t.test('4. POST /v1/developer/keys/regenerate generates new valid API key', async () => {
    const res = await fetch(`${baseUrl}/keys/regenerate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ merchant_id: guestMerchantId, merchant_name: 'Acme Test Corp' })
    })

    assert.equal(res.status, 201)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.ok(data.api_key)
    assert.ok(data.key_id)
    assert.equal(data.merchant_id, guestMerchantId)
  })

  await t.test('5. POST /v1/developer/webhook-config updates webhook callback URL', async () => {
    const res = await fetch(`${baseUrl}/webhook-config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: guestMerchantId,
        webhook_url: 'https://httpbin.org/post',
        secret: 'whsec_custom_secret_key_123'
      })
    })

    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.webhook_url, 'https://httpbin.org/post')
    assert.equal(data.webhook_secret, 'whsec_custom_secret_key_123')
  })

  await t.test('6. POST /v1/developer/sandbox/order creates real sandbox order', async () => {
    const res = await fetch(`${baseUrl}/sandbox/order`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: guestMerchantId,
        amount: 2500,
        payment_method: 'bKash',
        cus_phone: '01799887766',
        callback_url: 'https://httpbin.org/post'
      })
    })

    assert.equal(res.status, 201)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.status, 'PENDING')
    assert.equal(data.amount, 2500)
    assert.ok(data.order_id)
    assert.ok(data.tran_id)
    assert.ok(data.checkout_url)

    createdOrderId = data.order_id
  })

  await t.test('7. POST /v1/developer/sandbox/simulate-mfs generates carrier SMS, updates to PAID, signs HMAC', async () => {
    const res = await fetch(`${baseUrl}/sandbox/simulate-mfs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        order_id: createdOrderId,
        merchant_id: guestMerchantId,
        amount: 2500,
        payment_method: 'bKash',
        customer_phone: '01799887766',
        scenario: 'MATCH_PAID'
      })
    })

    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.status, 'PAID')
    assert.ok(data.trx_id)
    assert.ok(data.sms_body.includes('Tk 2500.00'))
    assert.ok(data.sms_body.includes(data.trx_id))
    assert.ok(data.webhook_delivery?.signature)
    assert.ok(data.webhook_delivery.signature.includes('v1='))
  })

  await t.test('8. POST /v1/developer/sandbox/simulate-mfs handles TIMEOUT_FAIL scenario', async () => {
    const res = await fetch(`${baseUrl}/sandbox/simulate-mfs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        order_id: createdOrderId,
        merchant_id: guestMerchantId,
        scenario: 'TIMEOUT_FAIL'
      })
    })

    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.equal(data.status, 'EXPIRED')
  })

  await t.test('9. GET /v1/developer/telemetry returns system metrics', async () => {
    const res = await fetch(`${baseUrl}/telemetry`)
    assert.equal(res.status, 200)
    const data = await res.json()
    assert.equal(data.ok, true)
    assert.ok(data.api_volume_24h >= 0)
    assert.ok(data.success_rate)
    assert.equal(data.system_status, 'OPERATIONAL')
  })
})
