// Admin Panel — Admin Supabase Client
// This connects to the PLATFORM OWNER'S Supabase project (NOT merchant databases).
// Use this for: gateway_config, platform_api_keys, payment_events, admin_users.

import { supabase, SUPABASE_URL, SUPABASE_ANON_KEY, isSupabaseConfigured } from './supabaseClient'
export const adminSupabase = supabase
export const ADMIN_SUPABASE_URL = SUPABASE_URL
export const ADMIN_SUPABASE_ANON_KEY = SUPABASE_ANON_KEY
export const isAdminSupabaseConfigured = isSupabaseConfigured

export async function reviewMerchantIdentity(merchantId: string, action: 'APPROVE' | 'REJECT', reason = '') {
  const status = action === 'REJECT' ? 'REJECTED' : 'VERIFIED'
  const nowIso = new Date().toISOString()
  const updates: Record<string, any> = {
    kyc_status: status,
    kyc_reviewed_at: nowIso,
    kyc_reviewed_by: 'ADMIN',
    kyc_rejection_reason: action === 'REJECT' ? (reason || 'Documents did not meet criteria') : null,
    updated_at: nowIso,
  }
  if (status === 'VERIFIED') {
    updates.status = 'ACTIVE'
    updates.trial_ends_at = new Date(Date.now() + 90 * 86400000).toISOString()
  }

  // 1. Attempt backend API first if available
  try {
    const { data: { session } } = await adminSupabase.auth.getSession()
    const masterSecret = typeof sessionStorage !== 'undefined' ? sessionStorage.getItem('swapnopay_admin_secret') : null
    const base = (import.meta as any).env?.VITE_BACKEND_URL || 'https://api.swapnopay.top'
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }

    if (masterSecret) {
      headers['X-Admin-Secret'] = masterSecret
    } else if (session?.access_token) {
      headers['Authorization'] = `Bearer ${session.access_token}`
    }

    const response = await fetch(`${base.replace(/\/$/, '')}/v1/admin/kyc/${encodeURIComponent(merchantId)}/review`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ action, reason }),
    })
    if (response.ok) {
      const result = await response.json()
      if (result.ok && result.merchant) {
        return result.merchant
      }
    }
  } catch (fetchErr) {
    console.warn('[reviewMerchantIdentity] Backend fetch notice (using direct Supabase update):', fetchErr)
  }

  // 2. Direct Supabase update fallback (guaranteed to execute even without backend server)
  const runDirectUpdate = async (payload: Record<string, any>) => {
    let res = await adminSupabase
      .from('merchants')
      .update(payload)
      .eq('id', merchantId)
      .select('*')
      .maybeSingle()

    if (!res.data && !res.error) {
      res = await adminSupabase
        .from('merchants')
        .update(payload)
        .eq('user_id', merchantId)
        .select('*')
        .maybeSingle()
    }
    return res
  }

  let updateFields = { ...updates }
  let { data: updatedMerchant, error: updateError } = await runDirectUpdate(updateFields)

  // Resilient fallback: if column doesn't exist in Supabase PostgREST schema cache (e.g. trial_ends_at)
  if (updateError && (updateError.message?.includes('trial_ends_at') || updateError.message?.includes('schema cache'))) {
    console.warn('[reviewMerchantIdentity] Schema cache column mismatch, retrying without trial_ends_at:', updateError.message)
    delete updateFields.trial_ends_at
    const retry = await runDirectUpdate(updateFields)
    updatedMerchant = retry.data
    updateError = retry.error
  }

  if (updateError) throw new Error('KYC review was not saved: ' + updateError.message)
  if (!updatedMerchant) {
    // If not found, return local representation with updates
    updatedMerchant = { id: merchantId, ...updateFields }
  }

  // Update audit trail in merchant_kyc_submissions
  try {
    await adminSupabase
      .from('merchant_kyc_submissions')
      .update({
        status: status === 'VERIFIED' ? 'APPROVED' : 'REJECTED',
        reviewed_at: nowIso,
        reviewed_by: 'ADMIN',
        rejection_reason: updates.kyc_rejection_reason,
        updated_at: nowIso,
      })
      .eq('merchant_id', updatedMerchant.id || merchantId)
  } catch (_) {}

  return updatedMerchant
}

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

