-- Production hardening and missing screen-backed tables.
-- This migration intentionally removes the permissive USING (true) policies
-- from the old consolidated development schema.

create or replace function public.current_merchant_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select id from public.merchants where user_id = auth.uid() limit 1;
$$;

alter table public.merchants add column if not exists email text;
alter table public.merchants add column if not exists phone text;
alter table public.merchants add column if not exists business_type text;
alter table public.merchants add column if not exists website text;

create or replace function public.handle_new_merchant_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
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

create unique index if not exists merchants_user_id_unique on public.merchants(user_id);
drop trigger if exists on_auth_user_create_merchant on auth.users;
create trigger on_auth_user_create_merchant
  after insert on auth.users
  for each row execute function public.handle_new_merchant_user();

insert into public.merchants (user_id, business_name, email, phone)
select u.id, coalesce(u.raw_user_meta_data ->> 'business_name', 'My Business'),
  u.email, nullif(u.raw_user_meta_data ->> 'phone', '')
from auth.users u
where not exists (select 1 from public.merchants m where m.user_id = u.id);

-- Let authenticated clients omit merchant_id; the database derives it from
-- the JWT instead of trusting a client-supplied tenant identifier.
alter table public.merchant_numbers alter column merchant_id set default public.current_merchant_id();
alter table public.orders alter column merchant_id set default public.current_merchant_id();
alter table public.payments alter column merchant_id set default public.current_merchant_id();
alter table public.sms_logs alter column merchant_id set default public.current_merchant_id();
alter table public.devices alter column merchant_id set default public.current_merchant_id();
alter table public.payment_forms alter column merchant_id set default public.current_merchant_id();
alter table public.customers alter column merchant_id set default public.current_merchant_id();
alter table public.suppliers alter column merchant_id set default public.current_merchant_id();
alter table public.ledger_transactions alter column merchant_id set default public.current_merchant_id();
alter table public.products alter column merchant_id set default public.current_merchant_id();
alter table public.product_variants alter column merchant_id set default public.current_merchant_id();
alter table public.stock_transactions alter column merchant_id set default public.current_merchant_id();
alter table public.expenses alter column merchant_id set default public.current_merchant_id();
alter table public.loans alter column merchant_id set default public.current_merchant_id();
alter table public.pos_sales alter column merchant_id set default public.current_merchant_id();
alter table public.business_analytics alter column merchant_id set default public.current_merchant_id();

alter table public.merchant_numbers add column if not exists account_type text not null default 'Personal';

-- Migration 05 only created a minimal form schema. Bring existing projects up
-- to the fields used by the form builder and its database-backed dashboards.
alter table public.payment_forms add column if not exists slug text;
update public.payment_forms set slug = 'pay-' || replace(id::text, '-', '') where slug is null or slug = '';
alter table public.payment_forms alter column slug set not null;
create unique index if not exists payment_forms_slug_unique on public.payment_forms(slug);
alter table public.payment_forms add column if not exists template_type text not null default 'SINGLE_PRODUCT';
alter table public.payment_forms add column if not exists products jsonb not null default '[]'::jsonb;
alter table public.payment_forms add column if not exists theme jsonb not null default '{}'::jsonb;
alter table public.payment_forms add column if not exists status text not null default 'DRAFT'
  check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
alter table public.payment_forms add column if not exists views_count integer not null default 0;
alter table public.payment_forms add column if not exists submissions_count integer not null default 0;
alter table public.payment_forms add column if not exists total_revenue numeric(12,2) not null default 0;
alter table public.payment_forms add column if not exists banner_url text;
alter table public.payment_forms add column if not exists updated_at timestamptz not null default now();
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'payment_forms' and column_name = 'amount'
  ) then
    alter table public.payment_forms alter column amount set default 0;
  end if;
end $$;

alter table public.form_submissions alter column order_id drop not null;
alter table public.form_submissions add column if not exists customer_name text;
alter table public.form_submissions add column if not exists customer_phone text;
alter table public.form_submissions add column if not exists customer_email text;
alter table public.form_submissions add column if not exists amount_bdt numeric(12,2) not null default 0;
alter table public.form_submissions add column if not exists payment_method text not null default 'bKash';
alter table public.form_submissions add column if not exists payment_status text not null default 'PENDING'
  check (payment_status in ('PAID', 'PENDING', 'FAILED', 'REFUNDED'));
alter table public.form_submissions add column if not exists trx_id text;

