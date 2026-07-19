# Authenticated deployment smoke-test hotfix — v27.2.31

## Incident

The first live staging deployment successfully pulled the immutable image, started PostgreSQL, applied Flyway V1–V23 and reached healthy application status. The deployment gate then failed because `smoke-test.sh` requested the protected `/` shell anonymously and `curl` received `401`.

## Root cause

DutyLog intentionally distinguishes browser navigation from API-style anonymous requests. A browser request carrying `Accept: text/html` is redirected to `/login.html`; a generic client may receive a JSON `401`. The old smoke test used generic curl headers and incorrectly treated the protected shell as a public resource.

## Fix

The v27.2.31 smoke test now:

1. verifies `/actuator/health`;
2. loads `/login.html` and captures the `XSRF-TOKEN` cookie;
3. verifies that an HTML request to `/` redirects to `/login.html`;
4. signs in through the CSRF-protected `/perform_login` endpoint using the deployment environment administrator;
5. keeps cookies and credential staging files inside a mode-0700 temporary directory;
6. downloads and validates the versioned app shell only after authentication;
7. still verifies public PWA assets and protected API behavior.

Deployment and rollback paths set `DUTYLOG_SMOKE_REQUIRE_AUTH=true`, so missing or rejected credentials fail closed. The regression harness `deploy/scripts/smoke-test-regression.py` verifies successful login, rejection of an invalid password and absence of password leakage in output.

No database migration or application feature behavior changed.
