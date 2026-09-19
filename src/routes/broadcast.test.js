import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { adminRouter } from './admin.js'

function createTestApp() {
  const app = express()
  app.use(express.json())
  app.use('/v1/admin', adminRouter)
  return app
}

test('Admin Notifications Broadcast Routes Suite', async (t) => {
  const originalSecret = process.env.ADMIN_SECRET
  process.env.ADMIN_SECRET = 'test-secret-123456789012345678901234'

  const app = createTestApp()
  const server = app.listen(0)
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}/v1/admin`

  t.after(() => {
    server.close()
    if (originalSecret !== undefined) {
      process.env.ADMIN_SECRET = originalSecret
    } else {
      delete process.env.ADMIN_SECRET
    }
  })

  await t.test('1. Rejects request without valid X-Admin-Secret header', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: 'Test', message: 'Hello' }),
    })
    assert.strictEqual(res.status, 401)
  })

  await t.test('2. Rejects broadcast with missing title', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
      body: JSON.stringify({ message: 'Hello merchants' }),
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /title is required/i)
  })

  await t.test('3. Rejects broadcast with empty title or title > 255 chars', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
      body: JSON.stringify({ title: 'A'.repeat(256), message: 'Hello merchants' }),
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /must not exceed 255 characters/i)
  })

  await t.test('4. Rejects broadcast with missing message body', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
      body: JSON.stringify({ title: 'Important Update' }),
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /message body is required/i)
  })

  await t.test('5. Rejects broadcast with invalid notification type', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
      body: JSON.stringify({
        title: 'Important Update',
        message: 'Hello merchants',
        type: 'INVALID_TYPE',
      }),
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /invalid notification type/i)
  })

  await t.test('6. Rejects broadcast with invalid severity', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
      body: JSON.stringify({
        title: 'Important Update',
        message: 'Hello merchants',
        type: 'ALERT',
        severity: 'SUPER_CRITICAL',
      }),
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /invalid severity/i)
  })

  await t.test('7. GET /notifications/broadcasts returns broadcast list or empty array', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcasts`, {
      headers: {
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
    })
    assert.strictEqual(res.status, 200)
    const body = await res.json()
    assert.strictEqual(body.ok, true)
    assert.ok(Array.isArray(body.broadcasts))
  })

  await t.test('8. POST /notifications/broadcast processes valid payload gracefully', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcast`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
      body: JSON.stringify({
        title: 'Ramadan Gateway Schedule',
        message: 'Banking settlement hours are adjusted for Ramadan.',
        type: 'ANNOUNCEMENT',
        severity: 'INFO',
        target: 'ALL',
        updateBanner: false,
      }),
    })
    assert.strictEqual(res.status, 200)
    const body = await res.json()
    assert.strictEqual(body.ok, true)
    assert.ok(body.batch_id)
    assert.strictEqual(body.title, 'Ramadan Gateway Schedule')
  })

  await t.test('9. DELETE /notifications/broadcasts/:batchId deletes or returns ok', async () => {
    const res = await fetch(`${baseUrl}/notifications/broadcasts/test-batch-uuid`, {
      method: 'DELETE',
      headers: {
        'X-Admin-Secret': 'test-secret-123456789012345678901234',
      },
    })
    assert.strictEqual(res.status, 200)
    const body = await res.json()
    assert.strictEqual(body.ok, true)
    assert.strictEqual(body.batch_id, 'test-batch-uuid')
  })
})
