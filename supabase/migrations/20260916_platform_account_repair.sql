-- ADMIN / PLATFORM PROJECT ONLY. Requires ADMIN_DATABASE_SCHEMA.sql.
-- Additive repair: no deletion of merchants, Auth users, or existing connections.
BEGIN;
ALTER TABLE public.merchant_gateway_settings
  ADD COLUMN IF NOT EXISTS merchant_name text,
  ADD COLUMN IF NOT EXISTS merchant_logo_url text,
  ADD COLUMN IF NOT EXISTS supabase_url text,
  ADD COLUMN IF NOT EXISTS supabase_anon_key text;
ALTER TABLE public.merchants
  ADD COLUMN IF NOT EXISTS photo_url text,
  ADD COLUMN IF NOT EXISTS onboarded_at timestamptz,
  ADD COLUMN IF NOT EXISTS kyc_reviewed_by text;
ALTER TABLE public.merchants
  ADD COLUMN IF NOT EXISTS nid_number text,
  ADD COLUMN IF NOT EXISTS nid_name text,
  ADD COLUMN IF NOT EXISTS nid_dob text,
  ADD COLUMN IF NOT EXISTS nid_front_url text,
  ADD COLUMN IF NOT EXISTS nid_back_url text,
  ADD COLUMN IF NOT EXISTS face_photo_url text,
  ADD COLUMN IF NOT EXISTS kyc_status text DEFAULT 'UNVERIFIED',
  ADD COLUMN IF NOT EXISTS kyc_submitted_at timestamptz,
  ADD COLUMN IF NOT EXISTS kyc_reviewed_at timestamptz,
  ADD COLUMN IF NOT EXISTS kyc_rejection_reason text;

CREATE TABLE IF NOT EXISTS public.merchant_kyc_submissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id uuid NOT NULL REFERENCES public.merchants(id),
  nid_number text NOT NULL, nid_name text, nid_dob text,
  nid_front_url text, nid_back_url text, face_photo_url text,
  liveness_passed boolean NOT NULL DEFAULT false, ocr_raw_text text,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
  submitted_at timestamptz NOT NULL DEFAULT now(), reviewed_at timestamptz,
  reviewed_by text, rejection_reason text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_kyc_submissions_merchant ON public.merchant_kyc_submissions(merchant_id);
ALTER TABLE public.merchant_kyc_submissions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Allow service_role full access to kyc" ON public.merchant_kyc_submissions;
DROP POLICY IF EXISTS platform_kyc_admin ON public.merchant_kyc_submissions;
CREATE POLICY platform_kyc_admin ON public.merchant_kyc_submissions TO authenticated
  USING (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()));

create table if not exists support_tickets (
  id             uuid primary key default gen_random_uuid(),
  merchant_id    text,
  business_name  text,
  email          text,
  phone          text,
  category       text not null default 'GENERAL',
  subject        text not null,
  description    text not null,
  status         text not null default 'OPEN'
    check (status in ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
  admin_reply    text,
  resolved_at    timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS platform_support_admin ON public.support_tickets;
CREATE POLICY platform_support_admin ON public.support_tickets TO authenticated
 USING (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()))
 WITH CHECK (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()));
GRANT SELECT, INSERT, UPDATE, DELETE ON public.support_tickets TO service_role;
GRANT SELECT, INSERT, UPDATE ON public.support_tickets TO authenticated;
create table if not exists feature_requests (
  id            uuid primary key default gen_random_uuid(),
  merchant_id   text,
  business_name text,
  email         text,
  phone         text,
  title         text not null,
  category      text not null default 'GENERAL',
  description   text not null,
  priority      text not null default 'MEDIUM'
    check (priority in ('LOW','MEDIUM','HIGH','CRITICAL')),
  status        text not null default 'PENDING'
    check (status in ('PENDING','UNDER_REVIEW','PLANNED','IN_PROGRESS','COMPLETED','REJECTED')),
  admin_notes   text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
ALTER TABLE public.feature_requests ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS platform_support_admin ON public.feature_requests;
CREATE POLICY platform_support_admin ON public.feature_requests TO authenticated
 USING (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()))
 WITH CHECK (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()));
