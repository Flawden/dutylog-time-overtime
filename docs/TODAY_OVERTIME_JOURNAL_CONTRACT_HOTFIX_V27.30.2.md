# v27.30.2 — Today Overtime Journal Contract Hotfix

## CI diagnosis

GitHub Actions executed the v27.30.1 Maven suite and stopped at one stale source contract:

- `TodayDashboardFrontendContractTest.dashboardComposesExistingShiftOvertimeTaskAndImportantDateStores`;
- the assertion still required `openOvertimeCreditModal` inside `35-today.js`;
- v27.30.1 intentionally replaced the Today direct credit shortcut and made the card action **Журнал**, which routes to `#overtime`.

The production handler was already correct. Credit creation remains available from global Quick Add and the Overtime workspace.

## Fix

- Update the historical Today Dashboard contract to require the journal route instead of the removed direct modal call.
- Add `TodayOvertimeJournalContractHotfixTest` to protect the complete boundary:
  - Today card label is **Журнал**;
  - Today routes to `#overtime`;
  - `35-today.js` does not invoke the credit modal;
  - `40-overtime.js` still owns `openOvertimeCreditModal` and its legitimate entry points.

## Non-goals

No production JavaScript behavior, API, database schema, Flyway migration, Payroll, Ledger, Vacation or calendar-projection logic changes. Flyway remains V46.

## Regression baseline

- 125 Java test classes;
- 620 `@Test` methods;
- 40 Playwright scenarios;
- Flyway V46.
