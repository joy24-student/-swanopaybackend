import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.7.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

async function getAccessToken(supabaseClient: any, userId: string): Promise<string> {
  const { data: conn, error } = await supabaseClient
    .from("supabase_connections")
    .select("*")
    .eq("user_id", userId)
    .single();

  if (error || !conn) {
    throw new Error("No active Supabase connection found for user.");
  }

  const isExpired = new Date(conn.access_token_expires_at) <= new Date(Date.now() + 60000); // 1-min buffer
  if (!isExpired && conn.encrypted_access_token) {
    return conn.encrypted_access_token;
  }

  // Refresh Token Exchange
  console.log("Access token expired. Refreshing token for user:", userId);
  const clientId = Deno.env.get("SUPABASE_OAUTH_CLIENT_ID") || "5d3dcd9b-1acf-4e31-96d2-d673af42a18b";
  const clientSecret = Deno.env.get("SUPABASE_OAUTH_CLIENT_SECRET") || "";
  const basicAuth = btoa(`${clientId}:${clientSecret}`);

  const refreshParams = new URLSearchParams();
  refreshParams.append("grant_type", "refresh_token");
  refreshParams.append("refresh_token", conn.encrypted_refresh_token);

  const res = await fetch("https://api.supabase.com/v1/oauth/token", {
    method: "POST",
    headers: {
      "Authorization": `Basic ${basicAuth}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: refreshParams.toString(),
  });

  const refreshed = await res.json();
  if (!res.ok || !refreshed.access_token) {
    throw new Error(`Token Refresh Failed: ${refreshed.error_description || "Invalid refresh token"}`);
  }

  const newExpiresAt = new Date(Date.now() + (refreshed.expires_in || 3600) * 1000).toISOString();
  await supabaseClient
    .from("supabase_connections")
    .update({
      encrypted_access_token: refreshed.access_token,
      encrypted_refresh_token: refreshed.refresh_token || conn.encrypted_refresh_token,
      access_token_expires_at: newExpiresAt,
      updated_at: new Date().toISOString(),
    })
    .eq("user_id", userId);

  return refreshed.access_token;
}

function generateSecurePassword(): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=";
  const array = new Uint8Array(24);
  crypto.getRandomValues(array);
  return Array.from(array, (byte) => chars[byte % chars.length]).join("");
}

const BOOTSTRAP_SQL = `
-- 0. Enable extensions & helper functions
create extension if not exists "pgcrypto";

create or replace function public.current_merchant_id()
returns uuid language sql stable security definer set search_path = public as $$
  select id from public.merchants where user_id = auth.uid() limit 1;
$$;

-- 1. Merchants Table
create table if not exists public.merchants (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  business_name text not null default 'My Store',
  email text,
  phone text,
  business_type text,
  website text,
  default_number text,
  webhook_secret text default encode(gen_random_bytes(32), 'hex'),
  created_at timestamptz default now()
);
create unique index if not exists merchants_user_id_unique on public.merchants(user_id);
create unique index if not exists merchants_webhook_secret_unique on public.merchants(webhook_secret);

create or replace function public.handle_new_merchant_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.merchants (user_id, business_name, email, phone)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'business_name', 'My Business'),
    new.email,
    nullif(new.raw_user_meta_data ->> 'phone', '')
  )
  on conflict (user_id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_create_merchant on auth.users;
create trigger on_auth_user_create_merchant after insert on auth.users
for each row execute function public.handle_new_merchant_user();

-- 2. Merchant Payment Numbers
create table if not exists public.merchant_numbers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  number text not null,
  type text not null check (type in ('bKash','Nagad','Rocket','Upay')),
  account_type text not null default 'Personal',
  is_default boolean default false,
  active boolean default true,
  created_at timestamptz default now()
);
create unique index if not exists merchant_numbers_merchant_number_unique on public.merchant_numbers(merchant_id, number);

-- 3. Orders Table
create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants not null default public.current_merchant_id(),
  tran_id text not null,
  amount numeric(12,2) not null,
  currency text default 'BDT',
  cus_phone text not null,
  cus_email text,
  cus_name text,
  status text not null default 'PENDING' check (status in ('PENDING','PAID','EXPIRED','CANCELLED')),
  product_name text,
  product_category text,
  callback_url text,
  success_url text,
  fail_url text,
  cancel_url text,
  metadata jsonb default '{}',
  expires_at timestamptz not null,
  paid_at timestamptz,
  payment_method text,
  sender_number text,
  matched_trx_id text,
  manual_match boolean default false,
  created_at timestamptz default now()
);
create unique index if not exists orders_merchant_tran_id_unique on public.orders(merchant_id, tran_id);

create table if not exists public.payment_gateway_settings (
  merchant_id uuid primary key references public.merchants(id) on delete cascade,
  gateway_enabled boolean not null default true,
  min_amount numeric(12,2) not null default 10.00,
  max_amount numeric(12,2) not null default 100000.00,
  daily_limit numeric(14,2) not null default 500000.00,
  receipt_retry_limit integer not null default 5 check (receipt_retry_limit between 1 and 10),
  auto_receipt_retry boolean not null default true,
  customer_receipts_enabled boolean not null default true,
  merchant_receipts_enabled boolean not null default true,
  merchant_receipt_email text,
  sms_notifications_enabled boolean not null default false,
  notification_phone text,
  success_callback_url text,
  failure_callback_url text,
  cancel_callback_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint payment_gateway_amount_range check (min_amount > 0 and max_amount >= min_amount and daily_limit >= max_amount)
);

create table if not exists public.payment_receipt_outbox (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null references public.merchants(id) on delete cascade,
  order_id uuid not null references public.orders(id) on delete cascade,
  event_type text not null default 'PAYMENT_PAID' check (event_type = 'PAYMENT_PAID'),
  status text not null default 'PENDING' check (status in ('PENDING','PROCESSING','SENT','FAILED')),
  attempts integer not null default 0 check (attempts between 0 and 20),
  next_attempt_at timestamptz not null default now(), claimed_at timestamptz,
  delivered_at timestamptz, last_error text,
  provider_response jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  unique(order_id,event_type)
);
create index if not exists payment_receipt_outbox_dispatch_idx
  on public.payment_receipt_outbox(status,next_attempt_at,created_at)
  where status in ('PENDING','PROCESSING','FAILED');
insert into public.payment_gateway_settings(merchant_id)
select id from public.merchants on conflict(merchant_id) do nothing;
create or replace function public.ensure_payment_gateway_settings()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.payment_gateway_settings(merchant_id,merchant_receipt_email)
  values(new.id,nullif(trim(new.email),'')) on conflict(merchant_id) do nothing;
  return new;
end;
$$;
drop trigger if exists ensure_payment_gateway_settings_trigger on public.merchants;
create trigger ensure_payment_gateway_settings_trigger after insert on public.merchants
for each row execute function public.ensure_payment_gateway_settings();

-- 4. Payments Table
create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants not null default public.current_merchant_id(),
  trx_id text,
  amount numeric(12,2) not null,
  sender_number text,
  merchant_number text,
  sms_timestamp timestamptz,
  sms_hash text unique not null,
  status text not null default 'UNMATCHED' check (status in ('MATCHED','UNMATCHED','DUPLICATE')),
  matched_order_id uuid references public.orders,
  created_at timestamptz default now()
);

