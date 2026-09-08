// SwapnoPay Backend — Device Alert & Customer Re-engagement Service
// 1. Alerts merchant via email when a customer opens checkout while their gateway device is offline.
// 2. Subscribes waiting customers to receive an instant re-engagement email when merchant comes back online.

import { getAdminClient } from './adminSupabase.js'
import {
  sendMerchantDeviceOfflineAlert,
  sendCustomerMerchantBackOnlineAlert,
} from './mailer.js'

// Cooldown to prevent spamming merchant inbox if multiple customers open checkout
const MERCHANT_OFFLINE_ALERT_COOLDOWN_MS = 15 * 60 * 1000 // 15 minutes

// In-memory cache for merchant offline alerts cooldown: merchantId -> timestamp
const merchantOfflineCooldown = new Map()

// In-memory store for pending customer alerts: merchantId -> Map<subscriberKey, Subscription>
const customerAlertsMap = new Map()

/**
 * Check if the merchant should be alerted that their device is offline while a customer is waiting.
 * Throttled to at most once every 15 minutes per merchant.
 *
 * @param {string} merchantId
 * @param {object} details - { order_id, amount, cus_email }
 */
export async function checkAndAlertMerchantOffline(merchantId, details = {}) {
  if (!merchantId) return

  const now = Date.now()
  const lastAlertTime = merchantOfflineCooldown.get(merchantId) || 0
  if (now - lastAlertTime < MERCHANT_OFFLINE_ALERT_COOLDOWN_MS) {
    // Within cooldown window — skip sending another email
    return
  }

  try {
    const admin = getAdminClient()
    if (!admin) return

    // Query merchant contact details from admin DB
    let merchantRecord = null

    // Try finding by UUID / id first
    const { data: byId } = await admin
      .from('merchants')
      .select('id, business_name, email, phone')
      .eq('id', merchantId)
      .maybeSingle()

    if (byId?.email) {
      merchantRecord = byId
    } else {
      // Fallback: try by user_id
      const { data: byUserId } = await admin
        .from('merchants')
        .select('id, business_name, email, phone')
        .eq('user_id', merchantId)
        .maybeSingle()
      if (byUserId?.email) merchantRecord = byUserId
    }

    if (!merchantRecord?.email) {
      console.warn(`[device-alerts] No email found for merchant ${merchantId} — skipping offline alert.`)
      return
    }

    console.log(`[device-alerts] Sending offline alert to merchant ${merchantRecord.email} (Order: ${details.order_id || 'N/A'})`)
    
    // Set cooldown immediately to avoid race conditions
    merchantOfflineCooldown.set(merchantId, now)

    await sendMerchantDeviceOfflineAlert({
      toEmail: merchantRecord.email,
      merchantName: merchantRecord.business_name || 'Merchant',
      orderId: details.order_id || null,
      amount: details.amount || null,
      customerEmail: details.cus_email || null,
    })
  } catch (err) {
    console.error(`[device-alerts] Failed to alert merchant ${merchantId} of offline device:`, err.message)
  }
}

/**
 * Subscribe a customer to be alerted the moment the merchant's device comes back online.
 *
 * @param {object} data - { merchant_id, customer_email, order_id, amount, checkout_url }
 */
export async function subscribeCustomerForDeviceAlert({
  merchant_id,
  customer_email,
  order_id = null,
  amount = null,
  checkout_url,
}) {
  if (!merchant_id || !customer_email || !checkout_url) {
    throw new Error('merchant_id, customer_email, and checkout_url are required')
  }

  const normalizedEmail = customer_email.trim().toLowerCase()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
    throw new Error('Please provide a valid email address')
  }

  const subKey = `${normalizedEmail}:${order_id || 'direct'}`
  const subscription = {
    merchant_id,
    customer_email: normalizedEmail,
    order_id: order_id || null,
    amount: amount || null,
    checkout_url,
    created_at: new Date().toISOString(),
  }

  // 1. Store in memory for immediate access
  if (!customerAlertsMap.has(merchant_id)) {
    customerAlertsMap.set(merchant_id, new Map())
  }
  customerAlertsMap.get(merchant_id).set(subKey, subscription)

  console.log(`[device-alerts] Customer ${normalizedEmail} subscribed for merchant ${merchant_id} online alert.`)

  // 2. Persist to admin Supabase database (non-blocking)
  try {
    const admin = getAdminClient()
    if (admin) {
      admin
        .from('gateway_device_alerts')
        .insert({
          merchant_id,
          customer_email: normalizedEmail,
          order_id: order_id || null,
          amount: amount ? Number(amount) : null,
          checkout_url,
          status: 'PENDING',
        })
        .then(({ error }) => {
          if (error) {
            console.warn('[device-alerts] Failed to persist alert to database (memory fallback active):', error.message)
          }
        })
        .catch(dbErr => {
          console.warn('[device-alerts] DB insert exception (memory fallback active):', dbErr.message)
        })
    }
  } catch (err) {
    // Non-fatal, memory store is active
  }

  return { ok: true, message: 'Alert subscription active' }
}

