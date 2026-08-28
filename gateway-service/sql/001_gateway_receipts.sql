create table if not exists merchant_gateway_connections (
  id bigserial primary key,
  project_ref text not null,
  merchant_id uuid not null,
  api_key_digest text not null unique,
  enabled boolean not null default true,
  daily_email_limit integer not null default 1000 check (daily_email_limit between 1 and 100000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (project_ref, merchant_id)
);

create table if not exists payment_receipt_deliveries (
  id bigserial primary key,
  connection_id bigint not null references merchant_gateway_connections(id) on delete cascade,
  project_ref text not null,
  merchant_id uuid not null,
  event_id uuid not null,
  order_id uuid not null,
  transaction_id text not null,
  payload_digest text not null,
  status text not null default 'PENDING' check (status in ('PENDING','PROCESSING','SENT','FAILED')),
  customer_message_id text,
  merchant_message_id text,
  attempts integer not null default 0,
  last_error text,
  claimed_at timestamptz,
  delivered_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (project_ref, event_id)
);
create index if not exists payment_receipt_deliveries_connection_created_idx
  on payment_receipt_deliveries(connection_id, created_at desc);
