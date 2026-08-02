# Security review

Status: v27.28.1.


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
