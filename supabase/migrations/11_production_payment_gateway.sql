-- Production payment-gateway settings and a durable, merchant-database-owned
-- receipt outbox. Payment truth never comes from the Android client or the
-- provider mail service: only a committed orders.status -> PAID transition can
-- enqueue a receipt.

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
  constraint payment_gateway_amount_range check (
    min_amount > 0 and max_amount >= min_amount and daily_limit >= max_amount
  ),
  constraint payment_gateway_receipt_email check (
    merchant_receipt_email is null or merchant_receipt_email ~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'
  ),
  constraint payment_gateway_notification_phone check (
    notification_phone is null or notification_phone ~ '^\+?[0-9][0-9 -]{8,18}[0-9]$'
  ),
  constraint payment_gateway_success_url check (
    success_callback_url is null or success_callback_url ~ '^https://'
  ),
  constraint payment_gateway_failure_url check (
    failure_callback_url is null or failure_callback_url ~ '^https://'
  ),
  constraint payment_gateway_cancel_url check (
    cancel_callback_url is null or cancel_callback_url ~ '^https://'
  )
);

alter table public.payment_gateway_settings enable row level security;
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
drop policy if exists payment_gateway_settings_own on public.payment_gateway_settings;
create policy payment_gateway_settings_own on public.payment_gateway_settings
  for all to authenticated
  using (merchant_id = public.current_merchant_id())
  with check (merchant_id = public.current_merchant_id());

create or replace function public.touch_payment_gateway_settings()
returns trigger language plpgsql set search_path = public as $$
begin
  new.updated_at := now();
  return new;
end;
$$;
drop trigger if exists touch_payment_gateway_settings_trigger on public.payment_gateway_settings;
create trigger touch_payment_gateway_settings_trigger
before update on public.payment_gateway_settings for each row
execute function public.touch_payment_gateway_settings();

create table if not exists public.payment_receipt_outbox (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null references public.merchants(id) on delete cascade,
  order_id uuid not null references public.orders(id) on delete cascade,
  event_type text not null default 'PAYMENT_PAID' check (event_type = 'PAYMENT_PAID'),
  status text not null default 'PENDING'
    check (status in ('PENDING','PROCESSING','SENT','FAILED')),
  attempts integer not null default 0 check (attempts >= 0 and attempts <= 20),
  next_attempt_at timestamptz not null default now(),
  claimed_at timestamptz,
  delivered_at timestamptz,
  last_error text,
  provider_response jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (order_id, event_type)
);
create index if not exists payment_receipt_outbox_dispatch_idx
  on public.payment_receipt_outbox(status, next_attempt_at, created_at)
  where status in ('PENDING','PROCESSING','FAILED');
create index if not exists payment_receipt_outbox_merchant_created_idx
  on public.payment_receipt_outbox(merchant_id, created_at desc);

alter table public.payment_receipt_outbox enable row level security;
drop policy if exists payment_receipt_outbox_merchant_read on public.payment_receipt_outbox;
create policy payment_receipt_outbox_merchant_read on public.payment_receipt_outbox
  for select to authenticated
  using (merchant_id = public.current_merchant_id());

create or replace function public.enqueue_verified_payment_receipt()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'PAID' and old.status is distinct from 'PAID' then
    insert into public.payment_receipt_outbox(merchant_id, order_id)
    values (new.merchant_id, new.id)
    on conflict (order_id, event_type) do nothing;
  end if;
  return new;
end;
$$;
drop trigger if exists enqueue_verified_payment_receipt_trigger on public.orders;
create trigger enqueue_verified_payment_receipt_trigger
after update of status on public.orders for each row
execute function public.enqueue_verified_payment_receipt();

-- Accept equivalent +880/01 customer-number formatting, but never auto-match a
-- masked or last-four-only sender. Amount, full normalized phone, unprocessed
-- official SMS row, pending status, and expiry are checked under row locks.
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

