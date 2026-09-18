-- ============================================================
-- SwapnoPay: Merchant App PIN — Supabase Migration
-- Run once in Supabase SQL Editor: https://supabase.com/dashboard
-- ============================================================

-- Add PIN hash column (stores SHA-256 hash of the 4-digit PIN)
-- Raw PIN is NEVER stored — only the hash.
ALTER TABLE public.merchants
  ADD COLUMN IF NOT EXISTS app_pin_hash         TEXT    DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS pin_reset_requested  BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN public.merchants.app_pin_hash IS
  'SHA-256 hex digest of the merchant 4-digit app PIN. NULL = no PIN set.';

COMMENT ON COLUMN public.merchants.pin_reset_requested IS
  'When TRUE the merchant must set a new PIN after their next password login. Admin sets this to reset a PIN.';
