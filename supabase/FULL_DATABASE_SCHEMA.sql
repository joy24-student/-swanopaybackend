-- ============================================================================
-- SWAPNOPAY FULL MASTER SUPABASE DATABASE SCHEMA (CONSOLIDATED MIGRATIONS 01-07)
-- Copy and paste this single SQL script directly into your Supabase SQL Editor
-- ============================================================================

-- 0. Enable extensions
create extension if not exists "pgcrypto";

-- 1. Merchants Table (Created first so helper functions can reference it)
create table if not exists merchants (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  business_name text not null default 'My Store',
  email text,
  phone text,
  business_type text,
  website text,
  default_number text,
  webhook_secret text,
  created_at timestamptz default now()
);
create unique index if not exists merchants_user_id_unique on merchants(user_id);

-- Helper function: get current user's merchant_id
create or replace function current_merchant_id()
returns uuid language sql stable security definer set search_path = public as $$
  select id from merchants where user_id = auth.uid() limit 1;
$$;

create or replace function handle_new_merchant_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into merchants (user_id, business_name, email, phone)
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
for each row execute function handle_new_merchant_user();
insert into merchants (user_id, business_name, email, phone)
select u.id, coalesce(u.raw_user_meta_data ->> 'business_name', 'My Business'),
  u.email, nullif(u.raw_user_meta_data ->> 'phone', '')
from auth.users u
where not exists (select 1 from merchants m where m.user_id = u.id);

-- 2. Merchant Payment Numbers
create table if not exists merchant_numbers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  number text not null,
  type text not null check (type in ('bKash','Nagad','Rocket','Upay')),
  is_default boolean default false,
  active boolean default true,
  created_at timestamptz default now()
);

-- 3. Orders Table
create table if not exists orders (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants not null,
  tran_id text unique not null,
  amount numeric(12,2) not null,
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

-- 4. Payments Table
create table if not exists payments (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants not null,
  trx_id text,
  amount numeric(12,2) not null,
  sender_number text,
  merchant_number text,
  sms_timestamp timestamptz,
  sms_hash text unique not null,
  status text not null default 'UNMATCHED' check (status in ('MATCHED','UNMATCHED','DUPLICATE')),
  matched_order_id uuid references orders,
  created_at timestamptz default now()
);

-- 5. SMS Logs Table
create table if not exists sms_logs (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null,
  merchant_id uuid references merchants not null,
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
create table if not exists devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  merchant_id uuid references merchants,
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
create table if not exists appeals (
  id uuid primary key default gen_random_uuid(),
  trx_id text not null,
  cus_phone text,
  order_id uuid references orders,
  note text,
  screenshot_url text,
  status text default 'PENDING_REVIEW' check (status in ('PENDING_REVIEW','APPROVED','REJECTED')),
  resolved_by uuid references auth.users,
  resolved_at timestamptz,
  created_at timestamptz default now()
);

-- 8. Notifications Table
create table if not exists notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  type text,
  title text,
  body text,
  read boolean default false,
  created_at timestamptz default now()
);

-- 9. Security Logs Table
create table if not exists security_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  merchant_id uuid references merchants,
  event text not null,
  details jsonb,
  ip_address text,
  created_at timestamptz default now()
);

-- 10. Security Settings Table
create table if not exists security_settings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users on delete cascade default auth.uid(),
  biometric_enabled boolean default true,
  updated_at bigint default extract(epoch from now()) * 1000
);

create table if not exists order_rate_limits (
  id bigint generated always as identity primary key,
  merchant_id uuid not null references merchants on delete cascade,
  client_hash text not null,
  created_at timestamptz not null default now()
);
create index if not exists order_rate_limits_lookup_idx
  on order_rate_limits(merchant_id, client_hash, created_at desc);

-- 11. MFS Regex Patterns Table
create table if not exists mfs_regex_patterns (
  id uuid primary key default gen_random_uuid(),
  mfs_name text not null check (mfs_name in ('bKash','Nagad','Rocket','Upay')),
  pattern_name text not null,
  regex_pattern text not null,
  active boolean default true,
  created_at timestamptz default now()
);

