# v27.45.0 Production Calendar Foundation review

Production Calendar adds authenticated owner-scoped persistence and three API operations under the existing Shifts module gate. The backend derives the owner from the authenticated principal; clients cannot provide a user id. Local mutation is accounting-period locked, database uniqueness is `(user_id, calendar_date, layer)`, and the v27.45.0 UI can edit only `LOCAL_OVERRIDE`; `BASE` is reserved for future official/imported sources. The release adds no credential, role, cross-account sharing path or dependency. Production Calendar changes work-norm metadata only and does not create absences or execute money rules.

Status: v27.45.0.

# v27.44.3 People Profile Coverage Semantics Hotfix review

v27.44.3 changes only client-side interpretation/presentation of already-authorized companion calendar data. It adds no endpoint, credential, role, authorization rule, persistence path, account-to-account transport or dependency. The fix is fail-closed: dates outside declared companion schedule coverage are treated as unknown rather than inferred free time.

Status: v27.44.4.

# v27.44.2 Exact Availability Timeline & Shared Shift Summary review

v27.44.2 adds only local derived presentation: exact availability boundary labels and aggregate overlap statistics over already-authorized calendar data. It adds no endpoint, credential, authorization rule, persistence path, account-to-account transport or dependency. Shared-shift totals are not persisted.

Status: v27.44.2.

# v27.44.1 Shared Availability UX & Shared Shifts review

v27.44.1 adds only client-side derived presentation over calendar data already authorized to the signed-in owner. It adds no endpoint, credential, role, authorization rule, persistence path, dependency or account-to-account transport. Shared-shift highlighting uses only effective work intervals and persists no inferred overlap.

Status: v27.44.1.

# v27.44.0 Shared Availability review

v27.44.0 adds no endpoint, credential, role, authorization rule, persistence path, dependency or account-to-account sharing transport. Shared Availability is derived in Vue only from calendar data already authorized to the signed-in owner. It exposes no other-profile Notes, Tasks, Payroll, Overtime, personal absence ledger or credentials, and persists no inferred free windows.

Status: v27.44.0.

# v27.43.0 People Profiles Managed Schedule Overrides review

v27.43.0 adds two authenticated Calendar-module endpoints for a calendar-layer owner to create/replace or delete one date-specific schedule override. Authorization is inherited from the parent `CalendarLayerService.requireOwned` check, ownership mismatches remain indistinguishable from not-found, and write requests remain under the existing CSRF/session policy. V48 stores only managed schedule facts (`WORK`/`OFF`, optional reason, owned shift type and optional time); it does not introduce account-to-account sharing, credentials, roles, personal Notes, Payroll, Overtime or absence-ledger access.

No dependency, CSP, offline-queue ownership or cross-user data path is added. Shared Availability remains out of scope.

Status: v27.43.0.

# v27.42.8 Browser Bundle Source Contract Alignment review

v27.42.8 changes no endpoint, authorization rule, credential handling, persistence path, accounting mutation, dependency or browser runtime behavior. It aligns one Java source-contract assertion with the already-shipped segmented bundle-budget keys and advances release identity only.

# v27.41.8 Shared Runtime Bundle Split review

v27.41.8 adds no endpoint, credential, authorization rule, dependency, persistence authority, cross-user data path or accounting mutation. It only changes Rollup chunk ownership for same-origin JavaScript already present in v27.41.7. CSP/same-origin policy and the service-worker JavaScript caching strategy remain unchanged; the browser audit continues to scan every emitted JS file for forbidden Node/CommonJS patterns.

# v27.41.7 Frontend Bundle Segmentation review

v27.41.7 adds no endpoint, credential, authorization rule, storage authority, dependency, cross-user data path or accounting mutation. The change only alters how already-authorized Vue code is delivered: a stable same-origin ES-module entry imports content-hashed same-origin chunks. Existing same-origin/CSP rules remain unchanged; the service worker uses its existing network-first JS policy and caches successful chunk responses under the current release cache. The audit scans all emitted JS for forbidden Node/CommonJS runtime patterns and caps entry, per-chunk and total size.

# v27.41.6 Accounting Integrity Semantics & Actionability review