-- The original migration did not define the local-security preference table.
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
create index if not exists order_rate_limits_lookup_idx
  on public.order_rate_limits(merchant_id, client_hash, created_at desc);
alter table public.order_rate_limits enable row level security;

create table if not exists public.employees (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null default public.current_merchant_id() references public.merchants on delete cascade,
  name text not null,
  designation text not null,
  role text not null,
  email text not null,
  phone text not null,
  department text not null default 'General',
  status text not null default 'Active' check (status in ('Active', 'Inactive')),
  avatar_url text,
  permissions jsonb not null default '[]'::jsonb,
  joined_date date not null default current_date,
  updated_at timestamptz not null default now()
);
create index if not exists employees_merchant_id_idx on public.employees(merchant_id);

-- A device PIN is never cloud data. Keep only non-secret device preferences.
alter table public.security_settings add column if not exists user_id uuid references auth.users on delete cascade;
alter table public.security_settings alter column user_id set default auth.uid();
alter table public.security_settings drop column if exists pin_code;
alter table public.security_settings enable row level security;
alter table public.employees enable row level security;
alter table public.pos_sales enable row level security;
alter table public.business_analytics enable row level security;

-- Remove permissive policies shipped by the legacy development bundle.
drop policy if exists "Merchants policy" on public.merchants;
drop policy if exists "Merchant numbers policy" on public.merchant_numbers;
drop policy if exists "Orders policy" on public.orders;
drop policy if exists "Payments policy" on public.payments;
drop policy if exists "SMS logs policy" on public.sms_logs;
drop policy if exists "Devices policy" on public.devices;
drop policy if exists "Appeals policy" on public.appeals;
drop policy if exists "Notifications policy" on public.notifications;
drop policy if exists "Security logs policy" on public.security_logs;
drop policy if exists "MFS regex patterns policy" on public.mfs_regex_patterns;
drop policy if exists "Payment forms policy" on public.payment_forms;
drop policy if exists "Form submissions policy" on public.form_submissions;
drop policy if exists "Customers policy" on public.customers;
drop policy if exists "Suppliers policy" on public.suppliers;
drop policy if exists "Ledger policy" on public.ledger_transactions;
drop policy if exists "Products policy" on public.products;
drop policy if exists "Product variants policy" on public.product_variants;
drop policy if exists "Stock transactions policy" on public.stock_transactions;
drop policy if exists "Expenses policy" on public.expenses;
drop policy if exists "Loans policy" on public.loans;
drop policy if exists "Merchant own record" on public.merchants;
drop policy if exists "Merchant numbers access" on public.merchant_numbers;
drop policy if exists "Orders merchant access" on public.orders;
drop policy if exists "Orders anon read by id" on public.orders;
drop policy if exists "Orders anon update cancel" on public.orders;
drop policy if exists "Orders anon insert" on public.orders;
drop policy if exists "Payments merchant access" on public.payments;
drop policy if exists "SMS logs insert by device" on public.sms_logs;
drop policy if exists "SMS logs read merchant" on public.sms_logs;
drop policy if exists "Devices own" on public.devices;
drop policy if exists "Devices merchant read" on public.devices;
drop policy if exists "Appeals merchant manage" on public.appeals;
drop policy if exists "Appeals anon insert" on public.appeals;
drop policy if exists "Appeals anon select" on public.appeals;
drop policy if exists "Notifications user" on public.notifications;
drop policy if exists "Security logs merchant" on public.security_logs;
drop policy if exists "Security logs insert" on public.security_logs;
drop policy if exists "Allow anonymous read access" on public.mfs_regex_patterns;
drop policy if exists "Allow authenticated write access" on public.mfs_regex_patterns;
drop policy if exists "Allow select payment_forms to anyone" on public.payment_forms;
drop policy if exists "Allow insert payment_forms for authenticated merchants" on public.payment_forms;
drop policy if exists "Allow all payment_forms management for owners" on public.payment_forms;
drop policy if exists "Allow select submissions for owners" on public.form_submissions;
drop policy if exists "Allow insert submissions to anyone" on public.form_submissions;
drop policy if exists "Customers merchant access" on public.customers;
drop policy if exists "Suppliers merchant access" on public.suppliers;
drop policy if exists "Ledger transactions merchant access" on public.ledger_transactions;
drop policy if exists "Products merchant access" on public.products;
drop policy if exists "Product variants merchant access" on public.product_variants;
drop policy if exists "Stock transactions merchant access" on public.stock_transactions;
drop policy if exists "Expenses merchant access" on public.expenses;
drop policy if exists "Loans merchant access" on public.loans;
drop policy if exists "Merchants can manage POS sales" on public.pos_sales;
drop policy if exists "Merchants can view analytics" on public.business_analytics;

