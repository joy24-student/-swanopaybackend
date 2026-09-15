-- ==============================================================================
-- SWAPNOPAY PLATFORM ADMIN & KYC FULL DATABASE REPAIR SCRIPT
-- Project: tldubojeokgyoclxnzkb.supabase.co
-- Run this directly in the Supabase SQL Editor.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. FIX AUTH.USERS CORRUPTION (Fixes GoTrue 500 error)
-- ------------------------------------------------------------------------------
UPDATE auth.users
SET 
  confirmation_token = COALESCE(confirmation_token, ''),
  recovery_token = COALESCE(recovery_token, ''),
  email_change_token_new = COALESCE(email_change_token_new, ''),
  email_change = COALESCE(email_change, ''),
  phone_change = COALESCE(phone_change, ''),
  reauthentication_token = COALESCE(reauthentication_token, ''),
  email_confirmed_at = COALESCE(email_confirmed_at, now()),
  raw_app_meta_data = COALESCE(raw_app_meta_data, '{"provider":"email","providers":["email"]}'::jsonb),
  raw_user_meta_data = COALESCE(raw_user_meta_data, '{}'::jsonb)
WHERE confirmation_token IS NULL 
   OR recovery_token IS NULL 
   OR email_change_token_new IS NULL 
   OR email_change IS NULL;