v27.41.6 adds no endpoint, credential, role, authorization, dependency, cross-user data path or automatic repair capability. It corrects reconciliation of existing current-user absence audit facts and exposes only audit metadata already returned by the authorized ledger-integrity response. Historical accounting mutations remain explicit/manual.

# v27.41.5 Browser Bundle Budget Rebaseline review

v27.41.5 changes no endpoint, credential, role, authorization, persistence, cross-user schedule access, dependency, runtime code path or accounting authority. The only production-delivery change is a narrow raw browser-bundle ceiling rebaseline from 800000 B to 810000 B; the gzip ceiling remains 250000 B and all forbidden-runtime-pattern checks remain blocking.

# v27.41.3 Today Visual Parity & Overtime Summary Recovery review

v27.41.3 adds no endpoint, credential, role, authorization, persistence, cross-user schedule access or dependency. Today consumes only the same current-user calendar/overtime snapshot already authorized for Calendar/Overtime; People Profiles remain out of scope.

# v27.41.2 Overtime Chart Source Contract Alignment review

v27.41.2 changes no auth, authorization, HTTP surface, persistence, offline queue or dependency. It aligns one Java source-contract assertion with existing pixel chart geometry.

# DutyLog security review

## v27.41.1 Overtime Chart Rendering Recovery review

- No endpoint, credential, role, authorization, CSRF, session, origin, storage or permission change.
- The release only changes Vue/CSS chart geometry and browser assertions over data already authorized for the current user.
- No new HTML injection surface, dependency or cross-user data path is introduced.
- `dataLayer` remains the only IndexedDB/outbox/reconnect executor; OpenAPI remains 124/130 and Flyway remains V47.

Status: v27.42.8.

## v27.41.0 Calendar Visual Language Foundation review

- No endpoint, credential, role, authorization, CSRF, session, origin, storage or permission change.
- The release consumes only calendar data already authorized for the current user and adds no People Profile/shared-schedule data path.
- Important Day icons and custom day markers are presentation-only values already returned by existing contracts; no HTML injection or new rendering authority is introduced.
- `dataLayer` remains the only IndexedDB/outbox/reconnect executor; OpenAPI remains 124/130 and Flyway remains V47.

Status: v27.41.0.


## v27.40.30 Legacy retirement closure / onboarding boundary review

- No endpoint, credential, role, origin, authorization, session, storage or remember-me policy changes.
- First-run onboarding remains on the existing authenticated module/profile APIs and gains only an explicit presentation-owner marker.
- The bounded exception grants no new authority and owns no route, normal Settings screen or offline queue execution.
- `dataLayer` remains the only IndexedDB queue/sync executor; OpenAPI remains 124/130 and Flyway V47.

Status: v27.41.0.


## v27.40.29 Vue logout ownership and offline status contract hotfix review

- No endpoint, credential, role, origin, storage engine, authorization or remember-me policy changes.
- Vue logout stops depending on retired DOM but still uses the existing same-origin CSRF-aware `POST /logout` path and server-side remember-me revocation.
- The new `dutylog:logout-request` is an in-page presentation/action signal only; it grants no new authority and is consumed by the existing authenticated shell session code.
- `data-network-state` exposes only coarse online/offline presentation state already visible to the user; no queue payload or secret is added to DOM.
- `dataLayer` remains the only IndexedDB queue/sync executor; OpenAPI remains 124/130 and Flyway remains V47.

Status: v27.40.29.

## v27.40.28 Legacy header async boot ownership hotfix review

- No endpoint, credential, role, origin, storage engine, authorization or CSRF boundary changes.
- The hotfix only guards recovery-header identity writes after Vue readiness; it does not restore duplicate chrome or expose new client authority.
- Authoritative profile/module state continues to publish through the existing same-origin platform boundary.
- `dataLayer` remains the only IndexedDB queue/sync executor; offline/sync semantics are unchanged.
- OpenAPI remains 124/130 and Flyway remains V47.

Status: v27.40.28.

## v27.40.24 final legacy ownership audit review

- No endpoint, role, credential, token, origin, storage engine or authorization boundary is added.
- Removing dead post-ready fallback DOM reduces duplicate UI surface without moving backend authority into the browser.
- First-run onboarding and offline/sync presentation retain their existing behavior; dataLayer remains the single offline mutation/sync owner.
- OpenAPI remains 124/130 and Flyway remains V47.

