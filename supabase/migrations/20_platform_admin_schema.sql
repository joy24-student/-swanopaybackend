-- Migration: 20_platform_admin_schema.sql
-- Consolidates platform control plane and admin database schema into versioned Supabase migrations
-- Tables: merchant_connections, gateway_config, merchant_gateway_settings, platform_api_keys,
--         payment_events, admin_users, webhook_secrets, audit_logs, showcase_config

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Merchant Connections
CREATE TABLE IF NOT EXISTS merchant_connections (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id     UUID REFERENCES merchants(id) ON DELETE CASCADE NOT NULL,
  public_endpoint TEXT,
  webhook_url     TEXT,
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS merchant_connections_merchant_id_unique ON merchant_connections(merchant_id);

-- 2. Gateway Configuration (Singleton Platform Settings)
CREATE TABLE IF NOT EXISTS gateway_config (
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
  updated_by                  UUID REFERENCES auth.users(id),
  created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO gateway_config (id)
VALUES ('00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

-- 3. Merchant Custom Gateway Settings
CREATE TABLE IF NOT EXISTS merchant_gateway_settings (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id           TEXT NOT NULL UNIQUE,
  bkash_enabled         BOOLEAN NOT NULL DEFAULT true,
  nagad_enabled         BOOLEAN NOT NULL DEFAULT true,
  rocket_enabled        BOOLEAN NOT NULL DEFAULT true,
  upay_enabled          BOOLEAN NOT NULL DEFAULT true,
  success_url           TEXT,
  fail_url              TEXT,
  cancel_url            TEXT,
  receiving_numbers     JSONB DEFAULT '{}'::jsonb,
  auto_appeal_matching  BOOLEAN NOT NULL DEFAULT false,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_merchant_gateway_settings_merchant ON merchant_gateway_settings(merchant_id);

-- 4. Platform API Keys
CREATE TABLE IF NOT EXISTS platform_api_keys (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id     TEXT NOT NULL,
  merchant_name   TEXT NOT NULL DEFAULT 'Unknown Merchant',
  label           TEXT NOT NULL DEFAULT 'Default API Key',
  key_digest      TEXT NOT NULL UNIQUE,
  key_preview     TEXT NOT NULL,
  revoked         BOOLEAN NOT NULL DEFAULT false,
  revoked_at      TIMESTAMPTZ,
  revoked_by      UUID REFERENCES auth.users(id),
  created_by      UUID REFERENCES auth.users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS platform_api_keys_digest_idx ON platform_api_keys(key_digest) WHERE NOT revoked;
CREATE INDEX IF NOT EXISTS platform_api_keys_merchant_idx ON platform_api_keys(merchant_id);

-- 5. Payment Events (Platform Ledger)
CREATE TABLE IF NOT EXISTS payment_events (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id        TEXT NOT NULL,
  tran_id         TEXT,
  trx_id          TEXT,
  status          TEXT NOT NULL CHECK (status IN ('PAID','FAILED','CANCELLED','PENDING')),
  amount          NUMERIC(12,2),
  currency        TEXT NOT NULL DEFAULT 'BDT',
  payment_method  TEXT,
  sender_number   TEXT,
  payment_time    TIMESTAMPTZ,
  merchant_id     TEXT,
  merchant_name   TEXT,
  project_ref     TEXT,
  cus_name        TEXT,
  cus_email       TEXT,
  product_name    TEXT,
  recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS payment_events_merchant_idx ON payment_events(merchant_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS payment_events_order_idx ON payment_events(order_id);
CREATE INDEX IF NOT EXISTS payment_events_status_idx ON payment_events(status, recorded_at DESC);

-- 6. Admin Users Table
CREATE TABLE IF NOT EXISTS admin_users (
  id          UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email       TEXT NOT NULL,
  role        TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('super_admin','admin','viewer')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 7. Webhook Secrets
CREATE TABLE IF NOT EXISTS webhook_secrets (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL UNIQUE,
  secret_hash TEXT NOT NULL,
  active      BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  rotated_at  TIMESTAMPTZ
);

-- 8. Platform Audit & Security Logs
CREATE TABLE IF NOT EXISTS audit_logs (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_email   TEXT,
  action        TEXT NOT NULL,
  details       JSONB DEFAULT '{}'::jsonb,
  ip_address    TEXT,
  user_agent    TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS audit_logs_action_idx ON audit_logs(action, created_at DESC);

-- 9. Showcase Config
CREATE TABLE IF NOT EXISTS showcase_config (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  key         TEXT UNIQUE NOT NULL,
  value       JSONB NOT NULL,
  updated_at  TIMESTAMPTZ DEFAULT now()
);

-- Enable RLS
ALTER TABLE merchant_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE gateway_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE merchant_gateway_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE showcase_config ENABLE ROW LEVEL SECURITY;

-- Service Role Policies (full bypass for backend services)
CREATE POLICY "Service Role full access on merchant_connections"
  ON merchant_connections FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on gateway_config"
  ON gateway_config FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on merchant_gateway_settings"
  ON merchant_gateway_settings FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on platform_api_keys"
  ON platform_api_keys FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on payment_events"
  ON payment_events FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on admin_users"
  ON admin_users FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on webhook_secrets"
  ON webhook_secrets FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on audit_logs"
  ON audit_logs FOR ALL USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on showcase_config"
  ON showcase_config FOR ALL USING (auth.role() = 'service_role');

-- Admin User Policies
CREATE POLICY "Admin read merchant_connections"
  ON merchant_connections FOR SELECT USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "admin_read_gateway_config" ON gateway_config
  FOR SELECT USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "admin_write_gateway_config" ON gateway_config
  FOR UPDATE USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "admin_manage_api_keys" ON platform_api_keys
  FOR ALL USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "admin_read_payment_events" ON payment_events
  FOR SELECT USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "admin_read_audit_logs" ON audit_logs
  FOR SELECT USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "admin_manage_showcase_config" ON showcase_config
  FOR ALL USING (EXISTS (SELECT 1 FROM admin_users WHERE id = auth.uid()));

CREATE POLICY "public_read_showcase_config" ON showcase_config
  FOR SELECT USING (true);

-- Update Trigger function if not exists
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS gateway_config_updated_at ON gateway_config;
CREATE TRIGGER gateway_config_updated_at
  BEFORE UPDATE ON gateway_config
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS merchant_gateway_settings_updated_at ON merchant_gateway_settings;
CREATE TRIGGER merchant_gateway_settings_updated_at
  BEFORE UPDATE ON merchant_gateway_settings
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
