-- Production operations: atomic POS, inventory stock-in, DPS and EMI schedules.

alter table public.loans add column if not exists provider_name text not null default '';
alter table public.loans add column if not exists account_reference text not null default '';
alter table public.loans add column if not exists start_date timestamptz;
create unique index if not exists loans_merchant_account_reference_unique
  on public.loans(merchant_id, account_reference) where account_reference <> '';

create table if not exists public.dps_accounts (
  id uuid primary key,
  merchant_id uuid not null default public.current_merchant_id() references public.merchants on delete cascade,
  provider_name text not null check (length(btrim(provider_name)) between 1 and 160),
  account_reference text not null check (length(btrim(account_reference)) between 1 and 120),
  monthly_deposit numeric(14,2) not null check (monthly_deposit > 0),
  interest_rate numeric(7,4) not null default 0 check (interest_rate >= 0 and interest_rate <= 100),
  duration_months integer not null check (duration_months between 1 and 600),
  start_date timestamptz not null,
  maturity_date timestamptz not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'MATURED', 'CLOSED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (merchant_id, account_reference),
  check (maturity_date > start_date)
);

create table if not exists public.finance_installments (
  id uuid primary key,
  merchant_id uuid not null default public.current_merchant_id() references public.merchants on delete cascade,
  account_type text not null check (account_type in ('DPS', 'LOAN')),
  account_id uuid not null,
  installment_number integer not null check (installment_number > 0),
  due_date timestamptz not null,
  principal_amount numeric(14,2) not null check (principal_amount >= 0),
  interest_amount numeric(14,2) not null default 0 check (interest_amount >= 0),
  total_amount numeric(14,2) not null check (total_amount > 0),
  status text not null default 'PENDING' check (status in ('PENDING', 'PAID', 'OVERDUE', 'WAIVED')),
  paid_at timestamptz,
  payment_method text,
  payment_reference text,
  created_at timestamptz not null default now(),
  unique (merchant_id, account_type, account_id, installment_number),
  unique (merchant_id, payment_reference),
  check (
    (status = 'PAID' and paid_at is not null and payment_method is not null and payment_reference is not null)
    or status <> 'PAID'
  )
);

create index if not exists dps_accounts_merchant_idx on public.dps_accounts(merchant_id, created_at desc);
create index if not exists finance_installments_due_idx
  on public.finance_installments(merchant_id, status, due_date);

alter table public.dps_accounts enable row level security;
alter table public.finance_installments enable row level security;
drop policy if exists "prod_dps_accounts_own" on public.dps_accounts;
create policy "prod_dps_accounts_own" on public.dps_accounts for all to authenticated
  using (merchant_id = public.current_merchant_id())
  with check (merchant_id = public.current_merchant_id());
drop policy if exists "prod_finance_installments_own" on public.finance_installments;
create policy "prod_finance_installments_own" on public.finance_installments for all to authenticated
  using (merchant_id = public.current_merchant_id())
  with check (merchant_id = public.current_merchant_id());

-- Accept the operational payment labels used by POS while keeping a closed enum.
alter table public.pos_sales drop constraint if exists pos_sales_payment_method_check;
alter table public.pos_sales add constraint pos_sales_payment_method_check
  check (payment_method in ('Cash', 'bKash', 'Nagad', 'Rocket', 'Upay', 'MFS', 'Card', 'Bank', 'Due'));

