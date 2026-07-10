# v27.0-rc4 security consolidation

Status: v27.0-rc4.

This release candidate consolidates the independent OWASP/code reviews and the new Markdown notes export without expanding product scope.

## Closed in code

- repaired and strengthened IDOR regressions; assertions now require exact `404` responses;
- separated stateless bearer-only `/api/mobile/**` from browser session authentication;
- added owner-scoped, bounded, streamed notes export with cache prevention and YAML escaping;
- made production registration closed by default;
- added application-level authentication rate limiting for Caddy/nginx parity;
- added structured security-event logging and rolling production logs;
- removed inline login JavaScript and dropped `script-src 'unsafe-inline'` from CSP;
- aligned HSTS/CSP headers across Spring, Caddy and nginx;
- raised normal registration password minimum to 8 characters;
- added Dependabot for Maven, Actions and Docker;
- changed the application container to a non-root runtime user;
- synchronized version metadata and RC documentation.

## Regression coverage

The RC adds or extends tests for:

- cross-user ownership isolation;
- mobile session vs bearer-token boundary;
- module guards through mobile sync;
- notes export ownership, cache headers, YAML escaping and resource caps;
- authentication rate limiting;
- registration language and password behavior;
- CSP without inline-script permission.

## Operational note

The built-in limiter is designed for the current single application instance. Deployments with multiple app replicas must move rate limiting to a shared gateway or data store.