create or replace function public.claim_payment_receipts(
  p_limit integer default 10,
  p_lease_seconds integer default 120
) returns setof public.payment_receipt_outbox
language plpgsql security definer set search_path = public as $$
begin
  if p_limit < 1 or p_limit > 50 or p_lease_seconds < 30 or p_lease_seconds > 900 then
    raise exception 'Invalid receipt claim parameters';
  end if;
  return query
  update public.payment_receipt_outbox outbox
  set status = 'PROCESSING', claimed_at = now(), attempts = attempts + 1,
      updated_at = now(), last_error = null
  where outbox.id in (
    select candidate.id
    from public.payment_receipt_outbox candidate
    left join public.payment_gateway_settings settings on settings.merchant_id = candidate.merchant_id
    where candidate.attempts < coalesce(settings.receipt_retry_limit, 5)
      and candidate.next_attempt_at <= now()
      and (
        candidate.status = 'PENDING' or
        (candidate.status = 'FAILED' and coalesce(settings.auto_receipt_retry, true)) or
        (candidate.status = 'PROCESSING' and candidate.claimed_at < now() - make_interval(secs => p_lease_seconds))
      )
    order by candidate.created_at
    for update of candidate skip locked
    limit p_limit
  )
  returning outbox.*;
end;
$$;

create or replace function public.complete_payment_receipt(
  p_event_id uuid,
  p_succeeded boolean,
  p_provider_response jsonb default '{}'::jsonb,
  p_error text default null
) returns void
language plpgsql security definer set search_path = public as $$
declare v_attempts integer;
begin
  select attempts into v_attempts from public.payment_receipt_outbox
  where id = p_event_id and status = 'PROCESSING' for update;
  if not found then raise exception 'Receipt event is not currently claimed'; end if;

  update public.payment_receipt_outbox
  set status = case when p_succeeded then 'SENT' else 'FAILED' end,
      delivered_at = case when p_succeeded then now() else delivered_at end,
      next_attempt_at = case when p_succeeded then next_attempt_at
        else now() + make_interval(secs => least(3600, (power(2, least(v_attempts, 10))::integer * 15))) end,
      provider_response = coalesce(p_provider_response, '{}'::jsonb),
      last_error = case when p_succeeded then null else left(coalesce(p_error, 'Receipt delivery failed'), 1000) end,
      updated_at = now()
  where id = p_event_id;
end;
$$;

-- Enforce configured transaction policy inside the merchant database for every
-- order creation path (REST API, hosted form, or future integrations).
create or replace function public.enforce_payment_gateway_order_rules()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_settings public.payment_gateway_settings%rowtype;
  v_paid_today numeric;
begin
  select * into v_settings from public.payment_gateway_settings
  where merchant_id = new.merchant_id;
  if not found then return new; end if;
  if not v_settings.gateway_enabled then raise exception 'payment gateway is disabled'; end if;
  if new.amount < v_settings.min_amount or new.amount > v_settings.max_amount then
    raise exception 'payment amount is outside the configured gateway range';
  end if;
  if new.payment_method is not null and not exists (
    select 1 from public.merchant_numbers mn
    where mn.merchant_id = new.merchant_id and mn.active = true
      and upper(mn.type) = upper(new.payment_method)
  ) then
    raise exception 'payment number is not configured';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(new.merchant_id::text || current_date::text, 0));
  select coalesce(sum(amount), 0) into v_paid_today from public.orders
  where merchant_id = new.merchant_id and (
    (status = 'PAID' and paid_at >= date_trunc('day', now())) or
    (status = 'PENDING' and expires_at >= now())
  );
  if v_paid_today + new.amount > v_settings.daily_limit then
    raise exception 'daily payment limit would be exceeded';
  end if;
  return new;
end;
$$;
drop trigger if exists enforce_payment_gateway_order_rules_trigger on public.orders;
create trigger enforce_payment_gateway_order_rules_trigger
before insert on public.orders for each row
execute function public.enforce_payment_gateway_order_rules();

revoke all on function public.touch_payment_gateway_settings() from public, anon, authenticated;
revoke all on function public.ensure_payment_gateway_settings() from public, anon, authenticated;
revoke all on function public.enqueue_verified_payment_receipt() from public, anon, authenticated;
revoke all on function public.enforce_payment_gateway_order_rules() from public, anon, authenticated;
revoke all on function public.claim_payment_receipts(integer, integer) from public, anon, authenticated;
grant execute on function public.claim_payment_receipts(integer, integer) to service_role;
revoke all on function public.complete_payment_receipt(uuid, boolean, jsonb, text) from public, anon, authenticated;
grant execute on function public.complete_payment_receipt(uuid, boolean, jsonb, text) to service_role;
revoke all on function public.match_payment_atomic(uuid,numeric,text,text,text,uuid) from public, anon, authenticated;
grant execute on function public.match_payment_atomic(uuid,numeric,text,text,text,uuid) to service_role;
