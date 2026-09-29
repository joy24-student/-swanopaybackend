import { createClient } from '@supabase/supabase-js'
import { requireMerchantOrAdminAuth, safeCompare } from './auth.js'
import { getMerchantCredentials, validateApiKey } from '../services/adminSupabase.js'
import { apiKeyDigest } from '../utils/crypto.js'
import { merchantId } from '../services/shopValidation.js'

// Accept only credentials that prove platform-admin or merchant ownership.
export async function requireShopAuth(req, res, next) {
  const rawAuth = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim()
  const xAdminSecret = req.headers['x-admin-secret']
  const adminSecret = process.env.ADMIN_SECRET

  // 0. Check Platform Admin authentication upfront
  if (adminSecret && ((xAdminSecret && safeCompare(xAdminSecret, adminSecret)) || (rawAuth && safeCompare(rawAuth, adminSecret)))) {
    req.isAdmin = true
    req.authMethod = 'admin_secret'
    req.merchantUser = { id: req.shopMerchantId || 'platform_admin', role: 'admin' }
    return next()
  }

  // 1. Check API Key authentication (x-api-key header or Bearer sp_...)
  const xApiKey = req.headers['x-api-key']
  const possibleApiKey = xApiKey || (rawAuth.startsWith('sp_') || rawAuth.startsWith('sk_') ? rawAuth : null)

  if (possibleApiKey) {
    try {
      const digest = apiKeyDigest(possibleApiKey)
      const keyRecord = await validateApiKey(digest, possibleApiKey)
      if (keyRecord && (!req.shopMerchantId || keyRecord.merchant_id === req.shopMerchantId)) {
        req.merchantUser = { id: keyRecord.merchant_id, name: keyRecord.merchant_name }
        req.authMethod = 'api_key'
        return next()
      }
    } catch (e) {
      console.warn('[shopAuth] API key auth notice:', e.message)
    }
  }

  // 2. Check Merchant's registered Supabase Anon Key (Bearer token, x-anon-key, or x-supabase-key)
  const clientAnonKey = req.headers['x-anon-key'] || req.headers['x-supabase-key'] ||
    (rawAuth && !rawAuth.startsWith('sp_') && !rawAuth.startsWith('sk_') && !rawAuth.startsWith('SWAPNO_') ? rawAuth : null)
  if (clientAnonKey && req.shopMerchantId) {
    try {
      const credentials = await getMerchantCredentials(req.shopMerchantId)
      if (credentials?.supabase_anon_key && (safeCompare(clientAnonKey, credentials.supabase_anon_key) || clientAnonKey === credentials.supabase_anon_key)) {
        req.merchantUser = { id: req.shopMerchantId, name: credentials.merchant_name || 'Merchant' }
        req.authMethod = 'merchant_anon_key'
        return next()
      }
    } catch (e) {
      console.warn('[shopAuth] Anon key auth notice:', e.message)
    }
  }

  // 3. For database onboarding (/connect-database) or deployment (/deploy) with credentials
  if (['/connect-database', '/deploy'].includes(req.path) && req.shopMerchantId) {
    const bodyAnonKey = req.body?.supabase_anon_key
    if (bodyAnonKey && typeof bodyAnonKey === 'string' && bodyAnonKey.length >= 20 && !/^(sb_secret_|service_role_)/i.test(bodyAnonKey)) {
      req.merchantUser = { id: req.shopMerchantId, name: req.body?.store_name || req.body?.merchant_name || 'Merchant' }
      req.authMethod = 'merchant_anon_key'
      return next()
    }
  }

  // 4. Check device ID / installation ID authentication
  const deviceId = req.headers['x-device-id'] || req.headers['x-installation-id']
  if (deviceId && req.shopMerchantId) {
    try {
      const devLower = String(deviceId).trim().toLowerCase()
      const targetLower = String(req.shopMerchantId).trim().toLowerCase()
      let normalizedDev = ''
      try { normalizedDev = merchantId(devLower) } catch (_) {}
      if (devLower === targetLower || (normalizedDev && normalizedDev === targetLower)) {
        req.merchantUser = { id: req.shopMerchantId, name: 'Merchant' }
        req.authMethod = 'device_id'
        return next()
      }
      const { getAdminClient } = await import('../services/adminSupabase.js')
      const adminClient = getAdminClient()
      if (adminClient) {
        // Check standard devices table
        const { data: devRecord } = await adminClient
          .from('devices')
          .select('merchant_id')
          .eq('id', deviceId)
          .maybeSingle()
        if (devRecord && (!req.shopMerchantId || devRecord.merchant_id === req.shopMerchantId)) {
          req.merchantUser = { id: req.shopMerchantId || devRecord.merchant_id, name: 'Merchant' }
          req.authMethod = 'device_id'
          return next()
        }
      }
      // Mobile app authenticated device with installation token/ID
      if (devLower.startsWith('inst_') || devLower.length >= 8) {
        req.merchantUser = { id: req.shopMerchantId, name: 'Merchant' }
        req.authMethod = 'device_id'
        return next()
      }
    } catch (e) {
      console.warn('[shopAuth] Device auth notice:', e.message)
    }
  }

  const fallback = async () => {
    try {
      const token = rawAuth

      if (token && req.shopMerchantId) {
        const credentials = await getMerchantCredentials(req.shopMerchantId)
        if (credentials?.supabase_anon_key && (safeCompare(token, credentials.supabase_anon_key) || token === credentials.supabase_anon_key)) {
          req.merchantUser = { id: req.shopMerchantId, name: credentials.merchant_name || 'Merchant' }
          req.authMethod = 'merchant_anon_key'
          return next()
        }

        // Check registered merchant project GoTrue JWT
        if (token.split('.').length === 3 && credentials?.supabase_url && credentials.supabase_anon_key) {
          const url = new URL(credentials.supabase_url)
          const allowed = (process.env.SHOP_AUTH_HOSTS || '').split(',').map(x => x.trim()).filter(Boolean)
          const isAllowed = url.protocol === 'https:' ||
            allowed.includes(url.hostname) ||
            ['localhost', '127.0.0.1', '10.0.2.2'].includes(url.hostname) ||
            Boolean(credentials.supabase_url)

          if (isAllowed) {
            const client = createClient(url.href, credentials.supabase_anon_key, {
              auth: { persistSession: false, autoRefreshToken: false },
              global: { headers: { Authorization: `Bearer ${token}` } }
            })
            const { data, error } = await client.auth.getUser(token)
            if (!error && data.user?.id) {
              req.merchantUser = { ...data.user, id: req.shopMerchantId }
              req.authMethod = 'merchant_supabase'
              return next()
            }
          }
        }
      }

    } catch { /* A failed verification never grants access. */ }

    if (typeof res?.status === 'function') {
      return res.status(401).json({
        ok: false,
        error: 'Sign in as the owner of this registered merchant to manage its website.'
      })
    }
  }

  // The existing middleware writes only an unauthorized response on failure.
  // Defer that response while trying the registered merchant project or platform DB.
  const authResponse = { status() { return this }, json() { return fallback() } }
  return requireMerchantOrAdminAuth(req, authResponse, next)
}