-- 5. SMS Logs Table
create table if not exists public.sms_logs (
  id uuid primary key default gen_random_uuid(),
  device_id text not null,
  merchant_id uuid references public.merchants not null default public.current_merchant_id(),
  raw_sms text not null,
  parsed_amount numeric(12,2),
  parsed_sender text,
  parsed_trx_id text,
  parsed_timestamp timestamptz,
  sms_hash text unique not null,
  processed boolean default false,
  status text default 'unprocessed' check (status in ('unprocessed','matched','unmatched','duplicate')),
  created_at timestamptz default now()
);

-- 6. Devices Table
create table if not exists public.devices (
  id text primary key,
  user_id uuid references auth.users,
  merchant_id uuid references public.merchants default public.current_merchant_id(),
  fcm_token text,
  device_model text,
  os_version text,
  battery_level integer,
  online boolean default true,
  last_sync timestamptz default now(),
  disabled boolean default false,
  created_at timestamptz default now()
);

-- 7. Appeals Table
create table if not exists public.appeals (
  id uuid primary key default gen_random_uuid(),
  trx_id text not null,
  cus_phone text,
  order_id uuid references public.orders,
  note text,
  screenshot_url text,
  status text default 'PENDING_REVIEW' check (status in ('PENDING_REVIEW','APPROVED','REJECTED')),
  resolved_by uuid references auth.users,
  resolved_at timestamptz,
  created_at timestamptz default now()
);

-- 8. Notifications Table
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  type text,
  title text,
  body text,
  read boolean default false,
  created_at timestamptz default now()
);

-- 9. Security Logs & Settings Table
create table if not exists public.security_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  merchant_id uuid references public.merchants default public.current_merchant_id(),
  event text not null,
  details jsonb,
  ip_address text,
  created_at timestamptz default now()
);

create table if not exists public.security_settings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users on delete cascade default auth.uid(),
  biometric_enabled boolean not null default false,
  updated_at bigint not null default (extract(epoch from now()) * 1000)::bigint
);

create table if not exists public.order_rate_limits (
  id bigint generated always as identity primary key,
  merchant_id uuid not null references public.merchants on delete cascade,
  client_hash text not null,
  created_at timestamptz not null default now()
);
create index if not exists order_rate_limits_lookup_idx on public.order_rate_limits(merchant_id, client_hash, created_at desc);

-- 10. MFS Regex Patterns Table
create table if not exists public.mfs_regex_patterns (
  id uuid primary key default gen_random_uuid(),
  mfs_name text not null check (mfs_name in ('bKash','Nagad','Rocket','Upay')),
  pattern_name text not null,
  regex_pattern text not null,
  active boolean default true,
  created_at timestamptz default now()
);

-- 11. Payment Forms Table
create table if not exists public.payment_forms (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade default public.current_merchant_id(),
  title text not null default 'Untitled Payment Form',
  description text,
  slug text unique not null default ('pay-' || replace(gen_random_uuid()::text, '-', '')),
  template_type text not null default 'BLANK',
  fields jsonb not null default '[]'::jsonb,
  products jsonb not null default '[]'::jsonb,
  pages jsonb not null default '[]'::jsonb,
  theme jsonb not null default '{}'::jsonb,
  status text not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  views_count integer default 0,
  submissions_count integer default 0,
  total_revenue numeric(12,2) default 0.00,
  logo_url text,
  banner_url text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

-- 12. Form Submissions Table
create table if not exists public.form_submissions (
  id uuid primary key default gen_random_uuid(),
  form_id uuid references public.payment_forms on delete cascade,
  order_id uuid references public.orders on delete set null,
  request_id uuid not null default gen_random_uuid(),
  client_hash text,
  customer_name text,
  customer_phone text,
  customer_email text,
  amount_bdt numeric(12,2) not null default 0.00,
  payment_method text not null default 'bKash',
  payment_status text not null default 'NOT_REQUIRED' check (payment_status in ('PAID', 'PENDING', 'FAILED', 'REFUNDED', 'NOT_REQUIRED')),
  trx_id text,
  answers jsonb not null default '{}'::jsonb,
  created_at timestamptz default now()
);
create unique index if not exists form_submissions_form_request_unique on public.form_submissions(form_id, request_id);

-- 13. Customers Table
create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  name text not null,
  phone text not null,
  email text,
  address text,
  opening_balance numeric(12,2) not null default 0.00,
  current_balance numeric(12,2) not null default 0.00,
  status text not null default 'VIP' check (status in ('VIP', 'Risk', 'Inactive', 'Potential')),
  created_at timestamptz default now()
);

-- 14. Suppliers Table
create table if not exists public.suppliers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  name text not null,
  phone text not null,
  email text,
  address text,
  opening_balance numeric(12,2) not null default 0.00,
  current_balance numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- 15. Ledger Transactions Table
create table if not exists public.ledger_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  customer_id uuid references public.customers on delete cascade,
  supplier_id uuid references public.suppliers on delete cascade,
  type text not null check (type in ('credit', 'payment')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  note text,
  product_details jsonb default '[]',
  is_voice_entry boolean default false,
  attachment_url text,
  payment_method text default 'Cash',
  invoice_no text,
  created_at timestamptz default now()
);

-- 16. Products Table
create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  name text not null,
  code text,
  category text default 'General',
  purchase_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  min_stock_threshold numeric(12,2) not null default 5.00,
  unit text not null default 'pcs',
  qr_code text,
  image_url text,
  created_at timestamptz default now()
);

