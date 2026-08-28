-- 7. QR Code Inventory & Product Variants Schema

-- Table for product variants (each variant can have its own prices, QR code, and stock)
create table if not exists product_variants (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  product_id uuid references products on delete cascade not null,
  variant_name text not null default 'Standard', -- e.g. 'Size XL', 'Red 500g'
  supplier_id uuid references suppliers on delete set null,
  qr_code text unique not null,
  cost_price numeric(12,2) not null default 0.00,  -- Real prize / purchase cost
  asking_price numeric(12,2) not null default 0.00,-- Asking / MSRP / Tag prize
  sale_price numeric(12,2) not null default 0.00,  -- Actual selling prize
  stock_quantity numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- Enable RLS
alter table product_variants enable row level security;

-- Setup RLS Policy
create policy "Product variants merchant access" on product_variants 
  for all using (merchant_id = current_merchant_id());

-- Indexes for performance
create index if not exists idx_product_variants_merchant on product_variants(merchant_id);
create index if not exists idx_product_variants_product on product_variants(product_id);
create index if not exists idx_product_variants_qr_code on product_variants(qr_code);
