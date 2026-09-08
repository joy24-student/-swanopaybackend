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
