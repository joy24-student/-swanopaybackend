# SmartPay – Complete Enterprise Documentation
### Version 3.1
**Real‑time payment verification for bKash, Nagad, Rocket, and Upay — with a platform-hosted Payment Gateway Page and Form Builder, backed by each merchant's own Supabase project**
Platform hosts the frontends only. Merchant owns 100% of the database, Edge Functions, and data — no recurring backend fees, full data sovereignty.

---

## Table of Contents
1. [System Overview](#1-system-overview)
2. [Complete Feature List](#2-complete-feature-list)
3. [Architecture](#3-architecture)
4. [Supabase Backend Setup](#4-supabase-backend-setup)
5. [SmartPay Merchant Android App](#5-smartpay-merchant-android-app)
6. [Payment Gateway Integration](#6-payment-gateway-integration)
7. [Hosted Payment Pages (No‑Code Checkout Forms)](#7-hosted-payment-pages-no-code-checkout-forms)
8. [Complete API Reference](#8-complete-api-reference)
9. [Appeal System](#9-appeal-system)
10. [Deployment, Operations & Environment Management](#10-deployment-operations--environment-management)
11. [Testing & Troubleshooting](#11-testing--troubleshooting)
12. [Appendix: SMS Templates & Regex Patterns](#12-appendix-sms-templates--regex-patterns)
13. [Roadmap](#13-roadmap)

---

## 1. System Overview

SmartPay replaces manual payment verification with a fully automated, self‑hosted system built entirely on infrastructure the merchant owns. There is no SmartPay-run server sitting between the merchant and their money — the merchant's own Supabase project *is* the backend.

**How it works end to end:**

1. A merchant installs the SmartPay Android app on a dedicated phone (or phones) that holds their bKash/Nagad/Rocket/Upay SIM.
2. The app silently reads incoming SMS from mobile banking senders and uploads the parsed transaction to the merchant's Supabase project.
3. A database-triggered Edge Function (`process-sms`) matches the incoming payment to a pending order using phone number + amount, inside a configurable time window (default 10 minutes).
4. The moment a match is found, the order status flips to `PAID` in Postgres. Any client subscribed via Supabase Realtime (the widget, the hosted page, a custom frontend) sees the update instantly — no polling.
5. A signed webhook fires to the merchant's own backend so server-side systems (order fulfillment, inventory, CRM) can react.
6. A branded receipt is generated and optionally emailed to the customer.
7. If no match is found before expiry, the customer can file an **appeal** with their transaction ID, which the merchant reviews and resolves from the Android app.

**Key principles:**

| Principle | What it means in practice |
|---|---|
| 100% self‑hosted | All order, payment, and customer data lives in the merchant's own Supabase project. SmartPay never sees or stores it. |
| No third‑party server | The only "SmartPay" infrastructure is a) the Android app APK and b) the Edge Function / widget source code, both of which run inside the merchant's own accounts. |
| No API agreements needed | Because payments are detected via SMS rather than a bank/MFS API integration, there's no merchant agreement, approval process, or revenue share with bKash/Nagad/Rocket/Upay required to get started. |
| Real‑time, event‑driven | Matching happens the instant an SMS lands, via a Postgres trigger → Edge Function, not a cron/polling job. |
| Fallback safety net | Appeals exist for the ~1–5% of payments that don't auto-match (mismatched phone number, delayed SMS, order already expired). |
| One-time cost | No per-transaction fee, no monthly SaaS subscription — merchant pays once for the app/source and hosts it on their own (often free-tier) Supabase project. |

**Who this is for:** merchants in markets where bKash/Nagad/Rocket/Upay dominate but formal payment gateway APIs are slow to approve, expensive, or unavailable to a given business category — while still wanting a modern, automated, no-manual-checking checkout experience comparable to Stripe/PayPal.

---

## 2. Complete Feature List

### Core Payment Automation
- Automatic SMS reading and parsing for bKash, Nagad, Rocket, Upay (traditional and newer message formats).
- Phone number + amount matching within a configurable time window (default 10 minutes, adjustable per merchant).
- Real‑time order status updates (`PENDING` → `PAID` / `EXPIRED` / `CANCELLED`) via Supabase Realtime — no page refresh needed anywhere in the stack.
- Webhook delivery with HMAC‑SHA256 signature for server‑side integration, with retry/backoff.
- Digital receipt generation (HTML + PDF) with merchant branding, delivered by email and shown in-app.
- Duplicate-payment protection via SHA‑256 SMS hashing.

### Android App (Merchant Side)
- Foreground SMS monitoring with a persistent, low-priority notification (Android battery-optimization compliant).
- Offline SMS queue with automatic retry via WorkManager.
- Multi‑device support — run multiple phones per merchant (e.g. one per MFS provider, or one per branch).
- Device registration, health monitoring (battery, connectivity, last-sync), and remote disable from the dashboard.
- Home dashboard with live revenue, pending orders, unmatched payments, and appeal count.
- Payment number management (add/edit/remove bKash/Nagad/Rocket/Upay numbers, default number selection).
- Order and payment viewing with filtering and search.
- Manual matching UI for unmatched payments.
- Appeal management (approve/reject/match to order).
- Customer CRM (list, payment history, notes).
- Reports (daily/weekly/monthly), export to CSV/PDF.
- Push notifications (FCM) for payment events, appeal submissions, device health alerts.
- Multi‑merchant account switching (agencies/operators managing several stores from one app).
- Security: biometric lock, session timeout, root/emulator detection, encrypted local DB (SQLCipher).

### Customer Checkout — three integration modes
1. **Embedded Widget** — a drop-in JS snippet the merchant adds to their own site.
2. **Headless API** — full control; merchant builds their own UI and calls the REST/Realtime API directly.
3. **Hosted Payment Pages** *(new in v3.0)* — SmartPay hosts the entire checkout/intake form; the merchant supplies only an API key and JSON configuration, and gets back a shareable URL. Covers multistep/single-page forms, all question types, file uploads, branded receipts, and redirects. See [Section 7](#7-hosted-payment-pages-no-code-checkout-forms).

### Headless API
- REST API to create orders, verify payments, manage appeals, and now manage hosted payment pages.
- Optional GraphQL endpoint (via PostgREST + `pg_graphql`) for flexible queries.
- Community SDKs for Node.js, PHP, Python, and Flutter.
- OpenAPI / Swagger documentation generated from the Edge Function contracts.

### Enterprise Features
- Multi‑merchant / multi‑store management from a single app or account.
- Role‑based access control — Owner, Admin, Finance, Operator, Read‑Only.
- Audit logs — immutable record of every state-changing action (`security_logs`).
- Encryption at rest for sensitive PII (phone numbers, uploaded documents) using `pgsodium`/application-level envelope encryption.
- Secret Manager — API keys and webhook secrets stored in Supabase Vault rather than plaintext columns.
- Configurable per-merchant rate limiting on public Edge Function endpoints.
- Disaster recovery — daily database backups and Storage exports to external cloud storage.
- Multi‑currency support (display + conversion) for USD, EUR, etc., alongside BDT.
- White‑labeling — custom branding across the widget, hosted pages, receipts, and dashboard.
- Plugin marketplace concept — ready integrations for WooCommerce, Shopify, WordPress.

---

## 3. Architecture

SmartPay is split cleanly into two ownership zones:

- **The SmartPay Platform** (hosted by *you*, the SmartPay operator) — two generic, stateless, multi-tenant frontend applications. They hold no merchant data of their own; every request they make is aimed at whichever Supabase project the merchant has connected.
- **Each Merchant's own Supabase Project** (hosted by *the merchant*, in their own Supabase account) — 100% of the data and business logic: database, Edge Functions, Storage, Auth, Realtime. This is unchanged from Sections 4–9 of this document.

```
+---------------------------------------------------------------------------+
|                         SmartPay Platform (hosted by you)                  |
|                                                                             |
|   +----------------------------+      +----------------------------+       |
|   |   Form Builder (admin)      |      |  Payment Gateway Page       |      |
|   |   — merchant designs pages, |      |  — public checkout/receipt  |      |
|   |     connects their own      |      |    renderer, used by every  |      |
|   |     Supabase project        |      |    merchant                 |      |
|   +--------------+---------------+      +---------------+--------------+     |
|                  |                                      |                   |
|   No database. No merchant records. No Edge Functions run here.            |
|   Both apps are pure frontend — every read/write goes straight to the      |
|   merchant's OWN Supabase project below, using credentials the merchant    |
|   supplies (see 7.1a "Connecting Your Supabase Project").                  |
+------------------------------|---------------------------|-----------------+
                                |                           |
                 merchant's Supabase URL + anon key, per request
                                |                           |
                                v                           v
+-------------------------------------------------------------------------------------------------+
|                              Merchant's own Supabase Project                                     |
|                                                                                                    |
|   +-------------+   +--------------+   +-----------+   +----------------------------------+       |
|   | PostgREST   |   | Realtime      |   | Storage   |   | Auth (merchant login, device auth)|      |
|   | (REST API)  |   | (WebSocket)   |   | (files,   |   |                                    |     |
|   |             |   |               |   |  receipts)|   +------------------------------------+     |
|   +-------------+   +--------------+   +-----------+                                              |
|                                                                                                    |
|   +----------------------------------------------------------------------------------------+       |
|   |                                PostgreSQL Database                                      |       |
|   |  merchants, merchant_numbers, orders, payments, sms_logs, devices, appeals,              |       |
|   |  notifications, security_logs, payment_pages, form_submissions, page_files               |       |
|   +----------------------------------------------------------------------------------------+       |
|                                                                                                    |
|   +----------------------------------------------------------------------------------------+       |
|   |                                    Edge Functions                                        |       |
|   |  create-order · process-sms · match-payment-manual · resolve-appeal · extend-order ·     |       |
|   |  cancel-order · get-dashboard-stats · create-payment-page · submit-payment-form ·         |       |
|   |  get-payment-page · get-form-submission · generate-receipt                                |       |
|   |  (all deployed BY the merchant, from the SmartPay source, INTO their own project)          |      |
|   +----------------------------------------------------------------------------------------+       |
+-------------------------------------------------------------------------------------------------+
            |                                                                    |
            v                                                                    v
+---------------------------+                                    +---------------------------------+
|   Merchant Backend         |                                    |   Notification Services           |
|   (Webhook Receiver)       |                                    |   (FCM push, transactional email) |
+---------------------------+                                    +---------------------------------+
```

**Why this matters:** you are not a data processor for any merchant's customers, orders, or payment data — you never touch it, store it, or have credentials to reach it beyond what a merchant explicitly hands your frontend for a given session/request. Your entire product surface is two well-built UIs; every merchant is fully self-hosted from the database layer down, exactly as in the original SmartPay design — the only thing that changed is that *you* now also provide (and host) the checkout page and the form-design tool, instead of the merchant having to build or embed those themselves.

**Data flow for a single transaction (happy path):**

1. Order created (`orders` row, `status = PENDING`) — via widget, headless API call, or hosted-page submission.
2. Customer sends money via their mobile banking app to the merchant's displayed number.
3. bKash/Nagad/Rocket/Upay sends a confirmation SMS to the merchant's phone.
4. SmartPay Android app parses it, hashes it, and inserts a row into `sms_logs`.
5. A Postgres `INSERT` webhook fires the `process-sms` Edge Function.
6. `process-sms` looks for a `PENDING` order with matching `cus_phone` + `amount` inside the expiry window and calls the `match_payment_atomic()` Postgres function to atomically flip the order to `PAID` and insert the corresponding `payments` row.
7. Supabase Realtime pushes the `UPDATE` to any subscribed client (widget/hosted page) instantly.
8. `process-sms` (or a follow-up trigger) fires the merchant's webhook and kicks off `generate-receipt`.

---

## 4. Supabase Backend Setup

This section provides the exact steps to create and configure the backend that the Android app, widget, headless API, and hosted pages all connect to.

### 4.1 Database Schema & SQL

Run the following SQL in the Supabase SQL Editor to create all required tables, indexes, and triggers. (Hosted Payment Page tables are in [Section 7.6](#76-new-database-tables).)

```sql
-- Enable pgcrypto for gen_random_uuid()
create extension if not exists "pgcrypto";

-- Merchants
create table merchants (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users not null,
  business_name text not null default 'My Store',
  default_number text,
  webhook_secret text not null default encode(gen_random_bytes(32), 'hex'),
  match_window_minutes integer not null default 10,
  timezone text not null default 'Asia/Dhaka',
  logo_url text,
  brand_color text default '#0b5fff',
  created_at timestamptz default now()
);

-- Merchant payment numbers (bKash, Nagad, Rocket, Upay)
create table merchant_numbers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  number text not null,
  type text not null check (type in ('bKash','Nagad','Rocket','Upay')),
  is_default boolean default false,
  active boolean default true,
  created_at timestamptz default now()
);

-- Orders
create table orders (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants not null,
  tran_id text unique not null,
  amount numeric(12,2) not null,
  currency text not null default 'BDT',
  cus_phone text not null,
  cus_email text,
  cus_name text,
  status text not null default 'PENDING' check (status in ('PENDING','PAID','EXPIRED','CANCELLED')),
  product_name text,
  product_category text,
  callback_url text,
  success_url text,
  fail_url text,
  cancel_url text,
  metadata jsonb default '{}',
  expires_at timestamptz not null,
  paid_at timestamptz,
  payment_method text,
  sender_number text,
  matched_trx_id text,
  manual_match boolean default false,
  created_at timestamptz default now()
);

-- Payments (matched/unmatched)
create table payments (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants not null,
  amount numeric(12,2) not null,
  sender_number text,
  merchant_number text,
  sms_timestamp timestamptz,
  sms_hash text unique not null,
  status text not null default 'UNMATCHED' check (status in ('MATCHED','UNMATCHED','DUPLICATE')),
  matched_order_id uuid references orders,
  created_at timestamptz default now()
);

-- SMS logs (raw and parsed)
create table sms_logs (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null,
  merchant_id uuid references merchants not null,
  raw_sms text not null,
  parsed_amount numeric(12,2),
  parsed_sender text,
  parsed_trx_id text,
  parsed_timestamp timestamptz,
  sms_hash text unique not null,
  processed boolean default false,
  status text default 'unprocessed' check (status in ('unprocessed','matched','unmatched','duplicate')),
  created_at timestamptz default now()
);

-- Devices
create table devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users not null,
  merchant_id uuid references merchants,
  fcm_token text,
  device_model text,
  os_version text,
  battery_level integer,
  online boolean default true,
  last_sync timestamptz default now(),
  disabled boolean default false,
  created_at timestamptz default now()
);

-- Appeals
create table appeals (
  id uuid primary key default gen_random_uuid(),
  trx_id text not null,
  cus_phone text,
  order_id uuid references orders,
  note text,
  screenshot_path text,
  status text default 'PENDING_REVIEW' check (status in ('PENDING_REVIEW','APPROVED','REJECTED')),
  resolved_by uuid references auth.users,
  resolved_at timestamptz,
  created_at timestamptz default now()
);

-- Notifications
create table notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users not null,
  type text,
  title text,
  body text,
  read boolean default false,
  created_at timestamptz default now()
);

-- Security logs (audit)
create table security_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users,
  merchant_id uuid references merchants,
  event text not null,
  details jsonb,
  ip_address text,
  created_at timestamptz default now()
);

-- Indexes
create index idx_orders_merchant_status on orders(merchant_id, status);
create index idx_orders_expires on orders(expires_at) where status = 'PENDING';
create index idx_orders_tran_id on orders(tran_id);
create index idx_payments_sms_hash on payments(sms_hash);
create index idx_payments_merchant_status on payments(merchant_id, status);
create index idx_sms_logs_merchant_created on sms_logs(merchant_id, created_at);
create index idx_sms_logs_hash on sms_logs(sms_hash);
create index idx_appeals_status on appeals(status);
create index idx_devices_merchant on devices(merchant_id);
create index idx_notifications_user_read on notifications(user_id, read);
```

**Notes on this schema vs. v2.0:**
- `merchants.webhook_secret` is now a real column (auto-generated), since `process-sms` and the receipt system depend on it for HMAC signing — it was referenced in code but missing from the original table definition.
- `merchants.match_window_minutes` and `timezone` make the previously hard-coded "10 minute window" configurable per merchant.
- `orders.currency` supports the multi-currency enterprise feature.
- `appeals.screenshot_path` stores the Storage path for evidence uploads.

### 4.2 Row Level Security Policies

Enable RLS and create policies so each merchant (identified by `user_id`) can only access their own data, and the Android device can only upload SMS logs for itself.

```sql
-- Enable RLS
alter table merchants enable row level security;
alter table merchant_numbers enable row level security;
alter table orders enable row level security;
alter table payments enable row level security;
alter table sms_logs enable row level security;
alter table devices enable row level security;
alter table appeals enable row level security;
alter table notifications enable row level security;
alter table security_logs enable row level security;

-- Helper function: get current user's merchant_id
create or replace function current_merchant_id()
returns uuid language sql stable as $$
  select id from merchants where user_id = auth.uid() limit 1;
$$;

-- Merchants: user can read/write own record
create policy "Merchant own record" on merchants
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Merchant numbers
create policy "Merchant numbers access" on merchant_numbers
  for all using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());

-- Orders: merchant can read/write own orders
create policy "Orders merchant access" on orders
  for all using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());

-- Payments: merchant only
create policy "Payments merchant access" on payments
  for all using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());

-- SMS logs: insert by registered device only, read by merchant
create policy "SMS logs insert by device" on sms_logs
  for insert with check (
    exists (select 1 from devices where id = device_id and user_id = auth.uid())
  );
create policy "SMS logs read merchant" on sms_logs
  for select using (merchant_id = current_merchant_id());

-- Devices: user can manage own devices; merchant can see all devices tied to them
create policy "Devices own" on devices
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "Devices merchant read" on devices
  for select using (merchant_id = current_merchant_id());

-- Appeals: merchant can read/write appeals related to their orders
create policy "Appeals merchant" on appeals
  for all using (
    exists (select 1 from orders where orders.id = appeals.order_id and orders.merchant_id = current_merchant_id())
  ) with check (
    order_id is null or exists (select 1 from orders where orders.id = order_id and orders.merchant_id = current_merchant_id())
  );

-- Notifications: user can read/write own notifications
create policy "Notifications user" on notifications
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Security logs: merchant can read; only service-role/Edge Functions insert
create policy "Security logs merchant read" on security_logs
  for select using (merchant_id = current_merchant_id());
-- No public insert policy — inserts only happen via the service-role key inside Edge Functions.
```

**Security note:** anonymous/public clients (widget, hosted page) never talk to the database directly for writes — every public write goes through an Edge Function using the **service role key**, which validates a `merchantSecret` first. RLS above governs *authenticated merchant dashboard/app* access, not the public checkout flow.

### 4.3 Edge Functions

SmartPay relies on several Supabase Edge Functions (TypeScript, Deno) that handle core business logic atomically. Deploy with the Supabase CLI: `supabase functions deploy <name>`.

> Functions called by anonymous clients or database webhooks must be deployed with `--no-verify-jwt`. Since they're reachable without a user JWT, **every function must independently validate its own credentials** (`merchantSecret` for merchant-authenticated calls, or a valid `page_id`/`slug` for public form calls).

#### `process-sms` (triggered by `INSERT` on `sms_logs`)

Matches an incoming SMS to a pending order using `cus_phone` + `amount` within the merchant's configured expiry window.

```typescript
// supabase/functions/process-sms/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  const payload = await req.json();
  const { record } = payload; // from database webhook

  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  );

  const { parsed_amount, parsed_sender, parsed_trx_id, parsed_timestamp, merchant_id, sms_hash, id: sms_log_id } = record;
  if (!parsed_amount || !parsed_sender) {
    return new Response(JSON.stringify({ status: "ignored" }), { status: 200 });
  }

  // 1. Duplicate check
  const { data: existingPayment } = await supabase
    .from("payments").select("id").eq("sms_hash", sms_hash).maybeSingle();
  if (existingPayment) {
    await supabase.from("sms_logs").update({ processed: true, status: "duplicate" }).eq("id", sms_log_id);
    return new Response(JSON.stringify({ status: "duplicate" }));
  }

  // 2. Find matching PENDING order within the merchant's expiry window
  const now = new Date().toISOString();
  const { data: matchedOrders } = await supabase
    .from("orders").select("*")
    .eq("merchant_id", merchant_id)
    .eq("amount", parsed_amount)
    .eq("cus_phone", parsed_sender)
    .eq("status", "PENDING")
    .gte("expires_at", now)
    .order("created_at", { ascending: true })
    .limit(1);

  if (!matchedOrders || matchedOrders.length === 0) {
    await supabase.from("payments").insert({
      merchant_id, amount: parsed_amount, sender_number: parsed_sender,
      sms_timestamp: parsed_timestamp, sms_hash, status: "UNMATCHED",
    });
    await supabase.from("sms_logs").update({ processed: true, status: "unmatched" }).eq("id", sms_log_id);
    return new Response(JSON.stringify({ status: "unmatched" }));
  }

  const order = matchedOrders[0];

  // 3. Atomic update: mark order PAID, insert payment, mark sms_log processed
  const { error } = await supabase.rpc("match_payment_atomic", {
    p_order_id: order.id,
    p_payment_amount: parsed_amount,
    p_sender_number: parsed_sender,
    p_trx_id: parsed_trx_id,
    p_sms_hash: sms_hash,
    p_sms_log_id: sms_log_id,
  });
  if (error) {
    console.error("match_payment_atomic error:", error);
    return new Response(JSON.stringify({ status: "error" }), { status: 500 });
  }

  // 4. Webhook + receipt (fire-and-forget, don't block the response)
  if (order.callback_url) await sendWebhook(order, supabase);
  await supabase.functions.invoke("generate-receipt", { body: { order_id: order.id } });

  return new Response(JSON.stringify({ status: "matched", order_id: order.id }));
});

async function sendWebhook(order: any, supabase: any) {
  const { data: merchant } = await supabase.from("merchants").select("webhook_secret").eq("id", order.merchant_id).single();
  const secret = merchant?.webhook_secret ?? "";
  const encoder = new TextEncoder();
  const payload = {
    tran_id: order.tran_id, order_id: order.id, status: "PAID",
    amount: order.amount, currency: order.currency ?? "BDT", paid_amount: order.amount,
    payment_method: order.payment_method ?? "unknown",
    sender_number: "******" + (order.cus_phone?.slice(-4) ?? ""),
    payment_time: new Date().toISOString(),
    cus_name: order.cus_name, cus_email: order.cus_email, cus_phone: order.cus_phone,
    product_name: order.product_name, metadata: order.metadata,
  };
  const body = JSON.stringify(payload);
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(body));
  const hexSig = Array.from(new Uint8Array(signature)).map((b) => b.toString(16).padStart(2, "0")).join("");

  // Retry with exponential backoff: 3 attempts, must return 2xx
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(order.callback_url, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Signature": hexSig },
        body,
      });
      if (res.ok) return;
    } catch (_e) { /* network error, fall through to retry */ }
    await new Promise((r) => setTimeout(r, 2 ** attempt * 1000));
  }
  console.error(`Webhook delivery failed after 3 attempts for order ${order.id}`);
}
```

Supporting Postgres function for the atomic update:

```sql
create or replace function match_payment_atomic(
  p_order_id uuid, p_payment_amount numeric, p_sender_number text,
  p_trx_id text, p_sms_hash text, p_sms_log_id uuid
) returns void language plpgsql as $$
begin
  update orders set
    status = 'PAID', paid_at = now(),
    sender_number = p_sender_number, matched_trx_id = p_trx_id
  where id = p_order_id and status = 'PENDING';

  insert into payments (merchant_id, amount, sender_number, sms_hash, status, matched_order_id)
  select merchant_id, p_payment_amount, p_sender_number, p_sms_hash, 'MATCHED', p_order_id
  from orders where id = p_order_id;

  update sms_logs set processed = true, status = 'matched' where id = p_sms_log_id;
end;
$$;
```

**Database Webhook setup** (Supabase Dashboard → Database → Webhooks): Name `process-sms`, Table `sms_logs`, Event `INSERT`, Type "Supabase Edge Function" → `process-sms`. Every SMS upload now automatically triggers matching.

#### `create-order` (public, merchant-secret authenticated)

```typescript
// supabase/functions/create-order/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  const body = await req.json();
  const { merchantSecret, tran_id, amount, cus_phone, cus_email, cus_name,
          product_name, callback_url, success_url, fail_url, cancel_url, metadata } = body;

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  const { data: merchant, error: mErr } = await supabase
    .from("merchants").select("id, match_window_minutes").eq("webhook_secret", merchantSecret).maybeSingle();
  // In production merchantSecret is a dedicated API key, not the webhook secret — see Section 10.3.
  if (mErr || !merchant) return new Response(JSON.stringify({ status: "ERROR", message: "Invalid merchantSecret" }), { status: 401 });

  if (!tran_id || !amount || !cus_phone) {
    return new Response(JSON.stringify({ status: "ERROR", message: "tran_id, amount, cus_phone are required" }), { status: 400 });
  }

  const { data: numberRow } = await supabase
    .from("merchant_numbers").select("number").eq("merchant_id", merchant.id).eq("active", true).eq("is_default", true).maybeSingle();

  const expiresAt = new Date(Date.now() + (merchant.match_window_minutes ?? 10) * 60_000).toISOString();

  const { data: order, error } = await supabase.from("orders").insert({
    merchant_id: merchant.id, tran_id, amount, cus_phone, cus_email, cus_name,
    product_name, callback_url, success_url, fail_url, cancel_url,
    metadata: metadata ?? {}, expires_at: expiresAt,
  }).select().single();

  if (error) return new Response(JSON.stringify({ status: "ERROR", message: error.message }), { status: 400 });

  return new Response(JSON.stringify({
    status: "SUCCESS", order_id: order.id,
    merchantNumber: numberRow?.number ?? null, expiresAt,
  }));
});
```

#### `match-payment-manual`, `resolve-appeal`, `extend-order`, `cancel-order`

These follow the same pattern — validate `merchantSecret`/merchant JWT, perform a single guarded `update`/`insert`, log to `security_logs`, return `{status, ...}`. Full request/response contracts are in [Section 8](#8-complete-api-reference); full source is in the SmartPay GitHub repository.

### 4.4 Storage for Appeals

```sql
insert into storage.buckets (id, name, public) values ('appeal-screenshots', 'appeal-screenshots', false);

create policy "Allow authenticated uploads"
on storage.objects for insert
with check (bucket_id = 'appeal-screenshots' and auth.role() = 'authenticated');

create policy "Allow anon uploads via signed upload token"
on storage.objects for insert
with check (bucket_id = 'appeal-screenshots' and auth.role() = 'anon');
-- Restrict further with a short-lived signed upload URL generated per-appeal by an Edge Function,
-- rather than allowing broad anon inserts, in production.
```

The checkout widget and hosted pages upload screenshots/documents using the Supabase JS SDK against a signed upload URL minted by an Edge Function (never the raw anon key with unrestricted bucket access).

### 4.5 Secrets & Environment Variables

| Variable | Where it lives | Used by |
|---|---|---|
| `SUPABASE_URL` | Edge Function env, Android app config, widget config | All |
| `SUPABASE_SERVICE_ROLE_KEY` | Edge Function env only — **never** shipped client-side | All Edge Functions |
| `SUPABASE_ANON_KEY` | Widget config, hosted page, Android app | Public API calls, Realtime subscriptions |
| Merchant API key (`merchantSecret`) | Supabase Vault, surfaced once at creation | `create-order`, `create-payment-page`, `extend-order`, `cancel-order` |
| `merchants.webhook_secret` | Database column (Vault-backed in enterprise mode) | HMAC signing of outbound webhooks |
| FCM server key | Edge Function env | Push notification dispatch |

---

## 5. SmartPay Merchant Android App

Written in Kotlin with Jetpack Compose, targeting a dedicated always-on device (a spare phone is common in practice).

### 5.1 SMS Detection Engine

- **Foreground Service** with a persistent, low-priority notification keeps the app alive under Android's background execution limits.
- **BroadcastReceiver** registered for `SMS_RECEIVED`.
- **Sender filtering** — only messages from sender IDs `bkash`, `NAGAD`, `upay` (case-insensitive) or `16216` (Rocket) are processed; everything else is ignored at the receiver level for battery/privacy.
- **Content keyword filter** — body must contain the appropriate "money received" phrase (`"received"`, `"Cash In"`, `"Money Received"`, `"Cash-In"`), filtering out balance alerts, OTPs, and promotions before regex parsing even runs.
- **Regex parsing** using the exact patterns in [Section 12](#12-appendix-sms-templates--regex-patterns) to extract amount, sender number, transaction ID, timestamp.
- **Deduplication** — SHA‑256 hash of `(sender + amount + trxID + timestamp)` checked against a local Room cache (24‑hour TTL) *and* the remote `sms_logs` table before upload, so re-delivered or re-scanned SMS never double count.
- **Local queue** — parsed SMS inserted into Room with status `PENDING`; app immediately attempts to insert into `sms_logs`. On failure (no internet), a `WorkManager` periodic task retries with backoff until successful.
- **Realtime dashboard** — the app subscribes to `orders`/`payments` changes via Supabase Realtime so the dashboard updates live without polling.

### 5.2 App Screens & Functionality

| Screen | Functionality |
|---|---|
| Onboarding | Email/Password or Google Sign-In via Supabase Auth; device registration; QR-code project linking; multi-merchant profile management. |
| Dashboard | Live revenue today, pending/successful/failed/unmatched counts, active device status (internet, SMS service, battery, sync), mini revenue chart. |
| Orders | List with filters (status, date, amount); detail view with timeline, extend-expiry, cancel actions. |
| Payments | Matched/unmatched list; manual matching dialog to link an unmatched payment to an order. |
| Appeals | Queue of pending appeals with approve/reject and "match to order" actions. |
| Merchant Numbers | Add/edit/remove numbers, enable/disable, set default. |
| Devices | All registered devices, health status, remote disable. |
| Notifications | In-app list + FCM push integration. |
| Customers | CRM view: phone, total payments, history, notes. |
| Hosted Pages *(new)* | Create/edit payment pages and their form fields, view submissions, copy hosted URL, view receipt template. |
| Reports | Revenue charts, CSV/PDF export. |
| Settings | Theme, language (English/Bangla), sync interval, biometric lock, session timeout, clear cache, about. |

### 5.3 Security Features

- `EncryptedSharedPreferences` for tokens and Supabase credentials.
- Room database encryption with SQLCipher.
- Play Integrity / root detection — blocks use on compromised devices.
- Biometric lock (fingerprint/face) required after configurable inactivity.
- `FLAG_SECURE` on sensitive screens (prevents screenshots of financial data).
- Clipboard clearing after copy operations (e.g. after copying a merchant number).
- JWT authentication with Supabase Auth for all API calls.
- Device binding — only registered devices can write to `sms_logs`, enforced by the RLS policy in [4.2](#42-row-level-security-policies).

---

## 6. Payment Gateway Integration

### 6.1 Embedded Widget

A single JavaScript file that renders a complete payment modal on the merchant's existing site.

```html
<script src="https://cdn.smartpay.com/widget.js"></script>
<script>
  window.SMARTPAY_CONFIG = {
    supabaseUrl: 'https://xxxxx.supabase.co',
    supabaseAnonKey: 'eyJh...'
  };
</script>
<div id="smartpay-button"></div>

<script>
  SmartPay.open({
    amount: 500,
    cus_phone: '01712345678',
    tran_id: 'INV-001',        // generated by your server
    order_id: 'abc-123',        // optional, if order was pre-created server-side
    merchantNumber: '017XXXXXXXX',
    onSuccess: (data) => window.location.href = '/thank-you',
    onFail: () => alert('Payment failed'),
    theme: { primaryColor: '#007bff', logo: '/logo.png' }
  });
</script>
```

| Parameter | Type | Description |
|---|---|---|
| `order_id` | string | Pre‑created order ID (if you called `create-order` server-side) |
| `amount` | number | Payment amount |
| `cus_phone` | string | Customer phone number |
| `tran_id` | string | Your unique transaction reference |
| `merchantNumber` | string | Override merchant number to display |
| `success_url` / `fail_url` / `cancel_url` | string | Redirect URLs |
| `onSuccess`, `onFail`, `onExpired`, `onCancel` | function | Callback hooks |
| `theme` | object | Custom colors, logo, font |

### 6.2 Headless API

Use the Edge Functions directly for full UI control. See [Section 8](#8-complete-api-reference) for the full contract of every endpoint, plus real-time subscription:

```javascript
const supabase = createClient(url, anonKey);
supabase.channel('order')
  .on('postgres_changes', { event: 'UPDATE', schema: 'public', table: 'orders', filter: `id=eq.${orderId}` },
      (payload) => console.log(payload.new.status))
  .subscribe();
```

### 6.3 Webhook Integration

When a payment is matched, `process-sms` sends a signed `POST` to `callback_url`, retrying up to 3 times with exponential backoff if the receiver doesn't return `2xx` (see [4.3](#43-edge-functions)).

```javascript
const crypto = require('crypto');
app.post('/webhook', express.json(), (req, res) => {
  const sig = req.headers['x-signature'];
  const body = JSON.stringify(req.body);
  const expected = crypto.createHmac('sha256', process.env.MERCHANT_SECRET).update(body).digest('hex');
  if (sig !== expected) return res.status(401).send('Invalid');
  const { tran_id, status } = req.body;
  // idempotent upsert against tran_id — the webhook may be delivered more than once
  res.json({ received: true });
});
```

**Reliability notes:** treat webhook delivery as *at-least-once*. Always key your handler's write on `tran_id` (idempotent upsert), because retries or duplicate deliveries can occur if your endpoint is briefly unavailable.

---

## 7. Hosted Payment Pages (No‑Code Checkout Forms)

A third integration mode alongside the Embedded Widget (6.1) and Headless API (6.2), delivered as **two platform-hosted, stateless frontend apps** that operate against each merchant's *own* Supabase project:

- **Payment Gateway Page** — one shared, public checkout/receipt renderer at e.g. `https://pay.smartpay.app/p/{merchant-subdomain}/{slug}`. It fetches page config and creates orders by calling `get-payment-page` / `submit-payment-form` on the **merchant's own Supabase project**, and subscribes to Realtime **on that same project** — never on any database you control.
- **Form Builder** — the admin UI a merchant uses to design a page (fields, layout, branding, redirects) and to connect their Supabase project. It writes the resulting `payment_pages` row directly into the merchant's own database — again, never into anything you host.

Payment detection and matching still run through the **same SMS engine** described in Sections 4–5, executing entirely inside the merchant's own Supabase project. Only the *collection layer* (the checkout form itself, and the tool used to design it) is provided by you as shared, reusable frontends.

### 7.1 Concept

A merchant creates a Payment Page from the Form Builder or via API, against their own project. A Payment Page bundles:

- **Product/order info** — name, description, price (fixed or customer-entered), product images.
- **A custom form** — the fields the merchant wants to collect from the customer before payment.
- **Branding** — merchant's own logo, primary color, optional custom subdomain/slug.
- **Redirect + receipt behavior** — where the customer lands after paying, and what the receipt looks like.

The merchant never hosts a checkout page or writes any frontend code; they design the page once in your Form Builder, and your shared Payment Gateway Page renders it — reading everything live from their own Supabase project.

### 7.1a Connecting Your Supabase Project

Before a merchant can use the Form Builder, they connect their own Supabase project:

1. Merchant creates a free/paid Supabase project in their own account (outside your platform entirely).
2. Merchant runs the SmartPay SQL bundle ([4.1](#41-database-schema--sql), [4.2](#42-row-level-security-policies), [7.6](#76-new-database-tables)) in their project's SQL Editor — either by hand, or via a **"Deploy to Supabase"** helper script your Form Builder provides that uses the Supabase Management API with a token the merchant grants for one-time setup only.
3. Merchant deploys the required Edge Functions ([4.3](#43-edge-functions), [7.7](#77-new-edge-functions)) into their project via `supabase functions deploy`, or a similar one-click deploy flow.
4. In the Form Builder, the merchant enters:
   - Their Supabase **Project URL**
   - Their Supabase **anon key** (safe to use client-side — this is what the public Payment Gateway Page will use to call their Edge Functions and subscribe to Realtime)
   - Their **merchantSecret/API key** (used only inside the Form Builder's authenticated session, to call `create-payment-page` — never shipped to the public checkout page)
5. The Form Builder stores this connection **only for the merchant's own convenience** — e.g. in their browser's local storage, or, if you want it to persist across devices/sessions, in a small `platform_connections` record scoped to their login on your side containing *only* the Supabase URL + anon key (never the service-role key or merchantSecret, which should never leave the merchant's own environment except at point of use). This is the one narrow exception to "you store nothing" — and even that is just a URL + a key that's already meant to be public-safe.
6. From this point on, every page the merchant designs and every payment page a customer visits reads and writes exclusively against the merchant's own Supabase project.

**Design implication:** the Payment Gateway Page must accept the target Supabase URL/anon key and page slug as part of the page identifier (e.g. via a merchant-specific subdomain or a `?project=` reference resolved at render time), so the same single deployed app can correctly serve thousands of different merchants' independent backends without ever mixing data between them.

### 7.2 Form Builder

Pages can be **single-page** (one scrollable form) or **multistep** (one question/group per screen with a progress bar), configurable per page.

| Field type | Description |
|---|---|
| `short_text` | Single-line text answer |
| `long_text` | Paragraph/textarea answer |
| `multiple_choice` | Single-select from a list of options |
| `checklist` | Multi-select checkboxes |
| `email` | Email input, format-validated |
| `phone` | Phone input; can be flagged as the payment-matching phone field |
| `file_upload` | Document/attachment upload (ID, proof, contract), stored in Supabase Storage |
| `image_choice` | Multiple choice where each option shows an image (e.g. size/color pick) |
| `image_display` | Non-input field that shows an image/diagram inline in the flow |
| `number` | Numeric input with optional min/max |
| `dropdown` | Single-select dropdown for long option lists |
| `custom` | Merchant-defined key/type pair for anything not covered above |

Each field has: `id`, `label`, `type`, `required`, `options` (for choice types), validation rules, and a `step`/order index. Fields flagged `payment_amount_source` or `payment_phone_source` feed directly into order creation — e.g. a "package tier" multiple-choice question can drive the amount charged, and a "your bKash number" phone field drives the SMS-matching phone.

### 7.3 API Key + Hosted URL Flow

**Step 1 — Create a Payment Page** (merchant, server-side, one-time or per-template):

```http
POST https://<supabase_url>/functions/v1/create-payment-page
Authorization: Bearer <anon_key>
Content-Type: application/json

{
  "merchantSecret": "sk_...",
  "title": "Pro Plan Subscription",
  "amount": 1500,
  "amount_mode": "field_driven",
  "currency": "BDT",
  "product_images": ["https://.../product1.jpg"],
  "logo_url": "https://.../merchant-logo.png",
  "theme": { "primaryColor": "#0b5fff" },
  "layout": "multistep",
  "fields": [
    { "id": "name", "type": "short_text", "label": "Full Name", "required": true, "step": 1 },
    { "id": "email", "type": "email", "label": "Email", "required": true, "step": 1 },
    { "id": "phone", "type": "phone", "label": "bKash Number", "required": true, "payment_phone_source": true, "step": 2 },
    { "id": "plan", "type": "multiple_choice", "label": "Choose a plan", "step": 2,
      "options": [{"label": "Monthly", "value": 1500}, {"label": "Yearly", "value": 15000}],
      "payment_amount_source": true },
    { "id": "id_doc", "type": "file_upload", "label": "Upload NID / Passport", "required": true, "step": 3 }
  ],
  "success_url": "https://yoursite.com/thank-you",
  "fail_url": "https://yoursite.com/payment-failed",
  "receipt": { "show_logo": true, "footer_note": "Thank you for your purchase." }
}
```

Response:

```json
{
  "status": "SUCCESS",
  "page_id": "pp_8f2a...",
  "hosted_url": "https://pay.smartpay.app/p/pro-plan-subscription-8f2a"
}
```

**Step 2 — Share or embed the `hosted_url`.** Redirect a "Buy Now" button straight to it, send it directly to a customer (invoice/DM/email/SMS), or generate a **one-time link per order** via `create-payment-page-instance` (same body, but tied to a specific `tran_id`) instead of one reusable page.

**Step 3 — Customer flow on the hosted page:**

1. Customer opens `hosted_url`, fills the multistep/single-page form (including any `image_choice`/`image_display` questions rendered inline), and submits.
2. SmartPay creates the underlying order (same `orders` table as 6.2), using the fields flagged `payment_phone_source`/`payment_amount_source`, and displays the merchant number + countdown, exactly like the embedded widget.
3. Customer pays via bKash/Nagad/Rocket/Upay as normal; the existing SMS-matching engine (Section 4.3, unchanged) detects and confirms the payment in real time.
4. On confirmation, the page shows a branded receipt (logo, order details, submitted answers, TrxID), then redirects to `success_url` after a short delay, or immediately if `redirect_immediately: true`.

### 7.4 Data Capture for the Merchant

Every submission — every form field answer, not just the payment fields — is stored and linked to the resulting order, giving the merchant a full CRM-style intake record, not just "payment received."

```http
GET https://<supabase_url>/functions/v1/get-form-submission?order_id=...
Authorization: Bearer <anon_key>
```

or directly via the Supabase client against `form_submissions` (RLS-scoped to the merchant).

### 7.5 Branded Receipt

Once `PAID` is reached, `generate-receipt` runs automatically and produces an HTML/PDF receipt with: merchant logo, business name, product name, amount, TrxID, payment method, timestamp, and the customer-submitted fields the merchant chose to include. The receipt is shown on the hosted success screen, emailed to the customer (if an email field was collected), and downloadable from the Android app / dashboard order detail view.

### 7.6 New Database Tables

```sql
create table payment_pages (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants not null,
  slug text unique not null,
  title text not null,
  amount numeric(12,2),
  amount_mode text not null default 'fixed' check (amount_mode in ('fixed','customer_choice','field_driven')),
  currency text default 'BDT',
  product_images jsonb default '[]',
  logo_url text,
  theme jsonb default '{}',
  layout text not null default 'single_page' check (layout in ('single_page','multistep')),
  fields jsonb not null default '[]',
  success_url text,
  fail_url text,
  cancel_url text,
  redirect_immediately boolean default false,
  receipt_config jsonb default '{}',
  active boolean default true,
  created_at timestamptz default now()
);

create table form_submissions (
  id uuid primary key default gen_random_uuid(),
  payment_page_id uuid references payment_pages not null,
  order_id uuid references orders,
  answers jsonb not null default '{}',
  created_at timestamptz default now()
);

create table page_files (
  id uuid primary key default gen_random_uuid(),
  submission_id uuid references form_submissions on delete cascade not null,
  field_id text not null,
  storage_path text not null,
  file_name text,
  content_type text,
  created_at timestamptz default now()
);

alter table payment_pages enable row level security;
alter table form_submissions enable row level security;
alter table page_files enable row level security;

create policy "Payment pages merchant access" on payment_pages
  for all using (merchant_id = current_merchant_id()) with check (merchant_id = current_merchant_id());

create policy "Form submissions merchant read" on form_submissions
  for select using (
    exists (select 1 from payment_pages where payment_pages.id = form_submissions.payment_page_id
            and payment_pages.merchant_id = current_merchant_id())
  );

create policy "Page files merchant read" on page_files
  for select using (
    exists (select 1 from form_submissions fs
            join payment_pages pp on pp.id = fs.payment_page_id
            where fs.id = page_files.submission_id and pp.merchant_id = current_merchant_id())
  );
```

Public inserts into `form_submissions`/`page_files` happen only via the `submit-payment-form` Edge Function (service-role key, validated against an active `payment_pages` row) — never directly from the browser.

### 7.7 New Edge Functions

| Function | Method | Description | Required Parameters |
|---|---|---|---|
| `create-payment-page` | POST | Create a reusable hosted payment page | `merchantSecret`, `title`, `fields`, `amount`/`amount_mode` |
| `create-payment-page-instance` | POST | Create a one-time link tied to a specific order/customer | `merchantSecret`, `page_id`, `tran_id`, optional `cus_*` |
| `get-payment-page` | GET | Public — fetch page config to render the hosted form | `slug` |
| `submit-payment-form` | POST | Public — submit form answers, creates the underlying order | `page_id`/`slug`, `answers`, uploaded file refs |
| `get-form-submission` | GET | Fetch a submission's answers (merchant-authenticated) | `order_id` or `submission_id` |
| `generate-receipt` | internal | Triggered on order → `PAID`, builds the branded receipt | `order_id` |

File uploads for `file_upload` fields go to a dedicated Storage bucket (`page-uploads`), scoped per submission, with signed URLs for merchant download — the same pattern already used for appeal screenshots ([4.4](#44-storage-for-appeals)).

---

## 8. Complete API Reference

All endpoints require `Authorization: Bearer <anon_key>`. Endpoints that mutate merchant data additionally require a valid `merchantSecret` in the body.

### `POST /functions/v1/create-order`
**Body:** `merchantSecret, tran_id, amount, cus_phone, cus_email?, cus_name?, product_name?, callback_url?, success_url?, fail_url?, cancel_url?, metadata?`
**Response 200:** `{"status":"SUCCESS","order_id":"...","merchantNumber":"...","expiresAt":"..."}`
**Response 400/401:** `{"status":"ERROR","message":"..."}`

### `POST /functions/v1/match-payment-manual`
**Body:** `merchantSecret, payment_id, order_id`
**Response 200:** `{"status":"SUCCESS","order_id":"...","payment_id":"..."}`

### `POST /functions/v1/resolve-appeal`
**Body:** `merchantSecret, appeal_id, action` (`APPROVE`/`REJECT`), `order_id?`
**Response 200:** `{"status":"SUCCESS","appeal_id":"...","new_status":"APPROVED"}`

### `POST /functions/v1/extend-order`
**Body:** `merchantSecret, order_id, new_expiry` (ISO string)
**Response 200:** `{"status":"SUCCESS","order_id":"...","expires_at":"..."}`

### `POST /functions/v1/cancel-order`
**Body:** `merchantSecret, order_id`
**Response 200:** `{"status":"SUCCESS","order_id":"...","status":"CANCELLED"}`

### `GET /functions/v1/get-dashboard-stats`
**Auth:** merchant JWT (auto-scoped)
**Response 200:** `{"revenue_today":..., "pending_orders":..., "unmatched_payments":..., "appeal_count":...}`

### `POST /functions/v1/create-payment-page` / `create-payment-page-instance`
See [7.3](#73-api-key--hosted-url-flow) for full body/response.

### `GET /functions/v1/get-payment-page?slug=...`
**Response 200:** full page config (`title`, `fields`, `theme`, `layout`, `amount`/`amount_mode`, `logo_url`, `product_images`) — used by the hosted-page renderer, no auth required (public).

### `POST /functions/v1/submit-payment-form`
**Body:** `page_id`/`slug`, `answers: { field_id: value }`, `uploaded_files?: { field_id: storage_path }`
**Response 200:** `{"status":"SUCCESS","order_id":"...","merchantNumber":"...","expiresAt":"..."}`

### `GET /functions/v1/get-form-submission?order_id=...`
**Auth:** merchant JWT
**Response 200:** `{"submission_id":"...","answers":{...},"files":[{"field_id":"...","url":"..."}]}`

### Database Tables (direct read/write via SDK, RLS-scoped)
`orders` – create, read, update status · `payments` – read, update · `appeals` – insert, update · `devices` – insert, update status · `merchant_numbers` – CRUD · `payment_pages` – CRUD · `form_submissions` – read.

---

## 9. Appeal System

When automatic matching fails (different phone number, delayed SMS, expired order), the customer can submit their Transaction ID.

**Flow:**

1. Order expires without a match → widget/hosted page shows an appeal form.
2. Customer enters TrxID and an optional note (and optionally a screenshot upload).
3. The form inserts a row into `appeals` (via an Edge Function, not a raw anon insert).
4. Merchant receives a push notification and sees the appeal in the Android app dashboard.
5. Merchant reviews the appeal:
   - **Approve** — selects the matching order; the order is marked `PAID`, the appeal `APPROVED`.
   - **Reject** — appeal marked `REJECTED`.
   - **Manual match** — if no order exists yet, merchant can create one or link an unmatched payment.
6. Every action is logged in `security_logs` for audit purposes.

---

## 10. Deployment, Operations & Environment Management

### 10.1 Environments
Run at least two Supabase projects — `staging` and `production` — with the Android app pointed at whichever project via the QR-linking flow in onboarding. Never test SMS parsing changes directly against production.

### 10.2 Deploying Edge Functions
```bash
supabase login
supabase link --project-ref <project-ref>
supabase functions deploy create-order --no-verify-jwt
supabase functions deploy process-sms --no-verify-jwt
supabase functions deploy submit-payment-form --no-verify-jwt
# repeat for each public function; merchant-authenticated dashboard-only functions can keep JWT verification on
```

### 10.3 Merchant API Keys
In production, don't reuse `webhook_secret` as the public `merchantSecret` (v2.0 examples conflated these for brevity). Instead:
- Generate a separate `api_keys` table (`id, merchant_id, key_hash, label, created_at, revoked_at`), store only a bcrypt/argon2 hash, and show the plaintext key once at creation — mirroring how Stripe/GitHub issue tokens.
- Every public Edge Function looks up `api_keys` by hash, not by comparing plaintext.
- Support multiple keys per merchant (e.g. one for the website, one for a mobile app) with independent revocation.

### 10.4 Backups & Disaster Recovery
- Enable Supabase's daily automated Postgres backups (or `pg_dump` via a scheduled Edge Function/cron to external storage for self-managed control).
- Export the `appeal-screenshots` and `page-uploads` Storage buckets on the same schedule.
- Document a restore runbook and test it at least quarterly.

### 10.5 Monitoring
- Watch Edge Function logs (Supabase Dashboard → Edge Functions → Logs) for `process-sms` error rates — a spike usually means a new SMS format from an MFS provider that the regex doesn't cover yet.
- Alert on `sms_logs` rows stuck `unprocessed` for more than a few minutes (indicates the database webhook isn't firing).
- Track `payments.status = 'UNMATCHED'` volume — a rising trend usually means expiry windows are too short or customers are paying from unregistered numbers.

---

## 11. Testing & Troubleshooting

**Testing:**
- Use a small real amount (e.g. 10 BDT) from a different phone to validate the full path end-to-end.
- Check `sms_logs` in Supabase to confirm the SMS was uploaded and processed.
- Verify webhook delivery in Edge Function logs.
- Simulate an SMS by inserting a row into `sms_logs` manually (with a unique `sms_hash`) to test matching logic without sending real money.
- For hosted pages: submit the form with each field type at least once, including a `file_upload`, to confirm Storage writes and `form_submissions` capture correctly.

**Common issues:**

| Symptom | Likely cause | Fix |
|---|---|---|
| Orders stuck `PENDING` | Android app has no internet / SMS permission revoked / phone in battery saver | Check device health screen; whitelist the app from battery optimization |
| SMS not parsed | MFS provider changed message wording | Compare the raw SMS body against the regex in Section 12; enable debug logs |
| Webhook not called | `callback_url` not publicly reachable or not returning 2xx | Confirm the URL is public; check for firewall/auth blocking Supabase's IPs |
| Duplicate payments | (should be prevented by `sms_hash` dedup) | Clear the local SMS cache if you see duplicates; check for hash-generation bugs after an app update |
| Hosted page shows "expired" instantly | `match_window_minutes` misconfigured or server/device clock drift | Verify `merchants.match_window_minutes` and that the phone's clock is NTP-synced |
| File upload fails on hosted page | Storage bucket policy too strict, or file exceeds size limit | Check `page-uploads` bucket policy and configured max file size |

---

## 12. Appendix: SMS Templates & Regex Patterns

**bKash Received (traditional):**
```
You have received Tk 317.00 from 01608637350. Fee Tk 0.00. Balance Tk 1,350.91. TrxID DG25WUQEH1 at 02/07/2026 15:00
```
Regex: `You have received Tk (\d+\.?\d*).*?from (\d+).*?TrxID (\w+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})`

**bKash Cash In (new format):**
```
Cash In Tk 1,020.00 from 01777787722 successful. Fee Tk 0.00. Balance Tk 1,033.91. TrxID DG27WSGI49 at 02/07/2026 13:55
```
Regex: `Cash In Tk ([\d,]+\.?\d*).*?from (\d+).*?TrxID (\w+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})`

**Nagad Money Received:**
```
Money Received.
Amount: Tk 1928.50
Sender: 01609499927
Ref: N/A
TxnID: 754MJGYV
Balance: Tk 2065.76
25/03/2026 16:56
```
Regex: `Money Received\..*?Amount:\s*Tk (\d+\.?\d*).*?Sender:\s*(\d+).*?TxnID:\s*(\w+).*?(\d{2}/\d{2}/\d{4} \d{2}:\d{2})`

**Rocket Cash‑In:**
```
Cash-In from A/C: ***332 Tk26,900.00 Fee: Tk.00, Your A/C Balance: Tk27,008.23.TxnId:6691775337 Date:01-JUL-26 04:11:32 pm. Download https://bit.ly/nexuspay
```
Regex: `Cash-In from A/C:\s*\*+\d+\s*Tk([\d,]+\.?\d*)[\s\S]*?TxnId:(\d+)\s+Date:(\d{2}-[A-Z]{3}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*[ap]m)`

**Upay Money Received (generic):**
Regex: `(?:Money Received|Received Taka)[\s\S]*?Taka\s*([\d,]+\.?\d*)[\s\S]*?from\s*(\d+)[\s\S]*?TrxID\s*(\w+)[\s\S]*?(\d{2}/\d{2}/\d{4} \d{2}:\d{2})`

All amounts are normalized (commas removed) and dates converted to UTC before storage.

---

## 13. Roadmap

Ideas not yet implemented, for future versions:
- Native SDKs for React Native / Flutter wrapping the hosted-page flow as an in-app WebView component.
- Recurring/subscription orders (repeat `create-order` on a schedule against a saved customer profile).
- Multi-language hosted pages (auto-detect or per-page locale for form labels/receipts).
- Partial refund / dispute tracking as a first-class order state.

---

**Support & Resources:** SmartPay GitHub (Edge Functions & widget source) · Community forum · Email support for enterprise customers.

*End of complete documentation.*