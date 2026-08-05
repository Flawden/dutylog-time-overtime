# v27.36.6 — Time Bank Usage-Date Chart Parity Hotfix

## Trigger

The exact v27.36.5 staging Chromium run passed 44 of 45 scenarios. The remaining `overtime-next.spec.js` assertion found no chart column for the actual usage day, even though the backend had created the usage successfully.

## Root cause

`ledgerChartColumns()` grouped both earned and used values through `account.credits`. `credit.usedHours` is an aggregate allocation total attached to the source credit; it is not the date of the usage event. A usage recorded on `2026-08-03` against credits earned on `2026-08-01` and `2026-08-02` therefore appeared only on the credit dates and never created the required `2026-08-03` column.

## Fix

- Earned chart values are grouped from `credit.workedDate` and `credit.hours`.
- Used chart values are grouped independently from `usage.usageDate` and `usage.hours`.
- `credit.usedHours` is intentionally excluded from chart aggregation to avoid double counting.
- Month mode keeps distinct daily buckets; year mode folds both event kinds into `YYYY-MM` buckets.

## Regression example

```text
2026-08-01  +3 / −0
2026-08-02  +2 / −0
2026-08-03  +0 / −4

Year bucket:
2026-08     +5 / −4
```

Two Vitest cases bind daily usage-date ownership and yearly month folding. A compile-gated Java hotfix contract verifies the two independent source loops, forbids `credit.usedHours` inside `ledgerChartColumns()` and preserves the strict Playwright usage-day locator.

## Scope

No Spring Boot, OpenAPI, PostgreSQL or Flyway change. FIFO allocation, balance arithmetic, absence ownership, generated transport, npm graph, CI routing and Playwright expectations remain unchanged.

## Acceptance

- exact Node/npm frontend gate;
- Maven verify and JaCoCo thresholds;
- all 45 Chromium scenarios;
- immutable image clean-PostgreSQL smoke;
- staging deployment.
