// SwapnoPay Backend — Main Server
// Express.js + Socket.io
// Bridges Admin Supabase (platform config) ↔ Merchant Supabase (payments) ↔ Web Widget

import 'dotenv/config'
import { createServer } from 'node:http'
import express from 'express'
import { Server as SocketIOServer } from 'socket.io'
import cors from 'cors'
import helmet from 'helmet'
import { rateLimit } from 'express-rate-limit'

import { initAdminSupabase } from './services/adminSupabase.js'
import { initMailer } from './services/mailer.js'
import { notifyWaitingCustomersMerchantOnline } from './services/deviceAlertService.js'
import { paymentRouter } from './routes/payment.js'
import { adminRouter } from './routes/admin.js'
import { keysRouter } from './routes/keys.js'
import { shopRouter } from './routes/shop.js'
import { kycRouter } from './routes/kyc.js'
import oauthRouter from './routes/oauth.js'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)


// ──────────────────────────────────────────────────────────────────────────────
// Validate required environment variables
// ──────────────────────────────────────────────────────────────────────────────
const REQUIRED_ENV = [
  'ADMIN_SUPABASE_URL',
  'ADMIN_SUPABASE_SERVICE_ROLE_KEY',
  'ADMIN_SUPABASE_ANON_KEY',
  'ADMIN_SECRET',
  'API_KEY_PEPPER',
  'PAYMENT_WEBHOOK_SECRET',
]
const missingEnv = REQUIRED_ENV.filter(k => !process.env[k])
if (missingEnv.length > 0) {
  console.error('[startup] ❌ Missing required environment variables:', missingEnv.join(', '))
  process.exit(1)
}
if ((process.env.ADMIN_SECRET || '').length < 32) {
  console.error('[startup] ❌ ADMIN_SECRET must be at least 32 characters')
  process.exit(1)
}
if ((process.env.API_KEY_PEPPER || '').length < 32) {
  console.error('[startup] ❌ API_KEY_PEPPER must be at least 32 characters')
  process.exit(1)
}

// ──────────────────────────────────────────────────────────────────────────────
// Initialise Admin Supabase (platform owner's database)
// ──────────────────────────────────────────────────────────────────────────────
try {
  initAdminSupabase()
} catch (err) {
  console.error('[startup] ❌ Admin Supabase initialisation failed:', err.message)
  console.error('[startup]    Set ADMIN_SUPABASE_URL and ADMIN_SUPABASE_SERVICE_ROLE_KEY')
  process.exit(1)
}

// ──────────────────────────────────────────────────────────────────────────────
// Initialise Email Mailer (Nodemailer + Gmail OAuth2) — Non-fatal
// ──────────────────────────────────────────────────────────────────────────────
try {
  initMailer()
} catch (err) {
  console.warn('[startup] ⚠️  Mailer initialisation warning:', err.message)
}

// ──────────────────────────────────────────────────────────────────────────────
// Parse allowed CORS origins and enforce safe defaults
// ──────────────────────────────────────────────────────────────────────────────
const allowedOriginsEnv = (process.env.ALLOWED_ORIGINS || '').trim()
const allowedOrigins = allowedOriginsEnv ? allowedOriginsEnv.split(',').map(s => s.trim()).filter(Boolean) : []

if (allowedOrigins.length === 0) {
  if (process.env.NODE_ENV === 'production') {
    console.error('[startup] ❌ ALLOWED_ORIGINS must be set in production (comma-separated list)')
    process.exit(1)
  }
  console.warn('[startup] ⚠️  ALLOWED_ORIGINS not set — using safe localhost defaults for development')
  // safe development defaults (can be overridden by ALLOWED_ORIGINS)
  allowedOrigins.push('http://localhost:5173', 'http://127.0.0.1:5173')
}

const corsOptions = {
  origin: (origin, cb) => {
    // Allow non-browser requests (curl, server-to-server) which have no Origin header
    if (!origin) return cb(null, true)
    if (allowedOrigins.includes(origin)) return cb(null, true)
    cb(new Error(`CORS: origin ${origin} is not allowed`))
  },
  credentials: true,
  methods: ['GET', 'POST', 'PATCH', 'OPTIONS'],
}

// ──────────────────────────────────────────────────────────────────────────────
// Express App
// ──────────────────────────────────────────────────────────────────────────────
const app = express()
const httpServer = createServer(app)

app.use(helmet({ contentSecurityPolicy: false }))
app.set('trust proxy', Number(process.env.TRUST_PROXY_HOPS || 1))
app.disable('x-powered-by')

app.use(cors(corsOptions))
app.options('*', cors(corsOptions))

// Serve uploads statically
app.use('/uploads', express.static(path.join(__dirname, '../uploads')))

// Parse JSON body and capture rawBody for HMAC verification on /v1/payment/verify
app.use(express.json({
  limit: '35mb',
  strict: true,
  verify: (req, _res, buf) => {
    req.rawBody = buf.toString('utf8')
  },
}))

