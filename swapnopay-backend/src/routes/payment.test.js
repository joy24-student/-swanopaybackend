import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { paymentRouter } from './payment.js'
import { shopConfiguration } from '../services/shopService.js'

function createTestApp() {
  const app = express()
  app.use(express.json())
  app.use((req, _res, next) => { req.isAdmin = true; next() })
  app.use('/v1/payment', paymentRouter(null))
  return app
}

test('SwapnoPay Payment Gateway & Production Security Suite', async (t) => {
  const app = createTestApp()
  const server = app.listen(0)
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}/v1/payment`

  t.after(() => {
    server.close()
  })

  await t.test('1. POST /v1/payment/create-order rejects missing amount or merchant_id', async () => {
    const resNoAmount = await fetch(`${baseUrl}/create-order`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ merchant_id: 'merchant-test-1', amount: 0 })
    })
    assert.equal(resNoAmount.status, 400)

    const resNoMerchant = await fetch(`${baseUrl}/create-order`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount: 500 })
    })
    assert.equal(resNoMerchant.status, 400)
  })

  await t.test('2. POST /v1/payment/create-order fails closed for an unregistered merchant', async () => {
    const payload = {
      merchant_id: 'merchant-test-123',
      tran_id: 'SWP-TEST-1001',
      order_number: 'ORD-TEST-1001',
      amount: 1250.50,
      cus_name: 'Tanvir Hossain',
      cus_phone: '01711223344',
      cus_email: 'tanvir@example.com',
      payment_method: 'bKash',
      items: [
        { product_name: 'Test Shirt', quantity: 2, unit_price: 625.25 }
      ]
    }

    const res = await fetch(`${baseUrl}/create-order`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    assert.equal(res.status, 404)
    const data = await res.json()
    assert.equal(data.ok, false)
    assert.match(data.error, /not registered/i)
  })

  await t.test('3. POST /v1/payment/create-order rejects private callback URLs before creating an order', async () => {
    const res = await fetch(`${baseUrl}/create-order`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: 'merchant-test-123', amount: 50, payment_method: 'bKash',
        callback_url: 'https://127.0.0.1/internal/callback'
      })
    })
    assert.equal(res.status, 400)
    assert.match((await res.json()).error, /public HTTPS URL/)
  })

  await t.test('4. GET /v1/payment/check-status rejects missing order_id and tran_id', async () => {
    const res = await fetch(`${baseUrl}/check-status`)
    assert.equal(res.status, 400)
    const data = await res.json()
    assert.equal(data.ok, false)
  })

  await t.test('5. GET /v1/payment/sms-webhook-health requires merchant_id', async () => {
    const resNoId = await fetch(`${baseUrl}/sms-webhook-health`)
    assert.equal(resNoId.status, 400)

    const resValid = await fetch(`${baseUrl}/sms-webhook-health?merchant_id=merchant-test-123`)
    assert.equal(resValid.status, 200)
    const data = await resValid.json()
    assert.equal(data.ok, true)
    assert.ok(['UNCONFIGURED', 'HEALTHY', 'NO_SMS_YET', 'BACKLOG_DETECTED'].includes(data.status))
  })

  await t.test('6. Production Environment Security: blocks missing SHOP_CONFIG_KEY and embedded DB', () => {
    // A. Missing secret in production throws
    assert.throws(
      () => {
        shopConfiguration({
          NODE_ENV: 'production',
          SHOP_CONFIG_KEY: '',
          ADMIN_SECRET: '',
        })
      },
      (err) => {
        return err.status === 500 && err.code === 'SHOP_NOT_CONFIGURED'
      }
    )

    // B. Missing database in production without ALLOW_EMBEDDED_SHOP_DB throws
    assert.throws(
      () => {
        shopConfiguration({
          NODE_ENV: 'production',
          SHOP_CONFIG_KEY: 'a'.repeat(64),
          SHOP_DATABASE_URL: '',
          DATABASE_URL: '',
          ALLOW_EMBEDDED_SHOP_DB: 'false',
        })
      },
      (err) => {
        return err.status === 503 && err.code === 'SHOP_NOT_CONFIGURED'
      }
    )
  })
})
