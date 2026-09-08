# SwapnoPay Enterprise Web Platform — Complete Master Project Documentation
### Full Functional Specification, System Architecture, Feature Catalog & Web Deployment Blueprint
**Version 4.0 (Enterprise Edition)**  
*Real-Time Automated Payment Gateway for bKash, Nagad, Rocket & Upay · No-Code Hosted Checkout Forms · Digital Khatabook CRM · Web Point-of-Sale (POS) · Inventory & Stock Control · Financial Analytics · AI Business Copilot · 100% Self-Hosted & Tenant-Isolated*

---

## Table of Contents
1. [Executive Summary & Value Proposition](#1-executive-summary--value-proposition)
2. [High-Level System Architecture](#2-high-level-system-architecture)
3. [Technology Stack for Full Web Application](#3-technology-stack-for-full-web-application)
4. [Master Feature Catalog (Detailed Functional Modules)](#4-master-feature-catalog-detailed-functional-modules)
   - [Module 1: Authentication, Onboarding & Security Suite](#module-1-authentication-onboarding--security-suite)
   - [Module 2: Merchant Command Center (Dashboard & Live Ops)](#module-2-merchant-command-center-dashboard--live-ops)
   - [Module 3: Order & Transaction Lifecycle Engine](#module-3-order--transaction-lifecycle-engine)
   - [Module 4: Real-Time SMS Gateway Ingestion & MFS Engine](#module-4-real-time-sms-gateway-ingestion--mfs-engine)
   - [Module 5: Payment Gateway Integration & Developer Portal](#module-5-payment-gateway-integration--developer-portal)
   - [Module 6: No-Code Hosted Form Builder Studio & Public Checkout](#module-6-no-code-hosted-form-builder-studio--public-checkout)
   - [Module 7: Customer & Supplier Ledgers (Digital Khatabook / Khata)](#module-7-customer--supplier-ledgers-digital-khatabook--khata)
   - [Module 8: Web Point-of-Sale (POS) & Sales Invoicing System](#module-8-web-point-of-sale-pos--sales-invoicing-system)
   - [Module 9: Inventory, Product Catalog & Barcode Management](#module-9-inventory-product-catalog--barcode-management)
   - [Module 10: Business Financials, Expense Tracker & Loan Manager](#module-10-business-financials-expense-tracker--loan-manager)
   - [Module 11: Enterprise Financial Reports & Analytics](#module-11-enterprise-financial-reports--analytics)
   - [Module 12: Customer Support & Dispute / Appeal Resolution Center](#module-12-customer-support--dispute--appeal-resolution-center)
   - [Module 13: Team Management & Role-Based Access Control (RBAC)](#module-13-team-management--role-based-access-control-rbac)
   - [Module 14: AI Business Copilot & Automated Intelligence](#module-14-ai-business-copilot--automated-intelligence)
   - [Module 15: Cloud Backup, Restore, Migration & System Settings](#module-15-cloud-backup-restore-migration--system-settings)
5. [Complete Master Database Schema (Supabase / PostgreSQL)](#5-complete-master-database-schema-supabase--postgresql)
6. [API Reference & Edge Functions Specification](#6-api-reference--edge-functions-specification)
7. [Web Application Architecture & Component Structure](#7-web-application-architecture--component-structure)
8. [Production Deployment & Operations Playbook](#8-production-deployment--operations-playbook)
9. [Troubleshooting & Disaster Recovery Protocol](#9-troubleshooting--disaster-recovery-protocol)

---

## 1. Executive Summary & Value Proposition

### 1.1 Product Overview
**SwapnoPay Enterprise** is a full-stack, real-time financial automation platform designed to empower merchants, digital businesses, and retail enterprises across Bangladesh and South Asia. It bridges the gap between conventional Mobile Financial Services (**bKash, Nagad, Rocket, Upay**) and modern enterprise web software without requiring expensive merchant aggregator contracts, transaction cuts, or third-party cloud lock-in.

### 1.2 Core Business Value
| Pillar | Traditional Payment Gateways | SwapnoPay Enterprise Platform |
|---|---|---|
| **Backend Ownership** | Third-party cloud server with centralized control | **100% Self-Hosted & Tenant-Isolated** inside merchant's own Supabase project |
| **Transaction Fees** | 1.5% – 3.5% per transaction + monthly fees | **0.00% Transaction Fee** — One-time setup, zero recurring server cuts |
| **Account Compatibility** | Restricted to registered Merchant accounts | **Works with Personal, Agent, and Merchant SIM accounts** |
| **Verification Speed** | Manual verification or delayed batch webhooks | **Instant Real-Time Matching (< 1.5s)** via SMS Webhooks & WebSockets |
| **Dispute Handling** | Protracted manual email support | **Automated Customer Appeal Portal** with visual screenshot audit |
| **Business Ecosystem** | Only payment checkout | **Complete All-in-One ERP**: Khata CRM + POS Billing + Inventory + Expenses + Loans + AI Copilot |

---

## 2. High-Level System Architecture

The SwapnoPay Web Platform employs a decoupled, stateless frontend architecture coupled with a dedicated Supabase infrastructure per merchant.

```
+---------------------------------------------------------------------------------------------------+
|                                  SwapnoPay Web Platform (Frontend UI)                             |
|                                                                                                   |
|   +--------------------------+  +--------------------------+  +-------------------------------+   |
|   |  Merchant Admin Portal   |  |   No-Code Form Studio    |  |  Public Hosted Checkout Page  |   |
|   |  - Dashboard & Analytics |  |   - Visual Drag-and-Drop |  |  - Real-time Timer & bKash/   |   |
|   |  - Orders & Transactions |  |   - Template Library     |  |    Nagad/Rocket/Upay Modal    |   |
|   |  - Khatabook & POS Web   |  |   - Response Analyzer    |  |  - Instant PDF Receipt Engine |   |
|   |  - Inventory & Expenses  |  |                          |  |  - Customer Dispute Appeal    |   |
|   +-------------+------------+  +-------------+------------+  +---------------+---------------+   |
+-----------------|-----------------------------|-------------------------------|-------------------+
                  |                             |                               |
                  +-----------------------------+-------------------------------+
                                                |
                              HTTPS REST (PostgREST) + WSS (Realtime)
                                                |
                                                v
+---------------------------------------------------------------------------------------------------+
|                                  Merchant's Supabase Project                                      |
|                                                                                                   |
|   +-------------------------------------------------------------------------------------------+   |
|   |                                     PostgreSQL 15+ Engine                                 |   |
|   |  - 24 Isolated Relational Tables (merchants, orders, payments, sms_logs, customers,       |   |
|   |    suppliers, pos_sales, products, product_variants, expenses, loans, payment_forms...)   |   |
|   |  - Row Level Security (RLS) enforcing strict multi-tenant data boundaries                  |   |
|   |  - Atomic matching stored procedures: match_payment_atomic(), match_payment_manual()      |   |
|   +-------------------------------------------------------------------------------------------+   |
|                                                ^                                                  |
|                                                | Database Triggers                                |
|                                                v                                                  |
|   +-------------------------------------------------------------------------------------------+   |
|   |                                   Deno Edge Functions (Serverless)                        |   |
|   |   create-order   ·   process-sms   ·   hosted-form   ·   resolve-appeal   ·   provision   |   |
|   +-------------------------------------------------------------------------------------------+   |
|                                                ^                                                  |
|                                                | Webhook Ingestion                                |
+------------------------------------------------|--------------------------------------------------+
                                                 |
                                     Signed HTTPS POST (HMAC-SHA256)
                                                 |
+------------------------------------------------+--------------------------------------------------+
|                            Android Gateway Daemon / SMS Listener Sim Box                          |
|   - Foreground Android Service capturing mobile banking SMS from bKash, Nagad, Rocket, Upay      |
|   - Local SQLite queue + automatic network retry loop with SHA-256 duplicate hashing              |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. Technology Stack for Full Web Application

### 3.1 Web Frontend Application
- **Framework**: **Next.js 14** (App Router, Server Components & Client Interactive Modules) / **React 18** with **TypeScript**.
- **State Management & Data Fetching**: **TanStack Query v5 (React Query)** for server state caching + **Zustand** for local client UI states.
- **Styling & Design System**: Modern Enterprise Dark/Light Theme with **Tailwind CSS**, Glassmorphism acrylic surfaces, CSS Variables, and Lucide React Icons.
- **Real-Time Data Streaming**: **@supabase/supabase-js** Realtime WebSocket channels (`postgres_changes` on `orders`, `payments`, `sms_logs`, `form_submissions`).
- **Data Visualization**: **Recharts** & **Chart.js** (Area revenue graphs, donut payment distributions, bar expense charts).
- **POS Scanner & Hardware**: **Html5Qrcode** for camera barcode/QR scanning + Web USB / Bluetooth for 58mm/80mm ESC/POS Thermal Receipt Printers.
- **Document Generation**: **jspdf** and **html2canvas** for pixel-perfect PDF Tax Invoices, Receipts, and Financial Statements.

### 3.2 Backend & Data Storage
- **Database Engine**: **PostgreSQL 15+** via Supabase with `pgcrypto` and `pg_cron` extensions.
- **Authentication**: **Supabase Auth** (Email + Password, Magic Link, OAuth 2.0, Deep Link Handlers).
- **Serverless Compute**: **Deno Edge Functions** deployed directly onto Supabase globally distributed edge nodes.
- **Blob Storage**: **Supabase Storage** with private/public buckets for Form File Uploads, KYC NID Proofs, Expense Receipts, and Product Images.

---

## 4. Master Feature Catalog (Detailed Functional Modules)

### Module 1: Authentication, Onboarding & Security Suite
1. **Multi-Method Merchant Authentication**:
   - Email & Password login with automated verification email delivery.
   - One-Click Magic Link login (Passwordless instant access).
   - Password reset workflow with secure one-time token expiration.
   - Supabase deep link integration for seamless authentication across web and mobile.
2. **Instant 1-Click Supabase Provisioning (OAuth 2.0)**:
   - Connects directly to merchant's Supabase account via Management API OAuth.
   - Automatically executes database migrations (Tables, Functions, Triggers, RLS, Indexes).
   - Generates project API keys and populates edge function environment secrets.
3. **Manual Connection Wizard & Secure Profile Switcher**:
   - Enter Supabase Project URL and Public Anon Key.
   - "Secure Proxy Mode" ensures service-role master keys are never exposed in browser runtime.
   - Multi-Account Switcher: Manage unlimited stores/businesses from a single web console.
4. **Enterprise Web Lock & Session Security**:
   - PIN Screen Lock & WebAuthn Biometric passkey integration for browser security.
   - Auto-lock timer with configurable inactivity timeout (1 min, 5 min, 15 min, Never).
   - Security Audit Log (`security_logs`) recording IP address, browser agent, timestamp, and state modifications.
5. **System Announcements & Dynamic Alerts**:
   - Real-time broadcast banner for system maintenance, security advisories, and feature updates.

---

### Module 2: Merchant Command Center (Dashboard & Live Ops)
1. **Real-Time KPI Metric Cards**:
   - **Today's Gross Revenue** (Calculated from matched orders + POS cash sales).
   - **Pending Orders Counter** (Orders currently awaiting customer payment).
   - **Unmatched SMS Queue** (Incoming payments requiring manual or automatic link).
   - **Active Dispute Appeals** (Customer payment claims awaiting merchant resolution).
   - **Net Profit & Cash Position** (Real-time income minus logged expenses).
   - **Low Stock Alerts** (Products below minimum inventory reorder threshold).
2. **Interactive Financial Trend Visualizer**:
   - Dynamic Area & Bar charts with custom interval toggles: **Today**, **Last 7 Days**, **Last 30 Days**, **This Month**, **Year to Date**, **Custom Date Range**.
   - Breakdown of sales volume vs transaction count.
3. **Quick Action Control Center**:
   - Create Instant Payment Order.
   - Launch Point-of-Sale (POS) Web Terminal.
   - Record Customer Credit / Debit Entry.
   - Open QR Stock-In Scanner.
   - Create New Hosted Checkout Form.
   - Test Gateway Payment Simulator.
4. **Live Activity Stream**:
   - Real-time WebSocket table displaying order completions, incoming SMS transactions, and customer submissions as they occur without refreshing.
5. **Backend & Device Health Monitor**:
   - Supabase database connection latency indicator.
   - Connected Android SMS listener phone status (Battery level, Wi-Fi/Cellular online status, Last Heartbeat sync timestamp).

---

### Module 3: Order & Transaction Lifecycle Engine
1. **Comprehensive Orders Data Grid**:
   - Filter by Status (`PENDING`, `PAID`, `EXPIRED`, `CANCELLED`).
   - Filter by Payment Method (`bKash`, `Nagad`, `Rocket`, `Upay`).
   - Search by Order ID, Transaction ID (`trx_id`), Customer Phone, or Customer Name.
   - Column sorting by creation timestamp, amount, and expiry time.
2. **Order Detail Inspection Drawer**:
   - Complete lifecycle timeline: Created -> SMS Received -> Matched -> Webhook Dispatched.
   - Raw SMS payload association with matching confidence score.
   - Customer information (Phone, Email, Address, Product description).
   - Manual status override with audit logging.
3. **Manual Payment Matching Resolver**:
   - Resolves unmatched payments (e.g. customer typed wrong phone number or payment arrived after order expiration).
   - Side-by-side matching tool: Select pending/expired order and link directly to unmatched transaction with atomic SQL resolution.
4. **Order Lifetime & Expiry Controls**:
   - Extend expiration timer (adds +10, +30, or +60 minutes to pending orders).
   - Immediate cancellation with webhook event notification.
5. **Tax Invoice & Digital Receipt Generator**:
   - Instant branded PDF generation with merchant logo, business registration info, QR code payment proof, and itemized breakdown.
   - Thermal print view formatted for 58mm and 80mm POS receipt printers.
   - Direct WhatsApp / SMS receipt link sharing.

---

### Module 4: Real-Time SMS Gateway Ingestion & MFS Engine
1. **Multi-Provider SMS Extraction**:
   - **bKash**: Personal & Merchant Cash In/Send Money/Payment formats.
   - **Nagad**: Personal & Merchant Cash In/Send Money formats.
   - **Rocket**: 12-digit account transaction logs.
   - **Upay**: MFS transaction alerts.
2. **Dynamic Regex Management System**:
   - In-app regular expression editor allowing custom SMS pattern creation and testing.
   - Toggle individual pattern active/inactive status without altering code.
   - Live SMS parser tester: Paste sample SMS text to verify instant extraction of Amount, TrxID, Sender, and Timestamp.
3. **Cryptographic Duplicate Protection**:
   - Every raw SMS is hashed using SHA-256 (`sms_hash`).
   - Database unique constraint prevents duplicate processing or double-crediting if an SMS is re-transmitted.
4. **SMS Audit Log Explorer**:
   - Detailed table of all captured messages with parsed fields vs raw body.
   - Error status indicators (`unprocessed`, `matched`, `unmatched`, `duplicate`).
   - Re-parse and Re-trigger action button for failed or adjusted regexes.
5. **Device Management & Remote Control**:
   - Register unlimited SMS listening Android phones.
   - Monitor phone health (Battery %, network state, sync lag).
   - Remote deactivate toggle to stop listening on decommissioned SIMs.

---

### Module 5: Payment Gateway Integration & Developer Portal
1. **Three Native Integration Modes**:
   - **Drop-In Web Checkout Widget**: Single-line JavaScript snippet (`<script src=".../widget.js"></script>`) that renders an overlay checkout modal.
   - **Headless REST API**: Full programmatic control for custom frontends (Next.js, WooCommerce, Shopify, Laravel, Flutter, React Native).
   - **Platform-Hosted Checkout Pages**: Zero-code shareable payment links.
2. **Interactive API Console & Documentation**:
   - Live endpoint tester with interactive request body builder.
   - Ready-to-copy code snippets in **cURL**, **Node.js (TypeScript)**, **PHP**, **Python**, and **Flutter**.
   - Downloadable Postman collection.
3. **Webhook Dispatcher & HMAC-SHA256 Signature Verification**:
   - Configurable Webhook URL endpoint for real-time order status updates.
   - Cryptographic `X-SwapnoPay-Signature` header computed with merchant's private webhook secret.
   - Automatic retry mechanism with exponential backoff for failed webhook deliveries.
   - Webhook delivery history inspector with HTTP response code logs and payload replays.
4. **DDoS, Rate Limiting & Fraud Prevention**:
   - Client fingerprint hashing and IP rate limiting on public order creation endpoints.
   - Configurable transaction amount floor and ceiling limits.

---

### Module 6: No-Code Hosted Form Builder Studio & Public Checkout
1. **Visual Drag-and-Drop Form Canvas**:
   - 4 Dedicated Management Tabs: **Builder**, **Settings**, **Integrations**, **Responses**.
   - Interactive canvas with real-time mobile and desktop live preview.
2. **Comprehensive Field Block Library**:
   - **Basic Fields**: Single-line Text, Multi-line Paragraph, Number, Phone Number, Email.
   - **Choice Fields**: Dropdown Select, Radio Single Choice, Multi-select Checkboxes.
   - **Media & Files**: File Upload (Documents, Images, PDF proofs uploaded to Supabase Storage).
   - **E-Commerce & Billing**: Product Catalog Item Selector with thumbnail, Price Calculator, Quantity Counter, Variant Pickers (Size, Color, Model).
3. **Pre-Built Form Template Library**:
   - *Single Product E-Commerce Checkout*.
   - *Multi-Item Product Order Form*.
   - *Event Registration & Ticket Booking*.
   - *Course Enrollment & Digital Asset Purchase*.
   - *Donation & Crowdfunding Collection*.
   - *Custom B2B Lead Intake*.
4. **AI Form Generator**:
   - Prompt-based AI builder: Describe your business form in plain English or Bangla to generate complete forms with fields, validation, and pricing logic in seconds.
5. **Theme & Branding Customizer**:
   - Custom business logo & banner upload.
   - Palette color picker (Brand primary, canvas background, text color).
   - Dark/Light mode default settings.
   - Custom CSS injection support for white-label styling.
6. **Form Settings & Workflow Automation**:
   - Custom URL slug generation (`https://yourdomain.com/f/summer-sale-2026`).
   - Stock limits per product with auto-sold-out states.
   - Payment requirement toggle (Require full payment, partial deposit, or Cash on Delivery).
   - Custom success redirect URL and automated email confirmation triggers.
7. **Public Responsive Checkout Renderer (`/f/[slug]`)**:
   - High-converting, mobile-optimized checkout experience.
   - Interactive payment selection (bKash, Nagad, Rocket, Upay).
   - Live countdown timer with real-time Supabase Realtime payment completion detection.
   - Instant downloadable digital receipt upon payment match.
8. **Submissions & Response Analytics**:
   - Full data table of all form submissions with custom answer columns.
   - Direct link between form submission and matched payment transaction.
   - Export submissions to CSV / Excel spreadsheet.
   - Form conversion rate, views count, and total collected revenue analytics.

---

### Module 7: Customer & Supplier Ledgers (Digital Khatabook / Khata)
1. **Customer Ledger (Baki Khata / Debtor CRM)**:
   - Customer directory with search, phone, email, and address.
   - Customer status classification: **VIP**, **Risk**, **Inactive**, **Potential**.
   - Live balance tracker (Total Due, Total Paid, Opening Balance).
   - Detailed transaction log per customer with date, invoice number, and notes.
2. **Supplier Ledger (Paona Khata / Creditor CRM)**:
   - Supplier profiles with procurement records.
   - Total outstanding payables balance.
   - Supplier payment history with payment method tracking.
3. **Transaction Entry Creator**:
   - Record **Credit (Baki / Sale)** or **Payment (Joma / Received)**.
   - Voice note entry support (transcription of voice recording to transaction amount and note).
   - Attachment upload: Take photo of physical paper memo or cash receipt.
   - Link transaction to specific POS products or invoice numbers.
4. **Automated Due Collection Reminders ("Tagada")**:
   - One-click generation of professional SMS & WhatsApp payment reminder messages in Bangla and English.
   - Dynamic inclusion of customer name, total due amount, merchant business name, and instant bKash payment link.
   - Tracking of last "Tagada" sent date to prevent duplicate spamming.
5. **Customer Account Statement PDF Generator**:
   - Generates formal, branded PDF Statements of Account.
   - Itemized debit/credit table, running balance calculation, company stamp placeholder, and customer signature line.

---

### Module 8: Web Point-of-Sale (POS) & Sales Invoicing System
1. **High-Speed Web POS Terminal**:
   - Visual category grid (All, Food, Clothing, Electronics, Groceries, Custom).
   - Instant product search by name, SKU code, or scanned barcode.
   - One-tap add to cart with instant quantity and price calculation.
2. **Integrated Barcode & QR Code Scanner**:
   - Uses web camera (via `Html5Qrcode`) or standard USB/Bluetooth hardware barcode guns.
   - Scans product barcodes or variant QR codes to instantly add items to active cart.
3. **Cart & Discount Management**:
   - Per-item discount or overall cart discount (Fixed BDT or Percentage %).
   - Tax / VAT calculation toggle.
   - Customer assignment: Select existing Khatabook customer or checkout as "Walk-in Customer".
4. **Split-Payment & Multi-Method Checkout**:
   - Pay via **Cash** (with automated Change Due calculator).
   - Pay via **bKash / Nagad / Rocket** (generates real-time QR code on POS screen).
   - Pay via **Credit Card / Bank Transfer**.
   - Add to **Customer Due (Baki)** — instantly updates customer's Khatabook balance.
5. **POS Invoice Archive & Thermal Printer Support**:
   - Complete sales history searchable by invoice number, date range, or cashier.
   - Print direct to 58mm/80mm ESC/POS Thermal Printers without print dialog headers.
   - Sales refund & void workflow with automatic inventory restock.

---

### Module 9: Inventory, Product Catalog & Barcode Management
1. **Master Product Catalog**:
   - Product Name, SKU Code, Category, Unit (pcs, kg, ltr, box, meter).
   - Purchase / Cost Price vs Sale / Asking Price with profit margin calculation.
   - Current Stock Level with Low Stock Threshold Indicator.
   - Product Image upload and storage.
2. **Product Variants Architecture**:
   - Create multi-attribute variants (e.g. Size: M, L, XL; Color: Black, Blue, Red).
   - Unique QR Code / Barcode per variant.
   - Independent cost price, sale price, and stock quantity per variant.
   - Association with specific suppliers for re-ordering.
3. **Stock Movement & Inventory Adjustments**:
   - Log **Stock In (Purchase)** and **Stock Out (Damage/Adjustment)**.
   - Reference notes and supplier linkage for full inventory traceability.
4. **Barcode & QR Label Sheet Generator**:
   - Generate printable PDF sheets of product barcodes and QR stickers formatted for standard label printers (e.g. A4 24-up or roll labels).
   - Includes business name, product title, SKU, variant, price, and scannable code.

---

### Module 10: Business Financials, Expense Tracker & Loan Manager
1. **Categorized Expense Tracker**:
   - Categories: **Utilities**, **Rent**, **Transport**, **Operating**, **Payroll**, **Inventory Purchase**, **Marketing**, **Others**.
   - Payment method recording (Cash, bKash, Bank Transfer).
   - Digital receipt photo attachment stored in cloud storage.
2. **Recurring Expense Scheduler**:
   - Setup recurring monthly expenses (e.g. Shop Rent, Internet bill, Staff wages) with automatic reminder triggers.
3. **Business Loan & Microfinance Ledger**:
   - Record business loans (Bank loans, NGO microfinance, Personal loans).
   - Principal amount, interest rate (%), loan duration in months.
   - Choice of interest calculation: **Flat Interest** or **Reducing Balance**.
   - Automated Equated Monthly Installment (EMI) calculation.
   - Installment repayment tracker with remaining principal and total interest paid.

---

### Module 11: Enterprise Financial Reports & Analytics
1. **Executive Financial Summary**:
   - Gross Sales Revenue.
   - Net Profit (Sales Revenue - Cost of Goods Sold - Operating Expenses).
   - Total Outstanding Customer Dues (Receivables).
   - Total Supplier Payables (Liabilities).
   - Cash In Hand vs Bank / MFS balances.
2. **Comprehensive Date Filtering**:
   - Today, Yesterday, Last 7 Days, Last 30 Days, This Month, Last Month, This Year, All Time, or Custom Range.
3. **Interactive Visual Analytics**:
   - Revenue vs Expense Comparison Chart.
   - Payment Method Share Breakdown (bKash vs Nagad vs Rocket vs Upay vs Cash vs Due).
   - Top Selling Products & High-Margin Categories.
   - Customer Due Ageing Analysis.
4. **Export & Reporting Hub**:
   - Export Executive Financial Report to branded PDF.
   - Export raw transactions, orders, and sales data to CSV / Excel spreadsheet.

---

### Module 12: Customer Support & Dispute / Appeal Resolution Center
1. **Self-Service Customer Dispute Portal**:
   - Customers with unmatched payments submit transaction ID, sender phone number, order reference, note, and screenshot proof.
2. **Merchant Dispute Triage Dashboard**:
   - Displays all `PENDING_REVIEW` appeals.
   - Side-by-side verification: Compares customer's claimed TrxID with database `sms_logs` and `payments` tables.
   - Screenshot viewer with zoom inspection.
3. **One-Click Appeal Actions**:
   - **Approve & Match**: Flips order status to `PAID`, marks payment as matched, and fires webhook.
   - **Reject**: Marks appeal as rejected with custom customer feedback note.
4. **Audit Trail**:
   - Records reviewer user ID, resolution timestamp, and decision note.

---

### Module 13: Team Management & Role-Based Access Control (RBAC)
1. **Staff & Employee Directory**:
   - Employee name, designation, department, email, phone number, and status (Active/Inactive).
2. **6 Pre-Configured Role Tiers with Granular Permissions**:
   - **Owner / Super Admin**: Unrestricted master access, database settings, API secrets, billing.
   - **Store Manager / Admin**: Daily operations, product catalog, orders, stock adjustments, financial views.
   - **Finance & Accounts**: Ledgers, expenses, invoices, loan tracking, financial report exports.
   - **POS Cashier / Sales Operator**: POS terminal, order creation, walk-in receipts, basic dues.
   - **Inventory & Warehouse**: Stock In/Out, barcode scanning, product catalog, supplier shipments.
   - **Support / Read-Only**: View orders, customer CRM, verify payment status; no delete or financial modification capabilities.
3. **Granular Feature Access Matrix**:
   - Permissions stored as JSON in `employees.permissions_json` controlling access per screen and action.

---

### Module 14: AI Business Copilot & Automated Intelligence
1. **Natural Language Financial Assistant**:
   - Ask complex business questions in conversational English or Bangla:
     - *"What was my net profit last week?"*
     - *"Which customers owe more than 10,000 BDT for over 30 days?"*
     - *"Which products have less than 5 items in stock?"*
     - *"Draft a polite reminder message for Rahim Enterprise."*
2. **Automated Business Health & Anomaly Detection**:
   - Identifies sudden drop in daily revenue or unexpected spike in operating expenses.
   - Flags suspicious repeated duplicate SMS submissions.
3. **Smart Debt Recovery Generator**:
   - Analyzes customer purchase frequency and creates customized payment reminder templates with direct payment links.

---

### Module 15: Cloud Backup, Restore, Migration & System Settings
1. **Complete Database Snapshot & Cloud Backup**:
   - 1-Click encrypted JSON export of entire business database (Orders, Customers, Ledgers, Products, Sales, Expenses).
   - Restore point creation and verification tool.
2. **Localization & Language Switcher**:
   - Full bilingual interface: **English** and **Bangla (বাংলা)** with instantaneous runtime toggle.
3. **UI Themes**:
   - Modern Dark Slate, Crisp Light Enterprise, and Midnight Gold aesthetic themes.
4. **Business Profile & KYC Management**:
   - Business Legal Name, Trade License Number, Tax Identification Number (TIN), Logo, Official Phone, Website.
   - Primary Bank Account info (Bank Name, Branch, Account Holder, Routing Number).

---

## 5. Complete Master Database Schema (Supabase / PostgreSQL)

Below is the consolidated, production-hardened PostgreSQL schema encompassing all 24 relational tables, extensions, RLS policies, indexes, and stored procedures.

```sql
-- ============================================================================
-- SWAPNOPAY MASTER DATABASE SCHEMA — CONSOLIDATED ENTERPRISE EDITION
-- ============================================================================

-- 0. Enable Cryptographic Extensions & Helper Functions
create extension if not exists "pgcrypto";

create or replace function current_merchant_id()
returns uuid language sql stable security definer set search_path = public as $$
  select id from merchants where user_id = auth.uid() limit 1;
$$;

-- 1. Merchants Table
create table if not exists merchants (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users on delete cascade,
  business_name text not null default 'My Business',
  email text,
  phone text,
  business_type text default 'Retail',
  website text,
  default_number text,
  webhook_secret text default encode(gen_random_bytes(32), 'hex'),
  created_at timestamptz default now()
);
create unique index if not exists merchants_user_id_unique on merchants(user_id);

-- Auto-provision merchant profile upon Supabase auth sign-up
create or replace function handle_new_merchant_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into merchants (user_id, business_name, email, phone)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'business_name', 'My Business'),
    new.email,
    nullif(new.raw_user_meta_data ->> 'phone', '')
  )
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_create_merchant on auth.users;
create trigger on_auth_user_create_merchant after insert on auth.users
for each row execute function handle_new_merchant_user();

-- 2. Merchant Payment Numbers
create table if not exists merchant_numbers (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  number text not null,
  type text not null check (type in ('bKash','Nagad','Rocket','Upay')),
  account_type text default 'Personal' check (account_type in ('Personal','Merchant','Agent')),
  is_default boolean default false,
  active boolean default true,
  created_at timestamptz default now()
);
create index if not exists idx_merchant_numbers_lookup on merchant_numbers(merchant_id, active);

-- 3. Orders Table
create table if not exists orders (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  tran_id text unique not null,
  amount numeric(12,2) not null,
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
create index if not exists idx_orders_matching on orders(merchant_id, status, cus_phone, amount, expires_at);

-- 4. Payments Table
create table if not exists payments (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  trx_id text,
  amount numeric(12,2) not null,
  sender_number text,
  merchant_number text,
  sms_timestamp timestamptz,
  sms_hash text unique not null,
  status text not null default 'UNMATCHED' check (status in ('MATCHED','UNMATCHED','DUPLICATE')),
  matched_order_id uuid references orders on delete set null,
  created_at timestamptz default now()
);
create index if not exists idx_payments_lookup on payments(merchant_id, status, trx_id);

-- 5. SMS Logs Table
create table if not exists sms_logs (
  id uuid primary key default gen_random_uuid(),
  device_id uuid,
  merchant_id uuid references merchants on delete cascade not null,
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
create index if not exists idx_sms_logs_hash on sms_logs(sms_hash);

-- 6. Devices Table (SMS Listening Nodes)
create table if not exists devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users on delete cascade,
  merchant_id uuid references merchants on delete cascade,
  fcm_token text,
  device_model text,
  os_version text,
  battery_level integer default 100,
  online boolean default true,
  last_sync timestamptz default now(),
  disabled boolean default false,
  created_at timestamptz default now()
);

-- 7. Appeals Table (Dispute Resolution)
create table if not exists appeals (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  trx_id text not null,
  cus_phone text,
  order_id uuid references orders on delete set null,
  note text,
  screenshot_url text,
  status text default 'PENDING_REVIEW' check (status in ('PENDING_REVIEW','APPROVED','REJECTED')),
  resolved_by uuid references auth.users on delete set null,
  resolved_at timestamptz,
  created_at timestamptz default now()
);

-- 8. Notifications Table
create table if not exists notifications (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  user_id uuid references auth.users on delete cascade,
  type text not null,
  title text not null,
  body text not null,
  read boolean default false,
  created_at timestamptz default now()
);

-- 9. Security Logs & Rate Limits
create table if not exists security_logs (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade,
  user_id uuid references auth.users on delete set null,
  event text not null,
  details jsonb default '{}',
  ip_address text,
  created_at timestamptz default now()
);

create table if not exists order_rate_limits (
  id bigint generated always as identity primary key,
  merchant_id uuid references merchants on delete cascade not null,
  client_hash text not null,
  created_at timestamptz default now()
);

-- 10. MFS Regex Patterns Table
create table if not exists mfs_regex_patterns (
  id uuid primary key default gen_random_uuid(),
  mfs_name text not null check (mfs_name in ('bKash','Nagad','Rocket','Upay')),
  pattern_name text not null,
  regex_pattern text not null,
  active boolean default true,
  created_at timestamptz default now()
);

-- 11. Payment Forms Table (Hosted Form Builder)
create table if not exists payment_forms (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  title text not null default 'Untitled Payment Form',
  description text,
  slug text unique not null default encode(gen_random_bytes(6), 'hex'),
  template_type text default 'SINGLE_PRODUCT',
  fields jsonb not null default '[]'::jsonb,
  products jsonb not null default '[]'::jsonb,
  theme jsonb not null default '{"primaryColor": "#F59E0B", "isDark": true}'::jsonb,
  status text not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  views_count integer default 0,
  submissions_count integer default 0,
  total_revenue numeric(12,2) default 0.00,
  logo_url text,
  banner_url text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);
create index if not exists idx_payment_forms_slug on payment_forms(slug);

-- 12. Form Submissions Table
create table if not exists form_submissions (
  id uuid primary key default gen_random_uuid(),
  form_id uuid references payment_forms on delete cascade not null,
  order_id uuid references orders on delete set null,
  customer_name text,
  customer_phone text,
  customer_email text,
  amount_bdt numeric(12,2) default 0.00,
  payment_method text default 'bKash',
  payment_status text default 'PAID' check (payment_status in ('PAID', 'PENDING', 'FAILED', 'REFUNDED')),
  trx_id text,
  answers jsonb not null default '{}'::jsonb,
  created_at timestamptz default now()
);

-- 13. Customers Table (Digital Khatabook)
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
create index if not exists idx_customers_merchant on customers(merchant_id, phone);

-- 14. Suppliers Table
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

-- 15. Ledger Transactions Table
create table if not exists ledger_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  customer_id uuid references customers on delete cascade,
  supplier_id uuid references suppliers on delete cascade,
  type text not null check (type in ('credit', 'payment')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  note text,
  product_details jsonb default '[]',
  is_voice_entry boolean default false,
  attachment_url text,
  payment_method text default 'Cash',
  invoice_no text,
  tagada_sent_at timestamptz,
  created_at timestamptz default now()
);

-- 16. Products Table
create table if not exists products (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  code text,
  category text default 'General',
  purchase_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  min_stock_threshold numeric(12,2) not null default 5.00,
  unit text not null default 'pcs',
  qr_code text,
  image_url text,
  created_at timestamptz default now()
);

-- 17. Product Variants Table
create table if not exists product_variants (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  product_id uuid references products on delete cascade not null,
  variant_name text not null default 'Standard',
  supplier_id uuid references suppliers on delete set null,
  qr_code text unique not null,
  cost_price numeric(12,2) not null default 0.00,
  asking_price numeric(12,2) not null default 0.00,
  sale_price numeric(12,2) not null default 0.00,
  stock_quantity numeric(12,2) not null default 0.00,
  created_at timestamptz default now()
);

-- 18. Stock Movement Transactions
create table if not exists stock_transactions (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  product_id uuid references products on delete cascade not null,
  variant_id uuid references product_variants on delete set null,
  type text not null check (type in ('in', 'out')),
  quantity numeric(12,2) not null,
  price numeric(12,2) not null default 0.00,
  customer_id uuid references customers on delete set null,
  supplier_id uuid references suppliers on delete set null,
  reference_note text default 'Stock Movement',
  created_at timestamptz default now()
);

-- 19. Expenses Table
create table if not exists expenses (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  category text not null check (category in ('Utilities', 'Rent', 'Transport', 'Operating', 'Payroll', 'Others')),
  amount numeric(12,2) not null,
  date timestamptz not null default now(),
  description text,
  receipt_image_url text,
  payment_method text default 'Cash',
  is_recurring boolean default false,
  created_at timestamptz default now()
);

-- 20. Business Loans Table
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

-- 21. POS Sales Invoices Table
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

-- 22. Employees Table (RBAC)
create table if not exists employees (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid references merchants on delete cascade not null,
  name text not null,
  designation text not null default 'Staff',
  role text not null default 'Operator' check (role in ('Admin','Finance','Sales','Inventory','Support','Operator')),
  email text not null,
  phone text not null,
  department text default 'General',
  status text default 'Active' check (status in ('Active','Inactive')),
  avatar_url text,
  permissions_json jsonb default '[]'::jsonb,
  joined_date date default current_date,
  updated_at timestamptz default now()
);

-- 23. Business Analytics Summary Table
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
-- STORED PROCEDURES & ATOMIC RESOLUTION FUNCTIONS
-- ============================================================================

-- Atomic Payment Match Function (Triggered when matching SMS is detected)
create or replace function match_payment_atomic(
  p_order_id uuid,
  p_payment_amount numeric,
  p_sender_number text,
  p_trx_id text,
  p_sms_hash text,
  p_sms_log_id uuid
) returns void language plpgsql security definer as $$
begin
  update orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = p_sender_number,
    matched_trx_id = p_trx_id
  where id = p_order_id and status = 'PENDING';

  insert into payments (merchant_id, trx_id, amount, sender_number, sms_hash, status, matched_order_id)
  select merchant_id, p_trx_id, p_payment_amount, p_sender_number, p_sms_hash, 'MATCHED', p_order_id
  from orders where id = p_order_id;

  update sms_logs set processed = true, status = 'matched' where id = p_sms_log_id;
end;
$$;

-- Manual Payment Match Function (Invoked by Merchant from Admin UI)
create or replace function match_payment_manual(
  p_order_id uuid,
  p_payment_id uuid
) returns void language plpgsql security definer as $$
declare
  v_payment_amount numeric;
  v_sender_number text;
  v_trx_id text;
  v_sms_hash text;
  v_merchant_id uuid;
begin
  select amount, sender_number, trx_id, sms_hash, merchant_id
  into v_payment_amount, v_sender_number, v_trx_id, v_sms_hash, v_merchant_id
  from payments
  where id = p_payment_id and status = 'UNMATCHED';

  if not found then
    raise exception 'Payment log not found or already matched.';
  end if;

  update orders set
    status = 'PAID',
    paid_at = now(),
    sender_number = v_sender_number,
    matched_trx_id = v_trx_id,
    manual_match = true
  where id = p_order_id and status in ('PENDING', 'EXPIRED');

  update payments set
    status = 'MATCHED',
    matched_order_id = p_order_id
  where id = p_payment_id;
end;
$$;

-- Enable Row Level Security (RLS) across all tables
alter table merchants enable row level security;
alter table merchant_numbers enable row level security;
alter table orders enable row level security;
alter table payments enable row level security;
alter table sms_logs enable row level security;
alter table devices enable row level security;
alter table appeals enable row level security;
alter table payment_forms enable row level security;
alter table form_submissions enable row level security;
alter table customers enable row level security;
alter table suppliers enable row level security;
alter table ledger_transactions enable row level security;
alter table products enable row level security;
alter table product_variants enable row level security;
alter table stock_transactions enable row level security;
alter table expenses enable row level security;
alter table loans enable row level security;
alter table pos_sales enable row level security;
alter table employees enable row level security;

-- Tenant RLS Policies (Ensure users only see their own merchant data)
create policy "Merchants manage own data" on merchants
  for all using (user_id = auth.uid());

create policy "Users access own orders" on orders
  for all using (merchant_id = current_merchant_id());

create policy "Users access own payments" on payments
  for all using (merchant_id = current_merchant_id());

create policy "Users access own forms" on payment_forms
  for all using (merchant_id = current_merchant_id());

create policy "Public can view published forms" on payment_forms
  for select using (status = 'PUBLISHED');

create policy "Public can submit to forms" on form_submissions
  for insert with check (true);

create policy "Users access own customers" on customers
  for all using (merchant_id = current_merchant_id());

create policy "Users access own products" on products
  for all using (merchant_id = current_merchant_id());

create policy "Users access own POS sales" on pos_sales
  for all using (merchant_id = current_merchant_id());
```

---

## 6. API Reference & Edge Functions Specification

### 6.1 Serverless Edge Functions
| Function Name | Route | Purpose | Authentication |
|---|---|---|---|
| `create-order` | `/functions/v1/create-order` | Creates a new pending order and returns transaction checkout parameters | Public / API Key |
| `process-sms` | `/functions/v1/process-sms` | Database webhook or direct ingest that parses SMS and matches orders atomically | Webhook Secret Header |
| `hosted-form` | `/functions/v1/hosted-form` | Renders published forms, processes customer submissions, and links orders | Public (`--no-verify-jwt`) |
| `resolve-appeal` | `/functions/v1/resolve-appeal` | Approves or rejects customer dispute appeals | Supabase JWT (Merchant) |
| `provision` | `/functions/v1/provision` | Automated database setup and migration runner | Management Token |

### 6.2 REST API Endpoints

#### 1. Create Order Endpoint
- **Method**: `POST /functions/v1/create-order`
- **Headers**: `apikey: <anon_key>`, `Content-Type: application/json`
- **Request Body**:
```json
{
  "amount": 1500.00,
  "cus_phone": "01712345678",
  "cus_name": "Rahim Ahmed",
  "cus_email": "rahim@example.com",
  "product_name": "Premium Cotton Shirt",
  "callback_url": "https://mystore.com/api/payment-webhook",
  "success_url": "https://mystore.com/checkout/success",
  "fail_url": "https://mystore.com/checkout/failed",
  "cancel_url": "https://mystore.com/checkout/cancelled",
  "expires_in_minutes": 15,
  "metadata": {
    "cart_id": "cart_98765",
    "delivery_address": "House 12, Road 4, Dhanmondi, Dhaka"
  }
}
```
- **Response (200 OK)**:
```json
{
  "status": "SUCCESS",
  "order_id": "8f3b23c1-0987-4321-abcd-9876543210ef",
  "tran_id": "ORD-20260824-78912",
  "amount": 1500.00,
  "expires_at": "2026-08-24T14:35:00Z",
  "payment_numbers": {
    "bKash": "01700000001",
    "Nagad": "01800000002",
    "Rocket": "01900000003"
  },
  "checkout_url": "https://pay.yourdomain.com/pay/ORD-20260824-78912"
}
```

#### 2. Process Incoming SMS Webhook
- **Method**: `POST /functions/v1/process-sms`
- **Headers**: `x-webhook-secret: <PROCESS_SMS_WEBHOOK_SECRET>`
- **Request Body**:
```json
{
  "device_id": "d1234567-89ab-cdef-0123-456789abcdef",
  "sender": "bKash",
  "raw_body": "You have received Tk 1,500.00 from 01712345678. Ref: . Fee Tk 0.00. Balance Tk 45,200.00. TrxID 9K28ALM7XP at 24/08/2026 14:20",
  "timestamp": 1787559600000
}
```

#### 3. Merchant Outgoing Webhook Event (Delivered to Merchant Server)
- **Headers**: `X-SwapnoPay-Signature: <HMAC_SHA256_HEX>`, `Content-Type: application/json`
- **Payload**:
```json
{
  "event": "payment.completed",
  "order_id": "8f3b23c1-0987-4321-abcd-9876543210ef",
  "tran_id": "ORD-20260824-78912",
  "amount": 1500.00,
  "payment_method": "bKash",
  "sender_number": "01712345678",
  "trx_id": "9K28ALM7XP",
  "status": "PAID",
  "paid_at": "2026-08-24T14:21:05Z",
  "metadata": {
    "cart_id": "cart_98765"
  }
}
```

---

## 7. Web Application Architecture & Component Structure

### 7.1 Frontend Directory Hierarchy (Next.js 14 App Router)
```
swapnopay-web/
├── app/
│   ├── (auth)/
│   │   ├── login/page.tsx               # Email, Magic Link & Password Login
│   │   ├── register/page.tsx            # Merchant Sign Up & Onboarding Wizard
│   │   ├── reset-password/page.tsx      # Password Recovery
│   │   └── lock/page.tsx                # Biometric & PIN Screen Lock
│   ├── (dashboard)/
│   │   ├── layout.tsx                   # Sidebar Navigation & Top Bar Frame
│   │   ├── page.tsx                     # Main Dashboard & Live Ops Command Center
│   │   ├── transactions/
│   │   │   ├── page.tsx                 # Orders & Payments Data Grid
│   │   │   └── [id]/page.tsx            # Order Detail & Manual Match Drawer
│   │   ├── forms/
│   │   │   ├── page.tsx                 # Payment Forms List & Analytics
│   │   │   ├── builder/page.tsx         # No-Code Visual Form Studio
│   │   │   ├── templates/page.tsx       # Pre-Built Template Picker
│   │   │   └── [id]/responses/page.tsx  # Form Submissions Table
│   │   ├── khatabook/
│   │   │   ├── customers/page.tsx       # Customer Due Ledger & Tagada Reminders
│   │   │   └── suppliers/page.tsx       # Supplier Ledger & Payables
│   │   ├── pos/
│   │   │   ├── page.tsx                 # High-Speed POS Billing Terminal
│   │   │   └── history/page.tsx         # Sales Invoice Archive
│   │   ├── inventory/
│   │   │   ├── page.tsx                 # Product Catalog & Variants
│   │   │   ├── stock-in/page.tsx        # Barcode/QR Stock Movement
│   │   │   └── barcodes/page.tsx        # Printable Barcode Label Generator
│   │   ├── financials/
│   │   │   ├── expenses/page.tsx        # Categorized Expense Tracker
│   │   │   └── loans/page.tsx           # Business Loan & EMI Calculator
│   │   ├── reports/page.tsx             # Financial Analytics & PDF Export
│   │   ├── sms-gateway/
│   │   │   ├── logs/page.tsx            # Raw SMS Logs & Parser Tester
│   │   │   └── devices/page.tsx         # Android Gateway Device Manager
│   │   ├── appeals/page.tsx             # Dispute Resolution Desk
│   │   ├── employees/page.tsx           # Team Management & RBAC
│   │   ├── copilot/page.tsx             # AI Business Assistant
│   │   └── settings/
│   │       ├── general/page.tsx         # Business Profile & KYC Verification
│   │       ├── gateways/page.tsx        # bKash, Nagad, Rocket, Upay Numbers
│   │       ├── webhooks/page.tsx        # Webhook Secrets & Delivery Tester
│   │       ├── supabase/page.tsx        # Supabase Backend Profile Switcher
│   │       └── backup/page.tsx          # Cloud Snapshot & JSON Backup
│   ├── f/
│   │   └── [slug]/page.tsx              # Public Responsive Hosted Checkout Page
│   ├── pay/
│   │   └── [tran_id]/page.tsx           # Public Direct Payment Gateway Modal
│   ├── api/
│   │   ├── health/route.ts              # System Health Probe
│   │   └── webhook-proxy/route.ts       # Secure Outgoing Webhook Proxy
│   ├── layout.tsx                       # Root Layout, Theme & Query Providers
│   └── globals.css                      # Tailwind Directives & Custom Enterprise Theme
├── components/
│   ├── ui/                              # Reusable Buttons, Modals, Tables, Inputs
│   ├── pos/                             # Cart Drawer, Barcode Scanner, Thermal Receipt
│   ├── forms/                           # Form Canvas, Field Controls, Theme Picker
│   ├── khatabook/                       # Tagada Modal, Account Statement Generator
│   └── dashboard/                       # Revenue Area Chart, Stat Cards, Live Feed
├── hooks/
│   ├── useSupabaseRealtime.ts           # Realtime Subscription Hook
│   ├── useAudioRecorder.ts              # Khata Voice Entry Hook
│   └── useBarcodeScanner.ts             # Web Camera Barcode Ingest Hook
├── lib/
│   ├── supabaseClient.ts                # Dynamic Multi-Tenant Supabase Instance
│   ├── smsParser.ts                     # Client-Side Regex Simulator
│   ├── pdfGenerator.ts                  # jsPDF Invoice & Statement Builder
│   └── hmacValidator.ts                 # Cryptographic Signature Verifier
└── types/                               # TypeScript Definitions for all 24 Tables
```

---

## 8. Production Deployment & Operations Playbook

### 8.1 Step 1: Deploy Web Frontend to Vercel / Cloudflare Pages / Node VPS
1. Clone the repository and install dependencies:
```bash
npm install
```
2. Configure `.env.production`:
```properties
NEXT_PUBLIC_DEFAULT_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_DEFAULT_SUPABASE_ANON_KEY=eyJhbGciOi...
NEXT_PUBLIC_PLATFORM_NAME=SwapnoPay
```
3. Build and deploy:
```bash
npm run build
npm run start
```

### 8.2 Step 2: Initialize Merchant Supabase Project
1. Log into [Supabase Dashboard](https://supabase.com).
2. Open the **SQL Editor** and paste the entire script from `supabase/FULL_DATABASE_SCHEMA.sql`.
3. Execute the script to create all 24 tables, stored procedures, and RLS policies.

### 8.3 Step 3: Deploy Deno Edge Functions
1. Install Supabase CLI:
```bash
npm install -g supabase
```
2. Link your project and set edge function secrets:
```bash
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase secrets set PROCESS_SMS_WEBHOOK_SECRET=YOUR_RANDOM_32_BYTE_HEX_SECRET
supabase secrets set RESEND_API_KEY=re_123456789  # Optional for Email receipts
```
3. Deploy functions:
```bash
supabase functions deploy create-order
supabase functions deploy process-sms
supabase functions deploy resolve-appeal
supabase functions deploy hosted-form --no-verify-jwt
```

### 8.4 Step 4: Configure Android SMS Listener Daemon
1. Install `SwapnoPay-Gateway.apk` on an Android phone containing the bKash/Nagad/Rocket/Upay SIM cards.
2. Open the app, grant `RECEIVE_SMS` and `POST_NOTIFICATIONS` permissions.
3. In Setup, enter your **Supabase URL**, **Anon Key**, and **Merchant ID**.
4. The background foreground service will automatically stream received payment SMS directly to your database with zero latency.

---

## 9. Troubleshooting & Disaster Recovery Protocol

| Symptom | Probable Cause | Immediate Remediation |
|---|---|---|
| **SMS Received on phone but order not marked PAID** | Regex mismatch or customer sent from unlisted number | 1. Check `sms_logs` table in Admin dashboard.<br>2. Test raw SMS in the SMS Parser Tester.<br>3. Use **Manual Payment Matcher** to link transaction to order with 1 click. |
| **Realtime status not updating on public checkout page** | Supabase Realtime publication disabled for `orders` table | In Supabase SQL Editor run:<br>`alter publication supabase_realtime add table orders;` |
| **Customer filed duplicate appeal** | MFS delay caused customer to submit twice | Review Appeal Desk, preview screenshot, approve first match and reject duplicate with feedback. |
| **Web POS thermal printer printing gibberish** | Incorrect baud rate or non-ESC/POS driver | Switch printer setting to Generic Text / 80mm ESC/POS mode. |
| **Merchant forgot Admin PIN / Password** | Locked out of web console | Use Magic Link login sent to merchant's registered email or reset via Supabase Auth Console. |

---
*Documentation compiled and maintained for SwapnoPay Enterprise Ecosystem. All rights reserved.*