// Global rate limiting
app.use(rateLimit({
  windowMs: 60_000,
  limit: 200,
  standardHeaders: 'draft-8',
  legacyHeaders: false,
  message: { error: 'Too many requests — please slow down' },
}))

// Stricter limit on payment/verify (webhook)
const webhookLimiter = rateLimit({
  windowMs: 60_000,
  limit: 60,
  message: { error: 'Webhook rate limit exceeded' },
})

// ──────────────────────────────────────────────────────────────────────────────
// In-Memory Merchant Heartbeat Map
// Tracks which merchant devices are live via Socket.io (O(1) device check)
// Structure: merchantId → { ts: number, socketId: string, deviceId: string|null }
// ──────────────────────────────────────────────────────────────────────────────
export const merchantHeartbeatMap = new Map()

const HEARTBEAT_GRACE_MS = 3 * 60 * 1000   // 3 minutes — device considered stale
const CLEANUP_INTERVAL_MS = 5 * 60 * 1000  // Clean up stale entries every 5 minutes

setInterval(() => {
  const now = Date.now()
  let cleaned = 0
  for (const [merchantId, hb] of merchantHeartbeatMap.entries()) {
    if (now - hb.ts > HEARTBEAT_GRACE_MS * 2) {
      merchantHeartbeatMap.delete(merchantId)
      cleaned++
    }
  }
  if (cleaned > 0) console.log(`[heartbeat] Cleaned ${cleaned} stale merchant heartbeat(s)`)
}, CLEANUP_INTERVAL_MS)

// ──────────────────────────────────────────────────────────────────────────────
// Socket.io
// ──────────────────────────────────────────────────────────────────────────────
const io = new SocketIOServer(httpServer, {
  cors: corsOptions,
  transports: ['websocket', 'polling'],
  pingTimeout: 30000,
  pingInterval: 15000,   // 15-second ping for fast disconnection detection
  upgradeTimeout: 10000,
  maxHttpBufferSize: 1e6,
})

io.on('connection', (socket) => {
  console.log(`[socket.io] Client connected: ${socket.id}`)

  // ── Widget joins a room for a specific order ──
  socket.on('join_order', ({ order_id } = {}) => {
    if (!order_id || typeof order_id !== 'string' || order_id.length > 100) return
    const room = `order:${order_id}`
    socket.join(room)
    console.log(`[socket.io] ${socket.id} joined order room: ${room}`)
    socket.emit('room_joined', { room, order_id, ts: Date.now() })
  })

  // ── Android merchant app joins a merchant room + marks as online ──
  socket.on('join_merchant', ({ merchant_id, device_id } = {}) => {
    if (!merchant_id || typeof merchant_id !== 'string' || merchant_id.length > 100) return
    const room = `merchant:${merchant_id}`
    socket.join(room)

    // Track merchant as live in heartbeat map
    merchantHeartbeatMap.set(merchant_id, {
      ts: Date.now(),
      socketId: socket.id,
      deviceId: device_id || null,
    })

    console.log(`[socket.io] ${socket.id} joined merchant room: ${room} | device: ${device_id || 'unknown'}`)
    socket.emit('room_joined', { room, merchant_id, ts: Date.now() })

    // Notify any waiting customers that this merchant is now online
    notifyWaitingCustomersMerchantOnline(merchant_id)
      .catch(e => console.warn('[socket.io] Alert notify error:', e.message))

    // Store merchant_id on socket for cleanup on disconnect
    socket.data.merchant_id = merchant_id
    socket.data.device_id = device_id || null
  })

  // ── Merchant device heartbeat — keeps device_active=true in memory ──
  socket.on('merchant_heartbeat', ({ merchant_id, device_id } = {}) => {
    if (!merchant_id || typeof merchant_id !== 'string') return
    merchantHeartbeatMap.set(merchant_id, {
      ts: Date.now(),
      socketId: socket.id,
      deviceId: device_id || socket.data.device_id || null,
    })
    // Acknowledge heartbeat with server timestamp
    socket.emit('heartbeat_ack', { ts: Date.now(), merchant_id })

    // Notify any waiting customers that this merchant is online
    notifyWaitingCustomersMerchantOnline(merchant_id)
      .catch(e => console.warn('[socket.io] Alert notify error:', e.message))
  })

  // ── Widget pings backend to confirm connection is alive ──
  socket.on('ping_backend', (cb) => {
    if (typeof cb === 'function') cb({ ts: Date.now(), ok: true })
    else socket.emit('pong_backend', { ts: Date.now(), ok: true })
  })

  // ── Client queries order status over WebSocket directly ──
  socket.on('order_lookup', async ({ order_id, merchant_id } = {}, cb) => {
    if (!order_id) return
    try {
      const { getOrderFromMerchantDB } = await import('./services/adminSupabase.js')
      const order = await getOrderFromMerchantDB(merchant_id, order_id)
      const resPayload = order ? { ok: true, order } : { ok: false, error: 'Order not found' }
      if (typeof cb === 'function') cb(resPayload)
      else socket.emit('order_lookup_res', resPayload)
    } catch (err) {
      if (typeof cb === 'function') cb({ ok: false, error: err.message })
    }
  })

  // ── Android device measures round-trip latency ──
  socket.on('merchant_ping', ({ client_ts } = {}, cb) => {
    const server_ts = Date.now()
    if (typeof cb === 'function') cb({ ok: true, client_ts, server_ts })
    else socket.emit('merchant_pong', { ok: true, client_ts, server_ts })
  })

  socket.on('disconnect', reason => {
    console.log(`[socket.io] Client disconnected: ${socket.id} — reason: ${reason}`)

    // Mark merchant offline after grace period if it was the last socket
    const merchant_id = socket.data.merchant_id
    if (merchant_id) {
      // Give 30s grace before marking offline (handles page refreshes / brief reconnects)
      setTimeout(() => {
        const hb = merchantHeartbeatMap.get(merchant_id)
        if (hb && hb.socketId === socket.id) {
          // No new socket reconnected for this merchant — mark stale
          merchantHeartbeatMap.delete(merchant_id)
          console.log(`[heartbeat] Merchant ${merchant_id} marked offline after disconnect`)
        }
      }, 30_000)
    }
  })

  socket.on('error', err => {
    console.error(`[socket.io] Socket error: ${socket.id}:`, err.message)
  })
})

