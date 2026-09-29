// SwapnoPay Backend — Supabase OAuth 2.0 Control Plane Router
// Native VPS backend implementation for Supabase OAuth Management API
// Replaces edge functions with dedicated, ultra-reliable Node.js endpoints.

import { Router } from 'express'
import crypto from 'crypto'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { getAdminClient, setMerchantGatewayConfig } from '../services/adminSupabase.js'
import { lookupMerchantInAdminDb, requirePlatformUser, requireData } from '../services/merchantAccount.js'
import { provisionProject, getDefaultProjectDbPassword, saveDefaultProjectCredentials } from '../services/provisionService.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const router = Router()

// Default Configuration
const DEFAULT_CLIENT_ID = '5d3dcd9b-1acf-4e31-96d2-d673af42a18b'
const DEFAULT_CLIENT_SECRET = 'sba_db474448667fb0eea9ab0b36d2395a29c9149b61'
const DEFAULT_REDIRECT_URI = 'https://api.swapnopay.top/v1/oauth/callback'

export function getOAuthCredentials() {
  const clientId = (process.env.SUPABASE_OAUTH_CLIENT_ID || DEFAULT_CLIENT_ID).trim()
  const clientSecret = (process.env.SUPABASE_OAUTH_CLIENT_SECRET || DEFAULT_CLIENT_SECRET).trim()
  const redirectUri = (process.env.SUPABASE_OAUTH_REDIRECT_URI || DEFAULT_REDIRECT_URI).trim()
  return { clientId, clientSecret, redirectUri }
}

// In-memory + disk fallback caches (ensure reliability across PM2 restarts or if RLS limits DB writes)
const oauthTxCache = new Map()
const connectionsCache = new Map()
const OAUTH_CACHE_FILE = path.resolve(__dirname, '../../data/oauth-state.json')

function loadOAuthDiskCache() {
  try {
    if (!fs.existsSync(OAUTH_CACHE_FILE)) return
    const raw = JSON.parse(fs.readFileSync(OAUTH_CACHE_FILE, 'utf8') || '{}')
    if (raw.transactions && typeof raw.transactions === 'object') {
      for (const [k, v] of Object.entries(raw.transactions)) {
        if (v && !oauthTxCache.has(k)) oauthTxCache.set(k, v)
      }
    }
    if (raw.connections && typeof raw.connections === 'object') {
      for (const [k, v] of Object.entries(raw.connections)) {
        if (v && !connectionsCache.has(k)) connectionsCache.set(k, v)
      }
    }
  } catch (_) {}
}

function saveOAuthDiskCache() {
  try {
    const dir = path.dirname(OAUTH_CACHE_FILE)
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
    const transactions = Object.fromEntries(oauthTxCache.entries())
    const connections = Object.fromEntries(connectionsCache.entries())
    fs.writeFileSync(OAUTH_CACHE_FILE, JSON.stringify({ transactions, connections }, null, 2), 'utf8')
  } catch (_) {}
}

loadOAuthDiskCache()

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

function escapeHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// Fetch or refresh access token with robust fallback lookup (directToken -> user_id -> tx_id -> latest active connection)
async function getValidAccessToken(userId, txId, directAccessToken = '') {
  if (directAccessToken && String(directAccessToken).trim().length >= 10) {
    return String(directAccessToken).trim()
  }

  loadOAuthDiskCache()
  const admin = getAdminClient()
  let conn = null

  // 1. Lookup in-memory cache
  if (userId && connectionsCache.has(userId)) {
    conn = connectionsCache.get(userId)
  }
  if (!conn && txId && connectionsCache.has(txId)) {
    conn = connectionsCache.get(txId)
  }

  // 2. Lookup by user_id in DB
  if (!conn && userId) {
    try {
      const { data } = await admin
        .from('supabase_connections')
        .select('*')
        .eq('user_id', userId)
        .maybeSingle()
      if (data) conn = data
    } catch (_) {}
  }

  // 3. Lookup by tx_id via control_oauth_transactions
  if (!conn && (txId || (userId && userId.includes('-')))) {
    const lookupId = txId || userId
    const cachedTx = oauthTxCache.get(lookupId)
    const matchedUserId = cachedTx?.user_id
    if (matchedUserId && connectionsCache.has(matchedUserId)) {
      conn = connectionsCache.get(matchedUserId)
    }

    if (!conn) {
      try {
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
      } catch (_) {}
    }
  }

  // 4. Fallback to latest active connection if user_id changed between onboarding and profile setup
  if (!conn && connectionsCache.has('latest')) {
    conn = connectionsCache.get('latest')
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
  refreshParams.append('client_id', clientId)
  if (clientSecret) {
    refreshParams.append('client_secret', clientSecret)
  }

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
    throw new Error(`Token Refresh Failed: ${refreshed.error_description || refreshed.message || 'Invalid refresh token'}`)
  }

  const newExpiresAt = new Date(Date.now() + (refreshed.expires_in || 3600) * 1000).toISOString()
  const updatedConn = {
    ...conn,
    encrypted_access_token: refreshed.access_token,
    encrypted_refresh_token: refreshed.refresh_token || conn.encrypted_refresh_token,
    access_token_expires_at: newExpiresAt,
    updated_at: new Date().toISOString(),
  }
  if (conn.user_id) connectionsCache.set(conn.user_id, updatedConn)
  connectionsCache.set('latest', updatedConn)
  saveOAuthDiskCache()

  try {
    await admin
      .from('supabase_connections')
      .update({
        encrypted_access_token: refreshed.access_token,
        encrypted_refresh_token: refreshed.refresh_token || conn.encrypted_refresh_token,
        access_token_expires_at: newExpiresAt,
        updated_at: new Date().toISOString(),
      })
      .eq('user_id', conn.user_id)
  } catch (_) {}

  return refreshed.access_token
}

