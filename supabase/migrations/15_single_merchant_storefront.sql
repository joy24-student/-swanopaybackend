-- ============================================================================
-- SwapnoPay Migration 15: Single-Merchant Storefront & Realtime Integration
-- Extends core Supabase schema with dedicated single-merchant eCommerce
-- storefront tables, automated stock decrements, and real-time mobile alerts.
-- ============================================================================

-- 1. Storefront Settings (Replaces MySQL tbl_settings)
create table if not exists public.store_settings (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants(id) on delete cascade not null,
  store_name text not null default 'My Online Store',
  store_slug text unique not null,
  tagline text,
  logo_url text,
  favicon_url text,
  banner_url text,
  currency_code text not null default 'BDT',
  currency_symbol text not null default '৳',
  contact_email text,
  contact_phone text,
  address text,
  meta_title text,
  meta_description text,
  meta_keywords text,
  facebook_url text,
  instagram_url text,
  whatsapp_number text,
  cod_enabled boolean not null default true,
  bkash_enabled boolean not null default true,
  nagad_enabled boolean not null default true,
  rocket_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_store_settings_slug on public.store_settings(store_slug);

-- 2. Hierarchical Categories
create table if not exists public.categories (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants(id) on delete cascade not null,
  parent_id uuid references public.categories(id) on delete cascade,
  name text not null,
  slug text not null,
  photo_url text,
  display_order int not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique(merchant_id, slug)
);
create index if not exists idx_categories_parent on public.categories(parent_id);

-- 3. Ensure Products table has storefront fields
alter table if exists public.products
  add column if not exists slug text,
  add column if not exists category_id uuid references public.categories(id) on delete set null,
  add column if not exists featured_image text,
  add column if not exists short_description text,
  add column if not exists description text,
  add column if not exists is_featured boolean default false,
  add column if not exists is_active boolean default true,
  add column if not exists total_views bigint default 0;

-- 4. Product Gallery Photos
create table if not exists public.product_photos (
  id uuid primary key default gen_random_uuid(),
  product_id uuid references public.products(id) on delete cascade not null,
  photo_url text not null,
  display_order int not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists idx_product_photos_pid on public.product_photos(product_id);

-- 5. Ensure Orders table has storefront fields
alter table if exists public.orders
  add column if not exists order_number text,
  add column if not exists shipping_address text,
  add column if not exists shipping_city text,
  add column if not exists subtotal numeric(12,2) default 0.00,
  add column if not exists shipping_cost numeric(12,2) default 0.00,
  add column if not exists discount_amount numeric(12,2) default 0.00,
  add column if not exists total_amount numeric(12,2) default 0.00,
  add column if not exists order_status text default 'PENDING' check (order_status in ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
  add column if not exists customer_note text;

create index if not exists idx_orders_order_number on public.orders(order_number);

-- 6. Order Line Items
create table if not exists public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid references public.orders(id) on delete cascade not null,
  product_id uuid references public.products(id) on delete set null,
  variant_id uuid references public.product_variants(id) on delete set null,
  product_name text not null,
  size text,
  color text,
  quantity int not null default 1,
  unit_price numeric(12,2) not null,
  total_price numeric(12,2) not null,
  created_at timestamptz not null default now()
);
create index if not exists idx_order_items_order on public.order_items(order_id);

-- 7. Persistent Customer Carts
create table if not exists public.customer_carts (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants(id) on delete cascade not null,
  session_id text not null,
  customer_id uuid references public.customers(id) on delete cascade,
  product_id uuid references public.products(id) on delete cascade not null,
  variant_id uuid references public.product_variants(id) on delete set null,
  quantity int not null default 1,
  unit_price numeric(12,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists idx_carts_session on public.customer_carts(session_id);

-- 8. Coupons & Promo Codes
create table if not exists public.coupons (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants(id) on delete cascade not null,
  code text not null,
  discount_type text not null check (discount_type in ('percentage', 'fixed')),
  discount_value numeric(12,2) not null,
  minimum_order numeric(12,2) not null default 0.00,
  usage_limit int not null default 0,
  used_count int not null default 0,
  start_date date not null,
  end_date date not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique(merchant_id, code)
);

-- 9. Shipping Delivery Methods
create table if not exists public.shipping_methods (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references public.merchants(id) on delete cascade not null,
  name text not null,
  cost numeric(12,2) not null default 0.00,
  estimated_delivery_days text not null default '2-3 Days',
  is_active boolean not null default true
);

-- 10. Product Reviews
create table if not exists public.product_reviews (
  id uuid primary key default gen_random_uuid(),
  product_id uuid references public.products(id) on delete cascade not null,
  customer_name text not null,
  rating int not null check (rating between 1 and 5),
  review_text text,
  is_approved boolean not null default true,
  created_at timestamptz not null default now()
);
create index if not exists idx_product_reviews_pid on public.product_reviews(product_id);

-- 11. Automated Triggers

-- Trigger 1: Stock decrement on order item insertion
create or replace function public.handle_order_stock_decrement()
returns trigger language plpgsql security definer as $$
begin
  update public.products
  set stock_quantity = greatest(0, stock_quantity - new.quantity)
  where id = new.product_id;
  return new;
end;
$$;

drop trigger if exists trg_order_items_stock_decrement on public.order_items;
create trigger trg_order_items_stock_decrement
after insert on public.order_items
for each row execute function public.handle_order_stock_decrement();

-- Trigger 2: Instant notification to merchant on new order
create or replace function public.notify_merchant_new_order()
returns trigger language plpgsql security definer as $$
declare
  v_user_id uuid;
begin
  select user_id into v_user_id from public.merchants where id = new.merchant_id;
  
  insert into public.notifications (
    user_id,
    type,
    title,
    body,
    read,
    created_at
  ) values (
    v_user_id,
    'NEW_ORDER',
    'New Order: ' || coalesce(new.order_number, new.tran_id),
    'Total: ৳' || coalesce(new.total_amount, new.amount) || ' from ' || new.cus_name || ' (' || coalesce(new.payment_method, 'COD') || ')',
    false,
    now()
  );

  -- Also insert into merchant_notifications for mobile app push feed
  begin
    insert into public.merchant_notifications (
      merchant_id,
      type,
      title,
      message,
      severity,
      entity_type,
      entity_id
    ) values (
      new.merchant_id,
      'NEW_ORDER',
      'New Order: ' || coalesce(new.order_number, new.tran_id),
      'Total: ৳' || coalesce(new.total_amount, new.amount) || ' from ' || new.cus_name || ' (' || coalesce(new.payment_method, 'COD') || ')',
      'HIGH',
      'order',
      new.id::text
    );
  exception when others then
    -- Silently handle if merchant_notifications table is not installed
    null;
  end;

  return new;
end;
$$;

drop trigger if exists trg_orders_notify_merchant on public.orders;
create trigger trg_orders_notify_merchant
after insert on public.orders
for each row execute function public.notify_merchant_new_order();

-- 12. Enable Row Level Security (RLS) & Public Read Access for Storefront
alter table public.store_settings enable row level security;
alter table public.categories enable row level security;
alter table public.product_photos enable row level security;
alter table public.order_items enable row level security;
alter table public.customer_carts enable row level security;
alter table public.coupons enable row level security;
alter table public.shipping_methods enable row level security;
alter table public.product_reviews enable row level security;

-- Public read policies for storefront visitors
drop policy if exists "Storefront settings read" on public.store_settings;
create policy "Storefront settings read" on public.store_settings for select using (true);

drop policy if exists "Storefront categories read" on public.categories;
create policy "Storefront categories read" on public.categories for select using (is_active = true);

drop policy if exists "Storefront products read" on public.products;
create policy "Storefront products read" on public.products for select using (is_active = true);

drop policy if exists "Storefront photos read" on public.product_photos;
create policy "Storefront photos read" on public.product_photos for select using (true);

drop policy if exists "Storefront shipping read" on public.shipping_methods;
create policy "Storefront shipping read" on public.shipping_methods for select using (is_active = true);

drop policy if exists "Storefront reviews read" on public.product_reviews;
create policy "Storefront reviews read" on public.product_reviews for select using (is_approved = true);

-- Public insert policies for customer cart & checkout
drop policy if exists "Storefront carts manage" on public.customer_carts;
create policy "Storefront carts manage" on public.customer_carts for all using (true) with check (true);

drop policy if exists "Storefront order items insert" on public.order_items;
create policy "Storefront order items insert" on public.order_items for insert with check (true);

-- Authenticated merchant full control policies
drop policy if exists "Merchant manage settings" on public.store_settings;
create policy "Merchant manage settings" on public.store_settings for all to authenticated
  using (merchant_id = public.current_merchant_id());

drop policy if exists "Merchant manage categories" on public.categories;
create policy "Merchant manage categories" on public.categories for all to authenticated
  using (merchant_id = public.current_merchant_id());

drop policy if exists "Merchant manage product photos" on public.product_photos;
create policy "Merchant manage product photos" on public.product_photos for all to authenticated
  using (exists (select 1 from public.products p where p.id = product_id and p.merchant_id = public.current_merchant_id()));

drop policy if exists "Merchant manage order items" on public.order_items;
create policy "Merchant manage order items" on public.order_items for all to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id and o.merchant_id = public.current_merchant_id()));

drop policy if exists "Merchant manage coupons" on public.coupons;
create policy "Merchant manage coupons" on public.coupons for all to authenticated
  using (merchant_id = public.current_merchant_id());

drop policy if exists "Merchant manage shipping" on public.shipping_methods;
create policy "Merchant manage shipping" on public.shipping_methods for all to authenticated
  using (merchant_id = public.current_merchant_id());

-- 13. Enable Realtime Publications
do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'order_items') then
    alter publication supabase_realtime add table public.order_items;
  end if;
end $$;

