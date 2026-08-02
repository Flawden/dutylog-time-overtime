# v27.28.2 — Calendar Persistence Reload Readiness Hotfix

## Failure observed in CI

Maven completed successfully and 36 of 37 Playwright scenarios passed. The remaining `calendar-persistence` scenario reloaded immediately after the `/api/calendar` response, while the same navigation was still completing its Overtime ledger projections. Chromium cancelled those obsolete reads and the strict fixture correctly reported the resulting `Failed to fetch` console errors.

## Fix

- Month, Week and Day calendar navigation publish `window.__dutylogCalendarNavigationReady`.
- The E2E helper waits for that promise, the ledger read-model promise and the existing application-idle/network-idle contract.
- Both intentional reload paths in `calendar-persistence.spec.js` wait for idle before destroying the page.
- No console error, request failure or HTTP status is globally ignored.

## Scope boundary

This is a browser-readiness contract hotfix only. Payroll runtime, API, OpenAPI, database schema and Flyway V45 are unchanged.
