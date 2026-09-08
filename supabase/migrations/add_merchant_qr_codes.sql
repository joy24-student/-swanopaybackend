-- ============================================================
-- SwapnoPay Admin DB — Migration: qr_codes per payment method
-- Run this on your ADMIN Supabase project
-- ============================================================

-- Add qr_codes JSONB column to merchant_gateway_settings
-- Stores per-method uploaded QR image URLs:
-- { "bKash": "https://...", "Nagad": "https://...", "Rocket": "https://...", "Upay": "https://..." }
ALTER TABLE merchant_gateway_settings
  ADD COLUMN IF NOT EXISTS qr_codes JSONB DEFAULT '{}'::jsonb;

COMMENT ON COLUMN merchant_gateway_settings.qr_codes
  IS 'Per-MFS method uploaded QR code image URLs. Keys: bKash, Nagad, Rocket, Upay. Shown in widget Step 2 instead of auto-generated QR.';

-- Create Supabase Storage bucket for merchant QR code images
-- Run this in the Supabase SQL Editor:
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'merchant-qr-codes',
  'merchant-qr-codes',
  true,
  2097152,  -- 2MB limit per QR image
  ARRAY['image/png', 'image/jpeg', 'image/jpg', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- Allow public read on the bucket (QR images are public by design)
CREATE POLICY IF NOT EXISTS "Public read merchant QR codes"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'merchant-qr-codes');

-- Allow service role to upload/delete
CREATE POLICY IF NOT EXISTS "Service role manage merchant QR codes"
  ON storage.objects FOR ALL
  USING (bucket_id = 'merchant-qr-codes');

-- Verify
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'merchant_gateway_settings'
  AND column_name IN ('qr_codes', 'receiving_numbers', 'merchant_logo_url')
ORDER BY column_name;
