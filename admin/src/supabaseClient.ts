import { createClient } from '@supabase/supabase-js'

export const SUPABASE_URL = ((import.meta as any).env.VITE_SUPABASE_URL as string) || ''
export const SUPABASE_ANON_KEY = ((import.meta as any).env.VITE_SUPABASE_ANON_KEY as string) || ''

export const isSupabaseConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY)

if (!isSupabaseConfigured) {
  console.warn(
    '[supabaseClient] VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY are not set in environment. Using placeholder client.'
  )
}

// Vite variables are compiled into browser JavaScript. Only the public anon
// key belongs here; authorization is enforced by Supabase Auth and RLS.
export const supabase = createClient(
  SUPABASE_URL || 'https://placeholder.supabase.co',
  SUPABASE_ANON_KEY || 'placeholder'
)
