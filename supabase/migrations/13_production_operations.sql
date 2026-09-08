-- Production operations: atomic POS/inventory, DPS/EMI schedules, durable notifications.

alter table public.loans add column if not exists provider_name text not null default '';
alter table public.loans add column if not exists account_reference text not null default '';
alter table public.loans add column if not exists start_date timestamptz;

create unique index if not exists loans_merchant_reference_unique
  on public.loans(merchant_id, lower(account_reference)) where account_reference <> '';
create unique index if not exists products_merchant_code_unique
  on public.products(merchant_id, lower(code)) where code is not null and code <> '';
create unique index if not exists products_merchant_qr_unique
  on public.products(merchant_id, lower(qr_code)) where qr_code is not null and qr_code <> '';

create table if not exists public.dps_accounts (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null default public.current_merchant_id() references public.merchants(id) on delete cascade,
  provider_name text not null check (length(trim(provider_name)) between 1 and 160),
  account_reference text not null check (length(trim(account_reference)) between 1 and 160),
  monthly_deposit numeric(14,2) not null check (monthly_deposit > 0),
  interest_rate numeric(7,4) not null default 0 check (interest_rate >= 0 and interest_rate <= 100),
  duration_months integer not null check (duration_months between 1 and 600),
  start_date timestamptz not null,
  maturity_date timestamptz not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE','MATURED','CLOSED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (merchant_id, account_reference)
);

create table if not exists public.finance_installments (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null default public.current_merchant_id() references public.merchants(id) on delete cascade,
  account_type text not null check (account_type in ('DPS','LOAN')),
  account_id uuid not null,
  installment_number integer not null check (installment_number > 0),
  due_date timestamptz not null,
  principal_amount numeric(14,2) not null check (principal_amount >= 0),
  interest_amount numeric(14,2) not null default 0 check (interest_amount >= 0),
  total_amount numeric(14,2) not null check (total_amount > 0),
  status text not null default 'PENDING' check (status in ('PENDING','PAID','OVERDUE','WAIVED')),
  paid_at timestamptz,
  payment_method text,
  payment_reference text,
  created_at timestamptz not null default now(),
  unique (merchant_id, account_type, account_id, installment_number)
);

create unique index if not exists finance_installment_payment_reference_unique
  on public.finance_installments(merchant_id, payment_reference)
  where payment_reference is not null and payment_reference <> '';
create index if not exists finance_installments_due_idx
  on public.finance_installments(merchant_id, status, due_date);