-- Replace every app-owned policy with an authenticated tenant boundary.
drop policy if exists "prod_merchants_own" on public.merchants;
create policy "prod_merchants_own" on public.merchants for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "prod_merchant_numbers_own" on public.merchant_numbers;
create policy "prod_merchant_numbers_own" on public.merchant_numbers for all to authenticated
  using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());

drop policy if exists "prod_orders_own" on public.orders;
create policy "prod_orders_own" on public.orders for all to authenticated
  using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());

drop policy if exists "prod_payments_own" on public.payments;
create policy "prod_payments_own" on public.payments for select to authenticated
  using (merchant_id = public.current_merchant_id());

drop policy if exists "prod_sms_logs_own" on public.sms_logs;
create policy "prod_sms_logs_own" on public.sms_logs for select to authenticated
  using (merchant_id = public.current_merchant_id());
drop policy if exists "prod_sms_logs_insert" on public.sms_logs;
create policy "prod_sms_logs_insert" on public.sms_logs for insert to authenticated
  with check (merchant_id = public.current_merchant_id() and processed = false);

drop policy if exists "prod_devices_own" on public.devices;
create policy "prod_devices_own" on public.devices for all to authenticated
  using (user_id = auth.uid() or merchant_id = public.current_merchant_id())
  with check (user_id = auth.uid() and merchant_id = public.current_merchant_id());

drop policy if exists "prod_appeals_own" on public.appeals;
create policy "prod_appeals_own" on public.appeals for select to authenticated
  using (exists (select 1 from public.orders o where o.id = appeals.order_id and o.merchant_id = public.current_merchant_id()));

drop policy if exists "prod_notifications_own" on public.notifications;
create policy "prod_notifications_own" on public.notifications for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "prod_security_logs_own" on public.security_logs;
create policy "prod_security_logs_own" on public.security_logs for all to authenticated
  using (user_id = auth.uid() or merchant_id = public.current_merchant_id())
  with check (user_id = auth.uid() and merchant_id = public.current_merchant_id());

drop policy if exists "prod_security_settings_own" on public.security_settings;
create policy "prod_security_settings_own" on public.security_settings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "prod_patterns_read" on public.mfs_regex_patterns;
create policy "prod_patterns_read" on public.mfs_regex_patterns for select to anon, authenticated using (active = true);

drop policy if exists "prod_payment_forms_owner" on public.payment_forms;
create policy "prod_payment_forms_owner" on public.payment_forms for all to authenticated
  using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());
drop policy if exists "prod_payment_forms_public_read" on public.payment_forms;
create policy "prod_payment_forms_public_read" on public.payment_forms for select to anon using (status = 'PUBLISHED');

drop policy if exists "prod_form_submissions_owner" on public.form_submissions;
create policy "prod_form_submissions_owner" on public.form_submissions for all to authenticated
  using (exists (select 1 from public.payment_forms f where f.id = form_submissions.form_id and f.merchant_id = public.current_merchant_id()))
  with check (exists (select 1 from public.payment_forms f where f.id = form_submissions.form_id and f.merchant_id = public.current_merchant_id()));

drop policy if exists "prod_employees_own" on public.employees;
create policy "prod_employees_own" on public.employees for all to authenticated
  using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id());

do $$
declare table_name text;
begin
  foreach table_name in array array[
    'customers','suppliers','ledger_transactions','products','product_variants',
    'stock_transactions','expenses','loans','pos_sales','business_analytics'
  ] loop
    execute format('drop policy if exists %I on public.%I', 'prod_' || table_name || '_own', table_name);
    execute format(
      'create policy %I on public.%I for all to authenticated using (merchant_id = public.current_merchant_id()) with check (merchant_id = public.current_merchant_id())',
      'prod_' || table_name || '_own', table_name
    );
  end loop;
end $$;

-- Only backend/service-role code should invoke privileged matching functions.
-- API credentials and request identifiers must be generated server-side and
-- scoped to one merchant.
alter table public.merchants alter column webhook_secret set default encode(gen_random_bytes(32), 'hex');
update public.merchants set webhook_secret = encode(gen_random_bytes(32), 'hex') where webhook_secret is null or webhook_secret = '';
alter table public.merchants alter column webhook_secret set not null;
create unique index if not exists merchants_webhook_secret_unique on public.merchants(webhook_secret);

