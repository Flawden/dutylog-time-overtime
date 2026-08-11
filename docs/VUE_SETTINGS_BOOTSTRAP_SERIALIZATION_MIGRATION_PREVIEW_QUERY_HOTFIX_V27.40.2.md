# v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix

## Evidence

The complete v27.40.1 Playwright artifact contains only the strict onboarding canary because the workflow stops at that gate. Both attempts fail after product assertions with the runtime collector. The first attempt records `500 GET /api/v1/schedule-templates`; the retry records `500 GET /api/v1/calendar-layers`. In both traces those two generated reads start together from `SettingsWorkspaceStore.loadRetiredIslandData()`. Both backend list paths can invoke `ScheduleTemplateService.ensureDefaults(user)`, which lazily inserts the same five system presets guarded by a unique `(user_id, name)` constraint. Alternating which endpoint fails is therefore evidence of one first-user check-then-insert race, not two unrelated endpoint failures.

Both attempts also record `400 MISSING_PARAMETER` for `/api/v1/shifts/legacy-migration/preview` and `/api/v1/tasks/legacy-deadline-migration/preview`. Their OpenAPI definitions require the `sourceTimezone` query parameter, while the migrated Settings API adapter called the generated operations without `query`.

## Fix

- Load schedule templates before calendar layers during native Settings bootstrap and schedule refresh, preventing two same-user default-seeding transactions from running concurrently.
- Pass `sourceTimezone` through the generated client for both legacy migration preview operations. Bootstrap derives it from the already-loaded profile/time context; post-migration refresh reuses the exact timezone selected by the user.
- Keep the strict browser HTTP/console collector unchanged; the fix removes the bad requests instead of allowlisting them.

## Acceptance

- exact frontend gate: strict typecheck, 52/52 Vitest and production build;
- Maven: 758/758;
- canary and Chromium: 48/48, zero flaky retries;
- immutable image, PostgreSQL V47 smoke and staging deployment green.