Status: v27.40.24.

## v27.40.23 pre-Vue Admin fallback contract alignment review

- This hotfix changes no authorization, endpoint, credential, storage or client privilege behavior.
- Admin remains protected by both `/api/admin/**` and `/api/v1/admin/**` ADMIN matchers plus controller defense in depth.
- The test alignment preserves the safer pre-Vue behavior: `#admin` does not revive a legacy privileged screen and falls back to Settings until Vue readiness.

Status: v27.40.23.


## v27.40.22 Vue Admin workspace and final live legacy UI retirement review

- Both `/api/admin/**` and canonical `/api/v1/admin/**` are protected by the same Spring Security ADMIN matcher and the controller's existing `requireAdmin()` defense-in-depth checks.
- Vue Admin uses same-origin generated transport with the existing session/CSRF client; no bearer secret, third-party origin, local privilege cache or client-side authorization authority is introduced.
- Removing legacy Admin state/API/render handlers and the final post-Vue route adapter reduces duplicate privileged UI surfaces.
- Offline `dataLayer`, service-worker API policy, PostgreSQL/Flyway V47 and secret handling are unchanged.

Status: v27.40.22.

## v27.40.21 Vue Payroll workspace retirement review

- Payroll continues to use same-origin authenticated generated API operations; Spring Boot remains authoritative for ownership, closed-period eligibility, ledger integrity and immutable calculation snapshots.
- The Vue migration adds no credential, token, origin, storage engine, offline queue or client-side monetary authority.
- Removing legacy Payroll browser state/helpers reduces duplicate mutation surfaces rather than expanding them.

Status: v27.40.21.

## v27.40.20 E2E release-version contract alignment review

- The change is test/release metadata only. It adds no endpoint, credential flow, permission, storage surface or cross-origin dependency.


## v27.40.19 E2E release-version authority review

- Reads only committed root `package.json` from the Node test process; no browser secret, endpoint, origin, storage or authorization boundary changes.

## v27.40.16 Route-entry freshness / Today workspace review

- Fresh route-entry reads use existing same-origin generated APIs and existing session/authorization boundaries.
- Today workspace visibility/order is derived from already-authoritative settings/module state; client route guards remain UX only.
- Note create read-your-write uses the already-returned server DTO and does not add storage, queue, retry or reconnect ownership.

## v27.40.15 Vue Route Guard Authority review

- Client-side Admin/module route guards now run in Vue only after authoritative profile/module state is known; this is UX/navigation enforcement, not a replacement for backend authorization.
- Blocked hashes canonicalize to Calendar, while server-side ownership/role/module checks remain authoritative for every protected API.
- No new origin, endpoint, token, cookie, storage engine, service-worker data cache or offline queue is introduced.

## v27.40.13 Vue Route State Authority review

- Route authority moves only between same-origin frontend layers: Vue now reads/writes the existing URL hash directly; no new origin, token, cookie or server endpoint is introduced.
- Payroll/Admin continue to use the same legacy authorization and server-side guards; direct hash navigation does not grant access.
- Offline persistence, service worker behavior, CSP, CSRF and session boundaries are unchanged.

## v27.39.0 Settings, Workspace & Integrations review

- Migrated settings remain same-origin/session-authenticated and use generated `/api/v1/*` transport; no third-party browser credential is introduced.
- Calendar subscription URLs are bearer secrets: only the server stores the token hash, while the raw issued URL is kept in volatile Vue state long enough to copy and is not persisted to localStorage or diagnostics.
- Telegram link codes/status/settings use canonical `/api/v1/telegram` aliases; the migration does not expose the bot token to the browser.
- Existing CSP, CSRF and cookie/session boundaries remain unchanged. Backend module guards remain fail-closed.
- ADR-008 changes Vite production source maps from public-by-default to disabled-by-default; explicitly requested maps are hidden diagnostic artifacts and are not linked from runtime assets.

## v27.38.0 Vue Tasks, Notes & Important Days review

