# DutyLog v27.42.8 — Tasks Module E2E Route Contract Alignment Hotfix

## CI evidence

v27.42.7 reached the Browser E2E regression suite with 47 tests passing. The sole failure was `task-modules.spec.js` waiting for `#view-tasks` immediately after `toggleModule(page, 'tasks', true)`. At that point `toggleModule` intentionally leaves the browser on Settings, while the Vue shell mounts the Tasks page only when the Tasks route is active.

## Fix

The regression now checks the persistent Tasks navigation item for module visibility, explicitly opens the Tasks route, and verifies the Vue-owned `[data-vue-domain-route="tasks"]` surface. The disable/re-enable data-persistence assertions remain unchanged.

## Scope

No production runtime, backend, persistence, OpenAPI, Flyway, People Profiles, schedule-template locking, retry or timeout behavior changes.
