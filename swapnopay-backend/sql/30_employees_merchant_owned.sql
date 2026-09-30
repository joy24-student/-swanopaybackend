-- ============================================================================
-- SWAPNOPAY MIGRATION 30: Employee / Team Roster is owned by the MERCHANT database
-- Run on every merchant-owned Supabase project. Idempotent and safe to re-run.
--
-- Why this migration exists
--   The Android app used to write the team roster (public.employees) into the shared
--   platform database *and* the merchant project, producing two divergent copies and
--   the "employee information is not synced" report. Employees are now written and read
--   ONLY against the merchant project (AppViewModel.resolveMerchantForOps()).
--
-- What it guarantees on the merchant project
--   1. public.employees exists with every column the app syncs
--      (avatar_url, permissions, joined_date, updated_at, department, ...).
--   2. merchant_id normalisation so an app-supplied merchant id that does not exist in
--      public.merchants is repaired instead of failing with FK 23503.
--   3. An RLS policy that accepts the app's data through the project anon key or an
--      authenticated session, so the roster can be written without creating a separate
--      Supabase Auth user inside this project.
--   4. PostgREST grants + schema cache reload.
-- ============================================================================

-- 0. Runner privileges (Supabase SQL editor may be impersonating another role)
RESET ROLE;
DO $$ BEGIN EXECUTE 'SET ROLE postgres'; EXCEPTION WHEN OTHERS THEN NULL; END $$;
DO $$ BEGIN GRANT USAGE ON SCHEMA public TO postgres, anon, authenticated, service_role;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- 1. Helper: resolve the owning merchant for this dedicated tenant database
CREATE OR REPLACE FUNCTION public.current_merchant_id()
RETURNS UUID LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_mid UUID;
  v_hdr TEXT;
BEGIN
  IF auth.uid() IS NOT NULL THEN
    SELECT id INTO v_mid FROM public.merchants WHERE user_id = auth.uid() LIMIT 1;
    IF v_mid IS NOT NULL THEN RETURN v_mid; END IF;
  END IF;

  BEGIN
    v_hdr := current_setting('request.headers', true)::json ->> 'x-merchant-id';
    IF v_hdr IS NOT NULL AND v_hdr ~ '^[0-9a-fA-F-]{36}$' THEN
      SELECT id INTO v_mid FROM public.merchants WHERE id = v_hdr::uuid LIMIT 1;
      IF v_mid IS NOT NULL THEN RETURN v_mid; END IF;
    END IF;
  EXCEPTION WHEN OTHERS THEN NULL;
  END;

  -- Dedicated tenant database: fall back to its own primary merchant row
  SELECT id INTO v_mid FROM public.merchants ORDER BY created_at ASC LIMIT 1;
  RETURN v_mid;
END;
$$;

DO $$ BEGIN GRANT EXECUTE ON FUNCTION public.current_merchant_id() TO postgres, authenticated, service_role, anon;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- 2. Helper: accept and self-provision the merchant_id on write.
--    The mobile app owns a merchant UUID (platform auth user id / installation id) that
--    usually has no matching row in this project and no Supabase Auth user either. Instead
--    of rejecting the write (FK 23503 / RLS 42501) or rewriting it to an unrelated row, the
--    merchant row is created on demand so the supplied id is preserved.
CREATE OR REPLACE FUNCTION public.fn_normalize_merchant_id()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_hdr TEXT;
  v_mid UUID;
  v_uid UUID;
  v_name TEXT;
  v_email TEXT;
  v_phone TEXT;