GRANT SELECT, INSERT, UPDATE, DELETE ON public.feature_requests TO service_role;
GRANT SELECT, INSERT, UPDATE ON public.feature_requests TO authenticated;
create table if not exists live_chat_messages (
  id           uuid primary key default gen_random_uuid(),
  merchant_id  text not null,
  sender       text not null check (sender in ('MERCHANT','PLATFORM_OWNER','AI_SUPPORT')),
  message      text not null,
  created_at   timestamptz not null default now()
);
ALTER TABLE public.live_chat_messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS platform_support_admin ON public.live_chat_messages;
CREATE POLICY platform_support_admin ON public.live_chat_messages TO authenticated
 USING (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()))
 WITH CHECK (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()));
GRANT SELECT, INSERT, UPDATE, DELETE ON public.live_chat_messages TO service_role;
GRANT SELECT, INSERT, UPDATE ON public.live_chat_messages TO authenticated;
CREATE TABLE IF NOT EXISTS public.showcase_config (key text PRIMARY KEY, value jsonb NOT NULL DEFAULT '{}', updated_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE public.showcase_config ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS platform_config_admin ON public.showcase_config;
CREATE POLICY platform_config_admin ON public.showcase_config TO authenticated
 USING (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()))
 WITH CHECK (EXISTS (SELECT 1 FROM public.admin_users WHERE id = auth.uid()));
GRANT SELECT, INSERT, UPDATE ON public.showcase_config TO authenticated;
GRANT SELECT ON public.showcase_config TO service_role;
GRANT ALL ON public.merchant_kyc_submissions TO service_role;

CREATE OR REPLACE FUNCTION public.save_platform_merchant_setup(
  p_user_id uuid, p_merchant_id uuid, p_profile jsonb, p_database jsonb
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = public AS $$
DECLARE m public.merchants; db_url text; db_key text;
BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended(p_user_id::text, 0));
  SELECT * INTO m FROM public.merchants WHERE user_id::text = p_user_id::text FOR UPDATE;
  IF NOT FOUND THEN
    SELECT * INTO m FROM public.merchants WHERE id = p_merchant_id FOR UPDATE;
    IF FOUND AND m.user_id IS NOT NULL AND m.user_id::text <> p_user_id::text THEN
      RAISE EXCEPTION 'Merchant belongs to another account';
    END IF;
  END IF;
  IF coalesce(trim(p_profile->>'business_name'),'') = '' OR coalesce(trim(p_profile->>'phone'),'') = '' THEN
    RAISE EXCEPTION 'Business name and phone are required';
  END IF;
  IF m.id IS NULL THEN
    INSERT INTO public.merchants(id, user_id, business_name, email, phone)
      VALUES(p_merchant_id, p_user_id, p_profile->>'business_name', p_profile->>'email', p_profile->>'phone') RETURNING * INTO m;
  END IF;
  UPDATE public.merchants SET user_id = p_user_id,
    business_name = trim(p_profile->>'business_name'), email = lower(p_profile->>'email'),
    phone = trim(p_profile->>'phone'),
    business_type = coalesce(nullif(p_profile->>'business_type',''),business_type),
    website = coalesce(nullif(p_profile->>'website',''),website),
    photo_url = coalesce(nullif(p_profile->>'photo_url',''),photo_url),
    onboarded_at = coalesce(onboarded_at,now()), updated_at = now()
    WHERE id = m.id RETURNING * INTO m;
  db_url := nullif(trim(p_database->>'supabase_url'),'');
  db_key := nullif(trim(p_database->>'supabase_anon_key'),'');
  IF (db_url IS NULL) <> (db_key IS NULL) THEN RAISE EXCEPTION 'Database URL and public key must be provided together'; END IF;
  INSERT INTO public.merchant_gateway_settings(merchant_id, merchant_name, merchant_logo_url, supabase_url, supabase_anon_key)
    VALUES(m.id, m.business_name, m.photo_url, db_url, db_key)
    ON CONFLICT (merchant_id) DO UPDATE SET merchant_name = excluded.merchant_name,
      merchant_logo_url = coalesce(excluded.merchant_logo_url,merchant_gateway_settings.merchant_logo_url),
      supabase_url = coalesce(excluded.supabase_url,merchant_gateway_settings.supabase_url),
      supabase_anon_key = coalesce(excluded.supabase_anon_key,merchant_gateway_settings.supabase_anon_key), updated_at = now();
  -- Manual connection details belong in gateway settings. Do not create invalid
  -- OAuth rows with missing encrypted tokens or overwrite existing OAuth tokens.
  RETURN to_jsonb(m);
END $$;

