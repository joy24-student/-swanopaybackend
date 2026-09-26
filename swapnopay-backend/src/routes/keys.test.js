import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { keysRouter } from './keys.js'

process.env.API_KEY_PEPPER = 'k'.repeat(64)

test('Dynamic API Key Management & Validation Suite', async (t) => {
  const app = express()
  app.use(express.json())
  app.use((req, res, next) => {
    req.isAdmin = true
    req.merchantUser = { id: 'test-merchant-uuid-1', email: 'merchant@test.com' }
    next()
  })
  app.use('/v1/admin/keys', keysRouter)

  const server = app.listen(0, '127.0.0.1')
  await new Promise(resolve => server.once('listening', resolve))
  const base = `http://127.0.0.1:${server.address().port}/v1/admin/keys`

  try {
    let generatedKey = ''

    await t.test('1. GET /active generates and returns dynamic API key for merchant', async () => {
      const res = await fetch(`${base}/active?merchant_id=test-merchant-uuid-1&email=merchant@test.com`)
      assert.equal(res.status, 200)
      const data = await res.json()
      assert.equal(data.ok, true)
      assert.ok(data.api_key.startsWith('sp_live_'), 'Key must start with sp_live_')
      assert.ok(data.key_preview.includes('****'))
      assert.equal(data.merchant_id, 'test-merchant-uuid-1')
      generatedKey = data.api_key
    })

    await t.test('2. POST /validate successfully verifies dynamic API key', async () => {
      const res = await fetch(`${base}/validate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ api_key: generatedKey }),
      })
      assert.equal(res.status, 200)
      const data = await res.json()
      assert.equal(data.ok, true)
      assert.equal(data.valid, true)
      assert.equal(data.merchant_id, 'test-merchant-uuid-1')
    })

    await t.test('3. POST /regenerate issues a fresh active API key', async () => {
      const res = await fetch(`${base}/regenerate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ merchant_id: 'test-merchant-uuid-1' }),
      })
      assert.equal(res.status, 201)
      const data = await res.json()
      assert.equal(data.ok, true)
      assert.ok(data.api_key.startsWith('sp_live_'))
      assert.notEqual(data.api_key, generatedKey, 'Regenerated key must be different from previous key')
    })
  } finally {
    await new Promise(resolve => server.close(resolve))
  }
})