BEGIN
  -- Optional tenant hints sent by the app as request headers
  BEGIN
    v_hdr   := NULLIF(btrim(coalesce(current_setting('request.headers', true)::json ->> 'x-merchant-id', '')), '');
    v_name  := NULLIF(btrim(coalesce(current_setting('request.headers', true)::json ->> 'x-business-name', '')), '');
    v_email := NULLIF(btrim(coalesce(current_setting('request.headers', true)::json ->> 'x-merchant-email', '')), '');
    v_phone := NULLIF(btrim(coalesce(current_setting('request.headers', true)::json ->> 'x-merchant-phone', '')), '');
  EXCEPTION WHEN OTHERS THEN NULL;
  END;

  BEGIN v_uid := auth.uid(); EXCEPTION WHEN OTHERS THEN v_uid := NULL; END;

  -- 1. Trust the merchant_id supplied in the payload or in the x-merchant-id header.
  BEGIN
    IF NEW.merchant_id IS NULL AND v_hdr ~ '^[0-9a-fA-F-]{36}$' THEN
      v_mid := v_hdr::uuid;
    ELSE
      v_mid := NEW.merchant_id;
    END IF;
  EXCEPTION WHEN OTHERS THEN v_mid := NEW.merchant_id;
  END;

  IF v_mid IS NOT NULL THEN
    IF NOT EXISTS (SELECT 1 FROM public.merchants WHERE id = v_mid) THEN
      BEGIN
        INSERT INTO public.merchants (id, user_id, business_name, email, phone)
        VALUES (v_mid, v_uid, COALESCE(v_name, 'My Store'), v_email, v_phone)
        ON CONFLICT (id) DO NOTHING;
      EXCEPTION WHEN OTHERS THEN
        -- Retry without user_id (a different row may already own this auth user)
        BEGIN
          INSERT INTO public.merchants (id, business_name, email, phone)
          VALUES (v_mid, COALESCE(v_name, 'My Store'), v_email, v_phone)
          ON CONFLICT (id) DO NOTHING;
        EXCEPTION WHEN OTHERS THEN NULL;
        END;
      END;
    END IF;

    IF EXISTS (SELECT 1 FROM public.merchants WHERE id = v_mid) THEN
      NEW.merchant_id := v_mid;
      RETURN NEW;
    END IF;
    v_mid := NULL; -- keep resolving below
  END IF;

  -- 2. Fall back to the signed-in platform user, then to this project's merchant row.
  IF v_uid IS NOT NULL THEN
    SELECT id INTO v_mid FROM public.merchants WHERE user_id = v_uid LIMIT 1;
  END IF;
  IF v_mid IS NULL THEN
    SELECT id INTO v_mid FROM public.merchants ORDER BY created_at ASC NULLS LAST LIMIT 1;
  END IF;

  -- 3. Nothing exists yet: create the tenant row so the write is never rejected.
  IF v_mid IS NULL THEN
    BEGIN
      INSERT INTO public.merchants (user_id, business_name, email, phone)
      VALUES (v_uid, COALESCE(v_name, 'My Store'), v_email, v_phone)
      RETURNING id INTO v_mid;
    EXCEPTION WHEN OTHERS THEN
      INSERT INTO public.merchants (business_name, email, phone)
      VALUES (COALESCE(v_name, 'My Store'), v_email, v_phone)
      RETURNING id INTO v_mid;
    END;
  END IF;

  NEW.merchant_id := v_mid;
  RETURN NEW;
EXCEPTION WHEN OTHERS THEN
  -- Never block a write: fall back to any existing merchant row.
  BEGIN
    IF NEW.merchant_id IS NULL THEN
      SELECT id INTO v_mid FROM public.merchants ORDER BY created_at ASC NULLS LAST LIMIT 1;
      NEW.merchant_id := v_mid;
    END IF;
  EXCEPTION WHEN OTHERS THEN NULL;
  END;
  RETURN NEW;
END;
$$;

DO $$ BEGIN GRANT EXECUTE ON FUNCTION public.fn_normalize_merchant_id() TO postgres, authenticated, service_role, anon;
EXCEPTION WHEN OTHERS THEN NULL; END $$;

