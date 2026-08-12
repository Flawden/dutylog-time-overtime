# v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement

## Purpose

Finish the route-authority sequence started in v27.40.13–v27.40.16. Vue already owns hash state, route guards and migrated route-entry freshness; v27.40.17 removes the remaining live post-Vue `hashchange -> applyRoute()` competition.

## Ownership

- `hashRoute.ts` remains the canonical Vue hash reader/writer/subscriber and now publishes `dutylog:vue-route-committed` after guard resolution.
- `App.vue` deduplicates identical committed route snapshots before publication.
- `70-user-boot.js` removes its `hashchange` listener at `dutylog:vue-ready`.
- The remaining legacy route adapter consumes committed Vue routes only for Payroll and Admin view/refresh side effects.
- The complete historical `applyRoute()` branch remains available before Vue readiness as recovery.

## Cutover safety

The pre-Vue active route is remembered. When Vue becomes ready, Payroll/Admin effects are not repeated if the canonical Vue route is unchanged; if Vue guards canonicalize the route, only the new canonical route is applied.

## Non-goals

No Payroll/Admin UI migration is attempted here. No API, database, Flyway, strict-TypeScript, retry/timeout or offline ownership rule changes. The existing legacy `dataLayer` remains the sole offline mutation/reconnect owner.

## Acceptance

Target: Node 20.18.1/npm 10.8.2 frontend gate, Java 17 Maven 766/766, Playwright canary and full 48/48 Chromium with zero flaky, immutable image and PostgreSQL V47 staging smoke.
