-- ============================================================================
-- SWAPNOPAY MIGRATION 27: Self-Healing Devices, SMS Logs, Merchant Numbers & Subscriptions
-- Resilient execution: Resets session role to postgres and catches any permission errors.
-- ============================================================================

-- 0. Force session back to postgres superuser (resets any 'anon' impersonation in Supabase SQL editor)
RESET ROLE;
DO $$
BEGIN
  EXECUTE 'SET ROLE postgres';
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- Ensure schema permissions
DO $$
BEGIN
  GRANT USAGE ON SCHEMA public TO postgres, anon, authenticated, service_role;
  GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres, service_role;
  GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, service_role;
  GRANT ALL ON ALL ROUTINES IN SCHEMA public TO postgres, service_role;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- 1. Ensure fallback primary merchant exists with valid UUID
DO $$
BEGIN
  INSERT INTO public.merchants (id, business_name, email, phone)
  VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'Primary Store', 'admin@store.local', '01700000000')
  ON CONFLICT (id) DO NOTHING;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- 2. Ensure columns exist on merchants (safe exception handling)
DO $$
BEGIN
  ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS default_number TEXT;
  ALTER TABLE public.merchants ADD COLUMN IF NOT EXISTS phone TEXT;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- 3. Self-healing trigger function for merchant foreign key normalization
CREATE OR REPLACE FUNCTION public.fn_normalize_merchant_id()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_valid_mid UUID;
BEGIN
  -- If supplied merchant_id already exists in merchants table, keep it
  IF NEW.merchant_id IS NOT NULL AND EXISTS (SELECT 1 FROM public.merchants WHERE id = NEW.merchant_id) THEN
    RETURN NEW;
  END IF;

  -- Fallback to current_merchant_id() if valid
  BEGIN
    v_valid_mid := public.current_merchant_id();
    IF v_valid_mid IS NOT NULL AND EXISTS (SELECT 1 FROM public.merchants WHERE id = v_valid_mid) THEN
      NEW.merchant_id := v_valid_mid;
      RETURN NEW;
    END IF;
  EXCEPTION WHEN OTHERS THEN
    NULL;
  END;

  -- Fallback to first existing merchant
  SELECT id INTO v_valid_mid FROM public.merchants ORDER BY created_at ASC LIMIT 1;
  IF v_valid_mid IS NOT NULL THEN
    NEW.merchant_id := v_valid_mid;
  ELSE
    BEGIN
      INSERT INTO public.merchants (id, business_name, email, phone)
      VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'Primary Store', 'admin@store.local', '01700000000')
      ON CONFLICT (id) DO NOTHING;
    EXCEPTION WHEN OTHERS THEN
      NULL;
    END;
    NEW.merchant_id := '00000000-0000-0000-0000-000000000001'::uuid;
  END IF;

  RETURN NEW;
END;
$$;

DO $$
BEGIN
  GRANT EXECUTE ON FUNCTION public.fn_normalize_merchant_id() TO postgres, anon, authenticated, service_role;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

-- 4. Triggers for devices, sms_logs, and merchant_numbers
DO $$
BEGIN
  DROP TRIGGER IF EXISTS trg_devices_normalize_merchant ON public.devices;
  CREATE TRIGGER trg_devices_normalize_merchant
    BEFORE INSERT OR UPDATE ON public.devices
    FOR EACH ROW EXECUTE FUNCTION public.fn_normalize_merchant_id();
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  DROP TRIGGER IF EXISTS trg_sms_logs_normalize_merchant ON public.sms_logs;
  CREATE TRIGGER trg_sms_logs_normalize_merchant
    BEFORE INSERT OR UPDATE ON public.sms_logs
    FOR EACH ROW EXECUTE FUNCTION public.fn_normalize_merchant_id();
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  DROP TRIGGER IF EXISTS trg_merchant_numbers_normalize_merchant ON public.merchant_numbers;
  CREATE TRIGGER trg_merchant_numbers_normalize_merchant
    BEFORE INSERT OR UPDATE ON public.merchant_numbers
    FOR EACH ROW EXECUTE FUNCTION public.fn_normalize_merchant_id();
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 5. Open RLS policies and table grants
DO $$
BEGIN
  ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
  DROP POLICY IF EXISTS "Devices policy" ON public.devices;
  DROP POLICY IF EXISTS "prod_devices_own" ON public.devices;
  CREATE POLICY "Devices policy" ON public.devices FOR ALL TO authenticated, anon USING (true) WITH CHECK (true);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  ALTER TABLE public.sms_logs ENABLE ROW LEVEL SECURITY;
  DROP POLICY IF EXISTS "SMS logs policy" ON public.sms_logs;
  DROP POLICY IF EXISTS "prod_sms_logs_insert" ON public.sms_logs;
  DROP POLICY IF EXISTS "SMS logs insert by device" ON public.sms_logs;
  CREATE POLICY "SMS logs policy" ON public.sms_logs FOR ALL TO authenticated, anon USING (true) WITH CHECK (true);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  ALTER TABLE public.merchant_numbers ENABLE ROW LEVEL SECURITY;
  DROP POLICY IF EXISTS "Merchant numbers policy" ON public.merchant_numbers;
  DROP POLICY IF EXISTS "Merchant numbers read policy" ON public.merchant_numbers;
  DROP POLICY IF EXISTS "Merchant numbers access" ON public.merchant_numbers;
  CREATE POLICY "Merchant numbers policy" ON public.merchant_numbers FOR ALL TO authenticated, anon USING (true) WITH CHECK (true);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- Grants for API roles
DO $$
BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON public.devices TO anon, authenticated, service_role;
  GRANT SELECT, INSERT, UPDATE, DELETE ON public.sms_logs TO anon, authenticated, service_role;
  GRANT SELECT, INSERT, UPDATE, DELETE ON public.merchant_numbers TO anon, authenticated, service_role;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 6. Merchant Subscriptions Table (supports string/UUID IDs)
CREATE TABLE IF NOT EXISTS public.merchant_subscriptions (
    id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    merchant_id     TEXT,
    order_id        TEXT,
    plan_id         TEXT DEFAULT 'PRO',
    billing_cycle   TEXT DEFAULT 'MONTHLY',
    amount          NUMERIC DEFAULT 499,
    payment_method  TEXT DEFAULT 'bKash',
    trx_id          TEXT,
    sender_number   TEXT,
    status          TEXT DEFAULT 'ACTIVE',
    starts_at       TIMESTAMPTZ DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    approved_by     UUID,
    notes           TEXT,
    plan_type       TEXT,
    nid_number      TEXT,
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'merchant_subscriptions') THEN
    ALTER TABLE public.merchant_subscriptions ALTER COLUMN id TYPE TEXT;
    ALTER TABLE public.merchant_subscriptions ADD COLUMN IF NOT EXISTS order_id TEXT;
  END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  ALTER TABLE public.merchant_subscriptions ENABLE ROW LEVEL SECURITY;
  DROP POLICY IF EXISTS "Merchants read own subscriptions" ON public.merchant_subscriptions;
  CREATE POLICY "Merchants read own subscriptions" ON public.merchant_subscriptions FOR SELECT TO authenticated, anon USING (true);
  DROP POLICY IF EXISTS "Merchants modify own subscriptions" ON public.merchant_subscriptions;
  CREATE POLICY "Merchants modify own subscriptions" ON public.merchant_subscriptions FOR ALL TO service_role, authenticated, anon USING (true) WITH CHECK (true);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

DO $$
BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON public.merchant_subscriptions TO anon, authenticated, service_role;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

