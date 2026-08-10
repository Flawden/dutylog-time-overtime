# v27.38.15 — Module Cache Authority Browser Parity Hotfix

## Evidence

The complete v27.38.14 Playwright report executes all 47 Chromium scenarios. Final result: **46 passed / 1 failed**, with `task-modules` failing on both attempts and no separate flaky artifact. The only runtime issues are one generated Task-family wave after disabling Tasks: selected-day Tasks, Inbox, metadata and Board each receive HTTP 403 `MODULE_DISABLED`, with their corresponding browser console resource errors.

The trace orders the relevant events as follows:

1. the Tasks module checkbox is unchecked;
2. `PATCH /api/modules` persists `{"enabled":{"tasks":false}}` successfully;
3. the settings flow calls `loadMonth()`;
4. `dataLayer.loadCalendar()` is allowed to paint its matching IndexedDB month snapshot before the network month result;
5. that month snapshot was captured while Tasks was enabled and previously carried its own `modules` array into `applyCalendarBundle()`;
6. the month-scoped cache could therefore overwrite the already-current global module map and briefly publish `tasks=true`;
7. Vue Productivity reacts to that rebound and issues `/api/v1/tasks`, `/api/v1/inbox`, `/api/v1/tasks/metadata` and `/api/v1/tasks/board`;
8. the backend correctly rejects all four because Tasks is already disabled.

The authoritative network month response itself reports `tasks=false`; backend persistence is correct. The defect is the pre-network cache authority boundary.

## Fix

- A matching cached calendar bundle is cloned before use. If the global runtime module map is already loaded, the cached bundle receives that current module list instead of its historical month-snapshot module list. Cached calendar facts can still render immediately, but cached configuration cannot roll module authority backward.
- After a successful module mutation, `saveModuleEnabled()` calls `loadMonth({ fresh:true })`. The module settings transaction therefore refreshes from the authoritative server month rather than replaying a pre-mutation IndexedDB calendar snapshot.
- The existing optimistic-disable / confirmed-enable request boundary from v27.38.14 remains intact.
- `403 MODULE_DISABLED` is not allowlisted or downgraded.

## Scope

No API/OpenAPI shape, backend module guard, business rule, PostgreSQL schema, Flyway migration, browser timeout, retry policy or runtime-error collector changes. Baseline remains 152 Java test classes / 751 `@Test` / 47 Playwright / 49 Vitest / Flyway V47.
