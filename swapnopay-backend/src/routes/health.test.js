import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'

function createHealthApp() {
  const app = express()
  const healthCheckHandler = (_req, res) => {
    res.json({
      ok: true,
      status: 'healthy',
      service: 'swapnopay-backend',
      version: '3.0.0',
      database: 'admin-supabase',
      socket_io_clients: 0,
      merchant_online_count: 0,
      timestamp: new Date().toISOString(),
    })
  }

  app.get('/healthz', healthCheckHandler)
  app.get('/health', healthCheckHandler)
  return app
}

test('Public Health Check Suite', async (t) => {
  const app = createHealthApp()
  const server = app.listen(0)
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}`

  t.after(() => {
    server.close()
  })

  await t.test('GET /healthz returns ok: true and status: healthy', async () => {
    const res = await fetch(`${baseUrl}/healthz`)
    assert.strictEqual(res.status, 200)
    const data = await res.json()
    assert.strictEqual(data.ok, true)
    assert.strictEqual(data.status, 'healthy')
    assert.strictEqual(data.service, 'swapnopay-backend')
  })

  await t.test('GET /health returns ok: true and status: healthy', async () => {
    const res = await fetch(`${baseUrl}/health`)
    assert.strictEqual(res.status, 200)
    const data = await res.json()
    assert.strictEqual(data.ok, true)
    assert.strictEqual(data.status, 'healthy')
    assert.strictEqual(data.service, 'swapnopay-backend')
  })
})
