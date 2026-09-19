-- ==============================================================================
-- Migration 23: Add trial_ends_at, subscription and security PIN columns to merchants table
-- Fixes: "Could not find the 'trial_ends_at' column of 'merchants' in the schema cache"
-- ==============================================================================

ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMPTZ;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(30) DEFAULT 'TRIAL';
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(50) DEFAULT 'STARTER';
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMPTZ;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS app_pin_hash TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS pin_reset_requested BOOLEAN DEFAULT FALSE;

-- Notify PostgREST to refresh its schema cache immediately
NOTIFY pgrst, 'reload schema';