create table if not exists public.merchant_notifications (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null default public.current_merchant_id() references public.merchants(id) on delete cascade,
  type text not null,
  title text not null,
  message text not null,
  severity text not null default 'INFO' check (severity in ('INFO','SUCCESS','WARNING','ERROR')),
  entity_type text,
  entity_id uuid,
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index if not exists merchant_notifications_feed_idx
  on public.merchant_notifications(merchant_id, created_at desc);

alter table public.dps_accounts enable row level security;
alter table public.finance_installments enable row level security;
alter table public.merchant_notifications enable row level security;

-- Migration 12 allowed direct finance writes. Account creation and payment
-- state transitions now go exclusively through authenticated RPCs below.
drop policy if exists "prod_dps_accounts_own" on public.dps_accounts;
drop policy if exists "prod_finance_installments_own" on public.finance_installments;
drop policy if exists dps_accounts_tenant on public.dps_accounts;
create policy dps_accounts_tenant on public.dps_accounts for select to authenticated
  using (merchant_id = public.current_merchant_id());

drop policy if exists finance_installments_tenant on public.finance_installments;
create policy finance_installments_tenant on public.finance_installments for select to authenticated
  using (merchant_id = public.current_merchant_id());

drop policy if exists merchant_notifications_select on public.merchant_notifications;
create policy merchant_notifications_select on public.merchant_notifications for select to authenticated
  using (merchant_id = public.current_merchant_id());
drop policy if exists merchant_notifications_update on public.merchant_notifications;
create policy merchant_notifications_update on public.merchant_notifications for update to authenticated
  using (merchant_id = public.current_merchant_id())
  with check (merchant_id = public.current_merchant_id());

-- Older installs allowed fewer POS labels than the current client.
alter table public.pos_sales drop constraint if exists pos_sales_payment_method_check;
alter table public.pos_sales add constraint pos_sales_payment_method_check
  check (payment_method in ('Cash','MFS','Card','Due','bKash','Nagad','Rocket','Upay','Bank'));

create or replace function public.create_finance_account_atomic(
  p_account_type text,
  p_account jsonb,
  p_installments jsonb
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_id uuid := (p_account->>'id')::uuid;
  v_row jsonb;
  v_months integer := (p_account->>'duration_months')::integer;
begin
  if v_merchant is null then raise exception 'Authenticated merchant required'; end if;
  if p_account_type not in ('DPS','LOAN') then raise exception 'Unsupported account type'; end if;
  if v_id is null or jsonb_typeof(p_installments) <> 'array' or jsonb_array_length(p_installments) <> v_months then
    raise exception 'Invalid finance account or schedule';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':finance:' || v_id::text, 0));
  if exists(select 1 from public.finance_installments where merchant_id=v_merchant and account_type=p_account_type and account_id=v_id) then
    return jsonb_build_object('id', v_id, 'idempotent', true);
  end if;

  if p_account_type = 'DPS' then
    insert into public.dps_accounts(id, merchant_id, provider_name, account_reference, monthly_deposit,
      interest_rate, duration_months, start_date, maturity_date)
    values(v_id, v_merchant, trim(p_account->>'provider_name'), trim(p_account->>'account_reference'),
      (p_account->>'monthly_deposit')::numeric, (p_account->>'interest_rate')::numeric, v_months,
      (p_account->>'start_date')::timestamptz, (p_account->>'maturity_date')::timestamptz);
  else
    insert into public.loans(id, merchant_id, provider_name, account_reference, principal_amount,
      interest_rate, interest_type, duration_months, monthly_installment, status, start_date, disbursed_at)
    values(v_id, v_merchant, trim(p_account->>'provider_name'), trim(p_account->>'account_reference'),
      (p_account->>'principal_amount')::numeric, (p_account->>'interest_rate')::numeric,
      p_account->>'interest_type', v_months, (p_account->>'monthly_installment')::numeric,
      'disbursed', (p_account->>'start_date')::timestamptz, (p_account->>'start_date')::timestamptz);
  end if;

  for v_row in select value from jsonb_array_elements(p_installments) loop
    insert into public.finance_installments(id, merchant_id, account_type, account_id, installment_number,
      due_date, principal_amount, interest_amount, total_amount)
    values((v_row->>'id')::uuid, v_merchant, p_account_type, v_id,
      (v_row->>'installment_number')::integer, (v_row->>'due_date')::timestamptz,
      (v_row->>'principal_amount')::numeric, coalesce((v_row->>'interest_amount')::numeric,0),
      (v_row->>'total_amount')::numeric);
  end loop;
  return jsonb_build_object('id', v_id, 'account_type', p_account_type, 'installments', v_months);
end;
$$;

create or replace function public.pay_finance_installment_atomic(
  p_installment_id uuid,
  p_payment_method text,
  p_payment_reference text,
  p_paid_at timestamptz default now()
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare v_row public.finance_installments;
begin
  if length(trim(p_payment_method)) not between 1 and 60 or length(trim(p_payment_reference)) not between 4 and 100 then
    raise exception 'Payment method and reference are required';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(public.current_merchant_id()::text || ':installment:' || p_installment_id::text,0));
  update public.finance_installments set status='PAID', paid_at=coalesce(p_paid_at,now()),
    payment_method=trim(p_payment_method), payment_reference=trim(p_payment_reference)
  where id=p_installment_id and merchant_id=public.current_merchant_id() and status in ('PENDING','OVERDUE')
  returning * into v_row;
  if v_row.id is null then
    select * into v_row from public.finance_installments
      where id=p_installment_id and merchant_id=public.current_merchant_id()
        and status='PAID' and payment_reference=trim(p_payment_reference);
  end if;
  if v_row.id is null then raise exception 'Installment missing, already paid, or reference conflict'; end if;
  return jsonb_build_object('id', v_row.id, 'status', v_row.status, 'idempotent', v_row.paid_at is not null);