-- 17. Product Variants Table
create table if not exists public.product_variants (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  product_id uuid references public.products on delete cascade not null,
  variant_name text not null default 'Standard',
  supplier_id uuid references public.suppliers on delete set null,
  qr_code text unique not null,
  cost_price numeric(12,2) not null default 0.00,
  asking_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- 18. Stock Transactions Table
create table if not exists public.stock_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  product_id uuid references public.products on delete cascade not null,
  variant_id uuid references public.product_variants on delete set null,
  type text not null check (type in ('in', 'out')),
  quantity numeric(12,2) not null,
  price numeric(12,2) not null default 0.00,
  customer_id uuid references public.customers on delete set null,
  supplier_id uuid references public.suppliers on delete set null,
  reference_note text,
  created_at timestamptz default now()
);

-- 19. Expenses Table
create table if not exists public.expenses (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  category text not null check (category in ('Utilities', 'Rent', 'Transport', 'Operating', 'Payroll', 'Others')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  description text,
  receipt_image_url text,
  payment_method text default 'Cash',
  is_recurring boolean default false,
  created_at timestamptz default now()
);

-- 20. Business Loans Table
create table if not exists public.loans (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  principal_amount numeric(12,2) not null,
  interest_rate numeric(5,2) not null,
  interest_type text not null check (interest_type in ('Flat', 'Reducing')),
  duration_months integer not null,
  monthly_installment numeric(12,2) not null,
  status text not null default 'applied' check (status in ('applied', 'approved', 'disbursed', 'repaid')),
  applied_at timestamptz default now(),
  disbursed_at timestamptz
);

-- 21. POS Sales Invoices Table
create table if not exists public.pos_sales (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  invoice_no text not null,
  customer_id uuid references public.customers on delete set null,
  customer_name text not null default 'Walk-in Customer',
  customer_phone text default '',
  subtotal numeric(12,2) not null default 0.00,
  discount numeric(12,2) not null default 0.00,
  net_total numeric(12,2) not null default 0.00,
  cash_received numeric(12,2) not null default 0.00,
  change_due numeric(12,2) not null default 0.00,
  payment_method text not null default 'Cash' check (payment_method in ('Cash', 'bKash', 'Nagad', 'Rocket', 'Card', 'Due', 'Bank')),
  payment_status text not null default 'PAID' check (payment_status in ('PAID', 'PARTIAL', 'DUE')),
  item_count integer not null default 1,
  cart_items jsonb default '[]',
  timestamp timestamptz default now(),
  created_at timestamptz default now()
);

-- 22. Business Analytics Summary Table
create table if not exists public.business_analytics (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants on delete cascade not null default public.current_merchant_id(),
  total_revenue numeric(12,2) not null default 0.00,
  cash_received numeric(12,2) not null default 0.00,
  total_dues numeric(12,2) not null default 0.00,
  total_payables numeric(12,2) not null default 0.00,
  total_expenses numeric(12,2) not null default 0.00,
  net_profit numeric(12,2) not null default 0.00,
  last_updated timestamptz default now()
);

-- 23. Employees Table
create table if not exists public.employees (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null default public.current_merchant_id() references public.merchants on delete cascade,
  name text not null,
  designation text not null,
  role text not null,
  email text not null,
  phone text not null,
  department text not null default 'General',
  status text not null default 'Active' check (status in ('Active','Inactive')),
  avatar_url text,
  permissions jsonb not null default '[]'::jsonb,
  joined_date date not null default current_date,
  updated_at timestamptz not null default now()
);

-- ============================================================================
-- STORED PROCEDURES & ATOMIC SECURITY FUNCTIONS
-- ============================================================================

create or replace function public.match_payment_atomic(
  p_order_id uuid,
  p_payment_amount numeric,
  p_sender_number text,
  p_trx_id text,
  p_sms_hash text,
  p_sms_log_id uuid
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_merchant_id uuid;
  v_sender_digits text;
begin
  v_sender_digits := regexp_replace(coalesce(p_sender_number,''), '[^0-9]', '', 'g');
  if length(v_sender_digits) < 10 then raise exception 'Full sender number is required for automatic matching'; end if;
  v_sender_digits := right(v_sender_digits,10);
  select o.merchant_id into v_merchant_id
  from public.orders o
  join public.sms_logs s on s.id = p_sms_log_id and s.merchant_id = o.merchant_id
  where o.id = p_order_id and o.status = 'PENDING' and o.expires_at >= now()
    and o.amount = p_payment_amount
    and right(regexp_replace(o.cus_phone,'[^0-9]','','g'),10) = v_sender_digits
    and s.processed = false and s.sms_hash = p_sms_hash
    and s.parsed_amount = p_payment_amount and s.parsed_trx_id = p_trx_id
    and right(regexp_replace(s.parsed_sender,'[^0-9]','','g'),10) = v_sender_digits
  for update of o, s;

  if not found then raise exception 'Order or SMS log is no longer eligible for matching'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_merchant_id::text || ':' || p_trx_id, 0));
  if exists(select 1 from public.payments where merchant_id=v_merchant_id and trx_id=p_trx_id) then
    raise exception 'Provider transaction ID was already processed';
  end if;
  update public.orders set status = 'PAID', paid_at = now(), sender_number = p_sender_number,
    matched_trx_id = p_trx_id where id = p_order_id;
  insert into public.payments (merchant_id, trx_id, amount, sender_number, sms_hash, status, matched_order_id)
  values (v_merchant_id, p_trx_id, p_payment_amount, p_sender_number, p_sms_hash, 'MATCHED', p_order_id);
  update public.sms_logs set processed = true, status = 'matched' where id = p_sms_log_id;
end;
$$;

