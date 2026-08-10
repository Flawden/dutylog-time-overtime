# v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix

## Evidence

The exact v27.38.8 Chromium run completed all 47 scenarios in about 15 minutes with 37 passed and 10 failed. The former shared Calendar selection cascade is gone.

## Root causes

1. Several E2E waits still observed retired legacy read/write URLs even though Vue Calendar, Important Days and Calendar Layers use generated `/api/v1/*` operations.
2. Multiple Daily Notes deliberately updates through the bounded legacy `/api/notes/{id}` offline adapter; its E2E wait had incorrectly moved to generated `/api/v1/notes/{id}`.
3. An authenticated offline reload restores cached modules/calendar data but cannot reload `/api/profile`; the v27.38.7 online onboarding guard therefore prevented cached Notes/Tasks/Important Vue islands from mounting.
4. Registering the current service worker on the login page can make the initial install/claim overlap the first authenticated onboarding lifecycle.
5. A successful Task mutation can be followed by overlapping read-model refreshes. The mutation response is already backend-authoritative and must remain visible while those reads settle.
6. Task description editing lives in an intentionally collapsed Advanced `<details>` section; browser acceptance must open that UI before filling the field.

## Invariants

- Backend remains source of truth; no frontend business calculation is introduced.
- Generated `/api/v1/*` transport remains the Vue owner except the already-approved offline note/task/inbox bridge aliases.
- Offline readiness is permitted only when the cached module map is loaded and the browser is actually offline; online first-run still requires completed onboarding.
- No Playwright timeout, retry, page-error collection or HTTP failure allowlist is weakened.
- No OpenAPI, PostgreSQL or Flyway change.