end;
$$;

create or replace function public.mark_merchant_notification_read(
  p_notification_id uuid,
  p_read_at timestamptz default now()
) returns integer language plpgsql security definer set search_path=public as $$
declare v_count integer;
begin
  update public.merchant_notifications set read_at=coalesce(p_read_at,now())
    where id=p_notification_id and merchant_id=public.current_merchant_id();
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

create or replace function public.mark_all_merchant_notifications_read(p_read_at timestamptz default now())
returns integer language plpgsql security definer set search_path=public as $$
declare v_count integer;
begin
  update public.merchant_notifications set read_at=coalesce(p_read_at,now())
    where merchant_id=public.current_merchant_id() and read_at is null;
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

create or replace function public.record_ledger_transaction_atomic(p_transaction jsonb)
returns uuid language plpgsql security definer set search_path=public as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_id uuid := (p_transaction->>'id')::uuid;
  v_customer uuid := nullif(p_transaction->>'customer_id','')::uuid;
  v_supplier uuid := nullif(p_transaction->>'supplier_id','')::uuid;
  v_type text := lower(p_transaction->>'type');
  v_amount numeric := (p_transaction->>'amount')::numeric;
  v_delta numeric;
begin
  if v_merchant is null or v_id is null then raise exception 'Authenticated merchant and transaction ID required'; end if;
  if (v_customer is null) = (v_supplier is null) then raise exception 'Choose exactly one ledger party'; end if;
  if v_type not in ('credit','payment') or v_amount <= 0 then raise exception 'Invalid ledger type or amount'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text||':ledger:'||v_id::text,0));
  if exists(select 1 from public.ledger_transactions where id=v_id and merchant_id=v_merchant) then return v_id; end if;
  insert into public.ledger_transactions(id,merchant_id,customer_id,supplier_id,type,amount,date,note,
    product_details,is_voice_entry,payment_method,invoice_no)
  values(v_id,v_merchant,v_customer,v_supplier,v_type,v_amount,
    coalesce((p_transaction->>'date')::timestamptz,now()),nullif(p_transaction->>'note',''),
    coalesce(p_transaction->'product_details','[]'::jsonb),coalesce((p_transaction->>'is_voice_entry')::boolean,false),
    coalesce(nullif(p_transaction->>'payment_method',''),'Cash'),nullif(p_transaction->>'invoice_no',''));
  if v_customer is not null then
    v_delta := case when v_type='credit' then v_amount else -v_amount end;
    update public.customers set current_balance=current_balance+v_delta where id=v_customer and merchant_id=v_merchant;
  else
    v_delta := case when v_type='credit' then -v_amount else v_amount end;
    update public.suppliers set current_balance=current_balance+v_delta where id=v_supplier and merchant_id=v_merchant;
  end if;
  if not found then raise exception 'Ledger party does not belong to merchant'; end if;
  return v_id;
end;
$$;

create or replace function public.stock_in_product_atomic(p_product jsonb, p_variants jsonb default '[]'::jsonb)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_product uuid := (p_product->>'id')::uuid;
  v_variant jsonb;
  v_variant_id uuid;
  v_qty numeric;
  v_opening numeric := coalesce((p_product->>'opening_quantity')::numeric,0);