export function formatKycImageUrl(url?: string | null): string | null {
  if (!url || typeof url !== 'string') return null
  const trimmed = url.trim()
  if (!trimmed || trimmed === 'null' || trimmed === 'undefined') return null

  // 1. Data URI
  if (trimmed.startsWith('data:image')) return trimmed

  // 2. Raw base64 string
  if (trimmed.startsWith('/9j/') || trimmed.startsWith('iVBORw0KGgo') || (trimmed.length > 200 && !trimmed.includes('/') && !trimmed.startsWith('http'))) {
    return `data:image/jpeg;base64,${trimmed}`
  }

  // 3. Absolute HTTP/HTTPS URLs
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    return trimmed
  }

  // 4. Backend local upload path
  if (trimmed.startsWith('uploads/') || trimmed.startsWith('/uploads/')) {
    const backend = (import.meta as any).env?.VITE_BACKEND_URL || 'https://api.swapnopay.top'
    return `${backend.replace(/\/$/, '')}/${trimmed.replace(/^\//, '')}`
  }

  // 5. Supabase storage object path (e.g. "kyc/merchant_123/front.jpg" or "merchant_123/front.jpg")
  const cleanPath = trimmed.replace(/^\/?(kyc-documents\/)?/, '')
  const supabaseUrl = ADMIN_SUPABASE_URL || 'https://tldubojeokgyoclxnzkb.supabase.co'
  return `${supabaseUrl.replace(/\/$/, '')}/storage/v1/object/public/kyc-documents/${cleanPath}`
}

export const GATEWAY_CONFIG_ID = '00000000-0000-0000-0000-000000000001'

export const RADYMATE_GALLERY_BUCKET = 'radymate-gallery'
export const RADYMATE_GALLERY_KEY = 'radymate_gallery'

export interface RadymateGalleryItem {
  title: string
  caption: string
  image: string
  uploaded_at?: string
  source?: 'storage' | 'fallback'
}

export interface RadymateGalleryConfig {
  items: RadymateGalleryItem[]
  updated_at?: string
}

export const DEFAULT_RADYMATE_GALLERY: RadymateGalleryItem[] = [
  {
    title: 'Launch Dashboard',
    caption: 'Storefront overview and order performance',
    image: 'https://images.unsplash.com/photo-1522202176988-66273c2fd55f?auto=format&fit=crop&w=1200&q=80',
    source: 'fallback'
  },
  {
    title: 'Premium Storefront',
    caption: 'Professional eCommerce front-end showcase',
    image: 'https://images.unsplash.com/photo-1556740749-887f6717d7e4?auto=format&fit=crop&w=1200&q=80',
    source: 'fallback'
  },
  {
    title: 'Checkout Flow',
    caption: 'Fast and trusted conversion experience',
    image: 'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=80',
    source: 'fallback'
  },
  {
    title: 'Merchant Console',
    caption: 'Insights and analytics dashboard for growth',
    image: 'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1200&q=80',
    source: 'fallback'
  }
]

/** Fetch the singleton gateway config row */
export async function fetchGatewayConfig() {
  const { data, error } = await adminSupabase
    .from('gateway_config')
    .select('*')
    .eq('id', GATEWAY_CONFIG_ID)
    .single()
  if (error) throw new Error('Failed to fetch gateway config: ' + error.message)
  return data
}

/** Update the singleton gateway config row */
export async function updateGatewayConfig(updates: Record<string, unknown>) {
  const { data, error } = await adminSupabase
    .from('gateway_config')
    .update({ ...updates, updated_at: new Date().toISOString() })
    .eq('id', GATEWAY_CONFIG_ID)
    .select('*')
    .single()
  if (error) throw new Error('Failed to save gateway config: ' + error.message)
  return data
}

/** Fetch recent platform payment events */
export async function fetchPaymentEvents(limit = 50) {
  const { data, error } = await adminSupabase
    .from('payment_events')
    .select('*')
    .order('recorded_at', { ascending: false })
    .limit(limit)
  if (error) throw new Error('Failed to fetch payment events: ' + error.message)
  return data || []
}

/** Fetch all API key records (no digest exposed) */
export async function fetchApiKeys() {
  const { data, error } = await adminSupabase
    .from('platform_api_keys')
    .select('id,merchant_id,merchant_name,label,key_preview,revoked,revoked_at,created_at')
    .order('created_at', { ascending: false })
  if (error) throw new Error('Failed to fetch API keys: ' + error.message)
  return data || []
}

