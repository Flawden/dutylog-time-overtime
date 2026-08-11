# v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover

v27.39.6 is the accepted green staging baseline. v27.40.0 opens the final legacy-retirement release family by removing the last three DOM compatibility islands from Settings instead of carrying them into the numbered-JavaScript retirement phase.

## Retired in this cut

- `#settingsLegacyHost` and `#settingsLegacyParking` no longer exist.
- `attachSettingsLegacy` / `openSettingsLegacySection` are removed from the Vue↔legacy bridge and legacy platform.
- Time/Timezone, Schedule Templates/Calendar Layers and Notifications are native Vue Settings components.
- Those components use the generated `/api/v1/*` client for profile/time, shift types, notification settings, schedule templates and calendar layers.
- Legacy Time/Schedule/Notification DOM renderers yield permanently once `data-vue-settings-workspace=ready`, so they cannot mutate the new Vue-owned nodes that intentionally preserve stable test/accessibility IDs.
- The canonical profile timezone is mirrored into the temporary legacy time-state adapter only to keep still-unretired Calendar/Overtime consumers coherent during the v27.40.x transition.

## Browser parity preserved

Existing browser journeys keep their business assertions. Their transport waits move to the generated owner where Settings now emits `/api/v1/profile`, `/api/v1/shift-types`, `/api/v1/notifications/settings` and `/api/v1/calendar-layers`. No retry, timeout, console/pageerror or HTTP-failure policy is weakened.

## Remaining v27.40.x retirement blockers

This release does **not** claim that all legacy JavaScript has disappeared. The remaining closure work is explicit:

- selected-day Calendar editor/panel compatibility host;
- legacy hash routing and shell-state bridge;
- legacy modal adapters still used by not-yet-migrated surfaces;
- legacy `dataLayer` offline queue/transport compatibility owner;
- legacy Payroll and Admin UI ownership;
- numbered JavaScript files that still contain those runtime responsibilities.

The v27.40.x milestone closes only when those owners are either migrated or deliberately retained as non-DOM transport with a documented boundary and the full exact-toolchain/staging acceptance is green.

## Locked baseline

- OpenAPI: 118 operations / 120 schemas / `91b48b10fa56`
- Flyway: V47
- Browser scenarios: 48
- Vitest: 52
- Java test classes / methods: 153 / 758