create or replace function public.checkout_pos_atomic(p_sale jsonb, p_items jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_sale_id uuid := (p_sale ->> 'id')::uuid;
  v_customer_id uuid := nullif(p_sale ->> 'customer_id', '')::uuid;
  v_invoice text := btrim(p_sale ->> 'invoice_no');
  v_item jsonb;
  v_variant public.product_variants%rowtype;
  v_product public.products%rowtype;
  v_qty numeric;
  v_subtotal numeric := 0;
  v_discount numeric := coalesce((p_sale ->> 'discount')::numeric, 0);
  v_net numeric;
  v_cart jsonb := '[]'::jsonb;
  v_existing public.pos_sales%rowtype;
begin
  if v_merchant is null then raise exception 'Authenticated merchant not found'; end if;
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'Checkout requires at least one item';
  end if;
  if v_invoice is null or length(v_invoice) not between 4 and 80 then raise exception 'Invalid invoice number'; end if;
  if coalesce(p_sale ->> 'payment_method', '') not in ('Cash','bKash','Nagad','Rocket','Upay','MFS','Card','Bank','Due') then
    raise exception 'Unsupported payment method';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':pos:' || v_sale_id::text, 0));
  select * into v_existing from public.pos_sales where id = v_sale_id;
  if found then
    if v_existing.merchant_id <> v_merchant then raise exception 'Sale identifier belongs to another merchant'; end if;
    return jsonb_build_object('id', v_existing.id, 'invoice_no', v_existing.invoice_no, 'idempotent', true);
  end if;

  if v_customer_id is not null and not exists (
    select 1 from public.customers where id = v_customer_id and merchant_id = v_merchant
  ) then raise exception 'Customer does not belong to merchant'; end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_qty := (v_item ->> 'quantity')::numeric;
    if v_qty is null or v_qty <= 0 then raise exception 'Item quantity must be positive'; end if;

    select * into v_variant
    from public.product_variants
    where id = (v_item ->> 'variant_id')::uuid and merchant_id = v_merchant
    for update;
    if not found then raise exception 'Product variant not found'; end if;

    select * into v_product
    from public.products
    where id = v_variant.product_id and merchant_id = v_merchant
    for update;
    if not found then raise exception 'Product not found'; end if;
    if v_variant.stock_quantity < v_qty or v_product.stock_quantity < v_qty then
      raise exception 'Insufficient stock for %', v_product.name;
    end if;

    update public.product_variants set stock_quantity = stock_quantity - v_qty where id = v_variant.id;
    update public.products set stock_quantity = stock_quantity - v_qty where id = v_product.id;
    insert into public.stock_transactions(
      id, merchant_id, product_id, variant_id, type, quantity, price, customer_id, reference_note
    ) values (
      gen_random_uuid(), v_merchant, v_product.id, v_variant.id, 'out', v_qty,
      v_variant.sale_price, v_customer_id, 'POS ' || v_invoice
    );

    v_subtotal := v_subtotal + (v_variant.sale_price * v_qty);
    v_cart := v_cart || jsonb_build_array(jsonb_build_object(
      'product_id', v_product.id, 'variant_id', v_variant.id, 'qr_code', v_variant.qr_code,
      'name', v_product.name, 'variant', v_variant.variant_name, 'quantity', v_qty,
      'unit_price', v_variant.sale_price, 'line_total', v_variant.sale_price * v_qty
    ));
  end loop;

  if v_discount < 0 or v_discount > v_subtotal then raise exception 'Invalid discount'; end if;
  v_net := v_subtotal - v_discount;
  insert into public.pos_sales(
    id, merchant_id, invoice_no, customer_id, customer_name, customer_phone,
    subtotal, discount, net_total, cash_received, change_due, payment_method,
    payment_status, item_count, cart_items, timestamp
  ) values (
    v_sale_id, v_merchant, v_invoice, v_customer_id,
    coalesce(nullif(btrim(p_sale ->> 'customer_name'), ''), 'Walk-in Customer'),
    coalesce(p_sale ->> 'customer_phone', ''), v_subtotal, v_discount, v_net,
    case when p_sale ->> 'payment_method' = 'Due' then 0 else v_net end, 0,
    p_sale ->> 'payment_method',
    case when p_sale ->> 'payment_method' = 'Due' then 'DUE' else 'PAID' end,
    jsonb_array_length(v_cart), v_cart, now()
  );

  if p_sale ->> 'payment_method' = 'Due' then
    if v_customer_id is null then raise exception 'A due sale requires a customer'; end if;
    insert into public.ledger_transactions(
      id, merchant_id, customer_id, type, amount, date, note, product_details,
      payment_method, invoice_no
    ) values (
      gen_random_uuid(), v_merchant, v_customer_id, 'credit', v_net, now(),
      'POS credit sale ' || v_invoice, v_cart, 'Due', v_invoice
    );
    update public.customers set current_balance = current_balance + v_net
      where id = v_customer_id and merchant_id = v_merchant;
  end if;

  return jsonb_build_object('id', v_sale_id, 'invoice_no', v_invoice, 'subtotal', v_subtotal, 'net_total', v_net);
end;
$$;