create or replace function public.resolve_appeal_atomic(
  p_appeal_id uuid, p_action text, p_order_id uuid, p_resolved_by uuid
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_merchant_id uuid;
  v_appeal public.appeals%rowtype;
  v_order public.orders%rowtype;
begin
  if p_action not in ('APPROVED', 'REJECTED') then raise exception 'Invalid appeal action'; end if;
  select id into v_merchant_id from public.merchants where user_id = p_resolved_by;
  if v_merchant_id is null then raise exception 'Merchant not found'; end if;
  select * into v_appeal from public.appeals
    where id = p_appeal_id and order_id = p_order_id and status = 'PENDING_REVIEW' for update;
  if not found then raise exception 'Appeal is not pending for this order'; end if;
  select * into v_order from public.orders
    where id = p_order_id and merchant_id = v_merchant_id for update;
  if not found then raise exception 'Order does not belong to merchant'; end if;
  if p_action = 'APPROVED' and v_order.status <> 'PENDING' then
    raise exception 'Only a pending order can be approved';
  end if;
  update public.appeals set status = p_action, resolved_at = now(), resolved_by = p_resolved_by
    where id = p_appeal_id;
  if p_action = 'APPROVED' then
    update public.orders set status = 'PAID', paid_at = now(),
      sender_number = coalesce(v_appeal.cus_phone, v_order.cus_phone),
      matched_trx_id = v_appeal.trx_id, manual_match = true where id = p_order_id;
    update public.payments set status = 'MATCHED', matched_order_id = p_order_id
      where merchant_id = v_merchant_id and trx_id = v_appeal.trx_id;
    if not found then
      insert into public.payments
        (merchant_id, trx_id, amount, sender_number, sms_timestamp, sms_hash, status, matched_order_id)
      values (v_merchant_id, v_appeal.trx_id, v_order.amount,
        coalesce(v_appeal.cus_phone, v_order.cus_phone), now(),
        encode(digest('appeal:' || p_appeal_id::text, 'sha256'), 'hex'), 'MATCHED', p_order_id);
    end if;
  end if;
end;
$$;

create or replace function public.check_order_rate_limit(
  p_merchant_id uuid, p_client_hash text, p_limit integer default 30,
  p_window_seconds integer default 60
) returns boolean language plpgsql security definer set search_path = public as $$
declare v_count integer;
begin
  if p_client_hash is null or length(p_client_hash) <> 64
     or p_limit < 1 or p_window_seconds < 1 then
    raise exception 'Invalid rate-limit parameters';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_merchant_id::text || ':' || p_client_hash, 0));
  delete from public.order_rate_limits
    where merchant_id = p_merchant_id and client_hash = p_client_hash
      and created_at < now() - make_interval(secs => p_window_seconds);
  select count(*) into v_count from public.order_rate_limits
    where merchant_id = p_merchant_id and client_hash = p_client_hash;
  if v_count >= p_limit then return false; end if;
  insert into public.order_rate_limits(merchant_id, client_hash) values (p_merchant_id, p_client_hash);
  return true;
end;
$$;

create or replace function public.get_daily_revenue(merchant_id_param uuid)
returns numeric language sql stable security definer set search_path = public as $$
  select coalesce(sum(amount), 0.00)
  from public.orders
  where merchant_id = merchant_id_param
    and status = 'PAID'
    and paid_at >= date_trunc('day', now());
$$;

drop function if exists public.create_hosted_form_submission(uuid,uuid,text,text,text,numeric,text,boolean,jsonb,text);
create function public.create_hosted_form_submission(
  p_form_id uuid, p_request_id uuid, p_customer_name text, p_customer_phone text,
  p_customer_email text, p_amount numeric, p_payment_method text,
  p_payment_required boolean, p_answers jsonb, p_client_hash text
) returns table(
  submission_id uuid, order_id uuid, transaction_id text, payment_status text,
  amount numeric, payment_number text, payment_method text, expires_at timestamptz, created boolean
)
language plpgsql security definer set search_path = public as $$
declare
  v_form public.payment_forms%rowtype;
  v_existing public.form_submissions%rowtype;
  v_order_id uuid;
  v_submission_id uuid;
  v_transaction_id text;
  v_number text;
  v_method text;
  v_expires_at timestamptz;
begin
  if p_request_id is null then raise exception 'request_id is required'; end if;
  select * into v_form from public.payment_forms
    where id = p_form_id and status = 'PUBLISHED' for update;
  if not found then raise exception 'published form not found'; end if;

  select * into v_existing from public.form_submissions
    where form_id = p_form_id and request_id = p_request_id;
  if found then
    return query select v_existing.id, v_existing.order_id, o.tran_id,
      v_existing.payment_status, v_existing.amount_bdt, n.number,
      v_existing.payment_method, o.expires_at, false
      from (select 1) seed
      left join public.orders o on o.id = v_existing.order_id
      left join public.merchant_numbers n on n.merchant_id = v_form.merchant_id
        and n.type = v_existing.payment_method and n.active = true
      order by n.is_default desc nulls last limit 1;
    return;
  end if;

  if coalesce((v_form.theme ->> 'close_after_limit')::boolean, false)
     and (select count(*) from public.form_submissions where form_id = p_form_id)
       >= greatest(coalesce((v_form.theme ->> 'max_responses')::integer, 1000), 1) then
    raise exception 'form response limit reached';
  end if;
  if (coalesce((v_form.theme ->> 'enable_closing_timeline')::boolean, false)
      or coalesce((v_form.theme ->> 'enableClosingTimeline')::boolean, false))
     and nullif(coalesce(v_form.theme ->> 'closing_deadline_epoch', v_form.theme ->> 'closingDeadlineEpoch'), '')::bigint > 0
     and (extract(epoch from now()) * 1000)::bigint > coalesce(v_form.theme ->> 'closing_deadline_epoch', v_form.theme ->> 'closingDeadlineEpoch')::bigint then
    raise exception 'form response limit reached: deadline expired';
  end if;
  if coalesce((v_form.theme ->> 'one_response_per_user')::boolean, false)
     and nullif(p_client_hash, '') is not null
     and exists (select 1 from public.form_submissions where form_id = p_form_id and client_hash = p_client_hash) then
    raise exception 'response already submitted';
  end if;

  if p_payment_required then
    if p_amount is null or p_amount <= 0 or p_amount > 10000000 then raise exception 'invalid payment amount'; end if;
    if nullif(trim(p_customer_phone), '') is null then raise exception 'phone is required for payment'; end if;
    select mn.number, mn.type into v_number, v_method from public.merchant_numbers mn
      where mn.merchant_id = v_form.merchant_id and mn.active = true
        and (upper(coalesce(p_payment_method, 'AUTO')) = 'AUTO' or upper(mn.type) = upper(p_payment_method))
      order by mn.is_default desc, mn.created_at asc limit 1;
    if v_number is null then raise exception 'payment number is not configured'; end if;
    v_order_id := gen_random_uuid();
    v_transaction_id := 'FORM-' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISSMS') || '-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
    v_expires_at := now() + interval '15 minutes';
    insert into public.orders(
      id, merchant_id, tran_id, amount, cus_phone, cus_email, cus_name,
      product_name, product_category, status, payment_method, callback_url, expires_at, metadata
    ) values (
      v_order_id, v_form.merchant_id, v_transaction_id, p_amount, trim(p_customer_phone),
      nullif(trim(p_customer_email), ''), nullif(trim(p_customer_name), ''),
      v_form.title, 'HOSTED_FORM', 'PENDING', v_method,
      case when coalesce((v_form.theme ->> 'payment_callback_enabled')::boolean, false)
        then nullif(v_form.theme ->> 'payment_callback_url', '') else null end,
      v_expires_at, jsonb_build_object('form_id', p_form_id, 'request_id', p_request_id)
    );
  else
    v_method := 'None';
  end if;

  v_submission_id := gen_random_uuid();
  insert into public.form_submissions(
    id, form_id, order_id, request_id, client_hash, customer_name, customer_phone,
    customer_email, amount_bdt, payment_method, payment_status, answers
  ) values (
    v_submission_id, p_form_id, v_order_id, p_request_id, nullif(p_client_hash, ''),
    nullif(trim(p_customer_name), ''), nullif(trim(p_customer_phone), ''),
    nullif(trim(p_customer_email), ''), coalesce(p_amount, 0), v_method,
    case when p_payment_required then 'PENDING' else 'NOT_REQUIRED' end,
    coalesce(p_answers, '{}'::jsonb)
  );
  update public.payment_forms set submissions_count = submissions_count + 1, updated_at = now() where id = p_form_id;
  return query select v_submission_id, v_order_id, v_transaction_id,
    case when p_payment_required then 'PENDING' else 'NOT_REQUIRED' end,
    coalesce(p_amount, 0), v_number, v_method, v_expires_at, true;
