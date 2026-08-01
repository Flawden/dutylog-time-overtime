# v27.27.1 — Ledger Workflow Browser Contract Hotfix

GitHub Actions compiled the application, completed all 598 Maven tests and then reported six Playwright failures out of 36 scenarios. The failures shared browser-contract and read-model freshness causes rather than six unrelated ledger defects.

## Runtime corrections

- Entering the Overtime view now refreshes the account and all ledger projections, so absence changes made while the view is hidden cannot leave stale Plan → Fact → Compensation totals on screen.
- Ledger integrity refreshes are serialized. Integrity reconciliation is never started in parallel with Time Compensation, which performs the same inspection and may repair FIFO allocations.
- A bounded browser promise exposes route and read-model readiness to E2E without bypassing application state.

## E2E contract corrections

- The strict runtime fixture still fails every unexpected same-origin HTTP error, but an explicitly marked expected status can be asserted without becoming a teardown regression. The closed-period scenario marks only its intentional `409 PERIOD_CLOSED` request.
- Timezone projection dates are derived from the browser's current month instead of being fixed to July 2026.
- Absence and Unified Ledger scenarios explicitly create `APPROVED` absences when they assert posted/used compensation.
- Reload scenarios wait for application background reads to settle before navigation.
- Overtime scenarios use the shared route and ledger-readiness helpers instead of direct tab clicks plus internal function calls.

## Compatibility boundary

Production API, OpenAPI, PostgreSQL schema and Flyway V44 are unchanged. The hotfix adds no payroll rules and does not weaken the strict browser fixture. Baseline: 113 Java test classes, 599 `@Test` methods and 36 Playwright scenarios.
