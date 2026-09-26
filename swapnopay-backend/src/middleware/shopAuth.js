import { createClient } from '@supabase/supabase-js'
import { requireMerchantOrAdminAuth, safeCompare } from './auth.js'
import { getMerchantCredentials, validateApiKey } from '../services/adminSupabase.js'
import { apiKeyDigest } from '../utils/crypto.js'

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
      const keyRecord = await validateApiKey(digest)
      if (keyRecord && (!req.shopMerchantId || keyRecord.merchant_id === req.shopMerchantId)) {
        req.merchantUser = { id: keyRecord.merchant_id, name: keyRecord.merchant_name }
        req.authMethod = 'api_key'
        return next()
      }
    } catch (e) {
      console.warn('[shopAuth] API key auth notice:', e.message)
    }
  }

  const fallback = async () => {
    try {
      const token = rawAuth

      // 2. Check registered merchant project GoTrue JWT
      if (token && token.split('.').length === 3) {
        const credentials = await getMerchantCredentials(req.shopMerchantId)
        if (credentials?.supabase_url && credentials.supabase_anon_key) {
          const url = new URL(credentials.supabase_url)
          const allowed = (process.env.SHOP_AUTH_HOSTS || '').split(',').map(x => x.trim()).filter(Boolean)
          if (url.protocol === 'https:' && (url.hostname.endsWith('.supabase.co') || allowed.includes(url.hostname))) {
            const client = createClient(url.href, credentials.supabase_anon_key, {
              auth: { persistSession: false, autoRefreshToken: false },
              global: { headers: { Authorization: `Bearer ${token}` } }
            })
            const { data, error } = await client.auth.getUser(token)
            if (!error && data.user?.id) {
              const { data: merchant, error: ownerError } = await client
                .from('merchants')
                .select('id, user_id')
                .eq('id', req.shopMerchantId)
                .eq('user_id', data.user.id)
                .maybeSingle()
              if (!ownerError && merchant) {
                req.merchantUser = data.user
                req.authMethod = 'merchant_supabase'
                return next()
              }
            }
          }
        }
      }

    } catch { /* A failed verification never grants access. */ }

    return res.status(401).json({
      ok: false,
      error: 'Sign in as the owner of this registered merchant to manage its website.'
    })
  }

  // The existing middleware writes only an unauthorized response on failure.
  // Defer that response while trying the registered merchant project or platform DB.
  const authResponse = { status() { return this }, json() { return fallback() } }
  return requireMerchantOrAdminAuth(req, authResponse, next)
}
