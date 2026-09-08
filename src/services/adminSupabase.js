// SwapnoPay Backend — Admin Supabase Service
// Connects to the PLATFORM OWNER'S Supabase database (NOT merchant databases).
// Handles: gateway config, API key management, payment event recording,
//          merchant profile (with logo), cross-DB device status, cross-DB order updates.

import { createClient } from '@supabase/supabase-js'

// ──────────────────────────────────────────────────────────────────────────────
// Singleton admin client (service role — full RLS bypass)
// ──────────────────────────────────────────────────────────────────────────────
let _adminClient = null

export function initAdminSupabase() {
  const url = process.env.ADMIN_SUPABASE_URL
  const serviceKey = process.env.ADMIN_SUPABASE_SERVICE_ROLE_KEY

  if (!url || !serviceKey) {
    throw new Error(
      'ADMIN_SUPABASE_URL and ADMIN_SUPABASE_SERVICE_ROLE_KEY are required.'
    )
  }

  _adminClient = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  })

  console.log('[admin-supabase] Connected to admin database:', url)
  return _adminClient
}

export function getAdminClient() {
  if (!_adminClient) {
    const url = process.env.ADMIN_SUPABASE_URL || process.env.SUPABASE_URL
    const serviceKey = process.env.ADMIN_SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_KEY
    if (url && serviceKey) {
      _adminClient = createClient(url, serviceKey, {
        auth: { persistSession: false, autoRefreshToken: false },
      })
      return _adminClient
    }
    throw new Error('Admin Supabase not initialised — call initAdminSupabase() first')
  }
  return _adminClient
}

export const getAdminSupabase = getAdminClient