export async function fetchRadymateGalleryConfig(): Promise<RadymateGalleryConfig> {
  const { data, error } = await adminSupabase
    .from('showcase_config')
    .select('value')
    .eq('key', RADYMATE_GALLERY_KEY)
    .maybeSingle()

  if (error && error.code !== 'PGRST116') {
    throw new Error('Failed to fetch Radymate gallery config: ' + error.message)
  }

  const gallery = data?.value
  const items = Array.isArray(gallery?.items) && gallery.items.length > 0 ? gallery.items : DEFAULT_RADYMATE_GALLERY
  return {
    items: items.map((item: RadymateGalleryItem) => ({
      ...item,
      source: item.source || 'fallback'
    })),
    updated_at: gallery?.updated_at || new Date().toISOString()
  }
}

export async function upsertShowcaseConfig(key: string, value: any) {
  const now = new Date().toISOString()
  try {
    const { data: existing } = await adminSupabase
      .from('showcase_config')
      .select('id, key')
      .eq('key', key)
      .maybeSingle()

    if (existing) {
      const { data, error } = await adminSupabase
        .from('showcase_config')
        .update({ value, updated_at: now })
        .eq('key', key)
        .select('*')
        .single()
      if (!error) return data
      if (error && error.code !== 'PGRST116') throw error
    }

    const { data, error } = await adminSupabase
      .from('showcase_config')
      .upsert({ key, value, updated_at: now }, { onConflict: 'key' })
      .select('*')
      .single()

    if (!error) return data

    // Fallback: If upsert hit a duplicate key conflict, update the existing row directly
    const { data: updateData, error: updateError } = await adminSupabase
      .from('showcase_config')
      .update({ value, updated_at: now })
      .eq('key', key)
      .select('*')
      .single()

    if (updateError) throw updateError
    return updateData
  } catch (err: any) {
    throw new Error(err?.message || 'Database error')
  }
}

export async function saveRadymateGalleryConfig(items: RadymateGalleryItem[]) {
  const payload: RadymateGalleryConfig = {
    items: items.map((item) => ({ ...item, source: item.source || 'storage' })),
    updated_at: new Date().toISOString()
  }

  try {
    return await upsertShowcaseConfig(RADYMATE_GALLERY_KEY, payload)
  } catch (error: any) {
    throw new Error('Failed to save Radymate gallery: ' + error.message)
  }
}

export async function uploadRadymateGalleryImage(file: File, title: string, caption: string): Promise<RadymateGalleryItem> {
  if (!file) throw new Error('Please choose an image file first.')

  const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_')
  const objectPath = `radymate/${Date.now()}-${Math.random().toString(36).slice(2, 10)}-${safeName}`

  const { data: uploadData, error: uploadError } = await adminSupabase.storage
    .from(RADYMATE_GALLERY_BUCKET)
    .upload(objectPath, file, {
      cacheControl: '3600',
      upsert: false,
      contentType: file.type || 'image/jpeg'
    })

  if (uploadError) {
    throw new Error('Supabase upload failed: ' + uploadError.message)
  }

  const { data: publicUrlData } = adminSupabase.storage
    .from(RADYMATE_GALLERY_BUCKET)
    .getPublicUrl(uploadData?.path || objectPath)

  const imageUrl = publicUrlData?.publicUrl
  if (!imageUrl) {
    throw new Error('Uploaded image is missing a public URL. Make sure the bucket is public.')
  }

  return {
    title: title.trim() || 'Radymate storefront preview',
    caption: caption.trim() || 'Premium layout preview',
    image: imageUrl,
    uploaded_at: new Date().toISOString(),
    source: 'storage'
  }
}

// ──────────────────────────────────────────────────────────────────────────────
// 3D App Showcase Gallery & Landing Page Controls
// ──────────────────────────────────────────────────────────────────────────────

export const APP_GALLERY_KEY = 'app_gallery'
export const LANDING_PAGE_KEY = 'landing_page_config'

export interface AppGalleryWidget {
  label: string
  value: string
}

export interface AppGalleryItem {
  id: string
  badge: string
  title: string
  description: string
  image?: string
  screen_bg?: string
  widgets: AppGalleryWidget[]
  active: boolean
  highlight?: boolean
}

export interface AppGalleryConfig {
  section_title: string
  section_subtitle: string
  items: AppGalleryItem[]
  updated_at?: string
}