-- 12. Payment Forms Table
create table if not exists payment_forms (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade,
  title text not null default 'Untitled Payment Form',
  description text,
  slug text unique not null default gen_random_uuid()::text,
  template_type text default 'BLANK',
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

-- 13. Form Submissions Table
create table if not exists form_submissions (
  id uuid primary key default gen_random_uuid(),
  form_id uuid references payment_forms on delete cascade,
  order_id uuid references orders on delete set null,
  request_id uuid not null default gen_random_uuid(),
  client_hash text,
  customer_name text,
  customer_phone text,
  customer_email text,
  amount_bdt numeric(12,2) default 0.00,
  payment_method text default 'bKash',
  payment_status text default 'NOT_REQUIRED' check (payment_status in ('PAID', 'PENDING', 'FAILED', 'REFUNDED', 'NOT_REQUIRED')),
  trx_id text,
  answers jsonb not null default '{}'::jsonb,
  created_at timestamptz default now()
);
create unique index if not exists form_submissions_form_request_unique on form_submissions(form_id, request_id);

-- 14. Customers Table
create table if not exists customers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  phone text not null,
  email text,
  address text,
  opening_balance numeric(12,2) not null default 0.00,
  current_balance numeric(12,2) not null default 0.00,
  status text not null default 'VIP' check (status in ('VIP', 'Risk', 'Inactive', 'Potential')),
  created_at timestamptz default now()
);

-- 15. Suppliers Table
create table if not exists suppliers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  phone text not null,
  email text,
  address text,
  opening_balance numeric(12,2) not null default 0.00,
  current_balance numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- 16. Ledger Transactions Table
create table if not exists ledger_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  customer_id uuid references customers on delete cascade,
  supplier_id uuid references suppliers on delete cascade,
  type text not null check (type in ('credit', 'payment')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  note text,
  product_details jsonb default '[]',
  is_voice_entry boolean default false,
  attachment_url text,
  created_at timestamptz default now()
);

-- 17. Products Table
create table if not exists products (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  code text,
  category text,
  purchase_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  min_stock_threshold numeric(12,2) not null default 5.00,
  unit text not null default 'pcs',
  created_at timestamptz default now()
);

-- 18. Product Variants Table
create table if not exists product_variants (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  product_id uuid references products on delete cascade not null,
  variant_name text not null default 'Standard',
  supplier_id uuid references suppliers on delete set null,
  qr_code text unique not null,
  cost_price numeric(12,2) not null default 0.00,
  asking_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- 19. Stock Transactions Table
create table if not exists stock_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  product_id uuid references products on delete cascade not null,
  type text not null check (type in ('in', 'out')),
  quantity numeric(12,2) not null,
  price numeric(12,2) not null,
  customer_id uuid references customers on delete set null,
  supplier_id uuid references suppliers on delete set null,
  created_at timestamptz default now()
);