- The migrated feature uses existing same-origin authenticated generated transport; no new origin, token, cookie, public endpoint or secret is introduced.
- Spring Boot keeps authorization, ownership and validation authority for tasks, notes, Important Events and Inbox conversion.
- Q-10 reuses the existing dataLayer offline queue/cache through typed bridge calls; it does not add a second persistence engine, service-worker API cache or offline authorization path.
- Offline-capable mutations are limited to behavior already supported by the existing queue. Reconnect flushes queued work and then refreshes authoritative server state.
- OpenAPI expands to 101 operations / 106 schemas, but PostgreSQL remains V47 and no CSP/session/CSRF/bearer/Telegram-secret boundary changes.

## v27.37.1 strict typecheck hotfix review

No security boundary changes. The release adds compile-time annotations only, keeps generated API transport, authentication, CSRF, session, ownership and legacy-island boundaries unchanged, and does not add a dependency or endpoint.

## v27.37.0 Vue Calendar & Timeline review

- The migrated feature uses only existing same-origin generated operations through the shared credentials/CSRF/request-ID transport; it adds no endpoint, origin, token or persistence authority.
- Spring Boot remains the sole authority for ownership, recurrence, shift/task/event/absence validation, calendar layers and every mutation.
- Vue receives owner-scoped read models and keeps only focused date, view mode and bounded presentation state. It does not cache API responses in the service worker or persist sensitive payloads.
- The selected-day editor remains one named compatibility island. Its host and bridge expose commands, not mutable global state, and successful legacy mutations request one Vue refresh.
- The PWA worker deletes only previous `dutylog-shell-*` caches, uses network-first HTML/JS/CSS and never caches API, authentication or user-data responses.
- Bundle budgets, strict Playwright page-error collection, one-image deployment, session/CSRF boundaries and the v27.36.5 single-pass staging path remain fail-closed.
- OpenAPI, PostgreSQL, Flyway V47, CSP, cookies, secrets and image-signing boundaries are unchanged.

## v27.36.4 Vue Absence & Time Bank browser-parity review

- The new bridge event carries owner-scoped read models already returned to the authenticated browser; it adds no endpoint, origin, credential, token or persistence path.
- Vue remains the only mutable domain presentation owner. Legacy receives a one-way projection and cannot write domain state through the event.
- Route preservation and modal deletion do not alter authorization, CSRF, session, ownership, closed-period or FIFO enforcement.
- No OpenAPI, PostgreSQL, Flyway, CSP, cookie or secret-handling change is introduced.

## v27.36.3 CI artifact quota resilience review

- Scope is GitHub Actions report publication, static workflow contracts, release identity and documentation.
- Artifact upload failures are tolerated only after the blocking build/test command has completed; frontend, Maven, Playwright, image and migration-smoke failures remain fatal.
- No credential, token, permission, deployment environment, runtime request, authentication, authorization, session, CSRF or persistence behavior changes.
- Short retention and compact JaCoCo payloads reduce storage exposure; run/attempt-qualified names prevent rerun collisions.

## v27.36.2 Vue timer static-contract compile-coverage review

- Scope is one source-only Java regression test, local compile-gate coverage, release identity and documentation.
- No authentication, authorization, CSRF, session, secret, request, persistence or dependency behavior changes.
- Strict TypeScript and the v27.36.1 browser timer implementation remain fail-closed; no cast, compiler relaxation or runtime change is introduced.

## v27.36.0 Vue Absence & Time Bank review

- Spring Boot remains the sole authority for authentication, ownership, absence overlap, compensation policy, FIFO reservation/posting/reversal, closed periods and ledger integrity.
- The Vue feature uses only same-origin generated operations through the shared credentials/CSRF/request-ID transport; it does not read mutable `window.state`, store secrets or introduce a new origin.
- Financial/time mutations are never queued offline. Duplicate clicks are blocked locally, stale reads are rejected by sequence token and HTTP `409` refreshes the authoritative server model before retry.
- Legacy Absence/Time Bank route and modal owners are retired only after Vue readiness. Named Today/Calendar adapters expose commands, not mutable domain state.
- The canonical OpenAPI document and generated TypeScript remain committed and drift-checked. Authentic lockfile, strict typecheck, bundle audit, strict Playwright error collection and the non-root one-image runtime remain enforced.
- No database schema, Flyway migration, cookie, CSP, bearer-token, Telegram-secret or public-edge change is introduced.