export const DEFAULT_APP_GALLERY: AppGalleryConfig = {
  section_title: 'See SwapnoPay in action.',
  section_subtitle: 'Explore the app experience built for modern businesses.',
  items: [
    {
      id: 'card-dashboard',
      badge: 'Dashboard',
      title: 'Executive Dashboard',
      description: 'Instant overview of sales, profit, and merchant health.',
      screen_bg: 'linear-gradient(135deg, #1e1b4b, #312e81 40%, #0f172a)',
      widgets: [
        { label: 'Sales', value: '৳ 28K' },
        { label: 'Orders', value: '214' },
        { label: 'Win', value: '92%' }
      ],
      active: true
    },
    {
      id: 'card-pos',
      badge: 'POS',
      title: 'POS & Billing',
      description: 'Fast billing, payment capture, and instant checkout.',
      screen_bg: 'linear-gradient(135deg, #111827, #1e293b 55%, #0f172a)',
      widgets: [
        { label: 'Cart', value: '৳ 1,250' },
        { label: 'Items', value: '3' },
        { label: 'Paid', value: 'Cash' }
      ],
      active: true
    },
    {
      id: 'card-ai-growth',
      badge: 'Popular',
      title: 'AI + Growth Engine',
      description: 'Sales intelligence and recommendation workflows.',
      screen_bg: 'linear-gradient(135deg, #1a1203, #2a1d0d 50%, #0f172a)',
      widgets: [
        { label: 'Profit', value: '৳ 9.6K' },
        { label: 'Match', value: '98%' },
        { label: 'AI', value: 'Ready' }
      ],
      active: true,
      highlight: true
    },
    {
      id: 'card-ledger',
      badge: 'Ledger',
      title: 'Digital Ledger',
      description: 'Track cashflow, supplier dues, and customer balances.',
      screen_bg: 'linear-gradient(135deg, #0f172a, #112236 45%, #0b1120)',
      widgets: [
        { label: 'Due', value: '৳ 11K' },
        { label: 'Clients', value: '24' },
        { label: 'Alerts', value: '5' }
      ],
      active: true
    },
    {
      id: 'card-copilot',
      badge: 'AI',
      title: 'Merchant Copilot',
      description: 'Actionable suggestions in Bangla or English.',
      screen_bg: 'linear-gradient(135deg, #111827, #0f172a 50%, #1f2937)',
      widgets: [
        { label: 'Sales', value: '৳ 48K' },
        { label: 'Trend', value: '2.1x' },
        { label: 'Advice', value: 'Yes' }
      ],
      active: true
    }
  ]
}

export interface LandingPageConfig {
  announcement_badge: string
  announcement_text: string
  hero_title: string
  hero_highlight: string
  hero_subtitle: string
  apk_download_url: string
  play_store_url: string
  web_portal_url: string
  docs_url: string
  demo_video_url: string
  metric_settlement: string
  metric_match_rate: string
  metric_uptime: string
  metric_merchants: string
  status_text: string
  updated_at?: string
}

export const DEFAULT_LANDING_PAGE_CONFIG: LandingPageConfig = {
  announcement_badge: 'NEXT-GEN BANGLADESH PAYMENT PLATFORM',
  announcement_text: 'PAYMENTS FOR BANGLADESH',
  hero_title: 'Payments.',
  hero_highlight: 'Reimagined.',
  hero_subtitle: 'Payment gateway automation, POS billing, inventory and digital ledger (ব্যবসা খাতা) for businesses in Bangladesh. Manage bKash, Nagad, Rocket and Upay payments in one platform.',
  apk_download_url: '/swapnopay-debug.apk',
  play_store_url: 'https://play.google.com/store/apps/details?id=com.example.lenden23',
  web_portal_url: 'https://pay.swapnopay.top/portal.html',
  docs_url: 'https://pay.swapnopay.top/docs.html',
  demo_video_url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
  metric_settlement: '2.4s',
  metric_match_rate: '99.8%',
  metric_uptime: '99.99%',
  metric_merchants: '12,400+',
  status_text: 'Android Version 1.0.0 • Offline Ready & Bank-Grade Encrypted',
}

export async function fetchAppGalleryConfig(): Promise<AppGalleryConfig> {
  const { data, error } = await adminSupabase
    .from('showcase_config')
    .select('value')
    .eq('key', APP_GALLERY_KEY)
    .maybeSingle()

  if (error && error.code !== 'PGRST116') {
    console.warn('[fetchAppGalleryConfig] error:', error.message)
  }

  const raw = data?.value
  if (raw && Array.isArray(raw.items) && raw.items.length > 0) {
    return {
      section_title: raw.section_title || DEFAULT_APP_GALLERY.section_title,
      section_subtitle: raw.section_subtitle || DEFAULT_APP_GALLERY.section_subtitle,
      items: raw.items,
      updated_at: raw.updated_at
    }
  }

  return DEFAULT_APP_GALLERY
}