CREATE OR REPLACE FUNCTION public.submit_platform_merchant_kyc(p_submission jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = public AS $$
DECLARE m public.merchants;
BEGIN
  SELECT * INTO m FROM public.merchants WHERE id::text = p_submission->>'merchant_id' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Merchant profile not found'; END IF;
  IF m.kyc_status = 'VERIFIED' THEN RAISE EXCEPTION 'Merchant identity is already verified'; END IF;
  IF coalesce(p_submission->>'nid_number','') !~ '^([0-9]{10}|[0-9]{13}|[0-9]{17})$'
    OR coalesce(p_submission->>'nid_front_url','') = '' OR coalesce(p_submission->>'nid_back_url','') = ''
    OR coalesce(p_submission->>'face_photo_url','') = '' OR NOT coalesce((p_submission->>'liveness_passed')::boolean,false)
    THEN RAISE EXCEPTION 'NID, both documents, and live face capture are required'; END IF;
  UPDATE public.merchants SET nid_number = p_submission->>'nid_number', nid_name = p_submission->>'nid_name',
    nid_dob = p_submission->>'nid_dob', nid_front_url = p_submission->>'nid_front_url',
    nid_back_url = p_submission->>'nid_back_url', face_photo_url = p_submission->>'face_photo_url',
    kyc_status = 'PENDING', kyc_submitted_at = now(), kyc_reviewed_at = NULL,
    kyc_reviewed_by = NULL, kyc_rejection_reason = NULL, updated_at = now()
    WHERE id = m.id RETURNING * INTO m;
  INSERT INTO public.merchant_kyc_submissions(merchant_id,nid_number,nid_name,nid_dob,nid_front_url,nid_back_url,
    face_photo_url,liveness_passed,ocr_raw_text)
    VALUES(m.id,m.nid_number,m.nid_name,m.nid_dob,m.nid_front_url,m.nid_back_url,m.face_photo_url,true,p_submission->>'ocr_raw_text');
  RETURN to_jsonb(m);
END $$;

CREATE OR REPLACE FUNCTION public.review_platform_merchant_kyc(p_merchant_id uuid, p_status text, p_reason text, p_reviewer text)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = public AS $$
DECLARE m public.merchants;
BEGIN
  IF p_status NOT IN ('VERIFIED','REJECTED') THEN RAISE EXCEPTION 'Invalid review status'; END IF;
  SELECT * INTO m FROM public.merchants WHERE id = p_merchant_id FOR UPDATE;
  IF NOT FOUND THEN
    SELECT * INTO m FROM public.merchants WHERE user_id::text = p_merchant_id::text FOR UPDATE;
  END IF;
  IF m.id IS NULL THEN RAISE EXCEPTION 'Merchant not found'; END IF;
  IF m.kyc_status NOT IN ('PENDING','PENDING_REVIEW') THEN RAISE EXCEPTION 'Merchant has no pending KYC submission'; END IF;
  UPDATE public.merchants SET kyc_status = p_status, kyc_reviewed_at = now(), kyc_reviewed_by = p_reviewer,
    kyc_rejection_reason = CASE WHEN p_status = 'REJECTED' THEN p_reason ELSE NULL END,
    status = CASE WHEN p_status = 'VERIFIED' AND status = 'PENDING_VERIFICATION' THEN 'ACTIVE' ELSE status END,
    updated_at = now() WHERE id = m.id RETURNING * INTO m;
  UPDATE public.merchant_kyc_submissions SET status = CASE WHEN p_status = 'VERIFIED' THEN 'APPROVED' ELSE 'REJECTED' END,
    reviewed_at = now(), reviewed_by = p_reviewer, rejection_reason = m.kyc_rejection_reason, updated_at = now()
    WHERE merchant_id = m.id AND status = 'PENDING';
  RETURN to_jsonb(m);
END $$;

REVOKE ALL ON FUNCTION public.save_platform_merchant_setup(uuid,uuid,jsonb,jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.submit_platform_merchant_kyc(jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.review_platform_merchant_kyc(uuid,text,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.save_platform_merchant_setup(uuid,uuid,jsonb,jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.submit_platform_merchant_kyc(jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.review_platform_merchant_kyc(uuid,text,text,text) TO service_role;
DO $$
DECLARE table_name text;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime' AND NOT puballtables) THEN
    FOREACH table_name IN ARRAY ARRAY['merchants','support_tickets','live_chat_messages','feature_requests','showcase_config'] LOOP
      IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public' AND tablename = table_name) THEN
        EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', table_name);
      END IF;
    END LOOP;
  END IF;
END $$;
NOTIFY pgrst, 'reload schema';
COMMIT;
