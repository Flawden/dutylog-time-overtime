# v27.38.5 — Windows Frontend Gate & Calendar Contract Alignment Hotfix

## Evidence

The first local v27.38.4 Windows run exposed two independent gate defects before browser acceptance:

1. `deploy/scripts/frontend-gate.ps1` resolved bare `npm` to `C:\Program Files\nodejs\npm.ps1`. Under `Set-StrictMode -Version Latest`, npm's wrapper reads `$MyInvocation.Statement` and throws `PropertyNotFoundStrict` before DutyLog can validate the pinned toolchain.
2. `mvn verify` executed all 751 tests and stopped with exactly two failures / zero errors, both in `VueCalendarTimelineMigrationFrontendContractTest`. The production schedule-layer wrapper correctly yields when `data-vue-calendar-timeline === "ready"`, while the test expected the inverse spelling. The cross-midnight coverage test used a Latin `N` in `Nочная задача`, while the fixture contains Cyrillic `Н`.

## Fix

- Resolve `npm.cmd` explicitly and reuse that executable for every Windows frontend-gate npm invocation.
- Lock the actual Vue-ownership early-return invariant for schedule layers.
- Lock cross-midnight task coverage through `scheduledEndDate` and next-day `dayFacts(...)` behavior rather than a label literal.
- Extend the existing executable-resolution Java contract; do not increase test counts or weaken browser assertions.

## Acceptance

Full 47/47 Chromium remains required before acceptance. Run the exact frontend gate first, then Maven 751/751, then `npm run test:e2e:canary`, targeted browser canaries, the complete Playwright suite, immutable image/clean PostgreSQL smoke and staging.
