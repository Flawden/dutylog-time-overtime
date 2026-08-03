# v27.30.1 — Unified Absence Quick Access Integration

This release completes the user-facing entry points for the unified absence composer.

## Functional entry points

- The global plus menu exposes **Оформить отсутствие** as a neutral action owned by the Vacation/Absence module, not by Overtime.
- The Today dashboard exposes a direct **Оформить отсутствие** action for the current date.
- The Today shortcut replaces the less important direct overtime-credit shortcut; adding overtime remains available from the global plus menu and Overtime workspace.
- The Overtime workspace remains contextual and opens the same composer with `TIME_OFF` and `OVERTIME_BANK` preselected.
- Quick Add focus falls back to the absence action when Vacation is enabled but draft and Overtime actions are unavailable.

## Non-goals

No visual redesign, animation, database migration, API change, payroll change, ledger change or calendar-projection change is included. Visual polish remains a later dedicated pass.

## Regression coverage

- `UnifiedAbsenceQuickAccessFrontendContractTest` protects the global, Today and Overtime entry-point boundaries.
- `today-dashboard.spec.js` opens the neutral composer directly from Today and verifies the global plus action remains available.
- Baseline: 124 Java test classes / 619 `@Test` methods / 40 Playwright scenarios.
- Flyway remains V46.