export async function saveAppGalleryConfig(config: AppGalleryConfig) {
  const payload: AppGalleryConfig = {
    section_title: config.section_title.trim() || DEFAULT_APP_GALLERY.section_title,
    section_subtitle: config.section_subtitle.trim() || DEFAULT_APP_GALLERY.section_subtitle,
    items: config.items,
    updated_at: new Date().toISOString()
  }

  try {
    return await upsertShowcaseConfig(APP_GALLERY_KEY, payload)
  } catch (error: any) {
    throw new Error('Failed to save App Gallery config: ' + error.message)
  }
}

export async function fetchLandingPageConfig(): Promise<LandingPageConfig> {
  const { data, error } = await adminSupabase
    .from('showcase_config')
    .select('value')
    .eq('key', LANDING_PAGE_KEY)
    .maybeSingle()

  if (error && error.code !== 'PGRST116') {
    console.warn('[fetchLandingPageConfig] error:', error.message)
  }

  const raw = data?.value
  if (raw && typeof raw === 'object') {
    return {
      ...DEFAULT_LANDING_PAGE_CONFIG,
      ...raw,
      updated_at: raw.updated_at
    }
  }

  return DEFAULT_LANDING_PAGE_CONFIG
}

export async function saveLandingPageConfig(config: LandingPageConfig) {
  const payload: LandingPageConfig = {
    ...config,
    updated_at: new Date().toISOString()
  }

  try {
    return await upsertShowcaseConfig(LANDING_PAGE_KEY, payload)
  } catch (error: any) {
    throw new Error('Failed to save Landing Page config: ' + error.message)
  }
}

export async function uploadAppGalleryScreenshot(file: File, folder = 'app-showcase'): Promise<string> {
  if (!file) throw new Error('Please choose a screenshot image first.')

  const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, '_')
  const objectPath = `${folder}/${Date.now()}-${Math.random().toString(36).slice(2, 10)}-${safeName}`

  const { data: uploadData, error: uploadError } = await adminSupabase.storage
    .from(RADYMATE_GALLERY_BUCKET)
    .upload(objectPath, file, {
      cacheControl: '3600',
      upsert: false,
      contentType: file.type || 'image/jpeg'
    })

  if (uploadError) {
    throw new Error('Supabase storage upload failed: ' + uploadError.message)
  }

  const { data: publicUrlData } = adminSupabase.storage
    .from(RADYMATE_GALLERY_BUCKET)
    .getPublicUrl(uploadData?.path || objectPath)

  const imageUrl = publicUrlData?.publicUrl
  if (!imageUrl) {
    throw new Error('Uploaded image is missing a public URL. Ensure bucket permissions allow public read.')
  }

  return imageUrl
}

// ─────────────────────────────────────────────────────────────
// MERCHANT APP PIN MANAGEMENT (Admin Panel)
// ─────────────────────────────────────────────────────────────

/** Get PIN status for a merchant — reads app_pin_hash and pin_reset_requested */
export async function getMerchantPinStatus(merchantId: string): Promise<{
  pin_set: boolean
  pin_reset_requested: boolean
}> {
  const { data, error } = await adminSupabase
    .from('merchants')
    .select('app_pin_hash, pin_reset_requested')
    .eq('id', merchantId)
    .maybeSingle()

  if (error) throw new Error('Failed to get PIN status: ' + error.message)
  return {
    pin_set: !!data?.app_pin_hash,
    pin_reset_requested: data?.pin_reset_requested ?? false,
  }
}

/** Admin: clear a merchant's PIN hash (forces merchant to set a new PIN on next login) */
export async function clearMerchantPin(merchantId: string): Promise<void> {
  const { error } = await adminSupabase
    .from('merchants')
    .update({
      app_pin_hash: null,
      pin_reset_requested: false,
      updated_at: new Date().toISOString(),
    })
    .eq('id', merchantId)

  if (error) throw new Error('Failed to clear merchant PIN: ' + error.message)
}

/** Admin: set pin_reset_requested = true (marks PIN for forced reset) */
export async function forceRequestPinReset(merchantId: string): Promise<void> {
  const { error } = await adminSupabase
    .from('merchants')
    .update({
      pin_reset_requested: true,
      updated_at: new Date().toISOString(),
    })
    .eq('id', merchantId)

  if (error) throw new Error('Failed to set PIN reset flag: ' + error.message)
}
