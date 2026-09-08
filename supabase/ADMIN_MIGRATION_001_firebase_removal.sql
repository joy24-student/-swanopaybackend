-- ============================================================================
-- SwapnoPay ADMIN DB — Migration 001: Firebase Removal & Schema Completion
-- Run this against your Admin Supabase project
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Complete merchant_gateway_settings table (missing columns)
-- ─────────────────────────────────────────────────────────────────────────────
alter table merchant_gateway_settings
  add column if not exists merchant_name       text,
  add column if not exists merchant_logo_url   text,
  add column if not exists supabase_url        text,
  add column if not exists supabase_anon_key   text,
  add column if not exists qr_codes            jsonb not null default '{}'::jsonb,
  add column if not exists status              text  not null default 'ACTIVE'
    check (status in ('ACTIVE', 'SUSPENDED', 'MAINTENANCE'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Form Submissions (replaces Firestore platform_submissions)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists form_submissions (
  id               uuid primary key default gen_random_uuid(),
  form_id          text,
  merchant_id      text,
  customer_name    text,
  customer_phone   text,
  customer_email   text,
  amount_bdt       numeric(12,2),
  payment_status   text default 'NOT_REQUIRED',
  answers          jsonb default '{}'::jsonb,
  created_at       timestamptz not null default now()
);
create index if not exists form_submissions_merchant_idx on form_submissions(merchant_id, created_at desc);

alter table form_submissions enable row level security;
create policy "admin_read_form_submissions" on form_submissions
  for select using (exists (select 1 from admin_users where id = auth.uid()));
create policy "service_insert_form_submissions" on form_submissions
  for insert with check (auth.role() = 'service_role');

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Support Tickets (replaces Firebase RTDB platform_owner/tickets)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists support_tickets (
  id             uuid primary key default gen_random_uuid(),
  merchant_id    text,
  business_name  text,
  email          text,
  phone          text,
  category       text not null default 'GENERAL',
  subject        text not null,
  description    text not null,
  status         text not null default 'OPEN'
    check (status in ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
  admin_reply    text,
  resolved_at    timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index if not exists support_tickets_status_idx on support_tickets(status, created_at desc);
create index if not exists support_tickets_merchant_idx on support_tickets(merchant_id);

alter table support_tickets enable row level security;
create policy "admin_manage_support_tickets" on support_tickets
  for all using (exists (select 1 from admin_users where id = auth.uid()));
create policy "service_insert_support_tickets" on support_tickets
  for insert with check (auth.role() = 'service_role');

create trigger support_tickets_updated_at
  before update on support_tickets
  for each row execute function set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Feature Requests (replaces Firebase RTDB platform_owner/feature_requests)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists feature_requests (
  id            uuid primary key default gen_random_uuid(),
  merchant_id   text,
  business_name text,
  email         text,
  phone         text,
  title         text not null,
  category      text not null default 'GENERAL',
  description   text not null,
  priority      text not null default 'MEDIUM'
    check (priority in ('LOW','MEDIUM','HIGH','CRITICAL')),
  status        text not null default 'PENDING'
    check (status in ('PENDING','UNDER_REVIEW','PLANNED','IN_PROGRESS','COMPLETED','REJECTED')),
  admin_notes   text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create index if not exists feature_requests_status_idx on feature_requests(status, created_at desc);

alter table feature_requests enable row level security;
create policy "admin_manage_feature_requests" on feature_requests
  for all using (exists (select 1 from admin_users where id = auth.uid()));
create policy "service_insert_feature_requests" on feature_requests
  for insert with check (auth.role() = 'service_role');

create trigger feature_requests_updated_at
  before update on feature_requests
  for each row execute function set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Live Chat Messages (replaces Firebase RTDB platform_owner/live_chats)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists live_chat_messages (
  id           uuid primary key default gen_random_uuid(),
  merchant_id  text not null,
  sender       text not null check (sender in ('MERCHANT','PLATFORM_OWNER','AI_SUPPORT')),
  message      text not null,
  created_at   timestamptz not null default now()
);
create index if not exists live_chat_merchant_idx on live_chat_messages(merchant_id, created_at asc);

alter table live_chat_messages enable row level security;
create policy "admin_manage_live_chat" on live_chat_messages
  for all using (exists (select 1 from admin_users where id = auth.uid()));
create policy "service_insert_live_chat" on live_chat_messages
  for insert with check (auth.role() = 'service_role');

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Admin Notifications (powers the real-time toast system)
-- ─────────────────────────────────────────────────────────────────────────────
create table if not exists admin_notifications (
  id          uuid primary key default gen_random_uuid(),
  type        text not null,  -- 'payment', 'ticket', 'feature_request', 'system'
  title       text not null,
  message     text not null,
  metadata    jsonb default '{}'::jsonb,
  read        boolean not null default false,
  created_at  timestamptz not null default now()
);
create index if not exists admin_notifications_unread_idx on admin_notifications(read, created_at desc);

alter table admin_notifications enable row level security;
create policy "admin_manage_notifications" on admin_notifications
  for all using (exists (select 1 from admin_users where id = auth.uid()));
create policy "service_insert_notifications" on admin_notifications
  for insert with check (auth.role() = 'service_role');

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. Fix RLS: payment_events service insert (was too permissive)
-- ─────────────────────────────────────────────────────────────────────────────
drop policy if exists "service_insert_payment_events" on payment_events;
create policy "service_insert_payment_events" on payment_events
  for insert with check (auth.role() = 'service_role');

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Trigger: auto-create admin_notification on new payment_event (PAID)
-- ─────────────────────────────────────────────────────────────────────────────
create or replace function notify_admin_on_payment()
returns trigger language plpgsql as $$
begin
  if new.status = 'PAID' then
    insert into admin_notifications(type, title, message, metadata)
    values (
      'payment',
      'Payment Received — ৳' || coalesce(new.amount::text, '0'),
      coalesce(new.merchant_name, 'Merchant') || ' · ' || coalesce(new.payment_method, 'MFS') || ' · Order #' || left(new.order_id, 8),
      jsonb_build_object('order_id', new.order_id, 'merchant_id', new.merchant_id, 'amount', new.amount, 'payment_method', new.payment_method)
    );
  end if;
  return new;
end;
$$;

drop trigger if exists payment_events_notify_admin on payment_events;
create trigger payment_events_notify_admin
  after insert on payment_events
  for each row execute function notify_admin_on_payment();

-- ─────────────────────────────────────────────────────────────────────────────
-- 9. Trigger: auto-create admin_notification on new support ticket
-- ─────────────────────────────────────────────────────────────────────────────
create or replace function notify_admin_on_ticket()
returns trigger language plpgsql as $$
begin
  insert into admin_notifications(type, title, message, metadata)
  values (
    'ticket',
    'New Support Ticket — ' || new.category,
    coalesce(new.business_name, 'Merchant') || ': ' || new.subject,
    jsonb_build_object('ticket_id', new.id, 'merchant_id', new.merchant_id, 'status', new.status)
  );
  return new;
end;
$$;

drop trigger if exists support_tickets_notify_admin on support_tickets;
create trigger support_tickets_notify_admin
  after insert on support_tickets
  for each row execute function notify_admin_on_ticket();

-- ─────────────────────────────────────────────────────────────────────────────
-- 10. Enable Supabase Realtime on notification-related tables
-- ─────────────────────────────────────────────────────────────────────────────
-- Run these in Supabase Dashboard > Database > Replication, OR:
alter publication supabase_realtime add table admin_notifications;
alter publication supabase_realtime add table support_tickets;
alter publication supabase_realtime add table feature_requests;
alter publication supabase_realtime add table live_chat_messages;
alter publication supabase_realtime add table payment_events;
