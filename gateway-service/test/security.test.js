import test from 'node:test'
import assert from 'node:assert/strict'
import { apiKeyDigest, escapeHtml, isProjectRef, isUuid, normalizedEmail } from '../src/security.js'

test('API key digests are deterministic and pepper scoped', () => {
  assert.equal(apiKeyDigest('key', 'a'.repeat(32)), apiKeyDigest('key', 'a'.repeat(32)))
  assert.notEqual(apiKeyDigest('key', 'a'.repeat(32)), apiKeyDigest('key', 'b'.repeat(32)))
})

test('identifiers and email addresses are constrained', () => {
  assert.equal(isProjectRef('abcdxyz123'), true)
  assert.equal(isProjectRef('../bad'), false)
  assert.equal(isUuid('00000000-0000-4000-8000-000000000001'), true)
  assert.equal(normalizedEmail(' User@Example.com '), 'user@example.com')
  assert.equal(normalizedEmail('bad\r\n@example.com'), '')
})

test('HTML escaping prevents receipt markup injection', () => {
  assert.equal(escapeHtml('<img onerror="x">'), '&lt;img onerror=&quot;x&quot;&gt;')
})
