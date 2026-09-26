import test from 'node:test'
import assert from 'node:assert/strict'
import { isSafeOutboundWebhookUrl } from './urlValidator.js'

test('SSRF URL Validator Suite', async (t) => {
  await t.test('1. Allows public HTTPS and HTTP URLs', () => {
    assert.equal(isSafeOutboundWebhookUrl('https://example.com/webhook').safe, true)
    assert.equal(isSafeOutboundWebhookUrl('http://my-api.com/callback').safe, true)
    assert.equal(isSafeOutboundWebhookUrl('https://hooks.slack.com/services/T00/B00/X00').safe, true)
  })

  await t.test('2. Blocks loopback addresses and localhost', () => {
    assert.equal(isSafeOutboundWebhookUrl('http://localhost/api').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://127.0.0.1:8080/secret').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://127.1.2.3/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://[::1]:80/admin').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://app.localhost/').safe, false)
  })

  await t.test('3. Blocks cloud metadata service (169.254.169.254)', () => {
    assert.equal(isSafeOutboundWebhookUrl('http://169.254.169.254/latest/meta-data/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://169.254.1.1/').safe, false)
  })

  await t.test('4. Blocks RFC1918 private subnets', () => {
    assert.equal(isSafeOutboundWebhookUrl('http://10.0.0.1/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://172.16.0.1/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://172.31.255.255/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('http://192.168.1.1/').safe, false)
  })

  await t.test('5. Blocks non-HTTP protocols (file, gopher, ftp)', () => {
    assert.equal(isSafeOutboundWebhookUrl('file:///etc/passwd').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('gopher://127.0.0.1:6379/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('ftp://example.com').safe, false)
  })

  await t.test('6. Blocks user credentials in URLs', () => {
    assert.equal(isSafeOutboundWebhookUrl('https://admin:secret@example.com/').safe, false)
  })

  await t.test('7. Blocks internal domain names (.local, .internal, .lan)', () => {
    assert.equal(isSafeOutboundWebhookUrl('https://service.local/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('https://db.internal/').safe, false)
    assert.equal(isSafeOutboundWebhookUrl('https://nas.lan/').safe, false)
  })
})