end;
$$;

create or replace function public.record_hosted_form_view(p_form_id uuid)
returns void language sql security definer set search_path = public as $$
  update public.payment_forms set views_count = views_count + 1
    where id = p_form_id and status = 'PUBLISHED';
$$;

create or replace function public.sync_hosted_form_payment_status()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_form_id uuid;
begin
  if old.status is distinct from new.status and new.status in ('PAID', 'EXPIRED', 'CANCELLED') then
    update public.form_submissions
      set payment_status = case new.status when 'PAID' then 'PAID' else 'FAILED' end,
          trx_id = coalesce(new.matched_trx_id, trx_id)
      where order_id = new.id and payment_status = 'PENDING'
      returning form_id into v_form_id;
    if new.status = 'PAID' and v_form_id is not null then
      update public.payment_forms set total_revenue = total_revenue + new.amount, updated_at = now()
        where id = v_form_id;
    end if;
  end if;
  return new;
end;
$$;
drop trigger if exists sync_hosted_form_payment_status_trigger on public.orders;
create trigger sync_hosted_form_payment_status_trigger after update of status on public.orders
for each row execute function public.sync_hosted_form_payment_status();

create or replace function public.cancel_order(order_id_param uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.orders set status = 'CANCELLED'
  where id = order_id_param and status = 'PENDING' and merchant_id = public.current_merchant_id();
end;
$$;

create or replace function public.extend_order(order_id_param uuid, new_expiry_param bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.orders set expires_at = to_timestamp(new_expiry_param / 1000)
  where id = order_id_param and status = 'PENDING' and merchant_id = public.current_merchant_id();
end;
$$;

create or replace function public.protect_paid_order_transition()
returns trigger language plpgsql set search_path = public as $$
begin
  if new.status = 'PAID' and old.status is distinct from 'PAID'
     and coalesce(auth.jwt() ->> 'role', '') <> 'service_role' then
    raise exception 'Only the trusted payment processor may mark an order paid';
  end if;
  return new;
end;
$$;
drop trigger if exists protect_paid_order_transition on public.orders;
create trigger protect_paid_order_transition before update of status on public.orders
for each row execute function public.protect_paid_order_transition();

create or replace function public.enforce_payment_gateway_order_rules()
returns trigger language plpgsql security definer set search_path = public as $$
declare v public.payment_gateway_settings%rowtype; v_committed numeric;
begin
  select * into v from public.payment_gateway_settings where merchant_id=new.merchant_id;
  if not found then return new; end if;
  if not v.gateway_enabled then raise exception 'payment gateway is disabled'; end if;
  if new.amount < v.min_amount or new.amount > v.max_amount then raise exception 'payment amount is outside the configured gateway range'; end if;
  if new.payment_method is not null and not exists(select 1 from public.merchant_numbers n where n.merchant_id=new.merchant_id and n.active=true and upper(n.type)=upper(new.payment_method)) then
    raise exception 'payment number is not configured';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(new.merchant_id::text||current_date::text,0));
  select coalesce(sum(amount),0) into v_committed from public.orders
    where merchant_id=new.merchant_id and ((status='PAID' and paid_at>=date_trunc('day',now())) or (status='PENDING' and expires_at>=now()));
  if v_committed+new.amount > v.daily_limit then raise exception 'daily payment limit would be exceeded'; end if;
  return new;
end;
$$;
drop trigger if exists enforce_payment_gateway_order_rules_trigger on public.orders;
create trigger enforce_payment_gateway_order_rules_trigger before insert on public.orders
for each row execute function public.enforce_payment_gateway_order_rules();

create or replace function public.enqueue_verified_payment_receipt()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'PAID' and old.status is distinct from 'PAID' then
    insert into public.payment_receipt_outbox(merchant_id,order_id)
    values(new.merchant_id,new.id) on conflict(order_id,event_type) do nothing;
  end if;
  return new;
end;
$$;
drop trigger if exists enqueue_verified_payment_receipt_trigger on public.orders;
create trigger enqueue_verified_payment_receipt_trigger after update of status on public.orders
for each row execute function public.enqueue_verified_payment_receipt();

create or replace function public.claim_payment_receipts(p_limit integer default 10,p_lease_seconds integer default 120)
returns setof public.payment_receipt_outbox language plpgsql security definer set search_path = public as $$
begin
  if p_limit < 1 or p_limit > 50 or p_lease_seconds < 30 or p_lease_seconds > 900 then raise exception 'Invalid receipt claim parameters'; end if;
  return query update public.payment_receipt_outbox o
    set status='PROCESSING',claimed_at=now(),attempts=attempts+1,updated_at=now(),last_error=null
    where o.id in (select c.id from public.payment_receipt_outbox c
      left join public.payment_gateway_settings s on s.merchant_id=c.merchant_id
      where c.attempts < coalesce(s.receipt_retry_limit,5) and c.next_attempt_at <= now()
        and (c.status='PENDING' or (c.status='FAILED' and coalesce(s.auto_receipt_retry,true)) or (c.status='PROCESSING' and c.claimed_at < now()-make_interval(secs=>p_lease_seconds)))
      order by c.created_at for update of c skip locked limit p_limit)
    returning o.*;
end;
$$;

create or replace function public.complete_payment_receipt(p_event_id uuid,p_succeeded boolean,p_provider_response jsonb default '{}'::jsonb,p_error text default null)
returns void language plpgsql security definer set search_path = public as $$
declare v_attempts integer;
begin
  select attempts into v_attempts from public.payment_receipt_outbox where id=p_event_id and status='PROCESSING' for update;
  if not found then raise exception 'Receipt event is not currently claimed'; end if;
  update public.payment_receipt_outbox set status=case when p_succeeded then 'SENT' else 'FAILED' end,
    delivered_at=case when p_succeeded then now() else delivered_at end,
    next_attempt_at=case when p_succeeded then next_attempt_at else now()+make_interval(secs=>least(3600,(power(2,least(v_attempts,10))::integer*15))) end,
    provider_response=coalesce(p_provider_response,'{}'::jsonb),
    last_error=case when p_succeeded then null else left(coalesce(p_error,'Receipt delivery failed'),1000) end,updated_at=now()
  where id=p_event_id;
end;
$$;

create or replace function public.match_payment_atomic(
  p_order_id uuid,
  p_payment_amount numeric,
  p_sender_number text,
  p_trx_id text,
  p_sms_hash text,
  p_sms_log_id uuid
) returns void language plpgsql security definer set search_path = public as $$
declare v_merchant_id uuid; v_sender_digits text;
begin
  v_sender_digits := regexp_replace(coalesce(p_sender_number,''), '[^0-9]', '', 'g');
  if length(v_sender_digits) < 10 then raise exception 'Full sender number is required for automatic matching'; end if;
  v_sender_digits := right(v_sender_digits,10);

  select o.merchant_id into v_merchant_id
  from public.orders o join public.sms_logs s on s.id=p_sms_log_id and s.merchant_id=o.merchant_id
  where o.id=p_order_id and o.status='PENDING' and o.expires_at>=now() and o.amount=p_payment_amount
    and right(regexp_replace(o.cus_phone,'[^0-9]','','g'),10)=v_sender_digits
    and s.processed=false and s.sms_hash=p_sms_hash and s.parsed_amount=p_payment_amount
    and right(regexp_replace(s.parsed_sender,'[^0-9]','','g'),10)=v_sender_digits
    and s.parsed_trx_id=p_trx_id
  for update of o,s;
  if not found then raise exception 'Order or SMS log is no longer eligible for matching'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_merchant_id::text||':'||p_trx_id,0));
  if exists(select 1 from public.payments where merchant_id=v_merchant_id and trx_id=p_trx_id) then
    raise exception 'Provider transaction ID was already processed';
  end if;
  update public.orders set status='PAID',paid_at=now(),sender_number=p_sender_number,matched_trx_id=p_trx_id where id=p_order_id;
  insert into public.payments(merchant_id,trx_id,amount,sender_number,sms_hash,status,matched_order_id)
    values(v_merchant_id,p_trx_id,p_payment_amount,p_sender_number,p_sms_hash,'MATCHED',p_order_id);
  update public.sms_logs set processed=true,status='matched' where id=p_sms_log_id;