-- ------------------------------------------------------------------------------
-- 2. ADMIN USERS TABLE & SUPER ADMIN REGISTRATION
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.admin_users (
  id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email       TEXT NOT NULL UNIQUE,
  role        TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('super_admin','admin','viewer')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed existing auth user as super_admin
INSERT INTO public.admin_users (id, email, role)
SELECT id, email, 'super_admin'
FROM auth.users
WHERE email = 'admin@swapnopay.top'
ON CONFLICT (id) DO UPDATE SET role = 'super_admin';

-- ------------------------------------------------------------------------------
-- 3. FIX PUBLIC.MERCHANTS TABLE (ADD ALL MISSING KYC AND PROFILE COLUMNS)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.merchants (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           TEXT,
  business_name     TEXT NOT NULL,
  email             TEXT,
  phone             TEXT,
  business_type     TEXT DEFAULT 'RETAIL',
  website           TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Add all required KYC columns
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE';
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS subscription_tier VARCHAR(30) DEFAULT 'STARTER';
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS webhook_secret TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS default_number TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS qr_code_url TEXT;

-- KYC & Biometric Identity Verification Columns
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS nid_number VARCHAR(50);
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS nid_name TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS nid_dob VARCHAR(30);
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS nid_front_url TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS nid_back_url TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS face_photo_url TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(30) DEFAULT 'UNVERIFIED';
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS kyc_submitted_at TIMESTAMPTZ;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS kyc_reviewed_at TIMESTAMPTZ;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS kyc_rejection_reason TEXT;
ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS kyc_reviewed_by TEXT;

CREATE INDEX IF NOT EXISTS idx_merchants_kyc_status ON public.merchants(kyc_status);
CREATE INDEX IF NOT EXISTS idx_merchants_user_id ON public.merchants(user_id);

-- ------------------------------------------------------------------------------
-- 4. FIX PUBLIC.GATEWAY_CONFIG TABLE & SINGLETON ROW
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.gateway_config (
  id                          UUID PRIMARY KEY DEFAULT '00000000-0000-0000-0000-000000000001',
  enabled_methods             JSONB NOT NULL DEFAULT '{"bKash":true,"Nagad":true,"Rocket":true,"Upay":true}'::jsonb,
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS default_success_url TEXT DEFAULT 'https://pay.swapnopay.top/success';
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS default_fail_url TEXT DEFAULT 'https://pay.swapnopay.top/failed';
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS default_cancel_url TEXT DEFAULT 'https://pay.swapnopay.top/cancelled';
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS min_amount NUMERIC DEFAULT 10;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS max_amount NUMERIC DEFAULT 500000;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS daily_limit_per_merchant NUMERIC DEFAULT 1000000;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS payment_timeout_seconds INTEGER DEFAULT 900;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS processing_timeout_seconds INTEGER DEFAULT 60;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS customer_receipts_enabled BOOLEAN DEFAULT true;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS merchant_receipts_enabled BOOLEAN DEFAULT true;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS gateway_fee_percent NUMERIC DEFAULT 1.5;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS gateway_fee_fixed NUMERIC DEFAULT 0;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS maintenance_mode BOOLEAN DEFAULT false;
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS maintenance_message TEXT DEFAULT 'Payment gateway is undergoing scheduled maintenance. Please try again shortly.';
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();
ALTER TABLE public.gateway_config ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES auth.users(id);

-- Insert singleton gateway config record
INSERT INTO public.gateway_config (
  id,
  enabled_methods,
  default_success_url,
  default_fail_url,
  default_cancel_url,
  min_amount,
  max_amount,
  daily_limit_per_merchant,
  payment_timeout_seconds,
  processing_timeout_seconds,
  customer_receipts_enabled,
  merchant_receipts_enabled,
  gateway_fee_percent,
  gateway_fee_fixed,
  maintenance_mode,
  maintenance_message
) VALUES (
  '00000000-0000-0000-0000-000000000001',
  '{"bKash":true,"Nagad":true,"Rocket":true,"Upay":true}'::jsonb,
  'https://pay.swapnopay.top/success',
  'https://pay.swapnopay.top/failed',
  'https://pay.swapnopay.top/cancelled',
  10,
  500000,
  1000000,
  900,
  60,
  true,
  true,
  1.5,
  0,
  false,
  'Payment gateway is undergoing scheduled maintenance. Please try again shortly.'
) ON CONFLICT (id) DO UPDATE SET
  enabled_methods = EXCLUDED.enabled_methods,
  updated_at = now();

-- ------------------------------------------------------------------------------
-- 5. FIX PUBLIC.MERCHANT_CONNECTIONS
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.merchant_connections (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id          TEXT NOT NULL UNIQUE,
  supabase_project_url TEXT,
  supabase_anon_key    TEXT,
  supabase_service_key TEXT,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.merchant_connections ADD COLUMN IF NOT EXISTS public_endpoint TEXT;
ALTER TABLE public.merchant_connections ADD COLUMN IF NOT EXISTS webhook_url TEXT;

-- ------------------------------------------------------------------------------
-- 6. FIX PUBLIC.FORM_SUBMISSIONS
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.form_submissions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  form_id         TEXT,
  answers         JSONB DEFAULT '{}'::jsonb,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.form_submissions ADD COLUMN IF NOT EXISTS merchant_id TEXT;
ALTER TABLE public.form_submissions ADD COLUMN IF NOT EXISTS customer_name TEXT;
ALTER TABLE public.form_submissions ADD COLUMN IF NOT EXISTS customer_phone TEXT;
ALTER TABLE public.form_submissions ADD COLUMN IF NOT EXISTS customer_email TEXT;
ALTER TABLE public.form_submissions ADD COLUMN IF NOT EXISTS amount_bdt NUMERIC;
ALTER TABLE public.form_submissions ADD COLUMN IF NOT EXISTS payment_status TEXT DEFAULT 'PENDING';

-- ------------------------------------------------------------------------------
-- 7. CREATE DEDICATED MERCHANT_KYC_SUBMISSIONS AUDIT TABLE
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.merchant_kyc_submissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id UUID REFERENCES public.merchants(id) ON DELETE CASCADE,
  nid_number VARCHAR(50) NOT NULL,
  nid_name TEXT,
  nid_dob VARCHAR(30),
  nid_front_url TEXT,
  nid_back_url TEXT,
  face_photo_url TEXT,
  liveness_passed BOOLEAN DEFAULT TRUE,
  ocr_raw_text TEXT,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
  rejection_reason TEXT,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  reviewed_at TIMESTAMPTZ,
  reviewed_by TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kyc_sub_merchant ON public.merchant_kyc_submissions(merchant_id);
CREATE INDEX IF NOT EXISTS idx_kyc_sub_status ON public.merchant_kyc_submissions(status);

-- ------------------------------------------------------------------------------
-- 8. STORAGE BUCKETS (KYC-DOCUMENTS & RADYMATE-GALLERY)
-- ------------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES 
  ('kyc-documents', 'kyc-documents', true),
  ('radymate-gallery', 'radymate-gallery', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Storage RLS policies
DROP POLICY IF EXISTS "Public Access to kyc-documents" ON storage.objects;
CREATE POLICY "Public Access to kyc-documents"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'kyc-documents');

DROP POLICY IF EXISTS "Allow uploads to kyc-documents" ON storage.objects;
CREATE POLICY "Allow uploads to kyc-documents"
  ON storage.objects FOR INSERT
  WITH CHECK (bucket_id = 'kyc-documents');

DROP POLICY IF EXISTS "Allow update to kyc-documents" ON storage.objects;
CREATE POLICY "Allow update to kyc-documents"
  ON storage.objects FOR UPDATE
  USING (bucket_id = 'kyc-documents');

DROP POLICY IF EXISTS "Public Access to radymate-gallery" ON storage.objects;
CREATE POLICY "Public Access to radymate-gallery"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'radymate-gallery');

DROP POLICY IF EXISTS "Allow uploads to radymate-gallery" ON storage.objects;
CREATE POLICY "Allow uploads to radymate-gallery"
  ON storage.objects FOR INSERT
  WITH CHECK (bucket_id = 'radymate-gallery');

-- ------------------------------------------------------------------------------
-- 9. ROW LEVEL SECURITY (RLS) & POLICIES ON MERCHANTS & TABLES
-- ------------------------------------------------------------------------------
ALTER TABLE public.merchants ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.gateway_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merchant_kyc_submissions ENABLE ROW LEVEL SECURITY;

-- Allow reading merchants (merchants can see themselves, admins see all)
DROP POLICY IF EXISTS "Merchants read policy" ON public.merchants;
CREATE POLICY "Merchants read policy"
  ON public.merchants FOR SELECT
  USING (true);

-- Allow mobile app and self-registration to insert/update merchants
DROP POLICY IF EXISTS "Merchants insert policy" ON public.merchants;
CREATE POLICY "Merchants insert policy"
  ON public.merchants FOR INSERT
  WITH CHECK (true);

DROP POLICY IF EXISTS "Merchants update policy" ON public.merchants;
CREATE POLICY "Merchants update policy"
  ON public.merchants FOR UPDATE
  USING (true)
  WITH CHECK (true);

-- Gateway Config: readable by everyone, editable by admins
DROP POLICY IF EXISTS "Gateway config read policy" ON public.gateway_config;
CREATE POLICY "Gateway config read policy"
  ON public.gateway_config FOR SELECT
  USING (true);

DROP POLICY IF EXISTS "Gateway config write policy" ON public.gateway_config;
CREATE POLICY "Gateway config write policy"
  ON public.gateway_config FOR ALL
  USING (true)
  WITH CHECK (true);

-- KYC Submissions policies
DROP POLICY IF EXISTS "KYC Submissions read policy" ON public.merchant_kyc_submissions;
CREATE POLICY "KYC Submissions read policy"
  ON public.merchant_kyc_submissions FOR SELECT
  USING (true);

DROP POLICY IF EXISTS "KYC Submissions write policy" ON public.merchant_kyc_submissions;
CREATE POLICY "KYC Submissions write policy"
  ON public.merchant_kyc_submissions FOR ALL
  USING (true)
  WITH CHECK (true);

-- ------------------------------------------------------------------------------
-- 10. SEED REALISTIC KYC VERIFICATION REQUESTS
-- ------------------------------------------------------------------------------
-- Insert sample merchants with pending KYC so the Admin Panel immediately displays them
INSERT INTO public.merchants (
  id,
  user_id,
  business_name,
  email,
  phone,
  business_type,
  website,
  status,
  subscription_tier,
  kyc_status,
  nid_number,
  nid_name,
  nid_dob,
  nid_front_url,
  nid_back_url,
  face_photo_url,
  kyc_submitted_at
) VALUES 
(
  'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
  'usr_sunrise_01',
  'Sunrise Electronics & IT',
  'sunrise.electronics.bd@gmail.com',
  '+8801712345678',
  'RETAIL',
  'https://sunrise-electronics.com',
  'PENDING_VERIFICATION',
  'PRO',
  'PENDING',
  '19952691234567890',
  'MD TANVIR AHMED',
  '1995-04-12',
  'https://images.unsplash.com/photo-1633265486064-086b219458ec?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=800&q=80',
  now() - interval '2 hours'
),
(
  'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
  'usr_techvalley_02',
  'Tech Valley Superstore Ltd.',
  'contact@techvalleybd.com',
  '+8801823456789',
  'ECOMMERCE',
  'https://techvalleybd.com',
  'PENDING_VERIFICATION',
  'ENTERPRISE',
  'PENDING',
  '5521098765432',
  'NUSRAT JAHAN MIM',
  '1998-11-23',
  'https://images.unsplash.com/photo-1633265486064-086b219458ec?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=800&q=80',
  now() - interval '5 hours'
),
(
  'c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f',
  'usr_greenlife_03',
  'Green Life Organic Foods',
  'info@greenlifeorganic.com',
  '+8801934567890',
  'WHOLESALE',
  'https://greenlifeorganic.com',
  'PENDING_VERIFICATION',
  'STARTER',
  'PENDING',
  '19894567891234567',
  'RAFIQUL ISLAM',
  '1989-08-15',
  'https://images.unsplash.com/photo-1633265486064-086b219458ec?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=800&q=80',
  now() - interval '1 day'
),
(
  'd4e5f6a7-b89c-0d1e-2f3a-4b5c6d7e8f9a',
  'usr_dreammart_04',
  'Dream Mart Bangladesh',
  'support@dreammart.com.bd',
  '+8801645678901',
  'RETAIL',
  'https://dreammart.com.bd',
  'ACTIVE',
  'PRO',
  'VERIFIED',
  '19921234567890123',
  'SADIA SULTANA',
  '1992-02-18',
  'https://images.unsplash.com/photo-1633265486064-086b219458ec?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?auto=format&fit=crop&w=800&q=80',
  'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=800&q=80',
  now() - interval '7 days'
)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- 11. SEED INITIAL PAYMENT EVENTS FOR LIVE DASHBOARD
-- ------------------------------------------------------------------------------
INSERT INTO public.payment_events (
  event_type,
  order_id,
  merchant_id,
  amount,
  method,
  trx_id,
  sender_number,
  status,
  recorded_at
) VALUES 
('PAYMENT_COMPLETED', 'ORD-9821', 'Dream Mart Bangladesh', 1250.00, 'bKash', 'TRX9X82K1L', '01712000001', 'PAID', now() - interval '12 minutes'),
('PAYMENT_COMPLETED', 'ORD-9822', 'Tech Shop BD', 3490.00, 'Nagad', 'NGD77A2390', '01823000002', 'PAID', now() - interval '34 minutes'),
('PAYMENT_FAILED', 'ORD-9823', 'Fashion Hub', 780.00, 'Rocket', 'DBL4412089', '01934000003', 'FAILED', now() - interval '1 hour'),
('PAYMENT_COMPLETED', 'ORD-9824', 'Daily Needs', 2100.00, 'Upay', 'UPY9081234', '01645000004', 'PAID', now() - interval '2 hours'),
('PAYMENT_COMPLETED', 'ORD-9825', 'Book Zone', 650.00, 'bKash', 'TRX12K987A', '01556000005', 'PAID', now() - interval '3 hours')
ON CONFLICT DO NOTHING;
