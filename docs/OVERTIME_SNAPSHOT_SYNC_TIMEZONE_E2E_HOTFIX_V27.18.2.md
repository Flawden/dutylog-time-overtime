# Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix — v27.18.2

## Problem confirmed by CI

The v27.18.1 browser run reached the real Overtime Next state split:

```text
account-page summary → totalUsedHours = 4
state.overtimeAccount.usages → stale empty array
chart → −0 h
```

The same run also exposed a flaky timezone scenario: the test clicked a day that was already selected, so the calendar toggle intentionally closed the day panel and hid `#shiftProjection`.

## Runtime fix

`GET /api/overtime/account-page` now returns:

```text
summary totals
+ paged credit rows
+ full canonical usage rows
```

The full usage list is intentional. Rebuilding usages from the current credit page is unsafe because one usage can be allocated across several credits and several pages.

`loadLedgerPage()` creates the next account snapshot and replaces totals plus `usages` before rendering. The overview, chart, FIFO details and responsive ledger therefore observe the same server projection.

## E2E stabilization

The shared `selectDate(page, date)` helper is now idempotent. It preserves an already selected visible day instead of toggling it off. The timezone scenario captures the exact `data-date` used for the shift and reopens that identity after the profile/calendar refresh.

## Compatibility

- HTTP endpoint remains additive: existing fields are unchanged and `usages` is added to `account-page`.
- Overtime minute accounting and FIFO allocation rules are unchanged.
- No Flyway migration; schema remains V36.
- Baseline remains 96 Java test classes, 500 `@Test` methods and 26 Playwright scenarios.