end;
$$;

create or replace function public.match_payment_manual(
  p_order_id uuid,
  p_payment_id uuid
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_payment_amount numeric;
  v_sender_number text;
  v_trx_id text;
  v_sms_hash text;
  v_merchant_id uuid;
begin
  select amount, sender_number, trx_id, sms_hash, merchant_id
  into v_payment_amount, v_sender_number, v_trx_id, v_sms_hash, v_merchant_id
  from public.payments
  where id = p_payment_id and status = 'UNMATCHED';

  if not found then
    raise exception 'Payment log not found or already matched.';
  end if;

  update public.orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = v_sender_number,
    matched_trx_id = v_trx_id,
    manual_match = true
  where id = p_order_id and status = 'PENDING';

  update public.payments set
    status = 'MATCHED',
    matched_order_id = p_order_id
  where id = p_payment_id;
end;
$$;

create or replace function public.resolve_appeal_atomic(
  p_appeal_id uuid,
  p_action text,
  p_order_id uuid,
  p_resolved_by uuid
) returns void language plpgsql security definer set search_path = public as $$
declare
  v_merchant_id uuid;
  v_appeal public.appeals%rowtype;
  v_order public.orders%rowtype;
begin
  if p_action not in ('APPROVED', 'REJECTED') then
    raise exception 'Invalid appeal action';
  end if;

  select id into v_merchant_id from public.merchants where user_id = p_resolved_by;
  if v_merchant_id is null then raise exception 'Merchant not found'; end if;

  select * into v_appeal from public.appeals
  where id = p_appeal_id and order_id = p_order_id and status = 'PENDING_REVIEW'
  for update;
  if not found then raise exception 'Appeal is not pending for this order'; end if;

  select * into v_order from public.orders
  where id = p_order_id and merchant_id = v_merchant_id
  for update;
  if not found then raise exception 'Order does not belong to merchant'; end if;

  if p_action = 'APPROVED' and v_order.status <> 'PENDING' then
    raise exception 'Only a pending order can be approved';
  end if;

  update public.appeals
  set status = p_action, resolved_at = now(), resolved_by = p_resolved_by
  where id = p_appeal_id;

  if p_action = 'APPROVED' then
    update public.orders
    set status = 'PAID', paid_at = now(),
        sender_number = coalesce(v_appeal.cus_phone, v_order.cus_phone),
        matched_trx_id = v_appeal.trx_id, manual_match = true
    where id = p_order_id;

    update public.payments
    set status = 'MATCHED', matched_order_id = p_order_id
    where merchant_id = v_merchant_id and trx_id = v_appeal.trx_id;

    if not found then
      insert into public.payments
        (merchant_id, trx_id, amount, sender_number, sms_timestamp, sms_hash, status, matched_order_id)
      values
        (v_merchant_id, v_appeal.trx_id, v_order.amount,
         coalesce(v_appeal.cus_phone, v_order.cus_phone), now(),
         encode(digest('appeal:' || p_appeal_id::text, 'sha256'), 'hex'),
         'MATCHED', p_order_id);
    end if;
  end if;
end;
$$;

create or replace function public.check_order_rate_limit(
  p_merchant_id uuid,
  p_client_hash text,
  p_limit integer default 30,
  p_window_seconds integer default 60
) returns boolean language plpgsql security definer set search_path = public as $$
declare
  v_count integer;
begin
  if p_client_hash is null or length(p_client_hash) <> 64
     or p_limit < 1 or p_window_seconds < 1 then
    raise exception 'Invalid rate-limit parameters';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_merchant_id::text || ':' || p_client_hash, 0));
  delete from public.order_rate_limits
  where merchant_id = p_merchant_id and client_hash = p_client_hash
    and created_at < now() - make_interval(secs => p_window_seconds);

  select count(*) into v_count
  from public.order_rate_limits
  where merchant_id = p_merchant_id and client_hash = p_client_hash;

  if v_count >= p_limit then return false; end if;
  insert into public.order_rate_limits(merchant_id, client_hash)
  values (p_merchant_id, p_client_hash);
  return true;
