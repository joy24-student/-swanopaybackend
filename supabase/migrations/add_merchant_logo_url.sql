-- ============================================================
-- SwapnoPay Admin DB — Migration: merchant_logo_url
-- Run this on your ADMIN Supabase project (platform owner DB)
-- ============================================================

-- Add merchant_logo_url to merchant_gateway_settings table
ALTER TABLE merchant_gateway_settings
  ADD COLUMN IF NOT EXISTS merchant_logo_url TEXT DEFAULT NULL;

-- Add merchant_name column if not already present
ALTER TABLE merchant_gateway_settings
  ADD COLUMN IF NOT EXISTS merchant_name TEXT DEFAULT NULL;

-- Comment on columns
COMMENT ON COLUMN merchant_gateway_settings.merchant_logo_url
  IS 'URL to merchant brand logo — displayed in the payment widget header and overlays';

COMMENT ON COLUMN merchant_gateway_settings.merchant_name
  IS 'Merchant display name shown in widget — overrides merchant DB business_name if set';

-- Verify the migration
SELECT
  column_name,
  data_type,
  is_nullable,
  column_default
FROM information_schema.columns
WHERE table_name = 'merchant_gateway_settings'
  AND column_name IN ('merchant_logo_url', 'merchant_name', 'supabase_url', 'supabase_anon_key')
ORDER BY column_name;
