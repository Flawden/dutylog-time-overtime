# Security review

Status: v26.6.10.

DutyLog is in release stabilization. This review is not a feature roadmap; it is a guardrail document for keeping the existing product safe enough to publish and operate.

## Security posture in v26.5

### Authentication

- Browser users authenticate with Spring Security session cookies.
- Mobile clients authenticate with explicit bearer access tokens and refresh-token rotation.
- `/api/mobile/**` remains CSRF-exempt because browsers do not attach `Authorization: Bearer ...` automatically.
- Browser-changing requests remain CSRF-protected through `XSRF-TOKEN` + `X-XSRF-TOKEN`.

### Authorization

- `/api/admin/**` is protected twice:
  - declarative Spring Security `hasRole("ADMIN")` matcher;
  - controller/service-level admin checks for sensitive operations.
- User data access stays owner-scoped in services and repositories.
- Module switches are not just UI switches. Disabled modules must also guard their API boundaries.

### Module-boundary fixes in v26.5

Two release-review gaps were closed:

1. `/api/mobile/sync` could update note/overtime fields through the aggregated mobile endpoint even if the corresponding modules were disabled. It now checks:
   - `notes` before writing or clearing day notes;
   - `overtime` before writing overtime/time-off fields.
2. A pending Telegram link code could theoretically be used after the user disabled the Telegram module. `TelegramLinkService.linkByCode(...)` now requires the owner's Telegram module to still be enabled before linking.

### Browser headers

`SecurityHeadersFilter` sets a baseline policy from the app itself:

- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `X-Frame-Options: SAMEORIGIN`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- `Content-Security-Policy` limited to self-hosted app assets, with temporary `unsafe-inline` for the current inline login script and inline styles
- `Strict-Transport-Security` when the request is HTTPS or arrives through an HTTPS reverse proxy

The reverse-proxy examples still set the same edge headers. The application-level filter is defense in depth.

### Session cookies

Production now explicitly sets:

```properties
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.timeout=7d
```

Local dev keeps `HttpOnly` and `SameSite=Lax`, but does not force `Secure` so `http://localhost:8080` continues to work.

## Regression tests added

`ModuleSecurityTest` covers:

- mobile sync cannot write notes when `notes` is disabled;
- mobile sync cannot write overtime when `overtime` is disabled;
- baseline browser security headers are present.

`release-check.sh` now also checks that the security review guardrails are still wired.

## Deferred hardening after 1.0

These are intentionally deferred unless a concrete issue appears during release testing:

- remove inline login script/styles and tighten CSP by dropping `unsafe-inline`;
- add app-level login throttling if deployment uses Caddy without nginx/fail2ban/plugin rate limiting;
- add audit log for admin actions;
- add optional backup encryption at rest;
- add structured security events for failed logins and token revocations.

Do not add these as new product features during the current freeze unless they are required to fix a release-blocking security bug.
