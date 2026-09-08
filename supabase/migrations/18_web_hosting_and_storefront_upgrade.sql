-- Migration 18: Web Hosting, Storefront Settings, and Catalog Sync Hardening

-- 1. Hardening store_settings for single-merchant storefronts & VPS deployment
alter table if exists public.store_settings
  add column if not exists theme_color text not null default '#4F46E5',
  add column if not exists custom_domain text,
  add column if not exists is_active boolean not null default true,
  add column if not exists vps_server_ip text,
  add column if not exists deployment_status text not null default 'LIVE',
  add column if not exists "BASE_URL" text;

-- Ensure unique constraint on store_settings(merchant_id) for safe upserts
create unique index if not exists store_settings_merchant_unique
  on public.store_settings(merchant_id);

-- Ensure index on store_slug
create index if not exists store_settings_slug_idx
  on public.store_settings(store_slug);

-- 2. Hardening products catalog
alter table if exists public.products
  add column if not exists is_active boolean not null default true,
  add column if not exists is_featured boolean not null default false,
  add column if not exists total_views bigint not null default 0;

create index if not exists idx_products_merchant_active
  on public.products(merchant_id, is_active);

-- 3. Graceful compatibility for legacy tbl_settings if present
do $$
begin
  if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'tbl_settings') then
    alter table public.tbl_settings add column if not exists store_slug text;
    alter table public.tbl_settings add column if not exists site_title text;
    alter table public.tbl_settings add column if not exists theme_color text default '#4F46E5';
    alter table public.tbl_settings add column if not exists custom_domain text;
    alter table public.tbl_settings add column if not exists is_active boolean default true;
    alter table public.tbl_settings add column if not exists currency_code text default 'BDT';
    alter table public.tbl_settings add column if not exists "BASE_URL" text;
    create unique index if not exists tbl_settings_merchant_unique on public.tbl_settings(merchant_id) where merchant_id is not null;
  end if;
end $$;
