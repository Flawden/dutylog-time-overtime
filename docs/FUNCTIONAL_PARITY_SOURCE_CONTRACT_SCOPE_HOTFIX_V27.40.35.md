# v27.40.35 — Functional Parity Source Contract Scope Hotfix

## CI evidence

The v27.40.34 exact frontend stage passed. Java 17 Maven then executed 790 tests and reported exactly two failures: `FunctionalParitySweepIIMobileUsabilityContractTest.monthCalendarUsesShiftColorAsScannableDataOnMobile` and `TimeBankUsageDateChartParityHotfixTest.earnedSeriesUsesCanonicalProjectedDayTotals`.

## Root cause

Both failures were stale source-contract scope assumptions rather than runtime regressions. The calendar runtime already stores `facts.shift?.color` in a local `shiftColor` helper before assigning `--shift-color`, while the test demanded the older direct inline assignment. Canonical overtime `serverProjection?.dayEarnedHours` authority lives inside `dayCreditTotals`; `ledgerChartColumns` intentionally consumes the helper output and therefore does not contain that internal expression.

## Fix

The calendar contract now asserts the `shiftColor` helper plus CSS-variable assignment. The overtime contract separately inspects `dayCreditTotals` for server projection authority and `ledgerChartColumns` for day-total consumption. Runtime Vue/TypeScript, HTTP/OpenAPI, PostgreSQL/Flyway, authentication, offline sync and onboarding behavior are unchanged.
