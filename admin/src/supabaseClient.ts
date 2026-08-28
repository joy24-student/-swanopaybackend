import { createClient } from '@supabase/supabase-js'

export const SUPABASE_URL = (import.meta.env.VITE_SUPABASE_URL as string) || ''
export const SUPABASE_ANON_KEY = (import.meta.env.VITE_SUPABASE_ANON_KEY as string) || ''

if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
  throw new Error('VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY are required')
}

// Vite variables are compiled into browser JavaScript. Only the public anon
// key belongs here; authorization is enforced by Supabase Auth and RLS.
export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)
