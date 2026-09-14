import { createClient } from '@supabase/supabase-js'

const DEFAULT_PLATFORM_URL = 'https://tldubojeokgyoclxnzkb.supabase.co'
const DEFAULT_PLATFORM_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRsZHVib2plb2tneW9jbHhuemtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc3NjcwODMsImV4cCI6MjEwMzM0MzA4M30.vlgmNEJ0_DpdbsZEQMA2Z82vwY4hwTxpgS4o9p5oEb0'

export const SUPABASE_URL = ((import.meta as any).env.VITE_SUPABASE_URL as string) || DEFAULT_PLATFORM_URL
export const SUPABASE_ANON_KEY = ((import.meta as any).env.VITE_SUPABASE_ANON_KEY as string) || DEFAULT_PLATFORM_KEY

export const isSupabaseConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY)

// Vite variables are compiled into browser JavaScript. Only the public anon
// key belongs here; authorization is enforced by Supabase Auth and RLS.
export const supabase = createClient(
  SUPABASE_URL,
  SUPABASE_ANON_KEY
)

