import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { pinRouter } from './pin.js'

function createTestApp() {
  const app = express()
  app.use(express.json())

  // Mock platform user middleware for testing
  app.use((req, res, next) => {
    req.platformUser = {
      id: '00000000-0000-0000-0000-000000000099',
      email: 'merchant_test@swapnopay.top'
    }
    next()
  })

  app.use('/v1/pin', pinRouter)
  return app
}

test('PIN Management Routes', async (t) => {
  const app = createTestApp()
  const server = app.listen(0)
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}/v1/pin`

  t.after(() => {
    server.close()
  })

  await t.test('rejects pin_hash if not provided', async () => {
    const res = await fetch(`${baseUrl}/set`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /pin_hash is required/i)
  })

  await t.test('rejects invalid non-SHA256 hex pin_hash', async () => {
    const res = await fetch(`${baseUrl}/set`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin_hash: '1234' }) // raw 4-digit PIN is rejected
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /SHA-256 hex string/i)
  })

  await t.test('accepts valid 64-char SHA-256 hash', async () => {
    const validHash = '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4'
    const res = await fetch(`${baseUrl}/set`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin_hash: validHash })
    })
    assert.notStrictEqual(res.status, 400)
  })
})
