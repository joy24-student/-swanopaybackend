**System Analysis & Recommendations — SwapnoPay (local workspace)**

Last updated: 2026-08-14

**Scope**: full-code analysis of database integration, Supabase connectivity, UI connection flows, missing functionality, runtime issues, and prioritized recommendations + developer guidance.

**Repository summary**
- Android app using Room (local SQLite) + optional Supabase cloud backend.
- Key areas: Room entities & DAO, `AppDatabase`, `AppRepository`, `SupabaseClient`, `AppViewModel`, and multiple Compose UI screens including `SetupScreen` and onboarding wizard.

**Key files (entry points and references)**
- Room DB and migrations: [app/src/main/java/com/example/data/local/AppDatabase.kt](app/src/main/java/com/example/data/local/AppDatabase.kt#L1)
- DAO methods (supabase profiles, sms queue, orders, payments): [app/src/main/java/com/example/data/local/AppDao.kt](app/src/main/java/com/example/data/local/AppDao.kt#L1)
- Repository orchestration: [app/src/main/java/com/example/data/repository/AppRepository.kt](app/src/main/java/com/example/data/repository/AppRepository.kt#L1)
- Remote Supabase client (HTTP/OkHttp): [app/src/main/java/com/example/data/remote/SupabaseClient.kt](app/src/main/java/com/example/data/remote/SupabaseClient.kt#L1)
- Credentials encryption helper: [app/src/main/java/com/example/security/CryptoManager.kt](app/src/main/java/com/example/security/CryptoManager.kt#L1)
- ViewModel: [app/src/main/java/com/example/ui/AppViewModel.kt](app/src/main/java/com/example/ui/AppViewModel.kt#L1160)
- Setup / Onboarding UI (Connect/Verify/Handshake): [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L7080)

**Architecture overview**
- Local persistence: Room DB (`AppDatabase`) + `AppDao` provides flows and suspend CRUD methods.
- Business logic: `AppRepository` implements parsing engine (SMS), DB insertion, and sync logic (upload to Supabase) and background retry loop.
- Network: `SupabaseClient` uses OkHttp for auth (signIn/signUp/refresh), REST CRUD, RPCs, Edge Functions, and a full realtime CRUD test helper.
- UI: Jetpack Compose screens drive onboarding and setup; `AppViewModel` exposes StateFlows for UI and orchestrates repository and Supabase flows.
- Security: `CryptoManager` encrypts credentials using Android Keystore (AES/GCM). Sensitive keys are stored encrypted in Room.

**Observed runtime / build issues**
1. A compile-time warning logged about a smart-cast to `FirebaseAnalytics` (delegated property); check analytics lazy property access in `AppViewModel` to ensure safe null handling. See compile log and `AppViewModel` analytics section.
2. `SupabaseClient` returns plain string messages for failures; UI sometimes surfaces raw messages (could be improved for translation and user actions).
3. No automated tests for `SupabaseClient` network flows — increases risk of regressions for network edge cases.

**Missing / incomplete functionality**
- `Supabase Profiles` management UI: Room and repo support multiple Supabase profiles (insert/activate/delete), but a dedicated screen to list, edit, delete, and test profiles is missing.
- Better sync controls: No explicit “Force Sync” or retry control in main UI for pending SMS/uploads; only a background retry loop exists.
- Diagnostics UX: `performRealtimeCrudTest` in `SupabaseClient` contains useful checks but the UI lacks a dedicated step-by-step diagnostics viewer exposing logs and remediation guidance.
- Per-profile sync metadata: `supabase_profiles` entities lack explicit last-sync time / last-error fields for diagnostics.

**Security observations**
- The app avoids persisting service-role keys (correct). The UI has a `Secure Proxy Mode` option; make this explicit and add guardrails to prevent service-role keys being pasted/stored client-side.
- `CryptoManager` uses Android Keystore; decryption returns empty string on error. Ensure migration handling if Keystore changes across device restores.

**Detailed issues & recommended fixes**
1. Profile manager (implementation plan, high priority)
   - Create screen `SupabaseProfilesScreen` reachable from `SetupScreen` and Settings.
   - Features: list profiles from `repository.observeSupabaseProfiles()`, test connection via `repository.testSupabaseConnection()`, edit connection name, delete, set active (calls `repository.selectActiveSupabaseProfile`), show encrypted fields masked, reveal with confirmation.
   - Files to modify: add `Screens.kt` composable (new), register route in navigation (`AppNavigation`), wire ViewModel with new flows. Use existing DAO/repo functions.

2. Diagnostics panel (high priority)
   - Add a modal that runs `SupabaseClient.performRealtimeCrudTest` (already present) and shows step logs returned via `onLogStep`. Provide Copy/Share and SQL hints for missing tables.
   - Map HTTP codes to actionable messages (401/403/404) and suggest next steps (check anon key, RLS policy, URL correctness).

3. Force Sync / Retry & Logs (medium)
   - Add a manual UI control in `SetupScreen` and a `Sync Logs` panel that shows recent upload attempts and errors from `AppRepository`/`AppViewModel` logs.
   - Persist last-sync and last-error in `supabase_profiles` for visibility.

4. Improve error handling & types (medium)
   - Refactor `SupabaseClient` methods to return sealed `Result` types or a `NetworkResult` object with `success/failure` plus code and reason. Update `AppRepository` to use structured errors for better UI messages.

5. Tests (medium)
   - Add unit tests for parsing engine (`AppRepository.parseSms`) covering sample SMS formats.
   - Add integration tests for `SupabaseClient` using a mock webserver (OkHttp's MockWebServer) to simulate 200/401/403/404 responses and validate client handling.

**Developer guidance & code snippets**
- Quick command: run unit tests and build locally:

```bash
./gradlew test
./gradlew assembleDebug
```

- To run Room unit tests that already reference `AppDatabase` in-memory builder: see tests under `app/src/test/java/com/example/DatabaseIsolationTest.kt`.

- Example: How to add `lastSyncAt` and `lastSyncError` to `SupabaseProfileEntity` (DB migration + DAO):
  1. Add fields to `SupabaseProfileEntity` data class (default values).
  2. Add Room migration in `AppDatabase.Companion` (e.g., `ALTER TABLE supabase_profiles ADD COLUMN lastSyncAt INTEGER NOT NULL DEFAULT 0;` and `ADD COLUMN lastSyncError TEXT NOT NULL DEFAULT ''`).
  3. Update DAO and repository getters to surface these fields.

**UI / UX suggestions (concise)**
- Single “Backends” entry in main header to access profiles and diagnostics. 
- Show masked keys with reveal action and copy button. 
- Display checklist progress with per-item tooltips linking to exact remediation steps. 
- Add in-app “How to get publishable anon key” modal with screenshots and exact location in Supabase console.

**Priority action list (short-term)**
1. Add `Supabase Profiles` screen — developer story and wire to repo. (High)
2. Expose `performRealtimeCrudTest` logs in modal and map errors → remediation texts. (High)
3. Add manual `Force Sync` and `Retry` control; persist last-sync metadata. (Medium)
4. Add structured network results & tests for `SupabaseClient`. (Medium)

**Next steps I can implement for you**
- Implement `Supabase Profiles` screen and wire ViewModel + navigation. (I can code this.)
- Add diagnostics modal connected to `performRealtimeCrudTest` with copyable logs. (I can code this.)
- Add `lastSyncAt`/`lastSyncError` fields with proper Room migration. (I can code migration + DAO changes.)

If you want, tell me which of the next steps to implement and I will start by creating the UI components and tests. Otherwise I can produce a PR-ready patch that implements the top-priority item (`Supabase Profiles` screen).

---
Generated by: repo analysis session (2026-08-14)

## Screen-by-screen Analysis

Below is a concise, actionable breakdown of each major UI screen, where it's defined, its responsibilities, the connected repository/ViewModel behavior, duplicate/overlapping functionality, and missing or suggested improvements.

- **Splash Screen**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L862)
   - Purpose: app initialization, splash steps, DB prepopulation, encrypted session check, auto-routing.
   - Key ViewModel hooks: `performSplashInitialization()`, `verifyEncryptedSessionToken()`.
   - Observations: good progress step model; consider surfacing slow-step diagnostics when migrations take long.

- **Onboarding Screen / Setup Wizard**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L2094)
   - Purpose: 6-step onboarding (DB connect, admin account, PIN/biometric, branding, final handshake).
   - Key UI actions: handshake pulse triggers `connectSupabase()` and `authenticateConnectedSupabase()` then calls `runSupabaseSystemTest()`.
   - Duplicate behavior: duplicates some Setup flows (Connect/Verify) that also exist in `SetupScreen` (see below). Consider consolidating into a single canonical setup path.
   - Missing: clearer error recovery when authentication fails during handshake, and an explicit step to manage saved profiles.

- **Login Screen**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L3534)
   - Purpose: sign-in UI for admin/operator accounts and recovery flows.
   - Connected flows: uses `SupabaseClient.signIn` and ViewModel authentication state. Check for consistent error-to-UI mapping.

- **Lock Screen**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L1150)
   - Purpose: PIN / biometric unlock when device is locked.
   - Observations: integrates with local Encrypted session; no immediate issues.

- **Setup Screen (Supabase Setup)**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L6887)
   - Purpose: primary place for connecting a Supabase project; contains Connect/Verify buttons and checklist.
   - Key ViewModel hooks: `connectSupabase()`, `runSupabaseSystemTest()`, diagnostic navigation to `SupabaseSetupGuideScreen`.
   - Duplicate actions: two buttons labeled Connect and Verify both call `connectSupabase()` — functional duplication; consider making Verify explicitly call `runSupabaseSystemTest()` and Connect only save profile.
   - Missing: dedicated `Supabase Profiles` management UI (create/edit/delete/select) and per-profile last-sync metadata.

- **Supabase Setup Guide Screen**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L7971)
   - Purpose: long-form step-by-step guidance and links to console / SQL and CLI guidance.
   - Observations: helpful static content; could be improved by embedding interactive checks (call `SupabaseClient.performRealtimeCrudTest`) and surfacing results inline.

- **Firebase Config Screen**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L4354)
   - Purpose: configure Firebase Realtime DB / notifications used for broadcast notices.
   - Observations: `AppViewModel.listenToFirebaseNotice()` adds RTDB listener; ensure empty-state and permission/error handling are surfaced.