begin
  if v_merchant is null or v_product is null then raise exception 'Authenticated merchant and product required'; end if;
  if jsonb_typeof(p_variants) <> 'array' then raise exception 'Variants must be an array'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':product:' || v_product::text,0));
  if exists(select 1 from public.products where id=v_product and merchant_id=v_merchant) then
    return jsonb_build_object('id', v_product, 'idempotent', true);
  end if;
  if coalesce((p_product->>'purchase_price')::numeric,0) < 0 or coalesce((p_product->>'sale_price')::numeric,0) < 0 or v_opening < 0 then
    raise exception 'Prices and quantity cannot be negative';
  end if;
  insert into public.products(id,merchant_id,name,code,category,purchase_price,sale_price,stock_quantity,
    unit,qr_code,cost_price,asking_price)
  values(v_product,v_merchant,trim(p_product->>'name'),nullif(trim(p_product->>'code'),''),
    coalesce(nullif(trim(p_product->>'category'),''),'General'),coalesce((p_product->>'purchase_price')::numeric,0),
    coalesce((p_product->>'sale_price')::numeric,0),0,coalesce(nullif(trim(p_product->>'unit'),''),'pcs'),
    nullif(trim(p_product->>'qr_code'),''),coalesce((p_product->>'cost_price')::numeric,0),
    coalesce((p_product->>'asking_price')::numeric,0));

  if jsonb_array_length(p_variants)=0 and v_opening > 0 then
    update public.products set stock_quantity=v_opening where id=v_product and merchant_id=v_merchant;
    insert into public.stock_transactions(merchant_id,product_id,type,quantity,price,reference_note)
      values(v_merchant,v_product,'in',v_opening,coalesce((p_product->>'purchase_price')::numeric,0),'Opening stock');
  end if;
  for v_variant in select value from jsonb_array_elements(p_variants) loop
    v_variant_id := (v_variant->>'id')::uuid;
    v_qty := (v_variant->>'quantity')::numeric;
    if v_qty <= 0 or coalesce((v_variant->>'cost_price')::numeric,0)<0 or coalesce((v_variant->>'sale_price')::numeric,0)<0 then
      raise exception 'Invalid variant quantity or price';
    end if;
    insert into public.product_variants(id,merchant_id,product_id,variant_name,supplier_id,qr_code,
      cost_price,asking_price,sale_price,stock_quantity)
    values(v_variant_id,v_merchant,v_product,trim(v_variant->>'variant_name'),nullif(v_variant->>'supplier_id','')::uuid,
      trim(v_variant->>'qr_code'),coalesce((v_variant->>'cost_price')::numeric,0),
      coalesce((v_variant->>'asking_price')::numeric,0),coalesce((v_variant->>'sale_price')::numeric,0),v_qty);
    update public.products set stock_quantity=stock_quantity+v_qty where id=v_product and merchant_id=v_merchant;
    insert into public.stock_transactions(merchant_id,product_id,variant_id,type,quantity,price,supplier_id,reference_note)
      values(v_merchant,v_product,v_variant_id,'in',v_qty,coalesce((v_variant->>'cost_price')::numeric,0),
        nullif(v_variant->>'supplier_id','')::uuid,'Opening stock');
  end loop;
  return jsonb_build_object('id', v_product);
end;
$$;

create or replace function public.update_product_atomic(p_product jsonb, p_adjustment jsonb default null)
returns uuid language plpgsql security definer set search_path=public as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_product uuid := (p_product->>'id')::uuid;
  v_current numeric;
  v_expected numeric := (p_product->>'expected_stock')::numeric;
  v_target numeric := (p_product->>'target_stock')::numeric;
  v_adjustment_id uuid;
  v_adjustment_type text;
  v_adjustment_quantity numeric;