## v27.35.7 Docker frontend OpenAPI build-context review

- Scope is Docker build context, test-only Java contracts, release identity and documentation.
- The canonical OpenAPI document is copied read-only from the existing backend source path; no secret or runtime configuration enters the frontend stage.
- Authentic lockfile, strict typecheck, drift checking and the non-root one-image runtime remain enforced.

Status: v27.35.7.

## v27.35.6 historical static-contract alignment review

- Scope is test-only Java source, release identity and documentation.
- No authentication, authorization, session, CSRF, token, secret, CSP or dependency behavior changes.
- The promoted lockfile, pinned image and Q-01–Q-05 gate assertions remain active.

Status: v27.35.6.

## v27.35.4 Java escaping compile-hotfix review

- The change is test-only Java source and release metadata; no runtime request, authentication, authorization, session, CSRF or secret handling changes.
- The authentic npm graph and generated API contract remain immutable.
- Strict frontend, Maven and browser gates remain fail-closed.

Status: v27.35.4.

## v27.35.3 committed-lockfile and fixture review

- The dependency graph is source-controlled with npm registry `resolved` URLs and SHA-512 integrity. Normal builds cannot silently regenerate resolution.
- CI and Docker use the same committed-lockfile `npm ci` boundary and fail on drift.
- The test-only fresh-`Response` change does not alter production request handling, credentials, CSRF or diagnostics.

Status: v27.35.3.

## v27.35.2 authentic-lockfile bootstrap review

- Exact Node/npm and exact direct pins constrain the bootstrap; CI/Docker generate and verify an authentic graph before the same `npm ci` boundary.
- The generated lockfile artifact is uploaded with SHA-256 and contains registry tarballs plus integrity metadata; Gate A remains blocked until that exact artifact is committed.
- OpenAPI generation consumes the committed same-repository contract and writes reviewed source; no runtime generation or remote schema fetch occurs.
- Request IDs are bounded before storage/display and never include session, CSRF or payload data.
- Recovery UI exposes release, public route and correlation ID only; stack traces, response bodies, cookies and tokens are not rendered.
- Global Vue and promise handlers do not suppress `console.error` or browser `unhandledrejection`; strict Playwright collection remains fail-closed.
- Same-origin session and CSRF behavior, CSP, cookies, bearer calendar URLs and Telegram secrets are unchanged.

Status: v27.35.2.

## v27.34.4 secondary navigation and draft-preview review

- Preview accepts a non-persistent calculated draft but create/update keep the positive-credit invariant.
- Secondary active state is presentation-only and exposes no new capability or mutable legacy state.
- API authorization, sessions, CSRF, PostgreSQL and Flyway V47 are unchanged.

## v27.34.3 Vue shell E2E navigation compatibility review

- The hotfix exposes only stable DOM test hooks and reuses already released named navigation/logout capabilities.
- Mutable legacy state is not exposed and no authorization path is moved into Vue.
- Hidden legacy controls are not restored or force-clicked.
- Session, CSRF, owner scope, CSP, API, PostgreSQL and Flyway V47 remain unchanged.

## v27.34.2 Vue browser-runtime bundle security review

- The fix uses compile-time replacement instead of exposing a mutable `process` shim in the browser.
- The generated bundle is rejected if Node/CommonJS runtime globals remain.
- Same-origin sessions, CSRF handling, bridge capabilities and CSP-relevant asset locations are unchanged.
- Playwright still fails on every page error and console error; no security/runtime signal is suppressed.

## v27.34.1 strict Vue type contract security review

- Strict `exactOptionalPropertyTypes` remains enabled; no compiler check is weakened or bypassed.
- The hotfix changes only native presentation attributes and a build-time Vite option.
- No endpoint, session, CSRF, CSP, authorization, persistence or container boundary changes.
- Stable shell CSS remains emitted locally and served same-origin; Flyway remains V47.

## v27.34.0 Vue app shell security review

