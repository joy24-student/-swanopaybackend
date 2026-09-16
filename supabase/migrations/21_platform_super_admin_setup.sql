-- ==============================================================================
-- SwapnoPay Platform Admin Database Schema & Super Admin Provisioning Script
-- Migration: 21_platform_super_admin_setup.sql
-- Run this in Supabase SQL Editor: https://supabase.com/dashboard/project/<ref>/sql
-- ==============================================================================

-- 1. Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Create public.admin_users Table (Full Super Admin Schema)
CREATE TABLE IF NOT EXISTS public.admin_users (
  id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email       TEXT NOT NULL,
  role        TEXT NOT NULL DEFAULT 'super_admin' CHECK (role IN ('super_admin', 'admin', 'viewer')),
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Case-insensitive unique index on email
CREATE UNIQUE INDEX IF NOT EXISTS admin_users_email_lower_idx ON public.admin_users (lower(email));

-- Ensure columns exist if table was previously created with fewer fields
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'admin_users' AND column_name = 'updated_at'
  ) THEN
    ALTER TABLE public.admin_users ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'admin_users' AND column_name = 'is_active'
  ) THEN
    ALTER TABLE public.admin_users ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
  END IF;
END $$;

-- 3. Automatic updated_at Trigger
CREATE OR REPLACE FUNCTION public.set_admin_users_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS tr_admin_users_updated_at ON public.admin_users;
CREATE TRIGGER tr_admin_users_updated_at
  BEFORE UPDATE ON public.admin_users
  FOR EACH ROW
  EXECUTE FUNCTION public.set_admin_users_updated_at();

-- 4. Helper Methods: is_exist, is_admin, is_super_admin