create or replace function public.stock_in_product_atomic(p_product jsonb, p_variants jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_product_id uuid := (p_product ->> 'id')::uuid;
  v_variant jsonb;
  v_variant_id uuid;
  v_supplier uuid;
  v_qty numeric;
  v_total numeric := 0;
begin
  if v_merchant is null then raise exception 'Authenticated merchant not found'; end if;
  if length(btrim(p_product ->> 'name')) not between 1 and 200 then raise exception 'Invalid product name'; end if;
  if jsonb_typeof(p_variants) <> 'array' then raise exception 'Variants must be an array'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':stock-in:' || v_product_id::text, 0));
  if exists (select 1 from public.products where id = v_product_id and merchant_id = v_merchant) then
    return jsonb_build_object('id', v_product_id, 'idempotent', true);
  end if;

  insert into public.products(
    id, merchant_id, name, code, category, purchase_price, sale_price,
    stock_quantity, unit, cost_price, asking_price, qr_code
  ) values (
    v_product_id, v_merchant, btrim(p_product ->> 'name'), nullif(btrim(p_product ->> 'code'), ''),
    coalesce(nullif(btrim(p_product ->> 'category'), ''), 'General'),
    greatest(coalesce((p_product ->> 'purchase_price')::numeric, 0), 0),
    greatest(coalesce((p_product ->> 'sale_price')::numeric, 0), 0), 0,
    coalesce(nullif(btrim(p_product ->> 'unit'), ''), 'pcs'),
    greatest(coalesce((p_product ->> 'cost_price')::numeric, 0), 0),
    greatest(coalesce((p_product ->> 'asking_price')::numeric, 0), 0),
    nullif(btrim(p_product ->> 'qr_code'), '')
  );

  if jsonb_array_length(p_variants) = 0 then
    v_qty := coalesce((p_product ->> 'opening_quantity')::numeric, 0);
    if v_qty < 0 then raise exception 'Opening quantity cannot be negative'; end if;
    if v_qty > 0 then
      update public.products set stock_quantity = v_qty where id = v_product_id;
      insert into public.stock_transactions(
        id, merchant_id, product_id, type, quantity, price, reference_note
      ) values (
        gen_random_uuid(), v_merchant, v_product_id, 'in', v_qty,
        greatest(coalesce((p_product ->> 'purchase_price')::numeric, 0), 0), 'Opening stock'
      );
    end if;
    return jsonb_build_object('id', v_product_id, 'stock_quantity', v_qty);
  end if;

  for v_variant in select value from jsonb_array_elements(p_variants)
  loop
    v_variant_id := (v_variant ->> 'id')::uuid;
    v_qty := (v_variant ->> 'quantity')::numeric;
    v_supplier := nullif(v_variant ->> 'supplier_id', '')::uuid;
    if v_qty is null or v_qty <= 0 then raise exception 'Variant quantity must be positive'; end if;
    if v_supplier is not null and not exists (
      select 1 from public.suppliers where id = v_supplier and merchant_id = v_merchant
    ) then raise exception 'Supplier does not belong to merchant'; end if;
    insert into public.product_variants(
      id, merchant_id, product_id, variant_name, supplier_id, qr_code,
      cost_price, asking_price, sale_price, stock_quantity
    ) values (
      v_variant_id, v_merchant, v_product_id, btrim(v_variant ->> 'variant_name'), v_supplier,
      btrim(v_variant ->> 'qr_code'), greatest((v_variant ->> 'cost_price')::numeric, 0),
      greatest((v_variant ->> 'asking_price')::numeric, 0),
      greatest((v_variant ->> 'sale_price')::numeric, 0), v_qty
    );
    insert into public.stock_transactions(
      id, merchant_id, product_id, variant_id, type, quantity, price, supplier_id, reference_note
    ) values (
      gen_random_uuid(), v_merchant, v_product_id, v_variant_id, 'in', v_qty,
      greatest((v_variant ->> 'cost_price')::numeric, 0), v_supplier, 'Opening stock'
    );
    v_total := v_total + v_qty;
  end loop;
  update public.products set stock_quantity = v_total where id = v_product_id;
  return jsonb_build_object('id', v_product_id, 'stock_quantity', v_total);
end;
$$;

