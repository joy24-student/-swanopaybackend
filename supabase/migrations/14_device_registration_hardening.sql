-- ============================================================================
-- SwapnoPay Migration 14: Device Registration Hardening & RLS Alignment
-- Fixes SMS pipeline blockers by setting proper defaults and permissions
-- for the devices table and match_payment_atomic function.
-- ============================================================================

-- 1. Ensure user_id and merchant_id on devices default to the authenticated caller
alter table if exists public.devices 
  alter column user_id set default auth.uid();

alter table if exists public.devices 
  alter column merchant_id set default public.current_merchant_id();

-- 2. Ensure RLS policies on devices allow authenticated merchants to insert/upsert
drop policy if exists "Devices own" on public.devices;
drop policy if exists "Devices merchant read" on public.devices;
drop policy if exists "prod_devices_own" on public.devices;

create policy "prod_devices_own" on public.devices
  for all to authenticated
  using (user_id = auth.uid() or merchant_id = public.current_merchant_id())
  with check (user_id = auth.uid());

-- 3. Ensure sms_logs insert policy allows inserts from registered devices OR matching merchant_id
drop policy if exists "SMS logs insert by device" on public.sms_logs;
drop policy if exists "prod_sms_logs_insert" on public.sms_logs;

create policy "prod_sms_logs_insert" on public.sms_logs
  for insert to authenticated
  with check (
    merchant_id = public.current_merchant_id()
    or exists (
      select 1 from public.devices
      where id = device_id and user_id = auth.uid()
    )
  );

-- 4. Harden match_payment_atomic and match_payment_manual with SECURITY DEFINER
create or replace function match_payment_atomic(
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

create or replace function match_payment_manual(
  p_order_id uuid,
  p_payment_id uuid
) returns void language plpgsql security definer as $$
declare
  v_payment_amount numeric;
  v_sender_number text;
  v_trx_id text;
  v_sms_hash text;
  v_merchant_id uuid;
begin
  -- Retrieve payment log details
  select amount, sender_number, trx_id, sms_hash, merchant_id
  into v_payment_amount, v_sender_number, v_trx_id, v_sms_hash, v_merchant_id
  from public.payments
  where id = p_payment_id and status = 'UNMATCHED';

  if not found then
    raise exception 'Payment log not found or already matched.';
  end if;

  -- Update order
  update public.orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = v_sender_number,
    matched_trx_id = v_trx_id,
    manual_match = true
  where id = p_order_id and status = 'PENDING';

  -- Update payment log
  update public.payments set
    status = 'MATCHED',
    matched_order_id = p_order_id
  where id = p_payment_id;
end;
$$;

