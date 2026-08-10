# v27.38.10 — Vue Offline, Task Publication & PWA Browser Parity Hotfix

## Evidence

v27.38.9 completed the exact frontend gate, Maven verify, release-check and the auth/onboarding browser canary before the full Chromium suite ended at **40 passed / 6 failed / 1 flaky**. This hotfix does not change test strictness; it addresses the remaining shared runtime/ownership roots.

## Offline Productivity hydration

The selected-day dataLayer/IndexedDB snapshot is already the released offline authority. v27.38.9 made the Vue Productivity workspace visible offline, but `refreshAll()` still requested online-only time context before it could consume that snapshot. v27.38.10 skips time-context, Task Board, Inbox and full Important-list reads while genuinely offline and lets `loadSelectedDate()` hydrate Tasks, Notes and Important occurrences from the existing bridge snapshot. No second offline database, queue or authorization path is introduced.

## Task mutation publication

Spring Boot remains authoritative for Task validation and persistence. A successful generated Task create/update returns the authoritative DTO. v27.38.10 publishes that DTO immediately into the current selected-day/default-board read models, runs the existing authoritative refreshes, then republishes the same DTO after sequencing settles. Existing Board rows are replaced in place; a missing row is appended only for the default open/unfiltered board. The frontend does not invent deadline, schedule or completion business rules.

## PWA first-run lifecycle

Service-worker registration is idempotent and exposed through the bounded `DutyLogPwaRuntime.register()` capability. Existing onboarded users register after authenticated application initialization. Fresh users do not start install/claim while the onboarding overlay owns the page; `finishOnboarding()` starts registration only after the persisted profile says onboarding is complete. First claim still avoids a reload and later controller updates on an already-controlled page keep the existing one-shot reload contract.

## Notes endpoint ownership correction

The pre-existing offline note update adapter owns PATCH `/api/notes/{id}`. Generated Vue delete owns DELETE `/api/v1/notes/{id}`. v27.38.9 accidentally aligned the E2E DELETE wait to the compatibility PATCH route; v27.38.10 restores the generated DELETE expectation without changing runtime API behavior.

## Non-goals and acceptance

- No backend business-rule, OpenAPI shape, PostgreSQL schema or Flyway change.
- No new offline queue/cache.
- No Playwright assertion, retry, timeout or runtime-error allowlist weakening.
- Baseline remains 152 Java test classes / 751 tests / 47 Chromium scenarios / 49 Vitest cases / Flyway V47 and OpenAPI 101 operations / 106 schemas.
- Acceptance remains blocked until exact frontend, Maven 751/751, boot canary, **clean 47/47 Chromium**, immutable image, clean PostgreSQL and staging are green.
