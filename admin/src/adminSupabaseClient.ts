// Admin Panel — Admin Supabase Client
// This connects to the PLATFORM OWNER'S Supabase project (NOT merchant databases).
// Use this for: gateway_config, platform_api_keys, payment_events, admin_users.

import { createClient } from '@supabase/supabase-js'

export const ADMIN_SUPABASE_URL =
  ((import.meta as any).env.VITE_ADMIN_SUPABASE_URL as string) || ''
export const ADMIN_SUPABASE_ANON_KEY =
  ((import.meta as any).env.VITE_ADMIN_SUPABASE_ANON_KEY as string) || ''

export const isAdminSupabaseConfigured = Boolean(ADMIN_SUPABASE_URL && ADMIN_SUPABASE_ANON_KEY)

if (!isAdminSupabaseConfigured) {
  console.warn(
    '[admin-supabase] VITE_ADMIN_SUPABASE_URL and VITE_ADMIN_SUPABASE_ANON_KEY are not configured. ' +
    'Add them to admin/.env. Using placeholder client.'
  )
}

// Anon key is safe for browser — Supabase Auth + RLS enforces access control.
// The admin_users table RLS ensures only authenticated admin users can read/write.
export const adminSupabase = createClient(
  ADMIN_SUPABASE_URL || 'https://placeholder.supabase.co',
  ADMIN_SUPABASE_ANON_KEY || 'placeholder'
)

// ──────────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────────

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

export async function saveRadymateGalleryConfig(items: RadymateGalleryItem[]) {
  const payload: RadymateGalleryConfig = {
    items: items.map((item) => ({ ...item, source: item.source || 'storage' })),
    updated_at: new Date().toISOString()
  }

  const { data, error } = await adminSupabase
    .from('showcase_config')
    .upsert({ key: RADYMATE_GALLERY_KEY, value: payload, updated_at: new Date().toISOString() })
    .select('*')
    .single()

  if (error) throw new Error('Failed to save Radymate gallery: ' + error.message)
  return data
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
