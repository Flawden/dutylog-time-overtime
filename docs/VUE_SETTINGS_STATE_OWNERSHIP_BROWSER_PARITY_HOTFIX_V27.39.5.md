# v27.39.5 — Vue Settings State Ownership Browser Parity Hotfix

## Evidence

The complete v27.39.4 Playwright report contains 48 scenarios: 42 passed and 6 failed. The failures reduce to three root causes:

1. Four `task-modules` scenarios call `toggleModule(..., true)` while Tasks is already checked. Playwright `check()` correctly becomes a no-op, so no `PATCH /api/v1/modules` or saved message can occur; the old helper nevertheless waited for both.
2. `design-system-shell` persists `appearance`, then the compatibility call used only to hide parked legacy Settings cards writes `none` into the same `dutylog.settings.openSection` key.
3. `workspace-layout-theme-studio` unchecks Overtime and then moves Tasks. `moveStudioItem()` calls `completeOrder()` and stores the completed universe back into `todayWidgets`, silently restoring the hidden Overtime card.

## Fix

- module helper returns successfully when the current checkbox state already equals the requested state; real state changes still require the exact PATCH and saved-message contract;
- legacy Settings-card visibility no longer persists the Vue-owned section key;
- widget movement filters the completed control order back through the pre-move visible-widget set before storing `todayWidgets`;
- existing Vitest and Java static contracts cover the regressions without increasing the baseline.

No backend/OpenAPI/Flyway behavior, retries, timeouts or runtime-error allowlist is changed.
