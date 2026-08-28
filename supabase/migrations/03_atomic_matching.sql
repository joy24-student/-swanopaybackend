-- Atomic Stored Procedure: match_payment_atomic
-- Performs standard database ledger updates within a safe PostgreSQL transaction block.
create or replace function match_payment_atomic(
  p_order_id uuid,
  p_payment_amount numeric,
  p_sender_number text,
  p_trx_id text,
  p_sms_hash text,
  p_sms_log_id uuid
) returns void language plpgsql as $$
begin
  -- 1. Update the order status to PAID
  update orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = p_sender_number,
    matched_trx_id = p_trx_id
  where id = p_order_id and status = 'PENDING';

  -- 2. Insert the payment matched record
  insert into payments (merchant_id, trx_id, amount, sender_number, sms_hash, status, matched_order_id)
  select merchant_id, p_trx_id, p_payment_amount, p_sender_number, p_sms_hash, 'MATCHED', p_order_id
  from orders where id = p_order_id;

  -- 3. Update the processed status on the incoming sms_log
  update sms_logs set processed = true, status = 'matched' where id = p_sms_log_id;
end;
$$;


-- Atomic Stored Procedure: match_payment_manual
-- Link an unmatched payment log directly to a pending order from the merchant dashboard.
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
  -- Retrieve payment log details
  select amount, sender_number, trx_id, sms_hash, merchant_id
  into v_payment_amount, v_sender_number, v_trx_id, v_sms_hash, v_merchant_id
  from payments
  where id = p_payment_id and status = 'UNMATCHED';

  if not found then
    raise exception 'Payment log not found or already matched.';
  end if;

  -- Update order
  update orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = v_sender_number,
    matched_trx_id = v_trx_id,
    manual_match = true
  where id = p_order_id and status = 'PENDING';

  -- Update payment record to MATCHED
  update payments set
    status = 'MATCHED',
    matched_order_id = p_order_id
  where id = p_payment_id;
end;
$$;
