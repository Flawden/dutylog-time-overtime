# v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix

- Uses the complete v27.40.1 canary Playwright artifact as source of truth: the canary is the only executed browser scenario and fails on both attempts with strict runtime HTTP collection.
- Classifies the alternating 500 as one shared-default seeding race introduced by the native Settings bootstrap: `listScheduleTemplates` and `listCalendarLayers` ran in one `Promise.all`, and both backend list paths can call `ScheduleTemplateService.ensureDefaults(user)`, so the first-user preset seed can collide on the unique `(user_id, name)` constraint. Settings now serializes the schedule-template read before the calendar-layer read, including later schedule refreshes.
- Fixes the two deterministic 400 responses by passing the required `sourceTimezone` query parameter to generated `/api/v1/shifts/legacy-migration/preview` and `/api/v1/tasks/legacy-deadline-migration/preview` calls, using the authoritative profile/time-context timezone and preserving the selected timezone after migration.
- Extends the existing Settings source contract only; no backend business rule, OpenAPI schema/operation, Flyway migration, browser retry/timeout or strict HTTP/runtime collector is weakened.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47; OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires exact frontend, Maven 758/758, clean canary, clean Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging.

# v27.40.1 — Vue Settings Retirement Maven Contract Alignment Hotfix

- Uses the first v27.40.0 staging run as the gate source of truth: exact frontend typecheck, 52 Vitest cases and production build complete before Maven fails.
- Reproduces the repository-only static frontend contracts and isolates the stale Settings assertion in `VueSettingsWorkspaceMigrationFrontendContractTest`: the Playwright scenario wraps the retired-host locator with `expect(...)`, while the Java contract still searched for the obsolete unwrapped string.
- Aligns only that source contract and release metadata. The retired `#settingsLegacyHost` stays retired; Vue Settings runtime, API/OpenAPI, Flyway, browser retries/timeouts and strict runtime/HTTP collectors are unchanged.
- Acceptance remains Maven 758/758, canary, clean Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging.

# v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover

- Starts from the accepted green v27.39.6 staging workflow and opens the v27.40.x legacy-retirement milestone without carrying Settings compatibility islands forward.
- Replaces Time/Timezone, Schedule Templates/Calendar Layers and Notifications with native Vue Settings components using generated `/api/v1/*` operations.
- Removes `#settingsLegacyHost`, `#settingsLegacyParking`, `attachSettingsLegacy` and `openSettingsLegacySection`; legacy Settings renderers yield after Vue readiness instead of mutating stable IDs owned by Vue.
- Keeps the canonical timezone synchronized into the temporary legacy time-state adapter while still-unretired Calendar/Overtime consumers exist; this is compatibility state, not DOM ownership.
- Aligns existing E2E transport waits with the generated Settings owner while preserving every business assertion and strict retry/runtime/HTTP policy.
- Does not falsely declare complete legacy removal: selected-day Calendar, legacy routing/modal adapters, offline `dataLayer`, Payroll/Admin and remaining numbered-JS responsibilities are explicit v27.40.x closure blockers.
- OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`; Flyway remains V47; locked acceptance baseline remains 153 Java test classes / 758 `@Test` methods / 48 Playwright scenarios / 52 Vitest cases.

# v27.39.6 — Module Runtime Synchronization & Offline Reconnect Ownership Hotfix

- Uses the complete v27.39.5 Playwright artifact as source of truth: 48 scenarios ran, 44 passed and 4 failed; the four final failures are all Task-module journeys. One additional `pwa-offline` first attempt is flaky even though its retry passes, so acceptance is still blocked.
- Fixes the Task failures at the product boundary rather than weakening the idempotent E2E helper: Vue Settings can bootstrap module metadata before first-run onboarding commits the selected preset, so it now merges the live shell/legacy module map after bootstrap and on every later module-state publication.
- Keeps the shell/runtime module map authoritative for current enablement while preserving the generated module catalog metadata used by Settings. An onboarding `tasks=false` can no longer leave a stale checked Tasks card that makes an idempotent helper return while `#view-tasks` remains hidden.
- Removes the redundant Vue Productivity `online` queue flush. The existing legacy `dataLayer` remains the single offline queue/reconnect owner and publishes `dutylog:offline-sync-complete`; Vue refreshes its read model only after that signal. This addresses the flaky trace where reconnect submitted the same queued note PATCH twice, producing one 200 and one concurrent 500 and leaving the queue pending.
- Extends existing JUnit source contracts only; no backend business rule, OpenAPI operation/schema, Flyway migration, browser retry/timeout, HTTP/runtime collector or E2E business assertion is weakened.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47; OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires exact frontend, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL smoke and staging.

# v27.39.5 — Vue Settings State Ownership Browser Parity Hotfix

- Uses the complete v27.39.4 Playwright artifact as source of truth: 48 scenarios ran, 42 passed and 6 failed.
- Makes the shared E2E module toggle idempotent: when a module already matches the requested state, the helper asserts the state and does not wait for a PATCH or saved-message that a no-op Playwright `check()`/`uncheck()` will not emit.
- Removes persisted Settings ownership from the legacy-card visibility bridge so `openSettingsLegacySection("none")` cannot overwrite Vue's `dutylog.settings.openSection=appearance`.
- Fixes Workspace Studio widget movement so `completeOrder()` may build the control universe without silently re-enabling Today widgets that the user intentionally hid.
- Extends existing Vitest/JUnit source contracts without increasing test counts or weakening browser retries, timeouts, HTTP/runtime collectors, backend rules, OpenAPI or Flyway.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47; OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires exact frontend, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL smoke and staging.

# v27.39.4 — Vue Settings Browser Ownership & Preview Correlation Hotfix

- Uses the complete v27.39.3 Playwright artifact as source of truth: 48 scenarios ran, 31 passed and 17 failed; 16 failures share the same retired `#view-settings` selector and one partial-time-off assertion captured an earlier auto-preview response.
- Updates the shared E2E `openView()` ownership map so Settings targets `[data-vue-settings-workspace-view]` and waits for the Vue Settings readiness marker instead of waiting for the intentionally removed legacy container.
- Makes the partial-time-off preview wait correlate by request payload (`PARTIAL`, `09:00`–`13:00`, `OVERTIME_BANK`) so the strict 240-minute assertion cannot accidentally consume a stale 09:00–18:00 background preview.
- Adds static source contracts for both browser invariants without changing application runtime behavior, backend rules, OpenAPI, PostgreSQL/Flyway, browser retries/timeouts or runtime-error allowlists.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47; OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires exact frontend, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL smoke and staging.

# v27.39.3 — Frontend Diagnostics Release Version Source Hotfix

- Uses the exact v27.39.2 staging frontend gate as source of truth: strict `vue-tsc` passed, then Vitest ran 52 cases and failed only `frontendDiagnostics.spec.ts` because the diagnostics runtime still embedded `27.39.1` while the release contract expected `27.39.2`.
- Removes the duplicated hard-coded frontend release literal from `vite.config.ts`; `__DUTYLOG_RELEASE_VERSION__` now derives from the canonical frontend `package.json` version used by the committed npm lockfile.
- Makes the diagnostics Vitest assertion derive its expected release from the same package metadata, so future version bumps cannot leave the test and Vite define on different literals.
- Adds release/static contracts that forbid reintroducing a hard-coded Vite release version while preserving strict TypeScript, OpenAPI, backend rules, Flyway, browser retries/timeouts and runtime diagnostics behavior.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47; OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires the exact frontend gate, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL smoke and staging.

# v27.39.2 — Vue Settings Maven Contract Alignment Hotfix

- Uses the local v27.39.1 Maven `verify` failure as source of truth: 758 tests ran, 756 passed, and only two source/static contracts were stale after the Settings migration and strict-TypeScript hotfix.
- Aligns the recurring PWA migration contract with the current synthetic predecessor cache (`v27.38.15`) already enforced by the Playwright scenario and release-check, instead of the obsolete `v27.36.8` fixture.
- Aligns the Settings ownership contract with the actual retirement boundary in `10-core.js`, which publishes readiness through `document.documentElement.setAttribute("data-vue-settings-workspace", "ready")`; runtime ownership code is unchanged.
- Changes no Vue runtime behavior, HTTP/OpenAPI contract, backend business rule, PostgreSQL/Flyway schema, Playwright retry/timeout policy or TypeScript strictness.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47; OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires the exact frontend gate, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, clean PostgreSQL and staging.

# v27.39.1 — Vue Settings Strict Typecheck Hotfix

- Uses the first v27.39.0 staging frontend-gate failure as the source of truth: authentic lockfile and OpenAPI drift checks pass, then strict `vue-tsc --noEmit` stops before Maven/Chromium on `exactOptionalPropertyTypes` and `noUncheckedIndexedAccess` violations in the new Settings/Workspace templates and model.
- Keeps strict TypeScript enabled. Optional DOM attributes are now omitted or normalized to concrete booleans instead of binding `undefined`; catalog lookups are narrowed through checked helpers before labels/required flags are consumed.
- Gives mobile sessions a deterministic defined Vue key even when the generated optional `id` is absent, and normalizes Telegram checkbox state to a concrete boolean.
- Replaces the bounds-checked array destructuring swap in Workspace Studio with explicit narrowed values so `noUncheckedIndexedAccess` can prove both elements exist.
- Extends the existing Settings migration source contract to bind the strict-template invariants without changing API/OpenAPI, backend business rules, PostgreSQL/Flyway, browser retries/timeouts or feature scope.
- Baseline remains 153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47. OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Acceptance still requires the exact Node 20.18.1/npm 10.8.2 frontend gate to pass, followed by Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, clean PostgreSQL and staging.

# v27.39.0 — Vue Settings, Workspace & Integrations

- Starts from the accepted green v27.38.15 staging baseline and opens the next planned migration milestone instead of carrying browser-parity debt forward.
- Installs one Vue Settings owner for section navigation, Profile, Language, Modules, Calendar Sync, Appearance/Workspace Studio and Telegram profile integration.
- Keeps Time, Schedule and Notifications as three named compatibility islands under `#settingsLegacyHost`; v27.40.0 owns their final retirement.
- Expands the generated OpenAPI browser contract from 101 operations / 106 schemas to **118 operations / 120 schemas** (`91b48b10fa56`) and moves migrated Settings writes to canonical generated `/api/v1/*` operations. Telegram keeps its compatibility alias and adds canonical `/api/v1/telegram` routing.
- Preserves the v27.38.15 module-toggle authority transaction: disable closes runtime before PATCH, enable waits for backend confirmation, failure restores prior state, and commit forces a fresh calendar/module-aware refresh.
- Moves UI Contract v2 workspace/layout/theme/palette/studio presentation into strict Vue models without duplicating backend business rules or the released root-theme application contract.
- Activates Q-11 and accepts ADR-008: normal production builds emit no public source maps; explicitly requested diagnostic maps are hidden. Calendar bearer URLs and Telegram link values remain outside local persistence/diagnostics.
- Adds one Chromium ownership journey, three Settings model Vitest cases and six source/architecture JUnit contracts. Target baseline is **153 Java test classes / 757 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases / Flyway V47**.
- Acceptance remains fail-closed on exact Node 20.18.1/npm 10.8.2 frontend gate, Java 17 Maven 757/757, canary, clean 48/48 Chromium with zero flaky, immutable image, clean PostgreSQL V47 smoke and staging deployment.

# v27.38.15 — Module Cache Authority Browser Parity Hotfix

- Uses the complete v27.38.14 Playwright report: 47 scenarios ran, 46 passed, and `task-modules` is the only failure on both attempts. The earlier `editor-modals` page-lifecycle flaky is gone.
- Proves the surviving 403 window is a calendar-cache authority bug, not a missing Vue read guard: Tasks are optimistically disabled before `/api/modules`, the backend accepts the disable, then `saveModuleEnabled()` calls `loadMonth()`, whose fast IndexedDB calendar snapshot can still carry the pre-toggle `tasks=true` module map and temporarily reopen generated Task reads while the backend correctly returns `MODULE_DISABLED`.
- Makes the already-loaded global module map authoritative over month-scoped cached calendar bundles. Cached month data may still paint immediately, but it cannot roll module enablement backward once runtime modules are loaded.
- Makes the post-module-mutation month refresh explicitly `fresh:true`, so the settings transaction does not consume a pre-mutation IndexedDB snapshot before the authoritative server bundle arrives.
- Extends the existing calendar reload static contract to bind both invariants without adding tests, weakening 403 diagnostics, changing browser timeouts/retries, or altering backend module guards.
- Changes no API/OpenAPI contract, backend business rule, PostgreSQL schema or Flyway migration. Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47; OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Acceptance still requires exact frontend, Maven 751/751, boot canary, clean 47/47 Chromium with zero flaky retries, immutable image, clean PostgreSQL and staging.

# v27.38.14 — Module Toggle Runtime Gate & Page Lifecycle Browser Parity Hotfix

- Uses the complete v27.38.13 Playwright report: the final result is 46/47 with `task-modules` as the only true failure, while `editor-modals` carries a first-attempt lifecycle-fetch artifact and therefore still violates the zero-flaky acceptance rule.
- Fixes the remaining Tasks module-disable browser failure without weakening diagnostics: module disable intent is published to runtime state before the guarded `/api/modules` persistence request, while enablement waits for server confirmation, so Vue stops issuing Task reads while the backend transitions the module to disabled; persistence failure restores the prior module snapshot.
- Adds bridge-backed `runtimeModuleEnabled()` guards to Vue Productivity read entry points, preventing selected-day, Board, Inbox, Important and Note-search requests from running against a module that the authoritative legacy runtime has already disabled even if a Pinia watcher is one tick behind.
- Fixes the `editor-modals` retry-only failure at page lifecycle boundaries: explicit `page.reload()` aborts two in-flight calendar reads, and legacy `loadMonth()` no longer reports that expected `pagehide` cancellation as a browser console error. Real network errors continue to be logged and fail Playwright.
- Records the approved post-parity Vacation Entitlement Engine before Payroll and shifts the remaining pre-freeze roadmap accordingly.
- Changes no API/OpenAPI contract, backend business rule, PostgreSQL schema, Flyway migration, Playwright timeout/retry policy or runtime-error allowlist.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Acceptance still requires exact frontend, Maven 751/751, boot canary, clean 47/47 Chromium with zero flaky retries, immutable image, clean PostgreSQL and staging.

# v27.38.13 — Vue Productivity Legacy Renderer Retirement Barrier Hotfix

- Uses the complete v27.38.12 Playwright HTML/trace artifact (44 passed / 3 failed) and keeps the release scoped to the last Task-only browser crash family. The v27.38.12 Notes summary ownership and PWA preset fixes are confirmed green.
- Proves the remaining Task failures are not backend persistence failures: generated Task mutations return 200, while Vue then raises runtime error 15 / null `parentNode` failures and enters the AppErrorBoundary recovery screen.
- Identifies the surviving ownership violation directly in the trace DOM: `data-vue-productivity=ready` is already authoritative, but `#taskBoardCategory` contains the legacy `<option value="all">` markup instead of the Vue-owned empty-value option. Legacy async Task metadata/board/inbox work can therefore finish after retirement and replace Vue-managed children.
- Adds a shared `vueOwnsProductivityUi()` retirement barrier to legacy Task metadata, editor, selected-day, Inbox and Board renderers. Renderer-level guards are deliberate: a loader that started before Vue retirement can still resolve afterward, so start-only guards are insufficient.
- Adds post-await ownership barriers to legacy Task metadata, Inbox and Board loaders so in-flight responses cannot publish legacy UI state after Vue takes ownership.
- Changes no Task API/OpenAPI contract, backend business rule, PostgreSQL schema, Flyway migration, browser timeout/retry policy or runtime-error allowlist.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Acceptance still requires exact frontend, Maven 751/751, boot canary, clean 47/47 Chromium with no flaky scenario, immutable image, clean PostgreSQL and staging.

# v27.38.12 — Vue Productivity Summary Ownership & PWA E2E Parity Hotfix

- Uses the full Playwright HTML/trace artifact from v27.38.11 (42 passed / 5 failed, no flaky retry) instead of inferring causes from the truncated Actions tail.
- Fixes the shared Task/Notes browser root at the DOM-ownership boundary: legacy `updateAccSummaries()` no longer mutates `#sumTasks`, `#sumNote` or `#sumImp` after Vue Productivity retirement. Those nodes are Vue Teleport targets, and legacy `textContent`/`innerHTML` writes were deleting Vue-owned children; Task create/update then entered the AppErrorBoundary recovery screen even though POST/PATCH and the following generated Task projections were all HTTP 200 and already contained the committed DTO.
- Moves all three Productivity accordion summaries to one always-mounted `ProductivityWorkspace` owner, including explicit disabled-module labels, so summary ownership remains stable while modules toggle.
- Aligns `pwa-upgrade.spec.js` with the released onboarding contract: the visible «Минимум» preset is keyed as `basic`; the stale `minimum` helper argument never matched a DOM element and was not a service-worker lifecycle defect.
- Keeps the v27.38.11 read-your-write protection as additional mutation robustness, but no longer treats projection lag as the cause of the five remaining failures; the captured network trace proves the post-mutation Task reads already contain the saved rows.
- Changes no backend business rule, generated OpenAPI shape, PostgreSQL schema, Flyway migration, Playwright timeout/retry policy or runtime-error allowlist.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Acceptance still requires exact frontend, Maven 751/751, boot canary, clean 47/47 Chromium with no flaky scenario, immutable image, clean PostgreSQL and staging.

# v27.38.11 — Vue Read-Your-Write & PWA Activation Browser Parity Hotfix

- Uses the completed v27.38.10 Chromium run (42 passed / 5 failed, no flaky retry) and keeps the fix scoped to the remaining browser-parity tail.
- Adds a short-lived backend-authoritative Task read-your-write overlay. Accepted selected-day and Board projection reads merge staged mutation DTOs before replacing Vue state, so a successful create/update cannot disappear while concurrent refreshes settle.
- Keeps Board ordering and admission backend-owned: existing rows are replaced in place, and only the already-defined default open/unfiltered Board may temporarily append a missing committed row.
- Aligns the multiple-daily-notes reload wait with the generated Vue Calendar owner at `/api/v1/calendar`; Note PATCH remains on the bounded offline adapter and DELETE remains generated `/api/v1/notes/{id}`.
- Removes a duplicate first-install service-worker update check: `register()` owns initial installation, while explicit `registration.update()` runs only for an already-existing registration. This preserves controlled-page upgrades without racing first onboarding claim.
- Extends existing source/static contracts only. No browser assertion, retry, timeout, API/OpenAPI shape, backend business rule, database schema or Flyway migration is weakened or changed.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Acceptance remains fail-closed on exact frontend, Maven 751/751, boot canary, clean 47/47 Chromium with no flaky scenario, immutable image, clean PostgreSQL and staging.

# v27.38.10 — Vue Offline, Task Publication & PWA Browser Parity Hotfix

