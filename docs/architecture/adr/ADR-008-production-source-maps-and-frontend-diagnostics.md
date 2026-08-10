# ADR-008 — Production source maps and frontend diagnostics

- Status: accepted
- Release: v27.39.0
- Date: 2026-08-10

## Context

DutyLog needs enough frontend diagnostics to recover from Vue/PWA failures without publishing implementation details or secrets with every production image. The existing controlled recovery surface records release, route, request ID and sanitized request metadata; it deliberately does not expose stack traces, response bodies, bearer URLs or tokens.

Vite previously emitted public source maps for every production bundle. That made debugging convenient, but it also published a complete mapping from the minified bundle back to application source even when no incident required it.

## Decision

1. Production frontend source maps are disabled by default.
2. A release operator may explicitly build hidden source maps with `DUTYLOG_FRONTEND_SOURCEMAPS=true` for a controlled diagnostic build.
3. Hidden maps are build artifacts only. The application HTML, service worker and runtime must never link to or advertise a `.map` file.
4. Controlled runtime diagnostics remain the primary production support surface: release, route, bounded request ID, sanitized method/path/status and safe failure metadata.
5. Query strings, fragments, calendar subscription bearer URLs, Telegram link codes, cookies, CSRF values, response bodies and stack traces are never included in the browser diagnostic snapshot.
6. Strict browser error collection remains enabled; this ADR does not suppress `console.error`, `pageerror`, rejected promises or failing HTTP responses.
7. If hidden maps are retained for an incident, they belong in access-controlled CI/operator storage with the same retention discipline as other diagnostic artifacts; they are not deployed with public static assets.

## Consequences

- Normal production images do not publish source maps.
- Support can still correlate a user-visible failure to release/request metadata without exposing secrets.
- A deliberately created diagnostic build can retain hidden maps without changing runtime behavior or public asset references.
- Security review must continue checking CSP, cookie flags, integration-secret handling and accidental `.map` publication independently of this ADR.
