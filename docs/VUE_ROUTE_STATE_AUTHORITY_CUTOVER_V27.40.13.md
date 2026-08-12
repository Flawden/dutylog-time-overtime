# v27.40.13 — Vue Route State Authority Cutover

Status: static candidate. Runtime acceptance remains mandatory.

## Why this cut exists

The Vue shell, navigation model and migrated domains were already native, but route state still made an unnecessary round trip through `DutyLogLegacyPlatform`: Vue navigation called `LegacyBridge.navigate()`, the legacy platform wrote `location.hash`, `applyRoute()` republished the hash inside `DutyLogLegacySnapshot`, and Vue then synchronized that legacy route field back into Pinia.

v27.40.13 removes that circular ownership. The URL hash remains the single browser transport so legacy Payroll/Admin and their route-entry side effects continue to work, but Vue now owns reading, writing and subscribing to the route state directly.

## Ownership changes

- `hashRoute.ts` owns canonical hash read/write/subscription for Vue.
- `App.vue` synchronizes `shell.rawRoute` / `shell.activeRoute` from `location.hash` and `hashchange`, independent of legacy-state publication.
- `DutyLogLegacySnapshot` no longer contains `route`; legacy state events carry only the compatibility state still sourced from legacy code.
- `LegacyBridge.navigate()` and the `navigate` fallback command are removed.
- Vue components navigate with `navigateHashRoute(...)` instead of routing through the legacy platform.
- `DutyLogVuePlatform.navigate(...)` replaces the historical `navigateLegacy(...)` E2E/public shell helper capability.
- `DutyLogLegacyPlatform.navigate(...)` and `applyRoute()` remain only as pre-Vue compatibility / legacy Payroll-Admin side-effect adapters. They are not Vue route state authority.

## Boundaries deliberately unchanged

- Payroll and Admin remain legacy-owned screens and continue to react to the canonical browser hash.
- The single legacy `dataLayer` remains the only IndexedDB/offline queue and reconnect owner.
- No HTTP/OpenAPI operation or schema changes; baseline remains 118 operations / 120 schemas with hash `91b48b10fa56`.
- Flyway remains V47.
- No retry, timeout, strict-TypeScript or browser failure collector relaxation.

## Acceptance

- Static release check must pass from the final ZIP.
- Exact Node 20.18.1 / npm 10.8.2 frontend gate.
- Maven 758/758.
- Playwright canary and full Chromium 48/48 with zero flaky retries.
- Immutable image/PostgreSQL V47 staging deployment.
