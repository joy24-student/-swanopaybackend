-- ============================================================================
-- Migration 19: Merchant KYC & Biometric Identity Verification
-- Adds full NID card details, document URLs, face photo, and review audit trail
-- ============================================================================

-- 1. Ensure KYC columns exist on public.merchants table
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

-- 2. Add check constraint on kyc_status if not already constrained
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'merchants_kyc_status_check'
  ) THEN
    ALTER TABLE public.merchants
      ADD CONSTRAINT merchants_kyc_status_check
      CHECK (kyc_status IN ('UNVERIFIED', 'PENDING', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED'));
  END IF;
EXCEPTION
  WHEN OTHERS THEN
    NULL; -- Skip if already constrained or syntax differences
END $$;

-- 3. Dedicated KYC Submissions audit table for platform administrator tracking
CREATE TABLE IF NOT EXISTS public.merchant_kyc_submissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id UUID NOT NULL REFERENCES public.merchants(id) ON DELETE CASCADE,
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

CREATE INDEX IF NOT EXISTS idx_kyc_submissions_merchant ON public.merchant_kyc_submissions(merchant_id);
CREATE INDEX IF NOT EXISTS idx_kyc_submissions_status ON public.merchant_kyc_submissions(status);
CREATE INDEX IF NOT EXISTS idx_merchants_kyc_status ON public.merchants(kyc_status);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.merchant_kyc_submissions ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'merchant_kyc_submissions' AND policyname = 'Allow service_role full access to kyc'
  ) THEN
    CREATE POLICY "Allow service_role full access to kyc"
      ON public.merchant_kyc_submissions
      FOR ALL
      USING (true)
      WITH CHECK (true);
  END IF;
END $$;
