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
