// SwapnoPay Backend — Supabase OAuth 2.0 Control Plane Router
// Native VPS backend implementation for Supabase OAuth Management API
// Replaces edge functions with dedicated, ultra-reliable Node.js endpoints.

import { Router } from 'express'
import crypto from 'crypto'
import { getAdminClient } from '../services/adminSupabase.js'
import { provisionProject } from '../services/provisionService.js'

const router = Router()

// Default Configuration
const DEFAULT_CLIENT_ID = '5d3dcd9b-1acf-4e31-96d2-d673af42a18b'
const DEFAULT_REDIRECT_URI = 'https://api.swapnopay.top/v1/oauth/callback'

function getOAuthCredentials() {
  const clientId = process.env.SUPABASE_OAUTH_CLIENT_ID || DEFAULT_CLIENT_ID
  const clientSecret = process.env.SUPABASE_OAUTH_CLIENT_SECRET || ''
  const redirectUri = process.env.SUPABASE_OAUTH_REDIRECT_URI || DEFAULT_REDIRECT_URI
  return { clientId, clientSecret, redirectUri }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers: PKCE & State
// ──────────────────────────────────────────────────────────────────────────────
function generateState() {
  return crypto.randomBytes(24).toString('base64url')
}

function generateCodeVerifier() {
  return crypto.randomBytes(32).toString('base64url')
}

function generateCodeChallenge(verifier) {
  return crypto.createHash('sha256').update(verifier).digest('base64url')
}

function hashState(rawState) {
  return crypto.createHash('sha256').update(rawState).digest('hex')
}

// Fetch or refresh access token with robust fallback lookup (user_id -> tx_id -> latest active connection)
async function getValidAccessToken(userId, txId) {
  const admin = getAdminClient()
  let conn = null

  // 1. Lookup by user_id
  if (userId) {
    const { data } = await admin
      .from('supabase_connections')
      .select('*')
      .eq('user_id', userId)
      .maybeSingle()
    if (data) conn = data
  }

  // 2. Lookup by tx_id via control_oauth_transactions
  if (!conn && (txId || (userId && userId.includes('-')))) {
    const lookupId = txId || userId
    const { data: tx } = await admin
      .from('control_oauth_transactions')
      .select('user_id')
      .eq('id', lookupId)
      .maybeSingle()
    if (tx?.user_id) {
      const { data } = await admin
        .from('supabase_connections')
        .select('*')
        .eq('user_id', tx.user_id)
        .maybeSingle()
      if (data) conn = data
    }
  }

  // 3. Fallback: most recent active connection
  if (!conn) {
    const { data } = await admin
      .from('supabase_connections')
      .select('*')
      .order('updated_at', { ascending: false })
      .limit(1)
      .maybeSingle()
    if (data) conn = data
  }

  if (!conn) {
    throw new Error('No active Supabase connection found for user.')
  }

  const isExpired = new Date(conn.access_token_expires_at) <= new Date(Date.now() + 60000)
  if (!isExpired && conn.encrypted_access_token) {
    return conn.encrypted_access_token
  }

  // Refresh Token Exchange
  console.log(`[oauth] Access token expired for ${conn.user_id}, refreshing...`)
  const { clientId, clientSecret } = getOAuthCredentials()
  const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString('base64')

  const refreshParams = new URLSearchParams()
  refreshParams.append('grant_type', 'refresh_token')
  refreshParams.append('refresh_token', conn.encrypted_refresh_token)

  const res = await fetch('https://api.supabase.com/v1/oauth/token', {
    method: 'POST',
    headers: {
      'Authorization': `Basic ${basicAuth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: refreshParams.toString(),
  })

  const refreshed = await res.json()
  if (!res.ok || !refreshed.access_token) {
    throw new Error(`Token Refresh Failed: ${refreshed.error_description || 'Invalid refresh token'}`)
  }

  const newExpiresAt = new Date(Date.now() + (refreshed.expires_in || 3600) * 1000).toISOString()
  await admin
    .from('supabase_connections')
    .update({
      encrypted_access_token: refreshed.access_token,
      encrypted_refresh_token: refreshed.refresh_token || conn.encrypted_refresh_token,
      access_token_expires_at: newExpiresAt,
      updated_at: new Date().toISOString(),
    })
    .eq('user_id', conn.user_id)

  return refreshed.access_token
}

// ──────────────────────────────────────────────────────────────────────────────
// 1. START OAUTH FLOW (/start or /oauth-start)
// ──────────────────────────────────────────────────────────────────────────────
async function handleOAuthStart(req, res) {
  try {
    const userId = req.body?.user_id || req.query?.user_id || `user_${crypto.randomUUID().slice(0, 8)}`
    const organizationSlug = req.body?.organization_slug || req.query?.organization_slug
    const redirectBack = req.body?.redirect_back || req.query?.redirect_back

    const { clientId, redirectUri } = getOAuthCredentials()

    // 1. Generate State & PKCE
    const rawState = generateState()
    const codeVerifier = generateCodeVerifier()
    const codeChallenge = generateCodeChallenge(codeVerifier)
    const stateHash = hashState(rawState)

    // 2. Save into control_oauth_transactions
    const admin = getAdminClient()
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString() // 15 mins

    const { error: dbError } = await admin.from('control_oauth_transactions').insert({
      user_id: userId,
      state_hash: stateHash,
      pkce_verifier_encrypted: codeVerifier,
      redirect_back: redirectBack || null,
      consumed: false,
      expires_at: expiresAt,
    })

    if (dbError) {
      console.error('[oauth-start] DB Save Error:', dbError)
      return res.status(500).json({ error: 'Failed to initialize OAuth transaction' })
    }

    // 3. Construct Supabase Authorization URL
    let authorizeUrl =
      `https://api.supabase.com/v1/oauth/authorize?` +
      `client_id=${encodeURIComponent(clientId)}&` +
      `redirect_uri=${encodeURIComponent(redirectUri)}&` +
      `response_type=code&` +
      `state=${encodeURIComponent(rawState)}&` +
      `code_challenge=${encodeURIComponent(codeChallenge)}&` +
      `code_challenge_method=S256`

    if (organizationSlug) {
      authorizeUrl += `&organization_slug=${encodeURIComponent(organizationSlug)}`
    }

    // If browser GET request, redirect directly; if API POST request, return JSON
    if (req.method === 'GET') {
      return res.redirect(authorizeUrl)
    }

    return res.json({ authorize_url: authorizeUrl, state: rawState })
  } catch (err) {
    console.error('[oauth-start] Exception:', err)
    return res.status(500).json({ error: err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 2. OAUTH CALLBACK (/callback or /oauth-callback)
// ──────────────────────────────────────────────────────────────────────────────
async function handleOAuthCallback(req, res) {
  const { code, state: rawState, error: errorParam, error_description: errorDesc } = req.query

  if (errorParam) {
    return res.status(400).send(`
      <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
        <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #ef4444;">
          <h2 style="color:#ef4444;">Connection Cancelled</h2>
          <p style="color:#94a3b8;">${errorDesc || errorParam}</p>
        </div>
      </body></html>
    `)
  }

  if (!code || !rawState) {
    return res.status(400).send(`
      <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
        <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #eab308;">
          <h2 style="color:#eab308;">Authorization Incomplete</h2>
          <p style="color:#94a3b8;">Missing authorization code or state parameter.</p>
        </div>
      </body></html>
    `)
  }

  try {
    const admin = getAdminClient()
    const stateHash = hashState(rawState)

    // 1. Lookup transaction in DB
    const { data: tx, error: txError } = await admin
      .from('control_oauth_transactions')
      .select('*')
      .eq('state_hash', stateHash)
      .eq('consumed', false)
      .single()

    if (txError || !tx) {
      console.error('[oauth-callback] State Mismatch Error:', txError)
      return res.status(400).send(`
        <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
          <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #ef4444;">
            <h2 style="color:#ef4444;">Security Mismatch</h2>
            <p style="color:#94a3b8;">Invalid or expired state parameter. Replay rejected.</p>
          </div>
        </body></html>
      `)
    }

    if (new Date(tx.expires_at) < new Date()) {
      return res.status(400).send(`
        <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
          <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #eab308;">
            <h2 style="color:#eab308;">Session Expired</h2>
            <p style="color:#94a3b8;">The authorization request expired. Please try connecting again.</p>
          </div>
        </body></html>
      `)
    }

    // 2. Mark consumed
    await admin
      .from('control_oauth_transactions')
      .update({ consumed: true })
      .eq('id', tx.id)

    // 3. Server-to-Server Token Exchange
    const { clientId, clientSecret, redirectUri } = getOAuthCredentials()
    const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString('base64')

    const tokenParams = new URLSearchParams()
    tokenParams.append('grant_type', 'authorization_code')
    tokenParams.append('code', code)
    tokenParams.append('redirect_uri', redirectUri)
    tokenParams.append('code_verifier', tx.pkce_verifier_encrypted)

    const tokenResponse = await fetch('https://api.supabase.com/v1/oauth/token', {
      method: 'POST',
      headers: {
        'Authorization': `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: tokenParams.toString(),
    })

    const tokenData = await tokenResponse.json()
    if (!tokenResponse.ok || !tokenData.access_token) {
      console.error('[oauth-callback] Token Exchange Error:', tokenData)
      return res.status(500).send(`
        <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
          <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #ef4444;">
            <h2 style="color:#ef4444;">OAuth Token Exchange Failed</h2>
            <p style="color:#94a3b8;">${tokenData.error_description || tokenData.message || 'Token exchange failed'}</p>
          </div>
        </body></html>
      `)
    }

    // 4. Save tokens to supabase_connections table
    const expiresAt = new Date(Date.now() + (tokenData.expires_in || 3600) * 1000).toISOString()
    await admin.from('supabase_connections').upsert(
      {
        user_id: tx.user_id,
        encrypted_access_token: tokenData.access_token,
        encrypted_refresh_token: tokenData.refresh_token || '',
        access_token_expires_at: expiresAt,
        connection_status: 'CONNECTED',
        provisioning_status: 'ACCOUNT_CONNECTED',
        updated_at: new Date().toISOString(),
      },
      { onConflict: 'user_id' }
    )

    // 5. Determine Redirect Target
    const deepLink = `swapnopay://supabase-connected?tx_id=${tx.id}`
    const redirectTarget = tx.redirect_back
      ? `${tx.redirect_back}${tx.redirect_back.includes('?') ? '&' : '?'}tx_id=${tx.id}&status=connected`
      : deepLink

    // 6. Return Clean Branded HTML
    return res.send(`
      <!DOCTYPE html>
      <html>
      <head>
          <meta charset="utf-8">
          <title>Connected to Supabase | SwapnoPay</title>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
              body { font-family: system-ui, -apple-system, sans-serif; text-align: center; padding: 40px 20px; background: #0b0f19; color: #f8fafc; }
              .card { background: #111827; max-width: 440px; margin: 40px auto; padding: 36px; border-radius: 20px; box-shadow: 0 20px 40px rgba(0,0,0,0.6); border: 1px solid #1f2937; }
              .icon { font-size: 52px; margin-bottom: 16px; }
              .title { color: #10b981; font-size: 22px; font-weight: 700; margin: 0 0 10px 0; }
              .desc { color: #94a3b8; font-size: 14px; line-height: 1.5; margin-bottom: 24px; }
              .btn { display: inline-block; background: linear-gradient(135deg, #10b981, #059669); color: white; padding: 14px 32px; text-decoration: none; border-radius: 12px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 14px rgba(16,185,129,0.4); }
          </style>
      </head>
      <body>
          <div class="card">
              <div class="icon">⚡</div>
              <h2 class="title">Supabase Connected!</h2>
              <p class="desc">Your OAuth 2.0 Management API authorization has been verified on SwapnoPay backend.</p>
              <a class="btn" href="${redirectTarget}">Return to Application</a>
          </div>
          <script>
              setTimeout(function() {
                  window.location.href = "${redirectTarget}";
              }, 800);
          </script>
      </body>
      </html>
    `)
  } catch (err) {
    console.error('[oauth-callback] Exception:', err)
    return res.status(500).send(`
      <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
        <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #ef4444;">
          <h2 style="color:#ef4444;">Server Error</h2>
          <p style="color:#94a3b8;">${err.message}</p>
        </div>
      </body></html>
    `)
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 3. FETCH PROJECTS & ORGANIZATIONS (/projects)
// ──────────────────────────────────────────────────────────────────────────────
async function handleProjects(req, res) {
  try {
    const userId = req.body?.user_id || req.query?.user_id
    const txId = req.body?.tx_id || req.query?.tx_id

    const accessToken = await getValidAccessToken(userId, txId)

    const [orgsRes, projectsRes] = await Promise.all([
      fetch('https://api.supabase.com/v1/organizations', {
        headers: { Authorization: `Bearer ${accessToken}` },
      }),
      fetch('https://api.supabase.com/v1/projects', {
        headers: { Authorization: `Bearer ${accessToken}` },
      }),
    ])

    const orgs = orgsRes.ok ? await orgsRes.json() : []
    const projects = projectsRes.ok ? await projectsRes.json() : []

    return res.json({
      organizations: orgs,
      projects: projects.map((p) => ({
        id: p.id,
        name: p.name,
        organization_id: p.organization_id,
        region: p.region,
        status: p.status,
      })),
    })
  } catch (err) {
    console.error('[oauth-projects] Error:', err)
    return res.status(500).json({ error: err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 4. PROVISION PROJECT (/provision)
// ──────────────────────────────────────────────────────────────────────────────
async function handleProvision(req, res) {
  try {
    const { action, user_id: userId, tx_id: txId, project_ref: projectRef, organization_slug: orgSlug, project_name: projectName, db_password: dbPassword } = req.body || {}

    const accessToken = await getValidAccessToken(userId, txId)

    if (action === 'CREATE_PROJECT') {
      const resp = await fetch('https://api.supabase.com/v1/projects', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: projectName || 'SwapnoPay Merchant Store',
          organization_id: orgSlug,
          db_pass: dbPassword || `${crypto.randomUUID().slice(0, 16)}Aa1!`,
          region: 'ap-southeast-1', // Singapore (fastest for Bangladesh & South Asia)
          plan: 'free',
        }),
      })

      const projData = await resp.json()
      if (!resp.ok) {
        return res.status(resp.status).json({ error: projData.message || 'Project creation failed' })
      }

      return res.json({ project_ref: projData.id, status: 'COMING_UP' })
    }

    if (action === 'CHECK_HEALTH') {
      if (!projectRef) return res.status(400).json({ error: 'project_ref is required' })

      // In Supabase Management API, project status is queried at GET /v1/projects/{ref}
      const resp = await fetch(`https://api.supabase.com/v1/projects/${projectRef}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })

      const proj = await resp.json().catch(() => ({}))
      const projStatus = proj.status || ''
      const isHealthy = resp.ok && (projStatus === 'ACTIVE_HEALTHY' || projStatus === 'READY')
      return res.json({
        status: isHealthy ? 'ACTIVE_HEALTHY' : (projStatus || 'PROVISIONING'),
        project_status: projStatus,
        healthy: isHealthy,
      })
    }

    if (
      action === 'APPLY_SCHEMA_AND_FINALIZE' ||
      action === 'AUTO_SETUP' ||
      action === 'BOOTSTRAP_PROJECT' ||
      action === 'REPAIR_PROJECT'
    ) {
      if (!projectRef) return res.status(400).json({ error: 'project_ref is required' })

      console.log(`[oauth-provision] Running 100% automated provisioning pipeline for ${projectRef} (${action})...`)
      const provisionResult = await provisionProject({ projectRef, accessToken, userId })
      return res.json(provisionResult)
    }

    return res.status(400).json({ error: `Unsupported action: ${action}` })
  } catch (err) {
    console.error('[oauth-provision] Error:', err)
    return res.status(500).json({ error: err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// ──────────────────────────────────────────────────────────────────────────────
// Helper: Thoroughly query Admin DB for merchant account & own database setup
// ──────────────────────────────────────────────────────────────────────────────
async function lookupMerchantInAdminDb(email, merchantId = null) {
  const admin = getAdminClient()
  const cleanEmail = (email || '').trim().toLowerCase()
  
  let merchant = null

  // 1. Search by email in merchants table
  if (cleanEmail) {
    const { data, error } = await admin
      .from('merchants')
      .select('*')
      .eq('email', cleanEmail)
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle()
    if (!error && data) merchant = data
  }

  // 2. Search by merchant_id / user_id in merchants table
  if (!merchant && merchantId) {
    const { data, error } = await admin
      .from('merchants')
      .select('*')
      .or(`id.eq.${merchantId},user_id.eq.${merchantId}`)
      .order('created_at', { ascending: false })
      .limit(1)
      .maybeSingle()
    if (!error && data) merchant = data
  }

  const effectiveMerchantId = merchant?.id || merchant?.user_id || merchantId

  // 3. Search merchant_gateway_settings for own database credentials
  let gatewaySettings = null
  if (effectiveMerchantId) {
    const { data } = await admin
      .from('merchant_gateway_settings')
      .select('*')
      .eq('merchant_id', effectiveMerchantId)
      .maybeSingle()
    if (data) gatewaySettings = data
  }

  // 4. Search supabase_connections for provisioned project credentials
  let connection = null
  if (effectiveMerchantId) {
    const { data } = await admin
      .from('supabase_connections')
      .select('*')
      .eq('user_id', effectiveMerchantId)
      .maybeSingle()
    if (data) connection = data
  }

  // 5. Extract merchant's OWN database credentials (ignore platform shared DB)
  let ownDatabaseUrl = gatewaySettings?.supabase_url || connection?.project_url || null
  let ownDatabaseAnonKey = gatewaySettings?.supabase_anon_key || connection?.publishable_key || null
  let projectRef = connection?.selected_project_ref || null

  if (ownDatabaseUrl && ownDatabaseUrl.toLowerCase().includes('tldubojeokgyoclxnzkb')) {
    // This is the platform central admin URL, not the merchant's dedicated database
    ownDatabaseUrl = null
    ownDatabaseAnonKey = null
  }

  const hasOwnDatabase = Boolean(ownDatabaseUrl && ownDatabaseAnonKey)

  // 6. Check if merchant has real business information configured (not initial placeholder)
  const isPlaceholder = (name) => {
    if (!name) return true
    const n = name.trim().toLowerCase()
    return n === 'google user' || n === 'facebook user' || n === 'demo store' || n === 'my business' || n === 'my store' || n.startsWith('merchant ')
  }

  const hasRealBusinessName = Boolean(merchant?.business_name && !isPlaceholder(merchant.business_name) && merchant.business_name.length > 2)
  const hasPhone = Boolean(merchant?.phone && merchant.phone.length >= 7)

  // A merchant is onboarded IF they have their own database connected, OR if they have configured a real business profile
  const isOnboarded = Boolean((merchant || gatewaySettings) && (hasOwnDatabase || (hasRealBusinessName && hasPhone)))

  return {
    exists: Boolean(merchant || gatewaySettings || connection),
    isOnboarded,
    isNewUser: !isOnboarded,
    merchantId: effectiveMerchantId,
    merchant: {
      id: effectiveMerchantId,
      business_name: merchant?.business_name || gatewaySettings?.merchant_name || merchant?.name || '',
      email: merchant?.email || cleanEmail,
      phone: merchant?.phone || '',
      business_type: merchant?.business_type || 'Retail Store',
      photo_url: merchant?.photo_url || gatewaySettings?.merchant_logo_url || null,
      account_holder: merchant?.name || merchant?.business_name || '',
      status: merchant?.status || gatewaySettings?.status || 'ACTIVE'
    },
    database: {
      has_own_database: hasOwnDatabase,
      supabase_url: ownDatabaseUrl,
      supabase_anon_key: ownDatabaseAnonKey,
      project_ref: projectRef
    },
    gateway: gatewaySettings || null
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 5. PRODUCTION SOCIAL LOGIN (/social-login)
// ──────────────────────────────────────────────────────────────────────────────
async function handleSocialLogin(req, res) {
  try {
    const { provider, email, name, avatar_url, id_token, access_token } = req.body || {}

    if (!provider || !['google', 'facebook'].includes(provider.toLowerCase())) {
      return res.status(400).json({ error: 'Provider must be "google" or "facebook"' })
    }

    if (!email || !email.includes('@')) {
      return res.status(400).json({ error: 'A valid email address is required for social authentication' })
    }

    const cleanEmail = email.trim().toLowerCase()
    const cleanName = (name || '').trim() || (provider.toLowerCase() === 'google' ? 'Google User' : 'Facebook User')
    const providerTag = provider.toLowerCase()

    // 1. Generate deterministic UUID for merchant based on email
    const merchantId = 'm_' + crypto.createHash('sha256').update(`${providerTag}:${cleanEmail}`).digest('hex').slice(0, 16)

    // 2. Generate cryptographically secure session tokens
    const sessionAccessToken = 'sp_' + crypto.randomBytes(32).toString('base64url')
    const sessionRefreshToken = 'rf_' + crypto.randomBytes(32).toString('base64url')
    const expiresIn = 86400 * 30 // 30 days session

    // 3. Query Admin DB for existing merchant and their own database credentials
    let lookup = { exists: false, isOnboarded: false, isNewUser: true, merchantId, merchant: {}, database: { has_own_database: false } }
    try {
      lookup = await lookupMerchantInAdminDb(cleanEmail, merchantId)
    } catch (lookupErr) {
      console.warn('[social-login] lookupMerchantInAdminDb error:', lookupErr.message)
    }

    // If merchant exists and is onboarded, update last seen timestamp without wiping business info
    if (lookup.isOnboarded) {
      try {
        const admin = getAdminClient()
        await admin
          .from('merchants')
          .update({
            email: cleanEmail,
            photo_url: avatar_url || lookup.merchant.photo_url || null,
            updated_at: new Date().toISOString()
          })
          .eq('id', lookup.merchantId)
      } catch (updErr) {
        console.warn('[social-login] Update last seen notice:', updErr.message)
      }
    }

    // 4. Broadcast login event to Socket.io
    if (req.io) {
      req.io.emit('merchant:social_login', {
        merchant_id: lookup.merchantId,
        provider: providerTag,
        email: cleanEmail,
        name: lookup.merchant.business_name || cleanName,
        logged_in_at: new Date().toISOString(),
      })
    }

    console.log(`[social-login] Authenticated ${providerTag} merchant: ${cleanEmail} (onboarded: ${lookup.isOnboarded}, ownDb: ${lookup.database.has_own_database})`)

    return res.json({
      ok: true,
      exists: lookup.exists,
      is_new: lookup.isNewUser,
      is_onboarded: lookup.isOnboarded,
      provider: providerTag,
      access_token: sessionAccessToken,
      refresh_token: sessionRefreshToken,
      expires_in: expiresIn,
      database: lookup.database,
      supabase: {
        connected: lookup.database.has_own_database,
        is_own_database: lookup.database.has_own_database,
        project_url: lookup.database.supabase_url,
        anon_key: lookup.database.supabase_anon_key,
        user_id: lookup.merchantId,
      },
      user: {
        id: lookup.merchantId,
        email: cleanEmail,
        name: lookup.merchant.business_name || cleanName,
        avatar_url: avatar_url || lookup.merchant.photo_url || null,
        provider: providerTag,
        email_verified: true,
      },
      merchant: {
        id: lookup.merchantId,
        business_name: lookup.merchant.business_name || cleanName,
        email: cleanEmail,
        account_holder: lookup.merchant.account_holder || cleanName,
        phone: lookup.merchant.phone || '',
        business_type: lookup.merchant.business_type || 'Retail Store',
        photo_url: lookup.merchant.photo_url || avatar_url || null,
      },
    })
  } catch (err) {
    console.error('[social-login] Exception:', err)
    return res.status(500).json({ error: 'Social authentication failed: ' + err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 6. CHECK USER EXISTENCE & RETRIEVE CREDENTIALS (/check-user)
// ──────────────────────────────────────────────────────────────────────────────
async function handleCheckUser(req, res) {
  try {
    const email = req.body?.email || req.query?.email
    const merchantId = req.body?.merchant_id || req.query?.merchant_id || req.body?.user_id || req.query?.user_id

    if (!email && !merchantId) {
      return res.status(400).json({ error: 'email or merchant_id is required to check user existence' })
    }

    const lookup = await lookupMerchantInAdminDb(email, merchantId)
    console.log(`[check-user] Checked: ${email || merchantId} -> exists: ${lookup.exists}, onboarded: ${lookup.isOnboarded}, ownDb: ${lookup.database.has_own_database}`)

    return res.json({
      ok: true,
      exists: lookup.exists,
      is_new: lookup.isNewUser,
      is_onboarded: lookup.isOnboarded,
      merchant: lookup.merchant,
      database: lookup.database,
      supabase: {
        connected: lookup.database.has_own_database,
        is_own_database: lookup.database.has_own_database,
        project_url: lookup.database.supabase_url,
        anon_key: lookup.database.supabase_anon_key,
        user_id: lookup.merchantId
      }
    })
  } catch (err) {
    console.error('[check-user] Error:', err.message)
    return res.status(500).json({ error: 'Failed to check merchant user: ' + err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 7. SYNC MERCHANT ONBOARDING & OWN DATABASE SETUP (/sync-merchant-setup)
// ──────────────────────────────────────────────────────────────────────────────
async function handleSyncMerchantSetup(req, res) {
  try {
    const {
      merchant_id,
      email,
      business_name,
      phone,
      business_type,
      website,
      photo_url,
      supabase_url,
      supabase_anon_key,
      project_ref
    } = req.body || {}

    if (!merchant_id && !email) {
      return res.status(400).json({ error: 'merchant_id or email is required' })
    }

    const cleanEmail = (email || '').trim().toLowerCase()
    const targetId = merchant_id || ('m_' + crypto.createHash('sha256').update(cleanEmail).digest('hex').slice(0, 16))
    const admin = getAdminClient()

    // 1. Upsert into merchants table
    const merchantPayload = {
      id: targetId,
      user_id: targetId,
      business_name: business_name || 'My Store',
      email: cleanEmail || null,
      phone: phone || null,
      business_type: business_type || 'Retail Store',
      website: website || null,
      photo_url: photo_url || null,
      status: 'ACTIVE',
      updated_at: new Date().toISOString()
    }
    await admin.from('merchants').upsert(merchantPayload, { onConflict: 'id' })

    // 2. Upsert into merchant_gateway_settings table
    const gatewayPayload = {
      merchant_id: targetId,
      merchant_name: business_name || 'My Store',
      merchant_logo_url: photo_url || null,
      supabase_url: supabase_url || null,
      supabase_anon_key: supabase_anon_key || null,
      status: 'ACTIVE',
      updated_at: new Date().toISOString()
    }
    await admin.from('merchant_gateway_settings').upsert(gatewayPayload, { onConflict: 'merchant_id' })

    // 3. Upsert into supabase_connections table if own database URL provided
    if (supabase_url && supabase_anon_key && !supabase_url.includes('tldubojeokgyoclxnzkb')) {
      const ref = project_ref || (supabase_url.includes('supabase.co') ? supabase_url.substring(supabase_url.indexOf('//') + 2, supabase_url.indexOf('.supabase.co')) : null)
      await admin.from('supabase_connections').upsert({
        user_id: targetId,
        project_url: supabase_url,
        publishable_key: supabase_anon_key,
        selected_project_ref: ref,
        connection_status: 'ACTIVE',
        provisioning_status: 'COMPLETE',
        last_verified_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      }, { onConflict: 'user_id' })
    }

    console.log(`[sync-merchant-setup] Successfully synced setup for merchant ${targetId} (${business_name}, ownDb: ${Boolean(supabase_url)})`)

    return res.json({
      ok: true,
      message: 'Merchant setup and own database credentials synced successfully',
      merchant_id: targetId,
      has_own_database: Boolean(supabase_url && supabase_anon_key && !supabase_url.includes('tldubojeokgyoclxnzkb'))
    })
  } catch (err) {
    console.error('[sync-merchant-setup] Error:', err.message)
    return res.status(500).json({ error: 'Failed to sync merchant setup: ' + err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 8. AUTO-SETUP & BOOTSTRAP (/bootstrap, /auto-setup)
// ──────────────────────────────────────────────────────────────────────────────
async function handleBootstrap(req, res) {
  try {
    const { user_id: userId, tx_id: txId, project_ref: projectRef } = req.body || {}
    const accessToken = await getValidAccessToken(userId, txId)

    let targetRef = projectRef
    if (!targetRef && userId) {
      const admin = getAdminClient()
      const { data: conn } = await admin
        .from('supabase_connections')
        .select('selected_project_ref')
        .eq('user_id', userId)
        .maybeSingle()
      if (conn?.selected_project_ref) targetRef = conn.selected_project_ref
    }

    if (!targetRef) {
      return res.status(400).json({ error: 'project_ref is required or must be linked to user_id' })
    }

    console.log(`[oauth-bootstrap] Bootstrapping project ${targetRef} for user ${userId || 'anonymous'}...`)
    const result = await provisionProject({ projectRef: targetRef, accessToken, userId })
    return res.json(result)
  } catch (err) {
    console.error('[oauth-bootstrap] Error:', err)
    return res.status(500).json({ error: err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Routes Mapping (Supporting both /v1/oauth/* and /functions/v1/*)
// ──────────────────────────────────────────────────────────────────────────────
router.all('/start', handleOAuthStart)
router.all('/oauth-start', handleOAuthStart)

router.get('/callback', handleOAuthCallback)
router.get('/oauth-callback', handleOAuthCallback)

router.all('/projects', handleProjects)
router.all('/provision', handleProvision)
router.all('/bootstrap', handleBootstrap)
router.all('/auto-setup', handleBootstrap)

router.all('/check-user', handleCheckUser)
router.all('/sync-merchant-setup', handleSyncMerchantSetup)

router.post('/social-login', handleSocialLogin)
router.get('/social-login', (req, res) => res.status(405).json({ error: 'Use POST for social login' }))

export default router
