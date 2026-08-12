# v27.40.14 — Vue Route Guard Authority Cutover

## Why

v27.40.13 made Vue the direct owner of canonical hash route state, but access policy still lived inside legacy `applyRoute()`. That meant Admin/profile and disabled-module routing still depended on a legacy side-effect listener even though the shell and migrated domains already consumed the hash directly.

## Cutover

- `guardHashRoute(...)` owns Admin and module route policy once authoritative profile/module state is loaded.
- Blocked `admin`, `vacation`, `overtime`, `payroll`, `tasks` and `important` requests canonicalize to `#calendar`.
- Before profile/modules are loaded, the guard is permissive so startup does not invent false denials.
- `App.vue` owns `document.body.dataset.view` and re-evaluates route policy whenever the legacy read model publishes updated access state.
- Calendar closes its Vue selected-day panel when leaving the Calendar route; post-Vue routing no longer calls legacy `selectDay(null)`.
- Once `data-vue-shell="ready"`, legacy `applyRoute()` only shows/enters Payroll/Admin. The full historical router remains available only before Vue readiness as recovery.

## Non-goals

- Payroll and Admin are not migrated in this release.
- The browser hash remains the single route transport.
- `dataLayer`, IndexedDB, reconnect ownership, OpenAPI, Flyway and backend business rules are unchanged.

## Acceptance

Static release contracts require the Vue guard policy, canonical redirects, Calendar route-exit ownership and a narrowed post-Vue legacy branch. Runtime acceptance remains exact Node 20.18.1/npm 10.8.2 frontend gate, Maven 760/760, Playwright canary/full 48/48 with zero flaky retries, immutable image and PostgreSQL V47 staging smoke.
