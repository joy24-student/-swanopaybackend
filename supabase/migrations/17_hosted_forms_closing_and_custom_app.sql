-- Migration 17: Enterprise Hosted Forms - Closing Timeline, CSV Backend, and Custom Web App Support

-- 1. Ensure indexes for payment_forms slug and lookup
create index if not exists payment_forms_merchant_status_idx on public.payment_forms(merchant_id, status);
create index if not exists payment_forms_slug_status_idx on public.payment_forms(slug, status);

-- 2. Enhanced create_hosted_form_submission with closing deadline enforcement
drop function if exists public.create_hosted_form_submission(uuid,uuid,text,text,text,numeric,text,boolean,jsonb,text);
create or replace function public.create_hosted_form_submission(
  p_form_id uuid,
  p_request_id uuid,
  p_customer_name text,
  p_customer_phone text,
  p_customer_email text,
  p_amount numeric,
  p_payment_method text,
  p_payment_required boolean,
  p_answers jsonb,
  p_client_hash text
) returns table(
  submission_id uuid,
  order_id uuid,
  transaction_id text,
  payment_status text,
  amount numeric,
  payment_number text,
  payment_method text,
  expires_at timestamptz,
  created boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_form public.payment_forms%rowtype;
  v_existing public.form_submissions%rowtype;
  v_order_id uuid;
  v_submission_id uuid;
  v_transaction_id text;
  v_number text;
  v_method text;
  v_expires_at timestamptz;
  v_deadline_epoch bigint;
begin
  if p_request_id is null then raise exception 'request_id is required'; end if;
  select * into v_form from public.payment_forms
    where id = p_form_id and status = 'PUBLISHED' for update;
  if not found then raise exception 'published form not found'; end if;

  -- Return existing record if idempotency key matches
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

  -- 1. Enforce Response Count Limit
  if coalesce((v_form.theme ->> 'close_after_limit')::boolean, false)
     and (select count(*) from public.form_submissions where form_id = p_form_id)
       >= greatest(coalesce((v_form.theme ->> 'max_responses')::integer, 1000), 1) then
    raise exception 'form response limit reached';
  end if;

  -- 2. Enforce Closing Timeline Deadline
  if (coalesce((v_form.theme ->> 'enable_closing_timeline')::boolean, false)
      or coalesce((v_form.theme ->> 'enableClosingTimeline')::boolean, false)) then
    v_deadline_epoch := nullif(coalesce(v_form.theme ->> 'closing_deadline_epoch', v_form.theme ->> 'closingDeadlineEpoch'), '')::bigint;
    if v_deadline_epoch is not null and v_deadline_epoch > 0
       and (extract(epoch from now()) * 1000)::bigint > v_deadline_epoch then
      raise exception 'form response limit reached: deadline expired';
    end if;
  end if;

  -- 3. Enforce One Response Per User (by client hash)
  if (coalesce((v_form.theme ->> 'one_response_per_user')::boolean, false)
      or coalesce((v_form.theme ->> 'oneResponsePerUser')::boolean, false))
     and nullif(p_client_hash, '') is not null
     and exists (select 1 from public.form_submissions where form_id = p_form_id and client_hash = p_client_hash) then
    raise exception 'response already submitted';
  end if;

  -- 4. Payment Creation (if required)
  if p_payment_required then
    if p_amount is null or p_amount <= 0 or p_amount > 10000000 then
      raise exception 'invalid payment amount';
    end if;
    if nullif(trim(p_customer_phone), '') is null then raise exception 'phone is required for payment'; end if;
    select mn.number, mn.type into v_number, v_method
      from public.merchant_numbers mn
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
      v_order_id, v_form.merchant_id, v_transaction_id, p_amount,
      trim(p_customer_phone), nullif(trim(p_customer_email), ''), nullif(trim(p_customer_name), ''),
      v_form.title, 'HOSTED_FORM', 'PENDING', v_method,
      case when coalesce((v_form.theme ->> 'payment_callback_enabled')::boolean, false)
        then nullif(v_form.theme ->> 'payment_callback_url', '') else null end,
      v_expires_at,
      jsonb_build_object('form_id', p_form_id, 'request_id', p_request_id)
    );
  else
    v_method := 'None';
  end if;

  -- 5. Record Form Submission
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

  update public.payment_forms set submissions_count = submissions_count + 1, updated_at = now()
    where id = p_form_id;

  return query select v_submission_id, v_order_id, v_transaction_id,
    case when p_payment_required then 'PENDING' else 'NOT_REQUIRED' end,
    coalesce(p_amount, 0), v_number, v_method, v_expires_at, true;
end;
$$;

revoke all on function public.create_hosted_form_submission(uuid,uuid,text,text,text,numeric,text,boolean,jsonb,text) from public, anon, authenticated;
grant execute on function public.create_hosted_form_submission(uuid,uuid,text,text,text,numeric,text,boolean,jsonb,text) to service_role;
