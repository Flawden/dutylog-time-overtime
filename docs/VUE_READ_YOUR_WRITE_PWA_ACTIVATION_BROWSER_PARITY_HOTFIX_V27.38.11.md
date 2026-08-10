# v27.38.11 — Vue Read-Your-Write & PWA Activation Browser Parity Hotfix

## Evidence

The completed v27.38.10 Chromium run reached **42 passed / 5 failed in 11.1 minutes with no flaky retry**. The remaining failing scenarios were `editor-modals`, `multiple-daily-notes`, `pwa-upgrade`, `task-modules`, and `tasks-inbox-next`.

## Root causes and corrections

### Task read-your-write projection sequencing

A successful Task create/update already returned the backend-authoritative DTO. v27.38.10 published it before projection refreshes and again after `Promise.all`, but an accepted selected-day or Board read could still replace Vue state in the middle of that window. v27.38.11 stages the committed DTO in a short-lived read-your-write overlay. `loadSelectedDate()` and `loadBoard()` merge staged writes before exposing accepted projection state; the overlay is removed only after the save refresh window settles.

This does not create frontend business authority. Board filters/order remain backend-owned, and the existing narrow default open/unfiltered admission rule is unchanged.

### Multiple Notes reload ownership

Note PATCH remains intentionally routed through the existing bounded offline adapter at `/api/notes/{id}` and DELETE remains generated `/api/v1/notes/{id}`. The remaining reload wait was still listening for legacy `/api/calendar`; it now waits for the Vue-generated `/api/v1/calendar` Calendar owner.

### First-install PWA activation

Service-worker registration already starts installation for a new registration. Calling `registration.update()` immediately on that same first registration can create a duplicate install/update lifecycle while onboarding hands control to the worker. v27.38.11 checks for a pre-existing registration before registering and forces `registration.update()` only for established registrations. Existing controlled-page upgrade behavior and cache cleanup remain intact.

## Invariants

- No Playwright timeout, retry, assertion, console/pageerror rule, or HTTP failure policy is weakened.
- No API/OpenAPI shape, backend business rule, PostgreSQL schema, or Flyway migration changes.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47.
- OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Acceptance requires exact frontend, Maven 751/751, boot canary, clean 47/47 Chromium with no flaky scenario, immutable image, clean PostgreSQL and staging.
