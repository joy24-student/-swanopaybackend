import { createClient } from '@supabase/supabase-js'

function resolveSupabaseUrl(envUrl: string | undefined): string {
  if (!envUrl || envUrl.includes('YOUR_ADMIN_PROJECT_REF') || envUrl.includes('placeholder')) {
    return 'https://tldubojeokgyoclxnzkb.supabase.co'
  }
  return envUrl
}

function resolveSupabaseKey(envKey: string | undefined): string {
  if (!envKey || envKey.includes('YOUR_ADMIN_ANON_KEY') || envKey.includes('placeholder')) {
    return 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0'
  }
  return envKey
}

export const SUPABASE_URL = resolveSupabaseUrl(((import.meta as any).env.VITE_ADMIN_SUPABASE_URL || (import.meta as any).env.VITE_SUPABASE_URL))
export const SUPABASE_ANON_KEY = resolveSupabaseKey(((import.meta as any).env.VITE_ADMIN_SUPABASE_ANON_KEY || (import.meta as any).env.VITE_SUPABASE_ANON_KEY))

export const isSupabaseConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY)

// Vite variables are compiled into browser JavaScript. Only the public anon
// key belongs here; authorization is enforced by Supabase Auth and RLS.
export const supabase = createClient(
  SUPABASE_URL,
  SUPABASE_ANON_KEY
)