- Vue owns presentation chrome only; all product authorization, mutation and persisted truth remain on Spring Boot and the existing owner-scoped APIs.
- The shell consumes a frozen read model containing route, allowed navigation, language, network state and safe profile display fields. Mutable `window.state` is never exposed.
- Vue reaches legacy behavior only through named `navigate`, `openModal`, `logout` and `subscribe` capabilities; it does not query or mutate product DOM.
- Same-origin assets, session cookies, CSRF headers and CSP remain unchanged. No CDN, new origin or frontend runtime server is introduced.
- Legacy navigation hides only after a successful Vue readiness event, preserving a fail-safe route to the product if the new bundle cannot boot.
- Vue modal focus handling, escape/overlay close behavior, visible focus and reduced-motion styles are covered by shared primitives.
- Node/Vite remain build-stage tooling; production still contains one non-root Spring Boot JAR image plus PostgreSQL. Flyway remains V47.

## v27.33.0 Vue foundation security review

- Vue assets are served from the existing same origin and remain covered by `script-src 'self'`; no CDN, inline script or new external connection is introduced.
- The typed client keeps `credentials: same-origin` and mirrors Spring's XSRF cookie/header contract for mutating requests.
- Vue receives only explicit bridge capabilities and an immutable diagnostic snapshot; mutable legacy state is not exposed.
- Memory-history routing prevents Vue from competing with the released hash router during migration.
- Node, npm and Vite exist only in build stages. The production image still contains the non-root JRE and one JAR.
- API authorization, session invalidation, owner scoping, PostgreSQL and Flyway V47 are unchanged.

## v27.32.0 absence/time-bank experience review

This release changes presentation and browser navigation only. It does not add a mutation endpoint, weaken linked-usage ownership, expose raw calendar tokens or change database constraints. FIFO forecast is calculated from the authenticated account read model and never persists data. Cross-workspace links use already owner-scoped identifiers and open the existing guarded absence editor. Flyway remains V47.

## v27.32.1 time-bank navigation review

- The hotfix introduces no endpoint, permission or persistence change.
- The linked usage still exposes only an owner-scoped `sourceAbsenceId`; the existing authenticated Vacation API refresh resolves the record.
- FIFO preview remains read-only and edit-aware through `excludePeriodId`; final mutation authority stays in the backend absence service.
- Same-origin session, CSRF, PostgreSQL and Flyway V47 boundaries are unchanged.


## v27.31.2 browser contract alignment review

This hotfix changes browser tests and release identity only. The expected `409 DIRECT_USAGE_RETIRED` probe moves from page `fetch` to the authenticated Playwright API request context, avoiding false console/network alarms without weakening the strict page fixture. Linked usages remain immutable outside their owning absence; no authorization, CSRF, owner scope, FIFO or database constraint changes are introduced.

## v27.31.1 static contract alignment review

This hotfix changes tests and release identity only. Authentication, authorization, ownership, CSRF, closed-period protection, FIFO allocation and database constraints are unchanged. The new guard explicitly prevents restoration of direct linked-usage mutation strings.

## v27.31.0 canonical ownership review

Migration boundary: V47 changes only check constraints, preserves the V42 checksum, performs no data update and does not weaken owner/module/closed-period authorization.

- Direct usage creation/edit endpoints fail closed with explicit `409` codes.
- Legacy migration is owner-scoped and requires both Vacation and Overtime modules.
- Closed accounting periods block promotion.
- Migration reuses the owned usage row and its owned FIFO allocations; foreign IDs are never attached.
- New absence create payloads cannot request transition-only `HOURS_ONLY`.
- Linked usages remain immutable outside their owner absence.


## Payroll Foundation boundaries

Payroll settings, adjustments and snapshots are owner-scoped. Browser writes remain CSRF-protected and period reads use `Cache-Control: no-store`. Monetary values are integers in minor units; no floating-point persistence is used. Final calculation requires a locked closed period plus a healthy ledger. Snapshots are revisioned and not overwritten; the calculation hash contains canonical numeric inputs and adjustment identifiers, never passwords, session identifiers or calendar-feed tokens. The initial release contains no tax or jurisdiction-specific legal formulae.

## Approval and closed-period boundaries

