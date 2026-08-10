# v27.38.12 — Vue Productivity Summary Ownership & PWA E2E Parity Hotfix

## Evidence

The v27.38.11 staging Chromium artifact `staging-playwright-report-31364855424-1` completed with **42 passed / 5 failed / 0 flaky**. Unlike the previous job tail, the Playwright report preserved the failing DOM snapshots and request traces, so this follow-up is based on browser evidence rather than another timing hypothesis.

The three Task failures (`editor-modals`, `task-modules`, `tasks-inbox-next`) all showed the same sequence: the Task `POST`/`PATCH` completed with HTTP 200, subsequent `/api/v1/tasks` and `/api/v1/tasks/board` reads already contained the committed Task, and then the Vue shell entered DutyLog Recovery. The Recovery request id matched the successful Task mutation. That disproves backend read-model lag as the primary cause of those failures.

The Notes failure showed two note cards and a calendar note badge of `2`, while the selected-day summary target `#sumNote` had been reset to `—`. This isolates a frontend DOM ownership collision rather than missing note data.

The PWA upgrade failure stopped on `[data-onboarding-preset="minimum"]`. The rendered onboarding dialog exposes the localized label “Минимум” under the canonical preset key `basic`; the rest of the suite already uses `basic`. The failing selector was therefore a stale E2E contract, not evidence of another service-worker activation failure.

## Root cause

`src/main/resources/static/js/30-calendar.js::updateAccSummaries()` still treated `#sumTasks`, `#sumNote`, and `#sumImp` as legacy-owned DOM. After the v27.38.0 Productivity migration those nodes are Vue Teleport targets. Legacy `textContent`/`innerHTML` assignments can remove Vue-owned Teleport children; a later Vue render then operates on a DOM tree that has been mutated behind Vue's renderer and can fall into the application error boundary.

This is inherited browser-parity debt from the Calendar/Productivity ownership transition. It explains the three Task recovery failures and the Notes summary mismatch with one mechanism.

## Fix boundary

- `ProductivityWorkspace.vue` is the single always-mounted Vue owner of the Tasks, Notes, and Important selected-day summary targets.
- Child selected-day components no longer compete for those three Teleport targets.
- Legacy `updateAccSummaries()` yields those targets once `data-vue-productivity="ready"`; unrelated legacy shift, schedule, overtime, vacation, and emoji summaries remain unchanged.
- `pwa-upgrade.spec.js` now uses the canonical onboarding preset key `basic` instead of the nonexistent `minimum` key.
- The v27.38.11 Task read-your-write overlay is retained as additional mutation robustness, but is no longer presented as the root fix for the observed Task browser failures.

No backend business rule, OpenAPI contract, database migration, Playwright timeout, retry policy, assertion strength, or HTTP-failure policy is weakened by this release.

## Acceptance

v27.38.x remains browser-incomplete until the authoritative Chromium suite completes **47/47 with no flaky retry**. Static release checks are necessary but are not sufficient to accept this release.
