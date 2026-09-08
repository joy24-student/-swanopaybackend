// SwapnoPay Backend — Auth Middleware
// Validates the ADMIN_SECRET header for /v1/admin/* routes
// Validates the PAYMENT_WEBHOOK_SECRET for /v1/payment/verify (from Supabase)
import crypto from 'crypto'

/**
 * Timing-safe string comparison to prevent timing attacks.
 */
function safeCompare(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false
  const bufA = Buffer.from(a)
  const bufB = Buffer.from(b)
  if (bufA.length !== bufB.length) return false
  return crypto.timingSafeEqual(bufA, bufB)
}

/**
 * Middleware: require X-Admin-Secret header to match ADMIN_SECRET env var.
 * Middleware: require valid admin authorization.
 * Supports:
 * 1. Supabase Auth JWT in Authorization header: Bearer <token> (checked against admin_users)
 * 2. Static X-Admin-Secret header matching ADMIN_SECRET env var (for CLI/scripts)
 * Used on all /v1/admin/* routes.
 */
export async function requireAdminSecret(req, res, next) {
  const adminSecret = process.env.ADMIN_SECRET

  // 1. Check static X-Admin-Secret header
  const xAdminSecret = req.headers['x-admin-secret']
  if (xAdminSecret && adminSecret && safeCompare(xAdminSecret, adminSecret)) {
    return next()
  }

  // 2. Check Authorization header
  const authHeader = req.headers['authorization']
  if (authHeader && authHeader.toLowerCase().startsWith('bearer ')) {
    const token = authHeader.replace(/^Bearer\s+/i, '').trim()

    // 2a. Direct static secret match via Bearer
    if (adminSecret && safeCompare(token, adminSecret)) {
      return next()
    }

    // 2b. Supabase Auth JWT verification against Admin Supabase DB
    try {
      const { getAdminClient } = await import('../services/adminSupabase.js')
      const adminClient = getAdminClient()
      const { data: { user }, error: userError } = await adminClient.auth.getUser(token)

      if (!userError && user?.id) {
        const { data: adminRecord } = await adminClient
          .from('admin_users')
          .select('id, role')
          .eq('id', user.id)
          .maybeSingle()

        if (adminRecord) {
          req.adminUser = { id: user.id, email: user.email, role: adminRecord.role || 'admin' }
          return next()
        }
      }
    } catch (err) {
      console.warn('[auth] JWT verification error:', err.message)
    }
  }

  if (!adminSecret && !req.headers['authorization']) {
    return res.status(503).json({ error: 'Admin authentication is not configured on the server' })
  }

  return res.status(401).json({ error: 'Unauthorized: invalid admin credentials' })
}

/**
 * Middleware: require X-Webhook-Secret header to match PAYMENT_WEBHOOK_SECRET env var.
 * Used on POST /v1/payment/verify (called by Supabase process-sms edge function).
 */
export function requireWebhookSecret(req, res, next) {
  const expected = process.env.PAYMENT_WEBHOOK_SECRET
  if (!expected) {
    return res.status(503).json({ error: 'Webhook authentication is not configured on the server' })
  }
  const provided = req.headers['x-webhook-secret']
  if (!provided || !safeCompare(provided, expected)) {
    return res.status(401).json({ error: 'Unauthorized: invalid webhook secret' })
  }
  next()
}

/**
 * Middleware: allows either platform Admin (via X-Admin-Secret / admin JWT)
 * OR the authenticated Merchant owner (via Supabase Auth JWT Bearer token).
 * Used for merchant-specific configuration routes.
 */
export async function requireMerchantOrAdminAuth(req, res, next) {
  const adminSecret = process.env.ADMIN_SECRET

  // 1. Check static X-Admin-Secret header
  const xAdminSecret = req.headers['x-admin-secret']
  if (xAdminSecret && adminSecret && safeCompare(xAdminSecret, adminSecret)) {
    req.isAdmin = true
    return next()
  }

  // 2. Check Authorization header
  const authHeader = req.headers['authorization']
  if (authHeader && authHeader.toLowerCase().startsWith('bearer ')) {
    const token = authHeader.replace(/^Bearer\s+/i, '').trim()

    // 2a. Direct static secret match via Bearer
    if (adminSecret && safeCompare(token, adminSecret)) {
      req.isAdmin = true
      return next()
    }

    // 2b. Supabase Auth JWT verification
    try {
      const { getAdminClient } = await import('../services/adminSupabase.js')
      const adminClient = getAdminClient()
      const { data: { user }, error: userError } = await adminClient.auth.getUser(token)

      if (!userError && user?.id) {
        // Check if admin user
        const { data: adminRecord } = await adminClient
          .from('admin_users')
          .select('id, role')
          .eq('id', user.id)
          .maybeSingle()

        if (adminRecord) {
          req.adminUser = { id: user.id, email: user.email, role: adminRecord.role || 'admin' }
          req.isAdmin = true
          return next()
        }

        // Check if merchant owner
        const targetMerchantId =
          req.body?.merchant_id ||
          req.query?.merchant_id ||
          req.params?.merchant_id ||
          req.params?.id

        if (!targetMerchantId || user.id === targetMerchantId) {
          req.merchantUser = user
          return next()
        }

        // Verify if user owns this merchant in merchants table
        const { data: merchantRecord } = await adminClient
          .from('merchants')
          .select('id, user_id')
          .eq('id', targetMerchantId)
          .eq('user_id', user.id)
          .maybeSingle()

        if (merchantRecord) {
          req.merchantUser = user
          return next()
        }
      }
    } catch (err) {
      console.warn('[auth] Merchant/Admin JWT verification error:', err.message)
    }
  }

  return res.status(401).json({ error: 'Unauthorized: valid merchant or admin credentials required' })
}
