# v27.27.2 — Ledger Browser State & Visibility Hotfix

GitHub Actions compiled the application, completed all 599 Maven tests and then reported five Playwright failures out of 36 scenarios. The failures were caused by two stale browser read models and three E2E contracts around responsive visibility, month boundaries and expected HTTP errors.

## Runtime corrections

- Entering Vacation Planner now refreshes its server projection even when a cached planner snapshot exists. Overtime credits created while Vacation is hidden are reflected in the available compensatory-time balance immediately.
- Vacation route loading exposes a bounded readiness promise used by browser scenarios without bypassing the real route.
- Timezone saves expose their full background refresh lifecycle. Reloads wait until calendar, tasks, ledger and notification projections finish instead of aborting an in-flight request.

## Browser-contract corrections

- Explicitly marked expected statuses receive a one-use console budget for Chromium's generic `Failed to load resource` message. Unmarked same-origin HTTP failures, page errors and console errors remain strict failures.
- Overtime Next uses stable dates inside the current month, so the `This month` filter is valid on the first day of a month.
- Unified Ledger assertions target the visible desktop table rather than the hidden mobile-card duplicate.

## Compatibility boundary

Production API, OpenAPI, PostgreSQL schema and Flyway V44 are unchanged. Approval workflow, reservations, postings, closed periods and append-only audit semantics are unchanged. Baseline: 114 Java test classes, 600 `@Test` methods and 36 Playwright scenarios.
