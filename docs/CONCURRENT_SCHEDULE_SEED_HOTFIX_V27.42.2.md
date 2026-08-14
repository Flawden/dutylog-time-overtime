# v27.42.2 — Concurrent Schedule Seed Hotfix

## Exact runtime failure
Playwright first-run onboarding reached the authenticated application but startup recorded `HTTP 500 GET /api/schedule-templates`; onboarding therefore remained hidden and the canary timed out. The same failure reproduced on retry.

## Cause
DutyLog intentionally retains two startup readers during the bounded legacy-onboarding transition: legacy boot and the Vue Settings workspace. Both can request schedule metadata for a newly registered user before built-in schedule presets exist. `ScheduleTemplateService.ensureDefaults()` previously used a check-then-insert sequence without a cross-transaction owner lock, so two transactions could both observe no presets and race the unique `(user_id, name)` constraint.

## Fix
`ensureDefaults()` now acquires a database `PESSIMISTIC_WRITE` lock on the owning user before reading/creating built-in schedule state. The lock serializes first-read seeding for the same owner across concurrent requests/instances sharing the database.

A dedicated concurrency regression starts two simultaneous first `list()` calls and requires both to return five presets while exactly five rows are persisted.

## Boundary
No retry, timeout, frontend-owner suppression, API/OpenAPI change, Flyway migration, People Profiles behavior, bundle budget, dependency or offline-queue change.
