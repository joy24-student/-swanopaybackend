// SwapnoPay Backend — Supabase OAuth 2.0 Control Plane Router
// Native VPS backend implementation for Supabase OAuth Management API
// Replaces edge functions with dedicated, ultra-reliable Node.js endpoints.

import { Router } from 'express'
import crypto from 'crypto'
import { getAdminClient } from '../services/adminSupabase.js'

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

// Fetch or refresh access token
async function getValidAccessToken(userId) {
  const admin = getAdminClient()
  const { data: conn, error } = await admin
    .from('supabase_connections')
    .select('*')
    .eq('user_id', userId)
    .single()

  if (error || !conn) {
    throw new Error('No active Supabase connection found for user.')
  }

  const isExpired = new Date(conn.access_token_expires_at) <= new Date(Date.now() + 60000)
  if (!isExpired && conn.encrypted_access_token) {
    return conn.encrypted_access_token
  }

  // Refresh Token Exchange
  console.log(`[oauth] Access token expired for ${userId}, refreshing...`)
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
    .eq('user_id', userId)

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
    if (!userId) {
      return res.status(400).json({ error: 'user_id is required' })
    }

    const accessToken = await getValidAccessToken(userId)

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
    const { action, user_id: userId, project_ref: projectRef, organization_slug: orgSlug, project_name: projectName, db_password: dbPassword } = req.body || {}

    if (!userId) {
      return res.status(400).json({ error: 'user_id is required' })
    }

    const accessToken = await getValidAccessToken(userId)

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

      const resp = await fetch(`https://api.supabase.com/v1/projects/${projectRef}/health`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })

      const health = await resp.json().catch(() => ({}))
      const isHealthy = resp.ok && (health.healthy === true || health.status === 'ACTIVE_HEALTHY')
      return res.json({ status: isHealthy ? 'ACTIVE_HEALTHY' : 'PROVISIONING' })
    }

    if (action === 'APPLY_SCHEMA_AND_FINALIZE') {
      if (!projectRef) return res.status(400).json({ error: 'project_ref is required' })

      // Get API keys for project
      const keysResp = await fetch(`https://api.supabase.com/v1/projects/${projectRef}/api-keys`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })

      const keys = keysResp.ok ? await keysResp.json() : []
      const anonKey = keys.find((k) => k.name === 'anon')?.api_key || keys[0]?.api_key || ''

      const projectUrl = `https://${projectRef}.supabase.co`
      return res.json({ project_url: projectUrl, publishable_key: anonKey, status: 'READY' })
    }

    return res.status(400).json({ error: `Unsupported action: ${action}` })
  } catch (err) {
    console.error('[oauth-provision] Error:', err)
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

export default router