- **Transactions / Ledger Screens**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L9376)
   - Contains: `TransactionsScreen` and `LegacyTransactionsScreen` (near-duplicate views).
   - Duplicate behavior: `LegacyTransactionsScreen` appears to replicate `TransactionsScreen` functionality; merge or clearly separate responsibilities (legacy listing vs new UX).
   - Connected flows: uses `AppRepository.observePayments`, `observeOrders`, `observeLedgerTransactions`.

- **Appeals Screen**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L9927)
   - Purpose: list and resolve appeals; connected to `resolveAppeal` Edge Function via `SupabaseClient.resolveAppeal`.
   - Missing: optimistic UI updates and clear error handling when Edge Function fails (retries / local queueing).

- **More / Settings / Reports**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L10044) [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L10902) [app/src/main/java/com/example/ui/ReportsScreen.kt](app/src/main/java/com/example/ui/ReportsScreen.kt#L56)
   - Purpose: app settings, preferences, analytics, various reports.
   - Observations: several shortcuts to `SupabaseSetupGuide` from different checklist items, which is helpful but can feel repetitive.

- **Order & Payment Detail Screens**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L11428) [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L11567)
   - Purpose: view details; hooked to Room cached entities; OK.

- **Business Profile & Payment Methods**: [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L11676) [app/src/main/java/com/example/ui/Screens.kt](app/src/main/java/com/example/ui/Screens.kt#L12474)
   - Purpose: merchant profile management and payment numbers; backed by Room. Consider adding import/export for merchant profile.

- **Form Builder / Hosted Forms**: [app/src/main/java/com/example/ui/FormBuilderScreens.kt](app/src/main/java/com/example/ui/FormBuilderScreens.kt#L38)
   - Purpose: create hosted checkout/payment forms; flows save to `PaymentFormCacheEntity` and optionally publish to Supabase Functions.
   - Duplicate behaviors: hosted form listing and form builder are used in multiple menu routes (`FormBuilder`, `FormBuilderStudio`, `AiFormBuilder`) — these appear to be variants of the same studio; unify route aliases or consolidate UI.

- **Backup & Restore**: [app/src/main/java/com/example/ui/BackupAndRestoreScreen.kt](app/src/main/java/com/example/ui/BackupAndRestoreScreen.kt#L41)
   - Purpose: local DB backup/restore. Missing: explicit export format docs and restore safety checks (version mismatch, migrations).

- **Employees / Ledgers / Reports**: [app/src/main/java/com/example/ui/EmployeesScreen.kt](app/src/main/java/com/example/ui/EmployeesScreen.kt#L40) [app/src/main/java/com/example/ui/LedgersScreens.kt](app/src/main/java/com/example/ui/LedgersScreens.kt#L214)
   - Purpose: HR and bookkeeping features backed by Room.

### Duplicate / Overlapping Options (found)
- Connect / Verify duplication: `SetupScreen` has both "Connect to Project" and "Verify Connection" buttons that call the same `connectSupabase()` path — leads to ambiguity. Suggest: `Connect` = save profile; `Verify` = system test.
- Onboarding handshake vs Setup connect: onboarding wizard and `SetupScreen` duplicate connection flows. Consolidate to avoid divergent behavior.
- Transactions listing: `TransactionsScreen` vs `LegacyTransactionsScreen` likely duplicate similar lists; merge or mark legacy as deprecated.
- Form builder routes: several aliases for the Form Builder UI create maintenance overhead; prefer canonical route and lightweight aliases.

### All functionality (summary)
- Local persistence (Room): sms queue, payments, orders, appeals, merchant profile, devices, mfs_patterns, customers, suppliers, products, variants, stock, expenses, loans, pos_sales, analytics, employees, merchant numbers, form cache, form submissions.
- Supabase integration: auth (signIn/signUp/refresh), REST table CRUD (orders, payments, devices), RPCs for revenue/cancel/extend, Edge Functions (resolve-appeal, hosted-form), realtime CRUD test for end-to-end validation.
- Parsing engine: robust SMS regex patterns with dynamic patterns loaded from `mfs_regex_patterns` (syncable from Supabase).
- Onboarding & security: encrypted AES-256 session, PIN & biometric lock flows, encrypted Supabase credential storage via Keystore.
- Background services: SMS queue auto-retry, device registration, periodic syncs initiated in `AppRepository`.

---

Progress update: screen-by-screen analysis appended. Next I can implement consolidation recommendations (e.g., fix Connect/Verify duplication) or scaffold the `Supabase Profiles` management screen — which would you prefer I build first?


