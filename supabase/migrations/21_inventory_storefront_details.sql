-- Apply after 13_production_operations.sql. Adds product metadata without changing stock semantics.
begin;
alter table public.products add column if not exists image_url text;
alter table public.products add column if not exists storefront_details jsonb not null default '{}'::jsonb;
alter table public.products add constraint products_storefront_details_object check (jsonb_typeof(storefront_details)='object' and octet_length(storefront_details::text)<=250000);
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
    unit,qr_code,cost_price,asking_price,image_url,storefront_details)
  values(v_product,v_merchant,trim(p_product->>'name'),nullif(trim(p_product->>'code'),''),
    coalesce(nullif(trim(p_product->>'category'),''),'General'),coalesce((p_product->>'purchase_price')::numeric,0),
    coalesce((p_product->>'sale_price')::numeric,0),0,coalesce(nullif(trim(p_product->>'unit'),''),'pcs'),
    nullif(trim(p_product->>'qr_code'),''),coalesce((p_product->>'cost_price')::numeric,0),
    coalesce((p_product->>'asking_price')::numeric,0),nullif(p_product->>'image_url',''),coalesce(p_product->'storefront_details','{}'::jsonb));

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
    unit=trim(p_product->>'unit'),
    image_url=case when p_product ? 'image_url' then nullif(p_product->>'image_url','') else image_url end,
    storefront_details=coalesce(p_product->'storefront_details',storefront_details)
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


create or replace function public.update_product_storefront(p_id uuid,p_details jsonb,p_image_url text default null)
returns void language plpgsql security definer set search_path=public as $$
begin
  if public.current_merchant_id() is null then raise exception 'Authenticated merchant required'; end if;
  update public.products set storefront_details=p_details,image_url=p_image_url
    where id=p_id and merchant_id=public.current_merchant_id();
  if not found then raise exception 'Product not found for this merchant'; end if;
end;
$$;
revoke all on function public.stock_in_product_atomic(jsonb,jsonb) from public,anon;
revoke all on function public.update_product_atomic(jsonb,jsonb) from public,anon;
revoke all on function public.update_product_storefront(uuid,jsonb,text) from public,anon;
grant execute on function public.stock_in_product_atomic(jsonb,jsonb) to authenticated;
grant execute on function public.update_product_atomic(jsonb,jsonb) to authenticated;
grant execute on function public.update_product_storefront(uuid,jsonb,text) to authenticated;
commit;
