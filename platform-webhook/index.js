// SwapnoPay Platform Webhook Receiver (Supabase Service Role Client)
// Requires env: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY

const express = require('express')
const bodyParser = require('body-parser')
const crypto = require('crypto')
const { createClient } = require('@supabase/supabase-js')

const supabaseUrl = process.env.SUPABASE_URL
const supabaseServiceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY

if (!supabaseUrl || !supabaseServiceRoleKey) {
  throw new Error('SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required')
}

const supabase = createClient(supabaseUrl, supabaseServiceRoleKey)

const app = express()
app.disable('x-powered-by')
app.use(bodyParser.json({ limit: '64kb', strict: true }))

// API Key & Shared Secret Verification
const SHARED_SECRET = process.env.PLATFORM_WEBHOOK_SECRET || ''
if (SHARED_SECRET.length < 32) throw new Error('PLATFORM_WEBHOOK_SECRET must be at least 32 characters')

function secretMatches(value) {
  const provided = Buffer.from(String(value || ''), 'utf8')
  const expected = Buffer.from(SHARED_SECRET, 'utf8')
  return provided.length === expected.length && crypto.timingSafeEqual(provided, expected)
}

app.post('/ingest', async (req, res) => {
  try {
    const authHeader = req.headers['authorization'] || ''
    const bearerToken = authHeader.toLowerCase().startsWith('bearer ') ? authHeader.substring(7).trim() : ''
    const h = req.headers['x-platform-secret'] || bearerToken
    if (!secretMatches(h)) return res.status(401).json({ error: 'Unauthorized' })

    const payload = req.body || {}
    const merchantId = String(payload.merchantId || payload.merchant_id || payload.merchant || '')
    const formId = String(payload.formId || payload.form_id || payload.form || '')
    const submission = payload.submission || payload.answers || payload.data
    const suppliedRequestId = String(payload.requestId || payload.request_id || '')
    const requestId = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(suppliedRequestId)
      ? suppliedRequestId : crypto.randomUUID()

    if (!/^[0-9a-f-]{36}$/i.test(merchantId) || !/^[0-9a-f-]{36}$/i.test(formId) || !submission || typeof submission !== 'object') {
      return res.status(400).json({ error: 'Valid merchantId, formId, and submission are required' })
    }

    const { data: form, error: formError } = await supabase
      .from('payment_forms').select('id').eq('id', formId).eq('merchant_id', merchantId).maybeSingle()
    if (formError || !form) return res.status(404).json({ error: 'Form not found for merchant' })

    let { data, error } = await supabase
      .from('form_submissions')
      .upsert({
        form_id: formId || null,
        request_id: requestId,
        answers: submission,
        payment_status: 'NOT_REQUIRED',
        amount_bdt: 0,
        raw_payload: { source: 'platform-webhook', merchant_id: merchantId },
        created_at: new Date().toISOString()
      }, { onConflict: 'form_id,request_id', ignoreDuplicates: true })
      .select('id')
      .maybeSingle()

    if (!error && !data) {
      const existing = await supabase.from('form_submissions').select('id')
        .eq('form_id', formId).eq('request_id', requestId).maybeSingle()
      data = existing.data
      error = existing.error
    }

    if (error) {
      console.error('Supabase ingest error:', error)
      return res.status(500).json({ error: 'Failed to write submission to Supabase' })
    }

    return res.json({ ok: true, id: data ? data.id : 'recorded' })
  } catch (err) {
    console.error('Webhook ingest exception:', err)
    return res.status(500).json({ error: 'Internal server error' })
  }
})

const port = process.env.PORT || 8080
app.listen(port, () => console.log('SwapnoPay Supabase platform webhook listening on port', port))