// ──────────────────────────────────────────────────────────────────────────────
// 1. START OAUTH FLOW (/start or /oauth-start)
// ──────────────────────────────────────────────────────────────────────────────
async function handleOAuthStart(req, res) {
  try {
    const userId = req.body?.user_id || req.query?.user_id || 'user_default'
    const organizationSlug = req.body?.organization_slug || req.query?.organization_slug
    const redirectBack = req.body?.redirect_back || req.query?.redirect_back

    const { clientId, redirectUri } = getOAuthCredentials()

    // 1. Generate State & PKCE
    const rawState = generateState()
    const codeVerifier = generateCodeVerifier()
    const codeChallenge = generateCodeChallenge(codeVerifier)
    const stateHash = hashState(rawState)

    // 2. Save into cache & control_oauth_transactions
    const expiresAt = new Date(Date.now() + 15 * 60 * 1000).toISOString() // 15 mins
    const txId = crypto.randomUUID()
    const txData = {
      id: txId,
      user_id: userId,
      state_hash: stateHash,
      pkce_verifier_encrypted: codeVerifier,
      redirect_back: redirectBack || null,
      consumed: false,
      expires_at: expiresAt,
      created_at: new Date().toISOString(),
    }
    oauthTxCache.set(stateHash, txData)
    oauthTxCache.set(txId, txData)
    saveOAuthDiskCache()

    try {
      const admin = getAdminClient()
      const { error: dbError } = await admin.from('control_oauth_transactions').insert({
        id: txData.id,
        user_id: userId,
        state_hash: stateHash,
        pkce_verifier_encrypted: codeVerifier,
        redirect_back: redirectBack || null,
        consumed: false,
        expires_at: expiresAt,
      })
      if (dbError) {
        console.warn('[oauth-start] DB Save Warning (continuing with in-memory transaction):', dbError.message)
      }
    } catch (dbErr) {
      console.warn('[oauth-start] DB Exception (continuing with in-memory transaction):', dbErr.message)
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

    return res.json({
      authorize_url: authorizeUrl,
      state: rawState,
      tx_id: txId,
      code_verifier: codeVerifier,
    })
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
          <p style="color:#94a3b8;">${escapeHtml(errorDesc || errorParam)}</p>
        </div>
      </body></html>
    `)
  }

  if (!code) {
    return res.status(400).send(`
      <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
        <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #eab308;">
          <h2 style="color:#eab308;">Authorization Incomplete</h2>
          <p style="color:#94a3b8;">Missing authorization code from Supabase.</p>
        </div>
      </body></html>
    `)
  }

  // If code is present but state parameter was omitted by client/fallback, deep link directly into the app
  if (code && !rawState) {
    console.warn('[oauth-callback] Code received without state parameter. Forwarding to app deep link directly.')
    const directAppDeepLink = `swapnopay://supabase-oauth-callback?code=${encodeURIComponent(code)}`
    return res.send(`
      <!DOCTYPE html>
      <html>
      <head>
          <meta charset="utf-8">
          <title>Authorization Approved | SwapnoPay</title>
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
              <h2 class="title">Supabase Authorized!</h2>
              <p class="desc">Your authorization has been granted. Returning to SwapnoPay application...</p>
              <a class="btn" href="${directAppDeepLink}">Return to Application</a>
          </div>
          <script>
              window.location.href = "${directAppDeepLink}";
              setTimeout(function() {
                  window.location.href = "${directAppDeepLink}";
              }, 400);
          </script>
      </body>
      </html>
    `)
  }

  try {
    loadOAuthDiskCache()
    const admin = getAdminClient()
    const stateHash = hashState(rawState)

    // 1. Lookup transaction in memory/disk first, then DB
    let tx = oauthTxCache.get(stateHash) || null
    if (!tx) {
      try {
        const { data: dbTx } = await admin
          .from('control_oauth_transactions')
          .select('*')
          .eq('state_hash', stateHash)
          .eq('consumed', false)
          .maybeSingle()
        if (dbTx) tx = dbTx
      } catch (_) {}
    }

    if (!tx) {
      console.warn('[oauth-callback] Transaction not matched in cache or DB. Forwarding code to app deep link:', rawState)
      if (code) {
        const directAppDeepLink = `swapnopay://supabase-oauth-callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(rawState || '')}`
        return res.send(`
          <!DOCTYPE html>
          <html>
          <head>
              <meta charset="utf-8">
              <title>Authorization Approved | SwapnoPay</title>
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
                  <h2 class="title">Supabase Authorized!</h2>
                  <p class="desc">Your authorization has been granted. Returning to SwapnoPay application...</p>
                  <a class="btn" href="${directAppDeepLink}">Return to Application</a>
              </div>
              <script>
                  window.location.href = "${directAppDeepLink}";
                  setTimeout(function() {
                      window.location.href = "${directAppDeepLink}";
                  }, 400);
              </script>
          </body>
          </html>
        `)
      }
      return res.status(400).send(`
        <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
          <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #ef4444;">
            <h2 style="color:#ef4444;">Authorization Code Missing</h2>
            <p style="color:#94a3b8;">No authorization code was returned by Supabase.</p>
          </div>
        </body></html>
      `)
    }

    if (new Date(tx.expires_at) < new Date() && !code) {
      return res.status(400).send(`
        <!DOCTYPE html><html><body style="font-family:system-ui;text-align:center;padding:50px;background:#0f172a;color:#f8fafc;">
          <div style="background:#1e293b;max-width:440px;margin:0 auto;padding:32px;border-radius:16px;border:1px solid #eab308;">
            <h2 style="color:#eab308;">Session Expired</h2>
            <p style="color:#94a3b8;">The authorization request expired. Please try connecting again.</p>
          </div>
        </body></html>
      `)
    }

    // 2. Mark consumed in memory and DB
    tx.consumed = true
    saveOAuthDiskCache()
    try {
      await admin
        .from('control_oauth_transactions')
        .update({ consumed: true })
        .eq('id', tx.id)
    } catch (_) {}

    // 3. Server-to-Server Token Exchange
    const { clientId, clientSecret, redirectUri } = getOAuthCredentials()
    const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString('base64')

    const tokenParams = new URLSearchParams()
    tokenParams.append('grant_type', 'authorization_code')
    tokenParams.append('client_id', clientId)
    if (clientSecret) {
      tokenParams.append('client_secret', clientSecret)
    }
    tokenParams.append('code', code)
    tokenParams.append('redirect_uri', redirectUri)
    if (tx.pkce_verifier_encrypted) {
      tokenParams.append('code_verifier', tx.pkce_verifier_encrypted)
    }

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
            <p style="color:#94a3b8;">${escapeHtml(tokenData.error_description || tokenData.message || 'Token exchange failed')}</p>
          </div>
        </body></html>
      `)
    }

    // 4. Save tokens to memory/disk cache and supabase_connections table
    const expiresAt = new Date(Date.now() + (tokenData.expires_in || 3600) * 1000).toISOString()
    const connRecord = {
      user_id: tx.user_id,
      encrypted_access_token: tokenData.access_token,
      encrypted_refresh_token: tokenData.refresh_token || '',
      access_token_expires_at: expiresAt,
      connection_status: 'CONNECTED',
      provisioning_status: 'ACCOUNT_CONNECTED',
      updated_at: new Date().toISOString(),
    }
    connectionsCache.set(tx.user_id, connRecord)
    connectionsCache.set(tx.id, connRecord)
    connectionsCache.set('latest', connRecord)
    saveOAuthDiskCache()

    try {
      await admin.from('supabase_connections').upsert(connRecord, { onConflict: 'user_id' })
    } catch (connErr) {
      console.warn('[oauth-callback] Connection DB upsert notice (persisted in-memory):', connErr.message)
    }

    // 4b. Automatically discover active project and save default db_password & anon_key in background
    ;(async () => {
      try {
        const pRes = await fetch('https://api.supabase.com/v1/projects', {
          headers: { Authorization: `Bearer ${tokenData.access_token}` },
        })
        if (pRes.ok) {
          const projects = await pRes.json()
          if (Array.isArray(projects) && projects.length > 0) {
            const activeProj = projects.find(p => p.status === 'ACTIVE_HEALTHY' || p.status === 'READY') || projects[0]
            if (activeProj?.id) {
              await saveDefaultProjectCredentials({
                userId: tx.user_id,
                projectRef: activeProj.id,
                accessToken: tokenData.access_token,
              })
            }
          }
        }
      } catch (autoErr) {
        console.warn('[oauth-callback] Auto credential discovery notice:', autoErr.message)
      }
    })()

    // 5. Determine Redirect Target
    const tokenQuery = `tx_id=${encodeURIComponent(tx.id)}&access_token=${encodeURIComponent(tokenData.access_token)}&refresh_token=${encodeURIComponent(tokenData.refresh_token || '')}`
    const deepLink = `swapnopay://supabase-connected?${tokenQuery}`
    const rawTarget = tx.redirect_back
      ? `${tx.redirect_back}${tx.redirect_back.includes('?') ? '&' : '?'}${tokenQuery}&status=connected`
      : deepLink
    const isSafeTarget = /^(https?:\/\/|swapnopay:\/\/)/i.test(rawTarget)
    const redirectTarget = isSafeTarget ? rawTarget : deepLink

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
          <p style="color:#94a3b8;">${escapeHtml(err.message)}</p>
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
    const directToken = req.body?.access_token || req.query?.access_token || ''

    const accessToken = await getValidAccessToken(userId, txId, directToken)

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

    // Automatically pre-save default db_password & anon_key for the primary project
    let autoConnectedProject = null
    if (Array.isArray(projects) && projects.length > 0) {
      const primaryProj = projects.find(p => p.status === 'ACTIVE_HEALTHY' || p.status === 'READY') || projects[0]
      if (primaryProj?.id) {
        try {
          const saved = await saveDefaultProjectCredentials({
            userId: userId || txId || 'user_default',
            projectRef: primaryProj.id,
            accessToken,
          })
          if (saved?.projectUrl && saved?.anonKey) {
            autoConnectedProject = {
              project_ref: saved.projectRef,
              project_url: saved.projectUrl,
              publishable_key: saved.anonKey,
              anon_key: saved.anonKey,
              db_password: saved.dbPassword || '',
              database_url: saved.databaseUrl || '',
            }
          }
        } catch (_) {}
      }
    }

    return res.json({
      organizations: orgs,
      projects: projects.map((p) => ({
        id: p.id,
        name: p.name,
        organization_id: p.organization_id,
        region: p.region,
        status: p.status,
      })),
      auto_connected_project: autoConnectedProject,
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
    const { action, user_id: userId, tx_id: txId, access_token: directToken, project_ref: projectRef, organization_slug: orgSlug, project_name: projectName, db_password: dbPassword } = req.body || {}

    const accessToken = await getValidAccessToken(userId, txId, directToken)

    if (action === 'CREATE_PROJECT') {
      const defaultPass = getDefaultProjectDbPassword(userId || projectName || 'merchant', dbPassword)

      // Resolve valid organization_id from Supabase Management API
      let targetOrgId = orgSlug
      try {
        const orgsRes = await fetch('https://api.supabase.com/v1/organizations', {
          headers: { Authorization: `Bearer ${accessToken}` },
        })
        if (orgsRes.ok) {
          const orgs = await orgsRes.json()
          if (Array.isArray(orgs) && orgs.length > 0) {
            const matchedOrg = orgs.find((o) => o.id === targetOrgId || o.slug === targetOrgId)
            if (matchedOrg) {
              targetOrgId = matchedOrg.id
            } else if (!targetOrgId || targetOrgId === 'personal' || !orgs.some((o) => o.id === targetOrgId)) {
              targetOrgId = orgs[0].id
            }
          }
        }
      } catch (orgErr) {
        console.warn('[oauth-create-project] Organization lookup warning:', orgErr.message)
      }

      if (!targetOrgId || targetOrgId === 'personal') {
        return res.status(400).json({ error: 'No valid Supabase organization found. Please ensure your Supabase account has an organization.' })
      }

      console.log(`[oauth-create-project] Creating project "${projectName || 'SwapnoPay Merchant Store'}" in organization ${targetOrgId}...`)

      const resp = await fetch('https://api.supabase.com/v1/projects', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: projectName || 'SwapnoPay Merchant Store',
          organization_id: targetOrgId,
          db_pass: defaultPass,
          region: 'ap-southeast-1', // Singapore (fastest for Bangladesh & South Asia)
        }),
      })

      const projData = await resp.json().catch(() => ({}))
      if (!resp.ok) {
        const errMsg = projData.message || projData.error || projData.msg || projData.error_description || (typeof projData === 'string' ? projData : `HTTP Error ${resp.status}`)
        console.error('[oauth-create-project] Project creation failed:', resp.status, projData)
        return res.status(resp.status).json({ error: `Supabase Project Creation Error: ${errMsg}` })
      }

      // Immediately persist the generated db_password and project_ref by default
      saveDefaultProjectCredentials({
        userId,
        projectRef: projData.id,
        accessToken,
        dbPassword: defaultPass,
      }).catch(() => {})

      return res.json({ project_ref: projData.id, status: 'COMING_UP', db_password: defaultPass })
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
      const provisionResult = await provisionProject({ projectRef, accessToken, userId, dbPassword })
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
async function handleSocialLogin(req, res) {
  return res.status(400).json({ error: 'Use platform Supabase OAuth. Email and name alone are not authentication.' })
}

async function handleCheckUser(req, res) {
  try {
    const email = req.platformUser.email
    const merchantId = req.platformUser.id

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
        db_password: lookup.database.db_password || '',
        database_url: lookup.database.database_url || '',
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
      project_ref,
      db_password,
      database_url,
    } = req.body || {}

    const user = req.platformUser
    const admin = getAdminClient()
    const lookup = await lookupMerchantInAdminDb(user.email, user.id)
    const targetId = lookup.merchantId
    if (!business_name?.trim() || !phone?.trim()) {
      return res.status(400).json({ error: 'Business name and phone are required' })
    }

    const inferredRef = project_ref || (supabase_url && supabase_url.includes('.supabase.co')
      ? supabase_url.replace(/^https?:\/\//i, '').split('.supabase.co')[0].trim()
      : '')
    const effectiveDbPassword = db_password || (inferredRef ? getDefaultProjectDbPassword(inferredRef) : '')
    const effectiveDatabaseUrl = database_url || (inferredRef && effectiveDbPassword
      ? `postgresql://${encodeURIComponent(`postgres.${inferredRef}`)}:${encodeURIComponent(effectiveDbPassword)}@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require`
      : '')

    let saved = null
    try {
      saved = requireData(await admin.rpc('save_platform_merchant_setup', {
        p_user_id: user.id, p_merchant_id: targetId,
        p_profile: { email: user.email, business_name: business_name.trim(), phone: phone.trim(),
          business_type, website, photo_url },
        p_database: { supabase_url, supabase_anon_key }
      }), 'Save merchant setup')
    } catch (rpcErr) {
      console.warn('[sync-merchant-setup] RPC failed, falling back to direct table update:', rpcErr.message)
      const profileData = {
        id: targetId,
        user_id: user.id,
        email: user.email,
        business_name: business_name.trim(),
        phone: phone.trim(),
        business_type: business_type || 'Retail Store',
        website: website || '',
        photo_url: photo_url || '',
        status: 'ACTIVE',
        onboarded_at: new Date().toISOString(),
        updated_at: new Date().toISOString()
      }
      if (supabase_url) profileData.supabase_url = supabase_url
      if (supabase_anon_key) profileData.supabase_anon_key = supabase_anon_key
      if (req.body?.pin_hash) {
        profileData.app_pin_hash = req.body.pin_hash
      }
      const { data: mData, error: mErr } = await admin
        .from('merchants')
        .upsert(profileData)
        .select()
        .single()
      if (mErr) console.warn('[sync-merchant-setup] Direct merchant upsert error:', mErr.message)
      saved = mData || profileData

      if (supabase_url && supabase_anon_key) {
        await admin.from('merchant_gateway_settings').upsert({
          merchant_id: targetId,
          supabase_url,
          supabase_anon_key,
          updated_at: new Date().toISOString()
        }).catch(() => {})
        await admin.from('supabase_connections').upsert({
          user_id: user.id,
          selected_project_ref: inferredRef || undefined,
          project_url: supabase_url,
          publishable_key: supabase_anon_key,
          updated_at: new Date().toISOString()
        }).catch(() => {})
      }
    }

    // Always persist to setMerchantGatewayConfig (memory + disk + merchants table) so anon_key & db_password are saved by default
    if (supabase_url || supabase_anon_key || effectiveDbPassword) {
      await setMerchantGatewayConfig(targetId, {
        merchant_name: business_name.trim(),
        merchant_logo_url: photo_url || undefined,
        supabase_url: supabase_url || undefined,
        supabase_anon_key: supabase_anon_key || undefined,
        db_password: effectiveDbPassword || undefined,
        database_url: effectiveDatabaseUrl || undefined,
        project_ref: inferredRef || undefined,
      }).catch(() => {})
    }

    if (!saved?.id) saved = { id: targetId }

    console.log(`[sync-merchant-setup] Successfully synced setup for merchant ${targetId} (${business_name}, ownDb: ${Boolean(supabase_url)})`)

    return res.json({
      ok: true,
      message: 'Merchant setup and own database credentials synced successfully',
      merchant_id: targetId,
      db_password: effectiveDbPassword,
      database_url: effectiveDatabaseUrl,
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
    const { user_id: userId, tx_id: txId, access_token: directToken, project_ref: projectRef, db_password: dbPassword } = req.body || {}
    const accessToken = await getValidAccessToken(userId, txId, directToken)

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
    const result = await provisionProject({ projectRef: targetRef, accessToken, userId, dbPassword })
    return res.json(result)
  } catch (err) {
    console.error('[oauth-bootstrap] Error:', err)
    return res.status(500).json({ error: err.message })
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 4. MOBILE / CLIENT OAUTH CODE EXCHANGE (/exchange or /oauth-exchange)
// ──────────────────────────────────────────────────────────────────────────────
async function handleOAuthExchange(req, res) {
  try {
    loadOAuthDiskCache()
    const code = req.body?.code || req.query?.code
    const codeVerifier = req.body?.code_verifier || req.query?.code_verifier || ''
    const rawState = req.body?.state || req.query?.state || ''
    const redirectUri = req.body?.redirect_uri || req.query?.redirect_uri || DEFAULT_REDIRECT_URI
    const userId = req.body?.user_id || req.query?.user_id || req.headers['x-merchant-id'] || 'user_default'

    if (!code) {
      return res.status(400).json({ error: 'Missing code parameter' })
    }

    let effectiveVerifier = codeVerifier ? String(codeVerifier).trim() : ''
    if (!effectiveVerifier && rawState) {
      const sh = hashState(String(rawState).trim())
      const cachedTx = oauthTxCache.get(sh)
      if (cachedTx?.pkce_verifier_encrypted) {
        effectiveVerifier = cachedTx.pkce_verifier_encrypted
      }
    }

    const { clientId, clientSecret } = getOAuthCredentials()
    const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString('base64')

    const tokenParams = new URLSearchParams()
    tokenParams.append('grant_type', 'authorization_code')
    tokenParams.append('client_id', clientId)
    if (clientSecret) {
      tokenParams.append('client_secret', clientSecret)
    }
    tokenParams.append('code', String(code).trim())
    tokenParams.append('redirect_uri', String(redirectUri).trim())
    if (effectiveVerifier) {
      tokenParams.append('code_verifier', effectiveVerifier)
    }

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
      console.error('[oauth-exchange] Supabase token error:', tokenData)
      return res.status(tokenResponse.status).json(tokenData)
    }

    const expiresAt = new Date(Date.now() + (tokenData.expires_in || 3600) * 1000).toISOString()
    const connRecord = {
      user_id: userId,
      encrypted_access_token: tokenData.access_token,
      encrypted_refresh_token: tokenData.refresh_token || '',
      access_token_expires_at: expiresAt,
      connection_status: 'CONNECTED',
      provisioning_status: 'ACCOUNT_CONNECTED',
      updated_at: new Date().toISOString(),
    }
    connectionsCache.set(userId, connRecord)
    connectionsCache.set('latest', connRecord)
    saveOAuthDiskCache()

    try {
      const admin = getAdminClient()
      await admin.from('supabase_connections').upsert(connRecord, { onConflict: 'user_id' })
    } catch (_) {}

    // Automatically discover project and save default db_password & anon_key on code exchange
    let autoCredentials = null
    try {
      const pRes = await fetch('https://api.supabase.com/v1/projects', {
        headers: { Authorization: `Bearer ${tokenData.access_token}` },
      })
      if (pRes.ok) {
        const projects = await pRes.json()
        if (Array.isArray(projects) && projects.length > 0) {
          const activeProj = projects.find(p => p.status === 'ACTIVE_HEALTHY' || p.status === 'READY') || projects[0]
          if (activeProj?.id) {
            autoCredentials = await saveDefaultProjectCredentials({
              userId: userId || 'user_default',
              projectRef: activeProj.id,
              accessToken: tokenData.access_token,
            })
          }
        }
      }
    } catch (autoErr) {
      console.warn('[oauth-exchange] Auto credential discovery notice:', autoErr.message)
    }

    return res.json({
      ...tokenData,
      auto_credentials: autoCredentials,
    })
  } catch (err) {
    console.error('[oauth-exchange] Exception:', err)
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

router.all('/exchange', handleOAuthExchange)
router.all('/oauth-exchange', handleOAuthExchange)

router.all('/projects', handleProjects)
router.all('/provision', handleProvision)
router.all('/bootstrap', handleBootstrap)
router.all('/auto-setup', handleBootstrap)

router.post('/check-user', requirePlatformUser, handleCheckUser)
router.post('/sync-merchant-setup', requirePlatformUser, handleSyncMerchantSetup)

router.post('/social-login', handleSocialLogin)
router.get('/social-login', (req, res) => res.status(405).json({ error: 'Use POST for social login' }))

export default router