-- Method 4A: admin_is_exist — Check if an admin exists by email
CREATE OR REPLACE FUNCTION public.admin_is_exist(p_email TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  RETURN EXISTS (
    SELECT 1 FROM public.admin_users 
    WHERE lower(email) = lower(trim(p_email)) 
      AND is_active = true
  );
END;
$$;

-- Method 4B: is_admin — Check if given user ID (or current caller) has admin rights
CREATE OR REPLACE FUNCTION public.is_admin(p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_user_id IS NULL THEN
    RETURN false;
  END IF;
  RETURN EXISTS (
    SELECT 1 FROM public.admin_users
    WHERE id = p_user_id 
      AND is_active = true 
      AND role IN ('super_admin', 'admin')
  );
END;
$$;

-- Method 4C: is_super_admin — Check if given user ID (or current caller) is super_admin
CREATE OR REPLACE FUNCTION public.is_super_admin(p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
  IF p_user_id IS NULL THEN
    RETURN false;
  END IF;
  RETURN EXISTS (
    SELECT 1 FROM public.admin_users
    WHERE id = p_user_id 
      AND is_active = true 
      AND role = 'super_admin'
  );
END;
$$;

-- 5. Row Level Security (RLS) Configuration
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Service Role full access on admin_users" ON public.admin_users;
CREATE POLICY "Service Role full access on admin_users"
  ON public.admin_users FOR ALL
  TO service_role
  USING (true)
  WITH CHECK (true);

DROP POLICY IF EXISTS "Authenticated users can read their own admin record" ON public.admin_users;
CREATE POLICY "Authenticated users can read their own admin record"
  ON public.admin_users FOR SELECT
  TO authenticated
  USING (id = auth.uid());

DROP POLICY IF EXISTS "Super admins can manage all admin records" ON public.admin_users;
CREATE POLICY "Super admins can manage all admin records"
  ON public.admin_users FOR ALL
  TO authenticated
  USING (public.is_super_admin(auth.uid()))
  WITH CHECK (public.is_super_admin(auth.uid()));

DROP POLICY IF EXISTS "Self-bootstrap initial super admin" ON public.admin_users;
CREATE POLICY "Self-bootstrap initial super admin"
  ON public.admin_users FOR INSERT
  TO authenticated
  WITH CHECK (
    NOT EXISTS (SELECT 1 FROM public.admin_users WHERE role = 'super_admin' AND is_active = true)
    OR id = auth.uid()
  );

-- 6. Ensure Core Platform Settings Table Exists
CREATE TABLE IF NOT EXISTS public.gateway_config (
  id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bkash_enabled               BOOLEAN NOT NULL DEFAULT true,
  nagad_enabled               BOOLEAN NOT NULL DEFAULT true,
  rocket_enabled              BOOLEAN NOT NULL DEFAULT true,
  upay_enabled                BOOLEAN NOT NULL DEFAULT true,
  default_success_url         TEXT DEFAULT 'https://pay.swapnopay.top/success',
  default_fail_url            TEXT DEFAULT 'https://pay.swapnopay.top/failed',
  default_cancel_url          TEXT DEFAULT 'https://pay.swapnopay.top/cancelled',
  min_amount                  NUMERIC(12,2) NOT NULL DEFAULT 10,
  max_amount                  NUMERIC(12,2) NOT NULL DEFAULT 500000,
  daily_limit_per_merchant    NUMERIC(12,2) NOT NULL DEFAULT 10000000,
  payment_timeout_seconds     INTEGER NOT NULL DEFAULT 600,
  processing_timeout_seconds  INTEGER NOT NULL DEFAULT 300,
  gateway_fee_percent         NUMERIC(5,2) NOT NULL DEFAULT 0,
  gateway_fee_fixed           NUMERIC(10,2) NOT NULL DEFAULT 0,
  customer_receipts_enabled   BOOLEAN NOT NULL DEFAULT true,
  merchant_receipts_enabled   BOOLEAN NOT NULL DEFAULT true,
  maintenance_mode            BOOLEAN NOT NULL DEFAULT false,
  maintenance_message         TEXT DEFAULT '',
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.gateway_config (id)
VALUES ('00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.gateway_config ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Service role full access on gateway_config" ON public.gateway_config;
CREATE POLICY "Service role full access on gateway_config" ON public.gateway_config
  FOR ALL TO service_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Admins can read gateway_config" ON public.gateway_config;
CREATE POLICY "Admins can read gateway_config" ON public.gateway_config
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS "Admins can update gateway_config" ON public.gateway_config;
CREATE POLICY "Admins can update gateway_config" ON public.gateway_config
  FOR UPDATE TO authenticated USING (public.is_admin(auth.uid())) WITH CHECK (public.is_admin(auth.uid()));

-- ==============================================================================
-- 7. Provision or Reset Super Admin Account
-- CHANGE THE EMAIL AND PASSWORD BELOW BEFORE RUNNING IF DESIRED:
-- ==============================================================================
DO $$
DECLARE
  v_email         TEXT := 'admin@swapnopay.top';        -- <--- Your Super Admin Email
  v_password      TEXT := 'Admin@SwapnoPay2026!';      -- <--- Your Super Admin Password
  v_user_id       UUID;
  v_encrypted_pw  TEXT;
BEGIN
  -- Generate bcrypt hash for the password
  v_encrypted_pw := crypt(v_password, gen_salt('bf'));

  -- Find existing user in auth.users by email
  SELECT id INTO v_user_id FROM auth.users WHERE lower(email) = lower(v_email);

  IF v_user_id IS NULL THEN
    -- User does not exist, insert new user directly into auth.users
    v_user_id := gen_random_uuid();
    INSERT INTO auth.users (
      instance_id,
      id,
      aud,
      role,
      email,
      encrypted_password,
      email_confirmed_at,
      raw_app_meta_data,
      raw_user_meta_data,
      created_at,
      updated_at,
      confirmation_token,
      recovery_token
    ) VALUES (
      '00000000-0000-0000-0000-000000000000',
      v_user_id,
      'authenticated',
      'authenticated',
      lower(v_email),
      v_encrypted_pw,
      now(),
      '{"provider":"email","providers":["email"]}'::jsonb,
      '{"role":"super_admin"}'::jsonb,
      now(),
      now(),
      '',
      ''
    );
    RAISE NOTICE 'Created new auth.users account for: % (ID: %)', v_email, v_user_id;
  ELSE
    -- User exists, update password and confirm email
    UPDATE auth.users
    SET encrypted_password = v_encrypted_pw,
        email_confirmed_at = COALESCE(email_confirmed_at, now()),
        raw_app_meta_data = raw_app_meta_data || '{"provider":"email","providers":["email"]}'::jsonb,
        raw_user_meta_data = raw_user_meta_data || '{"role":"super_admin"}'::jsonb,
        updated_at = now()
    WHERE id = v_user_id;
    RAISE NOTICE 'Updated existing auth.users credentials for: % (ID: %)', v_email, v_user_id;
  END IF;

  -- Insert or update public.admin_users with super_admin role
  INSERT INTO public.admin_users (id, email, role, is_active, updated_at)
  VALUES (v_user_id, lower(v_email), 'super_admin', true, now())
  ON CONFLICT (id) DO UPDATE SET
    role = 'super_admin',
    is_active = true,
    email = lower(v_email),
    updated_at = now();

  RAISE NOTICE 'SUCCESS! Super Admin is active for % (ID: %). You can now log into the Platform Admin Panel.', v_email, v_user_id;
END $$;
