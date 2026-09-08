-- ============================================================================
-- Migration 16: Gateway Device Alerts & Customer Re-engagement
-- Tracks customer alert requests when merchant device is offline,
-- and triggers re-engagement emails when the device comes back online.
-- ============================================================================

create table if not exists gateway_device_alerts (
  id              uuid primary key default gen_random_uuid(),
  merchant_id     text not null,
  customer_email  text not null,
  order_id        text,
  amount          numeric,
  checkout_url    text not null,
  status          text not null default 'PENDING' check (status in ('PENDING', 'NOTIFIED', 'CANCELLED')),
  notified_at     timestamptz,
  created_at      timestamptz not null default now()
);

create index if not exists idx_device_alerts_merchant on gateway_device_alerts(merchant_id, status);
create index if not exists idx_device_alerts_email on gateway_device_alerts(customer_email);
