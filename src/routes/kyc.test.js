import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { kycRouter } from './kyc.js'

function createTestApp() {
  const app = express()
  app.use(express.json({ limit: '10mb' }))

  // Mock platform user middleware
  app.use((req, _res, next) => {
    req.platformUser = {
      id: '00000000-0000-0000-0000-000000000099',
      email: 'merchant_test@swapnopay.top'
    }
    req.platformAccount = {
      merchantId: '00000000-0000-0000-0000-000000000099'
    }
    next()
  })
  app.use('/v1/kyc', kycRouter)
  return app
}

test('KYC & Bangladeshi NID Extraction Routes', async (t) => {
  const app = createTestApp()
  const server = app.listen(0)
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}/v1/kyc`

  t.after(() => {
    server.close()
  })

  await t.test('1. POST /v1/kyc/extract-nid requires front or back image', async () => {
    const res = await fetch(`${baseUrl}/extract-nid`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({})
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /required/i)
  })

  await t.test('2. POST /v1/kyc/extract-nid handles base64 image and returns structured response', async () => {
    // 1x1 white pixel jpeg base64
    const dummyJpeg = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
    const res = await fetch(`${baseUrl}/extract-nid`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ front_base64: dummyJpeg })
    })

    assert.strictEqual(res.status, 200)
    const body = await res.json()
    assert.strictEqual(body.ok, true)
    assert.ok(body.extracted_fields)
    assert.ok('nid_number' in body.extracted_fields)
    assert.ok('name_bangla' in body.extracted_fields)
    assert.ok('name_english' in body.extracted_fields)
    assert.ok('validation' in body)
  })

  await t.test('3. POST /v1/kyc/submit rejects invalid non-10/13/17 digit NID numbers', async () => {
    const res = await fetch(`${baseUrl}/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nid_number: '12345', // only 5 digits
        front_base64: 'fake',
        back_base64: 'fake',
        selfie_base64: 'fake'
      })
    })
    assert.strictEqual(res.status, 400)
    const body = await res.json()
    assert.match(body.error, /জাতীয় পরিচয়পত্র/)
  })
})
