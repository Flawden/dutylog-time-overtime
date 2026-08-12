# DutyLog v27.40.21 — Vue Payroll Workspace Retirement

## Goal

Retire Payroll as a live legacy UI/domain owner after Vue became authoritative for shell routing, guards and route-entry freshness.

## Ownership cut

- `PayrollWorkspace.vue` owns the `payroll` route and preserves the stable Payroll DOM IDs used by browser acceptance.
- `payrollStore.ts` owns Payroll read/loading/error state.
- `payrollApi.ts` uses only generated OpenAPI operation IDs: `payrollPeriod`, `updatePayrollSettings`, `addPayrollAdjustment`, and `calculatePayrollRevision`.
- `DutyLogVueDomains.payroll` exposes only `ready()` and `refresh()` as the public browser-domain boundary.
- The legacy `view-payroll` section, `45-payroll.js`, `state.payrollPeriod`, `state.payrollLoading`, and legacy `api.payroll*` helpers are retired.
- Post-Vue legacy route effects are now Admin-only. The pre-Vue router no longer invokes a retired Payroll owner.

## Compatibility and parity

The Vue workspace preserves the existing Payroll user contract: month selection, period/integrity status, time and money summary, rate/currency settings, closed-period adjustments, immutable revision calculation/history, existing CSS classes and stable `#payroll*` selectors.

The existing Playwright Payroll Foundation scenario is deliberately retained rather than replaced. Its readiness helper now waits on `DutyLogVueDomains.payroll.ready()`.

## Architecture constraints

- Spring Boot remains authoritative for closed-period eligibility, ledger integrity, canonical paid time, rounding and immutable snapshots.
- Vue performs presentation formatting only and does not reinterpret calendar/ledger business facts.
- No new offline mutation queue is introduced; Payroll remains online/server-authoritative in this release.
- OpenAPI remains 118 operations / 120 schemas with hash `91b48b10fa56`.
- Flyway remains V47.

## Acceptance surface

- Java test classes: 158
- JUnit `@Test`: 769
- Playwright scenarios: 48
- Vitest cases: 58