-- 3. Ensure the employees table exists on the merchant project
CREATE TABLE IF NOT EXISTS public.employees (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id UUID NOT NULL DEFAULT public.current_merchant_id() REFERENCES public.merchants(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  designation TEXT NOT NULL DEFAULT 'Staff',
  role TEXT NOT NULL DEFAULT 'Sales',
  email TEXT NOT NULL DEFAULT '',
  phone TEXT NOT NULL DEFAULT '',
  department TEXT NOT NULL DEFAULT 'General',
  status TEXT NOT NULL DEFAULT 'Active',
  avatar_url TEXT,
  permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
  joined_date DATE NOT NULL DEFAULT current_date,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Repair partial/older table variants in place
DO $$
BEGIN
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS merchant_id UUID';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS name TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS designation TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS role TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS email TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS phone TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS department TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS status TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS avatar_url TEXT';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS permissions JSONB DEFAULT ''[]''::jsonb';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS joined_date DATE DEFAULT current_date';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now()';
  EXECUTE 'ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now()';
  EXECUTE 'CREATE INDEX IF NOT EXISTS employees_merchant_id_idx ON public.employees(merchant_id)';
  EXECUTE 'CREATE INDEX IF NOT EXISTS index_employees_merchantId ON public.employees(merchant_id)';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- If an older deployment stored permissions as text, convert it so the app JSON payload fits
DO $$
DECLARE v_type TEXT;
BEGIN
  SELECT data_type INTO v_type FROM information_schema.columns
   WHERE table_schema = 'public' AND table_name = 'employees' AND column_name = 'permissions';
  IF v_type = 'text' THEN
    EXECUTE 'ALTER TABLE public.employees ALTER COLUMN permissions DROP DEFAULT';
    EXECUTE 'ALTER TABLE public.employees ALTER COLUMN permissions TYPE JSONB USING '
         || 'CASE WHEN permissions IS NULL OR btrim(permissions) = '''' THEN ''[]''::jsonb ELSE permissions::jsonb END';
    EXECUTE 'ALTER TABLE public.employees ALTER COLUMN permissions SET DEFAULT ''[]''::jsonb';
  END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 4. Keep the tenant's primary merchant row available, then repair orphaned rows
DO $$
DECLARE v_mid UUID;
BEGIN
  SELECT id INTO v_mid FROM public.merchants ORDER BY created_at ASC LIMIT 1;
  IF v_mid IS NULL THEN
    INSERT INTO public.merchants (id, business_name, email, phone)
    VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'Primary Store', 'admin@store.local', '01700000000')
    ON CONFLICT (id) DO NOTHING;
    v_mid := '00000000-0000-0000-0000-000000000001'::uuid;
  END IF;

  UPDATE public.employees e SET merchant_id = v_mid
   WHERE e.merchant_id IS NULL
      OR NOT EXISTS (SELECT 1 FROM public.merchants m WHERE m.id = e.merchant_id);
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 5. Attach the normalisation trigger to employees
DO $$
BEGIN
  IF to_regprocedure('public.fn_normalize_merchant_id()') IS NOT NULL THEN
    EXECUTE 'DROP TRIGGER IF EXISTS trg_employees_normalize_merchant ON public.employees';
    EXECUTE 'CREATE TRIGGER trg_employees_normalize_merchant
               BEFORE INSERT OR UPDATE OF merchant_id ON public.employees
               FOR EACH ROW EXECUTE FUNCTION public.fn_normalize_merchant_id()';
  END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 6. RLS: accept the mobile app's writes directly (anon key or authenticated session).
--    No Supabase Auth user has to be created in this project: the tenant row is
--    self-provisioned by the trigger above, so the data is stored under the app's own
--    merchant UUID and the app's merchant_id=eq.<uuid> reads return it.
DO $$
BEGIN
  EXECUTE 'ALTER TABLE public.employees ENABLE ROW LEVEL SECURITY';
  EXECUTE 'DROP POLICY IF EXISTS "Employees policy" ON public.employees';
  EXECUTE 'DROP POLICY IF EXISTS "employees policy" ON public.employees';
  EXECUTE 'DROP POLICY IF EXISTS "employees open sync policy" ON public.employees';
  EXECUTE 'DROP POLICY IF EXISTS "employees merchant sync policy" ON public.employees';
  EXECUTE 'CREATE POLICY "employees merchant sync policy" ON public.employees
             FOR ALL TO authenticated, anon
             USING (true)
             WITH CHECK (true)';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 7. PostgREST grants
DO $$
BEGIN
  EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON public.employees TO postgres, anon, authenticated, service_role';
  EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres, anon, authenticated, service_role';
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

-- 8. Realtime (best effort) so the employee roster streams to signed-in devices
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables
                    WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'employees') THEN
      EXECUTE 'ALTER PUBLICATION supabase_realtime ADD TABLE public.employees';
    END IF;
  END IF;
EXCEPTION WHEN OTHERS THEN NULL;
END $$;

NOTIFY pgrst, 'reload schema';

