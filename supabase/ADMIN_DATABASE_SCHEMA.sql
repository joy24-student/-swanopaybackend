-- ============================================================================
-- SwapnoPay ADMIN Supabase Master Schema
-- Platform Owner's Control Plane Database
-- Manages: Merchants Registry, OAuth Control Plane, Gateway Config, API Keys, Payment Events, Admin Users
-- ============================================================================

-- 0. Enable required extensions
create extension if not exists "pgcrypto";

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Merchants Table (Platform Merchant Registry)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists merchants (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid references auth.users on delete cascade,
  business_name       text not null default 'My Store',
  email               text,
  phone               text,
  business_type       text default 'RETAIL',
  website             text,
  default_number      text,
  webhook_secret      text,
  status              text not null default 'ACTIVE' check (status in ('ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION')),
  subscription_tier   text not null default 'STARTER' check (subscription_tier in ('STARTER', 'PRO', 'ENTERPRISE')),
  nid_number          text,
  nid_name            text,
  nid_dob             text,
  nid_front_url       text,
  nid_back_url        text,
  face_photo_url      text,
  kyc_status          text not null default 'UNVERIFIED' check (kyc_status in ('UNVERIFIED', 'PENDING', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED')),
  kyc_submitted_at    timestamptz,
  kyc_reviewed_at     timestamptz,
  kyc_rejection_reason text,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);
create unique index if not exists merchants_user_id_unique on merchants(user_id);
create index if not exists idx_merchants_status on merchants(status);
create index if not exists idx_merchants_kyc_status on merchants(kyc_status);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. OAuth State & PKCE Tracking (Control Plane)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists control_oauth_transactions (
  id                      uuid primary key default gen_random_uuid(),
  user_id                 text not null,
  state_hash              text not null unique,
  pkce_verifier_encrypted text not null,
  redirect_back           text,
  consumed                boolean default false,
  expires_at              timestamptz not null,
  created_at              timestamptz default now()
);
create index if not exists idx_oauth_tx_state on control_oauth_transactions(state_hash);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Customer/Merchant Supabase Connections & Tokens (Control Plane)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists supabase_connections (
  id                      uuid primary key default gen_random_uuid(),
  user_id                 text not null unique,
  organization_slug       text,
  selected_project_ref    text,
  encrypted_access_token  text not null,
  encrypted_refresh_token text not null,
  access_token_expires_at timestamptz not null,
  oauth_scopes            text[] default '{}',
  connection_status       text default 'ACTIVE',
  provisioning_status     text default 'NOT_STARTED',
  publishable_key         text,
  project_url             text,
  last_verified_at        timestamptz,
  created_at              timestamptz default now(),
  updated_at              timestamptz default now()
);
create index if not exists idx_supabase_conn_user on supabase_connections(user_id);
create index if not exists idx_supabase_conn_project on supabase_connections(selected_project_ref);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Merchant Connection Webhooks & Public Endpoints
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists merchant_connections (
  id              uuid primary key default gen_random_uuid(),
  merchant_id     uuid references merchants on delete cascade not null,
  public_endpoint text,
  webhook_url     text,
  created_at      timestamptz default now(),
  updated_at      timestamptz default now()
);
create unique index if not exists merchant_connections_merchant_id_unique on merchant_connections(merchant_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Gateway Configuration (Singleton row — Platform Owner Settings)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists gateway_config (
  id                          uuid primary key default gen_random_uuid(),
  -- Payment methods toggle
  bkash_enabled               boolean not null default true,
  nagad_enabled               boolean not null default true,
  rocket_enabled              boolean not null default true,
  upay_enabled                boolean not null default true,
  -- Default redirect URLs (used if merchant hasn't configured their own)
  default_success_url         text default 'https://pay.swapnopay.top/success',
  default_fail_url            text default 'https://pay.swapnopay.top/failed',
  default_cancel_url          text default 'https://pay.swapnopay.top/cancelled',
  -- Transaction limits
  min_amount                  numeric(12,2) not null default 10,
  max_amount                  numeric(12,2) not null default 500000,
  daily_limit_per_merchant    numeric(12,2) not null default 10000000,
  -- Timeouts (seconds)
  payment_timeout_seconds     integer not null default 600,
  processing_timeout_seconds  integer not null default 300,
  -- Fees
  gateway_fee_percent         numeric(5,2) not null default 0,
  gateway_fee_fixed           numeric(10,2) not null default 0,
  -- Receipt toggles
  customer_receipts_enabled   boolean not null default true,
  merchant_receipts_enabled   boolean not null default true,
  -- Maintenance mode
  maintenance_mode            boolean not null default false,
  maintenance_message         text default '',
  -- Audit
  updated_by                  uuid references auth.users,
  created_at                  timestamptz not null default now(),
  updated_at                  timestamptz not null default now()
);

-- Ensure singleton config row exists
insert into gateway_config (id)
values ('00000000-0000-0000-0000-000000000001')
on conflict (id) do nothing;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5b. Merchant Custom Gateway Settings
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists merchant_gateway_settings (
  id                    uuid primary key default gen_random_uuid(),
  merchant_id           text not null unique,
  bkash_enabled         boolean not null default true,
  nagad_enabled         boolean not null default true,
  rocket_enabled        boolean not null default true,
  upay_enabled          boolean not null default true,
  success_url           text,
  fail_url              text,
  cancel_url            text,
  receiving_numbers     jsonb default '{}'::jsonb, -- e.g. {"bKash": "017...", "Nagad": "018..."}
  auto_appeal_matching  boolean not null default false,
  created_at            timestamptz not null default now(),
  updated_at            timestamptz not null default now()
);
create index if not exists idx_merchant_gateway_settings_merchant on merchant_gateway_settings(merchant_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Platform API Keys (Issued to merchants by SwapnoPay Admin)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists platform_api_keys (
  id              uuid primary key default gen_random_uuid(),
  merchant_id     text not null,           -- ID from merchant registry or merchant DB
  merchant_name   text not null default 'Unknown Merchant',
  label           text not null default 'Default API Key',
  key_digest      text not null unique,    -- HMAC-SHA256 peppered digest (never raw)
  key_preview     text not null,           -- e.g. "sp_live_3a4b****"
  revoked         boolean not null default false,
  revoked_at      timestamptz,
  revoked_by      uuid references auth.users,
  created_by      uuid references auth.users,
  created_at      timestamptz not null default now()
);
create index if not exists platform_api_keys_digest_idx on platform_api_keys(key_digest) where not revoked;
create index if not exists platform_api_keys_merchant_idx on platform_api_keys(merchant_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. Payment Events (Platform-wide Payment Ledger & Analytics)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists payment_events (
  id              uuid primary key default gen_random_uuid(),
  order_id        text not null,
  tran_id         text,
  trx_id          text,
  status          text not null check (status in ('PAID','FAILED','CANCELLED','PENDING')),
  amount          numeric(12,2),
  currency        text not null default 'BDT',
  payment_method  text,
  sender_number   text,
  payment_time    timestamptz,
  merchant_id     text,
  merchant_name   text,
  project_ref     text,
  cus_name        text,
  cus_email       text,
  product_name    text,
  recorded_at     timestamptz not null default now()
);
create index if not exists payment_events_merchant_idx on payment_events(merchant_id, recorded_at desc);
create index if not exists payment_events_order_idx on payment_events(order_id);
create index if not exists payment_events_status_idx on payment_events(status, recorded_at desc);

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Admin Users Table (Platform Administrators)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists admin_users (
  id          uuid primary key references auth.users on delete cascade,
  email       text not null,
  role        text not null default 'admin' check (role in ('super_admin','admin','viewer')),
  created_at  timestamptz not null default now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. Webhook Secrets (For Securing Edge Function / Webhook Requests)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists webhook_secrets (
  id          uuid primary key default gen_random_uuid(),
  name        text not null unique,   -- e.g. 'process_sms', 'payment_receipt', 'backend_verify'
  secret_hash text not null,
  active      boolean not null default true,
  created_at  timestamptz not null default now(),
  rotated_at  timestamptz
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. MFS Regex Patterns Table (SMS Parsing Patterns for bKash, Nagad, Rocket, Upay)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists mfs_regex_patterns (
  id            uuid primary key default gen_random_uuid(),
  mfs_name      text not null check (mfs_name in ('bKash','Nagad','Rocket','Upay')),
  pattern_name  text not null,
  regex_pattern text not null,
  description   text,
  active        boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- Seed default MFS SMS Regex Patterns
insert into mfs_regex_patterns (id, mfs_name, pattern_name, regex_pattern, description)
values
  ('11111111-1111-1111-1111-111111111111', 'bKash', 'bKash Cash In / Payment Received', 'You have received Tk ([0-9,.]+) from ([0-9]+)\. TrxID ([A-Z0-9]+)', 'Standard bKash merchant/cash-in notification SMS'),
  ('22222222-2222-2222-2222-222222222222', 'Nagad', 'Nagad Payment Received', 'Received Tk ([0-9,.]+) from ([0-9]+)\. TxnID: ([A-Z0-9]+)', 'Standard Nagad merchant notification SMS'),
  ('33333333-3333-3333-3333-333333333333', 'Rocket', 'Rocket Cash In', 'Tk([0-9,.]+) received from ([0-9]+)\. Ref: ([A-Z0-9]+)', 'Standard DBBL Rocket cash-in notification SMS'),
  ('44444444-4444-4444-4444-444444444444', 'Upay', 'Upay Payment Received', 'Received Tk ([0-9,.]+) from ([0-9]+)\. TxnID ([A-Z0-9]+)', 'Standard Upay wallet payment notification SMS')
on conflict (id) do nothing;

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. Platform Audit & Security Logs
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists audit_logs (
  id            uuid primary key default gen_random_uuid(),
  actor_email   text,
  action        text not null,
  details       jsonb default '{}'::jsonb,
  ip_address    text,
  user_agent    text,
  created_at    timestamptz not null default now()
);
create index if not exists audit_logs_action_idx on audit_logs(action, created_at desc);

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. Row Level Security (RLS) & Policies
-- ─────────────────────────────────────────────────────────────────────────────
alter table merchants enable row level security;
alter table control_oauth_transactions enable row level security;
alter table supabase_connections enable row level security;
alter table merchant_connections enable row level security;
alter table gateway_config enable row level security;
alter table platform_api_keys enable row level security;
alter table payment_events enable row level security;
alter table admin_users enable row level security;
alter table webhook_secrets enable row level security;
alter table mfs_regex_patterns enable row level security;
alter table audit_logs enable row level security;

-- Service Role policies (full backend access)
create policy "Service Role full access on merchants"
  on merchants for all using (auth.role() = 'service_role');

create policy "Service Role full access on control_oauth_transactions"
  on control_oauth_transactions for all using (auth.role() = 'service_role');

create policy "Service Role full access on supabase_connections"
  on supabase_connections for all using (auth.role() = 'service_role');

create policy "Service Role full access on merchant_connections"
  on merchant_connections for all using (auth.role() = 'service_role');

-- Admin User policies
create policy "Admin read merchants"
  on merchants for select using (exists (select 1 from admin_users where id = auth.uid()));

create policy "Users read own connection status"
  on supabase_connections for select using (auth.uid()::text = user_id);

create policy "Admin read merchant_connections"
  on merchant_connections for select using (exists (select 1 from admin_users where id = auth.uid()));

create policy "admin_read_gateway_config" on gateway_config
  for select using (exists (select 1 from admin_users where id = auth.uid()));

create policy "admin_write_gateway_config" on gateway_config
  for update using (exists (select 1 from admin_users where id = auth.uid()));

create policy "admin_manage_api_keys" on platform_api_keys
  for all using (exists (select 1 from admin_users where id = auth.uid()));

create policy "admin_read_payment_events" on payment_events
  for select using (exists (select 1 from admin_users where id = auth.uid()));

create policy "service_insert_payment_events" on payment_events
  for insert with check (true);

create policy "admin_manage_mfs_regex" on mfs_regex_patterns
  for all using (exists (select 1 from admin_users where id = auth.uid()));

create policy "admin_read_audit_logs" on audit_logs
  for select using (exists (select 1 from admin_users where id = auth.uid()));

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. Helper Triggers & Functions
-- ─────────────────────────────────────────────────────────────────────────────
create or replace function set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger gateway_config_updated_at
  before update on gateway_config
  for each row execute function set_updated_at();

create trigger merchants_updated_at
  before update on merchants
  for each row execute function set_updated_at();

create trigger supabase_connections_updated_at
  before update on supabase_connections
  for each row execute function set_updated_at();

create trigger mfs_regex_patterns_updated_at
  before update on mfs_regex_patterns
  for each row execute function set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────────
-- 14. Showcase Landing Page Dynamic Assets & Configuration
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists showcase_config (
  id                      uuid primary key default gen_random_uuid(),
  key                     text unique not null,
  value                   jsonb not null,
  updated_at              timestamptz default now()
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 15. Gateway Device Alerts & Customer Re-engagement
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists gateway_device_alerts (
  id              uuid primary key default gen_random_uuid(),
  merchant_id     text not null,
  customer_email  text not null,
  order_id        text,
  amount          numeric,
  checkout_url    text not null,
  status          text not null default 'PENDING' check (status in ('PENDING', 'NOTIFIED', 'CANCELLED')),
  notified_at     timestamptz,
  created_at      timestamptz not null default now()
);
create index if not exists idx_device_alerts_merchant on gateway_device_alerts(merchant_id, status);
create index if not exists idx_device_alerts_email on gateway_device_alerts(customer_email);