begin
  if v_merchant is null or v_product is null then raise exception 'Authenticated merchant and product required'; end if;
  if nullif(trim(coalesce(p_product->>'name','')),'') is null or nullif(trim(coalesce(p_product->>'unit','')),'') is null then
    raise exception 'Product name and unit are required';
  end if;
  if coalesce((p_product->>'purchase_price')::numeric,-1) < 0 or
     coalesce((p_product->>'sale_price')::numeric,-1) < 0 or v_expected < 0 or v_target < 0 then
    raise exception 'Prices and stock cannot be negative';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':product:' || v_product::text,0));
  select stock_quantity into v_current from public.products
    where id=v_product and merchant_id=v_merchant for update;
  if not found then raise exception 'Product does not belong to merchant'; end if;

  update public.products set
    name=trim(p_product->>'name'),
    code=nullif(trim(p_product->>'code'),''),
    qr_code=nullif(trim(p_product->>'qr_code'),''),
    category=coalesce(nullif(trim(p_product->>'category'),''),'General'),
    purchase_price=(p_product->>'purchase_price')::numeric,
    sale_price=(p_product->>'sale_price')::numeric,
    cost_price=coalesce((p_product->>'cost_price')::numeric,(p_product->>'purchase_price')::numeric),
    asking_price=coalesce((p_product->>'asking_price')::numeric,(p_product->>'sale_price')::numeric),
    unit=trim(p_product->>'unit')
    where id=v_product and merchant_id=v_merchant;

  if p_adjustment is null or jsonb_typeof(p_adjustment)='null' then
    if v_target <> v_current then raise exception 'A stock adjustment is required when quantity changes'; end if;
    return v_product;
  end if;
  if exists(select 1 from public.product_variants where product_id=v_product and merchant_id=v_merchant) then
    raise exception 'Variant product stock must be adjusted through variants';
  end if;
  v_adjustment_id := (p_adjustment->>'id')::uuid;
  if exists(select 1 from public.stock_transactions where id=v_adjustment_id and merchant_id=v_merchant) then
    if v_current <> v_target then raise exception 'Stock adjustment was already applied but target stock differs'; end if;
    return v_product;
  end if;
  if v_current <> v_expected then raise exception 'Stock changed on another device; refresh and retry'; end if;
  v_adjustment_type := p_adjustment->>'type';
  v_adjustment_quantity := (p_adjustment->>'quantity')::numeric;
  if v_adjustment_quantity <= 0 or
     (v_adjustment_type='in' and v_target-v_current <> v_adjustment_quantity) or
     (v_adjustment_type='out' and v_current-v_target <> v_adjustment_quantity) or
     v_adjustment_type not in ('in','out') then
    raise exception 'Invalid stock adjustment';
  end if;
  update public.products set stock_quantity=v_target where id=v_product and merchant_id=v_merchant;
  insert into public.stock_transactions(id,merchant_id,product_id,type,quantity,price,reference_note)
    values(v_adjustment_id,v_merchant,v_product,v_adjustment_type,v_adjustment_quantity,
      coalesce((p_adjustment->>'price')::numeric,0),coalesce(nullif(p_adjustment->>'reference_note',''),'Manual stock adjustment'));
  return v_product;
end;
$$;

create or replace function public.adjust_inventory_atomic(p_movement jsonb)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_id uuid := (p_movement->>'id')::uuid;
  v_product uuid := (p_movement->>'product_id')::uuid;
  v_variant uuid := nullif(p_movement->>'variant_id','')::uuid;
  v_type text := lower(p_movement->>'type');
  v_quantity numeric := (p_movement->>'quantity')::numeric;
  v_delta numeric;
begin
  if v_merchant is null or v_id is null or v_product is null then
    raise exception 'Authenticated merchant, movement and product are required';
  end if;
  if v_type not in ('in','out') or v_quantity is null or v_quantity <= 0 then
    raise exception 'Inventory movement type and positive quantity are required';
  end if;
  if coalesce((p_movement->>'price')::numeric,0) < 0 then raise exception 'Inventory price cannot be negative'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':stock:' || v_id::text,0));
  if exists(select 1 from public.stock_transactions where id=v_id and merchant_id=v_merchant) then
    return jsonb_build_object('id', v_id, 'idempotent', true);
  end if;
  v_delta := case when v_type='in' then v_quantity else -v_quantity end;
  if v_variant is not null then
    update public.product_variants set stock_quantity=stock_quantity+v_delta
      where id=v_variant and product_id=v_product and merchant_id=v_merchant and stock_quantity+v_delta>=0;
    if not found then raise exception 'Variant is missing or has insufficient stock'; end if;
  end if;
  update public.products set stock_quantity=stock_quantity+v_delta
    where id=v_product and merchant_id=v_merchant and stock_quantity+v_delta>=0;
  if not found then raise exception 'Product is missing or has insufficient stock'; end if;
  insert into public.stock_transactions(id,merchant_id,product_id,variant_id,type,quantity,price,customer_id,supplier_id,reference_note)
    values(v_id,v_merchant,v_product,v_variant,v_type,v_quantity,coalesce((p_movement->>'price')::numeric,0),
      nullif(p_movement->>'customer_id','')::uuid,nullif(p_movement->>'supplier_id','')::uuid,
      nullif(p_movement->>'reference_note',''));
  return jsonb_build_object('id', v_id, 'stock_delta', v_delta);
