# SwapnoPay branded form router

Cloudflare Worker origin for `https://forms.swapnopay.top`. It keeps customer-facing form URLs branded while each form, response, order, and uploaded file remains in the merchant's own Supabase project.

Public URLs use the form's random UUID without hyphens:

```text
https://forms.swapnopay.top/f/7bf835b29f524bafa8b50fd709985507
```

The Worker stores only public routing metadata in KV: project URL, form UUID, slug, registration owner UUID, and timestamps. It never stores a merchant access token, publishable key, database password, or service-role key.

## Request flow

```text
Android publish
  -> merchant payment_forms row (authenticated/RLS)
  -> POST forms.swapnopay.top/v1/routes (merchant access token)
  -> Worker verifies /auth/v1/user and payment_forms ownership
  -> KV public route mapping

Customer /f/<public-id>
  -> Cloudflare Worker
  -> merchant /functions/v1/hosted-form?slug=<slug>
  -> merchant database and private Storage
```

All browser GET, submission POST, attachment upload, and payment-status polling requests remain on the branded origin and are proxied to the correct merchant function. The origin hostname is accepted only when it matches a canonical `https://<project-ref>.supabase.co` URL, preventing arbitrary-origin proxying.

## Deploy once for SwapnoPay

Prerequisites: the `swapnopay.top` zone must be active in the same Cloudflare account used by Wrangler. Do not create a competing `forms` CNAME; Cloudflare Custom Domains creates the DNS record and certificate.

```powershell
cd form-router
npm install
npx wrangler login
npx wrangler kv namespace create FORM_ROUTES
```

Copy the returned namespace ID into `wrangler.toml` as `FORM_ROUTES.id`, then run:

```powershell
npm run check
npm run deploy
```

Verify:

```powershell
curl.exe https://forms.swapnopay.top/health
```

This Worker is deployed once by SwapnoPay. The `hosted-form` Edge Function and database migrations are still deployed separately into every merchant-owned Supabase project.

## Registration security

`POST /v1/routes` requires the merchant's current Supabase access token. The Worker verifies that token against the supplied project, then performs an RLS-scoped lookup of the exact published form. A user cannot register another merchant's form without a valid session in that merchant project.

The Android application falls back to the direct merchant Supabase URL when registration fails during the current session. Route registration is idempotent: publishing the same form again refreshes its slug while preserving its public URL.
