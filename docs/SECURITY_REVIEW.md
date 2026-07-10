# Security review

Status: v27.0-rc4.

DutyLog is in release stabilization. This document records the security boundaries that are enforced by code and tests in the current release candidate. It is a static review and regression baseline, not a substitute for a live penetration test.

## Authentication boundaries

DutyLog now has two explicit Spring Security chains.

### Browser and shared web API

- Browser users authenticate with `JSESSIONID`.
- State-changing browser requests require `XSRF-TOKEN` / `X-XSRF-TOKEN`.
- Bearer-authenticated mobile clients may use shared endpoints without browser CSRF, but an invalid bearer token is rejected rather than falling back to a browser session.
- Form-login failures, authentication challenges and access denials create structured security events.

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
- Normal public-registration passwords require at least 8 characters; bootstrap admin passwords require at least 20.

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

Fields include event type, result, username, source IP, method, path and request ID. Passwords, bearer/refresh tokens, Telegram link codes and note contents are never logged. Production writes stdout plus a bounded rolling file in the `app_logs` Docker volume.

## Supply-chain baseline

- Maven, GitHub Actions and Docker Dependabot updates are enabled.
- Runtime frontend assets are self-hosted; no CDN scripts are loaded.
- The application container runs as UID/GID `10001`, not root.
- CI runs Maven tests and static release checks on every push and pull request.

Action commit-SHA and Docker image digest pinning are deliberately not fabricated in this offline review. They should be added only after verifying current trusted SHAs/digests from upstream sources. Dependabot now provides the update path meanwhile.

## Remaining non-blocking work

- live DAST/pentest against the deployed server;
- immutable SHA/digest pinning after online verification;
- shared rate limiting if more than one app instance is deployed;
- removal of inline styles and `style-src 'unsafe-inline'`;
- MFA/account lockout if DutyLog grows beyond a small trusted deployment;
- operational alert routing for repeated `SECURITY_AUDIT` warnings.