end;
$$;

create or replace function public.checkout_pos_atomic(p_sale jsonb, p_items jsonb)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_sale uuid := (p_sale->>'id')::uuid;
  v_item jsonb; v_product uuid; v_variant uuid; v_qty numeric; v_price numeric;
  v_customer uuid := nullif(p_sale->>'customer_id','')::uuid;
begin
  if v_merchant is null or v_sale is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)=0 then
    raise exception 'Authenticated merchant, sale and items required';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':pos:' || v_sale::text,0));
  if exists(select 1 from public.pos_sales where id=v_sale and merchant_id=v_merchant) then
    return jsonb_build_object('id', v_sale, 'idempotent', true);
  end if;
  for v_item in select value from jsonb_array_elements(p_items) loop
    v_product := (v_item->>'product_id')::uuid; v_variant := nullif(v_item->>'variant_id','')::uuid;
    v_qty := (v_item->>'quantity')::numeric; v_price := (v_item->>'unit_price')::numeric;
    if v_qty<=0 or v_price<0 then raise exception 'Invalid cart quantity or price'; end if;
    if v_variant is not null then
      update public.product_variants set stock_quantity=stock_quantity-v_qty
        where id=v_variant and product_id=v_product and merchant_id=v_merchant and stock_quantity>=v_qty;
      if not found then raise exception 'Insufficient variant stock'; end if;
    end if;
    update public.products set stock_quantity=stock_quantity-v_qty
      where id=v_product and merchant_id=v_merchant and stock_quantity>=v_qty;
    if not found then raise exception 'Insufficient product stock'; end if;
    insert into public.stock_transactions(merchant_id,product_id,variant_id,type,quantity,price,customer_id,reference_note)
      values(v_merchant,v_product,v_variant,'out',v_qty,v_price,v_customer,'POS '||(p_sale->>'invoice_no'));
  end loop;
  insert into public.pos_sales(id,merchant_id,invoice_no,customer_id,customer_name,customer_phone,subtotal,
    discount,net_total,cash_received,change_due,payment_method,payment_status,item_count,cart_items,"timestamp")
  values(v_sale,v_merchant,p_sale->>'invoice_no',v_customer,coalesce(p_sale->>'customer_name','Walk-in Customer'),
    coalesce(p_sale->>'customer_phone',''),(p_sale->>'subtotal')::numeric,(p_sale->>'discount')::numeric,
    (p_sale->>'net_total')::numeric,(p_sale->>'cash_received')::numeric,(p_sale->>'change_due')::numeric,
    p_sale->>'payment_method',p_sale->>'payment_status',(p_sale->>'item_count')::integer,
    coalesce(p_sale->'cart_items',p_items),now());
  if p_sale->>'payment_status'='DUE' then
    if v_customer is null then raise exception 'A due sale requires a customer'; end if;
    insert into public.ledger_transactions(merchant_id,customer_id,type,amount,note,product_details,payment_method,invoice_no)
      values(v_merchant,v_customer,'credit',(p_sale->>'net_total')::numeric,'POS credit sale '||(p_sale->>'invoice_no'),
        coalesce(p_sale->'cart_items',p_items),'Due',p_sale->>'invoice_no');
    update public.customers set current_balance=current_balance+(p_sale->>'net_total')::numeric
      where id=v_customer and merchant_id=v_merchant;
    if not found then raise exception 'Customer does not belong to merchant'; end if;
  end if;
  return jsonb_build_object('id', v_sale, 'invoice_no', p_sale->>'invoice_no');
end;
$$;