-- 20. Expenses Table
create table if not exists expenses (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  category text not null check (category in ('Utilities', 'Rent', 'Transport', 'Operating', 'Payroll', 'Others')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  description text,
  receipt_image_url text,
  payment_method text default 'Cash',
  is_recurring boolean default false,
  created_at timestamptz default now()
);

-- 21. Business Loans Table
create table if not exists loans (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  principal_amount numeric(12,2) not null,
  interest_rate numeric(5,2) not null,
  interest_type text not null check (interest_type in ('Flat', 'Reducing')),
  duration_months integer not null,
  monthly_installment numeric(12,2) not null,
  status text not null default 'applied' check (status in ('applied', 'approved', 'disbursed', 'repaid')),
  applied_at timestamptz default now(),
  disbursed_at timestamptz
);

-- 22. POS Sales Invoices Table
create table if not exists pos_sales (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  invoice_no text not null,
  customer_id uuid references customers on delete set null,
  customer_name text not null default 'Walk-in Customer',
  customer_phone text default '',
  subtotal numeric(12,2) not null default 0.00,
  discount numeric(12,2) not null default 0.00,
  net_total numeric(12,2) not null default 0.00,
  cash_received numeric(12,2) not null default 0.00,
  change_due numeric(12,2) not null default 0.00,
  payment_method text not null default 'Cash' check (payment_method in ('Cash', 'bKash', 'Card', 'Due')),
  payment_status text not null default 'PAID' check (payment_status in ('PAID', 'PARTIAL', 'DUE')),
  item_count integer not null default 1,
  cart_items jsonb default '[]',
  timestamp timestamptz default now(),
  created_at timestamptz default now()
);

-- 23. Business Analytics Summary Table
create table if not exists business_analytics (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  total_revenue numeric(12,2) not null default 0.00,
  cash_received numeric(12,2) not null default 0.00,
  total_dues numeric(12,2) not null default 0.00,
  total_payables numeric(12,2) not null default 0.00,
  total_expenses numeric(12,2) not null default 0.00,
  net_profit numeric(12,2) not null default 0.00,
  last_updated timestamptz default now()
);

-- ============================================================================
-- STORED PROCEDURES & RPC FUNCTIONS
-- ============================================================================

create or replace function match_payment_atomic(
  p_order_id uuid,
  p_payment_amount numeric,
  p_sender_number text,
  p_trx_id text,
  p_sms_hash text,
  p_sms_log_id uuid
) returns void language plpgsql as $$
begin
  update orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = p_sender_number,
    matched_trx_id = p_trx_id
  where id = p_order_id and status = 'PENDING';

  insert into payments (merchant_id, trx_id, amount, sender_number, sms_hash, status, matched_order_id)
  select merchant_id, p_trx_id, p_payment_amount, p_sender_number, p_sms_hash, 'MATCHED', p_order_id
  from orders where id = p_order_id;

  update sms_logs set processed = true, status = 'matched' where id = p_sms_log_id;
end;
$$;

create or replace function match_payment_manual(
  p_order_id uuid,
  p_payment_id uuid
) returns void language plpgsql as $$
declare
  v_payment_amount numeric;
  v_sender_number text;
  v_trx_id text;
  v_sms_hash text;
  v_merchant_id uuid;
begin
  select amount, sender_number, trx_id, sms_hash, merchant_id
  into v_payment_amount, v_sender_number, v_trx_id, v_sms_hash, v_merchant_id
  from payments
  where id = p_payment_id and status = 'UNMATCHED';

  if not found then
    raise exception 'Payment log not found or already matched.';
  end if;

  update orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = v_sender_number,
    matched_trx_id = v_trx_id,
    manual_match = true
  where id = p_order_id and status = 'PENDING';

  update payments set
    status = 'MATCHED',
    matched_order_id = p_order_id
  where id = p_payment_id;
end;
$$;

create or replace function get_daily_revenue(merchant_id_param uuid)
returns numeric language sql stable as $$
  select coalesce(sum(amount), 0.00)
  from orders
  where merchant_id = merchant_id_param
    and status = 'PAID'
    and paid_at >= date_trunc('day', now());
$$;

create or replace function cancel_order(order_id_param uuid)
returns void language plpgsql as $$
begin
  update orders set status = 'CANCELLED' where id = order_id_param and status = 'PENDING';
end;
$$;

create or replace function extend_order(order_id_param uuid, new_expiry_param bigint)
returns void language plpgsql as $$
begin
  update orders set expires_at = to_timestamp(new_expiry_param / 1000) where id = order_id_param and status = 'PENDING';
end;
$$;

create or replace function resolve_appeal(appeal_id_param uuid, action_param text, order_id_param uuid default null)
returns void language plpgsql as $$
begin
  if action_param = 'APPROVED' then
    update appeals set status = 'APPROVED', resolved_at = now() where id = appeal_id_param;
    if order_id_param is not null then
      update orders set status = 'PAID', paid_at = now(), manual_match = true where id = order_id_param;
    end if;
  else
    update appeals set status = 'REJECTED', resolved_at = now() where id = appeal_id_param;
  end if;
end;
$$;

-- Production credential defaults and hardened automatic matching.
alter table public.merchants alter column webhook_secret set default encode(gen_random_bytes(32), 'hex');
update public.merchants set webhook_secret = encode(gen_random_bytes(32), 'hex') where webhook_secret is null or webhook_secret = '';
alter table public.merchants alter column webhook_secret set not null;
create unique index if not exists merchants_webhook_secret_unique on public.merchants(webhook_secret);
create unique index if not exists merchant_numbers_merchant_number_unique on public.merchant_numbers(merchant_id, number);
alter table public.orders drop constraint if exists orders_tran_id_key;
create unique index if not exists orders_merchant_tran_id_unique on public.orders(merchant_id, tran_id);

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
begin
  select o.merchant_id into v_merchant_id
  from public.orders o
  join public.sms_logs s on s.id = p_sms_log_id and s.merchant_id = o.merchant_id
  where o.id = p_order_id and o.status = 'PENDING' and o.expires_at >= now()
    and o.amount = p_payment_amount and o.cus_phone = p_sender_number
    and s.processed = false and s.sms_hash = p_sms_hash
    and s.parsed_amount = p_payment_amount and s.parsed_sender = p_sender_number
    and s.parsed_trx_id = p_trx_id
  for update of o, s;
  if not found then raise exception 'Order or SMS log is no longer eligible for matching'; end if;
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

revoke all on function public.current_merchant_id() from public, anon;
grant execute on function public.current_merchant_id() to authenticated, service_role;
revoke all on function public.handle_new_merchant_user() from public, anon, authenticated;
revoke all on function public.match_payment_atomic(uuid, numeric, text, text, text, uuid) from public, anon, authenticated;
grant execute on function public.match_payment_atomic(uuid, numeric, text, text, text, uuid) to service_role;
revoke all on function public.resolve_appeal_atomic(uuid, text, uuid, uuid) from public, anon, authenticated;
grant execute on function public.resolve_appeal_atomic(uuid, text, uuid, uuid) to service_role;
revoke all on function public.check_order_rate_limit(uuid, text, integer, integer) from public, anon, authenticated;
grant execute on function public.check_order_rate_limit(uuid, text, integer, integer) to service_role;
revoke all on function public.resolve_appeal(uuid, text, uuid) from public, anon, authenticated;
grant execute on function public.resolve_appeal(uuid, text, uuid) to service_role;
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
revoke all on function public.protect_paid_order_transition() from public, anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('appeal-screenshots', 'appeal-screenshots', false, 5242880,
  array['image/jpeg', 'image/png', 'image/webp', 'application/pdf'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;
insert into storage.buckets (id, name, public, file_size_limit)
values ('form-uploads', 'form-uploads', false, 10485760)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;
drop policy if exists "prod_appeal_screenshots_own" on storage.objects;
create policy "prod_appeal_screenshots_own" on storage.objects for all to authenticated
using (bucket_id = 'appeal-screenshots' and (storage.foldername(name))[1] = public.current_merchant_id()::text)
with check (bucket_id = 'appeal-screenshots' and (storage.foldername(name))[1] = public.current_merchant_id()::text);

-- Seed default MFS Regex Patterns
insert into mfs_regex_patterns (mfs_name, pattern_name, regex_pattern) values
  ('bKash', 'traditional', 'You have received Tk (\d+\.?\d*).*?from (\d+).*?TrxID (\w+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})'),
  ('bKash', 'cash-in', 'Cash In Tk ([\d,]+\.?\d*).*?from (\d+).*?TrxID (\w+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})'),
  ('Nagad', 'received', 'Money Received\..*?Amount:\s*Tk (\d+\.?\d*).*?Sender:\s*(\d+).*?TxnID:\s*(\w+).*?(\d{2}/\d{2}/\d{4} \d{2}:\d{2})'),
  ('Rocket', 'cash-in', 'Cash-In from A/C:\s*\*+\d+\s*Tk([\d,]+\.?\d*)[\s\S]*?TxnId:(\d+)\s+Date:(\d{2}-[A-Z]{3}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*[ap]m)'),
  ('Upay', 'received', '(?:Money Received|Received Taka)[\s\S]*?Taka\s*([\d,]+\.?\d*)[\s\S]*?from\s*(\d+)[\s\S]*?TrxID\s*(\w+)[\s\S]*?(\d{2}/\d{2}/\d{4} \d{2}:\d{2})')
on conflict do nothing;

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

-- Team screen storage and safe tenant defaults. The authenticated user's
-- merchant id is derived by Postgres; mobile clients never choose a tenant id.
alter table merchant_numbers add column if not exists account_type text not null default 'Personal';
create table if not exists employees (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null default current_merchant_id() references merchants on delete cascade,
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
alter table merchant_numbers alter column merchant_id set default current_merchant_id();
alter table orders alter column merchant_id set default current_merchant_id();
alter table payments alter column merchant_id set default current_merchant_id();
alter table sms_logs alter column merchant_id set default current_merchant_id();
alter table devices alter column merchant_id set default current_merchant_id();
alter table payment_forms alter column merchant_id set default current_merchant_id();
alter table customers alter column merchant_id set default current_merchant_id();
alter table suppliers alter column merchant_id set default current_merchant_id();
alter table ledger_transactions alter column merchant_id set default current_merchant_id();
alter table products alter column merchant_id set default current_merchant_id();
alter table product_variants alter column merchant_id set default current_merchant_id();
alter table stock_transactions alter column merchant_id set default current_merchant_id();
alter table expenses alter column merchant_id set default current_merchant_id();
alter table loans alter column merchant_id set default current_merchant_id();
alter table pos_sales alter column merchant_id set default current_merchant_id();
alter table business_analytics alter column merchant_id set default current_merchant_id();

alter table merchants enable row level security;
alter table merchant_numbers enable row level security;
alter table orders enable row level security;
alter table payments enable row level security;
alter table sms_logs enable row level security;
alter table devices enable row level security;
alter table appeals enable row level security;
alter table notifications enable row level security;
alter table security_logs enable row level security;
alter table mfs_regex_patterns enable row level security;
alter table payment_forms enable row level security;
alter table form_submissions enable row level security;
alter table customers enable row level security;
alter table suppliers enable row level security;
alter table ledger_transactions enable row level security;
alter table products enable row level security;
alter table product_variants enable row level security;
alter table stock_transactions enable row level security;
alter table expenses enable row level security;
alter table loans enable row level security;
alter table employees enable row level security;
alter table pos_sales enable row level security;
alter table business_analytics enable row level security;
alter table security_settings enable row level security;
alter table order_rate_limits enable row level security;

create policy "Merchants policy" on merchants for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Merchant numbers policy" on merchant_numbers for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Orders policy" on orders for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Payments policy" on payments for select to authenticated using (merchant_id = current_merchant_id());
create policy "SMS logs policy" on sms_logs for select to authenticated using (merchant_id = current_merchant_id());
create policy "SMS logs insert" on sms_logs for insert to authenticated with check (merchant_id = current_merchant_id() and processed = false);
create policy "Devices policy" on devices for all to authenticated using (user_id = auth.uid() or merchant_id = current_merchant_id()) with check (user_id = auth.uid() and merchant_id = current_merchant_id());
create policy "Appeals policy" on appeals for select to authenticated using (exists (select 1 from orders o where o.id = appeals.order_id and o.merchant_id = current_merchant_id()));
create policy "Notifications policy" on notifications for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Security logs policy" on security_logs for all to authenticated using (user_id = auth.uid() or merchant_id = current_merchant_id()) with check (user_id = auth.uid() and merchant_id = current_merchant_id());
create policy "MFS regex patterns policy" on mfs_regex_patterns for select to anon, authenticated using (active = true);
create policy "Payment forms policy" on payment_forms for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Published payment forms public read" on payment_forms for select to anon using (status = 'PUBLISHED');
create policy "Form submissions policy" on form_submissions for all to authenticated using (exists (select 1 from payment_forms f where f.id = form_submissions.form_id and f.merchant_id = current_merchant_id())) with check (exists (select 1 from payment_forms f where f.id = form_submissions.form_id and f.merchant_id = current_merchant_id()));
create policy "Customers policy" on customers for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Suppliers policy" on suppliers for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Ledger policy" on ledger_transactions for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Products policy" on products for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Product variants policy" on product_variants for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Stock transactions policy" on stock_transactions for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Expenses policy" on expenses for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Loans policy" on loans for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Employees policy" on employees for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "POS sales policy" on pos_sales for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Analytics policy" on business_analytics for all to authenticated using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());
create policy "Security settings policy" on security_settings for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Enable Realtime
alter publication supabase_realtime add table orders;

-- ============================================================================
-- CONTROL PLANE & SUPABASE OAUTH MANAGEMENT TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS control_oauth_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL,
    state_hash TEXT NOT NULL UNIQUE,
    pkce_verifier_encrypted TEXT NOT NULL,
    redirect_back TEXT,
    consumed BOOLEAN DEFAULT false,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_oauth_tx_state ON control_oauth_transactions(state_hash);

CREATE TABLE IF NOT EXISTS supabase_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL UNIQUE,
    organization_slug TEXT,
    selected_project_ref TEXT,
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    access_token_expires_at TIMESTAMPTZ NOT NULL,
    oauth_scopes TEXT[] DEFAULT '{}',
    connection_status TEXT DEFAULT 'ACTIVE',
    provisioning_status TEXT DEFAULT 'NOT_STARTED',
    publishable_key TEXT,
    project_url TEXT,
    last_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_supabase_conn_user ON supabase_connections(user_id);
CREATE INDEX IF NOT EXISTS idx_supabase_conn_project ON supabase_connections(selected_project_ref);

ALTER TABLE control_oauth_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE supabase_connections ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Service Role full access on control_oauth_transactions"
    ON control_oauth_transactions FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Service Role full access on supabase_connections"
    ON supabase_connections FOR ALL
    USING (auth.role() = 'service_role');

CREATE POLICY "Users read own connection status"
    ON supabase_connections FOR SELECT
    USING (auth.uid()::text = user_id);
