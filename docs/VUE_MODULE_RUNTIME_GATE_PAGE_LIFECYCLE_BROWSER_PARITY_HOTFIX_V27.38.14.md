# v27.38.14 — Module Toggle Runtime Gate & Page Lifecycle Browser Parity Hotfix

## Evidence

The uploaded v27.38.13 Playwright report executed all 47 scenarios and finished at 46/47 on the final result. `task-modules` is the only final failure. Both attempts complete the product assertions but the runtime collector records eight generated Task-family reads (`/api/v1/tasks`, `/api/v1/tasks/board`, `/api/v1/tasks/metadata`, `/api/v1/inbox`) returning guarded HTTP 403 while the Tasks module is disabled. The report also preserves an `editor-modals` first-attempt artifact: all editor/timeline assertions pass, then the explicit `page.reload()` aborts two in-flight `/api/v1/calendar` reads and legacy `loadMonth()` logs `TypeError: Failed to fetch`.

## Fix

1. `saveModuleEnabled()` publishes disablement before the backend PATCH and restores the previous snapshot if persistence fails; enablement stays gated until the backend confirms the module is available. Module disablement is therefore an immediate runtime request boundary without creating the inverse enable race.
2. Vue Productivity reads use `runtimeModuleEnabled()` backed by the live legacy bridge snapshot with Pinia fallback. Direct Board/Inbox/Important/Note-search entry points fail closed without issuing guarded requests when their module is disabled.
3. `loadMonth()` tracks the page lifecycle and ignores only `AbortError`/`Failed to fetch` caused after `pagehide`. Genuine network errors during a live page still log and fail browser diagnostics.

No Playwright assertion, timeout, retry, HTTP-failure collector or backend module guard is weakened.