end;
$$;

-- Permissions & Grants
revoke all on function public.current_merchant_id() from public, anon;
grant execute on function public.current_merchant_id() to authenticated, service_role;
revoke all on function public.match_payment_atomic(uuid, numeric, text, text, text, uuid) from public, anon, authenticated;
grant execute on function public.match_payment_atomic(uuid, numeric, text, text, text, uuid) to service_role;
revoke all on function public.resolve_appeal_atomic(uuid, text, uuid, uuid) from public, anon, authenticated;
grant execute on function public.resolve_appeal_atomic(uuid, text, uuid, uuid) to service_role;
revoke all on function public.check_order_rate_limit(uuid, text, integer, integer) from public, anon, authenticated;
grant execute on function public.check_order_rate_limit(uuid, text, integer, integer) to service_role;
revoke all on function public.create_hosted_form_submission(uuid,uuid,text,text,text,numeric,text,boolean,jsonb,text) from public, anon, authenticated;
grant execute on function public.create_hosted_form_submission(uuid,uuid,text,text,text,numeric,text,boolean,jsonb,text) to service_role;
revoke all on function public.record_hosted_form_view(uuid) from public, anon, authenticated;
grant execute on function public.record_hosted_form_view(uuid) to service_role;
revoke all on function public.enqueue_verified_payment_receipt() from public, anon, authenticated;
revoke all on function public.ensure_payment_gateway_settings() from public, anon, authenticated;
revoke all on function public.enforce_payment_gateway_order_rules() from public, anon, authenticated;
revoke all on function public.claim_payment_receipts(integer,integer) from public, anon, authenticated;
grant execute on function public.claim_payment_receipts(integer,integer) to service_role;
revoke all on function public.complete_payment_receipt(uuid,boolean,jsonb,text) from public, anon, authenticated;
grant execute on function public.complete_payment_receipt(uuid,boolean,jsonb,text) to service_role;
grant execute on function public.cancel_order(uuid) to authenticated, service_role;
grant execute on function public.extend_order(uuid, bigint) to authenticated, service_role;

-- Private hosted-form attachments. The hosted-form edge function is the only
-- anonymous-facing writer and uses the service role after field validation.
insert into storage.buckets (id, name, public, file_size_limit)
values ('form-uploads', 'form-uploads', false, 10485760)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;

-- Seed default MFS Regex Patterns
insert into public.mfs_regex_patterns (mfs_name, pattern_name, regex_pattern) values
  ('bKash', 'traditional', 'You have received Tk (\\\\d+\\\\.?\\\\d*).*?from (\\\\d+).*?TrxID (\\\\w+) at (\\\\d{2}/\\\\d{2}/\\\\d{4} \\\\d{2}:\\\\d{2})'),
  ('bKash', 'cash-in', 'Cash In Tk ([\\\\d,]+\\\\.?\\\\d*).*?from (\\\\d+).*?TrxID (\\\\w+) at (\\\\d{2}/\\\\d{2}/\\\\d{4} \\\\d{2}:\\\\d{2})'),
  ('Nagad', 'received', 'Money Received\\\\..*?Amount:\\\\s*Tk (\\\\d+\\\\.?\\\\d*).*?Sender:\\\\s*(\\\\d+).*?TxnID:\\\\s*(\\\\w+).*?(\\\\d{2}/\\\\d{2}/\\\\d{4} \\\\d{2}:\\\\d{2})'),
  ('Rocket', 'cash-in', 'Cash-In from A/C:\\\\s*\\\\*+\\\\d+\\\\s*Tk([\\\\d,]+\\\\.?\\\\d*)[\\\\s\\\\S]*?TxnId:(\\\\d+)\\\\s+Date:(\\\\d{2}-[A-Z]{3}-\\\\d{2}\\\\s+\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\s*[ap]m)'),
  ('Upay', 'received', '(?:Money Received|Received Taka)[\\\\s\\\\S]*?Taka\\\\s*([\\\\d,]+\\\\.?\\\\d*)[\\\\s\\\\S]*?from\\\\s*(\\\\d+)[\\\\s\\\\S]*?TrxID\\\\s*(\\\\w+)[\\\\s\\\\S]*?(\\\\d{2}/\\\\d{2}/\\\\d{4} \\\\d{2}:\\\\d{2})')
on conflict do nothing;

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

alter table public.merchants enable row level security;
alter table public.merchant_numbers enable row level security;
alter table public.orders enable row level security;
alter table public.payments enable row level security;
alter table public.sms_logs enable row level security;
alter table public.devices enable row level security;
alter table public.appeals enable row level security;
alter table public.notifications enable row level security;
alter table public.security_logs enable row level security;
alter table public.security_settings enable row level security;
alter table public.order_rate_limits enable row level security;
alter table public.mfs_regex_patterns enable row level security;
alter table public.payment_forms enable row level security;
alter table public.form_submissions enable row level security;
alter table public.customers enable row level security;
alter table public.suppliers enable row level security;
alter table public.ledger_transactions enable row level security;
alter table public.products enable row level security;
alter table public.product_variants enable row level security;
alter table public.stock_transactions enable row level security;
alter table public.expenses enable row level security;
alter table public.loans enable row level security;
alter table public.pos_sales enable row level security;
alter table public.business_analytics enable row level security;
alter table public.employees enable row level security;
alter table public.payment_gateway_settings enable row level security;
alter table public.payment_receipt_outbox enable row level security;