create or replace function public.create_finance_account_atomic(
  p_account_type text,
  p_account jsonb,
  p_installments jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_account_id uuid := (p_account ->> 'id')::uuid;
  v_duration integer := (p_account ->> 'duration_months')::integer;
  v_item jsonb;
begin
  if v_merchant is null then raise exception 'Authenticated merchant not found'; end if;
  if p_account_type not in ('DPS', 'LOAN') then raise exception 'Unsupported account type'; end if;
  if jsonb_typeof(p_installments) <> 'array' or jsonb_array_length(p_installments) <> v_duration then
    raise exception 'Installment schedule does not match tenure';
  end if;
  if v_duration not between 1 and 600 then raise exception 'Invalid tenure'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':finance:' || v_account_id::text, 0));

  if p_account_type = 'DPS' then
    if exists (select 1 from public.dps_accounts where id = v_account_id and merchant_id = v_merchant) then
      return jsonb_build_object('id', v_account_id, 'idempotent', true);
    end if;
    insert into public.dps_accounts(
      id, merchant_id, provider_name, account_reference, monthly_deposit,
      interest_rate, duration_months, start_date, maturity_date
    ) values (
      v_account_id, v_merchant, btrim(p_account ->> 'provider_name'), btrim(p_account ->> 'account_reference'),
      (p_account ->> 'monthly_deposit')::numeric, (p_account ->> 'interest_rate')::numeric,
      v_duration, (p_account ->> 'start_date')::timestamptz, (p_account ->> 'maturity_date')::timestamptz
    );
  else
    if exists (select 1 from public.loans where id = v_account_id and merchant_id = v_merchant) then
      return jsonb_build_object('id', v_account_id, 'idempotent', true);
    end if;
    insert into public.loans(
      id, merchant_id, provider_name, account_reference, principal_amount,
      interest_rate, interest_type, duration_months, monthly_installment,
      status, applied_at, disbursed_at, start_date
    ) values (
      v_account_id, v_merchant, btrim(p_account ->> 'provider_name'), btrim(p_account ->> 'account_reference'),
      (p_account ->> 'principal_amount')::numeric, (p_account ->> 'interest_rate')::numeric,
      p_account ->> 'interest_type', v_duration, (p_account ->> 'monthly_installment')::numeric,
      'disbursed', now(), (p_account ->> 'start_date')::timestamptz, (p_account ->> 'start_date')::timestamptz
    );
  end if;

  for v_item in select value from jsonb_array_elements(p_installments)
  loop
    insert into public.finance_installments(
      id, merchant_id, account_type, account_id, installment_number, due_date,
      principal_amount, interest_amount, total_amount
    ) values (
      (v_item ->> 'id')::uuid, v_merchant, p_account_type, v_account_id,
      (v_item ->> 'installment_number')::integer, (v_item ->> 'due_date')::timestamptz,
      (v_item ->> 'principal_amount')::numeric, (v_item ->> 'interest_amount')::numeric,
      (v_item ->> 'total_amount')::numeric
    );
  end loop;
  return jsonb_build_object('id', v_account_id, 'installments', v_duration);
end;
$$;

create or replace function public.pay_finance_installment_atomic(
  p_installment_id uuid,
  p_payment_method text,
  p_payment_reference text,
  p_paid_at timestamptz default now()
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_merchant uuid := public.current_merchant_id();
  v_row public.finance_installments%rowtype;
  v_reference text := btrim(p_payment_reference);
begin
  if v_merchant is null then raise exception 'Authenticated merchant not found'; end if;
  if length(btrim(p_payment_method)) not between 2 and 40 then raise exception 'Invalid payment method'; end if;
  if length(v_reference) not between 4 and 100 then raise exception 'Invalid payment reference'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_merchant::text || ':finance-payment:' || v_reference, 0));
  select * into v_row from public.finance_installments
    where id = p_installment_id and merchant_id = v_merchant for update;
  if not found then raise exception 'Installment not found'; end if;
  if v_row.status = 'PAID' and v_row.payment_reference = v_reference then
    return jsonb_build_object('id', v_row.id, 'idempotent', true);
  end if;
  if v_row.status not in ('PENDING', 'OVERDUE') then raise exception 'Installment cannot be paid from current status'; end if;
  update public.finance_installments
    set status = 'PAID', paid_at = p_paid_at, payment_method = btrim(p_payment_method),
        payment_reference = v_reference
    where id = p_installment_id and merchant_id = v_merchant;
  return jsonb_build_object('id', p_installment_id, 'status', 'PAID');
end;
$$;

revoke all on function public.checkout_pos_atomic(jsonb, jsonb) from public, anon;
revoke all on function public.stock_in_product_atomic(jsonb, jsonb) from public, anon;
revoke all on function public.create_finance_account_atomic(text, jsonb, jsonb) from public, anon;
revoke all on function public.pay_finance_installment_atomic(uuid, text, text, timestamptz) from public, anon;
grant execute on function public.checkout_pos_atomic(jsonb, jsonb) to authenticated;
grant execute on function public.stock_in_product_atomic(jsonb, jsonb) to authenticated;
grant execute on function public.create_finance_account_atomic(text, jsonb, jsonb) to authenticated;
grant execute on function public.pay_finance_installment_atomic(uuid, text, text, timestamptz) to authenticated;

do $$
begin
  begin alter publication supabase_realtime add table public.dps_accounts; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.finance_installments; exception when duplicate_object then null; end;
end $$;
