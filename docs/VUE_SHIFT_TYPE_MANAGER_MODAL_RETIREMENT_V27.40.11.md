# v27.40.11 — Vue Shift Type Manager Modal Retirement

## Baseline

v27.40.10 is the proven-green predecessor: exact frontend, Maven 758/758, Playwright 48/48 with zero flaky retries, immutable image and PostgreSQL V47 staging deployment are accepted.

## Ownership cut

`ShiftTypeManagerModal.vue` is the live Shift Type editor owner after Vue Settings readiness. The legacy `#shiftTypeModal` source markup remains only as pre-Vue recovery fallback and `retireDomainOwners("settings-workspace")` removes it before the Vue-owned modal can open.

The migrated editor preserves the established browser IDs: `shiftTypeModal`, `shiftTypeForm`, `customList`, `nsName`, `nsHours`, `nsStart`, `nsEnd`, `nsBreak`, `nsPlan`, `nsNotificationsEnabled`, `nsNotificationMinutes`, `swRow`, `shiftTypeMessage`, `shiftTypeCancelEdit` and `shiftTypeSave`.

## Generated transport

Shift Type list/create/update/delete use the generated OpenAPI client operations `listShiftTypes`, `createShiftType`, `updateShiftType` and `deleteShiftType` on canonical `/api/v1/shift-types` routes. Successful mutations refresh the Settings Shift Type collection and the Vue Calendar read model.

## Parity

- Built-in shifts keep immutable name/color and cannot be deleted.
- Custom shifts keep name/color editing and deletion.
- Cross-midnight duration calculation, break validation and planned-hour fallback remain equivalent to the legacy editor.
- Per-shift notification enablement and reminder minutes remain editable.
- Existing palette swatches plus custom color input remain available.

## Bridge retirement

`openShiftTypeManager` is removed from `LegacyBridge` and `DutyLogLegacyPlatform`. Calendar selected-day `+` calls `DutyLogVueDomains.settingsWorkspace.openShiftTypeManager()` directly. The historical global `openShiftTypeManager()` remains only as a recovery-compatible entry and delegates to the Vue Settings domain once readiness is published.

## Boundaries retained

- `dataLayer` remains the sole offline mutation/reconnect owner; Shift Type CRUD remains online/server-authoritative.
- Payroll/Admin remain legacy hash-router boundaries.
- Historical numbered JavaScript still contains recovery/fallback implementations pending later retirement cuts.
- OpenAPI shape/count and Flyway V47 are unchanged.
