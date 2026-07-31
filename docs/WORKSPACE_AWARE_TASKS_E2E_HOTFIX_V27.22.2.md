# v27.22.2 — Workspace-Aware Tasks E2E Navigation Hotfix

## Failure

The v27.22.1 Maven gate completed all 544 tests, then Playwright reported four failures while 27 scenarios passed. Each failed scenario targeted `#tabbar a[data-view="tasks"]`, but the accepted Shift Worker workspace intentionally marks that anchor `workspaceHidden`. The Vacation Planner browser scenario passed.

## Resolution

- `mobile-layout.spec.js` opens Tasks through `openView(page, "tasks")`.
- `task-details.spec.js` uses the same workspace-aware route before checking projected deadlines.
- `task-modules.spec.js` uses `openView()` for the Inbox flow.
- Task module persistence checks `moduleHidden`, not visual workspace placement.

`openView()` first clicks a visible primary tab. When the current workspace keeps a valid screen outside primary navigation, it uses the same `#tasks` route exposed by the application. Module-disabled routes remain protected by production `applyRoute()`.

## Scope

- No production JavaScript behavior changes.
- No HTTP API or OpenAPI changes.
- No database migration; Flyway remains V40.
- Baseline remains 103 Java test classes / 544 `@Test` methods / 31 Playwright scenarios.