Ledger integrity, accounting periods and factual work are owner-scoped and guarded by the Overtime module. Browser writes remain CSRF-protected. Integrity and factual-work reads use `Cache-Control: no-store`. Closed periods reject ordinary mutations with stable `PERIOD_CLOSED`; corrections are append-only rows rather than hidden rewrites. Linked absence usages remain source-owned and cannot be edited through manual overtime endpoints.


## Unified ledger authorization

Linked overtime usages are owner-scoped and source-owned by their absence. Manual update/delete endpoints reject `ABSENCE` usages with `LINKED_USAGE_MANAGED_BY_ABSENCE`; Vacation Planner is the only mutation route. The unified summary requires the Overtime module, uses owner-scoped repositories, bounded dates and `Cache-Control: no-store`. No salary or financial data is inferred in v27.26.0.


DutyLog is in release stabilization. This document records the security boundaries that are enforced by code and tests in the current release candidate. It is a static review and regression baseline, not a substitute for a live penetration test.

## Authentication boundaries

DutyLog now has two explicit Spring Security chains.

### Browser and shared web API

- Browser users authenticate with `JSESSIONID`.
- State-changing browser requests require `XSRF-TOKEN` / `X-XSRF-TOKEN`.
- Bearer-authenticated mobile clients may use shared endpoints without browser CSRF, but an invalid bearer token is rejected rather than falling back to a browser session.
- Form-login failures, authentication challenges and access denials create structured security events.
- Browser principals include a durable `auth_version`. Password changes, administrative resets, role changes and bootstrap role/password changes increment it; stale `JSESSIONID` sessions are invalidated before the next authorization decision.

### Native mobile API

- `/api/mobile/**` is stateless (`SessionCreationPolicy.STATELESS`).
- A browser `JSESSIONID` never authenticates this path.
- Public endpoints are limited to login, refresh and logout.
- Every other mobile endpoint requires a valid bearer access token.
- `MobileSecurityBoundaryTest` proves that a web session is rejected and a valid bearer token succeeds.

## Authorization and IDOR

- Every user-owned entity is fetched through owner-scoped repositories or an explicit ownership check.
- Cross-user access returns `404`, not `403`, to avoid confirming that a foreign resource exists.
- `OwnershipIsolationTest` checks the exact `404` status for tasks, quick scenarios, important dates, shift types, overtime credits/usages and mobile sessions.
- `/api/admin/**` remains protected by both `hasRole("ADMIN")` and sensitive-operation service checks.
- Disabled modules guard backend writes, including aggregated mobile sync.


## External calendar subscription

- Subscription tokens contain 256 random bits and are accepted only in URL-safe Base64 form.
- The raw bearer token is shown only after issue/rotation; the database stores SHA-256 plus a non-secret hint.
- Rotation invalidates the old digest atomically; revocation deletes the credential without touching calendar data.
- The public feed is read-only, owner-scoped and returns 404 for malformed, unknown, revoked or module-disabled tokens.
- Application diagnostics log the path but never the token query value; security audit events log only the hint. The supplied nginx examples disable access logging for the exact `/calendar-feed.ics` location, and every active Certbot-managed HTTP/HTTPS server block must retain that exception before subscriptions are enabled.
- Feed and exports use `Cache-Control: no-store`, bounded date/event/byte limits and no OAuth credentials.

## Notes export

`GET /api/export/notes` is an authenticated data-portability endpoint.

- Querying is owner-scoped in the database.
- Foreign notes are covered by a regression test.
- The archive is streamed instead of first being materialized as one large `byte[]`.
- A conservative database count is checked before loading export rows.
- Note count and uncompressed byte limits are configurable.
- ZIP paths are generated only from `LocalDate` values.
- YAML front matter is escaped for quotes, backslashes and control characters.
- Responses use `Cache-Control: no-store` and related no-cache headers.
- Export remains available when the Notes UI module is disabled: disabling a module does not remove the user's right to retrieve stored data.

## Browser policy

The login runtime was moved to `/js/login.js`. The CSP no longer permits inline scripts:

```text
default-src 'self';
script-src 'self';
style-src 'self' 'unsafe-inline';
img-src 'self' data:;
connect-src 'self';
manifest-src 'self';
base-uri 'self';
frame-ancestors 'self';
form-action 'self'
```

The same baseline headers are present in Spring, Caddy and nginx. HSTS is emitted for HTTPS traffic.