// ──────────────────────────────────────────────────────────────────────────────
// Merchant Supabase client factory (short-lived, per-request)
// Uses credentials stored in admin DB merchant_gateway_settings
// ──────────────────────────────────────────────────────────────────────────────
function createMerchantClient(supabaseUrl, supabaseAnonKey) {
  if (!supabaseUrl || !supabaseAnonKey) {
    throw new Error('Merchant Supabase credentials not configured')
  }
  // Validate URL format for safety
  const url = new URL(supabaseUrl) // throws on invalid URL
  if (!url.hostname.endsWith('.supabase.co') && !url.hostname.endsWith('.supabase.in')) {
    // Allow custom Supabase-compatible self-hosted URLs too
    console.warn('[merchant-db] Non-standard Supabase host:', url.hostname)
  }
  return createClient(supabaseUrl, supabaseAnonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
}

// ──────────────────────────────────────────────────────────────────────────────
// Get stored merchant credentials from admin DB
// ──────────────────────────────────────────────────────────────────────────────
async function getMerchantCredentials(merchantId) {
  if (!merchantId) return null
  const admin = getAdminClient()
  const { data, error } = await admin
    .from('merchant_gateway_settings')
    .select('supabase_url, supabase_anon_key, merchant_name, merchant_logo_url')
    .eq('merchant_id', merchantId)
    .maybeSingle()

  if (data?.supabase_url) return data

  // Fallback: check supabase_connections from control plane
  try {
    const { data: conn } = await admin
      .from('supabase_connections')
      .select('project_url, publishable_key')
      .eq('user_id', merchantId)
      .maybeSingle()

    if (conn?.project_url) {
      return {
        supabase_url: conn.project_url,
        supabase_anon_key: conn.publishable_key,
        merchant_name: null,
        merchant_logo_url: null,
      }
    }
  } catch {
    // Ignore fallback failure
  }

  return null
}

// ──────────────────────────────────────────────────────────────────────────────
// Check merchant device active status via MERCHANT's own Supabase DB
// Returns: { active: boolean, last_seen: string|null, device_count: number }
// ──────────────────────────────────────────────────────────────────────────────
export async function getMerchantDeviceStatus(merchantId, heartbeatMap = null) {
  if (!merchantId) return { active: null, last_seen: null, device_count: 0 }

  // Fast path: check in-memory heartbeat map first (O(1) — populated by Socket.io)
  if (heartbeatMap && heartbeatMap.has(merchantId)) {
    const hb = heartbeatMap.get(merchantId)
    const ageMs = Date.now() - hb.ts
    if (ageMs < 180_000) { // < 3 minutes = live
      return { active: true, last_seen: new Date(hb.ts).toISOString(), device_count: 1, source: 'heartbeat' }
    }
  }

  // Slow path: query merchant's Supabase devices table
  try {
    const creds = await getMerchantCredentials(merchantId)
    if (!creds?.supabase_url || !creds?.supabase_anon_key) {
      // No merchant DB configured — skip check, allow payment
      return { active: null, last_seen: null, device_count: 0, source: 'unconfigured' }
    }

    const merchantClient = createMerchantClient(creds.supabase_url, creds.supabase_anon_key)

    const { data: devices, error } = await merchantClient
      .from('devices')
      .select('id, online, last_sync, disabled, created_at')
      .eq('merchant_id', merchantId)
      .eq('disabled', false)

    if (error) {
      console.warn(`[device-status] Merchant DB query failed for ${merchantId}:`, error.message)
      return { active: null, last_seen: null, device_count: 0, source: 'db_error' }
    }

    if (!devices || devices.length === 0) {
      return { active: false, last_seen: null, device_count: 0, source: 'no_devices' }
    }

    // Check if any device is online and sync'd within last 3 minutes
    const THREE_MIN = 3 * 60 * 1000
    const now = Date.now()
    let lastSeen = null

    const hasActiveDevice = devices.some(d => {
      const syncTime = d.last_sync || d.created_at
      const syncTs = new Date(syncTime).getTime()
      const isRecent = (now - syncTs) < THREE_MIN
      if (isRecent && (!lastSeen || syncTs > new Date(lastSeen).getTime())) {
        lastSeen = syncTime
      }
      return d.online === true && isRecent
    })

    return {
      active: hasActiveDevice,
      last_seen: lastSeen,
      device_count: devices.length,
      source: 'merchant_db',
    }
  } catch (err) {
    console.error(`[device-status] Error checking merchant ${merchantId}:`, err.message)
    return { active: null, last_seen: null, device_count: 0, source: 'error' }
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// ──────────────────────────────────────────────────────────────────────────────
// Cross-DB: Read order details from MERCHANT's Supabase DB
// Used by /v1/payment/verify and /v1/payment/order/:order_id
// Supports lookup by UUID id or string tran_id
// ──────────────────────────────────────────────────────────────────────────────
export async function getOrderFromMerchantDB(merchantId, orderIdOrTranId) {
  if (!orderIdOrTranId) return null

  try {
    const creds = await getMerchantCredentials(merchantId)
    if (creds?.supabase_url && creds?.supabase_anon_key) {
      const merchantClient = createMerchantClient(creds.supabase_url, creds.supabase_anon_key)
      const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(orderIdOrTranId)

      let query = merchantClient
        .from('orders')
        .select('id, tran_id, amount, status, cus_name, cus_phone, cus_email, product_name, payment_method, matched_trx_id, sender_number, paid_at, expires_at, success_url, fail_url, cancel_url, created_at')

      if (isUuid) {
        query = query.or(`id.eq.${orderIdOrTranId},tran_id.eq.${orderIdOrTranId}`)
      } else {
        query = query.eq('tran_id', orderIdOrTranId)
      }

      const { data, error } = await query.maybeSingle()

      if (!error && data) {
        return data
      }
      if (error) {
        console.warn(`[merchant-db] getOrder warning for ${orderIdOrTranId}:`, error.message)
      }
    }
  } catch (err) {
    console.error(`[merchant-db] getOrderFromMerchantDB failed:`, err.message)
  }

  // Fallback: check admin DB payment_events
  try {
    const admin = getAdminClient()
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(orderIdOrTranId)
    let q = admin.from('payment_events').select('*')
    if (isUuid) {
      q = q.or(`order_id.eq.${orderIdOrTranId},tran_id.eq.${orderIdOrTranId}`)
    } else {
      q = q.eq('tran_id', orderIdOrTranId)
    }
    const { data: eventData } = await q.order('recorded_at', { ascending: false }).limit(1).maybeSingle()
    if (eventData) {
      return {
        id: eventData.order_id,
        tran_id: eventData.tran_id,
        amount: eventData.amount,
        status: eventData.status,
        cus_name: eventData.cus_name,
        cus_phone: eventData.sender_number,
        cus_email: eventData.cus_email,
        product_name: eventData.product_name,
        payment_method: eventData.payment_method,
        matched_trx_id: eventData.trx_id,
        sender_number: eventData.sender_number,
        paid_at: eventData.payment_time,
        created_at: eventData.recorded_at,
      }
    }
  } catch {}

  return null
}

// ──────────────────────────────────────────────────────────────────────────────
// Cross-DB: Update order status on MERCHANT's Supabase DB
// Called by /v1/payment/verify, /v1/payment/cancel, or appeal resolutions
// ──────────────────────────────────────────────────────────────────────────────
export async function updateOrderStatusOnMerchantDB(merchantId, orderId, status, trxData = {}) {
  try {
    const creds = await getMerchantCredentials(merchantId)
    if (!creds?.supabase_url || !creds?.supabase_anon_key) {
      console.warn(`[merchant-db] No credentials for merchant ${merchantId}, skipping order update`)
      return false
    }

    const merchantClient = createMerchantClient(creds.supabase_url, creds.supabase_anon_key)

    // Normalize status to match schema check constraint: ('PENDING','PAID','EXPIRED','CANCELLED')
    let dbStatus = status
    if (dbStatus === 'FAILED') dbStatus = 'CANCELLED'

    const updatePayload = {
      status: dbStatus,
    }
    if (dbStatus === 'PAID') {
      updatePayload.paid_at = trxData.payment_time ? new Date(trxData.payment_time).toISOString() : new Date().toISOString()
    }
    if (trxData.matched_trx_id) updatePayload.matched_trx_id = String(trxData.matched_trx_id)
    if (trxData.tran_id)        updatePayload.tran_id        = String(trxData.tran_id)
    if (trxData.payment_method) updatePayload.payment_method = String(trxData.payment_method)
    if (trxData.sender_number)  updatePayload.sender_number  = String(trxData.sender_number)

    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(orderId)
    let query = merchantClient.from('orders').update(updatePayload)
    if (isUuid) {
      query = query.eq('id', orderId)
    } else {
      query = query.eq('tran_id', orderId)
    }

    const { error } = await query

    if (error) {
      console.error(`[merchant-db] Failed to update order ${orderId} to ${status}:`, error.message)
      return false
    }

    console.log(`[merchant-db] ✅ Order ${orderId} updated to ${status} on merchant ${merchantId} DB`)
    return true
  } catch (err) {
    console.error(`[merchant-db] updateOrderStatusOnMerchantDB failed:`, err.message)
    return false
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Gateway Configuration (singleton row id=00000000-0000-0000-0000-000000000001)
// ──────────────────────────────────────────────────────────────────────────────
const CONFIG_ID = '00000000-0000-0000-0000-000000000001'

/**
 * Read the full gateway configuration from admin Supabase.
 */
export async function getGatewayConfig() {
  const { data, error } = await getAdminClient()
    .from('gateway_config')
    .select('*')
    .eq('id', CONFIG_ID)
    .single()

  if (error) {
    console.error('[admin-supabase] getGatewayConfig error:', error.message)
    return getDefaultGatewayConfig()
  }

  return normaliseConfig(data)
}

/**
 * Write/merge gateway configuration to admin Supabase.
 */
export async function setGatewayConfig(config) {
  const row = denormaliseConfig(config)
  row.updated_at = new Date().toISOString()

  const { data, error } = await getAdminClient()
    .from('gateway_config')
    .update(row)
    .eq('id', CONFIG_ID)
    .select('*')
    .single()

  if (error) throw new Error('Failed to save gateway config: ' + error.message)
  return normaliseConfig(data)
}

// ──────────────────────────────────────────────────────────────────────────────
// Merchant-Specific Gateway Settings (with device check + logo)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Fetch and dynamically merge platform gateway config with merchant-specific choices.
 * Also performs a cross-DB device status check on the merchant's own Supabase.
 * @param {string} merchantId
 * @param {Map} heartbeatMap — in-memory Socket.io heartbeat map (optional)
 */
export async function getMerchantGatewayConfig(merchantId, heartbeatMap = null) {
  const globalConfig = await getGatewayConfig()
  if (!merchantId) return { ...globalConfig, device_active: null, merchant_logo_url: null }

  const { data: merchantRow } = await getAdminClient()
    .from('merchant_gateway_settings')
    .select('*')
    .eq('merchant_id', merchantId)
    .maybeSingle()

  // Device status check — queries merchant's Supabase DB
  let deviceStatus = { active: null, last_seen: null, device_count: 0 }
  try {
    deviceStatus = await getMerchantDeviceStatus(merchantId, heartbeatMap)
  } catch (e) {
    console.warn('[gateway-config] Device status check failed:', e.message)
  }

  if (!merchantRow) {
    return {
      ...globalConfig,
      device_active: deviceStatus.active,
      device_last_seen: deviceStatus.last_seen,
      merchant_logo_url: null,
    }
  }

  return {
    ...globalConfig,
    enabled_methods: {
      bKash:  globalConfig.enabled_methods.bKash  && (merchantRow.bkash_enabled  ?? true),
      Nagad:  globalConfig.enabled_methods.Nagad  && (merchantRow.nagad_enabled  ?? true),
      Rocket: globalConfig.enabled_methods.Rocket && (merchantRow.rocket_enabled ?? true),
      Upay:   globalConfig.enabled_methods.Upay   && (merchantRow.upay_enabled   ?? true),
    },
    default_success_url:  merchantRow.success_url || globalConfig.default_success_url,
    default_fail_url:     merchantRow.fail_url    || globalConfig.default_fail_url,
    default_cancel_url:   merchantRow.cancel_url  || globalConfig.default_cancel_url,
    receiving_numbers:    merchantRow.receiving_numbers || {},
    qr_codes:             merchantRow.qr_codes || {},
    auto_appeal_matching: merchantRow.auto_appeal_matching ?? false,
    merchant_customized:  true,
    merchant_logo_url:    merchantRow.merchant_logo_url || null,
    merchant_name:        merchantRow.merchant_name || null,
    supabase_url:         merchantRow.supabase_url || null,
    supabase_anon_key:    merchantRow.supabase_anon_key || null,
    // Device status from merchant's own DB
    device_active:        deviceStatus.active,
    device_last_seen:     deviceStatus.last_seen,
    device_count:         deviceStatus.device_count,
    device_source:        deviceStatus.source,
  }
}

/**
 * Save custom gateway configuration for a specific merchant in admin Supabase.
 */
export async function setMerchantGatewayConfig(merchantId, settings) {
  if (!merchantId) throw new Error('merchant_id is required')

  const row = {
    merchant_id:          merchantId,
    bkash_enabled:        settings.bkash_enabled ?? true,
    nagad_enabled:        settings.nagad_enabled ?? true,
    rocket_enabled:       settings.rocket_enabled ?? true,
    upay_enabled:         settings.upay_enabled ?? true,
    success_url:          settings.success_url || null,
    fail_url:             settings.fail_url || null,
    cancel_url:           settings.cancel_url || null,
    receiving_numbers:    settings.receiving_numbers || {},
    qr_codes:             settings.qr_codes || {},
    auto_appeal_matching: settings.auto_appeal_matching ?? false,
    updated_at:           new Date().toISOString(),
  }

  const { data, error } = await getAdminClient()
    .from('merchant_gateway_settings')
    .upsert(row, { onConflict: 'merchant_id' })
    .select('*')
    .single()

  if (error) throw new Error('Failed to save merchant gateway config: ' + error.message)
  return data
}

// ──────────────────────────────────────────────────────────────────────────────
// Platform API Keys
// ──────────────────────────────────────────────────────────────────────────────

export async function storeApiKeyRecord(record) {
  const { data, error } = await getAdminClient()
    .from('platform_api_keys')
    .insert({
      id:            record.id,
      merchant_id:   record.merchant_id,
      merchant_name: record.merchant_name,
      label:         record.label,
      key_digest:    record.digest,
      key_preview:   record.key_preview,
      revoked:       false,
    })
    .select('*')
    .single()

  if (error) throw new Error('Failed to store API key: ' + error.message)
  return data
}

export async function revokeApiKeyRecord(keyId) {
  const { error } = await getAdminClient()
    .from('platform_api_keys')
    .update({ revoked: true, revoked_at: new Date().toISOString() })
    .eq('id', keyId)

  if (error) throw new Error('Failed to revoke API key: ' + error.message)
}

export async function listApiKeyRecords() {
  const { data, error } = await getAdminClient()
    .from('platform_api_keys')
    .select('id,merchant_id,merchant_name,label,key_preview,revoked,revoked_at,created_at')
    .order('created_at', { ascending: false })

  if (error) throw new Error('Failed to list API keys: ' + error.message)
  return data || []
}

export async function validateApiKey(digest) {
  const { data, error } = await getAdminClient()
    .from('platform_api_keys')
    .select('id,merchant_id,merchant_name,label,key_preview,revoked')
    .eq('key_digest', digest)
    .single()

  if (error || !data) return null
  if (data.revoked) return null
  return data
}

// ──────────────────────────────────────────────────────────────────────────────
// Payment Events (Platform Analytics)
// ──────────────────────────────────────────────────────────────────────────────

export async function recordPaymentEvent(orderId, eventData) {
  try {
    const admin = getAdminClient()
    const { error } = await admin
      .from('payment_events')
      .insert({
        order_id:       orderId,
        tran_id:        eventData.tran_id || null,
        trx_id:         eventData.trx_id || null,
        status:         eventData.status || 'PAID',
        amount:         eventData.amount || null,
        currency:       eventData.currency || 'BDT',
        payment_method: eventData.payment_method || null,
        sender_number:  eventData.sender_number || null,
        payment_time:   eventData.payment_time
          ? new Date(eventData.payment_time).toISOString()
          : new Date().toISOString(),
        merchant_id:    eventData.merchant_id || null,
        merchant_name:  eventData.merchant_name || null,
        project_ref:    eventData.project_ref || null,
        cus_name:       eventData.cus_name || null,
        cus_email:      eventData.cus_email || null,
        product_name:   eventData.product_name || null,
      })

    if (error) {
      console.error('[admin-supabase] recordPaymentEvent notice:', error.message)
    }
  } catch (err) {
    console.warn('[admin-supabase] recordPaymentEvent skipped:', err.message)
  }
}

export async function listPaymentEvents({ limit = 50, status = null, merchantId = null } = {}) {
  let q = getAdminClient()
    .from('payment_events')
    .select('*')
    .order('recorded_at', { ascending: false })
    .limit(Math.min(limit, 200))

  if (status) q = q.eq('status', status)
  if (merchantId) q = q.eq('merchant_id', merchantId)

  const { data, error } = await q
  if (error) throw new Error('Failed to fetch payment events: ' + error.message)
  return data || []
}

/**
 * Aggregated platform statistics with exact counts and revenue calculation
 */
export async function getPaymentStats() {
  const admin = getAdminClient()

  const [paidCountRes, failedCountRes, cancelledCountRes, recentRes, paidAmountsRes] = await Promise.all([
    admin.from('payment_events').select('*', { count: 'exact', head: true }).eq('status', 'PAID'),
    admin.from('payment_events').select('*', { count: 'exact', head: true }).eq('status', 'FAILED'),
    admin.from('payment_events').select('*', { count: 'exact', head: true }).eq('status', 'CANCELLED'),
    admin.from('payment_events').select('*').order('recorded_at', { ascending: false }).limit(10),
    admin.from('payment_events').select('amount').eq('status', 'PAID'),
  ])

  const totalPaid = paidCountRes.count ?? 0
  const totalFailed = failedCountRes.count ?? 0
  const totalCancelled = cancelledCountRes.count ?? 0
  const recentEvents = recentRes.data || []
  const totalRevenue = (paidAmountsRes.data || []).reduce((sum, e) => sum + Number(e.amount || 0), 0)

  return {
    total_paid: totalPaid,
    total_failed: totalFailed,
    total_cancelled: totalCancelled,
    total_revenue_bdt: totalRevenue.toFixed(2),
    recent_events: recentEvents,
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Health Check
// ──────────────────────────────────────────────────────────────────────────────
export async function pingAdminDatabase() {
  try {
    const { error } = await getAdminClient()
      .from('gateway_config')
      .select('id')
      .eq('id', CONFIG_ID)
      .single()
    return !error
  } catch {
    return false
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Config normalisation helpers
// ──────────────────────────────────────────────────────────────────────────────

function normaliseConfig(row) {
  if (!row) return getDefaultGatewayConfig()
  return {
    id: row.id,
    enabled_methods: {
      bKash:  row.bkash_enabled  ?? true,
      Nagad:  row.nagad_enabled  ?? true,
      Rocket: row.rocket_enabled ?? true,
      Upay:   row.upay_enabled   ?? true,
    },
    default_success_url:  row.default_success_url  || 'https://pay.swapnopay.top/success',
    default_fail_url:     row.default_fail_url     || 'https://pay.swapnopay.top/failed',
    default_cancel_url:   row.default_cancel_url   || 'https://pay.swapnopay.top/cancelled',
    min_amount:           Number(row.min_amount)   || 10,
    max_amount:           Number(row.max_amount)   || 500000,
    daily_limit_per_merchant: Number(row.daily_limit_per_merchant) || 10000000,
    payment_timeout_seconds:    Number(row.payment_timeout_seconds)    || 600,
    processing_timeout_seconds: Number(row.processing_timeout_seconds) || 300,
    gateway_fee_percent: Number(row.gateway_fee_percent) || 0,
    gateway_fee_fixed:   Number(row.gateway_fee_fixed)   || 0,
    customer_receipts_enabled: row.customer_receipts_enabled ?? true,
    merchant_receipts_enabled: row.merchant_receipts_enabled ?? true,
    maintenance_mode:    row.maintenance_mode    ?? false,
    maintenance_message: row.maintenance_message || '',
    updated_at: row.updated_at,
  }
}

function denormaliseConfig(config) {
  const row = {}
  if (config.enabled_methods) {
    row.bkash_enabled  = config.enabled_methods.bKash  ?? true
    row.nagad_enabled  = config.enabled_methods.Nagad  ?? true
    row.rocket_enabled = config.enabled_methods.Rocket ?? true
    row.upay_enabled   = config.enabled_methods.Upay   ?? true
  }
  const textFields = [
    'default_success_url', 'default_fail_url', 'default_cancel_url', 'maintenance_message',
  ]
  const numFields = [
    'min_amount', 'max_amount', 'daily_limit_per_merchant',
    'payment_timeout_seconds', 'processing_timeout_seconds',
    'gateway_fee_percent', 'gateway_fee_fixed',
  ]
  const boolFields = [
    'customer_receipts_enabled', 'merchant_receipts_enabled', 'maintenance_mode',
  ]
  for (const f of textFields) if (config[f] !== undefined) row[f] = config[f]
  for (const f of numFields)  if (config[f] !== undefined) row[f] = Number(config[f])
  for (const f of boolFields) if (config[f] !== undefined) row[f] = Boolean(config[f])
  return row
}

export function getDefaultGatewayConfig() {
  return {
    enabled_methods: { bKash: true, Nagad: true, Rocket: true, Upay: true },
    default_success_url: 'https://pay.swapnopay.top/success',
    default_fail_url:    'https://pay.swapnopay.top/failed',
    default_cancel_url:  'https://pay.swapnopay.top/cancelled',
    min_amount: 10, max_amount: 500000,
    daily_limit_per_merchant: 10000000,
    payment_timeout_seconds: 600,
    processing_timeout_seconds: 300,
    gateway_fee_percent: 0, gateway_fee_fixed: 0,
    customer_receipts_enabled: true, merchant_receipts_enabled: true,
    maintenance_mode: false, maintenance_message: '',
    device_active: null, device_last_seen: null,
    merchant_logo_url: null,
    updated_at: null,
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Merchant Administration & Database Provisioning
// ──────────────────────────────────────────────────────────────────────────────

export async function listMerchants() {
  const { data, error } = await getAdminClient()
    .from('merchant_gateway_settings')
    .select('*')
    .order('updated_at', { ascending: false })

  if (error) throw new Error('Failed to fetch merchants: ' + error.message)
  return data || []
}

export async function getMerchantById(merchantId) {
  const { data, error } = await getAdminClient()
    .from('merchant_gateway_settings')
    .select('*')
    .eq('merchant_id', merchantId)
    .maybeSingle()

  if (error) throw new Error('Failed to fetch merchant: ' + error.message)
  return data
}

/**
 * Register or update merchant profile including logo URL and Supabase DB credentials.
 */
export async function upsertMerchantProfile(profile) {
  const row = {
    merchant_id:        profile.merchant_id,
    merchant_name:      profile.merchant_name || 'Merchant ' + profile.merchant_id.slice(0, 6),
    merchant_logo_url:  profile.merchant_logo_url || null,
    supabase_url:       profile.supabase_url || null,
    supabase_anon_key:  profile.supabase_anon_key || null,
    bkash_enabled:      profile.bkash_enabled ?? true,
    nagad_enabled:      profile.nagad_enabled ?? true,
    rocket_enabled:     profile.rocket_enabled ?? true,
    upay_enabled:       profile.upay_enabled ?? true,
    success_url:        profile.success_url || null,
    fail_url:           profile.fail_url || null,
    cancel_url:         profile.cancel_url || null,
    receiving_numbers:  profile.receiving_numbers || {},
    qr_codes:           profile.qr_codes || {},
    auto_appeal_matching: profile.auto_appeal_matching ?? false,
    status:             profile.status || 'ACTIVE',
    updated_at:         new Date().toISOString(),
  }

  const { data, error } = await getAdminClient()
    .from('merchant_gateway_settings')
    .upsert(row, { onConflict: 'merchant_id' })
    .select('*')
    .single()

  if (error) throw new Error('Failed to save merchant profile: ' + error.message)
  return data
}

export async function updateMerchantStatus(merchantId, status) {
  const { data, error } = await getAdminClient()
    .from('merchant_gateway_settings')
    .update({ status, updated_at: new Date().toISOString() })
    .eq('merchant_id', merchantId)
    .select('*')
    .single()

  if (error) throw new Error('Failed to update merchant status: ' + error.message)
  return data
}

// ──────────────────────────────────────────────────────────────────────────────
// Showcase Config
// ──────────────────────────────────────────────────────────────────────────────

export async function getShowcaseConfig() {
  try {
    const { data, error } = await getAdminClient()
      .from('showcase_config')
      .select('*')
      .eq('key', 'main_showcase')
      .maybeSingle()

    if (error) {
      console.warn('[showcase] Admin DB query notice:', error.message)
      return null
    }
    return data ? data.value : null
  } catch (e) {
    console.warn('[showcase] getShowcaseConfig fallback:', e.message)
    return null
  }
}

export async function setShowcaseConfig(valueData) {
  const row = {
    key: 'main_showcase',
    value: valueData,
    updated_at: new Date().toISOString()
  }

  const { data, error } = await getAdminClient()
    .from('showcase_config')
    .upsert(row, { onConflict: 'key' })
    .select('*')
    .single()

  if (error) throw new Error('Failed to save showcase config: ' + error.message)
  return data.value
}

// ──────────────────────────────────────────────────────────────────────────────
// Dispute Appeals (Customer unverified payments)
// ──────────────────────────────────────────────────────────────────────────────

export async function createDisputeAppeal(merchantId, { order_id, trx_id, cus_phone, payment_method, note, screenshot_url } = {}) {
  if (!order_id || !trx_id) {
    throw new Error('order_id and trx_id are required for dispute appeal')
  }

  const appealRecord = {
    order_id,
    trx_id: String(trx_id).trim().toUpperCase(),
    cus_phone: cus_phone ? String(cus_phone).trim() : null,
    note: note ? String(note).slice(0, 500) : 'Customer initiated payment appeal',
    screenshot_url: screenshot_url || null,
    status: 'PENDING_REVIEW',
    created_at: new Date().toISOString(),
  }

  try {
    const creds = await getMerchantCredentials(merchantId)
    if (creds?.supabase_url && creds?.supabase_anon_key) {
      const merchantClient = createMerchantClient(creds.supabase_url, creds.supabase_anon_key)
      const { data, error } = await merchantClient
        .from('appeals')
        .insert(appealRecord)
        .select()
        .single()

      if (!error && data) {
        console.log(`[appeals] Created appeal ${data.id} in merchant ${merchantId} DB`)
        return data
      }
      if (error) {
        console.warn(`[appeals] Merchant DB insert notice:`, error.message)
      }
    }
  } catch (err) {
    console.warn(`[appeals] Merchant DB appeal write failed:`, err.message)
  }

  // Fallback: log payment event in admin DB
  await recordPaymentEvent(order_id, {
    tran_id: order_id,
    trx_id,
    payment_method: payment_method || 'MFS',
    status: 'PENDING',
    merchant_id: merchantId,
    sender_number: cus_phone,
    product_name: `Appeal submitted: ${note || 'Disputed payment'}`,
  })

  return {
    id: 'app_' + Date.now(),
    order_id,
    trx_id,
    cus_phone,
    status: 'PENDING_REVIEW',
    created_at: appealRecord.created_at,
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Merchant Device List
// ──────────────────────────────────────────────────────────────────────────────

export async function getMerchantDevicesList(merchantId) {
  if (!merchantId) return []
  try {
    const creds = await getMerchantCredentials(merchantId)
    if (!creds?.supabase_url || !creds?.supabase_anon_key) return []

    const merchantClient = createMerchantClient(creds.supabase_url, creds.supabase_anon_key)
    const { data, error } = await merchantClient
      .from('devices')
      .select('id, online, last_sync, disabled, created_at')
      .eq('merchant_id', merchantId)
      .order('last_sync', { ascending: false })

    if (error) {
      console.warn(`[devices] Query notice for merchant ${merchantId}:`, error.message)
      return []
    }
    return data || []
  } catch (err) {
    console.warn(`[devices] getMerchantDevicesList error:`, err.message)
    return []
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// MFS SMS Parsing Patterns (bKash, Nagad, Rocket, Upay)
// ──────────────────────────────────────────────────────────────────────────────

export async function getMfsPatterns() {
  try {
    const admin = getAdminClient()
    const { data } = await admin
      .from('showcase_config')
      .select('*')
      .eq('key', 'mfs_sms_patterns')
      .maybeSingle()

    if (data?.value) return data.value
  } catch {}

  // Production MFS regex patterns
  return {
    bKash: {
      regex: 'TrxID\\s+([A-Z0-9]+).*?Tk\\s+([0-9,.]+).*?from\\s+([0-9]+)',
      trx_group: 1,
      amount_group: 2,
      sender_group: 3,
      sample_sender: 'bKash',
    },
    Nagad: {
      regex: 'TxnID:\\s*([A-Z0-9]+).*?Amount:\\s*Tk\\s*([0-9,.]+).*?Customer:\\s*([0-9]+)',
      trx_group: 1,
      amount_group: 2,
      sender_group: 3,
      sample_sender: '16167',
    },
    Rocket: {
      regex: 'TxnId:\\s*([0-9]+).*?Tk\\.([0-9,.]+).*?From\\s*([0-9]+)',
      trx_group: 1,
      amount_group: 2,
      sender_group: 3,
      sample_sender: '16216',
    },
    Upay: {
      regex: 'TrxID:\\s*([A-Z0-9]+).*?Amt:\\s*Tk\\s*([0-9,.]+).*?From:\\s*([0-9]+)',
      trx_group: 1,
      amount_group: 2,
      sender_group: 3,
      sample_sender: 'UPAY',
    },
  }
}

export async function setMfsPatterns(patterns) {
  const admin = getAdminClient()
  const row = {
    key: 'mfs_sms_patterns',
    value: patterns,
    updated_at: new Date().toISOString(),
  }
  const { data, error } = await admin
    .from('showcase_config')
    .upsert(row, { onConflict: 'key' })
    .select('*')
    .single()

  if (error) throw new Error('Failed to save MFS patterns: ' + error.message)
  return data.value
}

// ──────────────────────────────────────────────────────────────────────────────
// System Diagnostic Overview
// ──────────────────────────────────────────────────────────────────────────────

export async function getAdminSystemOverview(heartbeatMap = null, io = null) {
  const mem = process.memoryUsage()
  const startTime = Date.now()
  const dbOk = await pingAdminDatabase()
  const dbLatencyMs = Date.now() - startTime

  return {
    service: 'swapnopay-backend',
    node_version: process.version,
    uptime_seconds: Math.floor(process.uptime()),
    memory: {
      rss_mb: (mem.rss / 1024 / 1024).toFixed(2),
      heap_used_mb: (mem.heapUsed / 1024 / 1024).toFixed(2),
      heap_total_mb: (mem.heapTotal / 1024 / 1024).toFixed(2),
    },
    socket_io: {
      connected_clients: io?.engine?.clientsCount || 0,
      active_merchants_online: heartbeatMap?.size || 0,
    },
    database: {
      admin_supabase_connected: dbOk,
      round_trip_latency_ms: dbLatencyMs,
    },
    timestamp: new Date().toISOString(),
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// Merchant KYC Verification (Admin Platform)
// ──────────────────────────────────────────────────────────────────────────────

export async function submitMerchantKyc({
  merchant_id,
  nid_number,
  nid_name,
  nid_dob,
  nid_front_url,
  nid_back_url,
  face_photo_url,
  liveness_passed = true,
  ocr_raw_text = '',
}) {
  if (!merchant_id) throw new Error('merchant_id is required')
  if (!nid_number) throw new Error('nid_number is required')

  const admin = getAdminClient()
  const now = new Date().toISOString()

  // 1. Update merchants table
  const updatePayload = {
    nid_number: String(nid_number).trim(),
    nid_name: nid_name ? String(nid_name).trim() : null,
    nid_dob: nid_dob ? String(nid_dob).trim() : null,
    nid_front_url: nid_front_url || null,
    nid_back_url: nid_back_url || null,
    face_photo_url: face_photo_url || null,
    kyc_status: 'PENDING',
    kyc_submitted_at: now,
    updated_at: now,
  }

  let updatedMerchant = null
  try {
    const { data, error } = await admin
      .from('merchants')
      .update(updatePayload)
      .eq('id', merchant_id)
      .select('*')
      .maybeSingle()

    if (error) {
      console.warn('[kyc] Failed to update merchants table by id, trying user_id:', error.message)
      const { data: d2 } = await admin
        .from('merchants')
        .update(updatePayload)
        .eq('user_id', merchant_id)
        .select('*')
        .maybeSingle()
      updatedMerchant = d2
    } else {
      updatedMerchant = data
    }
  } catch (err) {
    console.warn('[kyc] Supabase merchants table update warning:', err.message)
  }

  // 2. Also record in merchant_kyc_submissions if table exists
  try {
    await admin.from('merchant_kyc_submissions').insert({
      merchant_id,
      nid_number: String(nid_number).trim(),
      nid_name: nid_name ? String(nid_name).trim() : null,
      nid_dob: nid_dob ? String(nid_dob).trim() : null,
      nid_front_url: nid_front_url || null,
      nid_back_url: nid_back_url || null,
      face_photo_url: face_photo_url || null,
      liveness_passed: Boolean(liveness_passed),
      ocr_raw_text: ocr_raw_text || null,
      status: 'PENDING',
      submitted_at: now,
    })
  } catch (err) {
    // Ignore if table not yet created
  }

  return updatedMerchant || { id: merchant_id, merchant_id, ...updatePayload }
}

export async function listPendingKycSubmissions() {
  const admin = getAdminClient()
  try {
    const { data, error } = await admin
      .from('merchants')
      .select('id, user_id, business_name, email, phone, nid_number, nid_name, nid_dob, nid_front_url, nid_back_url, face_photo_url, kyc_status, kyc_submitted_at, kyc_reviewed_at, kyc_rejection_reason, created_at')
      .in('kyc_status', ['PENDING', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED'])
      .order('kyc_submitted_at', { ascending: false })

    if (error) throw error
    return data || []
  } catch (err) {
    // Fallback: select all merchants where nid_number is not null
    const { data: fallback, error: e2 } = await admin
      .from('merchants')
      .select('*')
      .not('nid_number', 'is', null)
    if (e2) {
      console.warn('[kyc] listPendingKycSubmissions warning:', e2.message)
      return []
    }
    return fallback || []
  }
}

export async function reviewMerchantKyc(merchantId, { action, reason, reviewed_by = 'ADMIN' }) {
  if (!merchantId) throw new Error('merchant_id is required')
  const isApproved = action === 'APPROVE' || action === 'VERIFY' || action === 'APPROVED' || action === 'VERIFIED'
  const status = isApproved ? 'VERIFIED' : 'REJECTED'
  const now = new Date().toISOString()

  const admin = getAdminClient()
  const payload = {
    kyc_status: status,
    kyc_reviewed_at: now,
    kyc_rejection_reason: status === 'REJECTED' ? (reason || 'Verification rejected by administrator') : null,
    kyc_reviewed_by: reviewed_by,
    updated_at: now,
  }
  if (status === 'VERIFIED') {
    payload.status = 'ACTIVE'
  }

  const { data, error } = await admin
    .from('merchants')
    .update(payload)
    .eq('id', merchantId)
    .select('*')
    .maybeSingle()

  if (error) {
    // Try updating by user_id
    const { data: d2, error: e2 } = await admin
      .from('merchants')
      .update(payload)
      .eq('user_id', merchantId)
      .select('*')
      .maybeSingle()
    if (e2) throw new Error('Failed to update KYC review: ' + error.message)
    return d2
  }

  // Also update submissions audit table if exists
  try {
    await admin
      .from('merchant_kyc_submissions')
      .update({
        status: status === 'VERIFIED' ? 'APPROVED' : 'REJECTED',
        reviewed_at: now,
        reviewed_by,
        rejection_reason: payload.kyc_rejection_reason,
      })
      .eq('merchant_id', merchantId)
      .eq('status', 'PENDING')
  } catch {}

  return data
}