create policy "Merchants policy" on public.merchants for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Merchant numbers policy" on public.merchant_numbers for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Orders policy" on public.orders for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Payments policy" on public.payments for select to authenticated using (merchant_id = public.current_merchant_id());
create policy "SMS logs policy" on public.sms_logs for select to authenticated using (merchant_id = public.current_merchant_id());
create policy "SMS logs insert" on public.sms_logs for insert to authenticated with check (merchant_id = public.current_merchant_id() and processed = false);
create policy "Devices policy" on public.devices for all to authenticated using (user_id = auth.uid() or merchant_id = public.current_merchant_id()) with check (user_id = auth.uid() and merchant_id = public.current_merchant_id());
create policy "Appeals policy" on public.appeals for select to authenticated using (exists (select 1 from public.orders o where o.id = appeals.order_id and o.merchant_id = public.current_merchant_id()));
create policy "Notifications policy" on public.notifications for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Security logs policy" on public.security_logs for all to authenticated using (user_id = auth.uid() or merchant_id = public.current_merchant_id()) with check (user_id = auth.uid() and merchant_id = public.current_merchant_id());
create policy "MFS regex patterns policy" on public.mfs_regex_patterns for select to anon, authenticated using (active = true);
create policy "Payment forms policy" on public.payment_forms for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Published payment forms public read" on public.payment_forms for select to anon using (status = 'PUBLISHED');
create policy "Form submissions policy" on public.form_submissions for all to authenticated using (exists (select 1 from public.payment_forms f where f.id = form_submissions.form_id and f.merchant_id = public.current_merchant_id())) with check (exists (select 1 from public.payment_forms f where f.id = form_submissions.form_id and f.merchant_id = public.current_merchant_id()));
create policy "Customers policy" on public.customers for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Suppliers policy" on public.suppliers for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Ledger policy" on public.ledger_transactions for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Products policy" on public.products for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Product variants policy" on public.product_variants for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Stock transactions policy" on public.stock_transactions for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Expenses policy" on public.expenses for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Loans policy" on public.loans for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Employees policy" on public.employees for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "POS sales policy" on public.pos_sales for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Analytics policy" on public.business_analytics for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Security settings policy" on public.security_settings for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Payment gateway settings policy" on public.payment_gateway_settings for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
create policy "Payment receipt outbox read policy" on public.payment_receipt_outbox for select to authenticated using (merchant_id = public.current_merchant_id());
`;

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabaseClient = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { user_id, action, project_ref, organization_slug, project_name, db_password } = await req.json();

    if (!user_id) {
      return new Response(JSON.stringify({ error: "user_id is required" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const token = await getAccessToken(supabaseClient, user_id);

    // ACTION 1: CREATE PROJECT
    if (action === "CREATE_PROJECT") {
      if (!organization_slug || !project_name) {
        return new Response(JSON.stringify({ error: "organization_slug and project_name required" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const generatedPass = db_password || generateSecurePassword();

      const createRes = await fetch("https://api.supabase.com/v1/projects", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          name: project_name,
          organization_slug: organization_slug,
          db_pass: generatedPass,
          region_selection: {
            type: "smartGroup",
            code: "apac",
          },
        }),
      });

      const newProj = await createRes.json();
      if (!createRes.ok) {
        return new Response(JSON.stringify({ error: newProj.message || "Failed to create project" }), {
          status: createRes.status,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      await supabaseClient
        .from("supabase_connections")
        .update({
          selected_project_ref: newProj.id,
          organization_slug,
          provisioning_status: "PROJECT_CREATING",
          updated_at: new Date().toISOString(),
        })
        .eq("user_id", user_id);

      return new Response(JSON.stringify({ status: "PROJECT_CREATING", project_ref: newProj.id }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    // ACTION 2: CHECK HEALTH & WAIT FOR ACTIVE_HEALTHY
    if (action === "CHECK_HEALTH") {
      const ref = project_ref;
      if (!ref) {
        return new Response(JSON.stringify({ error: "project_ref required" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      const healthRes = await fetch(`https://api.supabase.com/v1/projects/${ref}/health`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      const healthData = healthRes.ok ? await healthRes.json() : [];
      const isHealthy = Array.isArray(healthData)
        ? healthData.every((item: any) => item.status === "ACTIVE_HEALTHY" || item.status === "HEALTHY" || item.status === "COMING_UP")
        : true;

      return new Response(
        JSON.stringify({
          status: isHealthy ? "ACTIVE_HEALTHY" : "PROVISIONING",
          health: healthData,
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // ACTION 3: APPLY SQL SCHEMA & RETRIEVE KEYS
    if (action === "APPLY_SCHEMA_AND_FINALIZE") {
      const ref = project_ref;
      if (!ref) {
        return new Response(JSON.stringify({ error: "project_ref required" }), {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      // 1. Run SQL Query Endpoint
      const sqlRes = await fetch(`https://api.supabase.com/v1/projects/${ref}/database/query`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ query: BOOTSTRAP_SQL }),
      });

      if (!sqlRes.ok) {
        const sqlErr = await sqlRes.text();
        console.warn("SQL Query warning/notice:", sqlErr);
      }

      // 2. Fetch API Keys
      const keysRes = await fetch(`https://api.supabase.com/v1/projects/${ref}/api-keys`, {
        headers: { Authorization: `Bearer ${token}` },
      });

      const keys = keysRes.ok ? await keysRes.json() : [];
      const pubKeyObj = keys.find((k: any) => k.name === "anon" || k.name === "publishable") || keys[0];
      const publishableKey = pubKeyObj ? pubKeyObj.api_key || pubKeyObj.key : "";
      const projectUrl = `https://${ref}.supabase.co`;

      // Update Connection DB
      await supabaseClient
        .from("supabase_connections")
        .update({
          selected_project_ref: ref,
          publishable_key: publishableKey,
          project_url: projectUrl,
          provisioning_status: "COMPLETE",
          connection_status: "ACTIVE",
          last_verified_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
        })
        .eq("user_id", user_id);

      // Sync active credentials to merchant_gateway_settings
      await supabaseClient
        .from("merchant_gateway_settings")
        .upsert(
          {
            merchant_id: user_id,
            supabase_url: projectUrl,
            supabase_anon_key: publishableKey,
            updated_at: new Date().toISOString(),
          },
          { onConflict: "merchant_id" }
        );

      return new Response(
        JSON.stringify({
          status: "COMPLETE",
          project_ref: ref,
          project_url: projectUrl,
          publishable_key: publishableKey,
        }),
        { headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(JSON.stringify({ error: "Invalid action" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: any) {
    console.error("provision Edge Function Exception:", err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