`style-src 'unsafe-inline'` remains because the current UI still uses inline styles. Removing it is a future refactor, not a release blocker. `script-src 'unsafe-inline'` is no longer present.

## Brute-force and registration defaults

- Production public registration defaults to closed.
- Authentication entry points are rate-limited in the application, so stock Caddy and nginx deployments receive the same protection.
- nginx keeps an optional second rate-limit layer as defense in depth.
- The limiter is intentionally single-instance. A future multi-instance deployment must use a shared gateway/Redis limiter.
- Normal public-registration and profile-change passwords require at least 8 characters; administrators require 12 on profile/admin reset paths; bootstrap admin passwords require at least 20.
- Forwarding headers are ignored unless `dutylog.security.trust-proxy-headers=true`. The supplied nginx/Caddy edge configurations overwrite `X-Real-IP` and `X-Forwarded-For`, preventing a client from choosing its own rate-limit/audit bucket.

## Security logging

`SECURITY_AUDIT` emits sanitized key-value events such as:

- `AUTH_LOGIN_FAILED`
- `AUTH_REQUIRED`
- `AUTH_ACCESS_DENIED`
- `AUTH_TOKEN_REJECTED`
- `AUTH_RATE_LIMITED`
- `AUTHZ_OWNERSHIP_MISMATCH`
- `TELEGRAM_LINK_REJECTED`
- `MOBILE_TOKEN_REVOKED`
- `ADMIN_ROLE_CHANGED`
- `ADMIN_PASSWORD_RESET`
- `DATA_EXPORT_NOTES`
- `DATA_EXPORT_CALENDAR`
- `DATA_EXPORT_CALENDAR_EVENT`
- `CALENDAR_FEED_ROTATED`
- `CALENDAR_FEED_REVOKED`

Fields include event type, result, username, source IP, method, path and request ID. Passwords, bearer/refresh tokens, Telegram link codes and note contents are never logged. Production writes stdout plus a bounded rolling file in the `app_logs` Docker volume.

## Backups and token retention

- PostgreSQL custom-format backups are verified with `pg_restore --list` and optional SHA-256 sidecars.
- Backup/restore scripts run with `umask 077`; backup directories are `0700`, dumps and checksum files are `0600`.
- Mobile access/refresh tokens remain stored only as SHA-256 hashes.
- Rows whose refresh expiry is older than the configured retention period are deleted by scheduled maintenance, preventing unbounded token-table growth.

## Supply-chain baseline

- Maven, GitHub Actions and Docker Dependabot updates are enabled.
- Runtime frontend assets are self-hosted; no CDN scripts are loaded.
- The application container runs as UID/GID `10001`, not root.
- CI runs Maven tests and static release checks on every push and pull request.

Action commit-SHA and Docker image digest pinning are deliberately not fabricated in this offline review. They should be added only after verifying current trusted SHAs/digests from upstream sources. Dependabot now provides the update path meanwhile.

## Residual risks and launch work

This review was performed against the source tree and existing CI artifacts. Live dependency/CVE intelligence and an Internet-facing penetration test were not available in the audit environment, so “no known vulnerabilities” is **not** claimed.

Before a public production launch:

- run live dependency/container/SCA scans and review every finding against the exact release digest;
- perform DAST/pentest against staging, including login throttling, session invalidation, CSRF, IDOR and reverse-proxy behavior;
- copy encrypted backups off the VPS and test a restore from that offsite copy;
- remember that membership in the host Docker group is effectively root-equivalent; use a dedicated deployment host/user or a narrowly restricted deployment mechanism when stronger isolation from other projects is required;
- pin verified GitHub Action commits and Docker image digests after checking current upstream releases online;
- keep the current limiter single-instance, or move rate limiting to a shared gateway/Redis before horizontal scaling;
- investigate concurrent duplicate Android `operationId` submissions under real PostgreSQL load; the unique constraint protects data, but a race still needs a dedicated stress/integration proof;
- replace in-memory admin listing with database pagination before the user count becomes large;
- remove inline styles and `style-src 'unsafe-inline'` in a future UI refactor;
- consider MFA/account lockout and alert routing if DutyLog becomes a broader public service.
