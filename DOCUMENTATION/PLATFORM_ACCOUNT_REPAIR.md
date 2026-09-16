# Merchant login, admin data, KYC, and support repair

## Findings and changes

| Failure | Repair |
| --- | --- |
| Existing accounts returned to onboarding when the account lookup failed or no dedicated database was attached. | Lookup failures remain errors. A completed business profile is sufficient for account restoration; database setup is independent. |
| Restoring a database generated another merchant ID. | Restore the canonical `merchants.id`; resolve connections using the authenticated `auth.users.id`, with a legacy merchant-ID fallback. |
| Platform tokens were copied into another project's session, and merchant login could replace platform authentication. | Keep platform and merchant-project sessions separate; each project must issue its own token. Logout clears both. |
| The social-login endpoint fabricated identities and session tokens from an email address. | Use Supabase browser OAuth with PKCE and verify the returned user with platform Auth. The old unauthenticated social-login shortcut is rejected. |
| Official support settings read from the active merchant project; support tickets/chat still used Firebase. | Authenticated backend routes read/write the admin project's `showcase_config`, `support_tickets`, `live_chat_messages`, and `feature_requests`. Replies and tickets refresh every 15 seconds while the support listener runs. |
| Setup and KYC writes ignored Supabase errors, and the app reported success without a saved record. | Check every database result. Save setup and KYC through transactional database functions. Show success only after a confirmed save. |
| KYC read from the merchant database; submitted URLs could point to images that were never uploaded. | Read status from the platform account. Submit actual images to the backend, validate/re-encode them, and use the returned stored paths. |
| Admin reviews bypassed the submission audit, and document URLs used the payment host. | Route approve/reject through the backend transaction and use the API host for relative image paths. |
| Admin screens used separate Supabase clients/configuration. | Share one platform Auth client, with admin environment variables taking precedence. |
| Android files contained interleaved old/new declarations and duplicate function bodies. | Repair those conflicts. Original snapshots are preserved locally under `build/repair-backup` and `build/repair-reappeared`. |

## Deploy in this order

1. Back up the **admin** database. Apply `supabase/migrations/20260916_platform_account_repair.sql` to the platform project. This additive migration assumes the platform's base tables from `ADMIN_DATABASE_SCHEMA.sql` already exist. It is tested for repeat application. Do not run it against a merchant's transaction database.
2. Deploy the updated backend. Set `ADMIN_SUPABASE_URL` and `ADMIN_SUPABASE_SERVICE_ROLE_KEY` for that same project. Generic merchant `SUPABASE_*` variables no longer act as an admin fallback.
3. The root Docker Compose now mounts `backend_uploads` at `/app/uploads`. **Before recreating an existing container, back up its current uploads and copy them into the new volume** so older KYC images remain accessible. Other deployment methods must likewise persist `uploads/kyc`. The backend accepts up to 26 MB for the authenticated KYC endpoint and up to 6 MB per image; the checked-in Nginx config already allows 50 MB.
4. Deploy the admin build with `VITE_ADMIN_SUPABASE_URL`, `VITE_ADMIN_SUPABASE_ANON_KEY`, and `VITE_BACKEND_URL=https://api.swapnopay.top`. Never put the service-role key in browser or Android configuration.
5. In admin System Settings, save the real support contacts/content to `showcase_config` under `system_config`. The migration deliberately does not invent official contact values or overwrite existing settings.
6. Configure enabled Google/Facebook providers and allow `https://swapnopay.top/auth-callback.html` in the platform Auth redirect list. Keep the existing web callback page, which forwards the callback to the app. Install the rebuilt Android app.

The existing Android platform URL is `https://tldubojeokgyoclxnzkb.supabase.co`; backend and admin configuration must target the same project. Merchant databases keep their own URLs, public keys, and independently issued sessions.

## Live acceptance checks

- Sign in with an existing verified account on a clean installation. Confirm the business name and canonical merchant ID restore without repeating onboarding.
- Repeat with an existing business profile that has no dedicated database. It should restore the account; database-dependent operations still need a connected merchant project.
- Make the account endpoint unavailable. Confirm sign-in shows a retryable error and does not erase onboarding state or create another merchant.
- Submit NID front/back and live face images. Confirm both `merchants.kyc_status = 'PENDING'` and an audit row appear in admin. Verify all three images open there.
- Approve/reject from admin. Confirm the audit and merchant status change together, and refreshing KYC in the app retrieves the decision/reason.
- Create a ticket and chat message; reply from admin. Confirm the same merchant sees the reply and another merchant cannot read it.
- Update official support contacts in admin and reopen the support screen. Confirm the app reads the saved platform settings.
- Sign out and sign in as another account. Confirm profile, tickets, chat, and stored sessions do not carry over.

## Validation and limitations

Local validation completed:

- Backend regression suite: 11 passed. The six platform-account/migration regressions also passed after the final migration changes.
- Android: debug APK assembled; all four `DatabaseIsolationTest` tests passed. The first test attempt exhausted C: temporary storage; rerunning with `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:\d\lenden23\build\platform-test-temp` passed using the compiled app classes.
- Admin: `tsc --noEmit` and the production Vite build passed.
- Docker Compose YAML and the persistent upload mount were validated; `git diff --check` passed.

Build artifacts: `app/build/outputs/apk/debug/app-debug.apk` and `build/admin-platform-final/`. Test/build logs are under `build/platform-*.log`.

Regression tests cover distinct Auth/merchant IDs, missing-schema errors, project/key pairing, existing profiles without dedicated databases, ownership mismatches, unauthenticated requests, repeatable migration, preservation of existing credentials, and rollback when a KYC audit insert fails. The SQL tests use the real base table definitions from `ADMIN_DATABASE_SCHEMA.sql` in PGlite/PostgreSQL.

Local tests do not prove the deployed schema, environment, OAuth providers, reverse proxy, or persistent uploads are configured. No live database credentials were available in the workspace and no production migration/deployment was performed. Historical KYC submissions that were falsely reported successful cannot be recovered if neither their data nor images were actually saved; affected merchants must resubmit. Duplicate historical merchants are reported for explicit reconciliation rather than automatically deleted or merged.

Authentication uses server-verified identity as documented in [Supabase getUser](https://supabase.com/docs/reference/javascript/auth-getuser). Platform write functions are restricted to the service role, and browser data access remains subject to [Supabase row-level security](https://supabase.com/docs/guides/database/postgres/row-level-security).
