# v27.36.7 — Time Bank Period Toggle Snapshot Stability Hotfix

## Trigger

The exact v27.36.6 staging run remained at 44/45 Chromium. The yearly chart already contained the usage, but switching to month mode caused the daily usage column to disappear.

## Root cause

`setRangeMode()` called the full `refresh()` path. That request reloaded planner, overtime account, period summaries and scenarios even though only the period-dependent projections needed a new range. The canonical account snapshot was therefore replaced during a presentation-only toggle, creating an avoidable race at the last browser assertion.

## Fix

The period-only loader keeps presentation toggles separate from canonical account refreshes.

- Add `loadPeriod()` for time-compensation, ledger-integrity and actual-work range data.
- Keep `account`, `planner` and scenarios untouched during month/year toggles.
- Use one shared read sequence for full and period-only loads.
- Let a newer toggle or full refresh invalidate every older read response.
- Keep full refresh available for explicit refreshes and mutations.

## Regression evidence

Vitest locks:
- immediate period ownership without account replacement;
- newest month/year response wins;
- later full refresh supersedes in-flight period-only work.

A compile-gated Java contract verifies that the period-only API path does not request `overtimeAccount`, the store does not assign `this.account` in `setRangeMode()`, and the strict Chromium locator remains unchanged.

## Scope

No Spring Boot, OpenAPI, generated transport, PostgreSQL or Flyway change. FIFO arithmetic, balance ownership, chart bucketing, CI routing and Playwright expectations remain unchanged.

## Acceptance

- exact Node/npm frontend gate;
- Maven verify and JaCoCo thresholds;
- all 45 Chromium scenarios;
- immutable image clean-PostgreSQL smoke;
- staging deployment.
