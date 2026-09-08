# SwapnoPay Platform Webhook Receiver (Supabase Service Role Client)

This Express app accepts POST requests from merchant systems and writes received submissions directly into Supabase under `form_submissions`.

Environment variables required:

- `SUPABASE_URL` — Your Supabase Project URL (e.g. `https://your-ref.supabase.co`).
- `SUPABASE_SERVICE_ROLE_KEY` — Supabase Service Role Secret Key.
- `PLATFORM_WEBHOOK_SECRET` — Required random secret of at least 32 characters. Send it only as `Authorization: Bearer <secret>` or `X-Platform-Secret`.

Start locally:

```bash
$env:SUPABASE_URL = "https://abc123xyz.supabase.co"
$env:SUPABASE_SERVICE_ROLE_KEY = "your-service-role-key"
$env:PLATFORM_WEBHOOK_SECRET = "your-random-secret-of-at-least-32-characters"
npm install
npm start
```

Example merchant POST:

```bash
curl -X POST https://platform.example.com/ingest \
  -H "Content-Type: application/json" \
  -H "X-Platform-Secret: your-secret" \
  -d '{"merchantId":"00000000-0000-4000-8000-000000000001","formId":"00000000-0000-4000-8000-000000000002","submission": {"name":"Alice","phone":"+8801..."}}'
```

Security notes:

- Use HTTPS and a unique strong `PLATFORM_WEBHOOK_SECRET`; the service refuses to start without it.
- `SUPABASE_SERVICE_ROLE_KEY` is server-only and must never be committed, placed in Vite variables, or shipped in Android.
