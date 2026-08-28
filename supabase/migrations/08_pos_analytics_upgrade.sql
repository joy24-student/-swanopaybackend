-- Migration 08: POS Sales, Business Analytics & Schema Upgrade for SwapnoPay
-- Upgrades Supabase PostgreSQL Schema to match UI & Room SQLite DB (Version 6)

-- ============================================================================
-- 1. Create POS Sales Invoices Table
-- ============================================================================
create table if not exists pos_sales (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  invoice_no text not null,
  customer_id uuid references customers on delete set null,
  customer_name text not null default 'Walk-in Customer',
  customer_phone text default '',
  subtotal numeric(12,2) not null default 0.00,
  discount numeric(12,2) not null default 0.00,
  net_total numeric(12,2) not null default 0.00,
  cash_received numeric(12,2) not null default 0.00,
  change_due numeric(12,2) not null default 0.00,
  payment_method text not null default 'Cash' check (payment_method in ('Cash', 'bKash', 'Card', 'Due')),
  payment_status text not null default 'PAID' check (payment_status in ('PAID', 'PARTIAL', 'DUE')),
  item_count integer not null default 1,
  cart_items jsonb default '[]',
  timestamp timestamptz default now(),
  created_at timestamptz default now()
);

-- ============================================================================
-- 2. Create Business Analytics Summary Table
-- ============================================================================
create table if not exists business_analytics (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  total_revenue numeric(12,2) not null default 0.00,
  cash_received numeric(12,2) not null default 0.00,
  total_dues numeric(12,2) not null default 0.00,
  total_payables numeric(12,2) not null default 0.00,
  total_expenses numeric(12,2) not null default 0.00,
  net_profit numeric(12,2) not null default 0.00,
  last_updated timestamptz default now()
);

-- ============================================================================
-- 3. Upgrade Existing Tables with Missing Columns
-- ============================================================================

-- Products: Add QR Code, Image URL, Cost & Asking Prices
alter table products add column if not exists qr_code text;
alter table products add column if not exists image_url text;
alter table products add column if not exists cost_price numeric(12,2) default 0.00;
alter table products add column if not exists asking_price numeric(12,2) default 0.00;

-- Ledger Transactions: Add Payment Method, Invoice No, Tagada Reminder timestamp
alter table ledger_transactions add column if not exists payment_method text default 'Cash';
alter table ledger_transactions add column if not exists invoice_no text;
alter table ledger_transactions add column if not exists tagada_sent_at timestamptz;

-- Expenses: Add Receipt Image URL, Payment Method, Recurring flag
alter table expenses add column if not exists receipt_image_url text;
alter table expenses add column if not exists payment_method text default 'Cash';
alter table expenses add column if not exists is_recurring boolean default false;

-- Stock Transactions: Add Variant Reference & Notes
alter table stock_transactions add column if not exists variant_id uuid references product_variants(id) on delete set null;
alter table stock_transactions add column if not exists reference_note text default 'Stock Transaction';

-- ============================================================================
-- 4. Enable Row Level Security (RLS) & Policies
-- ============================================================================
alter table pos_sales enable row level security;
alter table business_analytics enable row level security;

-- POS Sales RLS Policies
create policy "Merchants can manage POS sales"
  on pos_sales for all
  using (auth.uid() = merchant_id or merchant_id in (select id from merchants where user_id = auth.uid()));

-- Business Analytics RLS Policies
create policy "Merchants can view analytics"
  on business_analytics for all
  using (auth.uid() = merchant_id or merchant_id in (select id from merchants where user_id = auth.uid()));

-- ============================================================================
-- 5. Enable Supabase Realtime Publication
-- ============================================================================
alter publication supabase_realtime add table pos_sales;
alter publication supabase_realtime add table business_analytics;