create or replace function public.enqueue_merchant_notification()
returns trigger language plpgsql security definer set search_path=public as $$
declare v_merchant uuid; v_type text; v_title text; v_message text; v_severity text := 'INFO'; v_entity uuid;
begin
  if tg_table_name='payments' then
    v_merchant:=new.merchant_id; v_type:='PAYMENT'; v_title:='Payment received';
    v_message:='Transaction '||coalesce(new.trx_id,'(provider reference pending)')||' for BDT '||new.amount||' is '||lower(new.status); v_severity:='SUCCESS'; v_entity:=new.id;
  elsif tg_table_name='appeals' then
    select merchant_id into v_merchant from public.orders where id=new.order_id;
    v_type:='APPEAL'; v_title:='Payment appeal received'; v_message:='A customer submitted an appeal for order '||new.order_id; v_severity:='WARNING'; v_entity:=new.id;
  elsif tg_table_name='form_submissions' then
    select merchant_id into v_merchant from public.payment_forms where id=new.form_id;
    v_type:='FORM_RESPONSE'; v_title:='New form response'; v_message:='A published form received a new response'; v_severity:='INFO'; v_entity:=new.id;
  else return new;
  end if;
  if v_merchant is not null and not exists(
    select 1 from public.merchant_notifications where merchant_id=v_merchant and type=v_type and entity_id=v_entity
  ) then
    insert into public.merchant_notifications(merchant_id,type,title,message,severity,entity_type,entity_id)
      values(v_merchant,v_type,v_title,v_message,v_severity,tg_table_name,v_entity);
  end if;
  return new;
end;
$$;

drop trigger if exists payments_merchant_notification on public.payments;
create trigger payments_merchant_notification after insert on public.payments
  for each row execute function public.enqueue_merchant_notification();
drop trigger if exists appeals_merchant_notification on public.appeals;
create trigger appeals_merchant_notification after insert on public.appeals
  for each row execute function public.enqueue_merchant_notification();
drop trigger if exists submissions_merchant_notification on public.form_submissions;
create trigger submissions_merchant_notification after insert on public.form_submissions
  for each row execute function public.enqueue_merchant_notification();

revoke all on function public.create_finance_account_atomic(text,jsonb,jsonb) from public, anon;
revoke all on function public.pay_finance_installment_atomic(uuid,text,text,timestamptz) from public, anon;
revoke all on function public.stock_in_product_atomic(jsonb,jsonb) from public, anon;
revoke all on function public.update_product_atomic(jsonb,jsonb) from public, anon;
revoke all on function public.adjust_inventory_atomic(jsonb) from public, anon;
revoke all on function public.checkout_pos_atomic(jsonb,jsonb) from public, anon;
revoke all on function public.mark_merchant_notification_read(uuid,timestamptz) from public, anon;
revoke all on function public.mark_all_merchant_notifications_read(timestamptz) from public, anon;
revoke all on function public.record_ledger_transaction_atomic(jsonb) from public, anon;
grant execute on function public.create_finance_account_atomic(text,jsonb,jsonb) to authenticated;
grant execute on function public.pay_finance_installment_atomic(uuid,text,text,timestamptz) to authenticated;
grant execute on function public.stock_in_product_atomic(jsonb,jsonb) to authenticated;
grant execute on function public.update_product_atomic(jsonb,jsonb) to authenticated;
grant execute on function public.adjust_inventory_atomic(jsonb) to authenticated;
grant execute on function public.checkout_pos_atomic(jsonb,jsonb) to authenticated;
grant execute on function public.mark_merchant_notification_read(uuid,timestamptz) to authenticated;
grant execute on function public.mark_all_merchant_notifications_read(timestamptz) to authenticated;
grant execute on function public.record_ledger_transaction_atomic(jsonb) to authenticated;
revoke all on function public.enqueue_merchant_notification() from public, anon, authenticated;
revoke insert, update, delete on public.dps_accounts, public.finance_installments from authenticated;
revoke insert, update, delete on public.merchant_notifications from authenticated;
grant select on public.dps_accounts to authenticated;
grant select on public.merchant_notifications to authenticated;
grant select on public.finance_installments to authenticated;