- Uses the completed v27.38.9 Chromium run (40 passed / 6 failed / 1 flaky) after frontend, Maven, release-check and the boot canary were green, so this hotfix stays scoped to browser parity.
- Makes the existing selected-day IndexedDB snapshot the offline Productivity authority: authenticated offline reload no longer blocks Notes/Tasks/Important hydration on online-only time-context, board, Inbox or full Important-list reads.
- Publishes the backend-authoritative Task mutation DTO immediately before concurrent projection refreshes and again after they settle; selected-day rows, the default board and Task metadata therefore cannot transiently lose a successful create/update. Board order remains backend-owned by replacing in place and only appending a missing saved row.
- Defers first service-worker registration until onboarding is authoritative. Existing users register after authenticated init; fresh users register only after `finishOnboarding()`, while the existing first-claim/no-reload and controlled-page upgrade behavior remains intact.
- Corrects the v27.38.9 Notes browser ownership typo: PATCH remains on the bounded offline-adapter `/api/notes/{id}` path, while DELETE again waits for generated `/api/v1/notes/{id}`.
- Extends static contracts for offline read boundaries, Task mutation publication and post-onboarding PWA registration. No browser assertion, retry, timeout or runtime-error allowlist is weakened.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`; PostgreSQL schema and backend business rules are unchanged.
- Acceptance remains fail-closed on exact frontend, Maven 751/751, canary, clean 47/47 Chromium with no flaky scenario, immutable image, clean PostgreSQL and staging.

# v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix

- Uses the completed v27.38.8 Chromium run (37 passed / 10 failed) to isolate the remaining failures into stale E2E ownership contracts, Productivity read-model publication, offline cached-module readiness and first-install PWA registration sequencing.
- Aligns Calendar, Important Days and Calendar Layers browser waits with their Vue generated `/api/v1/*` owners while preserving the bounded legacy `/api/notes/{id}` path used by the existing offline note adapter.
- Keeps cached Productivity readable during an authenticated offline reload once the cached module map is restored, without reopening the online pre-onboarding `MODULE_DISABLED` race fixed in v27.38.7.
- Starts first-install service-worker registration from the authenticated application instead of the login page, preventing the install/claim lifecycle from racing first-run onboarding while preserving update checks for established app sessions.
- Re-publishes the backend-authoritative Task mutation DTO into the current selected-day/default-board read models after concurrent refresh sequencing, preventing a successful save from transiently blanking the task surface.
- Aligns the Task Details E2E with the intentionally collapsed Advanced editor section instead of forcing hidden form controls.
- Extends existing Java/static contracts only; baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Changes no backend business rule, API/OpenAPI shape, PostgreSQL schema, Flyway migration, browser timeout, retry policy or runtime-error allowlist. Acceptance remains blocked on exact frontend, Maven 751/751, canary, 47/47 Chromium, immutable image, clean PostgreSQL and staging.

# v27.38.8 — Vue Shared Browser Parity Hotfix

- Uses the complete v27.38.7 Chromium result (22 passed / 25 failed) to collapse the failures into four shared browser-parity root causes instead of weakening 25 scenarios independently.
- Aligns the shared `selectDate()` helper with Vue Calendar focus semantics: an already-focused day stays selected and one idempotent click reopens the selected-day compatibility island instead of expecting legacy toggle-off behavior.
- Replaces `structuredClone()` on reactive Pinia Task/Important drafts with explicit plain snapshots, so browser mutations reach generated `/api/v1/*` writes and `mutationPending` is always released by the existing `finally`.
- Adds deadline date/time to Task Board metadata, preserving the backend-projected `dueTime` that timezone browser acceptance expects.
- Prevents a first service-worker claim from reloading first-run onboarding; controller changes from an already-controlled page still perform the existing one-shot upgrade reload.
- Extends existing Java source contracts only; baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.
- Changes no backend business rule, API/OpenAPI shape, PostgreSQL schema, Flyway migration, browser timeout, retry policy or runtime-error allowlist. Acceptance remains blocked on exact frontend, Maven 751/751, canary, 47/47 Chromium, immutable image, clean PostgreSQL and staging.

# v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix

- Uses the first real v27.38.6 boot-canary evidence instead of running the remaining 47-scenario suite: Vue dist preflight and Spring boot succeed, but the canary records 58 runtime issues from repeated `403 MODULE_DISABLED` reads after the Minimum onboarding preset disables Tasks, Notes and Important Days.
- Fixes the boot race at its ownership boundary: Vue Productivity does not read optional module APIs until both the legacy module snapshot is loaded and first-run onboarding is completed. A reload with disabled modules therefore starts from the authoritative module map instead of optimistically treating unknown modules as enabled.
- Stabilizes the shell module snapshot identity so repeated legacy-state publications with the same module values no longer retrigger Productivity refresh waves. This removes the request storm that amplified the module-disable race without weakening the browser fixture or allowing expected 403s through the test harness.
- Keeps backend module guards, generated OpenAPI transport, retries, Playwright console/HTTP failure collection and the fail-fast canary unchanged. No PostgreSQL/Flyway/OpenAPI shape change.
- Baseline remains 152 Java test classes / 751 `@Test` methods / 47 Playwright scenarios / 49 Vitest cases / Flyway V47. Acceptance remains pending on exact frontend, Maven, the one-scenario boot canary, targeted browser checks, 47/47 Chromium, image, PostgreSQL smoke and staging.

# v27.38.6 — Vue Calendar Offline Source Typecheck Hotfix

- Restores the Calendar offline-source contract that v27.38.4 wired into `CalendarTimelineWorkspace.vue` but did not actually export from `calendarTimelineStore.ts`; this was the direct cause of TS2305 in the exact CI frontend gate.
- Adds the typed `CalendarTimelineOfflineSource` installer and uses the existing legacy `dataLayer` snapshot only as a network/offline fallback; HTTP/business failures still surface instead of being silently hidden by stale cache.
- Explicitly types the workspace `focusDate` callback and extends existing Calendar store/static regression coverage without increasing the 49-case Vitest or 751-test Java baselines.
- Full Chromium acceptance remains pending; do not start E2E until exact Node 20.18.1/npm 10.8.2 frontend gate and Maven are green.

# v27.38.5 — Windows Frontend Gate & Calendar Contract Alignment Hotfix

- Fixes the first native Windows frontend-gate defect exposed by v27.38.4: PowerShell `Set-StrictMode` was invoking `C:\Program Files\nodejs\npm.ps1`, whose `$MyInvocation.Statement` access fails before the pinned Node/npm checks can run. The Windows gate now resolves and invokes `npm.cmd` explicitly for version, install, graph and script execution.
- Aligns the two remaining Maven failures from the v27.38.4 local run without changing Calendar runtime behavior: the schedule-layer source contract now matches the real `if (... === "ready") return` Vue-ownership guard, and cross-midnight unit coverage is asserted through scheduled end-date/day-span behavior instead of a fragile mixed-script task-label literal.
- Extends the existing frontend executable-resolution contract to lock `npm.cmd` usage and reject regression back to bare `npm` invocation under the Windows gate. No new JUnit/Playwright/Vitest case is added.
- Keeps the baseline at 152 Java test classes / 751 `@Test` methods / 47 Playwright scenarios / 49 Vitest cases / Flyway V47; OpenAPI remains 101 operations / 106 schemas and PostgreSQL is unchanged.
- Acceptance remains pending until the exact Windows/Linux frontend gate, Maven 751/751, boot canary, targeted browser canaries, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deployment are green.

# v27.38.4 — Vue Productivity Ownership & Boot Browser Parity Hotfix

- Consolidates the browser-parity defects found by the v27.38.3 source audit instead of paying another full-suite timeout for one-line ownership fixes: Vue domain commands are installed before legacy owner retirement, and retired Productivity/Calendar renderers yield without dereferencing removed DOM.
- Restores selected-day/calendar parity after the Vue migration: Notes quick-create navigates to the canonical Calendar date and opens the Notes section, Calendar navigation closes the blocking mobile panel before period changes, legacy Calendar experience/layer wrappers stop mutating Vue-owned mode/layer markup, and full/partial absence facts are rendered from the backend range projection.
- Fixes canonical date/time presentation edges: timezone-projected shifts render only on their display date, midnight-exclusive shift endings do not create a phantom next-day segment, timed tasks carry their end date across midnight, and Productivity uses backend `workDate`/`workTimezone` before reporting readiness.
- Hardens offline/PWA/browser delivery: Calendar can consume the existing dataLayer snapshot while offline, the PWA previous-cache fixture is seeded before current service-worker registration, native Windows gets an exact pinned frontend gate, and Playwright refuses to start when the Vue dist assets are missing.
- Adds a fast `auth-onboarding` Playwright boot canary before the full browser suite in CI/staging. Full 47-scenario Chromium remains mandatory; retries, assertions, page-error collection, timeout policy and backend business authority are unchanged.
- Keeps the baseline at 152 Java test classes / 751 `@Test` methods / 47 Playwright scenarios / 49 Vitest cases / Flyway V47. OpenAPI remains 101 operations / 106 schemas and there is no PostgreSQL migration.
- Acceptance remains pending until exact Node 20.18.1/npm 10.8.2 frontend gate, Maven 751/751, browser canaries, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deployment are green.

# v27.38.3 — Vue Productivity Strict Typecheck Hotfix

- Fixes the nine strict `vue-tsc` errors exposed by the first exact v27.38.2 frontend gate after lockfile, delivery and OpenAPI drift checks passed.
- Moves Today cross-domain `window.DutyLogVueDomains` calls out of the Vue template into typed setup functions, reuses the generated `Task` schema for Calendar tasks, and makes the note autosave timer environment-safe.
- Removes four Pinia action parameter initializers that referenced `this` and normalizes optimistic DayNote content so nullable update input cannot violate the non-null response model under `exactOptionalPropertyTypes`.
- Extends existing Java source contracts without adding a test method; baseline remains 152 Java test classes / 751 `@Test` methods / 47 Playwright scenarios / 49 Vitest cases / Flyway V47.
- Changes no PostgreSQL schema, Flyway migration, OpenAPI shape (101 operations / 106 schemas), backend business rule, browser timeout, retry policy or Playwright assertion.
- Acceptance remains pending until the exact frontend gate is green, followed by Maven/JUnit, `npm run test:e2e`, immutable image, clean PostgreSQL smoke and staging deployment.

# v27.38.2 — Vue Productivity Manifest Contract Alignment Hotfix

- Fixes the last Maven-failing static contract exposed after v27.38.1 reduced the suite from three failures to one: the migration manifest heading is `Offline/reconnect boundary`, while the Java source contract used case-sensitive `String.contains("offline/reconnect")`.
- Makes the documentation contract semantic and casing-stable via `manifest.toLowerCase(Locale.ROOT).contains("offline/reconnect")`; the manifest wording, Vue productivity runtime, generated OpenAPI contract and offline queue ownership remain unchanged.
- Adds no test method or browser scenario; baseline remains 152 Java test classes / 751 `@Test` methods / 47 Playwright scenarios / 49 Vitest cases / Flyway V47.
- Changes no PostgreSQL schema, Flyway migration, API semantics, browser timeout, retry policy or Playwright assertion.
- Acceptance remains pending until Maven/JUnit reaches 751/751 and the exact frontend, Chromium, image, PostgreSQL and staging gates are green.

# v27.38.1 — Vue Productivity Static Contract Alignment Hotfix

- Aligns three Maven-failing static contracts exposed by the first v27.38.0 local `mvn verify`: the historical Absence/Time Bank contract no longer owns global OpenAPI operation/schema totals, the Task duration contract follows Vue's dynamic `v-for` binding, and legacy-owner retirement checks the canonical `data-vue-productivity` readiness marker through the actual DOM dataset path.
- Preserves the v27.38.0 Vue Tasks, Notes & Important Days runtime, generated OpenAPI contract (101 operations / 106 schemas), offline/reconnect queue ownership and all backend business rules unchanged.
- Adds no test method or browser scenario; baseline remains 152 Java test classes / 751 `@Test` methods / 47 Playwright scenarios / 49 Vitest cases / Flyway V47.
- Changes no PostgreSQL schema, Flyway migration, API semantics, timeout, retry policy or Playwright assertion.
- Acceptance remains pending until the exact frontend gate, Maven/JUnit, all 47 Chromium scenarios, immutable image, clean PostgreSQL smoke and staging deployment are green.

# v27.38.0 — Vue Tasks, Notes & Important Days

- Migrates Tasks, Inbox, multiple daily Notes and Important Days into one bounded Vue productivity owner, including selected-day bodies and editor/detail modals, while keeping Spring Boot authoritative for all persisted business rules.
- Expands the generated OpenAPI browser contract to 101 operations / 106 schemas so migrated online reads and writes use typed `/api/v1/*` operations without hand-written transport.
- Activates Q-10 offline/reconnect by reusing the existing dataLayer queue/snapshot through typed bridge capabilities for note edits, task completion and Inbox capture; no second offline queue or browser-side business authority is introduced.
- Adds independent latest-read-wins sequencing, duplicate-submit guards, HTTP 409 refresh, task/deadline parity, Important Event floating/timed semantics and legacy productivity owner retirement while preserving released public selectors.
- Advances the baseline to 152 Java test classes / 751 `@Test` methods / 47 strict Chromium Playwright scenarios / 49 Vitest cases; PostgreSQL and Flyway remain unchanged at V47.
- Rollback remains application-image-only because this release adds no database migration; v27.37.5 was a browser-incomplete predecessor (31/47 Chromium), so v27.38.x must absorb and close the remaining parity failures before acceptance.

# v27.37.5 — Vue Calendar Selected-Day Island Lifecycle Hotfix

- Uses the completed v27.37.4 Chromium run (28 passed / 19 failed) to isolate the next shared Calendar migration lifecycle defect instead of treating the 19 failures as independent domain regressions.
- Preserves the selected-day `#panel` compatibility island across Vue Calendar route unmounts: CalendarPage now parks the island on `document.body` in `onBeforeUnmount()` and reattaches it to `#calendarLegacyPanelHost` when Calendar mounts again.
- Fixes the root cause behind repeated `renderChips() -> #chips.innerHTML` and related `null.hidden` browser exceptions after leaving Calendar for Settings/Tasks/other routes, without making mandatory selected-day descendants optional.
- Extends existing Java and Vitest contracts only; baseline remains 151 Java test classes / 743 `@Test` / 47 Chromium Playwright scenarios / 43 Vitest cases / Flyway V47.
- Makes no backend business-rule, API/OpenAPI, PostgreSQL/Flyway, timeout, retry or assertion weakening change. Independent Calendar mode/projection regressions remain intentionally outside this hotfix.

# v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix

- Aligns the Calendar/Timeline PWA bundle-budget regression contract with the canonical Maven project version instead of a stale hardcoded `27.37.1` literal that blocked `mvn verify` after 742 otherwise-green JUnit tests.
- Makes both the current PWA shell-cache assertion and `frontend/browser-bundle-budget.json` release assertion derive from `pom.xml`, preventing the same stale-version failure on future release bumps.
- Preserves the v27.37.3 selected-day island runtime hotfix unchanged; this release changes no business logic, API/OpenAPI contract, PostgreSQL schema, Flyway migration, timeout, retry policy or browser acceptance strictness.
- Adds no JUnit, Playwright or Vitest case; the baseline remains 151 Java test classes / 743 `@Test` / 47 Chromium Playwright scenarios / 43 Vitest cases / Flyway V47.
- Acceptance remains pending until exact frontend gate, Maven/JUnit, all 47 Chromium scenarios, immutable image smoke and staging deployment are green.

# v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix

- Fixes the second shared fresh-user boot failure revealed by the v27.37.2 Chromium trace: `selectDay(null)` still dereferenced the retired legacy Calendar `#layout` during `loadProfile() -> applyRoute()`.
- Treats only the retired `#layout` wrapper as optional while keeping the preserved selected-day editor `#panel` strict, matching the bounded compatibility-island architecture.
- Lets boot reach `maybeShowOnboarding()` instead of throwing before `#firstRunOnboarding` becomes visible, removing the common ~30-second timeout cause shared by unrelated browser scenarios.
- Extends the existing Calendar/Timeline migration source contract without adding a new JUnit, Playwright or Vitest case; the baseline remains 151 classes / 743 `@Test` / 47 Playwright / 43 Vitest.
- Changes no API, OpenAPI shape, business rule, PostgreSQL schema, Flyway migration, npm graph, retry policy, browser timeout or CI runner routing.

# v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix

- Fixes the shared fresh-user Playwright timeout cascade after the Calendar/Timeline Vue owner retires legacy month-navigation controls.
- Makes legacy `applyRoute()` update `#prev`, `#todayBtn` and `#next` only when those controls still exist, preventing a `null.style` exception during `loadProfile()`.
- Restores the first-run onboarding path so `#firstRunOnboarding` can become visible and the existing onboarding helper can continue to module/profile persistence.
- Extends the existing Calendar/Timeline migration source contract without adding a new JUnit or Playwright scenario, so the 151 classes / 743 `@Test` / 47 Playwright / 43 Vitest baseline is unchanged.
- Changes no API, OpenAPI shape, business rule, PostgreSQL schema, Flyway migration, npm graph, Calendar ownership boundary, retry policy or browser timeout.

# v27.37.1 — Vue Calendar & Timeline Strict Typecheck Hotfix

- Fixes the four strict `vue-tsc` failures found by the first v27.37.0 frontend gate.
- Adds explicit `string` / `CalendarMode` types to the public Calendar/Timeline bridge callback.
- Moves Pinia action defaults that referenced `this` into typed action bodies, preserving the same runtime behavior without implicit-`any` context.
- Adds four compile-gated Java source contracts and keeps strict TypeScript enabled.
- Changes no API, OpenAPI contract, business rule, PostgreSQL schema, Flyway migration, npm graph, route ownership or browser expectation.

# v27.37.0 — Vue Calendar & Timeline

- Migrates Today plus Calendar Month, Week and Day read surfaces to Vue 3 with strict TypeScript and one canonical generated-API range store.
- Adds focused-date navigation, persisted mode/focus, stale-read protection, hourly timeline composition and optimistic calendar-layer visibility with rollback.
- Retires legacy Today/Calendar read owners while preserving the mature selected-day mutation editor as one named compatibility island.
- Adds 10 Vitest cases, two strict Chromium scenarios, 11 compile-gated Java migration contracts and explicit single-owner browser acceptance.
- Establishes the v27.37.0 quality baselines: accepted PWA cache-upgrade ADR/E2E and fail-closed raw/gzip browser-bundle budgets.
- Keeps Spring Boot authoritative; OpenAPI remains 98 operations / 103 schemas, PostgreSQL and Flyway remain unchanged at V47.

# v27.36.8 — Vue Read Sequencing Static Contract Alignment Hotfix

- Aligns three stale Java implementation-string contracts with the accepted shared `readSequence` runtime introduced in v27.36.7.
- Updates the historical browser-parity and migration guards to require the winning shared read before projection publication.
- Updates the Single-Pass regression guard to the canonical account-snapshot Vitest scenario name.
- Adds a compile-gated regression class that forbids `refreshSequence` from returning to the migrated domain contracts.
- Changes no Vue runtime behavior, backend API, OpenAPI, PostgreSQL schema, Flyway migration, CI routing or Playwright expectation.

# v27.36.7 — Time Bank Period Toggle Snapshot Stability Hotfix

- Stops month/year toggles from reloading and replacing the canonical overtime account snapshot.
- Adds a period-only loader for compensation, integrity and actual-work ranges while preserving credits, usages and balance in memory.
- Uses one shared read sequence so fast toggles and later full refreshes cannot let stale responses win.
- Adds store/Vitest coverage for account identity stability, month/year races and full-refresh supersession.
- Preserves the strict 45-scenario Chromium contract, single-pass staging validation, backend API, OpenAPI, PostgreSQL schema and Flyway V47.

# v27.36.6 — Time Bank Usage-Date Chart Parity Hotfix

- Closes the final deterministic Chromium parity failure from v27.36.5: Time Bank chart usage bars are now bucketed by the actual `usageDate`, while earned bars remain bucketed by credit `workedDate`.
- Stops reusing aggregate `credit.usedHours` as a dated usage event, preventing double counting and restoring the missing standalone usage-day column.
- Adds daily and yearly Vitest coverage for `2026-08-01 +3`, `2026-08-02 +2`, `2026-08-03 −4`, including the folded `2026-08 +5/−4` result.
- Adds a compile-gated Java source contract preserving the strict Chromium locator and the separation between earned-source dates and usage-event dates.
- Preserves the v27.36.5 single-pass CI routing and changes no Spring Boot business rule, OpenAPI shape, npm dependency graph, PostgreSQL schema or Flyway migration.

# v27.36.5 — Single-Pass CI & Final Vue Browser Parity Hotfix

- Closes the final two deterministic Chromium gaps from v27.36.4: the Time Bank period selector now exposes explicit ARIA boolean tokens immediately, and the Absence Composer refreshes authoritative balance/type context before opening.
- Makes month/year ownership optimistic while preserving server refresh sequencing, so accessibility state no longer waits on the full multi-read-model request.
- Adds Vitest coverage for optimistic period selection and stale-loaded-account refresh before composer launch.
- Routes push validation through a single owner: ordinary CI skips only pushes to `test`, while `Deploy staging` keeps the complete Vue, Maven, static, Chromium, immutable-image and clean-PostgreSQL path.
- Keeps pull-request and non-staging branch CI fully independent; no test, coverage threshold, browser assertion or deployment smoke is removed.
- Changes no Spring Boot business rule, OpenAPI shape, npm dependency graph, PostgreSQL schema or Flyway migration.

# v27.36.4 — Vue Absence & Time Bank Browser Parity Hotfix

- Completes browser parity for the first Vue domain after the 45-scenario Chromium run reached the migrated Absence and Time Bank runtime and exposed eight deterministic ownership/projection gaps.
- Removes the retired legacy Time Bank guide modal, preserves Today/Calendar routes for modal launches and moves absence deletion into the visible Vue edit modal.
- Publishes a typed post-refresh projection event so the still-legacy Calendar, Today and selected-day panels update after Vue absence/credit mutations without restoring legacy route ownership.
- Exposes usage ratio and oldest FIFO credit on the Time Bank overview, keeps period insights on Credits and delegates selected-day absence editing to the Vue domain owner.
- Adds Vitest and Java static contracts for projection publication, route preservation, unique global IDs, visible modal deletion and one-way Vue-to-legacy projection synchronization.
- Changes no Spring Boot business rules, OpenAPI shape, npm dependency graph, PostgreSQL schema or Flyway migration.

# v27.36.3 — CI Artifact Quota Resilience Hotfix

- Makes JaCoCo and Playwright artifact publication non-blocking so a GitHub storage-quota exhaustion cannot stop static checks, image build, clean PostgreSQL smoke or deployment.
- Uploads only `jacoco.xml` and `jacoco.csv`, keeps reports for three days and uses run/attempt-qualified artifact names.
- Uploads Playwright reports only after a failure, with three-day retention and missing-file tolerance in both CI and staging validation.
- Preserves the fully compiled v27.36.2 Vue Absence & Time Bank runtime, Maven/JUnit behavior, OpenAPI contract, npm graph, PostgreSQL schema and Flyway V47 unchanged.

# v27.36.2 — Vue Timer Static Contract Compile Coverage Hotfix

- Fixes the Java 17 `testCompile` failure in the timer regression contract by replacing an illegal multiline string literal with whitespace-normalized source matching.
- Renames the timer test to `VueBrowserTimerHandleTypeFrontendContractTest` so the established static-contract compiler always includes it.
- Broadens the local Java syntax gate to compile both `*FrontendContractTest.java` and source-only `*HotfixTest.java` files, closing the coverage gap that allowed the malformed test to reach CI.
- Preserves the v27.36.1 browser timer implementation, Absence/Time Bank runtime, generated OpenAPI contract, npm graph, backend API, PostgreSQL schema and Flyway V47 unchanged.

# v27.36.1 — Vue Browser Timer Handle Type Hotfix

- Uses explicit browser `window.setTimeout` / `window.clearTimeout` handles in the Absence Composer and credit editor debounce flows.
- Resolves strict `vue-tsc` DOM/Node timer overload ambiguity without weakening TypeScript or changing debounce behavior.
- Adds static regression contracts for both components and preserves the v27.36.0 Absence & Time Bank migration unchanged.
- Changes no backend API, OpenAPI contract, npm dependency graph, database schema, Flyway migration or domain ownership.

# v27.36.0 — Vue Absence & Time Bank

- Migrates the complete Absence and Time Bank domain to Vue 3 with strict TypeScript: unified absence composer, absence journal, responsive overtime ledger, plan/fact summary, integrity status, exact credit editor, reusable scenarios, usage ownership and FIFO forecast.
- Keeps Spring Boot authoritative for allowance arithmetic, overlap validation, compensation, exact credited minutes, ownership, FIFO reservation/posting/reversal, accounting periods and ledger integrity.
- Extends the generated OpenAPI contract with typed quick-scenario CRUD, named absence preview rows/type summaries and correct array/allOf generation; the committed client now covers 98 operations and 103 schemas without hand-written transport.
- Adds Q-06 stale-read, duplicate-submit and HTTP 409 recovery guards, four Vitest model scenarios and six store/concurrency scenarios, and a Playwright migration scenario proving one runtime owner and one mutation under a native double click.
- Retires the legacy Absence and Time Bank route/modal owners at runtime while preserving only named typed adapters required by still-legacy Today and Calendar actions.
- Changes no PostgreSQL schema or Flyway migration; rollback is an application-image rollback to v27.35.7.

# v27.35.7 — Docker Frontend OpenAPI Build Context Hotfix

- Copies the canonical backend OpenAPI YAML into the Docker frontend stage before the generated-contract drift check.
- Preserves the committed authentic npm graph, strict typecheck, 16 Vitest cases, Vite build and one-image Spring Boot topology.
- Adds binding regression guards for canonical source path, Docker ordering and absence of a frontend contract duplicate.
- Changes no runtime behavior, API, OpenAPI content, schema, Flyway migration or domain ownership.

# v27.35.6 — Gate A Historical Static Contract Alignment Hotfix

- Aligns four stale test-only architecture contracts with the promoted committed-lockfile and pinned-delivery foundation.
- Requires tracked `frontend/package-lock.json`, semantic green-acceptance wording, recursive Flyway V1–V47 discovery and the exact pinned Node image.
- Preserves the fully green frontend gate, authentic npm graph, production Java sources, API, OpenAPI, PostgreSQL, Flyway files and one-image topology.
- Adds release guards for all four corrected expectations before the next domain migration.

# v27.35.5 — Gate A Quality Register Lambda Capture Compile Hotfix

- Fixes the Maven `testCompile` blocker in `VueDeliveryContractsDiagnosticsFoundationTest`: the loop counter is no longer captured directly by a lambda.
- Introduces an immutable `rowPrefix` per iteration, preserving the Q-02–Q-05 Engineering Quality Register assertions without weakening them.
- Adds a release guard for the effectively-final capture pattern and for the forbidden direct loop-counter capture.
- Keeps the fully green frontend gate from `v27.35.4`, authentic committed npm graph, API, OpenAPI, PostgreSQL, Flyway V47, runtime code and one-image topology unchanged.

# v27.35.4 — Frontend Gate Static Contract Java Escaping Hotfix

- Fixes the only Maven `testCompile` blocker in `AuthenticLockfileCommitGeneratedClientFixtureHotfixTest`: the expected shell command now escapes the embedded Java string quotes correctly.
- Keeps the authentic committed npm graph, clean-checkout `npm ci`, successful `vue-tsc`, 16/16 Vitest and Vite delivery path unchanged.
- Adds a release guard for the exact escaped Java source contract so this specific compile regression cannot return unnoticed.
- Keeps API, OpenAPI, PostgreSQL, Flyway V47, domain ownership, runtime code and one-image topology unchanged.

# v27.35.3 — Authentic Lockfile Commit & Generated Client Fixture Hotfix

- Commits the authentic npm graph generated by GitHub Actions run `30906521813`; registry tarballs, SHA-512 integrity and dependency edges are now reproducible source inputs.
- Restores normal clean-checkout delivery: verify committed graph → `npm ci` → `vue-tsc` → Vitest → Vite. CI and Docker no longer regenerate dependency resolution.
- Fixes `generatedClient.spec.ts` so each mocked request receives a fresh `Response`; the production HTTP client remains unchanged.
- Marks Q-01–Q-05 implemented; Gate A is accepted only after full green CI/staging.
- Keeps API, OpenAPI, PostgreSQL, Flyway V47, domain ownership and one-image topology unchanged.

# v27.35.2 — Authentic npm Lockfile Bootstrap Hotfix

- Removed the synthetic frontend lockfile after GitHub Actions proved that its flat dependency table could launch `vue-tsc` but produced an internally incompatible Volar graph.
- Added exact-toolchain lockfile bootstrap with Node `20.18.1` and npm `10.8.2`: `npm install --package-lock-only --ignore-scripts`, authenticity verification, then `npm ci`.
- Added structural lockfile guards for registry tarballs, SHA-512 integrity, dependency/peer edges and the complete Vue/Volar/TypeScript chain.
- Added `if: always()` GitHub Actions artifacts for the generated lockfile and SHA-256 manifest so the exact CI graph can be promoted in `v27.35.3`.
- Applied the same bootstrap → verify → `npm ci` sequence in the Docker Node stage.
- Reopened Q-01 and Gate A honestly; no product-domain Vue migration may begin until the generated lockfile is committed and passes clean-checkout CI without regeneration.
- Kept API, OpenAPI operations, PostgreSQL, Flyway V47, FIFO, Payroll, strict TypeScript and one-image deployment unchanged.
- Regression baseline advances to 139 Java test classes, 669 `@Test` methods, 44 Playwright scenarios and 16 Vitest cases.

# v27.35.1 — Frontend Lockfile Executable Resolution Hotfix

- Corrected the frontend lockfile metadata that left `vue-tsc`, `vitest` and `vite` package directories installed without local `node_modules/.bin` launchers.
- Pinned the released npm tarball and `bin` mapping for the three build CLIs and TypeScript.
- Added immediate post-`npm ci` executable checks and `npm ls --all` to both the shared frontend gate and Docker Node stage.
- Strengthened `verify:delivery` so malformed CLI metadata or missing installed launchers fail before typecheck.
- Kept `npm ci`, exact Node/npm pins, OpenAPI drift protection, strict TypeScript and the no-`npx`/no-global-tool boundary.
- Kept product behavior, API, PostgreSQL, Flyway V47, FIFO, Payroll and one-image deployment unchanged.
- Regression baseline advances to 138 Java test classes, 665 `@Test` methods, 44 Playwright scenarios and 16 Vitest cases.

# v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation

- Added a committed frontend lockfile and replaced mutable frontend installs with `npm ci` in local gates, GitHub Actions and the Docker Node stage.
- Pinned Node `20.18.1`, npm `10.8.2` and exact dependency versions across package metadata, CI and Docker.
- Added deterministic OpenAPI-to-TypeScript schema and operation generation, a committed generated contract, drift gate and operationId-based typed client.
- Added correlated `X-Request-Id`, route and release diagnostics to the shared frontend transport and API errors; query strings are discarded and diagnostic values are bounded.
- Added Vue descendant/global error capture, unhandled rejection diagnostics and controlled recovery UI without hiding failures from strict Playwright collection.
- Added the domain migration manifest/parity template, ADR repository with ADR-001–ADR-005, dependency/vulnerability policy and repository copies of the binding migration standard and quality register.
- Closed Engineering Quality Register Q-01–Q-05 and Gate A before `v27.36.0 — Vue Absence & Time Bank`.
- Kept domain ownership, API behavior, PostgreSQL, Flyway V47, FIFO, Payroll and one-image deployment unchanged.
- Regression baseline advances to 137 Java test classes, 661 `@Test` methods, 44 Playwright scenarios and 16 Vitest cases.

# v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix

- Split canonical overtime draft preview from strict persistence validation: calculated previews may return zero or negative credited minutes, while create/update still reject non-positive credits.
- Added service and MockMvc regressions proving that a full shift minus its plan returns HTTP `200` with `0` credited minutes but cannot be persisted.
- Added active-state and `aria-current` propagation for Vue secondary routes through the visible More control and the matching modal item.
- Updated the Vue shell E2E to follow the released secondary-navigation surface instead of assuming Tasks belongs to primary navigation.
- Updated the Overtime scenario E2E to require the zero-hour draft preview to succeed before extending the interval by two hours.
- Kept strict TypeScript, strict browser runtime collection, API shape, PostgreSQL, Docker topology and Flyway V47 unchanged.
- Regression baseline advances to 136 Java test classes, 652 `@Test` methods, 44 Playwright scenarios and 11 Vitest cases.

# v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix

- Migrated historical Playwright navigation from hidden legacy chrome to the released Vue/legacy public navigation bridge.
- Added stable Vue shell test hooks for brand, profile, more menu, logout and close actions without exposing mutable legacy state.
- Updated shell, mobile, onboarding, settings, timezone, tasks and overtime scenarios to assert visible Vue chrome while preserving legacy product-screen ownership.
- Declared full onboarding explicitly where a scenario requires optional modules instead of assuming the default Work preset enables Tasks.
- Updated Calendar Sync ICS identity coverage from stale `27.33.0` to the current release.
- Kept strict browser error collection, API, PostgreSQL schema, Docker topology and Flyway V47 unchanged.
- Regression baseline advances to 135 Java test classes, 648 `@Test` methods, 44 Playwright scenarios and 11 Vitest cases.

# v27.34.2 — Vue Browser Runtime Bundle Hotfix

- Replaced the Vite library-mode `process.env.NODE_ENV` reference with the literal production value so the Vue shell can execute in browsers without a Node `process` global.
- Added a post-build browser-bundle audit that rejects unreplaced `process.env`, CommonJS `require(...)` / `module.exports` and Node-only path globals.
- Wired the audit into `npm run build`, so CI and the isolated Docker Node stage validate the same generated JavaScript before Maven and Playwright.
- Kept Playwright page-error collection strict; the hotfix removes the runtime error instead of filtering it.
- Kept strict TypeScript, dependencies, product behavior, API, PostgreSQL schema, Docker topology and Flyway V47 unchanged.
- Regression baseline advances to 134 Java test classes, 647 `@Test` methods, 44 Playwright scenarios and 11 Vitest cases.

# v27.34.1 — Vue Strict Type Contract Hotfix

- Fixed strict `vue-tsc` failures caused by explicitly passing `undefined` into optional native button attributes under `exactOptionalPropertyTypes`.
- Kept `exactOptionalPropertyTypes` enabled and normalized `aria-current`, `type` and `disabled` bindings to valid concrete values.
- Removed the unsupported Vite 5 `LibraryOptions.cssFileName` field while preserving the stable CSS filename through Rollup asset output.
- Added a regression contract that rejects the three undefined bindings and the unsupported Vite option.
- Kept the Vue shell architecture, API, PostgreSQL schema, Docker topology and Flyway V47 unchanged.
- Regression baseline advances to 133 Java test classes, 646 `@Test` methods, 44 Playwright scenarios and 11 Vitest cases.

# v27.34.0 — Vue App Shell & Design System

- Moved the visible DutyLog brand, profile entry, primary/secondary navigation and network state from numbered JavaScript into Vue 3 + TypeScript.
- Added an immutable legacy shell read model for route, workspace navigation, enabled views, language, module readiness and safe profile display fields.
- Added named bridge capabilities for navigation, modal opening, logout and state subscription without exposing mutable legacy state or product DOM.
- Added typed shared `UiButton`, `UiBadge`, `UiCard`, `UiTabs`, `UiEmptyState`, `UiModal`, `ToastHost` and `AppIcon` primitives.
- Added responsive desktop/sidebar/mobile shell chrome, bottom navigation, bottom-sheet behavior, visible focus and reduced-motion support.
- Kept legacy product screens and hash routing authoritative; old chrome hides only after Vue readiness so failed boots retain a usable fallback.
- Kept one Spring Boot JAR/image, one app container, the released API/PostgreSQL schema and Flyway V47.
- Regression baseline advances to 132 Java test classes, 645 `@Test` methods, 44 Playwright scenarios and 11 Vitest cases.

# v27.33.0 — Vue Frontend Foundation & CI/CD

- Added the first production Vue 3 + TypeScript + Vite runtime inside the existing Spring Boot application and same-origin session boundary.
- Introduced a strict `frontend/` migration boundary with Pinia, memory-history Vue Router, typed API/CSRF client, unit tests and an explicit legacy bridge.
- Kept the released legacy shell fully functional while mounting a headless Vue foundation that can be awaited through `window.__dutylogVueReady`.
- Added a Node build stage to the single DutyLog Docker image; Maven packages `frontend/dist` under `static/vue`, and the Docker context explicitly keeps `frontend/package.json`, so production still uses one app container plus PostgreSQL.
- Extended CI, staging validation and production validation with frontend install, typecheck, Vitest and Vite build gates before Maven and Playwright.
- Added source, packaging and browser regression contracts for the Vue boundary without changing the HTTP API, PostgreSQL schema or Flyway V47.
- Established the migration rule: no new large feature is added to numbered legacy JavaScript; domains move completely to Vue and their old code is deleted after parity.
- Regression baseline advances to 131 Java test classes, 640 `@Test` methods and 43 Playwright scenarios.

# v27.32.1 — Time Bank Absence Navigation Hotfix

- Fixed the real browser navigation defect where «Открыть отсутствие» routed to Vacation but left Unified Absence Composer hidden.
- Added one canonical `openAbsenceEditor(...)` boundary that refreshes a missing absence, loads the current bank, opens the owning record in the modal and builds its FIFO preview.
- Preserved inline editing inside the Absence workspace while making cross-workspace navigation modal and explicit.
- Kept API, PostgreSQL, FIFO ownership, Payroll and Flyway V47 unchanged.
- Documented the approved full Vue 3 + TypeScript + Vite migration before further product features.
- Regression baseline advances to 130 Java test classes, 635 `@Test` methods and 42 Playwright scenarios.

# v27.32.0 — Absence & Time Bank Experience

- Made **Отпуск и отсутствия** the primary event journal while preserving Overtime as the canonical time-bank read model.
- Split the time bank into Overview, Credits, Bank Usage and FIFO Detail views.
- Separated posted usage from future reserved time and exposed the truly free balance.
- Added absence-to-usage and usage-to-absence navigation without restoring linked-usage editing.
- Added oldest-first FIFO forecasting, including what will be spent next, remaining balance and shortage.
- Restored the edited absence's own allocations during preview so an existing reservation never competes with itself.
- Added absence filters, actionable empty states, contextual guidance and the first repeatable product-learning journey.
- Closed the release-check matcher coprocess explicitly so the historical gate exits after reporting its result.
- Kept the API, PostgreSQL, Payroll, canonical ownership and Flyway V47 unchanged.
- Regression baseline advances to 129 Java test classes, 633 `@Test` methods and 42 Playwright scenarios.

# v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix

- Aligned two Playwright scenarios with absence-owned linked-usage behavior after the canonical retirement release.
- Moved the intentional `409 DIRECT_USAGE_RETIRED` probe to Playwright `APIRequestContext`, outside the strict browser console/network failure monitor.
- Replaced stale `data-edit-usage` expectations with read-only linked-usage assertions and editing/deletion through the owning absence.
- Preserved the Overtime journal, FIFO allocations, credits and remaining-balance checks.
- Added `CanonicalAbsenceBrowserContractAlignmentHotfixTest` to prevent restoration of standalone linked-usage browser controls.
- Production runtime, API, PostgreSQL, Payroll and Flyway V47 are unchanged.

# v27.31.1 — Canonical Absence Static Contract Alignment Hotfix

- Aligned five historical Maven source contracts with the intentional v27.31.0 canonical absence frontend shape.
- Kept direct coverage serialization so imported `HOURS_ONLY` rows remain truthful.
- Updated legacy migration contracts to the actual `openLegacyUsageMigration(focusId)` and `renderLegacyUsageMigrationPreview(preview)` boundaries.
- Removed stale expectations that linked time-off usages behave like editable legacy manual usages; transitional legacy deletion remains available until promotion.
- Added a focused regression guard that requires canonical ownership strings and forbids retired legacy-write strings.
- Production JavaScript, API, PostgreSQL, FIFO, Payroll and Flyway V47 are unchanged.
- Regression baseline advances to 127 Java test classes, 626 `@Test` methods and 41 Playwright scenarios.

# v27.31.0 — Canonical Absence Ledger & Legacy Retirement

- Made Unified Absence Composer the only user-facing write path for vacation, overtime-backed time off, sick leave, unpaid leave and custom absences.
- Retired new direct overtime usage creation and direct legacy usage editing with explicit `409` contracts.
- Kept the Overtime workspace, credit creation, earned/used/remaining statistics, FIFO queue, allocations and exports.
- Added previewed one-time promotion of old MANUAL usages into canonical TIME_OFF absences while preserving usage IDs and FIFO allocation rows.
- Added transition-only `HOURS_ONLY` coverage so imported durations remain truthful when the historical start/end interval is unknown.
- Routed Telegram `/timeoff` through the canonical absence service instead of creating a detached usage.
- Added service, HTTP, static and Chromium regression coverage for direct-write retirement, legacy promotion, linked ownership and balance restoration.
- Added forward-only Flyway V47 to extend the immutable V42 absence coverage/shape constraints for truthful `HOURS_ONLY` legacy rows without rewriting existing data.
- Kept Payroll, Unified Ledger, Calendar Sync and all released migrations V42–V46 unchanged.
- Regression baseline advances to 126 Java test classes, 625 `@Test` methods and 41 Playwright scenarios.

# v27.30.2 — Today Overtime Journal Contract Hotfix

- Fixed the single stale Maven assertion that still required the removed Today direct overtime-credit shortcut.
- Confirmed the Today overtime card is a journal link and correctly routes to the Overtime workspace.
- Kept credit creation available from global Quick Add and the Overtime workspace.
- Added focused regression coverage for the Journal → Overtime boundary and ownership of the credit modal.
- Kept production behavior, API, PostgreSQL, Payroll, Unified Ledger, absence projection and Flyway V46 unchanged.
- Regression baseline advances to 125 Java test classes, 620 `@Test` methods and 40 Playwright scenarios.

# v27.30.1 — Unified Absence Quick Access Integration

- Added a direct neutral absence action to the Today dashboard for the current date.
- Kept the global plus action neutral and owned by Vacation/Absences rather than Overtime.
- Preserved the contextual Overtime entry point with `TIME_OFF / OVERTIME_BANK` preselection.
- Replaced the Today direct overtime-credit shortcut; overtime credit creation remains available from global Quick Add and the Overtime workspace.
- Fixed Quick Add keyboard focus when Vacation is the only enabled mutation module.
- Added focused frontend and browser coverage for global, Today and Overtime entry points.
- Kept API, PostgreSQL, Payroll, Unified Ledger, calendar projection and Flyway V46 unchanged.
- Regression baseline advances to 124 Java test classes, 619 `@Test` methods and 40 Playwright scenarios.

# v27.30.0 — Unified Absence Composer & Calendar Projection

- Unified Vacation, overtime-backed time off, sick leave, unpaid leave and custom absence entry in one reusable composer.
- Routed global quick-add and new overtime usage entry points through the canonical absence service and linked FIFO usage boundary.
- Added dynamic source/balance context for vacation allowance, overtime bank, sick policy, unpaid leave and no-balance categories.
- Kept the planned shift as plan data while projecting full-day absence as the factual state and partial absence as a typed time strip.
- Added type glyphs and status-aware calendar treatment so meaning is not communicated by color alone.
- Kept existing direct overtime usages editable for compatibility; no API or database migration is required.
- Refreshed composer balance data on every open so newly created overtime credits are immediately available to FIFO-backed time off.
- Corrected the pre-push Maven ICS version assertion and the three bounded browser expectations without changing the public API or schema.
- Isolated `TelegramLinkDetachedOwnerIntegrationTest` in a dedicated H2 database so its `@DirtiesContext` / `create-drop` shutdown cannot erase the shared test schema under IntelliJ's class order.
- Regression baseline advances to 123 Java test classes, 616 `@Test` methods and 39 Playwright scenarios; Flyway remains V46.

# v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix

- Preserved the explicit Custom Workspace Today-card order across `/api/profile` sanitization and autosave responses.
- Kept Shift mandatory without forcing it back to the first position when the user deliberately moved another card above it.
- Added an HTTP persistence regression and a focused source contract for the server-side order boundary.
- Kept the strict 38-scenario Playwright fixture, Workspace Studio UI, API shape, PostgreSQL and Flyway V46 unchanged.
- Regression baseline advances to 122 Java test classes, 614 `@Test` methods and 38 Playwright scenarios.

# v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix

- Fixed Custom Workspace creation to inherit the active preset's Today cards together with its navigation.
- Preserved the required Shift card and the existing bounded widget allowlist.
- Strengthened the Workspace Studio browser scenario at the inheritance boundary without weakening the strict fixture.
- Added a focused regression contract for preset-to-custom Today widget cloning.
- Kept API, OpenAPI, PostgreSQL, Payroll, Unified Ledger and Flyway V46 unchanged.
- Regression baseline advances to 121 Java test classes, 612 `@Test` methods and 38 Playwright scenarios.

# v27.29.1 — Theme Package Token Scope Contract Hotfix

- Fixed the single false Maven assertion in `WorkspaceLayoutThemeStudioFrontendContractTest` by matching the actual JavaScript theme-token template literal instead of searching for Java-only escape backslashes.
- Added a dedicated regression contract that distinguishes Java source escaping from runtime JavaScript file content.
- Preserved the real theme registry, token scopes, package isolation, decoration metadata and all 38 Playwright scenarios unchanged.
- Kept production JavaScript, CSS, profile persistence, API, OpenAPI, PostgreSQL and Flyway V46 unchanged.
- Regression baseline advances to 120 Java test classes, 611 `@Test` methods and 38 Playwright scenarios.

# v27.29.0 — Workspace, Layout & Theme Studio

- Advanced DutyLog UI Core to contract v2 and turned the declarative foundation into a user-facing Workspace Studio.
- Added safe custom navigation ordering/visibility with mandatory Today and Settings routes and a five-item primary-navigation limit.
- Added independent Today-card ordering/visibility while keeping the Shift card mandatory and preserving all hidden data.
- Added Sidebar and Mobile Flow layout packages without duplicating screens or business logic.
- Added compact calendar density, pill/dot schedule-layer presentation and a pointer-free calm-grid decoration.
- Extended the profile whitelist and synchronous bootstrap for the new UI contract while preserving theme package isolation and rejecting arbitrary CSS/JavaScript.
- Kept Payroll, Ledger, Vacation, Calendar APIs, PostgreSQL and Flyway V46 unchanged.
- Regression baseline advances to 119 Java test classes, 610 `@Test` methods and 38 Playwright scenarios.

# v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix

- Added forward-only Flyway V46 to align `payroll_snapshots.calculation_hash` with the existing JPA `VARCHAR(64)` contract.
- Preserved the released V45 checksum and converted existing fixed-width values with `BTRIM(...)` during the type change.
- Kept the `NOT NULL` invariant and the existing lowercase 64-character SHA-256 check constraint.
- Added a regression contract that pins the immutable V45 hash and guards V46 plus the entity mapping.
- Kept Payroll calculations, API, OpenAPI, UI, snapshot revision semantics and all 37 Playwright scenarios unchanged.
- Regression baseline advances to 118 Java test classes, 605 `@Test` methods and 37 Playwright scenarios; Flyway advances to V46.

# v27.28.2 — Calendar Persistence Reload Readiness Hotfix

- Exposed one bounded calendar-navigation readiness promise for Month, Week and Day header navigation.
- Made the persistence E2E wait for the complete calendar + ledger projection before intentionally reloading the page.
- Added the same application-idle guard to the shift-delete reload path.
- Preserved the strict browser fixture: no `Failed to fetch` console suppression or broad network-error allowlist was added.
- Kept Payroll runtime, API, OpenAPI, PostgreSQL and Flyway V45 unchanged.
- Regression baseline advances to 117 Java test classes, 604 `@Test` methods and 37 Playwright scenarios.

# v27.28.1 — Payroll Module Registry Contract Hotfix

- Fixed the Payroll static contract to verify the canonical `ModuleKeys.PAYROLL` registration instead of requiring the unrelated literal `ModuleService.PAYROLL`.
- Preserved `DutyLogModules` as the single registry with its existing static `ModuleKeys.*` import and `TIME_ACCOUNTING` Payroll contract.
- Kept production runtime, API, OpenAPI, PostgreSQL and Flyway V45 unchanged.
- Regression baseline remains 116 Java test classes, 603 `@Test` methods and 37 Playwright scenarios.

# v27.28.0 — Payroll Foundation

- Added V45 owner-scoped payroll settings, append-only monetary adjustments and immutable versioned snapshots.
- Added a posted-only canonical payroll source to `TimeCompensationService`; Payroll never reinterprets calendar tables independently.
- Final calculation requires a closed period, healthy ledger and positive hourly rate.
- Added hourly-rate calculation in minor units with `HALF_UP`, transparent time/money subtotals and SHA-256 calculation hashes.
- Added `/api/v1/payroll` read/write endpoints, no-store period projections and module guards.
- Added a dedicated responsive Payroll workspace, preview, adjustments and revision history.
- Added service, static contract and Playwright coverage; Flyway advances to V45.

# v27.27.2 — Ledger Browser State & Visibility Hotfix

- Refreshed Vacation Planner on route entry so overtime credits created while the screen is hidden cannot leave a stale compensatory-time balance.
- Exposed bounded readiness promises for Vacation Planner and the full timezone-save refresh lifecycle.
- Completed the explicit expected-status browser contract for Chromium's generic resource console message without weakening unmarked HTTP or console failures.
- Made Overtime Next month-boundary safe and scoped Unified Ledger assertions to the visible desktop surface.
- Kept API, OpenAPI, PostgreSQL and Flyway V44 unchanged.
- Regression baseline advances to 114 Java test classes, 600 `@Test` methods and 36 Playwright scenarios.

# v27.27.1 — Ledger Workflow Browser Contract Hotfix

- Refreshed the Overtime read model on route entry so Vacation Planner mutations cannot leave stale Plan → Fact → Compensation totals.
- Serialized integrity and time-compensation reads to avoid concurrent FIFO reconciliation.
- Added explicit browser readiness helpers for ledger routes and application-idle reloads.
- Kept the strict runtime fixture while allowing only explicitly marked expected HTTP statuses such as `409 PERIOD_CLOSED`.
- Made the timezone projection scenario current-month safe and made posted-compensation scenarios explicitly `APPROVED`.
- Kept API, OpenAPI, PostgreSQL and Flyway V44 unchanged.
- Regression baseline advances to 113 Java test classes, 599 `@Test` methods and 36 Playwright scenarios.

# v27.27.0 — Ledger Integrity & Approval Workflow

- Added a full absence approval lifecycle: `DRAFT`, `PLANNED`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED` and `COMPLETED`.
- Split overtime-backed absences into `RESERVED` and `POSTED` usage states so planned requests cannot double-spend compensatory time.
- Added append-only `time_ledger_entries` with explicit reversals and closed-period adjustments instead of silent historical rewrites.
- Added accounting-period close/reopen controls, integrity reconciliation and stable conflict codes for locked periods.
- Closed periods now also protect planned-shift mutations from direct day edits, mobile sync, bulk fill, schedule-template apply and destructive shift-type deletion, while notes and day markers stay editable.
- Added explicit factual work intervals, including overnight work, while plan-as-fact remains the default simple mode.
- Extended the Plan → Fact → Compensation read model with actual-work provenance, reservation/posting totals, integrity state and period closure.
- Flyway V44 is additive and leaves `day_entries` unchanged.
- Regression baseline advances to 112 Java test classes, 598 `@Test` methods and 36 Playwright scenarios.

# v27.26.2 — Canonical Lineage Recovery

- Restored one canonical release line after an accidental branch rollback from the advanced v27.26.x tree to the earlier v27.22.x/v27.23.x line.
- Recovered External Calendar Sync, Calendar Comfort, Absence & Time-Off Overhaul and Unified Time & Compensation Ledger in one forward release without duplicating Flyway migrations.
- Preserved the current Workspace Route E2E navigation contract: Tasks remain reachable through `openView(...)`, while module enablement is asserted on `#view-tasks` instead of a workspace-hidden tab.
- Retained V41, V42 and V43 exactly once; `day_entries` remains unchanged and no rollback SQL is introduced.
- Added a lineage integrity contract and release gate covering calendar-sync hardening, modal-panel navigation, plan/fact absences and the canonical overtime ledger.
- Regression baseline advances to 110 Java test classes, 592 `@Test` methods and 35 Playwright scenarios.

# v27.26.1 — Absence Request Constructor Compile Hotfix

- Fixed Maven `testCompile` after the Unified Ledger DTO gained the explicit `compensationPolicy` field.
- Updated four `VacationPlannerServiceTest` fixtures from the removed nine-argument request shape to the full ten-argument `AbsencePeriodCreateRequest` contract.
- Preserved the intended `OVERTIME_BANK` source for partial, full-day and insufficient-balance time-off scenarios.
- Added a source contract and release guard that reject future nine-argument constructor calls before CI packaging.
- Kept production runtime, HTTP API, OpenAPI, PostgreSQL schema and Flyway V43 unchanged.
- Regression baseline advances to 110 Java test classes, 591 `@Test` methods and 35 Playwright scenarios.

# v27.26.0 — Unified Time & Compensation Ledger

- Unified planned shifts, factual absences and compensation sources into one minute-authoritative read model.
- Time off covered by previously worked hours now creates one absence-owned FIFO overtime usage; editing reallocates it and deleting the absence restores the balance.
- Added explicit `VACATION_ALLOWANCE`, `OVERTIME_BANK`, `SICK_PAY`, `UNPAID` and `NONE` compensation policies without deleting planned shifts.
- Added `/api/time-compensation` and `/api/v1/time-compensation`, a monthly Plan → Fact → Compensation UI, linked-usage locks and owner-scoped no-store responses.
- Flyway V43 converts the legacy standalone time-off balance to the oldest opening overtime credit and links existing time-off absences without changing `day_entries`.
- The deprecated settings balance remains wire-compatible but is no longer mutable authority; salary calculation is intentionally deferred to Payroll Foundation.
- Regression baseline advances to 110 Java test classes, 590 `@Test` methods and 35 Playwright scenarios.

# v27.25.2 — Absence Experience Frontend Contract Hotfix

- Aligned the Vacation Planner frontend contract with the accepted v27.25.0 plan/fact calendar composition.
- Week agenda absences remain intentionally bounded with `facts.absences.slice(0, 3)`.
- Partial absences stay timed in the hourly day, while full-day absences stay in the all-day rail.
- Added a regression contract that rejects the stale unbounded `for (const absence of facts.absences)` expectation.
- Kept production JavaScript, HTTP API, OpenAPI, PostgreSQL schema and Flyway V42 unchanged.
- Regression baseline advances to 109 Java test classes, 581 `@Test` methods and 34 Playwright scenarios.

# v27.25.1 — Absence Preview Lambda Compile Hotfix

- Fixed Java compilation in `VacationPlannerService.buildPreview(...)` by snapshotting the mutable loop date before the overlap-search lambda.
- Preserved preview semantics: counted days, shift conflicts, overlap detection and plan/fact projection use the same local date for each iteration.
- Added a static contract and release guard that reject direct capture of the incremented `date` loop variable.
- Kept Absence & Time-Off runtime behavior, HTTP API, OpenAPI, database schema and Flyway V42 unchanged.
- Regression baseline advances to 109 Java test classes, 580 `@Test` methods and 34 Playwright scenarios.

# v27.25.0 — Absence & Time-Off Overhaul

- Introduced a plan/fact absence model: planned shifts remain immutable while full-day absences become the factual calendar surface.
- Added partial time off with exact local start/end times, independent hour balances and stable `TIME_OFF_LIMIT_EXCEEDED` validation.
- Added `VACATION_DAYS`, `TIME_OFF_HOURS` and `NONE` balance policies, a built-in Time Off type, configurable full-day charging and per-type summaries.
- Added Month/Week/Day/selected-day composition, full-weight absence cells, planned-shift context, partial-time bars and monthly absence totals.
- Exported partial time off as timed `.ics` events while keeping full-day absences all-day and preserving source shift data.
- Flyway advances to V42; regression baseline advances to 109 Java test classes, 579 `@Test` methods and 34 Playwright scenarios.

# v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix

- Fixed the new mobile calendar comfort scenario to close the intentionally modal selected-day panel before navigating to another month.
- Preserved the real product contract: returning to today in Month mode selects the day and opens its details; the backdrop continues to block clicks through the panel.
- Added release guards for the explicit `#pClose` route, hidden panel state and removal of `with-panel` before the second header navigation.
- Kept Calendar Comfort runtime behavior, HTTP API, database and Flyway V41 unchanged.
- Regression baseline remains 108 Java test classes, 569 `@Test` methods and 33 Playwright scenarios.

# v27.24.0 — Calendar Comfort & Correctness

- Added a contextual, mobile-friendly «Сегодня» return control that appears only after the calendar leaves the current month/date and works across Month, Week and Day modes.
- Contextual important-day creation now inherits the selected calendar date instead of reusing a stale draft value; important-event checkboxes are constrained to the design-system size.
- Reworked the Today shift card for overnight intervals: compact time stays on one line while the two calendar dates move into a separate readable chip.
- Calendar refreshes preserve the last rendered month, show a calm non-blocking status and record bounded in-memory performance diagnostics without starting the final optimization cycle.
- Companion schedule layers now use a compact horizontally scrollable pill bar with explicit visibility state and accessible labels.
- Flyway remains V41; regression baseline advances to 108 Java test classes, 569 `@Test` methods and 33 Playwright scenarios.

# v27.23.2 — Calendar Sync Runtime Boot Hotfix

- Fixed the browser-wide `ReferenceError: localDateKey is not defined` raised while initializing the default `.ics` export range.
- Reused the canonical `keyOf(...)` helper for local floating calendar dates instead of introducing UTC conversion or another duplicate formatter.
- Added static Java and release-gate guards that reject the undefined helper before Playwright packaging.
- Kept External Calendar Sync business logic, HTTP API, nginx protection and Flyway V41 unchanged.
- Regression baseline advances to 107 Java test classes, 564 `@Test` methods and 32 Playwright scenarios.

# v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix

- Fixed the calendar-sync controller test helper to decode MockMvc JSON explicitly as UTF-8 instead of ISO-8859-1.
- Preserved the real token hint contract (`prefix…suffix`) and added a Unicode assertion using `\u2026`.
- Kept calendar feed issuance, rotation, revocation, nginx protection, HTTP API and Flyway V41 unchanged.
- Regression baseline remains 107 Java test classes, 563 `@Test` methods and 32 Playwright scenarios.

# v27.23.0 — External Calendar Sync

- Added RFC 5545 `.ics` export for one important event and owner-selected ranges up to 366 days.
- Composed shifts, planned tasks, important events and absences into one read-only external calendar without changing DutyLog source data.
- Added a private rolling subscription feed with 256-bit bearer tokens; only SHA-256 digests and non-secret hints are stored.
- Added explicit issue/rotate/revoke lifecycle, old-token invalidation, module guards, no-store responses and stable Web/v1/OpenAPI contracts.
- Hardened supplied nginx edge configs with an exact `/calendar-feed.ics` location and `access_log off`, preventing bearer URLs from entering reverse-proxy access logs.
- Added a responsive Settings integration panel, one-time secret display and per-event export from important-event details.
- Flyway advances to V41; regression baseline advances to 107 Java test classes, 563 `@Test` methods and 32 Playwright scenarios.

# v27.22.2 — Workspace Route E2E Navigation Hotfix

- Replaced direct clicks on the workspace-hidden Tasks tab with the shared `openView(page, "tasks")` product-route helper.
- Kept Shift Worker navigation unchanged: Tasks remain available through the route without returning to the primary tab bar.
- Module enablement is asserted on the Tasks view itself, while workspace placement remains an independent layout decision.
- Kept production JavaScript behavior, HTTP API, Flyway V40 and the regression baseline unchanged at 103 Java test classes, 544 `@Test` methods and 31 Playwright scenarios.

# v27.22.1 — Vacation Planner Frontend Contract Hotfix

- Aligned the Shift Worker workspace contract with the new `vacation` navigation route and the actual Today widget order.
- Replaced the invented `vacation-day` static expectation with the real all-day composition contract: `facts.absences` → `type:"vacation"` → `editAbsenceFromOccurrence`.
- Replaced the brittle hardcoded count of seven persisted switchable modules with a count derived from `DutyLogModules.ALL`; Vacation Planner is the eighth switchable non-admin module.
- Kept Vacation Planner runtime behavior, HTTP API, Flyway V40 and the regression baseline unchanged at 103 Java test classes, 544 `@Test` methods and 31 Playwright scenarios.

# v27.22.0 — Vacation Planner

- Added owner-scoped vacation settings, absence types and planned/approved absence periods as an independent domain.
- Added annual allowance, carryover, configurable work-year boundaries and `CALENDAR_DAYS` / `WEEKDAYS` counting without country-specific holiday assumptions.
- Added 14/28/35-day presets, per-day preview, shift-conflict warnings, overlap rejection and per-work-year allowance enforcement; cross-year preview reports the most constrained balance.
- Serialized allowance-sensitive writes per owner to prevent concurrent overbooking and validate rule changes against every stored work year.
- Added additive calendar `absences` projections across Month, Week, Day and focused-day details without mutating shift rows.
- Added responsive Vacation Planner UI, custom types, stable Web/v1 API aliases, OpenAPI documentation and browser coverage.
- Flyway advances to V40; regression baseline advances to 103 Java test classes, 544 `@Test` methods and 31 Playwright scenarios.

# v27.21.2 — Schedule Accordion E2E Selector Hotfix

- Added strict `openDayModuleById()` Playwright infrastructure for day panels that share one product module key.
- Updated the schedule-template browser flow to open `#accSched` explicitly instead of resolving both `#accShift` and `#accSched` through `[data-day-module="shifts"]`.
- Kept the generic `openDayModule()` helper strict for modules that still have a one-to-one selector contract; no `.first()` fallback masks duplicate surfaces.
- Added release-gate assertions for the exact helper export and schedule accordion route.
- No runtime, API, database or Flyway changes; the baseline remains 100 Java test classes, 525 `@Test` methods and 30 Playwright scenarios.

# v27.21.1 — Schedule Templates Frontend Contract Alignment Hotfix

- Aligned the fresh-calendar regression guard with the actual `loadMonth → dataLayer.loadCalendar → api.month` architecture instead of requiring a direct API call inside `30-calendar.js`.
- Replaced the obsolete browser-side weekday rotation and `/api/days/fill` expectation with authoritative server preview/apply contracts.
- Corrected static API adapter assertions to match `async scheduleTemplates()` and `async calendarLayers()`.
- Corrected the preview surface contract from the invented `schedulePreview` id to the runtime/Playwright id `tplPreview`.
- No runtime, API, database or Flyway changes; the baseline remains 100 Java test classes, 525 `@Test` methods and 30 Playwright scenarios.

# v27.21.0 — Schedule Templates & Calendar Layers

- Added reusable owner-scoped schedule templates with up to 64 ordered shift steps, cycle-start or weekday alignment and five immutable built-in presets.
- Added conflict-aware preview and safe application: occupied days are skipped by default, overwrite is explicit, and manual day edits remain ordinary dated records after application.
- Added read-only companion calendar layers with name, color, IANA timezone, template, anchor, date bounds and server-owned visibility.
- Projected timed layer shifts from their source timezone into the user's display timezone while keeping all-day/off occurrences floating.
- Composed visible layers into month cells, week agenda and hourly day timeline without duplicating or mutating the owner's calendar rows.
- Added full settings management, API v1 contracts, Flyway V39 and end-to-end coverage for preview, apply, projection and visibility.
- Protected template integrity: a custom shift type cannot be deleted while any schedule template still references it.
- Regression baseline is now 100 Java test classes, 525 `@Test` methods and 30 Playwright scenarios.

# v27.20.2 — Calendar Day Details E2E Flow Hotfix

- Replaced the final hidden Month-grid assumption in `notes-important-events-next.spec.js` with the real «Все детали дня» product route from the focused Day view.
- Added shared `openSelectedDayDetails()` Playwright infrastructure that preserves the focused date, enters Day mode when needed and waits for the full selected-day panel before opening modules.
- Kept `selectDate()` mode-preserving and idempotent; the hotfix does not weaken the v27.20.1 calendar contract or change production calendar behavior.
- Added static and release-gate assertions so Notes & Important Events browser coverage cannot reopen hidden Month-only modules directly from Day mode.
- No API, schema or Flyway changes; the baseline remains 97 Java test classes, 513 `@Test` methods and 29 Playwright scenarios.

# v27.20.1 — Important Event Modal & Offline Notes E2E Hotfix

- Enforced a single-open-modal lifecycle for important-event details and editor dialogs, including propagation guards for board actions and a final close after save/refresh.
- Made the shared Playwright `selectDate()` helper idempotent across Month, Week and Day modes instead of requiring a hidden month-grid cell to be visible.
- Replaced the obsolete read-only offline-note assertion with an end-to-end queue contract: edit existing note offline, persist IndexedDB snapshot and `updateNote`, reconnect, synchronize and verify the server-authoritative value.
- Added explicit browser/static regression guards for modal exclusivity, interactive-row propagation, calendar-mode restoration and offline note queue behavior.
- No API or database changes; Flyway remains V38 and the baseline remains 97 Java test classes, 513 `@Test` methods and 29 Playwright scenarios.

# v27.20.0 — Notes & Important Events Next

- Added owner-scoped note search across titles and Markdown content with date-range and result-limit controls.
- Added offline editing for existing notes through the coalesced IndexedDB `updateNote` queue while preserving independent notes, pin/order, fullscreen, export and long-text behavior.
- Expanded important dates into three compatible modes: floating important dates, events and multi-day periods.
- Added all-day/timed semantics, source IANA timezone, canonical instants, place, description, icon, category and per-event reminder offsets.
- Added read-first event details, a full editor, contextual calendar opening, hourly timed-event cards and all-day period rails.
- Routed quick capture and the historical timezone E2E through the new modal editor, removing the hidden inline-editor compatibility shell.
- Added Flyway V38; historical rows remain floating all-day `IMPORTANT_DATE` records.
- Regression baseline is now 97 Java test classes, 513 `@Test` methods and 29 Playwright scenarios.

# v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix

- Stabilized the Ghost-versus-Outline browser contract by polling the end of the CSS border transition instead of sampling an intermediate animation frame.
- Replaced exact CSS color-string equality with semantic alpha measurement through Chromium's canvas rasterizer, covering `rgba()`, `oklab()` and other valid serializations.
- Kept the production button tokens and the 150 ms visual transition unchanged; this is an E2E contract fix, not a UI behavior change.
- Preserved visible Outline borders, fully transparent Ghost borders, hover-surface coverage and the seven-variant preview contract.
- No API, database or Flyway changes; the regression baseline remains 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios.

# v27.19.3 — Task Deadline Validation E2E Contract Hotfix

- Updated the single stale Playwright deadline-validation assertion to match the planned-interval contract introduced by Tasks & Inbox Next.
- Kept the more precise runtime validation message for timed tasks: a deadline cannot precede the planned interval end.
- Preserved the legacy all-day/date fallback message and its Java service/controller coverage.
- Added a release gate assertion for the planned-interval E2E message so this contract cannot silently drift again.
- No runtime behavior, API or schema changes; Flyway remains V37 and the regression baseline remains 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios.

# v27.19.2 — Frontend Asset Contract Stability Hotfix

- Replaced release-number-specific frontend asset assertions with version-neutral `?v=` contract checks while retaining exact bundle names and load-order guarantees.
- Fixed Today Dashboard, UI Core, Calendar Mobile Experience and Design System shell contracts after the v27.19.1 cache-busting bump.
- Added a release gate that rejects semantic-version literals in `*FrontendContractTest.java` asset-query assertions so future patch releases cannot repeat the same failure.
- Kept runtime cache-busting strict: `index.html`, login assets, Service Worker cache and release metadata still resolve to the exact release version.
- No runtime behavior, API or schema changes; Flyway remains V37 and the regression baseline remains 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios.

# v27.19.1 — Task Board Date Range Compatibility Hotfix

- Restored the long-standing `from` / `to` task-board contract: filters use `dueDate`, falling back to the task `date` when no deadline exists.
- Added explicit `scheduledFrom` / `scheduledTo` parameters for planned-range intersection without changing existing API clients.
- Switched the Web/PWA board date controls and “this month” preset to the planned-range parameters while keeping their existing UI state and labels.
- Preserved source-compatible `TaskService.listBoard(...)` overloads for internal and downstream callers.
- Added service, controller and frontend contract assertions for both deadline/date compatibility and planned-interval filtering, including overnight overlap.
- No schema change; Flyway remains V37. Regression baseline remains 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios.

# v27.19.0 — Tasks & Inbox Next

- Added first-class task planning independent from deadlines: all-day, point, exact start/end, duration presets and overnight intervals.
- Added Flyway V37 with project, projected planning fields, canonical instants and source-timezone provenance while preserving existing tasks as all-day rows.
- Added project metadata, suggestions, chips, search participation and a board project filter.
- Rebuilt read-first task details around the planned interval and retained deadline/reminder facts as separate concepts.
- Replaced the calendar’s synthetic 45-minute deadline block with exact planned segments, including cross-midnight splitting and point events.
- Added searchable Inbox presentation across local queued and server-backed entries without changing quick-capture/offline semantics.
- Added mobile planning controls, 15/30/45/60/90/120-minute presets, frontend/server validation and timezone reprojection tests.
- Regression baseline is now 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios. Flyway advances to V37.

# v27.18.3 — UI Settings & Button Variants Quality Hotfix

- Turned Theme palette into an executable source mode: selecting it restores both accents from the active theme instead of leaving stale custom overrides in place.
- Added an explicit Restore theme colors action that works even when the palette select already says Theme palette, covering legacy/inconsistent saved snapshots.
- Added theme-owned secondary accents and made built-in theme changes refresh both colors only while Theme palette is active; preset/custom palettes remain independent across workspace and layout changes.
- Added visible palette-source states for Theme colors, Preset palette and Customized, reusing the revisioned serialized appearance autosave queue.
- Split Outline and Ghost button contracts: Outline keeps a persistent accent border, while Ghost has no visible idle border or shadow and reveals its surface only on hover/active.
- Added semantic Solid, Soft, Outline, Ghost, Secondary, Danger, Link and Icon tokens/states, including disabled, busy, focus and mobile touch-target behavior.
- Added a live seven-variant button preview, static frontend contracts and a browser regression covering palette reset, reload, theme switching, workspace/layout independence and computed Ghost/Outline styles.
- No schema or domain API change; Flyway remains V36. Regression baseline remains 96 Java test classes and 500 `@Test` methods, and grows to 27 Playwright scenarios.

# v27.18.2 — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix

- Extended `GET /api/overtime/account-page` with the canonical full `usages` snapshot so the summary, paged ledger, FIFO details and chart are rendered from one coherent server projection.
- Stopped reconstructing usage totals from paged credit rows; one usage can span several credits and pages, so the authoritative usage list now travels explicitly with the page response.
- Updated `loadLedgerPage()` to replace summary totals and usages together before rendering, fixing the real `+5 / −4 / +1` state split where the chart showed `−0` while the overview was correct.
- Added service and controller assertions that filtered/paged account responses still include complete usage allocations.
- Made the shared Playwright `selectDate()` helper idempotent so selecting an already active day does not toggle it off.
- Stabilized the timezone projection scenario by capturing the exact assigned calendar date and reopening that date after the canonical timezone refresh.
- No schema change; Flyway remains V36. Regression baseline remains 96 Java test classes, 500 `@Test` methods and 26 Playwright scenarios.

# v27.18.1 — Overtime Next E2E Contract Hotfix

- Fixed the overtime integrity browser scenario so desktop runs target the visible delete action inside `#ledgerRows` instead of the earlier hidden mobile-card duplicate.
- Scoped the post-delete assertions to the desktop ledger, preserving the intentional shared action attributes across responsive presentations.
- Fixed the Overtime Next trend scenario to respect the real grouping contract: All-time/Year use monthly keys while Month uses daily keys.
- The trend scenario now proves both `YYYY-MM` and `YYYY-MM-DD` projections before continuing to desktop/mobile parity checks.
- No production accounting logic, domain API or schema changed; Flyway remains V36.
- Regression baseline remains 96 Java test classes, 500 `@Test` methods and 26 Playwright scenarios.

# v27.18.0 — Overtime Next

- Rebuilt the overtime workspace around a calm balance-first summary while preserving the existing minute-accurate FIFO accounting model.
- Added global available balance, total earned/used metrics, usage ratio and a visible next-in-FIFO credit.
- Added fast Month / Year / All-time period controls plus custom date, status and text filters.
- Added responsive earned-versus-used charts that switch between daily and monthly grouping without introducing a chart dependency; usage is plotted on its actual time-off date rather than the source-credit date.
- Added an explicit FIFO queue that explains which open credit will be consumed next.
- Replaced the wide ledger table on phones with detailed credit cards, allocation details and edit/delete actions; the professional table remains on desktop.
- Preserved quick credit/usage editors, legacy timezone migration and CSV/Excel exports.
- Added a dedicated frontend contract and Playwright scenario for desktop/mobile parity and actual usage-date chart projection.
- Updated the local release gate for the 26-scenario baseline and reused one cached matcher process instead of spawning a new fixed-string search for every contract.
- No domain API or schema change; Flyway remains V36. Regression baseline is now 96 Java test classes, 500 `@Test` methods and 26 Playwright scenarios.

# v27.17.6 — Classic Sunset

- Removed the user-facing Classic shell selector and all runtime switching branches after UI Core v1 staging acceptance.
- Made Today the single default route and workspace navigation the only primary-navigation authority.
- Removed Classic-only CSS/navigation rules while keeping `data-shell="next"` as an inert internal stylesheet scope for a safe incremental cleanup.
- Removed `shellMode` from the profile whitelist/API response; legacy stored `shellMode=classic` values are silently discarded and cannot reactivate the retired interface.
- Replaced the browser fallback scenario with a migration regression that injects the legacy local value and proves the single DutyLog shell still boots.
- Classic rollback is now handled by immutable Git/Docker releases instead of a second interface inside the application.
- No schema or domain API change; Flyway remains V36. Regression baseline remains 95 Java test classes, 496 `@Test` methods and 25 Playwright scenarios.

# v27.17.5 — UI Core E2E Accordion Hotfix

- Fixed the UI Core Playwright scenario so it respects the persisted Appearance accordion state after reload instead of blindly clicking the already-open header and collapsing the Classic selector.
- The browser contract now explicitly verifies that the Appearance card remains open, the Classic choice is visible and `dutylog.settings.openSection=appearance` survives reload before testing the fallback switch.
- No production UI, profile, domain API or schema behavior changed; Flyway remains V36.
- Regression baseline remains 95 Java test classes, 496 `@Test` methods and 25 Playwright scenarios.

# v27.17.4 — UI Core & Workspace Foundation

- Introduced UI Core contract v1: one shared DOM/business layer with declarative registries for workspaces, layouts, themes, palettes, decorations, screens and Today widgets.
- Added three workspaces (`Shift Worker`, `Planner`, `Minimal`) that reorder and selectively expose the same existing routes without duplicating screens or APIs.
- Added three layout presets (`Dashboard`, `Compact`, `Focus`) and made primary navigation adapt to the active workspace.
- Split built-in visual themes into isolated CSS packages scoped by `data-ui-theme`; user palettes remain independent from layout and workspace selection.
- Added synchronous pre-paint bootstrap for shell, theme, palette, layout and custom colors to avoid interface flashes.
- Added automatic appearance persistence with debounce, revision guards and a serialized save queue; the explicit save button remains as a manual retry/flush.
- Extended the existing profile whitelist with UI Core v1 fields and safe widget ordering; arbitrary CSS/JavaScript remains rejected.
- Preserved Classic as a real emergency fallback: workspace hiding never overrides Classic navigation.
- Added static Java contracts, an expanded Playwright shell scenario, theme-package contract checks and deployment smoke coverage for the new assets.
- No schema or domain API change; Flyway remains V36. Regression baseline: 95 Java test classes, 496 `@Test` methods and 25 Playwright scenarios.

# v27.17.3 — Java Contract Build Gate Hotfix

- Fixed the malformed escaped Java string literal in `CalendarMobileExperienceFrontendContractTest` that stopped Maven during `testCompile` before Playwright and deployment.
- Added a fast `javac` syntax gate for all static `*FrontendContractTest.java` files using minimal local JUnit stubs, so this class of source-level regression is caught by `release-check.sh` before packaging.
- Kept the v27.17.2 timeline readability behavior unchanged.
- No schema, backend API or frontend behavior change; Flyway remains V36. Regression baseline remains 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.17.2 — Calendar Timeline Readability Hotfix

- Increased the desktop visual floor for short timed tasks, reminders and overtime events so title and time remain readable.
- Preserved the compact mobile timeline layout unchanged.
- Always renders a task time range before optional category / priority metadata.
- Added visual lane reservation for short events so the larger readable card cannot overlap the next timed item.
- Extended the existing desktop editor E2E scenario to verify a real `17:41` task, event height and both text rows staying inside the card.
- No schema or backend API change; Flyway remains V36. Regression baseline remains 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.17.1 — Calendar & Notes Quality Hotfix

- Made the multiple-notes editor responsive to its own selected-day container with container queries instead of global viewport breakpoints.
- Prevented list, action toolbar, tabs, preview and editor controls from escaping narrow desktop side rails.
- Added an explicit all-day rail with a label, count and compact chips for important dates, untimed tasks, notes and untimed shifts.
- Prefilled important-date creation with the currently selected calendar day instead of a stale previous value.
- Projected reminders by their actual display/reminder date and removed duplicate `IMPORTANT_DAY` notification blocks from the hourly shift grid.
- Changed task deadline time precision from five minutes to one minute, including the `17:41` E2E contract.
- Extended existing Playwright and static frontend contracts without increasing the test-count baseline.
- No schema or backend API change; Flyway remains V36. Regression baseline: 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.17.0 — Calendar Mobile Experience

- Added a persistent Month / Week / Day calendar scale for DutyLog Next.
- Added a mobile week strip, selected-day agenda and swipe navigation.
- Added an hourly day timeline for shifts, timed tasks, reminders and overtime, plus all-day items and a live current-time marker.
- Connected Today Dashboard cards and date strip to the hourly day view while preserving the full legacy day editor.
- Reused the existing authoritative calendar/task/overtime stores; no parallel API or persistence model was introduced.
- Kept Classic as an immediate fallback and preserved all business logic.
- No schema or backend API change; Flyway remains V36. Regression baseline: 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.16.3 — Time Settings Transaction Hotfix

- Preserved unsaved shift-template form values while a timezone save continues background calendar/task/ledger refreshes.
- Serialized debounced and manual built-in shift-template updates so an older in-flight autosave cannot repaint or win over a newer manual apply.
- Added revision guards that only mark the exact captured draft as committed and ignore stale UI completion work.
- Extended frontend contracts and release checks for the queue and draft-preservation invariants.
- No schema or backend API change; Flyway remains V36. Regression baseline remains 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.16.2 — Next Route & Time Settings E2E Hotfix

- Aligned browser scenarios with the intentional DutyLog Next startup route: `#today` remains home, while shared helpers explicitly open Calendar before selecting a date.
- Added robust E2E workspace navigation that uses visible tabs when available and hash routing for hidden legacy workspaces such as Important Dates.
- Updated onboarding regression expectations to protect Today persistence across reload instead of the retired Calendar default.
- Fixed the built-in shift-time apply race by cancelling the pending autosave debounce before an explicit apply.
- Added static regression guards for the route helper and debounce cancellation.
- No schema or backend API change; Flyway remains V36. Regression baseline remains 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.16.1 — Today Runtime & Repository Truth Hotfix

- Fixed a frontend bundle load-order regression where `35-today.js` resolved `openQuickActions` before `50-tasks.js` had declared it.
- Deferred the Today quick-actions callback until click time, removing the shared `ReferenceError` that cascaded into all 24 Playwright failures.
- Added a Java frontend contract and fast release-check guards for the safe callback form.
- Updated current-release documentation to Java 17, Flyway V36 and the v27.17.0 Calendar Mobile Experience roadmap.
- No database migration and no backend/business behavior change; Flyway remains V36.
- Regression baseline remains 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.16.0 — Today Dashboard

- Added `#today` as the default DutyLog Next destination while preserving the full calendar as a separate primary route.
- Added a responsive seven-day strip, active/next shift card, immutable-instant progress and countdown, overtime balance, today tasks, upcoming important dates and quick actions.
- Reused the existing calendar bundle, shift occurrence, overtime account, task and important-date stores; no parallel dashboard API or persistence model was introduced.
- Kept all writes inside existing task, note, overtime, important-date and quick-action flows.
- Reduced mobile primary navigation to five focused destinations: Today, Calendar, Overtime, Tasks and More.
- Added static frontend contracts and a mobile Playwright scenario covering task creation, immediate dashboard composition, calendar opening and brand navigation.
- No schema change; Flyway remains V36. Regression baseline: 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.15.0 — Design System & Mobile Shell Foundation

- Added an additive `design-system.css` layer with spacing, radius, surface, shadow, focus and responsive navigation tokens.
- Introduced the DutyLog Next shell: branded top bar, adaptive month header, icon-based primary navigation and safe-area-aware mobile bottom bar.
- Preserved the previous layout as Classic; the shell can be switched instantly from Appearance settings without touching domain data or APIs.
- Persisted the allowlisted `themeConfig.shellMode` enum (`next` / `classic`) through the existing profile theme contract.
- Refreshed cards, forms, buttons, calendar cells, settings, modals, loading state and login presentation while keeping the existing DOM and business handlers.
- Added light-theme and reduced-motion boundaries for the new shell.
- Added static frontend contracts, profile validation coverage and a mobile Playwright scenario that switches Next → Classic → Next and protects horizontal overflow/ARIA navigation state.
- No schema or business-logic change; Flyway remains V36. Regression baseline: 92 Java test classes, 485 `@Test` methods and 23 Playwright scenarios.

# v27.14.2 — Calendar Notes Persistence E2E Hotfix

- Updated the calendar persistence browser scenario to the Multiple Daily Notes contract.
- The test now creates a concrete note through `POST /api/notes` before editing, because the empty-state editor is intentionally hidden.
- Debounced content persistence is awaited through `PATCH /api/notes/{id}` instead of the removed legacy day-level note `PUT`.
- Month navigation, full reload, shift persistence and emoji persistence are still verified in the same end-to-end flow.
- No production code or schema change; Flyway remains V36. Regression baseline remains 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.

# v27.14.1 — Mobile Notes Tombstone Hotfix

- Fixed Android API v1 note clears deleting the versioned `day_entries` tombstone through the new multiple-note legacy-shadow bridge.
- `DayNoteService` now accepts an explicit empty-row retention policy from `DayEntryService`; only versioned v1 sync preserves the tombstone, while legacy mobile clear keeps its historical row-deletion behaviour.
- Optimistic versions remain monotonic after `clearNote`, so stale offline creates still receive a conflict instead of resurrecting deleted content.
- Explicit clear flags continue to win over contradictory note/emoji values in the same patch.
- No schema change; Flyway remains V36. Regression baseline remains 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.

# v27.14.0 — Multiple Daily Notes

- Replaced the single mutable day-note field with independent owner-scoped notes per calendar date.
- Added titles, pinning, stable ordering, individual edit/delete operations and a dedicated `/api/notes` + `/api/v1/notes` contract.
- Added Flyway V36 and one-time migration of every non-empty legacy `day_entries.note`; the old field remains a primary-note compatibility shadow.
- Day/calendar/mobile payloads now expose the full `notes` collection while preserving the legacy `note` field.
- The day panel now provides a note list, active editor, pin/reorder/delete controls and a calendar count badge.
- Debounced title/content edits merge into one PATCH so rapid input cannot lose either field.
- Offline snapshots remain readable; unsupported note mutations are disabled until the server is reachable.
- ZIP export writes one Markdown file per independent note.
- Regression baseline: 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.

# v27.13.0 — Temporal Consistency & Legacy Cleanup

- Calendar month totals now use the current-timezone overtime projection and never resurrect stale `day_entries` hours when the projected balance is exactly zero.
- Compatibility `/api/overtime/summary` and `/api/overtime/ledger` now share the authoritative account projection.
- Added canonical server-side overtime preview so DST gaps/overlaps and an independently configured browser timezone cannot change the editor result.
- `FIXED_TIME` quick scenarios now carry signed day offsets and reproject across canonical timezone changes, including UTC+14 ↔ UTC−11 round trips.
- Added Flyway V35 for `quick_scenarios.end_day_offset`; legacy `end_next_day` remains a compatibility alias.
- Explicitly preserved floating civil-date semantics for birthdays, important dates, notes, markers, date-only task/subtask deadlines, time-off dates and daily digest time.
- Regression baseline: 88 Java test classes, 467 `@Test` methods and 21 Playwright scenarios.

# v27.12.1 — Midnight Projection Contract Hotfix

- Reconciled the legacy exact-24-hour `12/12` source-credit rule with the v27.12 current-timezone civil-day projection.
- An `08:00 → 08:00` interval still persists as two immutable 12-hour source credits, but user-facing projected totals are asserted as `16 h` before local midnight and `8 h` after it.
- Removed the stale assertion that treated projected account rows as persisted source rows.
- Preserved all 1440 earned minutes, FIFO provenance and account balance.
- Clarified the overtime form hint and API documentation. No database migration; Flyway remains at V34.
- Regression baseline remains 87 Java test classes, 460 `@Test` methods and 21 Playwright scenarios.

# v27.12.0 — Zoned Daily Projection Engine

- Exact overtime credits are projected into current-timezone calendar-day slices without rewriting persisted source rows.
- A `22:00–02:00` absolute interval now redistributes as `2/2`, `1/3` or `0/4` when the canonical timezone moves.
- FIFO allocation intervals are split by the same local-midnight boundaries while credit IDs, allocation IDs and total minutes stay unchanged.
- Ledger rows expose daily earned/used/remaining totals and full source-credit totals through an additive projection DTO.
- Calendar selected-day totals, server-side date filters, CSV and Excel exports use the projected local date.
- Edit/delete actions use full source-credit usage, preventing deletion through an unused projected fragment.
- Legacy quantity-only credits remain one floating row because their missing source instant cannot be inferred safely.
- Added service, frontend-contract and Playwright coverage. Flyway remains at V34.
- Regression baseline: 87 Java test classes, 460 `@Test` methods and 21 Playwright scenarios.

# v27.11.4 — Task Deadline & Reminder Timezone Hotfix

- Timed task deadlines now persist one absolute `dueInstant` plus their original IANA timezone and source local date/time.
- Changing the canonical timezone reprojects the displayed deadline without changing overdue state or the underlying moment.
- Deadline projection can cross midnight; date-only deadlines remain floating civil dates.
- Added an explicit legacy task-deadline preview/migration wizard because historical local-only rows have no trustworthy source timezone.
- Task-specific browser, mobile and Telegram reminders now share the same authoritative `remindAtInstant`.
- Task details expose the original deadline whenever it differs from the current projection.
- Added Flyway V34, API/OpenAPI fields, service/controller/frontend/Telegram tests and a Playwright scenario for `14:10 UTC+5 → 12:10 UTC+3`.
- Regression baseline: 86 Java test classes, 456 `@Test` methods and 20 Playwright scenarios.

# v27.11.3 — Shift Template & Reminder Timezone Hotfix

- Timed shift templates are rebased when the canonical IANA timezone changes, preserving the same real start/end instants for future assignments.
- Built-in and custom timed templates refresh back into the settings form after the authoritative calendar reload.
- Existing dated shifts remain immutable because legacy rows are frozen before template rebasing.
- Shift reminders now use the occurrence `shiftStartInstant`, including projected local date changes across midnight and month boundaries.
- Browser and Telegram delivery consume the same `remindAtInstant`; the legacy wall-clock fallback remains only for unmigrated rows.
- Added service, controller, frontend-contract and Playwright coverage. Flyway remains at V33.
- Regression baseline: 85 Java test classes, 446 `@Test` methods and 19 Playwright scenarios.

# v27.11.2 — E2E Stability Hotfix

- The shift editor Playwright flow now waits for the authoritative `/api/calendar` refresh triggered by assignment before reloading the page, preventing an intentional navigation abort from being reported as `console.error: Failed to fetch`.
- The next-day timezone projection scenario now validates the compact source interval shown by the UI (`03.07 23:00–04.07 07:00`) and the canonical source date (`2026-07-03`) separately.
- No production calculation or database migration changed; Flyway remains at V33.
- Regression baseline remains 85 Java test classes, 442 `@Test` methods and 19 Playwright scenarios.

# v27.11.1 — CI & Contract Hotfix

- обновлены два устаревших frontend-контракта под occurrence-based проекцию смен;
- тест Task Details теперь формирует валидный JSON через ObjectMapper;
- тест legacy-миграции получает гарантированно сохранённые идентификаторы строк;
- production-логика и Flyway V33 не изменялись.

## v27.11.0 — Shift Occurrences & Calendar Projection

- Concrete dated shifts now persist immutable UTC start/end identity and their original IANA timezone.
- Changing the canonical user timezone reprojects existing shifts instead of reinterpreting `08:30` as `08:30` in the new zone.
- Calendar projection can move an occurrence to another local date and split it visually at midnight without duplicating the database row.
- Added safe legacy-shift preview/migration and automatic freezing in the old zone before a timezone change.
- Unrelated day saves no longer silently guess a legacy shift timezone.
- Hardened Service Worker activation so v27.10 Task Details cannot remain hidden behind stale frontend assets.
- Flyway continues with V33.

## v27.10.0 — Task Details

- Added a dedicated read-first task details modal; card clicks no longer throw users directly into editing.
- Added optional multi-line task descriptions with a 4000-character limit and Flyway V32.
- Added owner-scoped `GET /api/tasks/{id}` and `/api/v1/tasks/{id}` endpoints.
- Details expose metadata, description, checklist, dates, reminder and explicit edit/complete/delete actions.
- Checklist items remain interactive from the details view.
- Task board search now includes description text.
- Online details refresh authoritatively while offline details fall back to the loaded calendar/board snapshot.
- Added backend, HTTP, frontend-contract and Playwright coverage.

## v27.9.4 — Overtime Split Projection Contract Hotfix

- Corrected the Playwright expectation for a cross-midnight eight-hour credit: the selected calendar day owns the seven-hour pre-midnight segment while the account balance owns all eight hours.
- Ledger usage references now expose stable `allocationPartIndex` and `allocationPartCount` metadata from the backend.
- Split-part badges render from the paged ledger response even when the full overtime account was not previously loaded into frontend state.
- Added backend and frontend regression assertions for stable `part 1/2` / `part 2/2` rendering.
- No database migration; Flyway remains at V31.

## v27.9.3 — Overtime Preflight Integrity Hotfix

- Usage create/update now validates total requested minutes before mutating a managed entity or inserting a new time-off row.
- Failed over-capacity commands remain side-effect free even inside a wider transaction that catches the domain exception.
- Added regression coverage proving a rejected usage edit keeps its original date, hours, reason and FIFO provenance.
- Updated the task/ledger frontend contract to the intentional `delete entire time-off` wording introduced in v27.9.2.
- Made the overtime modal Playwright scenario deterministic by explicitly setting break and planned deductions to zero.
- No database migration; Flyway remains at V31.

## v27.9.2 — Overtime Ledger Integrity Hotfix

- FIFO replacement is now planned and validated fully in memory before stored allocations are removed.
- Deleting one time-off rebuilds only surviving usages while preserving every overtime credit.
- Post-rebuild invariants verify credit IDs, usage IDs, exact requested minutes and per-credit capacity.
- Ledger rows are rendered atomically through a detached document fragment; a broken allocation cannot leave a partial table.
- Split usages show `part 1/2`, and destructive actions are labelled `delete entire time-off`.
- Added backend, frontend-contract and Playwright regression coverage for two credits, two usages and deleting only one split usage.
- No database migration; Flyway remains at V31.

## v27.9.1 — Overtime Allocation Rendering Hotfix

- Fixed `ReferenceError: formatDate is not defined` while rendering exact cross-midnight FIFO allocations.
- Selected-day rendering no longer aborts after an overtime usage is created, so the calendar highlight and shift details follow the clicked date.
- Exact allocations continue to split cleanly at midnight using the existing `formatDateHuman` helper.
- Expanded the browser regression to create an eight-hour overnight credit, consume it fully and verify both `17:00–24:00` and `00:00–01:00` segments without console errors.
- Added a frontend contract and release-gate runtime smoke for the allocation formatter.
- No database migration or FIFO model changes; Flyway remains at V31.

## v27.9.0 — Overtime Interval Engine

- Replaced floating-point FIFO authority with deterministic integer minutes.
- Added exact source intervals and provenance to overtime allocations.
- Rebuilds the complete FIFO ledger after usage create/update/delete so cancellation restores the same source minutes.
- Added legacy overtime timezone preview/migration wizard without automatic guessing.
- Simplified the user-facing model to one canonical IANA timezone while preserving legacy API fields.
- Existing absolute overtime reprojects when the canonical timezone changes; original source timezone remains recorded.
- Cross-midnight used intervals render as separate calendar-day segments.
- Shift cards now explain net work and break separately.
- Added Flyway V31, API/OpenAPI contracts, backend/frontend regression coverage and release documentation.

## v27.8.1 — Timezone Projection Refresh Hotfix

### Authoritative timezone refresh
- The authenticated profile now loads before the first calendar request, so dated shifts are projected with the persisted work/display zones from the beginning of the session.
- Saving timezone settings forces a cache-bypassing calendar read and replaces the IndexedDB month snapshot instead of repainting a stale source zone first.
- The overtime ledger refreshes together with the calendar because absolute overtime rows use the same display timezone.

### Regression coverage
- Replaced the browser scenario with the real regression path: create a dated shift, change work/display zones, then verify the existing card changes from `08:30 Asia/Yekaterinburg` to `06:30 Europe/Moscow` without retaining `Europe/Kyiv`.
- Added frontend contracts for boot ordering, fresh calendar propagation, snapshot bypass and ledger refresh.
- Baseline: 80 Java test classes / 413 `@Test` methods and 16 Chromium Playwright scenarios. Flyway remains at V30.

## v27.8.0 — Zoned Work Intervals

### Dated shift projections
- Every dated shift with start/end times is resolved through its work IANA timezone into one immutable `startInstant` / `endInstant` pair.
- Day API responses expose work-local and display-local projections, source/display zone identifiers, elapsed/net minutes and midnight-crossing flags.
- Calendar cells and the selected-day panel show the configured display-zone time while preserving the original work-zone range. A display-zone save reloads the active month without rewriting schedule data.

### Absolute overtime identity
- New calculated overtime credits persist `start_at_instant`, `end_at_instant` and `source_timezone` through Flyway V30.
- Duration and overlap protection use real elapsed instants, so DST gaps/overlaps no longer create silent one-hour errors.
- Existing calculated credits retain their source timezone when edited. Saving unchanged fields after changing the account work timezone cannot move the stored interval.
- Historical credits remain legacy-local because their original timezone was never stored; V30 deliberately performs no guessed backfill.

### Interface and compatibility
- Overtime rows prefer display-zone projections and keep the original work range/source zone as secondary context.
- `DayDto` keeps a source-compatible constructor and appends nullable `shiftInterval`; existing floating dates, tasks, notes and important dates remain unchanged.
- Exact interval-slice FIFO provenance is intentionally deferred to Overtime 2.0; current allocations continue consuming hour quantities.

### Regression coverage
- Added work/display shift projection, DST elapsed-duration, absolute overtime persistence, source-zone edit stability, migration and frontend contract tests.
- Added a browser scenario proving `08:30 Asia/Yekaterinburg` renders as `06:30 Europe/Moscow` without changing the work interval.
- Baseline: 79 Java test classes / 409 `@Test` methods and 16 Chromium Playwright scenarios. Flyway ends at V30.

## v27.7.1 — Task & Ledger Layout Hotfix

### Task cards
- Replaced the wrapping day-task flex row with a stable three-column grid: checkbox, flexible body and pinned delete action.
- Kept inline subtasks aligned under the task body and removed the mobile-only offset that could push the checklist outside the card.

### Overtime ledger
- Added an explicit `Действия / Actions` column for credit-level edit and delete controls.
- Rendered each FIFO usage as a structured block and renamed its controls to `ред. списание` / `удалить списание`, making credit actions and usage actions unambiguous.

### Regression coverage
- Added a frontend contract test for the task-card grid, dedicated ledger action column and explicit usage-action labels.
- Baseline: 78 Java test classes / 399 `@Test` methods and 15 Chromium Playwright scenarios.

## v27.7.0 — Time Foundation

### Explicit time semantics
- Split the user time context into persisted IANA `workTimezone` and `displayTimezone` values. Existing accounts inherit their current work timezone as the display timezone, preserving previous behaviour.
- Centralised current time, timezone projection and work-local conversion in `UserTimeService`; legacy helpers remain compatible and explicitly mean work time.
- Documented and tested deterministic DST handling: nonexistent wall-clock times move forward through the gap and ambiguous times use the earlier offset.

### Absolute reminders and delivery identity
- Added one-instant/two-projection reminder fields: `remindAtInstant`, work timezone, display-local value and display timezone.
- Reminder sorting and past filtering now compare `Instant` values instead of server-local date-times.
- Telegram scan windows and new delivery identities now use absolute instants. Flyway V29 adds nullable `remind_at_instant TIMESTAMPTZ`; legacy rows remain local because their original timezone was never stored, and runtime deduplication safely supports both generations.

### Work interval foundation
- Added `WorkIntervalService`, which resolves work-local start/end values into absolute intervals and calculates elapsed/net minutes across midnight and daylight-saving transitions.
- Task overdue rules now use the account's work timezone rather than the operating-system timezone.
- Added authenticated `/api/time/context` and `/api/v1/time/context` endpoints plus work/display timezone fields in mobile user responses.

### Interface and compatibility
- Added separate work/display timezone selectors, a same-as-work shortcut and a two-clock preview. Legacy work-only profile updates keep display coupled until the user explicitly separates the zones.
- Calendar dates continue to use work timezone; absolute synchronization and mobile-session timestamps use the display timezone; legacy Inbox audit timestamps remain local until they gain an explicit instant.
- Floating dates and existing task, shift and overtime records are intentionally not mass-converted in this release.

### Regression coverage
- Added DST gap/overlap, absolute projection, work-interval, profile, notification, Telegram deduplication, API and frontend contract coverage.
- Flyway now ends at V29.
- Baseline: 78 Java test classes / 398 `@Test` methods and 15 Chromium Playwright scenarios.

## v27.6.3 — Polish & Consistency

### Business rules and ordering
- Added central `validateBusinessRules()` validation after all create/update fields are applied.
- A task due date can no longer precede the task date; the same-day deadline remains valid.
- A subtask due date can no longer precede its parent task date.
- Day, range and board responses share one stable open-first comparator; optimistic browser updates use the same rule.

### Lightweight subtask deadlines
- Added nullable date-only `dueDate` to one-level checklist items without turning them into full tasks.
- Flyway migration **V28** adds `task_subtasks.due_date` and an owner-friendly lookup index.
- Updated entity, DTO, create/update reconciliation, OpenAPI and Inbox-to-task compatibility.
- Existing clients remain compatible through legacy DTO constructors; omitted dates are preserved while explicit blank values clear them.

### Task UX polish
- Replaced the text-only checklist badge with an accessible graphical progress bar and numeric value.
- Standardised inline disclosure as `Подзадачи (2/3)` and preserved expansion state during local re-renders.
- Added compact inline subtask dates, denser metadata chips and a consistent icon vocabulary.
- Added a completed-task divider only when open and completed tasks are visible together.
- Improved 320–430 px layouts for long text, date inputs and checklist controls.

### Regression coverage
- Added service and HTTP tests for create/update deadline rules, final-entity validation, open-first sorting, subtask deadline persistence and clearing.
- Extended frontend contracts for V28, client validation, progress semantics and completed grouping.
- Added a Chromium scenario covering invalid deadlines, persisted subtask dates, progress accessibility and immediate completed-task reordering.
- Baseline: 76 Java test classes / 389 `@Test` methods and 15 Chromium Playwright scenarios.

## v27.6.2 — Tasks & Subtasks

### One-level subtasks
- Added ordered one-level subtasks to normal tasks. Recursive nesting is intentionally prohibited so the task editor remains compact and the data model stays predictable.
- Each task can contain up to 50 checklist items with independent text, completion state and explicit order.
- The task editor supports adding, removing and reordering checklist items before save.

### Compact task UX
- Calendar tasks and the task board show only a compact progress badge such as `2/4` by default.
- The checklist expands inline on demand, keeping dense task lists readable on mobile and desktop.
- Individual checklist items can be toggled without opening the full task editor.
- Completing a parent with unfinished checklist items requires explicit confirmation and can atomically complete the remaining items.

### Backend and persistence
- Added the owner-scoped subtask endpoint `PATCH /api/tasks/{taskId}/subtasks/{subtaskId}` and its versioned `/api/v1` alias.
- Task create/update and Inbox-to-task conversion now accept ordered subtasks.
- Flyway migration **V27** creates `task_subtasks` with cascade deletion, stable ordering and a non-negative order constraint.
- Inline child toggles are online-only in this release; full subtask offline synchronization remains part of the planned offline-first architecture.

### Regression coverage
- Added service and controller coverage for creation, ordering, reconciliation, search, ownership isolation and explicit parent completion.
- Added frontend contract checks and a Chromium scenario covering `0/2 → 1/2 → 2/2` completion.
- Baseline: 76 Java test classes / 385 `@Test` methods and 14 Chromium Playwright scenarios.

## v27.6.1 — Quick Capture Polish

### Product UX
- Reframed Inbox as a temporary capture layer instead of a second task list or a separate navigation destination.
- Replaced the large Inbox card above tasks with a compact collapsed tray inside the task board. The tray keeps the open-item counter visible without pushing tasks down the page.
- Removed thought-specific wording from the primary flow; the interface now speaks about entries and things to remember.

### Universal quick add
- The global `+` now opens one draft field directly. Pressing Enter saves the text to Inbox, while Shift+Enter inserts a new line.
- The same draft can prefill a new task, append to today's Markdown note or become the title of a new important date.
- Quick actions are module-aware and appear only for enabled Tasks, Notes, Important dates and Overtime modules.
- Removed the extra quick-capture modal and one unnecessary tap from the mobile path.

### Compatibility and regression coverage
- Existing `/api/inbox`, idempotent offline queue, archive/restore and Inbox-to-task conversion remain unchanged.
- No new database migration is required; Flyway remains V1–V26.
- Updated frontend contract and Playwright coverage for the compact tray and `+ → text → Inbox → task` flow.
- Baseline: 76 Java test classes / 381 `@Test` methods and 13 Chromium Playwright scenarios.

## v27.6.0 — Mobile Tasks & Inbox UX

### Fast capture and Inbox
- Added a user-scoped Inbox for unstructured thoughts with open/archived states, timestamps and one-step conversion into a normal task.
- Inbox creation accepts a client operation id, making offline retries idempotent instead of creating duplicate thoughts after reconnect.
- Added a global mobile `+` action with explicit choices: capture a thought, create a task, add overtime and **Списать переработку**.
- Quick capture requires only text and remains usable offline through the existing IndexedDB operation queue.

### Task UX and metadata
- Removed the large inline task-creation form from the selected-day panel; calendar and task board now open one reusable editor.
- The mobile task editor is full-screen, focuses the task text first and hides optional category, tags, priority, due time and reminders behind a progressive disclosure section.
- Added saved lower-case categories and up to ten normalised tags per task, metadata suggestions and tag-aware board search.
- Task due time remains a native `input type=time`, allowing the operating system picker on mobile devices.

### Backend and persistence
- Added `inbox_items`, `InboxService`, REST endpoints under `/api/inbox` and `/api/v1/inbox`, ownership isolation and task-module guards.
- Added `day_task_tags` as an element collection and a metadata endpoint at `/api/tasks/metadata`.
- Flyway migration **V26** creates Inbox and task-tag storage and normalises existing task categories to lower case.
- Internal task creation now enforces the same 500-character text limit as HTTP validation, including Inbox-to-task conversion.

### Current deployment strategy
- The shared VPS remains a private-beta staging host only. A separate production stack is intentionally deferred until DutyLog has its own stronger server and domain, preserving resource headroom for YARUGA.

### Regression coverage
- Added service, controller and frontend contract coverage for Inbox CRUD, idempotency, ownership, offline capture, conversion, tags and mobile UI.
- Updated Playwright task flows and added an end-to-end quick-capture-to-task scenario.
- Baseline: 76 Java test classes / 381 `@Test` methods and 13 Chromium Playwright scenarios.

## v27.5.2 hotfix — restore drill Flyway boolean check

### Fixed
- Fixed a false-negative restore drill failure after a successful PostgreSQL restore. Concatenating the Flyway `success` boolean into text yields `true`, while the script incorrectly required the standalone psql representation `t`.
- The integrity query now emits the explicit terminal state `success` or `failed`, and the shell check validates that stable value.
- Backup creation, checksum verification, archive restore, temporary-resource cleanup and the live DutyLog database are unchanged.
- When running operational commands interactively, strict shell mode should remain inside the scripts rather than being enabled for the whole SSH login shell; otherwise any expected non-zero script result closes the SSH session.

### Regression coverage
- The release gate now verifies the explicit Flyway state expression and refuses the old fragile `|t` check.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.2 hotfix — Telegram linked-owner fetch

### Fixed
- Confirmed and fixed the production `LazyInitializationException` raised by `/today`, `/tomorrow` and their quick-action aliases after Telegram polling resolved a linked account outside the repository transaction.
- `TelegramLinkRepository.findByTelegramChatId(...)` now fetches the linked `owner` through an entity graph, so the command layer can safely read the persisted IANA timezone after the service transaction closes.
- `/help`, command-menu registration, notifications and write-command validation are unchanged.

### Regression coverage
- Added a real Spring integration test that persists a Telegram link, lets the lookup transaction end, and then reads `AppUser.getWorkTimezone()` from the returned detached account.
- Baseline: 73 Java test classes / 368 `@Test` methods and 12 Chromium Playwright scenarios.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.2 — Telegram command menu and quick actions

### Telegram discoverability
- DutyLog now registers its supported commands through Telegram `setMyCommands`, so `/today`, `/tomorrow`, `/week`, `/tasks`, `/balance`, `/task`, `/done`, `/ppr`, `/timeoff` and `/help` are visible in the chat command menu with short descriptions.
- Command-menu registration starts after application boot and refreshes periodically, so a temporary Telegram outage does not require a redeploy; failures are logged without exposing the bot token.
- Registration can be disabled or rescheduled through environment variables without changing application code.

### Quick actions
- Every bot reply carries a compact persistent Telegram keyboard with `Сегодня`, `Завтра`, `Задачи`, `Баланс`, `Неделя` and `Помощь`.
- Button labels are accepted as first-class command aliases, so the user no longer has to type slash commands manually.
- `/help` explicitly explains that the primary actions are available under the Telegram input field.

### Regression coverage
- Added HTTP-contract tests for `setMyCommands`, command descriptions, retry-safe guards and the persistent reply keyboard payload.
- Added command-service coverage proving every quick-action label dispatches to the same timezone-aware logic as its slash-command equivalent.
- Baseline: 72 Java test classes / 367 `@Test` methods and 12 Chromium Playwright scenarios.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.1 — Telegram commands and mobile sync status bugfix

### Telegram bot
- `/today` and `/tomorrow` now build a best-effort day summary: a failure in shifts, tasks, important dates or overtime no longer makes the whole command disappear.
- A partially available summary explicitly marks the failed section while still returning all data that loaded successfully.
- Unexpected command failures and empty command results now produce a safe Telegram reply instead of silent polling logs; server logs include only the command name, user/chat identifiers and exception type.
- `UserTimeService` is injected into the command service, keeping `/today` and `/tomorrow` aligned with the account's persisted IANA timezone and making the date boundary deterministic in tests.

### Mobile synchronization status
- Fixed the compact header status pill: the later mobile CSS rule no longer forces the long synchronization label onto one unbreakable line.
- Mobile status uses `синхр…` while active, can wrap long cross-tab/pending states, and stays inside the header column.
- Offline synchronization action buttons now allow wrapping inside a `minmax(0, 1fr)` mobile grid.

### Regression coverage
- Added tests for `/tomorrow@BotName`, partial day-summary recovery and safe replies for unexpected linked-command failures.
- Extended the frontend contract for the compact mobile synchronization label and wrapping rules.
- Baseline: 72 Java test classes / 364 `@Test` methods and 12 Chromium Playwright scenarios.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.0 — Backup and recovery hardening

### Deployment bundle hotfix
- Fixed the remote deployment bundle so the host receives `check-backup-freshness.sh`, `restore-drill.sh` and `install-backup-timer.sh` together with the updated backup script.
- Added release checks that fail when any production runtime backup tool is omitted from `remote-deploy.sh`.
- Isolated the intentional missing-configuration CI gate test from the real GitHub job summary, preventing a successful validation job from displaying a false “Deployment configuration is incomplete” warning.
- Application code, database schema and the already-created verified backup are unchanged.

### Backup safety
- `backup-postgres.sh` now defaults to the active `deploy/compose/docker-compose.deploy.yml`, fails closed when `.env` or Compose is missing, validates numeric retention and prevents concurrent writers with `flock`.
- Dumps and SHA-256 files are published atomically with restrictive permissions only after PostgreSQL accepts the custom archive.
- Added `check-backup-freshness.sh` to enforce a maximum backup age, checksum verification and optional live `pg_restore --list` validation.

### Recovery safety
- Real restores require a matching checksum by default and reject unsupported formats before stopping the application.
- Added an EXIT recovery trap: when a restore fails after stopping a running application, DutyLog is started again and the original restore failure remains visible.
- Added `restore-drill.sh` for isolated PostgreSQL 16 recovery exercises with no network, no published ports, table/Flyway verification and exact temporary-resource cleanup.

### Operations
- Added `install-backup-timer.sh` to render and enable environment-specific systemd service/timer units.
- Added `backup-tooling-self-test.sh`; CI verifies backup rotation/checksum, freshness checks, restore failure recovery and systemd unit rendering without touching a real database.
- Documented the successful staging drill, timer installation, retention and the remaining requirement for an off-VPS copy.

### Database
- Flyway remains V1–V25. No schema migration is required.

## v27.4.3 — Reminder timezone and sync UX bugfix

### Fixed
- Task creation and editing now accept any whole-minute reminder offset from 0 to 10080, including 3 minutes; both inputs use `step=1` and the frontend validates an integer value.
- Browser reminders now prefer the backend-provided `remindAtInstant` UTC instant calculated from the user's saved IANA timezone. The existing user-local `remindAt` value remains available for display and Telegram delivery.
- Browser reminder polling now builds its source-date range from DutyLog's selected timezone rather than the operating-system timezone of the current device.
- Changing the saved timezone invalidates the cached browser reminder schedule immediately.
- Removed the duplicate “Короткий интервал” text field from overtime credit creation. New calculations use explicit start/end inputs; historical manual `timeRange` values are preserved while editing old records.
- Manual offline synchronization now shows an accessible live status, disables the button while running and reports completion, no changes, offline state, cross-tab locking or partial failure.

### Regression coverage
- Added backend coverage proving `Asia/Yekaterinburg` local reminder times are serialized to the correct UTC instants.
- Added frontend contracts for arbitrary reminder minutes, absolute browser reminder scheduling, explicit overtime start/end and synchronization feedback.
- Extended task-editor E2E with a 3-minute reminder and updated overtime E2E to use start/end fields.
- Added a browser E2E scenario that observes synchronization progress and its final “No changes” result.

### Baseline
- 72 Java test classes / 362 `@Test` methods.
- 12 Chromium Playwright scenarios.
- PostgreSQL Flyway remains V1–V25; no migration is required.

## v27.4.2 — Timezone simplification and critical regression pack

### Remember-me E2E hotfix
- Fixed the fresh-session bootstrap regression to call `GET /api/calendar` with its required `from` and `to` query parameters.
- The previous test correctly restored authentication but then expected `200` from an intentionally invalid calendar request that returned `400`.
- Production remember-me, calendar API and database behavior are unchanged.

### Changed
- Replaced the manual region name, free-form timezone and Moscow-offset controls with one generated IANA timezone selector and an explicit Save action.
- The selector shows a readable city label and current UTC offset while persisting the canonical IANA identifier such as `Europe/Chisinau`.
- Removed the region/object and manual Moscow-offset fields from the UI; existing stored legacy values are ignored and cleared on the next save.
- Separated timezone controls visually from built-in day/night shift templates without changing shift data or calculation rules.

### Regression coverage
- Added a browser regression that restores authentication in a completely fresh browser context using only the persistent remember-me cookie, verifies parallel PWA bootstrap API calls and proves logout revokes the old cookie.
- Extended task editor coverage to persist category, priority, due date and due time in the single modal.
- Extended shift-type coverage to create, edit, assign and reload a custom shift through the modal manager.
- Updated timezone E2E coverage for the compact selector, explicit save and persistence after reload.
- Extended deployment smoke tests with authenticated read-only profile, module, session and identity API checks.
- Added an HTTPS-only production smoke wrapper; it refuses to run without authenticated smoke credentials.

### Baseline
- 71 Java test classes / 358 `@Test` methods.
- 11 Chromium Playwright scenarios.
- PostgreSQL Flyway remains V1–V25; no migration is required.

## v27.4.1 — Overtime scenario manager

### Changed
- Moved quick-scenario creation, editing and deletion out of Settings and into the shared overtime credit modal.
- Added a scenario dropdown with a dedicated management action and a final “Manage scenarios…” entry.
- Added “Save current values as a scenario”: the editor converts a shift-anchored overtime interval into a reusable scenario draft without discarding the current credit form.
- Scenario management switches views inside the same modal instead of stacking dialogs; returning to the credit editor preserves all entered values.
- Existing scenario CRUD API and stored data remain unchanged; no database migration is required.

### Validation hotfix
- Fixed the new Playwright scenario so it creates a real two-hour post-shift overtime interval instead of trying to save a zero-hour full-shift draft.
- Added an accurate validation message when start/end are present but break and planned norm reduce the overtime total to zero.
- Production API, database schema and scenario persistence are unchanged.

### Tests
- Added frontend contracts proving the Settings card is gone and the single-window manager owns scenario CRUD.
- Added a Playwright flow covering shift assignment, draft creation from a filled overtime form, scenario creation, editing and return to the original credit editor.
- Baseline: 71 Java test classes / 356 `@Test` methods and 10 Playwright scenarios.

## v27.4.0 — Unified overtime editors

### Changed
- Replaced the oversized overtime form inside the selected-day panel with two compact actions: earn overtime and use time off.
- Added the same actions to the overtime ledger so entries can be created from either the calendar context or the account table.
- Added one shared modal editor for overtime credits and one shared modal editor for time-off usages; create and edit now use the same forms.
- Overtime editing from the ledger opens directly in the editor instead of navigating to another month in the calendar.
- Existing quick scenarios are available through a compact dropdown in the overtime editor.
- The selected calendar date is prefilled when an editor is opened from a day; ledger actions use the last selected date or today.
- Added live before/after balance preview for time-off usage and responsive full-height mobile editors.

### Tests
- Added frontend contract coverage for the compact calendar controls, shared modals and direct ledger editing.
- Added a Playwright scenario covering create, use and edit flows from both calendar and ledger entry points.

## v27.3.1 — Stable browser session and editor modals

- Replaced rotating persistent remember-me tokens with a stable, fixed-expiry token service so parallel PWA bootstrap requests can no longer invalidate each other after a browser close or application restart.
- Added a regression that reuses the same remember-me cookie for multiple restored requests, matching the browser's real parallel startup behavior.
- Replaced the task edit prompt chain with one structured modal containing text, date, category, priority, due date/time and reminder controls.
- Moved shift-type creation and editing out of Settings into a dedicated manager opened by the `+` chip in the selected-day calendar panel.
- The shift manager now handles built-in and custom types in one place, including time, break, norm, color and per-shift notification settings.
- Removed the obsolete Shift types card and navigation item from Settings, reducing settings density without deleting any shift data.
- Added Java frontend contracts and a Playwright flow covering both modal editors.

## v27.3.0 — Important dates, user timezone and precise overtime editing

### CI test stabilization hotfix

- Injected `UserTimeService` into Telegram notification delivery instead of constructing a private clock source inside the service.
- Made Telegram scheduler tests deterministic with a fixed user-local timestamp, so UTC GitHub runners do not compare reminders against the default Moscow timezone.
- Updated the frontend timezone contract to assert the actual profile `PUT /api/profile` request rather than a nonexistent `api.updateProfile` helper.
- Production reminder timing, database schema and public API behavior are unchanged.

- moved important dates out of Settings into a dedicated top-level workspace with search, filters, create, edit, delete and jump-to-calendar actions;
- persisted each user's validated IANA work timezone in PostgreSQL through Flyway V25 and the Profile API;
- synchronized timezone selection between devices and applied user-local time to notification filtering and Telegram commands/delivery;
- made Edit in the overtime ledger open the correct calendar month, day and overtime form automatically;
- added a return-to-ledger action and exact row highlighting by credit/usage id instead of highlighting every row on the same date;
- kept the existing selected-day important-date quick form for fast entry.

## v27.2.33 — Persistent login, shift reassign and compact mobile UX

### E2E stability hotfix

- The mobile layout scenario now onboards with the Full preset because it explicitly tests the Tasks navigation; the previous Standard preset intentionally kept Tasks disabled and made the selector correctly hidden.
- Module toggle tests now start waiting for the final “modules saved” state before clicking and match the final message exactly, preventing the 1.8-second transient status from being missed on fast runners.
- No production application, database or Flyway behavior changed.

### CI registry hotfix

- Replaced environment-local package tarball URLs in `package-lock.json` with public `registry.npmjs.org` URLs.
- Switched CI and staging validation from `npm install` to reproducible `npm ci`.
- Added npm registry pinning, dependency cache and bounded retries for transient registry failures.
- Added release-gate checks that reject internal-only package registry URLs.

- Fixed a full-day snapshot race where a debounced note save could restore a shift immediately after the user deleted it; writes are now serialized per date and stale responses cannot overwrite newer local revisions.
- Day upsert handling now accepts an intentionally empty successful response when deleting the final value from a date, so the UI does not report a JSON parse failure after a successful delete.
- Added explicit persistent browser login with a 30-day `DUTYLOG_REMEMBER_ME` HttpOnly cookie, JDBC-backed token storage and logout/password/role-change revocation.
- Added PostgreSQL Flyway migration `V24__persistent_web_login.sql`; local H2 test/dev schema is represented by the matching JPA entity.
- Added compact mobile headers, collapsible task/overtime filters, horizontally scrollable stat chips and hidden one-page pagers.
- The selected-day mobile sheet now sits above the app shell and hides the fixed bottom navigation, preventing controls from being covered.
- Added Java remember-me integration coverage and Playwright regressions for delete/reassign during a pending note save and compact mobile behavior.
- Baseline: 66 Java test classes / 342 `@Test` methods and 6 Playwright scenarios.

## v27.2.32 — Pipefail-safe authenticated smoke-test hotfix

- Fixed staging deployment exit `141` during the authenticated app-shell probe.
- Removed producer-to-`grep -q` pipelines from `smoke-test.sh`; under `set -o pipefail`, an early successful match could terminate the producer with SIGPIPE and falsely fail a healthy deployment.
- Added a large multiline app-shell regression fixture that reproduces the former failure deterministically.
- Kept the CSRF-aware login, secure-cookie loopback handling and protected asset checks from v27.2.31.

## v27.2.31 — Authenticated deployment smoke-test hotfix

- Fixed the first real staging deployment failure: the smoke test no longer tries to download the protected application shell anonymously.
- Browser navigation to `/` is verified with `Accept: text/html` and must redirect to `/login.html`; API-style anonymous requests may continue to receive JSON `401`.
- Deployment smoke tests now authenticate with the bootstrap administrator through the real CSRF-protected `/perform_login` flow, keep cookies in a permission-restricted temporary directory and verify the versioned app shell only after login.
- Deployment and rollback paths fail closed when authenticated smoke credentials are missing or rejected.
- Added a local HTTP regression harness that proves valid credentials pass, invalid credentials fail and passwords are not printed.
- Application features, Flyway V1–V23, 340 Java tests and 5 Playwright scenarios are unchanged from v27.2.30.

## v27.2.30 — Host nginx CI/CD deployment hardening

- Active staging/production delivery now uses the VPS-wide system nginx instead of a shared Caddy container.
- DutyLog publishes only to `127.0.0.1`, with staging on `18082` and production on `18083`; deployment preflight rejects any non-loopback bind address.
- Removed the external `dutylog_edge` network and Caddy dependency from the active Compose/bootstrap path while keeping legacy examples as optional references.
- Added a full loopback smoke test before the public HTTPS smoke test, making container failures distinguishable from DNS/TLS/nginx failures.
- Added configurable Docker memory/PID limits and JSON log rotation for the shared 2 GiB VPS.
- Added concrete nginx/Certbot templates for `stage.yaruga-trophy.ru` and `dutylog.yaruga-trophy.ru`, with forwarding headers overwritten at the trusted edge.
- Updated CI/CD, staging, production and VPS runbooks for the real YARUGA + DutyLog shared-host topology.
- Application behavior, Java test baseline (340) and Playwright baseline (5) are unchanged from v27.2.29.

## v27.2.29 — Final security and product audit hardening

- Browser sessions now carry an `auth_version`; password resets and role changes invalidate cached `JSESSIONID` authorities on the next request.
- Normal password changes enforce the same 8-character minimum as registration; administrators remain at 12 characters and bootstrap credentials at 20.
- Authentication rate limiting and `SECURITY_AUDIT` no longer trust forwarding headers unless the managed proxy mode is enabled; supplied nginx/Caddy configs overwrite client-supplied IP headers.
- PostgreSQL backups and checksums are created under `umask 077` with `0700` directories and `0600` files.
- Expired mobile authentication-token rows are cleaned on a bounded retention schedule.
- Added integration and unit regressions for stale web sessions, proxy-header spoofing, auth-version changes and token cleanup.
- Flyway migration chain is now V1–V23. Baseline: 65 Java test classes / 340 `@Test` methods and 5 Playwright scenarios.

## v27.2.28 — Staging deployment gate and diagnostics hardening

- Split staging delivery into validation, immutable image build/clean-PostgreSQL verification and a separate remote deployment job.
- Staging now runs the same Maven `verify`, JaCoCo floor, release gate and Playwright browser suite before an image can be built for deployment.
- Added the GitHub Environment switch `DUTYLOG_DEPLOY_ENABLED`. When it is absent or false, the workflow stays green after building and verifying the immutable image, clearly records that remote deployment was skipped and never creates a `staging-tested-tree-*` promotion tag.
- Added fail-fast CI deployment configuration validation that reports missing variable/secret names without printing secret values and validates HTTPS, SSH port, path, user and key shape.
- Production remains fail-closed and now uses the same explicit preflight before touching GHCR or the server.
- Improved `remote-deploy.sh` diagnostics so all missing inputs are reported together.
- Backend behavior, database schema, Flyway migrations, 327 Java tests and 5 Playwright scenarios are unchanged.

## v27.2.27 — Playwright marker accordion hotfix

- Fixed the remaining calendar-persistence E2E failure: the custom marker input lives inside the closed Marker `<details>` section, so the scenario now expands `data-day-module="core"` before filling `#dayEmojiCustom`.
- The authoritative reload assertion now explicitly reopens both Notes and Marker sections before checking their persisted controls.
- The PWA/offline scenario is already green; no production, database or Flyway behavior changed.
- Java baseline remains 61 classes / 327 `@Test` methods; browser baseline remains 5 Playwright tests.

## v27.2.26 — Playwright selector, accordion and line-ending hotfix

- Fixed the calendar persistence E2E contract: selected shift chips now expose `aria-pressed="true"` instead of relying only on inline colors, and the test asserts that accessible state.
- Added a reusable `openDayModule` Playwright helper so note scenarios expand the closed `<details>` accordion before filling `#noteEdit`.
- Updated both calendar persistence and PWA offline scenarios to open the Notes module explicitly before waiting for the debounced day save.
- Added `.gitattributes` with repository-wide LF normalization, CRLF only for Windows command files and binary exclusions.
- The Java baseline remains 61 classes / 327 `@Test` methods; the browser baseline remains 5 Playwright tests.
- Backend behavior, database schema and Flyway migrations are unchanged.

## v27.2.25 — Playwright browser E2E regression baseline

- Added a real Chromium E2E layer for registration, login-language persistence, first-run onboarding, calendar data persistence, module disable/enable survival, task completion, mobile viewport usability and PWA offline startup.
- Added automatic detection of browser `console.error`, uncaught page errors, failed same-origin requests and unexpected happy-path HTTP `4xx/5xx` responses.
- Added stable non-visual DOM contracts for calendar dates, shift chips and task rows.
- Added an isolated `application-e2e.properties` profile using in-memory H2 on port 4173; it never touches the local file database and keeps external Telegram traffic disabled.
- GitHub Actions now installs Chromium, runs Playwright before building the deployment image and uploads traces/screenshots/videos on failure.
- Added npm Dependabot coverage and Playwright usage documentation.
- Java/JUnit baseline remains 61 classes and 327 `@Test` methods; browser baseline adds 5 Playwright tests.
- Database schema and Flyway migrations are unchanged.

## v27.2.24 — Coverage floor and startup/module regression suite

- Added direct startup coverage for bootstrap-admin configuration, credential validation, account creation, promotion, optional forced password reset and one-time legacy-admin cleanup.
- Added module registry/service coverage for normalization, immutable contracts, unique keys/orders, acyclic dependencies, locked modules, admin visibility, unknown persisted keys and dependency activation.
- Added current-user resolution and extended note-export coverage for count/select races, blank-note filtering, audit events, ZIP structure and YAML escaping.
- JaCoCo now fails `mvn verify` when bundle instruction coverage drops below 88% or branch coverage drops below 70%.
- The suite now contains 61 test classes and 327 `@Test` methods.
- Production API behavior, database schema and Flyway migrations are unchanged.

## v27.2.23 — Security test contract and secret-safe error logging hotfix

- Исправлены два чрезмерно строгих теста Content-Type: `application/json;charset=UTF-8` теперь корректно принимается как JSON.
- Browser redirect contract теперь отправляет `Accept: text/html`, как настоящий браузер, и не смешивает HTML-навигацию с JSON API channel.
- `ApiExceptionHandler` больше не логирует throwable целиком для неожиданных ошибок: в журнал попадают request ID, method, path и безопасное имя класса исключения.
- Добавлена регрессия, запрещающая утечку текста исключения и throwable stack в production error log.
- Production API envelope, база данных и Flyway не менялись.

## v27.2.22 — Security infrastructure regression and auth hardening suite

- Added direct coverage for API version/deprecation headers, browser security headers, request correlation IDs, Bearer authentication, authentication rate limiting, structured security audit logs and stable API error envelopes.
- Added MockMvc coverage for integrated security boundaries: public headers, mobile/web 401 responses, admin 403 responses, request-id propagation and mixed-case Bearer handling.
- Bearer authentication schemes are now recognized case-insensitively, including repeated whitespace, and the web CSRF bearer matcher uses the same parser.
- Web, legacy mobile and Android v1 login aliases now share one per-IP rate-limit bucket; web and Android registrations share a separate registration bucket.
- Expanded the regression baseline to 57 test classes and 300 `@Test` methods.
- No database schema changed.

## v27.2.21 — Telegram date validation and test harness hotfix

- Fixed Telegram task date parsing so impossible calendar dates such as `31.02` are normalized to the stable `BAD_REQUEST` `ApiException` contract instead of leaking `DateTimeException`.
- Corrected `TelegramBotServiceTest` to register all `MockRestServiceServer` expectations before the first HTTP request; the previous test attempted to add an expectation after execution had already started.
- Added release guards for both regressions.
- Production behavior changed only for malformed Telegram dates; database schema and Flyway migrations are unchanged.
- The suite remains 50 test classes and 254 `@Test` methods.

## v27.2.20 — Telegram bot regression and delivery hardening suite

- Added unit coverage for Telegram command parsing, aliases, task creation/completion, manual and interval overtime, time-off, summaries and invalid input.
- Added HTTP-client coverage for bot polling, one-time link codes, unlinked chats, command replies, update offsets, malformed updates and overlapping-poll protection.
- Added notification-delivery coverage for due windows, per-user failure isolation, deduplication, retry semantics and every reminder message type.
- Added MockMvc coverage for the browser Telegram API, module guards, link-code status, notification settings, unlink cleanup, authentication and CSRF.
- Telegram sends now fail closed: empty responses and `ok=false` are never recorded as successful deliveries.
- Telegram HTTP errors now redact the bot token before they are written to application logs, and updates without a chat id are ignored safely.
- The suite now contains 50 test classes and 254 `@Test` methods.
- No database schema changed.

## v27.2.19 — PostgreSQL migration and CI version hotfix

- Fixed the clean PostgreSQL Flyway chain: `V7__notification_settings.sql` referenced the nonexistent table `app_users`; the canonical table created by `V1__init.sql` is `users`.
- Added `PostgreSqlMigrationContractTest`, which scans migrations in order and rejects foreign keys targeting tables that have not been created by the same or an earlier migration.
- Removed the stale hard-coded `27.2.9` build/release metadata from CI, staging and production workflows. GitHub Actions now resolves the semantic version directly from `pom.xml` and passes it through immutable image and deployment metadata.
- Added release-gate checks for the corrected notification-settings foreign key and dynamic workflow version propagation.
- The suite now contains 46 test classes and 224 `@Test` methods.
- This corrects a pre-production migration that could never succeed on a clean PostgreSQL database; no new Flyway version was added.

## v27.2.18 — Mobile auth and sync lifecycle regression suite

- Added service-level coverage for mobile login, hashed token storage, access/refresh expiry, refresh rotation, logout, device normalization, session activity, owner isolation and revoke-all behaviour.
- Added MockMvc coverage for both legacy and `/api/v1/mobile/auth` login, refresh, logout, session listing and session revocation routes.
- Added service and HTTP coverage for Android v1 idempotency, owner-scoped operation ids, optimistic version conflicts, no-op rejection, module-scoped failures, clear precedence and versioned tombstones.
- Fixed batch isolation for malformed day dates: an invalid date is now returned as a per-operation `REJECTED` result and no longer aborts valid neighbouring operations.
- Added validation guards for structurally malformed direct service operations and preserved legacy mobile clear/delete semantics alongside v1 tombstones.
- Corrected the documented baseline count: v27.2.17 contains 193, not 194, `@Test` methods. The suite now contains 45 classes and 223 `@Test` methods.
- No database schema changed.

## v27.2.17 — Admin test context bootstrap hotfix

- Fixed `UserAdminServiceTest` so it no longer supplies only `dutylog.admin.username` to the full Spring context.
- The incomplete bootstrap pair correctly triggered the production safety guard requiring username and password together, which prevented the test `ApplicationContext` from loading and cascaded into dozens of red test results.
- The test now constructs `UserAdminService` with an explicit bootstrap-admin name while leaving the application bootstrap listener unconfigured, avoiding startup side effects and still testing bootstrap-admin protections.
- Added release guards preventing the incomplete test property from returning.
- No production behavior or database schema changed; the suite remains 41 classes and 193 `@Test` methods.

## v27.2.16 — Profile and administration regression suite

- Added complete profile HTTP coverage for safe defaults, display name and birthday persistence, locale, onboarding, theme preferences, accent normalization and the allow-listed Theme Builder configuration.
- Added malformed/corrupt theme coverage, value clamping, authentication and CSRF boundaries, and guards proving that password hashes and arbitrary CSS-like keys are never returned.
- Added browser-facing mobile-session coverage for owner-only listing, one-session revocation, CSRF, IDOR-safe `404` responses and revoke-all behavior after a password change.
- Added registration-setting service coverage for default/database sources, audit metadata and legacy boolean spellings.
- Added administrative service and MockMvc coverage for search, role filters, pagination, bootstrap/current-user flags, promotion, safe demotion, last-admin protection, password reset, mobile-session revocation and registration toggling.
- Fixed `SystemController` expected client errors to use the stable `ApiException` envelope instead of `ResponseStatusException`, which could be swallowed by the generic advice and returned as `500 INTERNAL_ERROR`.
- Expanded the regression baseline to 41 test classes and 193 `@Test` methods.
- No database schema changed.

## v27.2.15 — Structured module-disabled error envelope hotfix

- Fixed the stable API error contract for disabled modules: `MODULE_DISABLED` responses now include a structured `moduleKey` field instead of forcing clients to parse `message` or the legacy `error` alias.
- Kept the legacy `MODULE_DISABLED:<key>` message for backward compatibility.
- Updated the PWA client to prefer `body.moduleKey` while retaining the legacy marker fallback for older servers.
- Documented `moduleKey` in the OpenAPI `ApiError` schema and module/API documentation.
- Strengthened task, important-date, notification, quick-scenario and overtime module-guard tests around the structured field.
- No database schema changed.

## v27.2.14 — Quick scenarios and overtime API regression suite

- Restored the missing `java.util.Map` and `java.util.stream.Collectors` imports in `ShiftTypeServiceTest` from the verified local correction.
- Added service-level coverage for quick-scenario default seeding, one-time deletion semantics, safe defaults, complete updates, optional-field clearing, FIXED_TIME consistency, owner isolation and stable errors.
- Added MockMvc coverage for legacy and `/api/v1/quick-scenarios` CRUD, validation envelopes, malformed bodies, module guards, CSRF, authentication and IDOR boundaries.
- Added overtime query/export coverage for open/partial/closed filters, date/search filters, safe pagination, CSV BOM and escaping, XLS HTML escaping, FIFO reallocation after usage updates, deletion rules and owner isolation.
- Added MockMvc coverage for legacy and `/api/v1/overtime` credit/usage CRUD, FIFO allocations, account pages, CSV/XLS exports, validation, module guards, CSRF, authentication and foreign IDs.
- Expanded the regression baseline to 36 test classes and 166 `@Test` methods.
- No production behaviour or database schema changed.

## v27.2.13 — Shift types and calendar patterns regression suite

- Added service-level coverage for built-in shift seeding, legacy-default repair, custom shift CRUD, optional time/reminder clearing, protected built-in identity and owner isolation.
- Added deletion coverage proving that removing a custom shift deletes shift-only rows but preserves notes, emoji, overtime and time-off on non-empty days.
- Added schedule-pattern coverage for 2/2, day/night/48 and weekday-rotated five-day weeks across month, year and leap-day boundaries.
- Added overwrite coverage proving that bulk fill changes only the shift while preserving day metadata, and that overwrite=false keeps existing shifts while filling empty dates.
- Added MockMvc coverage for `/api/days/fill`, `/api/v1/days/fill`, `/api/shift-types` and `/api/v1/shift-types`, including validation envelopes, CSRF, authentication and ownership boundaries.
- Added frontend contract guards for every schedule preset and the selected-weekday rotation used by the five-day template.
- Expanded the regression baseline to 32 test classes and 141 `@Test` methods.
- No production behaviour or database schema changed.

## v27.2.12 — Important dates regression suite

- Added service-level coverage for important-day defaults, owner-scoped ordering, full updates, deletion and stable error handling.
- Added recurrence coverage for one-time dates, monthly end-of-month clamping, yearly leap-day fallback and leap-year restoration.
- Added deterministic occurrence ordering by date and title, plus owner isolation and no-duplicate source-event checks.
- Added MockMvc coverage for legacy and `/api/v1/important-days` aliases, full CRUD, occurrences, validation envelopes, malformed JSON, missing parameters, CSRF, authentication, module guards and ownership boundaries.
- Extended the regression baseline and release gate with the Important dates contracts.
- No production behaviour or database schema changed.

## v27.2.11 — Task priority regression test correction

- Corrected the task regression suite: `URGENT` is a supported `TaskPriority`, so it must not be used as an invalid-filter example.
- The negative validation assertion now uses the genuinely unsupported value `critical`.
- Added a positive regression assertion proving that the case-insensitive `urgent` board filter returns URGENT tasks.
- No production behaviour or database schema changed.

## v27.2.10 — Task board status validation hotfix

- Fixed task-board status validation so an unknown status is rejected even when the user has no tasks.
- Moved validation before the repository stream instead of relying on a per-task filter side effect.
- The existing service and MockMvc regression tests now guard the empty-board case that exposed the defect.
- No database schema changed.

## v27.2.9 — Task regression suite

- Added service-level task coverage for creation defaults, trimming, updates, reminder cleanup, day/range lists, board statuses, category/priority/search/date filters, pagination, validation and deletion.
- Added MockMvc coverage for legacy and `/api/v1/tasks` aliases, complete CRUD, board metadata, stable error envelopes, CSRF, authentication, module guards, data preservation while disabled and owner isolation.
- Documented why IntelliJ JUnit runs do not generate JaCoCo and how to run `mvn clean verify` from IntelliJ's Maven tool window or a Windows terminal.
- No production behaviour or database schema changed.

## v27.2.8 — Test compilation hotfix

- Fixed invalid Java character literals in `CalendarMonthReloadContractTest`: Java strings now use escaped double quotes.
- Added release-check guards for the exact browser-cache assertions so this contract test cannot silently become uncompilable again.
- No production behaviour or database schema changed.

## v27.2.7 — Regression test baseline and notification poll shutdown

- Stopped the browser notification interval immediately when the Notifications module is disabled.
- Added defensive recovery for a stale frontend module map: one raced `MODULE_DISABLED:notifications` response stops polling and resynchronizes module metadata instead of repeating every ten seconds.
- Prevented an in-flight reminder response from being delivered after the module is switched off.
- Preserved structured `code` and `moduleKey` metadata on frontend API errors.
- Added behavioural backend coverage for shift, task, important-day and digest reminder calculations, completed-task filtering and user isolation.
- Added notification API boundary, module dependency, task reminder persistence and frontend scheduler contract tests.
- CI now runs `mvn verify` and publishes a JaCoCo HTML coverage report as a build artifact.
- Added a regression test matrix that maps the successful manual pass to automated guards.

## v27.2.6 — Module-isolated day saves and browser reminders

- Fixed the `Minimum` preset regression: shift, marker and note writes no longer fail with `MODULE_DISABLED:overtime` merely because the web snapshot contains neutral overtime values.
- Disabled Notes/Overtime modules are now read-only for day updates: hidden data stays in the database and is not exposed in the response until the module is enabled again.
- The web client omits optional module fields from `PUT /api/days/{date}` when their modules are disabled.
- Added a running-page/PWA browser notification scheduler with deduplication, a five-minute wake-up grace window and service-worker notification click handling.
- Task reminder controls are disabled with an explanation while the Notifications module is off.
- Prevented an early Telegram status request from generating a misleading `403` before module metadata finishes loading.
- Added `DayModuleIsolationTest` for old-client neutral payloads, hidden-data preservation and real-write rejection.

## v27.2.5 — Calendar day identity hotfix

- Preserve `date` and sync metadata in module-aware day sanitization.
- Prevent calendar rows from collapsing into `state.days[undefined]`.
- Treat missing static resources as 404 instead of 500.

# v27.2.5 — Calendar day identity hotfix

- Bulk schedule rows are saved explicitly and verified by a fresh database read before the endpoint reports success.
- Calendar reload after fill bypasses IndexedDB and browser HTTP cache.
- Calendar responses are marked `Cache-Control: no-store`.
- Added end-to-end MockMvc coverage for POST fill followed by GET calendar with all 31 dates.
- Preserved stale-response and month-specific snapshot guards from the earlier calendar fixes.

## v27.1.0 — Android API contract freeze

- Added stable `/api/v1/**` aliases and a dedicated Bearer-only `/api/v1/mobile/**` contract for Android.
- Added mobile registration that returns the first access/refresh token pair immediately.
- Standardized API failures as machine-readable envelopes with `code`, `message`, `fields`, `requestId` and `timestamp` while retaining legacy `error`; malformed parameters and unexpected controller failures use the same envelope.
- Added optimistic `version`/`updatedAt` fields to day records and Flyway migration `V22__android_api_contract.sql`; version `0` is reserved for a missing row and persisted rows start at `1`.
- Added durable per-user idempotency records for offline sync operations without storing note/task payloads.
- Added per-operation sync results: `APPLIED`, `ALREADY_APPLIED`, `CONFLICT` and `REJECTED`, including explicit `NO_CHANGES` rejection for empty mutations.
- Added version-conflict protection via `baseVersion` and preserved empty version rows as lightweight tombstones.
- Added the canonical OpenAPI file at `/openapi/dutylog-v1.yaml` and contract documentation/tests.
- Added `X-DutyLog-Api-Version: v1` and deprecation headers for legacy `/api/mobile/**` routes.
- Throttled mobile-token `lastUsedAt` writes to once per five minutes and bounded idempotency retention to 90 days by default.
- Kept web/PWA endpoints backward compatible; no frontend product feature was added.

## v27.0-rc4 — Security consolidation

- Added bounded streaming export of all owner-scoped notes as Obsidian-friendly Markdown ZIP.
- Escaped YAML front matter, disabled response caching and documented export availability when Notes UI is disabled.
- Split `/api/mobile/**` into a stateless Bearer-only security chain; browser sessions can no longer authenticate mobile endpoints.
- Added explicit mobile-boundary, export and expanded IDOR regression tests with exact 404 assertions.
- Added structured security events for login/authz/token/Telegram/admin-reset actions and bounded rolling production logs.
- Added application-level authentication rate limiting shared by Caddy and nginx deployments.
- Closed public registration by default in production and raised normal password minimum to 8 characters.
- Removed `script-src 'unsafe-inline'` by extracting login JavaScript; synchronized CSP/HSTS headers across Spring, Caddy and nginx.
- Added Dependabot for Maven, GitHub Actions and Docker, and made the runtime container non-root.
- Synchronized runtime, service worker, docs, smoke checks and package layout on `27.0-rc4`.

## v27.0-rc1 — Release Candidate

- Froze v26.6.12 as the UX-polished release-candidate baseline.
- Bumped app, service worker, static cache-busting, smoke-test and documentation version to `27.0-rc1`.
- Added release-candidate documentation: production deploy guide, backup/restore guide, user guide and RC checklist.
- Kept feature freeze: no new product features, only release documentation and final static guardrails.
- CI and local release gate are expected to pass before deploying this candidate.

## v26.6.12 — notifications alignment and admin navigation hotfix

- Pinned notification status chips to the right side of the notifications settings header so RU/EN labels no longer drift into the middle of the card.
- Made the browser-permission chip and active notification settings feel less disabled by using clearer active-state styling.
- Added a settings-style side navigation to the administrator view: Users, Registration and Diagnostics.
- Added release-check guardrails for the notification header layout and admin navigation.


## v26.6.11 — UI alignment and registration test hotfix

- Fixed `RegistrationTest` compilation after the login-language hotfix: the JSON body helper now supports `languagePreference`.
- Kept settings header controls pinned on the right across RU/EN so autosave, browser-permission chips and the profile avatar do not drift when labels change length.
- Documented the Java 25 Tomcat Native warning as a local runtime warning, not an application startup failure.
- Added release-check guardrails for the helper overload and right-side settings alignment.


## v26.6.10 — login language registration hotfix

- Registration now preserves the language selected on the login page.
- `login.html` sends `languagePreference` during account creation.
- New users created with EN selected land in the English onboarding/app shell instead of falling back to RU.
- Added registration language persistence test.
- Release/check docs updated to v26.6.10.

## v26.6.9 — English i18n polish hotfix

- Fixed Russian leftovers after switching the interface to English: today marker, calendar total label, working-time box, autosave chip and notification browser status.
- Re-render settings panels when language changes so dynamic cards do not keep stale Russian text.
- Localized built-in shift names (`Дневная`, `Ночная`, `Выходной`) in calendar chips, selected-day summaries and shift settings while keeping custom shift names unchanged.
- Localized quick-scenario hints, notification empty states and common admin/status strings.
- Added CSS language-specific text for the current-day marker.
- Added release-check guardrails for the i18n hotfix.

## v26.6.8 — today clarity and dismissible hidden-blocks hotfix

- Made the current-day calendar marker visually different from the selected-day outline: today now uses a subtle tinted/dashed cell, corner dot and text label, while the clicked day keeps the strong solid accent outline.
- Added a close button to the selected-day `Скрытые блоки` notice.
- The hidden-blocks notice dismissal is stored in localStorage and stays hidden on future visits for that browser.
- Kept `Настроить модули` available while the notice is visible, but removed the always-on nagging after dismissal.
- Added release-check guardrails for the dismissible notice and less-confusing today styling.

## v26.6.7 — onboarding and today highlight hotfix

- Renamed the onboarding preset `Работа + переработки` to the shorter `Стандарт`.
- The selected onboarding preset is now highlighted dynamically; choosing `Минимум`, `Стандарт` or `Всё включить` updates the active pill immediately.
- Manual module changes clear the preset highlight unless the selection exactly matches a preset again.
- Made the current day in the calendar more visible with a dedicated highlighted cell style, not only a small text label.
- Added release-check guardrails for the onboarding preset state and today-cell highlight.

## v26.6.6 — CI permission hotfix

- Fixed GitHub Actions release gate startup on checkouts where shell scripts lose executable bit.
- CI now runs `bash ./deploy/scripts/release-check.sh` instead of executing the script directly.
- Added `docs/CI_PERMISSION_HOTFIX.md` and release-check guardrails for the CI invocation.

## v26.6.5 — properties and tests hotfix

- Replaced Cyrillic comments in `.properties` files with ASCII English comments to avoid mojibake in editors/terminals with the wrong file encoding.
- Added `spring.jpa.open-in-view=false` to test properties to match the application baseline and remove the test warning.
- Fixed Telegram link tests after the Telegram module guard: tests now enable the Telegram module explicitly where linking is expected.
- Fixed the expired Telegram-code test fixture to use the real `DL-123456` code format.
- Updated the registration CSRF regression to expect Spring Security's `403 Forbidden` for missing CSRF.
- Fixed module dependency semantics: disabling a module now disables dependent modules too, so disabling Overtime also disables Scenarios and mobile sync guards stay effective.
- Added release-check guardrails for ASCII `.properties`, module dependency cascade and the updated test expectations.

## v26.6.4 — console and module details UX hotfix

- Hid module contract counts and technical details from regular users; they remain visible only to administrators.
- Stopped admin registration/user-list requests from running during generic settings initialization, removing avoidable `403` console noise for non-admin sessions.
- Fixed `renderTimeSettings()` shadowing the translation helper `t()`, which caused a settings-route runtime error.
- Added release-check guardrails for the settings-time shadowing bug, admin auto-fetch noise, and admin-only module developer details.

## v26.6.3 — compact modules UX hotfix

- Made the modules settings screen more compact: shorter cards, independent grid row height and no oversized stretch from disabled module copy.
- Moved module runtime/API/offline contract details behind collapsed technical details.
- Shortened disabled-module copy while keeping the data-preservation message.
- Fixed module badge/text overlap in narrow cards, including the Admin module card.
- Reworked the selected-day hidden-blocks notice so the “Configure modules” button no longer collides with the text.

## v26.6.2 — frontend runtime hotfix

- Fixed boot-time helper ordering in split JS: `$` and `esc()` are now defined before `applyAppearance(loadLocalAppearance())`.
- Fixed the blank calendar/tabs regression caused by `ReferenceError: esc is not defined` and cascading `$ is not defined` errors.
- Hardened the service worker against unsupported request schemes such as `chrome-extension://` and made runtime cache writes non-fatal.
- Added release-check guardrails for boot helper order and unsupported service-worker cache writes.

## v26.6.1 — UX boot hotfix

- Fixed a release UX blocker where the PWA could remain visually stuck on the boot overlay.
- Boot overlay is no longer shipped as the initial static body state.
- Added startup failsafe and error/rejection handlers to unlock the interface if boot fails.
- Added release-check guardrail against reintroducing initial `appBooting` body state.

# v26.6 — UX release polish

- Kept feature freeze: no new large product scope.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.6`.
- Added a visible boot/loading state while the PWA prepares profile, modules and the calendar.
- Added calendar, task board and overtime ledger loading skeletons so navigation does not feel frozen on slow links.
- Reworked empty states for tasks, important days and the overtime ledger into compact explanatory cards.
- Polished module settings with enabled/disabled/basic counters and explicit disabled-module copy explaining that data is preserved.
- Added `docs/UX_RELEASE_POLISH.md` and updated release checklist commands for the v26.6 tag.

# v26.5 — Security review

- Kept release stabilization freeze: no new user-facing features.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.5`.
- Added application-level security headers through `SecurityHeadersFilter` so the baseline browser policy is present even before/without reverse-proxy hardening.
- Added production session cookie hardening: `HttpOnly`, `Secure`, `SameSite=Lax` and explicit session timeout.
- Closed a module-boundary gap in `/api/mobile/sync`: mobile day sync can no longer write notes or overtime fields when the corresponding modules are disabled.
- Closed a Telegram module-boundary gap: pending Telegram link codes no longer link an account after the user disables the Telegram module.
- Added `ModuleSecurityTest` regressions for mobile sync module guards and browser security headers.
- Extended `release-check.sh` with security review guardrails for headers, session cookie settings, mobile module guards, Telegram module guards and regression tests.
- Added `docs/SECURITY_REVIEW.md` with the reviewed threat boundaries and follow-up policy.

# v26.4 — Code cleanup

- Kept release stabilization freeze: no new user-facing features.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.4`.
- Cleaned split frontend file headers so they describe the current ordered-script runtime instead of the old monolithic `app.js` history.
- Removed stale runtime comments that still pointed to `app.js` as the active frontend entrypoint.
- Hardened `deploy/scripts/release-check.sh` with exact split-JS order validation and a legacy runtime `app.js` reference guard.
- Refactored `deploy/scripts/smoke-test.sh` and `release-check.sh` to use an explicit `STATIC_JS` list instead of repeating split asset names in scattered checks.
- Added `docs/CODE_CLEANUP.md` with stabilization-safe cleanup rules and postponed technical debt.

# v26.3 — Release hardening

- Entered release stabilization: no new user-facing feature scope in this release.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.3`.
- Fixed admin system status version source: `/api/admin/status` now uses `info.app.version` instead of hardcoded `26.0`.
- Added `deploy/scripts/release-check.sh` as a local release gate for version consistency, frontend checks, Flyway migration sequence, shell syntax, Java brace balance and production config safety.
- CI now runs `mvn test` and then the same release gate.
- Hardened production preflight: stricter domain validation, distinct secret checks, public app-port check and Caddy security header check.
- Added HSTS and a basic CSP to Caddy examples.
- Updated release, production launch and runbook docs for the current split-frontend release.

# v26.2 — Tests, CI and frontend split

- Split the former `app.js` into ordered static JS files under `static/js/`.
- Added GitHub Actions CI with Maven tests and frontend static checks.
- Added service and web regression tests for calendar, overtime, Telegram linking, registration, profile password and admin access.
- Added nginx auth rate-limit example and Caddy warning.
- Updated smoke-test for split frontend assets.

- Added first-run module onboarding for new users.
- New users choose a calmer module set before landing in the full interface.
- Added module presets: minimum, work + overtime, enable all.
- Added per-module onboarding toggles powered by the existing module registry.
- Added `users.onboarding_completed` via `V21__user_onboarding.sql`; existing users are marked completed by default during upgrade.
- Profile API now exposes and accepts `onboardingCompleted`.
- Onboarding saves module choices through `PATCH /api/modules`; skipped onboarding keeps current/default module settings.
- Added `docs/ONBOARDING.md`.
- Frontend/backend/service-worker versions bumped to `26.2`.

# v25.3 — Developer module contracts

- Added backend package `ru.daniil.shifts.module` with stable `ModuleKeys`, `ModuleCategory`, `ModuleContract` and canonical `DutyLogModules` registry.
- `ModuleService` now uses the contract registry instead of local ad-hoc definitions while preserving existing `ModuleService.*` constants for controllers.
- Module API payload now includes contract metadata: category, display order, UI slots, API prefixes and offline queue types.
- Added `GET /api/modules/contracts` for clients/tests that explicitly need module contract metadata.
- Module settings UI now shows module category and contract summary, and dependency names are rendered as user-facing module titles.
- Added `docs/MODULE_CONTRACTS.md` and updated module/API documentation.
- Frontend/backend/service-worker versions bumped to `25.3`.

# v25.2 — Module-aware offline snapshot

- Calendar snapshot responses now include the effective module list for the current user.
- Calendar aggregation respects disabled modules server-side: disabled tasks, important dates, overtime, notifications and scenarios are omitted from the bundle.
- Disabled notes/overtime fields are stripped from day entries in the calendar bundle, so offline cache does not expose hidden modules.
- IndexedDB snapshots are sanitized according to enabled modules before being written.
- Offline queue refuses new mutations that belong to disabled modules and moves stale disabled-module mutations to failed operations instead of replaying them.
- Offline diagnostics/export now include module snapshot information.
- Frontend/backend/service-worker versions bumped to `25.2`.

# v25.1 — Module-aware day panel

- Selected-day panel sections now have explicit module ownership.
- Disabled modules hide their day panel blocks through one module-aware registry instead of scattered ad-hoc toggles.
- Calendar day markers now respect modules: notes, tasks, important dates, overtime and reminders no longer show when their module is off.
- Day panel rendering skips disabled module renderers and avoids touching their controls.
- Added an inline day-panel hint when blocks are hidden by disabled modules; data is not deleted and modules can be re-enabled in settings.
- Fixed the note preview tab handler shadowing the translation function.
- Frontend/backend/service-worker versions bumped to `25.1`.

# v25.0 — User modules

- Added modular-monolith foundation: backend module registry plus per-user module switches.
- Added `user_module_settings` table via `V20__user_modules.sql`.
- Added `GET /api/modules` and `PATCH /api/modules`.
- Added Settings → Modules UI with safe toggles, module descriptions and dependency hints.
- Disabled modules are hidden from main navigation, selected-day panel and related settings sections.
- Major feature APIs now return `403 MODULE_DISABLED:<key>` when the module is disabled.
- Disabling a module does not delete user data; it only hides and guards the feature.
- Frontend/backend/service-worker versions bumped to `25.0`.

# v24.0.4 — i18n full coverage polish

- Expanded English translations across the web/PWA UI: day panel, overtime, tasks, shift settings, scenarios, notifications, admin, diagnostics, offline sync and common dynamic statuses.
- Added dynamic translation helpers for counters, page ranges, statuses, sync messages, confirmation text and calculated labels.
- Added English CSS pseudo-content for offline-only warnings.
- Bumped frontend/backend/service-worker runtime version to `24.0.4`.

# v24.0.2 — I18N coverage polish

- Expanded English translation coverage for profile, password, active sessions, Telegram linking, settings hints and diagnostics copy.
- Dynamic profile/session/Telegram messages now use the app dictionary instead of hardcoded Russian strings.
- Frontend/backend/service-worker versions bumped to `24.0.2`.

# v24.0.1 — Shift settings UX and important-day refresh

- The `+` shift button in the selected-day panel now opens Settings directly on the expanded `Shifts` section, scrolls to it and focuses the custom shift name field.
- Important-day deletion now refreshes the selected-day summary immediately, including the `0 important days` state, without requiring a browser reload.
- Custom shift form copy clarified: `Calendar, h` is only a short calendar label, while `Norm, h` is used for overtime calculations. Empty norm is auto-calculated from start/end/break.
- Frontend/backend/service-worker versions bumped to `24.0.1`.

# v24.0 — Notification status polish

- Notification settings header now uses compact status chips instead of raw text like `0 шт · браузер: разрешено`.
- Reminder count and browser permission status are visually aligned with theme/admin/time status chips.
- Frontend/backend/service-worker versions bumped to `24.0`.

# v23.1.5 — Offline sync copy and compile polish

- Added missing `PostMapping` import in `SystemController` for the admin password reset endpoint.
- Renamed confusing offline sync UI copy: `Повторить ошибки` is now `Повторить неудачные операции`.
- Renamed related failed-operation actions so the dialog talks about operations, not “repeating errors”.
- Frontend/backend/service-worker versions bumped to `23.1.5`.

# v23.1.4 — Telegram tasks pagination compile fix

- Fixed Telegram `/tasks` command after `TaskService.listBoard(...)` was changed to return a paged result.
- Telegram now requests the first page of open tasks and uses the page total for the summary.
- Frontend/backend/service-worker versions bumped to `23.1.4`.

# v23.1.3 — Large list pagination hardening

- Admin users list now loads by pages instead of sending all users to the browser.
- Global task board now uses backend response pagination with page size capped at 100.
- Overtime ledger table now requests a paged ledger response; CSV/XLS export remains full for the selected filters.
- Added pager controls and page-size selectors for users, tasks and overtime ledger.
- Frontend/backend/service-worker versions bumped to `23.1.3`.

# v23.1.2 — Spring parameter binding fix

- Исправлен runtime-крэш Spring MVC: `Name for argument ... not specified` при запросах вроде `/api/calendar` и `/api/tasks/board`.
- Во всех контроллерах явно указаны имена `@RequestParam` и `@PathVariable`, чтобы приложение не зависело от reflection parameter names при сборке из IntelliJ/IDEA.
- В `pom.xml` добавлен `maven-compiler-plugin` с `<parameters>true</parameters>` как дополнительная страховка для Maven-сборки.
- Frontend/backend/service-worker версии подняты до `23.1.2`.

# v23.1.1 — Visual polish

- Theme summary in `Внешний вид` redesigned from raw text into compact chips: preset, base mode and accent color.
- `Автосохранение` in time settings is now shown as a status badge integrated into the card header.
- Users/roles admin header now shows clean metric chips: `Пользователей` and `Админов`, fixing the broken line wrap.
- Frontend/backend/service-worker versions bumped to `23.1.1`.

# v23.1 — Theme Builder

- Расширен раздел `Внешний вид` до безопасного Theme Builder без доступа к CSS.
- Добавлены пресеты: `DutyLog Default`, `Midnight`, `OLED Black`, `Forest`, `Sunset`, `Industrial`, `Soft Purple` и режим `Custom`.
- Добавлена точная настройка через контролы: фон приложения, цвет карточек, внутренние блоки, текст, вторичный текст, границы, стиль кнопок, стиль карточек, тени, плотность и скругление.
- Добавлен live preview прямо в настройках: календарные клетки, карточка и кнопки показывают изменения до сохранения.
- Настройки темы сохраняются в профиле пользователя как безопасный JSON `theme_config`, содержащий только whitelist-поля, не пользовательский CSS.
- В БД добавлены `users.theme_preset` и `users.theme_config` через миграцию `V18__theme_builder.sql`.
- Backend валидирует значения Theme Builder: только `#RRGGBB`, разрешённые enum-значения и ограниченный диапазон скругления.
- Роли `USER/ADMIN`, будущий `account_tier` `FREE/PAID/VIP` и внешний вид остаются отдельными слоями.
- Emoji-маркеры дней из v23.0 сохранены; картинки/стикерпаки/загрузка файлов по-прежнему не добавлялись.
- Frontend/backend/service-worker версии подняты до `23.1`.

# v22.3 — Users and roles admin panel

- Добавлен админский список пользователей в разделе `Система` → `Пользователи и роли`.
- Администратор видит логин, отображаемое имя, роль, отметку `env admin`, текущего пользователя и read-only тариф `FREE` как задел под будущие `PAID/VIP`.
- Добавлены admin endpoints `GET /api/admin/users`, `PATCH /api/admin/users/{id}/role`, `POST /api/admin/users/{id}/password`.
- Роли пока строго ограничены `USER` и `ADMIN`; публичная регистрация по-прежнему создаёт только `USER`.
- Стартовый env-админ остаётся защищённым: его нельзя понизить до `USER`, а собственную активную админскую роль нельзя снять из UI.
- Постоянное демоутирование всех “неожиданных” админов заменено на одноразовую cleanup-миграцию при первом старте v22.3: дальше дополнительных админов можно легально назначать из админки.
- Пароль bootstrap-админа больше не перезатирается env-паролем при каждом рестарте. Env-пароль используется для первого создания; аварийный сброс возможен через `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true`.
- Добавлен админский сброс пароля пользователя с отзывом мобильных токенов.
- В БД добавлены `users.account_tier`, `users.created_at`, `users.updated_at` через миграцию `V16__user_admin_management.sql`.
- Диагностика администратора теперь показывает количество пользователей, количество админов, разрешённые роли и зарезервированные будущие тарифы.
- Версии frontend/backend/smoke-test подняты до `22.3`.

# v22.2 — Registration hardening

- Публичная регистрация теперь управляется из админского раздела `Система`, а не только через deployment-конфиг.
- Добавлена системная настройка `registration.enabled` в БД (`app_settings`) и миграция `V15__app_settings.sql`.
- Backend строго проверяет настройку при `POST /api/auth/register`: если регистрация закрыта, даже прямой запрос получает `403`.
- Добавлен публичный endpoint `GET /api/auth/registration-status`, чтобы страница входа могла скрывать форму регистрации без авторизации.
- Добавлены admin endpoints `GET/PATCH /api/admin/settings/registration` для чтения и изменения настройки регистрации.
- В админском разделе добавлена карточка `Публичная регистрация` с переключателем и текущим статусом.
- Диагностика администратора теперь показывает состояние публичной регистрации и источник настройки.
- Страница входа скрывает вкладку регистрации, если админ закрыл публичную регистрацию.
- Стартовый админ по-прежнему создаётся только через env bootstrap; отдельной UI-регистрации администраторов нет.
- Версии frontend/backend/smoke-test подняты до `22.2`.

# v22.1 — Secure admin bootstrap

- Удалён риск “кто первый зарегистрировался — тот админ”: публичная регистрация теперь всегда создаёт пользователя с ролью `USER`.
- Добавлен backend bootstrap администратора через переменные окружения `DUTYLOG_ADMIN_USERNAME` и `DUTYLOG_ADMIN_PASSWORD`. При старте приложение создаёт такого пользователя или повышает существующего до `ADMIN`, обновляет пароль и демоутит неожиданные `ADMIN`-аккаунты до `USER`.
- Стартовый набор смен вынесен в `DefaultShiftSeedService`, чтобы его получали и обычные регистрации, и создаваемый bootstrap-админ.
- Spring Security `UserDetailsService` теперь отражает роль из БД: администратор получает `ROLE_ADMIN`, обычный пользователь — `ROLE_USER`.
- `docker-compose.prod.yml`, `.env.production.example`, `.env.example` и `application*.properties` обновлены под явный admin bootstrap.
- `deploy/scripts/check-production-env.sh` теперь требует явные admin-переменные и проверяет длину/формат без вывода секретов.
- Добавлен `docs/ADMIN_BOOTSTRAP.md` и обновлены production-документы/чеклисты: первый администратор больше не зависит от порядка регистрации.
- Версии frontend/backend/smoke-test подняты до `22.1`.

# v22.0 — Production launch

- Добавлен `docs/PRODUCTION_LAUNCH.md`: короткий боевой сценарий первого VPS-запуска от `.env` до smoke test, backup и проверки PWA на телефоне.
- Добавлен `deploy/scripts/check-production-env.sh`: preflight production-конфигурации без вывода секретов. Проверяет `.env`, домен, пароли, Telegram-настройки, Caddyfile, compose и базовые команды.
- Усилен `deploy/scripts/smoke-test.sh`: теперь проверяются health, login page, app shell, manifest, service worker версии `v22.0`, static assets и защищённый admin API.
- Обновлены `README.md`, `docs/DEPLOY.md`, `docs/PRODUCTION_RUNBOOK.md`, `docs/VPS_CHECKLIST.md`, `docs/SECURITY_CHECKLIST.md`, `docs/BACKUP.md` и `docs/RELEASE_CHECKLIST.md` под первый production launch.
- Добавлена metadata для `/actuator/info`: имя приложения, версия `22.0`, текущий тип клиента `web/PWA inside Spring Boot monolith`.
- Frontend-кэш поднят до `v22.0`: `app.css?v=22.0`, `app.js?v=22.0`, `dutylog-shell-v22.0`.
- Backend-версия в админской диагностике поднята до `22.0`.
- Product scope не расширялся: offline scope остался прежним, native mobile-приложение в релиз не добавлялось.

# v21.2 — Offline QA and release candidate

- Добавлена пользовательская диагностика оффлайна прямо в панели синхронизации: online/offline, доступность IndexedDB, возраст snapshot, очередь, ошибки и состояние sync-lock.
- Добавлена кнопка `Скопировать диагностику`, чтобы быстро снять безопасный отчёт по web/PWA-клиенту без серверной админки.
- Диагностические отчёты явно фиксируют текущую платформу: web/PWA внутри Spring Boot-монолита; отдельного native mobile-приложения пока нет.
- Добавлен `docs/RELEASE_CHECKLIST.md` с ручным чеклистом перед релизом, offline QA, проверкой PWA и подготовкой к VPS-деплою.
- Обновлена offline-документация: v21.2 считается release candidate, offline scope не расширен.
- Исправлена мелкая фронтовая опечатка в `dataLayer.loadCalendar`: лишняя вложенная проверка `navigator.onLine` после чтения snapshot.
- Frontend-кэш поднят до `v21.2`: `app.css?v=21.2`, `app.js?v=21.2`, `dutylog-shell-v21.2`.
- Backend-версия в диагностике поднята до `21.2`.

# v21.1 — Offline hardening

- Добавлена подробная панель синхронизации из индикатора в шапке: ожидающие операции, неудачные операции, последняя синхронизация и состояние подключения.
- Добавлена ручная синхронизация из панели, массовый повтор неудачных операций, повтор одной неудачной операции и удаление неудачной операции из списка.
- Добавлен emergency export локального offline-состояния в JSON: `app`, `version`, `snapshot`, `queue`, `failed`, `meta` и сведения браузера.
- Добавлена защита от двух вкладок: синхронизация берёт короткий lock в `localStorage`, чтобы очередь не проигрывалась параллельно.
- Индикатор теперь показывает устаревшие данные, если последний локальный снимок старше суток.
- Ошибки оффлайн-операций стали точнее: для переработок, графиков, смен, уведомлений, Telegram, профиля и важных дат объясняется, почему нужна связь с сервером.
- Обновлены тексты offline-плашек в интерфейсе.
- Frontend-кэш поднят до `v21.1`: `app.css?v=21.1`, `app.js?v=21.1`, `dutylog-shell-v21.1`.
- Backend-версия в диагностике поднята до `21.1`.

# v21.0 — Offline Mode / local-first lite

- Добавлен фронтовый offline-слой без переписывания backend: `dataLayer`, IndexedDB и очередь синхронизации.
- Приложение сохраняет последний снимок календаря в локальную базу `dutylog-offline` и может открыть его без сети.
- В шапку добавлен индикатор подключения: онлайн/оффлайн, время последней синхронизации и счётчик неотправленных изменений.
- Оффлайн поддержаны безопасные операции:
  - смена выбранного дня;
  - заметка выбранного дня;
  - установка состояния задачи `done: true/false`.
- Изменения применяются в UI оптимистично, сохраняются в IndexedDB и отправляются на сервер при появлении сети или по клику на индикатор.
- Очередь проигрывается FIFO; сетевые ошибки оставляют операцию в очереди, а 400/404/409 переносятся в список «не применилось».
- Service Worker не участвует в хранении данных: данные лежат только в IndexedDB/dataLayer, оболочка остаётся network-first.
- Сложные операции — переработки, списания, смены, сценарии, уведомления, Telegram, профиль — при отсутствии сети получают понятный отказ: «Эта операция требует связи с сервером».
- Добавлена документация `docs/OFFLINE_MODE.md`.
- Frontend-кэш поднят до `v21.0`: `app.css?v=21.0`, `app.js?v=21.0`, `dutylog-shell-v21.0`.
- Backend-версия в диагностике поднята до `21.0`.

# v20.8 — Production launch hardening

- Добавлен `docker-compose.prod.yml` для VPS: PostgreSQL, DutyLog app и Caddy reverse proxy в одной production-схеме.
- Добавлен `.env.production.example` с production-переменными и безопасными placeholder-значениями.
- Добавлен `deploy/caddy/Caddyfile.example` для HTTPS через Caddy.
- Добавлен альтернативный пример nginx: `deploy/nginx/dutylog.conf.example`.
- Исправлен `Dockerfile` после переименования проекта: теперь копируется `dutylog-*.jar`, а не старый `shift-calendar-*.jar`.
- В runtime-образ добавлен `curl`, чтобы healthcheck контейнера мог проверять `/actuator/health`.
- Добавлен Docker `HEALTHCHECK` и healthcheck app-сервиса в compose.
- Добавлен `deploy/scripts/smoke-test.sh` для быстрой проверки health/login/manifest/protected API после запуска.
- Добавлены документы:
  - `docs/PRODUCTION_RUNBOOK.md`;
  - `docs/SECURITY_CHECKLIST.md`.
- Обновлены `README.md`, `docs/DEPLOY.md`, `docs/VPS_CHECKLIST.md`, `docs/ARCHITECTURE.md` и `docs/API.md`.
- Frontend-кэш поднят до `v20.8`: `app.css?v=20.8`, `app.js?v=20.8`, `dutylog-shell-v20.8`.
- Backend-версия в диагностике поднята до `20.8`.

# v20.7 — Backup and restore

- Добавлены production-ready скрипты для PostgreSQL: `deploy/scripts/backup-postgres.sh`, `deploy/scripts/restore-postgres.sh`, `deploy/scripts/list-backups.sh`.
- Backup теперь создаётся в PostgreSQL custom format `.dump` с `--no-owner`, `--no-privileges` и checksum `.sha256`, если доступен `sha256sum`.
- Restore поддерживает `.dump`, `.dump.gz`, `.sql`, `.sql.gz`, спрашивает подтверждение и останавливает app-контейнер перед восстановлением.
- Добавлены переменные `.env.example` для backup-скриптов: `BACKUP_DIR`, `BACKUP_KEEP_LAST`, `DUTYLOG_DB_SERVICE`, `DUTYLOG_APP_SERVICE`.
- Добавлены systemd-примеры ежедневного backup: `deploy/systemd/dutylog-backup.service.example`, `deploy/systemd/dutylog-backup.timer.example`.
- Добавлены документы `docs/BACKUP.md`, `docs/DEPLOY.md`, `docs/VPS_CHECKLIST.md`.
- README обновлён: backup/restore, безопасная остановка Docker, документация для VPS.
- Frontend-кэш поднят до `v20.7`: `app.css?v=20.7`, `app.js?v=20.7`, `dutylog-shell-v20.7`.
- Backend-версия в диагностике поднята до `20.7`.

# v20.6 — Admin diagnostics profile

- Служебная диагностика вынесена из обычных пользовательских настроек в отдельный раздел `Система`.
- Кнопка `Система` появляется в шапке только у администратора.
- Добавлена роль пользователя `ADMIN/USER`; первый пользователь существующей установки автоматически получает `ADMIN` через миграцию `V14__admin_role_and_diagnostics.sql`.
- Новые установки назначают первого зарегистрированного пользователя администратором, остальные пользователи создаются обычными.
- Endpoint диагностики перенесён с `GET /api/system/status` на `GET /api/admin/status` и теперь возвращает данные только администратору.
- Вкладка `⚙` очищена от технической диагностики и снова выглядит как пользовательские настройки.
- Frontend-кэш поднят до `v20.6`: `app.css?v=20.6`, `app.js?v=20.6`, `dutylog-shell-v20.6`.
- Backend-версия в диагностике поднята до `20.6`.

# v20.5 — Product copy and architecture cleanup

- Пользовательские тексты в настройках приведены к product-ready стилю: меньше внутренних формулировок, больше понятных пользовательских подсказок.
- В интерфейсе заменены технические слова `backend/frontend` на `сервер/интерфейс` там, где это видит пользователь.
- Раздел диагностики стал понятнее: `Кэш приложения`, `Защита сессии`, `Версия сервера`, безопасный отчёт без секретов.
- README переписан как продуктовая документация: возможности, стек, запуск, production, Telegram, безопасность и ссылки на документы.
- Добавлен `docs/ARCHITECTURE.md` с описанием слоёв, модулей, границ web/mobile API и правил изменений.
- Добавлен `docs/PRODUCT_COPY.md` со стилем текстов интерфейса и словарём терминов.
- Frontend-кэш поднят до `v20.5`: `app.css?v=20.5`, `app.js?v=20.5`, `dutylog-shell-v20.5`.
- Backend-версия в диагностике поднята до `20.5`.

# v20.4 — Settings navigation and accordion

- Вкладка `⚙` больше не выглядит длинной простынёй: добавлена layout-обёртка `settingsShell` с навигацией и контентом.
- Меню настроек стало полноценным: Профиль, Время, Смены, Сценарии, Уведомления, Важные даты, Диагностика.
- На десктопе меню закреплено сбоку, на узких экранах превращается в горизонтальную прокручиваемую панель.
- Все большие настройки стали аккордеоном: карточки можно открывать и сворачивать, а при выборе пункта меню открывается только нужная секция.
- Добавлены кнопки `развернуть всё` и `свернуть всё`.
- Последний открытый раздел сохраняется в `localStorage`, чтобы настройки продолжались с того места, где пользователь остановился.
- Frontend-кэш поднят до `v20.4`: `app.css?v=20.4`, `app.js?v=20.4`, `dutylog-shell-v20.4`.

# v20.3 — Diagnostics and settings polish

- Добавлено внутреннее оглавление настроек, чтобы вкладка `⚙` не превращалась в длинную простыню.
- Секция `Время и регион` упрощена: главный сценарий теперь `Сохранить и применить к сменам`, а изменения дефолтов дневной/ночной смены автосохраняются и автоматически применяются к встроенным сменам после короткой паузы.
- Добавлена карточка `Диагностика`: frontend/backend version, Service Worker, браузер, CSRF-cookie, серверное время, активные профили Spring, состояние БД и Telegram.
- Добавлен endpoint `GET /api/system/status`; он требует web-сессию и не отдаёт секреты.
- Добавлен `RequestDiagnosticsFilter`: request-id, метод, путь, статус и длительность запросов пишутся в логи для `/api/**`, `/actuator/**`, login/logout.
- Добавлена настройка логирования `DUTYLOG_REQUEST_LOG_LEVEL`.
- Добавлен `docs/GIT_WORKFLOW.md`: как жить с Git, тегами, откатами и как не удалить данные PostgreSQL.
- Frontend-кэш поднят до `v20.3`: `app.css?v=20.3`, `app.js?v=20.3`, `dutylog-shell-v20.3`.

# v20.2.1 — Header avatar spacing fix

- Исправлен версточный баг шапки: аватар профиля больше не прижимается к стрелке месяца на узких экранах.
- Для шапки добавлены нормальные gap-отступы, а блок пользователя на мобильном уходит вправо через `margin-left:auto`.
- Имя пользователя в шапке на маленьких экранах теперь аккуратно обрезается, а не ломает ряд.
- Frontend-кэш поднят до `v20.2.1`: `app.css?v=20.2.1`, `app.js?v=20.2.1`, `dutylog-shell-v20.2.1`.

# v20.2 — Telegram quick actions

- Telegram-бот получил первые изменяющие команды поверх уже существующих сервисов DutyLog.
- Добавление задач: `/task текст`, `/task завтра текст`, `/task 2026-07-10 текст`, `/задача ...`.
- Закрытие задач: `/done 12`, `/готово 12`, `/закрыть 12`; `/tasks` теперь показывает id задач.
- Начисление переработки: `/ppr 17-08 причина`, `/ppr 10.07 17-20 причина`, `/ppr 2 причина`.
- Для интервальной переработки поддержаны токены `обед60` и `план8/план0`; расчёт идёт через существующий `OvertimeService`, включая разбиение ночей и защиту от пересечений.
- Списание отгула: `/timeoff 8 причина`, `/отгул завтра 8 причина`, `/списать 2026-07-10 4 причина`; FIFO остаётся на стороне `OvertimeService`.
- Ошибки доменной валидации теперь возвращаются пользователю в Telegram-сообщении, а не только пишутся в лог.
- Frontend-кэш поднят до `v20.2`: `app.css?v=20.2`, `app.js?v=20.2`, `dutylog-shell-v20.2`.

# v20.1 — Telegram notifications

- Telegram теперь умеет не только отвечать на команды, но и сам отправлять напоминания.
- Добавлена миграция `V13__telegram_notifications.sql`: флаг `telegram_links.notifications_enabled` и таблица `telegram_notification_deliveries` для защиты от повторной отправки одного и того же напоминания.
- Scheduler берёт уже рассчитанные backend-напоминания из `NotificationService`: смены, задачи, важные дни и вечерний дайджест.
- Отправка в Telegram использует те же настройки, что и веб-уведомления: включение смен, задач, важных дней, дайджеста и времена напоминаний.
- В блок Telegram во вкладке `⚙` добавлен переключатель `присылать напоминания в Telegram`.
- Добавлен endpoint `PATCH /api/telegram/settings` для web-safe настройки Telegram-привязки.
- Добавлены env-настройки `DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED`, `DUTYLOG_TELEGRAM_NOTIFICATION_SCAN_DELAY_MS`, `DUTYLOG_TELEGRAM_NOTIFICATION_LOOKBACK_MINUTES`, `DUTYLOG_TELEGRAM_NOTIFICATION_LOOKAHEAD_MINUTES`.
- `TelegramBotService.sendMessage` теперь возвращает статус отправки, чтобы не отмечать неотправленное напоминание как доставленное.
- Frontend-кэш поднят до `v20.1`: `app.css?v=20.1`, `app.js?v=20.1`, `dutylog-shell-v20.1`.

# v20.0 — Telegram foundation

- Telegram-бот поселён внутри текущего Spring Boot backend, не отдельным сервисом.
- Добавлены сущности и таблицы `telegram_links` и `telegram_link_codes` + миграция `V12__telegram_foundation.sql`.
- Во вкладке `⚙` в профиле появился блок Telegram: статус, создание кода привязки, отключение Telegram и список первых команд.
- Привязка работает через одноразовый код: создать в DutyLog → отправить боту `/start DL-123456`.
- Добавлены web endpoint’ы `/api/telegram/status`, `/api/telegram/link-code`, `/api/telegram/link`. Они защищены web-сессией и CSRF.
- Добавлен long polling для Telegram Bot API, выключен по умолчанию и включается env-переменными.
- Первые команды: `/today`, `/tomorrow`, `/week`, `/tasks`, `/balance`, `/help`, плюс русские алиасы `/сегодня`, `/завтра`, `/неделя`, `/задачи`, `/баланс`.
- Бот пока read-only: показывает смены, задачи, важные дни и баланс переработок, но ещё не создаёт задачи/переработки.
- Добавлены env-настройки `DUTYLOG_TELEGRAM_*` в `.env.example`, `docker-compose.yml`, `application.properties` и `application-prod.properties`.
- Frontend-кэш поднят до `v20.0`: `app.css?v=20.0`, `app.js?v=20.0`, `dutylog-shell-v20.0`.

# v19.10.2-branding

- Приложение переименовано в **DutyLog: Time & Overtime**.
- Обновлены: `index.html`, `login.html`, `manifest.json`, тестовое уведомление, README, CHANGES и API-документация.
- `app.css` и `app.js` подключаются как `?v=19.10.2`.
- Service worker получил новый cache name: `dutylog-shell-v19.10.2`.
- Maven `artifactId` переименован в `dutylog`, display-name проекта — `DutyLog: Time & Overtime`.
- Технические имена БД/пакетов сохранены: `shift_calendar`, `ru.daniil.shifts`, endpoint'ы `/api/shift-types` и т.п. не переименовывались, чтобы не ломать миграции, API и существующие данные.

## v19.10.1 — cleanup после слияния

- Корневая папка архива в v19.10.1 была приведена к `v19.10.1/shift-calendar`.
- Свежие изменения `v19.5–v19.10` перенесены в `CHANGES.md`, чтобы журнал релизов жил рядом с проектом.
- Убрана устаревшая документация о том, что CSRF отключён: web-cookie интерфейс теперь работает с `XSRF-TOKEN`/`X-XSRF-TOKEN`, а `/api/mobile/**` остаётся stateless под Bearer.
- `index.html` подключает `app.css` и `app.js` с версией `?v=19.10.1`.
- Service worker обновлён до `dutylog-shell-v19.10.2`; JS/CSS теперь network-first, чтобы свежий HTML не получал старый кэшированный frontend.
- Web UI профиля больше не вызывает `/api/mobile/auth/sessions`; добавлены CSRF-защищённые web endpoint’ы `/api/profile/sessions`.

## v19.10 — профиль

- Добавлен профиль во вкладке `⚙`: отображаемое имя, день рождения, аватар-инициалы в шапке.
- В день рождения пользователя календарь показывает поздравительный баннер.
- Добавлена смена пароля с проверкой текущего пароля.
- После смены пароля все мобильные сессии отзываются.
- Добавлен список мобильных устройств/сессий с возможностью отзыва.
- Добавлена миграция `V11__user_profile.sql`.

## v19.9.1 — освобождение из заметочного плена

- Исправлены случаи, когда скрытые fullscreen/overlay-блоки оставались видимыми из-за конфликта `hidden` и `display:flex`.
- Добавлен глобальный предохранитель для похожих скрытых панелей.

## v19.9 — полноэкранные Markdown-заметки

- Добавлен fullscreen-режим заметки в стиле Obsidian: редактор + живое превью.
- Работает `Esc` для выхода.
- `Tab` вставляет отступ в редакторе.
- Сохранение заметки идёт через существующий пайплайн автосохранения дня.

## v19.8 — polish нативных контролов

- Добавлен `color-scheme: dark` на корне, чтобы нативные поля/селекты не становились белыми в тёмной теме.
- Доработаны стили пресетов уведомлений, кнопок важных дней и чекбоксов.

## v19.7 — фиксы секции времени и региона

- Дописаны стили секции `Время и регион`.
- Исправлен `var(--txt)` и связанные визуальные проблемы полей.

## v19.6 — регистрация и service worker

- Исправлен сценарий, когда service worker отдавал старый `login.html` из кэша и регистрация ловила 401/проблемы с cookie.
- HTML переведён на network-first.
- В `login.html` добавлен `ensureCsrf()` как страховка перед отправкой формы/регистрацией.

## v19.5 — безопасность и тесты

- Включена CSRF-защита web-интерфейса через cookie `XSRF-TOKEN` и заголовок `X-XSRF-TOKEN`.
- `/api/mobile/**` оставлен stateless для Bearer API и исключён из CSRF.
- `docker-compose.yml` стал fail-hard по паролям: без `.env` приложение не должно тихо стартовать с дефолтными секретами.
- Добавлены 7 тестов `OvertimeService`, покрывающих ночи, сутки, пересечения, FIFO, списание больше доступного, редактирование и удаление списаний.

## v19.4 — время и регион

- Добавлена секция `Время и регион` во вкладке `⚙`.
- Можно указать рабочий регион/объект, рабочий часовой пояс и пометку сдвига от Москвы.
- Добавлен предпросмотр текущего времени в рабочем часовом поясе и в часовом поясе браузера.
- Добавлены дефолты дневной и ночной смены: начало, конец, обед и плановые часы.
- Дефолты можно сохранить локально в браузере.
- Дефолты можно применить к встроенным сменам `Дневная` и `Ночная`, чтобы уведомления, быстрые сценарии и кнопка `план по смене` брали актуальное время.
- Добавлены кнопки переноса дефолтов дневной/ночной смены в форму создания кастомной смены.
- Backend не менялся; это фронтовая настройка и удобный способ обновить существующие типы смен через уже имеющийся API.


## v19.3 — мобильная полировка

- Добавлена нижняя мобильная навигация для вкладок.
- Панель выбранного дня на телефоне превращена в нижнюю шторку с затемнением фона.
- Увеличены кликабельные зоны кнопок, чипов, вкладок и полей под палец.
- Уменьшены отступы и размеры карточек календаря на маленьких экранах.
- Улучшена адаптивность фильтров, форм переработок, задач, уведомлений и настроек.
- При переходе с календаря на другие вкладки панель дня закрывается, чтобы не висела поверх интерфейса.
- Backend не менялся.

# v19.1 — полировка настроек

- Backend не тронут; изменения только во фронте.
- Панель выбранного дня разгружена: создание и настройка смен переехали во вкладку `⚙`.
- Плюсик в списке смен теперь открывает настройки смен, а не раскрывает форму прямо в панели дня.
- Редактор пользовательских быстрых сценариев переехал во вкладку `⚙`; в панели дня остались только карточки применения сценариев.
- Создание важных дней переехало во вкладку `⚙`; в панели дня остался список важных событий выбранной даты.
- Для важных дней добавлено отдельное поле даты и быстрые кнопки `выбранный день` / `сегодня`.
- Во вкладке `⚙` появился список всех важных дней как настроек, с удалением.
- Добавлены стили для секций настроек, чтобы `⚙` не выглядела как свалка.

# v17.2 — кастомные быстрые сценарии и фиксы UI

- Дефолтная дневная смена изменена на `08:30–17:00`, обед `30 мин`, план `8 ч`.
- Для старых пользователей мягко обновляется только старый дефолт `06:30–17:00`; если время смены уже меняли вручную, оно не перетирается.
- Карточки быстрых сценариев больше не уезжают за правую панель: сетка стала адаптивной.
- Быстрые сценарии теперь хранятся как пользовательские настройки, а не только жёстко прошитые кнопки.
- Добавлен API `/api/quick-scenarios`: список, создание, редактирование и удаление сценариев.
- В интерфейсе появился блок `свои сценарии`: можно задать старт от начала/конца смены, конец через N минут/в фиксированное время/в конец смены, обед, вычет плана и причину по умолчанию.
- Стандартные сценарии сидятся один раз для пользователя; если удалить сценарий, он не будет насильно появляться снова.
- Добавлена миграция `V9__quick_scenarios_and_day_shift_time.sql`.

# v15.1 — быстрые сценарии переработки

- Добавлены быстрые кнопки в блоке переработки: `+2ч после смены`, `+4ч после смены`, `остался в ночь`, `с начала смены + ночь`, `обычная смена целиком`.
- Сценарии берут дату выбранного дня и параметры выбранной смены: начало, конец, обед и плановые часы.
- `+2ч/+4ч после смены` ставят переработку сразу после окончания смены без вычета плана.
- `остался в ночь` ставит интервал от конца выбранной смены до 08:00 следующего дня для дневных смен, либо безопасную заготовку на 2 часа после окончания для остальных.
- `с начала смены + ночь` заполняет полный фактический интервал от начала смены до 08:00 следующего дня и вычитает плановые часы смены.
- `обычная смена целиком` подставляет время выбранной смены, обед и план — удобно для проверки настроек смены или ручной корректировки.
- Все сценарии являются только заготовками: после нажатия можно поправить время, обед, план и причину перед начислением.

# v15.0 — смены со временем

- У типа смены появились поля `startTime`, `endTime`, `breakMinutes`, `plannedHours`.
- Встроенные смены получили стартовые настройки: дневная `06:30–17:00`, обед `30 мин`, план `8 ч`; ночная `20:00–08:00`, обед `60 мин`, план `11 ч`; выходной `0 ч`.
- Кастомную смену теперь можно создавать сразу со временем начала/конца, обедом и плановыми часами.
- В списке смен появилась кнопка `настроить`: можно поправить время, обед и план; у пользовательских смен также название и цвет.
- Кнопка `план по смене` в переработке теперь берёт `plannedHours`, а не старое поле `hours`.
- Добавлена кнопка `время по смене`, которая заполняет начало/конец/обед/план по выбранной смене.
- Кнопка `по смене` в списании отгула также использует плановые часы смены.
- Добавлен endpoint `PATCH /api/shift-types/{id}`.
- Добавлена миграция `V6__shift_type_time_model.sql`.

# v14.7 — экспорт журнала переработок

- Добавлен экспорт текущей таблицы переработок в CSV.
- Добавлен Excel-совместимый экспорт `.xls` без тяжёлых зависимостей.
- Экспорт учитывает фильтры таблицы: период, статус и поиск.
- В выгрузку попадают: день переработки, время, начислено, причина, использовано, куда списано, остаток, обед, вычтенный план и признак авторасчёта.
- CSV отдаётся с UTF-8 BOM, чтобы кириллица нормально открывалась в Excel.
- Добавлены endpoint’ы `/api/overtime/export.csv` и `/api/overtime/export.xls`.

# Изменения

## v14.6.1-manual-time-quality

Маленькая правка удобства ручного начисления переработки.

- Поле `Дата ручн.` переименовано в понятное `Дата начисления`.
- Добавлены кнопки `выбранный день` и `сегодня` для даты начисления.
- Короткий ввод времени теперь умеет считать часы сам: `17:00–20:00`, `17–20`, `17–08`.
- Если короткий интервал пересекает полночь, конец автоматически считается следующим днём.
- К короткому вводу применяются `обед, мин` и `вычесть план, ч`.
- После расчёта backend получает нормальные `startDateTime/endDateTime`, поэтому сохраняются защита от дублей и разбивка интервала по датам.

## v14.6-ledger-editing

Добавлено безопасное редактирование начислений переработки и списаний отгула.

- В таблице переработок появилась кнопка `ред.` для начислений.
- Можно изменить дату ручной записи, текст времени, часы и причину.
- Для рассчитанных начислений можно изменить начало, конец, обед и вычтенный план — backend пересчитает часы сам.
- Если редактирование интервала превращает одну строку в несколько дат, сервер заменит строку на несколько начислений, но только если она ещё не использована списаниями.
- Если начисление уже частично списано, его нельзя уменьшить ниже уже использованных часов.
- При редактировании рассчитанных интервалов сохраняется защита от дублей и пересечений.
- В списаниях появилась кнопка `ред.`. Можно изменить дату, часы и причину списания.
- Если изменить часы списания, FIFO-распределение пересобирается заново.
- Если доступных часов не хватает, изменение списания отклоняется и старое распределение остаётся.
- Добавлены API endpoint'ы `PATCH /api/overtime/credits/{id}` и `PATCH /api/overtime/usages/{id}`.

## v14.5-ledger-polish

Полировка таблицы переработок без изменения основной бухгалтерской логики.

- Добавлены фильтры журнала переработок: период `с/по`, быстрые кнопки `этот месяц` и `всё время`.
- Добавлен фильтр по состоянию начислений: все, с остатком, частично списанные, полностью списанные.
- Добавлен текстовый поиск по дате, времени, причине начисления и причинам списаний.
- Добавлена строка итогов по отфильтрованной таблице: сколько записей показано, начислено, использовано, остаток, сколько записей открыто/закрыто.
- В строках журнала появились бейджи статуса: `остаток`, `частично`, `списано`.
- Текущий выбранный в календаре день подсвечивается в таблице переработок.
- Удаление начислений и списаний теперь просит подтверждение.
- Для начислений, которые уже используются списаниями, в таблице показывается подсказка `сначала списания`, чтобы не было ощущения, что кнопка удаления просто пропала.

## v14.4-overtime-split-and-duplicates

Исправлены две проблемы расчётной переработки по интервалу времени.

- Интервал через полночь больше не падает одной суммой на дату начала.
- Сервер раскладывает рассчитанное начисление на несколько строк журнала.
- Обычные ночные интервалы режутся по датам: например `17:00 → 08:00` станет отдельными кусками до полуночи и после полуночи.
- Ровные сутки вида `08:00 → 08:00` следующего дня режутся пополам, чтобы в календаре было две понятные половины по двум датам.
- Обед и вычтенные плановые часы снимаются с самых ранних минут интервала.
- Сервер запрещает пересечения рассчитанных интервалов. Нельзя второй раз начислить тот же период `03.07 20:00 → 04.07 08:00` или частично пересекающийся кусок.
- Ручные начисления без `startDateTime/endDateTime` работают как раньше.
- Новая миграция БД не нужна: используются уже добавленные поля `start_at` и `end_at`.

## v14.3-overtime-date-fix

Исправлен баг начисления переработки при вводе интервала с датой, отличной от выбранного дня календаря.

- Если переработка создаётся через поля `начало` и `конец`, дата начисления теперь берётся из `startDateTime`.
- Пример: открыт день `2026-07-09`, но начало указано `2026-07-03T08:30` — запись будет начислена на `2026-07-03`, а не на `2026-07-09`.
- Backend тоже применяет это правило, поэтому Android/API-клиент не сможет случайно положить рассчитанную переработку на неправильный день.
- Для ручных начислений без интервала дата по-прежнему берётся из выбранного дня / поля `date`.

## v14.2-overtime-time-calc

Поверх версии с аккордеоном добавлен автоподсчёт часов переработки по интервалу работы.

### Переработка по времени

- В начислении переработки теперь можно указать `начало` и `конец` через `datetime-local`.
- Добавлены поля `обед, мин` и `вычесть план, ч`.
- Формула: `переработка = конец - начало - обед - плановые часы`.
- Если заносишь только кусок переработки, плановые часы оставляешь `0`.
- Если заносишь всю фактическую смену целиком, можно вычесть план по кнопке `план по смене`.
- Добавлена быстрая кнопка `17–08` для сценария “остался в ночь”.
- Ручной ввод часов сохранён: можно по-прежнему просто вписать количество часов без начала/конца.
- В таблице переработок отображается, что запись рассчитана из интервала, с учётом обеда и вычтенных плановых часов.

### API и БД

- `POST /api/overtime/credits` теперь принимает `startDateTime`, `endDateTime`, `breakMinutes`, `plannedHours`.
- Backend пересчитывает часы сам, поэтому фронт не является единственным источником расчёта.
- Добавлена миграция `V5__overtime_time_calculation.sql`.

## v13-overtime-accounting

Переработка вынесена в полноценную бухгалтерию часов.

### Журнал начислений и списаний

- Добавлены сущности `OvertimeCredit`, `OvertimeUsage`, `OvertimeAllocation`.
- Начисление переработки хранит дату, диапазон времени, часы и причину.
- Списание отгула хранит дату, часы и причину.
- Списание автоматически распределяется по начислениям по FIFO: сначала самые старые остатки.
- Переработка больше не “сгорает” при переходе на следующий месяц.
- Можно начислить переработку в мае и списать её в августе.
- Добавлена таблица переработок в веб-интерфейсе: день, время, начислено, причина, использовано, куда списано, остаток.

### API и БД

- Добавлен `GET /api/overtime/account`.
- Добавлен `POST /api/overtime/credits`.
- Добавлен `DELETE /api/overtime/credits/{id}`.
- Добавлен `POST /api/overtime/usages`.
- Добавлен `DELETE /api/overtime/usages/{id}`.
- `GET /api/calendar?from=&to=` теперь дополнительно отдаёт `overtimeAccount` с общим остатком переработки.
- Добавлена миграция `V4__overtime_accounting.sql`.

## v12-android-ready

Слой подготовки под полноценное Android-приложение.

### Мобильная авторизация

- Добавлена сущность `MobileAuthToken`.
- Добавлен `MobileAuthService`.
- Добавлен `BearerTokenAuthenticationFilter`.
- Android может ходить в API через `Authorization: Bearer <accessToken>`.
- Веб-сессия `JSESSIONID` сохранена и не сломана.
- Access token живёт коротко, refresh token — дольше.
- Refresh token ротируется при обновлении.
- В базе хранятся SHA-256 хэши токенов, а не сами токены.
- Добавлено управление мобильными сессиями/устройствами.

### Mobile API

- Добавлен `POST /api/mobile/auth/login`.
- Добавлен `POST /api/mobile/auth/refresh`.
- Добавлен `POST /api/mobile/auth/logout`.
- Добавлен `GET /api/mobile/auth/me`.
- Добавлен `GET /api/mobile/auth/sessions`.
- Добавлен `DELETE /api/mobile/auth/sessions/{id}`.
- Добавлен `GET /api/mobile/bootstrap?from=&to=`.
- Добавлен `POST /api/mobile/sync` для пакетной синхронизации изменений дней.

### БД и документация

- Добавлена миграция `V3__mobile_auth_tokens.sql`.
- Обновлены `README.md`, `docs/API.md`, `docs/ANDROID_API_PLAN.md`.

## v11-important-days-tasks

Новый продуктовый слой поверх календаря смен.

### Задачи

- Добавлены отдельные задачи дня с чекбоксами.
- Добавлены endpoint’ы `GET/POST/PATCH/DELETE /api/tasks`.
- На клетке календаря появляется `!`, если в дне есть невыполненные задачи.
- Когда все задачи выполнены, красный индикатор гаснет и превращается в спокойную отметку `✓`.
- Задачи не смешиваются с Markdown-заметкой и готовы для Android/Telegram.

### Важные дни

- Добавлены важные дни: дни рождения, годовщины, платежи, техосмотры и любые пользовательские события.
- Поддерживаются повторы: `NONE`, `MONTHLY`, `YEARLY`.
- Добавлены endpoint’ы `GET/POST/PATCH/DELETE /api/important-days`.
- Добавлен endpoint `GET /api/important-days/occurrences?from=&to=` для развёрнутых повторений в диапазоне.
- На календаре важные дни помечаются `★`.
- 29 февраля в невисокосный год показывается 28 февраля, чтобы ежегодное событие не пропадало.
- Ежемесячное событие на 31 число в коротких месяцах показывается в последний день месяца.

### API и БД

- `GET /api/calendar?from=&to=` теперь отдаёт `tasks` и `importantDays`.
- Добавлены сущности `DayTask`, `ImportantDay`, `RepeatMode`.
- Добавлены репозитории и сервисы `TaskService`, `ImportantDayService`.
- Добавлена миграция `V2__important_days_and_tasks.sql`.


## v10-api-architecture

Следующий шаг к полноценному продукту и Android-клиенту.

### API

- Добавлен Android-friendly endpoint `GET /api/calendar?from=&to=`.
- Ответ `/api/calendar` включает типы смен, дни диапазона и сводку переработки.
- Добавлен endpoint `GET /api/overtime/balance?from=&to=`.
- Добавлен endpoint `GET /api/overtime/ledger?from=&to=`.
- Старые endpoint’ы веб-версии `/api/days`, `/api/days/{date}`, `/api/days/fill` сохранены.
- Ограничен диапазон запросов календаря/переработок: максимум 366 дней.

### Архитектура

- Добавлен сервисный слой: `CurrentUserService`, `ShiftTypeService`, `DayEntryService`, `CalendarService`, `OvertimeService`.
- Контроллеры стали тоньше и больше не держат основную бизнес-логику.
- Добавлено доменное исключение `ApiException`.
- `ApiExceptionHandler` теперь обрабатывает сервисные ошибки единым JSON-форматом.
- В `DayEntryRepository` добавлен метод сортированной загрузки диапазона по дате.

### Документация

- Добавлен `docs/API.md` с описанием основных endpoint’ов.
- Обновлён `docs/ANDROID_API_PLAN.md` под новую архитектуру.
- Обновлён README.

## v9-production-foundation

Первый шаг от MVP к нормальному продукту и серверному запуску.

### Инфраструктура

- Добавлен PostgreSQL-драйвер.
- Добавлен Flyway.
- Добавлена production-миграция `src/main/resources/db/migration/postgresql/V1__init.sql`.
- Добавлен production-профиль `application-prod.properties`.
- В production включён Flyway и `spring.jpa.hibernate.ddl-auto=validate`.
- В dev-режиме H2 оставлена для быстрого запуска в IntelliJ.
- В dev-режиме Flyway отключён, Hibernate по-прежнему может обновлять H2-схему.
- Добавлен Dockerfile.
- Добавлен `docker-compose.yml` с PostgreSQL и приложением.
- Добавлен `.env.example`.
- Добавлен пример nginx-конфига: `deploy/nginx/shift-calendar.conf.example`.
- Добавлен скрипт бэкапа PostgreSQL: `deploy/scripts/backup-postgres.sh`.
- Добавлен Spring Boot Actuator health endpoint `/actuator/health`.

### Код

- Поле `note` в `DayEntry` теперь явно мапится как `text`, чтобы нормально работать с PostgreSQL.
- Поля `overtime_hours` и `time_off_hours` помечены как `nullable=false`.
- `/actuator/health` разрешён без авторизации.

### Документация

- README переписан под dev/prod запуск.
- Добавлен `docs/ROADMAP.md`.
- Добавлен `docs/ANDROID_API_PLAN.md`.

## v8-overtime

- Добавлены поля переработки и списания отгула в день.
- Добавлен месячный баланс переработки.
- Добавлены отметки `+7ч`, `-8ч` и т.п. в календаре.
- Массовое заполнение графика не стирает переработки и отгулы.

## v7-monthfill

- Исправлено заполнение графика через границу месяца.
- По умолчанию график заполняется на 31 день вперёд.
- Пятидневка привязана к реальным дням недели.

## v6-schedules

- Добавлена встроенная смена «Выходной».
- Добавлено массовое заполнение графика.
- Добавлены шаблоны 2/2, день/ночь/48, пятидневка 5/2, день/72, ночь/72.

## v5-customonly

- Стартовыми оставлены только базовые смены.
- Остальные типы смен пользователь создаёт сам.

## v4-dorabotano

- Исправлено автосохранение заметок.
- Добавлена валидация.
- Добавлен `ApiExceptionHandler`.
- Добавлен базовый PWA-слой.

## v15.2 — улучшение UI быстрых сценариев

- Блок быстрых сценариев переработки переделан из ряда кнопок в понятные карточки.
- Добавлен контекст выбранного дня и смены: дата, смена, время, плановые часы, обед.
- Карточки сценариев автоматически блокируются, если у выбранной смены не настроено нужное время.
- Добавлена активная подсветка последнего выбранного сценария.
- Добавлена кнопка «очистить поля» для сброса формы переработки.
- Добавлены короткие описания сценариев прямо в интерфейсе:
  - +2 часа после смены;
  - +4 часа после смены;
  - остался в ночь;
  - смена + ночь;
  - обычная смена.
- Backend-логика не менялась: FIFO, защита от дублей, разбивка интервалов по датам и экспорт остались как в предыдущих версиях.

## v17.0 — уведомления и напоминания

- Добавлена сущность `NotificationSettings` с пользовательскими настройками напоминаний.
- Добавлена миграция `V7__notification_settings.sql`.
- Добавлен backend-сервис расчёта напоминаний для Web/PWA, Android и будущего Telegram-бота.
- Добавлены напоминания:
  - перед сменой;
  - о невыполненных задачах;
  - о важных днях;
  - вечерний дайджест на завтра.
- Добавлен API:
  - `GET /api/notifications/settings`;
  - `PATCH /api/notifications/settings`;
  - `GET /api/notifications/upcoming?from=&to=`.
- `GET /api/calendar?from=&to=` теперь отдаёт `notificationSettings` и `reminders`.
- В веб-интерфейс добавлен блок «Уведомления»: настройки, запрос разрешения браузера, тест уведомления и список ближайших напоминаний.
- На днях календаря появляется метка 🔔, если на дату есть рассчитанные напоминания.

## v17.1 — Полировка уведомлений

- Добавлены настройки уведомлений на уровне конкретной смены:
  - можно отключить напоминания для отдельной смены;
  - можно задать своё `notificationMinutesBefore`, отличное от глобального значения.
- Напоминания перед сменой теперь учитывают настройки конкретного типа смены.
- Добавлен endpoint `GET /api/notifications/tomorrow` для быстрой проверки напоминаний на завтра.
- Endpoint `GET /api/notifications/upcoming` получил параметр `includePast`.
- В веб-интерфейсе добавлены быстрые пресеты времени напоминания: 15, 30, 60, 90 и 120 минут.
- В блок уведомлений добавлены кнопки:
  - `Текущий месяц`;
  - `Проверить завтра`.
- В списке смен теперь видно, отключены ли уведомления или задано своё время напоминания.

## v18.0 — Задачи 2.0: категории, сроки, уведомления

- Расширена модель задач: категория, приоритет, срок выполнения, время срока, индивидуальное напоминание.
- В календаре просроченные задачи теперь отмечаются отдельным индикатором `!!`.
- В панели дня добавлены фильтры задач: все / открытые / просроченные / выполненные, а также фильтр по категории.
- В списке задач появились бейджи: категория, приоритет, срок, напоминание, просрочка.
- Уведомления теперь учитывают индивидуальные сроки задач: если у задачи задан срок и напоминание, reminder рассчитывается от due date/time.
- Добавлена миграция `V10__task_metadata_and_due_dates.sql`.

## v18.1 — Общий экран задач

- Добавлен общий блок «Все задачи» под календарём.
- Задачи теперь можно смотреть одним списком, не открывая каждый день отдельно.
- Добавлены фильтры общего списка:
  - открытые;
  - просроченные;
  - открытые не просроченные;
  - выполненные;
  - все задачи;
  - категория;
  - приоритет;
  - период;
  - поиск по тексту, категории и датам.
- В общем списке задач можно:
  - поставить/снять галочку;
  - открыть день задачи в календаре;
  - редактировать задачу;
  - удалить задачу.
- Добавлен API `GET /api/tasks/board` для общего экрана задач и будущего Android-экрана задач.

## v19.2 — важные дни обратно в панель дня

- Важные дни возвращены в панель выбранного дня: теперь можно ткнуть дату и сразу добавить день рождения/платёж/событие.
- Из вкладки ⚙ убрана секция важных дней, чтобы настройки не выглядели странно и не дублировали дневной контекст.
- Исправлено отображение скрытых вкладок: настройки больше не появляются под календарём из-за CSS `display:flex`.