// ──────────────────────────────────────────────────────────────────────────────
// Routes
// ──────────────────────────────────────────────────────────────────────────────

// Attach io and heartbeatMap to request object for route handlers
app.use((req, _res, next) => {
  req.io = io
  req.heartbeatMap = merchantHeartbeatMap
  next()
})

// Public health check
app.get('/healthz', (_req, res) => {
  res.json({
    ok: true,
    service: 'swapnopay-backend',
    version: '3.0.0',
    database: 'admin-supabase',
    socket_io_clients: io.engine.clientsCount,
    merchant_online_count: merchantHeartbeatMap.size,
    timestamp: new Date().toISOString(),
  })
})

// Payment routes (pass both io + heartbeatMap)
app.use('/v1/payment', webhookLimiter, paymentRouter(io, merchantHeartbeatMap))

// Public Showcase config route
app.get('/v1/showcase', async (_req, res) => {
  try {
    const { getShowcaseConfig } = await import('./services/adminSupabase.js')
    const config = await getShowcaseConfig()
    res.json({ ok: true, config: config || {} })
  } catch (err) {
    res.json({ ok: true, config: {} })
  }
})

// Admin routes (protected by ADMIN_SECRET)
app.use('/v1/admin', adminRouter)
app.use('/v1/admin/keys', keysRouter)

// Web Shop & Launch Website routes
app.use('/v1/shop', shopRouter)

// Merchant & Admin KYC verification routes
app.use('/v1/kyc', kycRouter)
app.use('/v1/admin/kyc', kycRouter)

// Supabase OAuth Control Plane (Native VPS backend endpoints)
app.use('/v1/oauth', oauthRouter)
app.use('/functions/v1', oauthRouter) // Compatibility alias for /functions/v1/oauth-callback, /functions/v1/oauth-start, etc.
app.use('/oauth', oauthRouter) // Direct root alias for /oauth/callback

// 404
app.use((_req, res) => {
  res.status(404).json({ error: 'Not found' })
})

// Error handler
app.use((err, _req, res, _next) => {
  console.error('[express] Unhandled error:', err.message)
  res.status(err.status || 500).json({ error: err.message || 'Internal server error' })
})

// ──────────────────────────────────────────────────────────────────────────────
// Start Server
// ──────────────────────────────────────────────────────────────────────────────
const PORT = Number(process.env.PORT || 4000)
httpServer.listen(PORT, '0.0.0.0', () => {
  console.log(`
╔══════════════════════════════════════════════════════════════════╗
║       SwapnoPay Backend v3 — Production Mode                     ║
╠══════════════════════════════════════════════════════════════════╣
║  Admin DB     → Admin Supabase (platform owner DB)               ║
║  Merchant DB  → Per-merchant Supabase (cross-DB bridge)          ║
║  HTTP/WS      → http://0.0.0.0:${PORT}                            ║
║  Health       → GET  /healthz                                    ║
║  Config       → GET  /v1/payment/config?merchant_id=<id>         ║
║  Device Check → GET  /v1/payment/device-status?merchant_id=<id>  ║
║  Notify       → POST /v1/payment/notify                          ║
║  Verify       → POST /v1/payment/verify  (webhook secret)        ║
║  Admin        → GET/POST /v1/admin/gateway-settings              ║
║  API Keys     → GET/POST /v1/admin/keys                          ║
╚══════════════════════════════════════════════════════════════════╝
`)
})

// Graceful shutdown
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    console.log(`[shutdown] Received ${signal} — closing server`)
    httpServer.close(() => {
      console.log('[shutdown] HTTP server closed')
      process.exit(0)
    })
  })
}