/**
 * Triggered when a merchant's device comes back online (via Socket.io or HTTP heartbeat).
 * Dispatches re-engagement emails to all waiting customers.
 *
 * @param {string} merchantId
 * @param {string|null} merchantName
 */
export async function notifyWaitingCustomersMerchantOnline(merchantId, merchantName = null) {
  if (!merchantId) return

  const waitingList = []

  // 1. Collect from in-memory map
  if (customerAlertsMap.has(merchantId)) {
    const mSubMap = customerAlertsMap.get(merchantId)
    for (const sub of mSubMap.values()) {
      waitingList.push(sub)
    }
    // Clear in-memory map for this merchant
    customerAlertsMap.delete(merchantId)
  }

  // 2. Collect pending from database if available
  try {
    const admin = getAdminClient()
    if (admin) {
      const { data: dbSubscribers, error } = await admin
        .from('gateway_device_alerts')
        .select('*')
        .eq('merchant_id', merchantId)
        .eq('status', 'PENDING')

      if (!error && Array.isArray(dbSubscribers)) {
        for (const row of dbSubscribers) {
          const key = `${row.customer_email}:${row.order_id || 'direct'}`
          // Deduplicate if already in memory waiting list
          const exists = waitingList.some(
            item => `${item.customer_email}:${item.order_id || 'direct'}` === key
          )
          if (!exists) {
            waitingList.push({
              db_id: row.id,
              merchant_id: row.merchant_id,
              customer_email: row.customer_email,
              order_id: row.order_id,
              amount: row.amount,
              checkout_url: row.checkout_url,
            })
          }
        }
      }
    }
  } catch (err) {
    console.warn('[device-alerts] DB fetch warning during online notification:', err.message)
  }

  if (waitingList.length === 0) return

  console.log(`[device-alerts] Merchant ${merchantId} is ONLINE! Notifying ${waitingList.length} waiting customer(s)...`)

  // Resolve merchant name if not passed
  let resolvedMerchantName = merchantName
  if (!resolvedMerchantName) {
    try {
      const admin = getAdminClient()
      if (admin) {
        const { data: mData } = await admin
          .from('merchants')
          .select('business_name')
          .or(`id.eq.${merchantId},user_id.eq.${merchantId}`)
          .maybeSingle()
        if (mData?.business_name) resolvedMerchantName = mData.business_name
      }
    } catch {}
  }

  // Dispatch emails to all waiting customers
  for (const sub of waitingList) {
    sendCustomerMerchantBackOnlineAlert({
      toEmail: sub.customer_email,
      merchantName: resolvedMerchantName || 'The Merchant',
      orderId: sub.order_id,
      amount: sub.amount,
      checkoutUrl: sub.checkout_url,
    })
      .then(msgId => {
        if (msgId && sub.db_id) {
          // Update DB record status
          const admin = getAdminClient()
          if (admin) {
            admin
              .from('gateway_device_alerts')
              .update({ status: 'NOTIFIED', notified_at: new Date().toISOString() })
              .eq('id', sub.db_id)
              .catch(() => {})
          }
        }
      })
      .catch(err => {
        console.error(`[device-alerts] Failed to send re-engagement email to ${sub.customer_email}:`, err.message)
      })
  }
}