-- Upserts for wallet numbers must be stable and tenant scoped.
delete from public.merchant_numbers older
using public.merchant_numbers newer
where older.merchant_id = newer.merchant_id
  and older.number = newer.number
  and older.id > newer.id;
create unique index if not exists merchant_numbers_merchant_number_unique
  on public.merchant_numbers(merchant_id, number);

alter table public.orders drop constraint if exists orders_tran_id_key;
create unique index if not exists orders_merchant_tran_id_unique on public.orders(merchant_id, tran_id);

-- Only the trusted SMS webhook may perform the automatic payment transition.
-- Row locks and the checks below make duplicate webhook deliveries safe.
create or replace function public.match_payment_atomic(
  p_order_id uuid,
  p_payment_amount numeric,
  p_sender_number text,
  p_trx_id text,
  p_sms_hash text,
  p_sms_log_id uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_merchant_id uuid;
begin
  select o.merchant_id into v_merchant_id
  from public.orders o
  join public.sms_logs s on s.id = p_sms_log_id and s.merchant_id = o.merchant_id
  where o.id = p_order_id
    and o.status = 'PENDING'
    and o.expires_at >= now()
    and o.amount = p_payment_amount
    and o.cus_phone = p_sender_number
    and s.processed = false
    and s.sms_hash = p_sms_hash
    and s.parsed_amount = p_payment_amount
    and s.parsed_sender = p_sender_number
    and s.parsed_trx_id = p_trx_id
  for update of o, s;

  if not found then
    raise exception 'Order or SMS log is no longer eligible for matching';
  end if;

  update public.orders set
    status = 'PAID', paid_at = now(), sender_number = p_sender_number, matched_trx_id = p_trx_id
  where id = p_order_id;

  insert into public.payments
    (merchant_id, trx_id, amount, sender_number, sms_hash, status, matched_order_id)
  values
    (v_merchant_id, p_trx_id, p_payment_amount, p_sender_number, p_sms_hash, 'MATCHED', p_order_id);

  update public.sms_logs set processed = true, status = 'matched' where id = p_sms_log_id;
end;
$$;

create or replace function public.resolve_appeal_atomic(
  p_appeal_id uuid,
  p_action text,
  p_order_id uuid,
  p_resolved_by uuid
) returns void
language plpgsql
security definer
set search_path = public
as $$
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
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
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

revoke all on function public.current_merchant_id() from public, anon;
grant execute on function public.current_merchant_id() to authenticated, service_role;
revoke all on function public.handle_new_merchant_user() from public, anon, authenticated;
revoke all on function public.match_payment_atomic(uuid, numeric, text, text, text, uuid) from public, anon, authenticated;
grant execute on function public.match_payment_atomic(uuid, numeric, text, text, text, uuid) to service_role;
revoke all on function public.resolve_appeal_atomic(uuid, text, uuid, uuid) from public, anon, authenticated;
grant execute on function public.resolve_appeal_atomic(uuid, text, uuid, uuid) to service_role;
revoke all on function public.check_order_rate_limit(uuid, text, integer, integer) from public, anon, authenticated;
grant execute on function public.check_order_rate_limit(uuid, text, integer, integer) to service_role;
do $$
begin
  if to_regprocedure('public.resolve_appeal(uuid,text,uuid)') is not null then
    execute 'revoke all on function public.resolve_appeal(uuid, text, uuid) from public, anon, authenticated';
    execute 'grant execute on function public.resolve_appeal(uuid, text, uuid) to service_role';
  end if;
end $$;

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

-- Appeal evidence is sensitive financial data. Store it in a private bucket
-- and scope object paths as <merchant_uuid>/<object_name>.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'appeal-screenshots',
  'appeal-screenshots',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
)
on conflict (id) do update set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "prod_appeal_screenshots_own" on storage.objects;
create policy "prod_appeal_screenshots_own"
on storage.objects for all to authenticated
using (
  bucket_id = 'appeal-screenshots'
  and (storage.foldername(name))[1] = public.current_merchant_id()::text
)
with check (
  bucket_id = 'appeal-screenshots'
  and (storage.foldername(name))[1] = public.current_merchant_id()::text
);
