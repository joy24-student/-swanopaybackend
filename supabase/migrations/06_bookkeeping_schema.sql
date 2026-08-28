-- 6. Bookkeeping and AI Business Copilot Tables Schema

-- Customers Table
create table if not exists customers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  phone text not null,
  email text,
  address text,
  opening_balance numeric(12,2) not null default 0.00,
  current_balance numeric(12,2) not null default 0.00,
  status text not null default 'VIP' check (status in ('VIP', 'Risk', 'Inactive', 'Potential')),
  created_at timestamptz default now()
);

-- Suppliers Table
create table if not exists suppliers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  phone text not null,
  email text,
  address text,
  opening_balance numeric(12,2) not null default 0.00,
  current_balance numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- Ledger Transactions Table (Credit & Payment records)
create table if not exists ledger_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  customer_id uuid references customers on delete cascade,
  supplier_id uuid references suppliers on delete cascade,
  type text not null check (type in ('credit', 'payment')), -- credit = বাকি, payment = জমা
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  note text,
  product_details jsonb default '[]', -- JSON detail of items included in transaction
  is_voice_entry boolean default false,
  attachment_url text,
  created_at timestamptz default now()
);

-- Products & Inventory Table
create table if not exists products (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  code text,
  category text,
  purchase_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  min_stock_threshold numeric(12,2) not null default 5.00,
  unit text not null default 'pcs', -- pcs, kg, bag, ltr
  created_at timestamptz default now()
);

-- Stock Transactions Table (Stock In/Out log)
create table if not exists stock_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  product_id uuid references products on delete cascade not null,
  type text not null check (type in ('in', 'out')),
  quantity numeric(12,2) not null,
  price numeric(12,2) not null,
  customer_id uuid references customers on delete set null,
  supplier_id uuid references suppliers on delete set null,
  created_at timestamptz default now()
);

-- Expenses Table
create table if not exists expenses (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  category text not null check (category in ('Utilities', 'Rent', 'Transport', 'Operating', 'Payroll', 'Others')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  description text,
  created_at timestamptz default now()
);

-- Business Loans Table
create table if not exists loans (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  principal_amount numeric(12,2) not null,
  interest_rate numeric(5,2) not null,
  interest_type text not null check (interest_type in ('Flat', 'Reducing')),
  duration_months integer not null,
  monthly_installment numeric(12,2) not null,
  status text not null default 'applied' check (status in ('applied', 'approved', 'disbursed', 'repaid')),
  applied_at timestamptz default now(),
  disbursed_at timestamptz
);

-- Enable RLS for all new tables
alter table customers enable row level security;
alter table suppliers enable row level security;
alter table ledger_transactions enable row level security;
alter table products enable row level security;
alter table stock_transactions enable row level security;
alter table expenses enable row level security;
alter table loans enable row level security;

-- Setup RLS Policies (all scoped to active merchant_id)
create policy "Customers merchant access" on customers for all using (merchant_id = current_merchant_id());
create policy "Suppliers merchant access" on suppliers for all using (merchant_id = current_merchant_id());
create policy "Ledger transactions merchant access" on ledger_transactions for all using (merchant_id = current_merchant_id());
create policy "Products merchant access" on products for all using (merchant_id = current_merchant_id());
create policy "Stock transactions merchant access" on stock_transactions for all using (merchant_id = current_merchant_id());
create policy "Expenses merchant access" on expenses for all using (merchant_id = current_merchant_id());
create policy "Loans merchant access" on loans for all using (merchant_id = current_merchant_id());

-- Indexes for performance
create index if not exists idx_customers_merchant on customers(merchant_id);
create index if not exists idx_suppliers_merchant on suppliers(merchant_id);
create index if not exists idx_ledger_transactions_customer on ledger_transactions(customer_id);
create index if not exists idx_products_merchant on products(merchant_id);
create index if not exists idx_expenses_merchant on expenses(merchant_id);
