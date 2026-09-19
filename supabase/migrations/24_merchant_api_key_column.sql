-- ==============================================================================
-- Migration 24: Add dynamic API key columns for Payment Gateway access
-- ==============================================================================

ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS api_key TEXT;
ALTER TABLE public.platform_api_keys ADD COLUMN IF NOT EXISTS raw_key TEXT;

CREATE INDEX IF NOT EXISTS idx_merchants_api_key ON public.merchants(api_key);
CREATE INDEX IF NOT EXISTS idx_platform_api_keys_raw ON public.platform_api_keys(raw_key);

-- Notify PostgREST to refresh its schema cache immediately
NOTIFY pgrst, 'reload schema';
