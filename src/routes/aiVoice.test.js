// Unit and Integration Tests for AI Voice Calling (100% Twilio-Free)
import test from 'node:test'
import assert from 'node:assert/strict'
import express from 'express'
import { aiVoiceRouter } from './aiVoice.js'

function createTestApp() {
  const app = express()
  app.use(express.json({ limit: '10mb' }))
  app.use(express.urlencoded({ extended: true }))
  app.use('/v1/voice', aiVoiceRouter(null))
  return app
}

test('Twilio-Free AI Voice Calling & Instant Record to AI', async (t) => {
  const app = createTestApp()
  const server = app.listen(0)
  const port = server.address().port
  const baseUrl = `http://127.0.0.1:${port}/v1/voice`

  t.after(() => {
    server.close()
  })

  await t.test('1. POST /record-to-ai - Process instant speech recording sent to AI', async () => {
    const res = await fetch(`${baseUrl}/record-to-ai`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: 'test_m1',
        speech_text: 'আপনাদের দোকান কখন খোলা থাকে?',
        customer_name: 'আরিফ আহমেদ'
      })
    })

    assert.strictEqual(res.status, 200)
    const data = await res.json()
    assert.strictEqual(data.ok, true)
    assert.ok(data.call_id)
    assert.strictEqual(data.transcription, 'আপনাদের দোকান কখন খোলা থাকে?')
    assert.ok(data.ai_reply.length > 5)
    assert.strictEqual(data.agent_name, 'তানিয়া (Tania)')
    assert.strictEqual(data.call_record.direction, 'inbound')
  })

  await t.test('2. POST /record-to-ai with base64 audio payload', async () => {
    // Simulated base64 audio snippet
    const dummyAudioBase64 = Buffer.from('FAKE_AUDIO_SAMPLE').toString('base64')
    const res = await fetch(`${baseUrl}/record-to-ai`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: 'test_m1',
        audio_base64: dummyAudioBase64,
        mime_type: 'audio/mp4'
      })
    })

    assert.strictEqual(res.status, 200)
    const data = await res.json()
    assert.strictEqual(data.ok, true)
    assert.ok(data.ai_reply)
    assert.ok(data.transcription)
  })

  await t.test('3. POST /outbound/due-reminder - Initiate due collection without Twilio', async () => {
    const res = await fetch(`${baseUrl}/outbound/due-reminder`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        merchant_id: 'test_m1',
        customer_phone: '+8801700112233',
        customer_name: 'আব্দুল করিম',
        due_amount: '২৫০০'
      })
    })

    assert.strictEqual(res.status, 200)
    const data = await res.json()
    assert.strictEqual(data.ok, true)
    assert.ok(data.call_id)
    assert.ok(data.script_spoken.includes('২৫০০ টাকা'))
    assert.ok(data.voice_call_url.includes('swapnopay.top/voice-call.html'))
  })

  await t.test('4. GET /logs - Verify call logs and transcripts', async () => {
    const res = await fetch(`${baseUrl}/logs?merchant_id=test_m1`)
    assert.strictEqual(res.status, 200)
    const data = await res.json()
    assert.strictEqual(data.ok, true)
    assert.ok(data.logs.length >= 3)
  })
})
