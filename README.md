# SwapnoPay Merchant Android

Offline-first Android merchant application built with Jetpack Compose, Room, Firebase Messaging, and a tenant-isolated Supabase backend.

## Local development

Requirements:

- Android Studio with JDK 17
- Android SDK 36.1 and a device/emulator running Android 7.0 or newer
- A Firebase `google-services.json` in `app/` if Firebase messaging and notices are required

Optional build-time Firebase labels can be placed in a root `.env` file:

```properties
FIREBASE_API_KEY=
FIREBASE_PROJECT_ID=
```

Blank values are supported. No service-role, R2 secret, or private server credential belongs in `.env` or the APK.

Build and verify:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

## Supabase production setup

Create a Supabase project, keep Email confirmation enabled, and apply all migrations in order:

```powershell
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push
```

For a brand-new project, apply `supabase/FULL_DATABASE_SCHEMA.sql`, migration 10, and migration 11. Existing projects must apply migrations 09-11 after migrations 01-08. The normal `supabase db push` command applies the ordered migration set automatically. Migration 11 makes the merchant database the payment source of truth, persists gateway policy, and creates the idempotent payment-receipt outbox.

Set Edge Function secrets and deploy the functions:

```powershell
supabase secrets set PROCESS_SMS_WEBHOOK_SECRET=YOUR_RANDOM_32_BYTE_SECRET
supabase secrets set RESEND_API_KEY=YOUR_RESEND_KEY
supabase secrets set FORM_NOTIFICATION_FROM="SwapnoPay Forms <forms@your-domain.example>"
# Optional SMS alert adapter; it must accept {to,message,submission_id,form_id} JSON.
supabase secrets set FORM_SMS_PROVIDER_URL=https://sms-provider.example/send FORM_SMS_PROVIDER_TOKEN=YOUR_PROVIDER_TOKEN
# Issued by the provider-owned Oracle service for this merchant/project only.
supabase secrets set PAYMENT_RECEIPT_WEBHOOK_SECRET=YOUR_SECOND_RANDOM_32_BYTE_SECRET
supabase secrets set SWAPNOPAY_RECEIPT_SERVICE_URL=https://api.swapnopay.top
supabase secrets set SWAPNOPAY_RECEIPT_API_KEY=YOUR_ENROLLED_PROJECT_RECEIPT_KEY
supabase functions deploy create-order
supabase functions deploy process-sms
supabase functions deploy resolve-appeal
supabase functions deploy hosted-form --no-verify-jwt
supabase functions deploy payment-receipt --no-verify-jwt
```

Create the `sms_logs` INSERT database webhook targeting `/functions/v1/process-sms`. Add an `x-webhook-secret` header matching `PROCESS_SMS_WEBHOOK_SECRET`.

Create a `payment_receipt_outbox` INSERT webhook targeting `/functions/v1/payment-receipt` with an `x-webhook-secret` header matching `PAYMENT_RECEIPT_WEBHOOK_SECRET`. Then configure the retry job from `supabase/payment_receipt_scheduler.example.sql`; it calls the same worker every minute using Supabase Vault, `pg_cron`, and `pg_net`.

Only the public Supabase URL and publishable/anon key are entered in the mobile onboarding flow. The service-role key remains exclusively in Supabase Edge Function secrets. Migration 09 enables tenant RLS, server-derived merchant IDs, protected payment transitions, and a private `appeal-screenshots` bucket. Migration 10 adds the private `form-uploads` bucket; uploaded response files are opened through a 60-second merchant-authorized signed URL and are never public objects.

The connection and renewable merchant session are encrypted on the phone. Database setup is therefore a one-time operation per installation; subsequent launches require only merchant login or the configured device lock. The `hosted-form` function serves published forms from the merchant's own Supabase project and writes validated, idempotent orders and submissions back to that project. Form-submission alerts can use Resend; verified-payment receipts use the provider-owned Oracle service and official Google Workspace Gmail API. Gmail OAuth credentials never enter Android or a merchant-owned Supabase project. Failed notification delivery never rolls back a submission or verified payment.

## Production payment gateway and official receipts

The payment state transition is owned by the merchant database:

```text
official MFS SMS / approved appeal
        -> merchant Supabase atomic transaction
        -> orders.status = PAID
        -> payment_receipt_outbox
        -> merchant payment-receipt Edge worker
        -> api.swapnopay.top on Oracle VPS
        -> payments@swapnopay.top through Gmail API
        -> separate customer and merchant receipts
```

The Oracle API cannot mark an order paid. It accepts only the fixed, enrolled-project receipt event produced after the merchant database commit, enforces idempotency and per-merchant quotas, and stores delivery/message IDs without storing arbitrary email HTML. Deploy it with [`deploy/oracle/README.md`](deploy/oracle/README.md). Configure Google Workspace SPF, DKIM, and DMARC before enabling live receipts.

The Android Payment Gateway Setup screen writes non-secret limits, callback URLs, and receipt preferences to `payment_gateway_settings` under tenant RLS. Active bKash, Nagad, Rocket, and Upay methods come from real `merchant_numbers`; provider/Gmail/API secrets are server-managed and are not editable or displayed on the phone.

## Branded hosted form domain

Customer-facing forms use the SwapnoPay-owned Cloudflare Worker at:

```text
https://forms.swapnopay.top/f/<32-character-public-form-id>
```

The public ID is the form's random UUID without hyphens, so it is stable and non-sequential. When an authenticated merchant publishes a form, the Android app first syncs the form into that merchant's `payment_forms` table and then registers the branded route at `POST https://forms.swapnopay.top/v1/routes`. The router verifies the merchant access token against the supplied Supabase project and performs an RLS-scoped form ownership check before writing the route mapping.

The router proxies rendered HTML, submissions, uploads, and payment-status polling to:

```text
https://<MERCHANT_PROJECT_REF>.supabase.co/functions/v1/hosted-form?slug=<FORM_SLUG>
```

Only public routing metadata is held by SwapnoPay. Merchant responses, orders, files, database credentials, access tokens, and service-role keys are not copied into the routing registry. If registration fails during publishing, the app uses the merchant's direct Supabase hosted-form URL as a fallback.

Deploy the branded router once from [`form-router/`](form-router/README.md). Its Cloudflare Custom Domain configuration creates the `forms.swapnopay.top` DNS record and TLS certificate; remove any existing conflicting `forms` CNAME before deployment. Each merchant-owned Supabase project still requires its own migrations and Edge Functions described above.

## Subscription boundary

Because every merchant owns and administers their Supabase database, a subscription flag stored there is not trustworthy for enforcing your fee. Production subscriptions must be verified by a small provider-owned licensing API or Google Play Billing. Bind each license to the Supabase project reference plus the authenticated merchant user ID, return a short-lived signed entitlement, and never place the licensing service secret in the APK. This test build intentionally does not claim that a paid plan is active.

## Release signing

Release builds enable R8 and resource shrinking. Supply signing credentials through CI environment variables:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_ALIAS       # optional; defaults to upload
KEY_PASSWORD
```

Without these variables Gradle produces an unsigned release artifact, which is useful for verification but cannot be distributed. Restrict Firebase API keys in Google Cloud Console and keep signing files outside source control.

## Data behavior

- Every operational screen reads from Room for reliable offline use.
- Records are isolated by the selected merchant/backend profile.
- Authenticated Supabase sessions are encrypted locally and refreshed before expiry.
- Cloud sync uses Supabase RLS; the app never supplies a trusted `merchant_id`.
- PIN and biometric settings remain device-local and are never uploaded.
- Failed writes remain visible in local storage and report their sync failure instead of claiming success.
