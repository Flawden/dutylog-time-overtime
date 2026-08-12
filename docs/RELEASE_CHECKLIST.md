# Release checklist

Status: v27.40.23.


## v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix acceptance

- [x] Base tree is exact v27.40.22.
- [x] Exact v27.40.22 frontend gate passed and Maven stopped before Playwright.
- [x] Reproduced source-only web contracts: 285 pass / 1 stale Today fallback assertion before alignment.
- [x] Today shell contract now requires pre-Vue `admin -> settings` recovery and preserves unknown-route `-> today` fallback.
- [x] No Admin runtime/API/security/offline ownership rollback; OpenAPI remains 124/130 and Flyway V47.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Maven 772/772 on Java 17.
- [ ] Playwright canary green.
- [ ] Full Chromium 48/48, 0 failed, 0 flaky.
- [ ] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [ ] Staging deploy green.

## v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement acceptance — predecessor cut (Maven red)

- [x] Base tree is the proven-green v27.40.21 release.
- [x] Admin Users/Roles, Registration and Diagnostics are Vue-owned and use generated canonical `/api/v1/admin/**` operations.
- [x] `/api/admin/**` and `/api/v1/admin/**` remain backend ADMIN-protected aliases.
- [x] Legacy Admin HTML/state/data/render/event ownership is absent from the live runtime.
- [x] No post-Vue legacy route side-effect adapter/event remains.
- [x] Limited pre-Vue recovery and the single `dataLayer` offline owner remain unchanged.
- [x] OpenAPI is 124/130, Flyway V47.
- [x] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Maven 772/772 on Java 17 — stale Today pre-Vue fallback source contract blocked verify.
- [ ] Playwright canary green.
- [ ] Full Chromium 48/48, 0 failed, 0 flaky.
- [ ] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [ ] Staging deploy green.

## v27.40.21 — Vue Payroll Workspace Retirement acceptance — accepted predecessor

- [x] Base tree is the proven-green v27.40.20 release.
- [x] Payroll route is Vue-owned and uses generated OpenAPI operations.
- [x] Legacy Payroll HTML/script/state/data helpers are absent from the live runtime.
- [x] Post-Vue legacy route effects are Admin-only.
- [x] Existing Payroll Foundation E2E remains the parity journey and waits on the public Vue Payroll domain.
- [x] OpenAPI 118/120, Flyway V47 and single `dataLayer` offline ownership remain unchanged.
- [x] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [x] Maven 769/769 on Java 17.
- [x] Playwright canary green.
- [x] Full Chromium 48/48, 0 failed, 0 flaky.
- [x] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [x] Staging deploy green.

## v27.40.20 — E2E Release Version Contract Alignment Hotfix acceptance

- [x] Exact v27.40.19 tree classified: frontend gate green; Maven 767 total / 2 deterministic source-contract failures; Playwright not reached.
- [x] PWA and Calendar Sync Java contracts now require the shared `e2e/release-version.js` authority instead of current-release literals.
- [x] No route/offline runtime ownership change beyond release metadata bump.
- [x] Exact frontend gate green.
- [x] Maven 767/767 green.
- [x] Playwright canary green and full 48/48 with zero flaky retries.
- [x] Immutable image + PostgreSQL V47 smoke + staging deploy green.


## v27.40.19 — E2E Release Version Authority Hotfix acceptance

- [x] Base tree is exact v27.40.18 release.
- [x] v27.40.18 browser report classified as 48 total / 46 passed / 0 flaky / 2 deterministic final failures from stale v27.40.16 E2E literals.
- [x] Browser current-release expectations derive from root `package.json` through `e2e/release-version.js`.
- [x] Vue diagnostics, Calendar ICS and PWA current-release assertions use the canonical helper.
- [x] Intentional historical `27.38.15-synthetic-previous` PWA fixture remains literal.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Maven 767/767 on Java 17.
- [ ] Playwright canary green.
- [ ] Full Chromium 48/48, 0 failed, 0 flaky.
- [ ] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [ ] Staging deploy green.

## v27.40.18 — Calendar ICS Release Version Contract Hotfix acceptance

- [x] Base tree is exact v27.40.17 route-commit/hash-listener release.
- [x] Preserve the v27.40.17 route runtime unchanged.
- [x] Java Calendar ICS PRODID matches the current DutyLog release version.
- [x] Project-version-derived source contract prevents future ICS PRODID release drift.
- [x] No route/business/API/offline ownership relaxation: OpenAPI 118/120, Flyway V47, strict TypeScript and single `dataLayer` ownership remain.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Maven 767/767 on Java 17.
- [ ] Playwright canary green.
- [ ] Full Chromium 48/48, 0 failed, 0 flaky.
- [ ] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [ ] Staging deploy green.

## v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement acceptance — predecessor cut; Maven release-version drift


- [x] Base tree is the accepted green v27.40.16 release.
- [x] Vue publishes guarded canonical route commits and suppresses duplicate identical commits.
- [x] Legacy detaches its `hashchange` listener after Vue readiness and consumes route commits only for Payroll/Admin effects.
- [x] Pre-Vue recovery keeps the full historical router and initial Payroll/Admin cutover does not double-refresh an unchanged route.
- [x] No route/business/API/offline ownership relaxation: OpenAPI 118/120, Flyway V47, strict TypeScript and single `dataLayer` ownership remain.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Maven 766/766 on Java 17.
- [ ] Playwright canary green.
- [ ] Full Chromium 48/48, 0 failed, 0 flaky.
- [ ] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [ ] Staging deploy green.

## v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix acceptance — accepted predecessor

- [x] Base tree is exact v27.40.15 `f68af0298c0ef28438178e5596aa02783fa6d892`.
- [x] v27.40.15 browser evidence classified as 43 clean / 1 retry-only flaky / 5 final failures across 48 scenarios.
- [x] Vue Overtime/Vacation route entry performs a fresh canonical read; Today performs a fresh dashboard read.
- [x] Today widget order/visibility is rendered from Vue workspace/module state.
- [x] Note create uses the returned DTO without a follow-up selected-day reload race.
- [x] Exact Node 20.18.1/npm 10.8.2 frontend gate.
- [x] Maven 764/764 on Java 17.
- [x] Playwright canary green.
- [x] Full Chromium 48/48, 0 failed, 0 flaky.
- [x] Immutable image and PostgreSQL V1–V47 migration smoke green.
- [x] Staging deploy green.

## v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix acceptance

- [x] Carry the v27.40.14 product/runtime code forward unchanged.
- [x] Record exact v27.40.14 CI evidence: exact frontend gate green; Maven 760 executed / 1 failure / 0 errors / 0 skipped.
- [x] Replace the formatting-sensitive comment assertion with structural `loadProfile()` checks.
- [x] Require profile assignment, `applyRoute()` and later `publishLegacyPlatformState()` publication in source order.
- [x] Keep route rules, retries/timeouts, strict TypeScript, OpenAPI 118/120, Flyway V47 and offline ownership unchanged.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 760/760.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image and PostgreSQL V47 staging acceptance.

## v27.40.14 — Vue Route Guard Authority Cutover acceptance — predecessor cut; Maven source-contract failure

- [x] Start from the proven-green v27.40.13 staging baseline.
- [x] Vue owns canonical hash read/write/subscription plus Admin/module route access policy after authoritative state loads.
- [x] Blocked Admin/disabled-module direct hashes canonicalize to Calendar.
- [x] Vue owns the body route marker and Calendar selected-day close-on-route-exit behavior.
- [x] Post-Vue legacy `applyRoute()` contains only Payroll/Admin side effects; full historical routing remains pre-Vue recovery.
- [x] No second router, offline queue or reconnect owner; OpenAPI remains 118/120 and Flyway V47.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 760/760 — exact CI: 759 passed, one formatting-sensitive source-contract failure, zero errors.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries — not reached.
- [ ] Immutable image and PostgreSQL V47 staging acceptance — not reached.


## v27.40.13 — Vue Route State Authority Cutover acceptance — accepted predecessor

- [x] Start from the proven-green v27.40.12 staging baseline.
- [x] Vue owns canonical hash read/write/subscription and shell route synchronization.
- [x] `DutyLogLegacySnapshot` carries no route state; `LegacyBridge` carries no navigate capability.
- [x] Migrated Vue navigation uses the typed hash route adapter and the public Vue platform exposes `navigate(...)`.
- [x] Payroll/Admin legacy route side effects and pre-Vue recovery remain compatible with the same hash transport.
- [x] No second router, offline queue or reconnect owner; OpenAPI remains 118/120 and Flyway V47.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image and PostgreSQL V47 staging acceptance.


## v27.40.12 — Legacy Command Surface Retirement acceptance — accepted predecessor

- [x] Start from the proven-green v27.40.11 staging baseline.
- [x] Remove dead generic modal/Productivity capabilities from the explicit Vue↔legacy bridge and platform declaration.
- [x] Keep the typed pre-adapter command fallback for navigation/logout only; forbid the retired `open-modal` variant.
- [x] Keep native Vue domains as the only live Task/Important/Quick Actions/Shift Type UI owners.
- [x] Keep Payroll/Admin hash routing and the single `dataLayer` offline queue/reconnect owner unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image and PostgreSQL V47 staging acceptance.


## v27.40.11 — Vue Shift Type Manager Modal Retirement acceptance — accepted predecessor

- [x] Start from the proven-green v27.40.10 baseline.
- [x] Require one live Vue `#shiftTypeModal` after Settings readiness while retaining source markup only for pre-Vue recovery.
- [x] Preserve `shiftTypeForm`, `customList`, `nsName`, time/break/plan, notification and color selectors/flows under Vue ownership.
- [x] Remove `openShiftTypeManager` from the generic legacy bridge and route Calendar `+` through `DutyLogVueDomains.settingsWorkspace`.
- [x] Use generated Shift Type create/update/delete operations and refresh the Vue Calendar read model after successful mutations.
- [x] Keep Shift Type mutations server-authoritative and leave `dataLayer` as the sole offline mutation/reconnect owner.
- [x] Keep Payroll/Admin routing and remaining historical numbered-JS entry points for later v27.40.x retirement.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image and PostgreSQL V47 staging acceptance.


## v27.40.10 — Vue Quick Actions Modal Retirement acceptance — accepted predecessor

- [x] Start from the proven-green v27.40.9 baseline.
- [x] Require one live Vue `#quickActionsModal` after Productivity readiness while retaining only pre-Vue fallback markup.
- [x] Preserve `quickActionText`, Inbox, Task, Note, Important, Overtime Credit and Absence selectors/flows under Vue ownership.
- [x] Remove `openQuickActions` from the generic legacy bridge and route Today/global fallback entry through `DutyLogVueDomains.productivity`.
- [x] Keep Inbox quick capture on the existing `dataLayer` offline adapter and preserve the single reconnect owner.
- [x] Keep Payroll/Admin routing and Shift Type Manager for later v27.40.x retirement.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image and PostgreSQL V47 staging acceptance.


## v27.40.9 — Vue Shell Navigation Model Retirement acceptance

- [x] Start from the proven-green v27.40.8 baseline.
- [x] Require shell navigation snapshots to derive workspace order/visibility from persisted appearance configuration rather than hidden `#tabbar` anchors/classes.
- [x] Require available routes to follow authoritative module state, while Today/Settings remain mandatory and primary navigation remains capped at five.
- [x] Require distinct Settings (`Настройки` / `Settings`) and overflow (`Ещё` / `More`) labels.
- [x] Keep Payroll/Admin hash routing and the existing single `dataLayer` offline owner as explicit later-retirement boundaries.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image and PostgreSQL V47 staging acceptance.

## v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix acceptance

- [x] Record the exact v27.40.7 Maven result: 758 tests / 1 failure / 0 errors / 0 skipped.
- [x] Keep the reconnect product fix unchanged and replace only the stale comment-format assertion with structural `onBeforeUnmount` checks.
- [x] Require the pending timer guard, timer cancellation/reset and guarded `updateNote` call inside the unmount block.
- [x] Keep `dataLayer.syncQueue()` as the sole reconnect queue owner; no retry, timeout, strictness, OpenAPI, Flyway or business-rule relaxation.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image and PostgreSQL V47 staging acceptance.


## v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix acceptance

- [x] Classify the complete v27.40.6 Chromium report as 43 clean / 1 retry-only flaky / 4 final failed.
- [x] Preserve the v27.40.4 native selected-day owner while restoring the legacy-equivalent shift projection and exact overtime allocation display semantics.
- [x] Keep the intentional wide desktop selected-day workspace and assert side-by-side notes geometry without overlap or horizontal overflow.
- [x] Prevent `SelectedDayNotes` from issuing an unmount save unless a debounce draft is pending, so reconnect cannot race the queued `dataLayer` PATCH with a second direct PATCH.
- [x] Keep the legacy `dataLayer` as the single offline mutation/reconnect owner and preserve strict browser failure collection.
- [ ] Run exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Run Maven 758/758.
- [ ] Run Playwright canary and full 48/48 with 0 flaky.
- [ ] Accept immutable staging image and PostgreSQL migration smoke.


## v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix acceptance

- [x] Preserve the v27.40.4 native selected-day UI owner and v27.40.5 strict-source contract alignment.
- [x] Fix only the exact TS2379 preview-key failure by guaranteeing a defined Vue `key` for generated preview items with optional `date`.
- [x] Keep `exactOptionalPropertyTypes`, OpenAPI 118/120, Flyway V47, retries/timeouts and offline ownership unchanged.
- [ ] Run exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Run Maven 758/758.
- [ ] Run Playwright canary and full 48/48 with 0 flaky.
- [ ] Accept immutable staging image and PostgreSQL migration smoke.

## v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix acceptance

- [x] Preserve the v27.40.4 Vue selected-day product code and single `dataLayer` offline writer.
- [x] Require `CalendarDaySection`, `CalendarMode` and `DutyLogCalendarTimelineDomain` in the Calendar workspace strict-source contract.
- [x] Require the explicit `openDay(date: string, section?: CalendarDaySection | null)` callback signature.
- [x] Keep strict TypeScript, OpenAPI 118/120 and Flyway V47 unchanged.
- [ ] Run exact Node 20.18.1/npm 10.8.2 frontend gate.
- [ ] Run Maven 758/758.
- [ ] Run Playwright canary and full 48/48 with 0 flaky.
- [ ] Accept immutable staging image and PostgreSQL migration smoke.

## v27.40.4 — Vue Calendar Selected-Day Panel Retirement acceptance

- [x] Remove `#calendarLegacyPanelHost` from the live Vue Calendar and render one native `#panel[data-vue-selected-day-panel]`.
- [x] Remove selected-day attach/park/open/close bridge ownership and preserve stable IDs for existing browser journeys.
- [x] Keep the legacy `dataLayer` as the sole offline day-write/reconnect owner; no second queue or online listener is introduced.
- [x] Keep cross-midnight shift `sourceDate`, schedule preview/apply, overtime, absence and Productivity selected-day semantics intact.
- [x] Bind legacy selected-day renderers to the Vue readiness barrier rather than allowing two writers for the same IDs.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate must pass with 52/52 Vitest.
- [ ] Maven Java 17 must pass 758/758.
- [ ] Canary and full Chromium must pass 48/48 with zero flaky retries.
- [ ] Immutable image, PostgreSQL V47 smoke and staging deployment must pass.


## v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix acceptance

- [x] Classify the full v27.40.2 report: 48 scenarios, 32 clean passed / 10 flaky / 6 final failed, with the recurring 500 reduced to lazy notification-settings first-read concurrency.
- [x] Guard only the missing-row notification-settings path with a pessimistic owner lock, double-check after lock acquisition and `saveAndFlush`; ordinary reads remain lock-free.
- [x] Restore curated timezone aliases such as `Europe/Kyiv` in native Vue Time Settings and keep exact timezone-save UI copy assertions.
- [x] Keep OpenAPI 118/120, Flyway V47, business semantics, retries/timeouts and strict browser failure collection unchanged.
- [x] Exact frontend gate passed with 52/52 Vitest.
- [x] Maven Java 17 passed 758/758.
- [x] Canary and full Chromium passed 48/48 with zero flaky retries.
- [x] Immutable image, PostgreSQL V47 smoke and staging deployment accepted.

## v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix acceptance

- [x] Classify the v27.40.1 canary artifact: one scenario, failed on both attempts, with one alternating 500 seed race and two deterministic 400 missing-query responses.
- [x] Serialize the Schedule Templates / Calendar Layers first-user reads instead of allowing both list transactions to seed identical presets concurrently.
- [x] Supply authoritative `sourceTimezone` to both generated migration-preview GETs and preserve it on post-migration refresh.
- [x] Keep backend rules, OpenAPI 118/120, Flyway V47, browser retries/timeouts and strict HTTP/runtime collection unchanged.
- [ ] Exact frontend gate must pass with 52/52 Vitest.
- [ ] Maven Java 17 must pass 758/758.
- [ ] Canary and full Chromium must pass 48/48 with zero flaky retries.
- [ ] Immutable image, PostgreSQL V47 smoke and staging deployment must pass.

## v27.40.1 — Vue Settings Retirement Maven Contract Alignment Hotfix acceptance

- [x] Use the first v27.40.0 staging failure at the first blocking backend gate: exact frontend typecheck/unit/build is green; Maven fails before release-check/Chromium.
- [x] Align `VueSettingsWorkspaceMigrationFrontendContractTest` with the already-current Playwright assertion `expect(page.locator('#settingsLegacyHost')).toHaveCount(0)`.
- [x] Keep the retired Settings host retired; no runtime, API/OpenAPI, Flyway, retry/timeout or browser-collector behavior changes.
- [ ] Maven Java 17 must pass 758/758.
- [ ] Canary and full Chromium must pass 48/48 with zero flaky retries.
- [ ] Immutable image, PostgreSQL V47 smoke and staging deployment must pass.

## v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover acceptance

- [x] Start from the accepted green v27.39.6 staging baseline.
- [x] Remove the Settings compatibility host/parking DOM and its bridge methods.
- [x] Make Time, Schedule/Calendar Layers and Notifications native Vue Settings surfaces using generated `/api/v1/*` operations.
- [x] Bind static ownership contracts so legacy Settings renderers cannot write once Vue is ready.
- [x] Preserve strict E2E assertions and move only transport waits that now belong to the generated owner.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate must pass with 52/52 Vitest.
- [ ] Maven Java 17 must pass 758/758.
- [ ] Canary and full Chromium must pass 48/48 with zero flaky retries.
- [ ] Immutable image build, clean PostgreSQL V47 migration/smoke and staging deployment must pass.
- [ ] v27.40.x is not closed until selected-day Calendar, router/state/modal, offline dataLayer coupling, Payroll/Admin and numbered-JS UI blockers are retired.

## v27.39.6 — Module Runtime Synchronization & Offline Reconnect Ownership Hotfix — accepted green

- [x] Classify the complete v27.39.5 browser artifact as 44/48 final with four Task-module failures plus one retry-only PWA offline flaky.
- [x] Synchronize Vue Settings module enablement from the authoritative shell snapshot after onboarding/external module changes; do not make the idempotent E2E helper force unnecessary PATCHes.
- [x] Preserve the legacy `dataLayer` as the sole offline queue/reconnect owner and publish one completion event for Vue Productivity refresh.
- [x] Keep strict business assertions, browser retries/timeouts, HTTP/runtime collection, backend rules, OpenAPI and Flyway unchanged.
- [x] Exact frontend gate.
- [x] Maven 758/758.
- [x] Canary and Chromium 48/48, zero flaky retries.
- [x] Immutable image, PostgreSQL smoke and staging deploy.


## v27.39.5 — Vue Settings State Ownership Browser Parity Hotfix acceptance

- [x] Playwright report classified as 42 pass / 6 fail with three root causes.
- [x] Module-toggle helper is idempotent when the requested state is already active.
- [x] Legacy Settings visibility bridge cannot overwrite the Vue-owned open-section persistence key.
- [x] Today widget reordering preserves hidden-widget visibility.
- [ ] Exact frontend gate.
- [ ] Maven 758/758.
- [ ] Canary and Chromium 48/48, zero flaky retries.
- [ ] Immutable image, PostgreSQL smoke and staging deploy.


## v27.39.4 — Vue Settings Browser Ownership & Preview Correlation Hotfix acceptance

- [x] Classify the complete v27.39.3 Playwright artifact as 31/48 with 17 failures, not 17 unrelated product bugs.
- [x] Bind all Settings navigation helpers to the Vue-owned `[data-vue-settings-workspace-view]` and explicit readiness attribute; never resurrect retired `#view-settings`.
- [x] Bind the partial-time-off response wait to the exact `PARTIAL` 09:00–13:00 `OVERTIME_BANK` request while preserving `durationMinutes === 240` and remaining-bank assertions.
- [x] Keep browser retries/timeouts, strict console/pageerror/HTTP collection, backend business authority, API/OpenAPI and Flyway unchanged.
- [ ] Exact frontend gate green: 52/52 Vitest plus production build/budget on Node 20.18.1/npm 10.8.2.
- [ ] Maven verify 758/758 green on Java 17.
- [ ] Canary green, Chromium 48/48 with zero flaky retries.
- [ ] Immutable image, PostgreSQL smoke and staging green.

## v27.39.3 — Frontend Diagnostics Release Version Source Hotfix acceptance

- [x] Use the exact v27.39.2 staging frontend gate as source of truth: typecheck green, Vitest 51/52 with one release-version mismatch.
- [x] Derive `__DUTYLOG_RELEASE_VERSION__` from `frontend/package.json`; no separately maintained release literal remains in Vite config.
- [x] Derive the diagnostics unit-test expectation from the same package metadata and add static guards against hard-coded Vite version drift.
- [x] Keep API/OpenAPI, backend business rules, Flyway, browser retries/timeouts and diagnostics semantics unchanged.
- [ ] Exact frontend gate green: 52/52 Vitest plus production build/budget on Node 20.18.1/npm 10.8.2.
- [ ] Maven verify 758/758 green on Java 17.
- [ ] Canary green, Chromium 48/48 with zero flaky retries.
- [ ] Immutable image, PostgreSQL smoke and staging green.


## v27.39.2 — Vue Settings Maven Contract Alignment Hotfix acceptance

- [x] Two failing local Maven source contracts aligned to current PWA and Settings ownership sources.
- [x] No runtime/API/Flyway/browser-policy changes.
- [ ] Exact frontend gate green.
- [ ] Maven verify 758/758 green.
- [ ] Canary green, Chromium 48/48 with zero flaky retries.
- [ ] Immutable image, PostgreSQL smoke and staging green.

## v27.39.1 — Vue Settings Strict Typecheck Hotfix acceptance

- [x] Classify the first v27.39.0 staging failure at the first blocking gate: lockfile/OpenAPI pass; strict `vue-tsc` fails before Maven/Chromium.
- [x] Preserve `strict`, `exactOptionalPropertyTypes` and `noUncheckedIndexedAccess`; do not weaken compiler configuration.
- [x] Remove optional-undefined DOM bindings and narrow string-indexed catalog/array reads before use.
- [x] Extend the Settings migration source contract without changing API/OpenAPI, backend rules, Flyway, retries or timeouts.
- [ ] Exact frontend gate must pass 52 Vitest + strict typecheck + production build/budget on Node 20.18.1/npm 10.8.2.
- [ ] Maven/JUnit must pass 758/758 across 153 test classes on Java 17.
- [ ] `npm run test:e2e:canary` then mandatory 48/48 Chromium must pass with zero flaky scenarios.
- [ ] Immutable image, clean PostgreSQL V47 smoke and staging deployment remain blocking acceptance gates.

## v27.39.0 — Vue Settings, Workspace & Integrations acceptance

- [x] Install one Vue Settings owner for Profile, Language, Modules, Calendar Sync and Appearance/Workspace Studio.
- [x] Park Time, Schedule and Notifications as explicit compatibility islands under `#settingsLegacyHost`; do not clone their mature mutation behavior.
- [x] Expand generated OpenAPI to 118 operations / 120 schemas and route migrated Settings writes through canonical `/api/v1/*` operations.
- [x] Preserve the accepted v27.38.15 module disable/enable authority transaction and fresh post-toggle calendar reload.
- [x] Activate Q-11/ADR-008: production source maps are disabled by default; optional diagnostic maps are hidden and integration bearer values remain out of local persistence/diagnostics.
- [x] Add model/source/browser contracts without weakening retries, timeouts, HTTP/runtime collection or backend module guards.
- [ ] Exact frontend gate must pass 52 Vitest + strict typecheck + production build/budget on Node 20.18.1/npm 10.8.2.
- [ ] Maven/JUnit must pass 757/757 across 153 test classes on Java 17.
- [ ] `npm run test:e2e:canary` then mandatory 48/48 Chromium must pass with zero flaky scenarios.
- [ ] Immutable image, clean PostgreSQL V47 smoke and staging deployment remain blocking acceptance gates.

## v27.38.15 — Module Cache Authority Browser Parity Hotfix acceptance

- [x] Use the complete v27.38.14 Playwright report: all 47 scenarios ran, 46 passed, `task-modules` is the only final failure on both attempts, and the prior `editor-modals` lifecycle flaky is gone.
- [x] Treat the already-loaded global module map as authoritative over month-scoped IndexedDB calendar snapshots so cached pre-toggle `tasks=true` cannot rebound after the backend is disabled.
- [x] Reload the calendar with `fresh:true` after a successful module mutation so the settings transaction bypasses the pre-mutation month snapshot.
- [x] Keep backend `MODULE_DISABLED` guards and strict Playwright HTTP/console collection unchanged; remove the invalid request window instead of allowlisting 403.
- [x] Extend existing static contracts only; add no JUnit/Vitest/Playwright case and change no API/OpenAPI, PostgreSQL/Flyway, timeout or retry contract.
- [ ] Accept v27.38.x only after exact frontend, Maven 751/751, boot canary, **47/47 Chromium with zero flaky retries**, immutable image, clean PostgreSQL and staging deployment are green.

## v27.38.14 — Module Toggle Runtime Gate & Page Lifecycle Browser Parity Hotfix acceptance

- [x] Use the complete v27.38.13 Playwright report instead of the Actions tail: 47 scenarios ran, `task-modules` is the only final failure, and `editor-modals` retains a first-attempt lifecycle-fetch artifact.
- [x] Treat module disable as an immediate runtime read boundary before the guarded `/api/modules` PATCH completes, keep enablement gated until server confirmation, and roll back on persistence failure.
- [x] Gate Vue Productivity selected-day, Board, Inbox, Important and Note-search reads against the latest bridge module snapshot as well as Pinia shell state.
- [x] Keep `403 MODULE_DISABLED` strict: do not allowlist or downgrade it in Playwright diagnostics.
- [x] Treat only `pagehide`/navigation-driven `AbortError` or `Failed to fetch` from the in-flight calendar load as expected lifecycle cancellation; preserve logging for real network errors.
- [x] Keep API/OpenAPI, backend business rules, PostgreSQL, Flyway V47, browser timeouts/retries and runtime-error collector unchanged.
- [ ] Accept v27.38.x only after exact frontend, Maven 751/751, boot canary, **47/47 Chromium with zero flaky retries**, immutable image, clean PostgreSQL and staging deployment are green.

## v27.38.13 — Vue Productivity Legacy Renderer Retirement Barrier Hotfix acceptance

- [x] v27.38.12 full Playwright report is classified as 44 passed / 3 failed; Notes and PWA fixes are confirmed green and only Task scenarios remain.
- [x] Remaining traces prove successful generated Task persistence followed by Vue runtime error 15 / null `parentNode` recovery, not missing backend rows.
- [x] Trace DOM proves legacy Task UI mutation after Vue retirement: `data-vue-productivity=ready` coexists with the legacy `value="all"` Board category option.
- [x] Legacy Task metadata/editor/selected-day/Inbox/Board renderers fail closed once Vue owns Productivity, including post-await barriers for in-flight metadata/Inbox/Board reads.
- [x] No browser timeout, retry, runtime-error allowlist, backend business authority, OpenAPI shape or Flyway schema is weakened.
- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate is green.
- [ ] Maven verify is green with 751/751 JUnit tests.
- [ ] `npm run test:e2e:canary` is green.
- [ ] Full Chromium Playwright is clean: 47/47, no flaky scenario.
- [ ] Immutable image smoke, clean PostgreSQL V47 smoke and staging deployment are green.

## v27.38.12 — Vue Productivity Summary Ownership & PWA E2E Parity Hotfix acceptance

- [x] v27.38.11 Playwright report/trace artifact is inspected for all five failures; screenshots, network records and exact locator failures are available.
- [x] Legacy selected-day summary writes yield after `data-vue-productivity=ready`; Vue owns `#sumTasks`, `#sumNote` and `#sumImp` continuously, including disabled-module labels.
- [x] Task create/update evidence confirms generated mutation + projection reads are 200 and already contain the saved DTO; no timeout or projection-error allowlist is added.
- [x] PWA upgrade E2E uses canonical onboarding preset key `basic`; stale `minimum` is forbidden.
- [x] No backend business authority, OpenAPI shape, Flyway schema, browser timeout, retry or assertion is weakened.
- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate is green.
- [ ] Maven verify is green with 751/751 JUnit tests.
- [ ] `npm run test:e2e:canary` is green.
- [ ] Full Chromium Playwright is clean: 47/47, no flaky scenario.
- [ ] Immutable image smoke, clean PostgreSQL V47 smoke and staging deployment are green.

## v27.38.11 — Vue Read-Your-Write & PWA Activation Browser Parity Hotfix acceptance

- [x] v27.38.10 evidence is classified as 42 passed / 5 failed with no flaky retry; earlier mandatory gates reached Browser E2E.
- [x] Committed Task DTOs remain staged across accepted selected-day/Board projection reads and are removed from the overlay only after the refresh window settles.
- [x] Multiple Notes reload waits for `/api/v1/calendar`; bounded offline-adapter PATCH and generated Note DELETE ownership remain distinct.
- [x] First PWA registration relies on `register()` installation and explicit `registration.update()` runs only for an existing registration.
- [x] No browser timeout, retry, assertion, backend business authority, OpenAPI shape or Flyway schema is weakened.
- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate is green.
- [ ] Maven verify is green with 751/751 JUnit tests.
- [ ] `npm run test:e2e:canary` is green.
- [ ] Full Chromium Playwright is clean: 47/47, no flaky scenario.
- [ ] Immutable image smoke, clean PostgreSQL V47 smoke and staging deployment are green.

## v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix acceptance

- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate is green.
- [ ] Maven verify is green with 751/751 JUnit tests.
- [ ] `npm run test:e2e:canary` is green.
- [ ] Full Chromium Playwright is green: 47/47.
- [ ] Immutable image smoke is green.
- [ ] Clean PostgreSQL V47 smoke is green.
- [ ] Staging deployment and smoke are green.

## v27.38.8 — Vue Shared Browser Parity Hotfix acceptance

- [x] v27.38.7 full Chromium evidence is classified into four shared root causes; no timeout/retry/error allowlist is used as a fix.
- [x] Existing source contracts guard Vue focused-date helper semantics, Proxy-safe Productivity snapshots, deadline-time board presentation and first-claim PWA stability without adding test methods.
- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate passes, including 49 Vitest and production bundle audit.
- [ ] `mvn -B --no-transfer-progress verify` reports 751/751 with JaCoCo gates met.
- [ ] `npm run test:e2e:canary` passes.
- [ ] `npm run test:e2e` reports 47/47 Chromium.
- [ ] Immutable image, clean PostgreSQL V47 smoke and staging deployment are green.

Tag only after all acceptance items are green:

```bash
git tag -a v27.38.8 -m "v27.38.8 — Vue Shared Browser Parity Hotfix"
git push origin v27.38.8
```

## v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix acceptance

- [ ] `./deploy/scripts/frontend-gate.ps1` passes on Windows with exact Node 20.18.1 / npm 10.8.2, or the Linux exact frontend gate passes in CI.
- [ ] `mvn -B --no-transfer-progress verify` reports 751/751 JUnit tests with JaCoCo gates met.
- [ ] `npm run test:e2e:canary` passes before any expensive full browser run.
- [ ] Targeted Calendar/Productivity/PWA canaries pass without browser console/page errors.
- [ ] `npm run test:e2e` reaches 47/47 Chromium with strict page-error collection unchanged.
- [ ] Immutable image build and clean PostgreSQL smoke pass.
- [ ] Staging deploy/smoke passes before the release is accepted or tagged.

After all blocking gates are green:
```bash
git tag -a v27.38.7 -m "v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix"
git push origin v27.38.7
```

## v27.38.3 — Vue Productivity Strict Typecheck Hotfix acceptance

- [x] v27.38.2 exact frontend gate passes authentic lockfile, delivery foundation and OpenAPI drift before failing strict TypeScript.
- [x] The nine compiler errors are fixed without weakening `strict`, `noImplicitAny` or `exactOptionalPropertyTypes`.
- [x] Today cross-domain commands are setup functions, CalendarTask reuses generated Task, note timer uses the actual timeout return type, Pinia defaults no longer reference `this`, and optimistic note content stays non-null.
- [x] Baselines remain 152 classes / 751 `@Test` / 47 Chromium / 49 Vitest; OpenAPI remains 101 operations / 106 schemas and Flyway remains V47.
- [ ] Exact frontend gate must be green.
- [ ] Maven verify must be 751/751.
- [ ] `npm run test:e2e` must be 47/47 Chromium.
- [ ] Immutable image, clean PostgreSQL smoke and staging deploy must be green before acceptance.

Tag only after all acceptance items are green:

```bash
git tag -a v27.38.3 -m "v27.38.3 — Vue Productivity Strict Typecheck Hotfix"
git push origin v27.38.3
```

## v27.38.2 — Vue Productivity Manifest Contract Alignment Hotfix acceptance

- [x] v27.38.1 local Maven executes all 751 tests with exactly one failure and zero errors.
- [x] The remaining failure is a case-sensitive documentation-source assertion, not production runtime behavior.
- [x] Offline/reconnect documentation is checked semantically with `Locale.ROOT` lowercase matching; runtime and public browser contracts are unchanged.
- [ ] Exact frontend gate is green.
- [ ] Maven/JUnit is 751/751.
- [ ] Chromium is 47/47.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment are green.

## v27.38.1 — Vue Productivity Static Contract Alignment Hotfix acceptance

- [x] Three local Maven failures are isolated to static source-contract expectations; production runtime was not reached by the failing gate.
- [x] Historical domain contracts no longer own unrelated global OpenAPI totals.
- [x] Task-duration and productivity-readiness assertions follow the released Vue implementation without weakening the public 45-minute option or owner-retirement boundary.
- [ ] Exact frontend gate is green.
- [ ] Maven/JUnit is 751/751.
- [ ] Chromium is 47/47.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment are green.

## v27.38.0 — Vue Tasks, Notes & Important Days acceptance

- [x] One Vue productivity owner replaces legacy Tasks/Important route roots, productivity modals and selected-day Tasks/Notes/Important bodies.
- [x] Spring Boot remains authoritative; generated OpenAPI contract is 101 operations / 106 schemas and generated TypeScript drift check passes.
- [x] Q-10 reuses the existing dataLayer offline queue/snapshot through typed bridge methods; no second offline queue is introduced.
- [x] Baselines advance to 152 Java test classes / 751 `@Test` / 47 Chromium / 49 Vitest; Flyway remains V47.
- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate passes `npm ci`, strict `vue-tsc`, all 49 Vitest cases, Vite and raw/gzip budgets.
- [ ] Maven/JaCoCo passes all 751 JUnit tests.
- [ ] All 47 Chromium scenarios pass with strict runtime/page-error collection.
- [ ] Immutable image, clean PostgreSQL V1–V47 smoke and staging deployment are green.

Tag only after all acceptance items are green:

```bash
git tag -a v27.38.0 -m "v27.38.0 — Vue Tasks, Notes & Important Days"
git push origin v27.38.0
```

## v27.37.5 Vue Calendar Selected-Day Island Lifecycle Hotfix acceptance
- [x] v27.37.4 Playwright completes all 47 scenarios in ~18 minutes: 28 pass and 19 fail, proving the fresh-user onboarding blocker is closed.
- [x] Failure evidence pins repeated `null.innerHTML` to `renderChips()` after cross-route actions and shows related `null.hidden` errors.
- [x] CalendarPage parks the mandatory selected-day editor before Vue removes `#calendarLegacyPanelHost`; Calendar mount reattaches the same DOM island.
- [x] Existing Java/Vitest contracts enforce the park/reattach lifecycle with no baseline count increase.
- [x] Baselines remain 151 classes / 743 `@Test` / 47 Chromium / 43 Vitest; OpenAPI remains 98 operations / 103 schemas and Flyway remains V47.
- [ ] v27.37.5 browser acceptance completed at 31/47 Chromium; it is not an accepted green baseline. Remaining parity failures are carried into v27.38.x and stay blocking.
Tag after full acceptance:

```bash
git tag -a v27.37.5 -m "v27.37.5 — Vue Calendar Selected-Day Island Lifecycle Hotfix"
git push origin v27.37.5
```
## v27.37.4 PWA Bundle Budget Release Contract Alignment Hotfix acceptance
- [x] v27.37.3 Maven/JUnit evidence isolates one failure in `pwaUpgradeAndBundleBudgetsBecomeRecurringFrontendGates` after 742 green tests.
- [x] The stale hardcoded `27.37.1` bundle-budget assertion is removed.
- [x] Current PWA cache and bundle-budget release assertions derive from the canonical DutyLog version in `pom.xml`.
- [x] v27.37.3 `#layout` null-safety runtime fix remains intact and strict `#panel` compatibility-island ownership remains enforced.
- [x] Baselines remain 151 classes / 743 `@Test` / 47 Chromium / 43 Vitest; OpenAPI remains 98 operations / 103 schemas and Flyway remains V47.
- [ ] Exact frontend gate, Maven verify, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deploy must be green before acceptance.
Tag after full acceptance:

```bash
git tag -a v27.37.4 -m "v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix"
git push origin v27.37.4
```
## v27.37.3 Vue Calendar Selected-Day Island Routing Hotfix acceptance
- [x] Chromium trace proves `selectDay(null) -> $("layout").classList` is the remaining shared fresh-user boot exception after v27.37.2.
- [x] Retired legacy `#layout` access is null-safe; the preserved selected-day `#panel` remains a strict required compatibility island.
- [x] Existing Calendar migration regression contract forbids the old strict `#layout` dereference and requires the strict `#panel` contract.
- [x] Java/Playwright/Vitest baseline counts remain 151 classes / 743 `@Test` / 47 Chromium / 43 Vitest; OpenAPI remains 98 / 103 and Flyway remains V47.
- [ ] Exact frontend gate, Maven verify, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deploy must be green before acceptance.
Tag after full acceptance:

```bash
git tag -a v27.37.3 -m "v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix"
git push origin v27.37.3
```
## v27.37.2 Vue Calendar Boot Routing Null-Safety Hotfix acceptance

- [x] Root cause narrowed to legacy `applyRoute()` dereferencing Calendar navigation controls already retired by the Vue Calendar owner.
- [x] Route chrome synchronization is null-safe and does not restore legacy controls.
- [x] Existing Calendar migration regression contract forbids the three direct `null.style` dereference shapes.
- [x] Java/Playwright/Vitest baseline counts remain 151 classes / 743 `@Test` / 47 Chromium / 43 Vitest.
- [ ] Exact frontend gate, Maven verify, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deploy must be green before acceptance.

Tag after full acceptance:

```bash
git tag -a v27.37.2 -m "v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix"
git push origin v27.37.2
```

## v27.37.1 Vue Calendar & Timeline Strict Typecheck Hotfix acceptance

- [ ] Exact Node 20.18.1 / npm 10.8.2 frontend gate passes `vue-tsc --noEmit`.
- [ ] CalendarTimelineWorkspace bridge callback has explicit `string` and optional `CalendarMode` parameters.
- [ ] Pinia `openDate` and `goToday` actions contain no default parameter initializer that references `this`.
- [ ] Maven/JUnit baseline is 151 classes / 743 `@Test`; the CI runner-routing regression now targets GitHub-hosted `ubuntu-latest`, while Playwright remains 47 and Vitest remains 43.
- [ ] OpenAPI remains 98 operations / 103 schemas and Flyway remains V47.

## v27.37.0 Vue Calendar & Timeline acceptance

- [ ] Exact Node 20.18.1 / npm 10.8.2 gate passes `npm ci`, `vue-tsc`, all 43 Vitest cases, Vite and raw/gzip bundle budgets.
- [ ] Maven verify executes all 738 tests with zero failures and JaCoCo remains above the locked thresholds.
- [ ] Vue is the only runtime owner of Today and Calendar Month/Week/Day read surfaces; duplicate route IDs are absent.
- [ ] The selected-day editor is attached exactly once under `#calendarLegacyPanelHost` and all writes remain Spring Boot owned.
- [ ] All 47 Chromium scenarios pass, including calendar ownership and previous-cache PWA upgrade.
- [ ] Immutable image, clean PostgreSQL V1–V47 smoke and staging deployment are green.
- [ ] OpenAPI remains 98 operations / 103 schemas and no database migration is introduced.

## v27.36.8 Vue Read Sequencing Static Contract Alignment Hotfix acceptance

- [ ] Exact frontend gate passes on pinned Node/npm.
- [ ] Maven verify executes all 727 tests with zero failures and JaCoCo remains green.
- [ ] All 45 Chromium scenarios pass, including the usage-date column after year/month toggles.
- [ ] Push to `test` remains single-pass through staging validation.
- [ ] Immutable image passes clean PostgreSQL smoke and deploys.

## v27.36.7 Time Bank Period Toggle Snapshot Stability Hotfix acceptance

- [ ] `frontend-gate.sh` passes on exact Node/npm.
- [ ] Maven verify and JaCoCo thresholds pass.
- [ ] All 45 Chromium scenarios pass, including the usage-date column after year/month toggles.
- [ ] Month/year toggles do not issue another overtime-account request or replace the canonical account snapshot.
- [ ] Push to `test` remains single-pass through staging validation.
- [ ] Immutable image passes clean PostgreSQL smoke and deploys.

## v27.36.6 Time Bank Usage-Date Chart Parity Hotfix acceptance

- [x] Earned chart buckets use credit work dates.
- [x] Used chart buckets use actual usage dates.
- [x] `credit.usedHours` is excluded from dated chart aggregation.
- [x] Daily and yearly model contracts pass.
- [ ] Exact 45/45 Chromium acceptance was not reached because the period toggle replaced the canonical account snapshot.

## v27.36.5 Single-Pass CI & Final Vue Browser Parity Hotfix acceptance

- [ ] `frontend-gate.sh` passes on exact Node/npm.
- [ ] Maven verify and JaCoCo thresholds pass.
- [ ] All 45 Chromium scenarios pass.
- [ ] Push to `test` starts staging validation but not duplicate ordinary CI.
- [ ] Immutable image passes clean PostgreSQL smoke and deploys.

## v27.36.4 Vue Absence & Time Bank Browser Parity Hotfix acceptance

- [x] Duplicate legacy `#timeBankGuideModal` and backdrop are removed after Vue ownership.
- [x] Winning Vue refresh publishes planner/account/reference-date projection.
- [x] Calendar, Today and selected-day panels refresh without legacy route renderers.
- [x] Today/Calendar composer launches preserve the originating route.
- [x] Edit deletion is inside the visible Vue modal.
- [x] Usage ratio and oldest FIFO credit are present on Time Bank Overview.
- [x] Selected-day absence actions delegate to the Vue editor.
- [x] Static contracts compile through the source-only local Java gate.
- [ ] Exact Node/npm frontend gate, Maven verify, Chromium, Docker, clean PostgreSQL and staging are green in GitHub Actions.

## v27.36.3 CI Artifact Quota Resilience Hotfix acceptance

- [ ] JaCoCo upload contains only `jacoco.xml` and `jacoco.csv`, uses three-day retention and cannot fail the job.
- [ ] CI and staging Playwright artifacts upload only after a failure, tolerate missing files and cannot fail the job.
- [ ] Artifact names include both `github.run_id` and `github.run_attempt`.
- [ ] Release static checks, Docker image build and clean PostgreSQL smoke still execute after an artifact upload error.
- [ ] Full frontend, Maven/JUnit, 45 Playwright scenarios, Docker, clean PostgreSQL and staging are green.
- [ ] No runtime, API, OpenAPI, npm graph, schema, Flyway or domain-ownership change.

## v27.36.2 Vue Timer Static Contract Compile Coverage Hotfix acceptance

- [ ] Java 17 compiles `VueBrowserTimerHandleTypeFrontendContractTest` before Maven.
- [ ] The test normalizes whitespace instead of embedding an illegal multiline ordinary string literal.
- [ ] `release-check.sh` compiles both `*FrontendContractTest.java` and source-only `*HotfixTest.java`.
- [ ] The v27.36.1 `window.setTimeout` / `window.clearTimeout` behavior remains unchanged.
- [ ] Full Maven/JUnit, 45 Playwright scenarios, Docker, clean PostgreSQL and staging are green.
- [ ] No backend API, OpenAPI, npm graph, schema, Flyway or domain-ownership change.

## v27.36.1 Vue Browser Timer Handle Type Hotfix acceptance

- [ ] `vue-tsc --noEmit` accepts `AbsenceComposer.vue` and `CreditEditor.vue` under exact CI Node/npm and Node/Vitest typings.
- [ ] Both debounce paths use `window.setTimeout` and `window.clearTimeout` with numeric nullable handles.
- [ ] Preview delays remain 260 ms and 280 ms; replacement and unmount still cancel pending timers.
- [ ] All 26 Vitest cases, Maven/JUnit, 45 Playwright scenarios, Docker, clean PostgreSQL and staging are green.
- [ ] No backend API, OpenAPI, dependency graph, schema, Flyway or domain-ownership change.

## v27.36.0 Vue Absence & Time Bank acceptance

- [ ] Vue is the only runtime owner of `#vacation` and `#overtime`; legacy route roots and editor modals are absent after readiness.
- [ ] Absence journal, full/partial composer, preview, balances and two-way Time Bank link preserve released behavior.
- [ ] Time Bank overview, integrity, responsive credits/table/cards/chart, usage ownership, FIFO forecast, exact editor and scenarios preserve parity.
- [ ] Generated OpenAPI contract reports 98 operations / 103 schemas with correct arrays/allOf and passes drift checking.
- [ ] Q-06 stale-read, duplicate-submit and HTTP 409 recovery tests pass; one native double click produces one mutation.
- [ ] `vue-tsc`, 26 Vitest cases, Vite build, 142 Java classes / 685 tests and 45 Playwright scenarios are green.
- [ ] Docker image, clean PostgreSQL migration smoke and staging deployment are green.
- [ ] Flyway remains V47; rollback to v27.35.7 requires no database rollback.

## v27.35.7 Docker Frontend OpenAPI Build Context Hotfix acceptance

- [x] Docker copies `src/main/resources/static/openapi/dutylog-v1.yaml` to the canonical `/src/...` path in the frontend stage.
- [x] OpenAPI copy occurs after dependency installation and before `npm run build`.
- [x] Authentic lockfile, `npm ci`, `vue-tsc`, 16 Vitest cases, OpenAPI drift and Vite bundle checks pass in Docker.
- [x] Maven packages the Vue bundle into the existing Spring Boot image.
- [x] Full Docker, clean PostgreSQL and staging validation are green.
- [x] No runtime, API, OpenAPI content, schema, migration or domain-ownership change.


## v27.35.6 Gate A Historical Static Contract Alignment Hotfix acceptance

- [ ] All four corrected static-contract methods pass in the full Maven suite.
- [ ] `frontend/package-lock.json` remains tracked and excluded from `.gitignore`.
- [ ] Flyway discovery finds exactly V1–V47 recursively under `db/migration`.
- [ ] Docker keeps `node:20.18.1-alpine3.20` and the one-image application topology.
- [ ] Full Maven, Playwright, Docker, clean PostgreSQL and staging validation are green.
- [ ] No runtime, API, OpenAPI, schema, migration or domain-ownership change.


## v27.35.5 Gate A Quality Register Lambda Capture Compile Hotfix acceptance

- [ ] `frontend-gate.sh` passes unchanged with committed authentic lockfile.
- [ ] Maven `testCompile` compiles `VueDeliveryContractsDiagnosticsFoundationTest`.
- [ ] Q-02–Q-05 assertions use immutable `rowPrefix`.
- [ ] Full Maven verify, Playwright, Docker, clean PostgreSQL and staging are green.
- [ ] No API, OpenAPI, schema, Flyway or runtime behavior change.

## v27.35.4 Frontend Gate Static Contract Java Escaping Hotfix acceptance

- [ ] The corrected assertion compiles with escaped quotes around `$FRONTEND_DIR`.
- [ ] Committed authentic lockfile verification, `npm ci`, `vue-tsc`, 16 Vitest cases and Vite remain green.
- [ ] Maven compiles 161 production sources and 140 test sources, then executes the full suite.
- [ ] All 44 Playwright scenarios, image build, clean PostgreSQL smoke and staging deployment pass.
- [ ] No API, OpenAPI, PostgreSQL, Flyway V47, domain ownership or runtime behavior changes.

## v27.35.3 Authentic Lockfile Commit & Generated Client Fixture Hotfix acceptance

- [ ] `frontend/package-lock.json` is tracked and matches the promoted CI graph except the root release version.
- [ ] Clean checkout runs `npm ci` without generating or mutating the graph.
- [ ] `vue-tsc`, all 16 Vitest cases and Vite build pass.
- [ ] Generated-client sequential requests use independent `Response` objects.
- [ ] CI, Docker and staging use the committed graph and do not upload bootstrap artifacts.
- [ ] Q-01–Q-05 are DONE; v27.36.0 starts only after this release is fully green.

## v27.35.2 Authentic npm Lockfile Bootstrap Hotfix acceptance

- [ ] Pinned Node `20.18.1` and npm `10.8.2` generate `frontend/package-lock.json` through `npm install --package-lock-only --ignore-scripts`.
- [ ] Authenticity verification confirms npm registry tarballs, SHA-512 integrity and dependency/peer edges before `npm ci`.
- [ ] `npm ci` creates local `vue-tsc`, `vitest` and `vite` launchers; `npm ls --all` passes.
- [ ] `vue-tsc`, 16 Vitest cases, Vite build and browser-bundle audit pass without internal Volar crashes.
- [ ] CI uploads `frontend/package-lock.json` and `generated-lockfile-manifest.txt` with `if: always()`.
- [ ] Docker uses the same bootstrap → verify → `npm ci` sequence.
- [ ] Maven reports 139 Java test classes / 669 `@Test` methods and all 44 Playwright scenarios pass.
- [ ] Q-01 remains `ACTIVE`; Gate A remains blocked until v27.35.3 commits the exact CI artifact.
- [ ] No backend API shape, PostgreSQL schema, Flyway V47, domain ownership or one-image topology change.

## v27.35.0 Vue Delivery, Contracts & Diagnostics Foundation acceptance

- [ ] `frontend/package-lock.json` is committed and `npm ci` is the only CI/Docker install path.
- [ ] Node `20.18.1`, npm `10.8.2` and direct dependencies are exact-pinned.
- [ ] `generate-openapi-contract.mjs --check` passes and generated TypeScript matches canonical OpenAPI SHA-256.
- [ ] Generated operation types are consumed through the typed same-origin client.
- [ ] Vue render/boot and unhandled-promise failures show recovery UI with release/route/request ID.
- [ ] Unexpected errors remain visible to strict Playwright page-error/request collectors.
- [ ] Migration manifest/parity template exists and ADR-001–ADR-005 are accepted/indexed.
- [ ] Engineering Quality Register Q-01–Q-05 are DONE in source; Gate A is accepted only after full green v27.35.3 CI/staging.
- [ ] No backend API shape, PostgreSQL schema, Flyway V47, domain ownership or one-image topology change.

## v27.34.4 Vue Secondary Navigation & Overtime Preview Contract Hotfix acceptance

- [x] Non-persistent zero/negative preview calculation is representable without weakening create/update writes.
- [x] Secondary Vue navigation exposes semantic active state.
- [x] Full 44-scenario Chromium baseline and staging deployment are green.

## v27.34.3 Vue Shell E2E Navigation Compatibility Hotfix acceptance

- [ ] `vue-tsc`, 11 Vitest cases, Vite build and browser-bundle audit pass.
- [ ] Maven reports 135 classes / 648 tests.
- [ ] All 44 Playwright scenarios pass without clicking hidden legacy chrome.
- [ ] Vue shell brand, More menu and logout hooks remain visible and keyboard-operable.
- [ ] Calendar Sync emits `PRODID` version 27.34.3.
- [ ] API/schema remain unchanged and Flyway remains V47.

## v27.34.2 Vue Browser Runtime Bundle Hotfix acceptance

- [ ] `vue-tsc`, 11 Vitest cases and Vite build pass.
- [ ] Browser-bundle audit reports no `process.env`, CommonJS or Node path globals.
- [ ] All 44 Playwright scenarios pass with the existing strict page-error collector.
- [ ] Docker builds the same audited frontend bundle into one application image.
- [ ] Maven reports 134 classes / 647 tests and Flyway remains V47.
- [ ] Staging reports release version 27.34.2 and architecture `vue-shell-v1`.

## v27.34.1 Vue Strict Type Contract Hotfix acceptance

- [ ] `bash deploy/scripts/frontend-gate.sh` passes with real `vue-tsc`, all 11 Vitest cases and Vite production build.
- [ ] `exactOptionalPropertyTypes` remains enabled.
- [ ] AppNavigation, UiButton and UiTabs emit no optional native attribute as explicit `undefined`.
- [ ] Vite 5 accepts the library config and emits both stable app-shell assets.
- [ ] All 646 JUnit tests and all 44 Playwright scenarios pass.
- [ ] Staging reports release version 27.34.1 and architecture `vue-shell-v1`.
- [ ] API, PostgreSQL, Flyway V47 and runtime container topology remain unchanged.

## v27.34.0 Vue App Shell & Design System acceptance

- [ ] `bash deploy/scripts/frontend-gate.sh` passes with strict `vue-tsc`, 11 Vitest cases and the stable app-shell JS/CSS assets.
- [ ] Vue owns the visible brand, profile entry, primary/secondary navigation, modal host and toast host.
- [ ] Workspace Studio navigation order and enabled routes synchronize through the immutable legacy read model.
- [ ] Legacy product screens, hash routing and mutation ownership remain functional behind named bridge capabilities.
- [ ] The old topbar/tabbar hide only after `data-vue-shell="ready"`; a failed Vue boot leaves legacy navigation visible.
- [ ] Desktop, Sidebar and mobile bottom-navigation layouts remain usable with keyboard focus and reduced motion.
- [ ] All 645 JUnit tests and coverage gates pass.
- [ ] All 44 Playwright scenarios pass with no browser/runtime errors.
- [ ] Docker builds one application image and clean PostgreSQL migration smoke passes on Flyway V47.
- [ ] Staging reports release version 27.34.0 and architecture `vue-shell-v1`.
- [ ] `dutylog-app` and PostgreSQL remain the only DutyLog runtime containers.

## v27.33.0 Vue Frontend Foundation & CI/CD acceptance

- [ ] `bash deploy/scripts/frontend-gate.sh` passes.
- [ ] Vue typecheck and five Vitest cases pass.
- [ ] Vite emits the stable JS and CSS foundation assets.
- [ ] Maven packages both files under `BOOT-INF/classes/static/vue`.
- [ ] All 640 JUnit tests and coverage gates pass.
- [ ] All 43 Playwright scenarios pass with no browser/runtime errors.
- [ ] Docker builds one application image and clean PostgreSQL migration smoke passes on Flyway V47.
- [ ] Staging reports release version 27.33.0 and the Vue readiness attributes.
- [ ] `dutylog-app` and PostgreSQL remain the only DutyLog runtime containers.

## v27.32.1 Time Bank Absence Navigation Hotfix acceptance

- confirm Maven reports 635 tests with zero failures;
- confirm Playwright reports 42 scenarios with zero failures;
- create a future overtime-backed absence after initial application boot;
- open Bank Usage and click `Открыть отсутствие`;
- confirm `#absenceComposerModal` becomes visible and contains the owning title/status;
- confirm the FIFO preview names the contributing credit and does not report a false shortage;
- confirm inline editing in «Отпуск и отсутствия» still works;
- confirm API/schema remain unchanged and Flyway stays V47.


## v27.32.0 Absence & Time Bank Experience acceptance

- confirm Absences owns create/edit/delete while Overtime linked usages remain read-only;
- confirm Overview separates posted, reserved and free hours;
- confirm Credits preserves filters, chart, export and credit editor;
- confirm Bank Usage links to the owning absence and exposes allocation details;
- confirm FIFO Detail forecasts oldest-first consumption, remainder and shortage;
- confirm future planned time off appears as reserved and reduces free balance;
- confirm guide entry points, empty states and the replayable time-bank guide;
- confirm 129 Java test classes / 633 test methods / 42 Playwright scenarios;
- confirm Flyway remains V47.

## Local gate

```bash
bash deploy/scripts/frontend-gate.sh
mvn -B --no-transfer-progress test
bash deploy/scripts/release-check.sh
```

When Docker is available:

```bash
docker build -t dutylog:release-check .
bash deploy/scripts/migration-smoke-test.sh dutylog:release-check
```



## v27.31.2 browser contract alignment acceptance

- confirm Maven reports 628 tests with zero failures;
- confirm Playwright reports 41 scenarios with zero failures;
- verify the intentional direct-usage retirement probe returns `409 DIRECT_USAGE_RETIRED` without appearing in `runtime-issues.txt`;
- confirm the Overtime ledger renders absence-owned usages as `Управляется отсутствием` and exposes no `data-edit-usage` / `data-del-usage` controls for them;
- edit the linked time off from Vacation/Absences and confirm the Composer opens the owning absence;
- delete one of two linked absences and confirm both credits remain, the deleted usage disappears and the surviving linked usage remains visible;
- confirm Flyway remains V47 and clean PostgreSQL startup passes.

## Workspace, Layout & Theme Studio acceptance

- confirm UI Core reports contract v2 before feature bundles boot;
- confirm custom navigation keeps Today and Settings and never exceeds five primary items;
- confirm hidden routes remain reachable through generated secondary links;
- confirm Shift remains available while optional Today cards can be reordered/hidden;
- confirm Sidebar returns to the bottom mobile navigation below the desktop breakpoint;
- confirm Mobile Flow stays a single content column;
- confirm calendar compact/dots modes change presentation only;
- confirm grid decoration has `pointer-events: none`;
- confirm arbitrary CSS/JavaScript and unknown ids are not persisted;
- confirm Payroll, Ledger, Vacation and Calendar smoke paths remain green;
- confirm Flyway remains V46.

## Payroll Module Registry Contract Hotfix acceptance

- confirm `PayrollFoundationContractTest` reads the canonical key from `ModuleKeys.PAYROLL`;
- confirm `DutyLogModules` registers `PAYROLL` in `TIME_ACCOUNTING`;
- confirm the contract does not require the unrelated literal `ModuleService.PAYROLL`;
- confirm production Payroll service, API, V45 and browser scenario remain unchanged;
- confirm 603 Java tests and 37 Playwright scenarios pass.

## Payroll Foundation acceptance

- confirm Flyway applies V45 exactly once and creates `payroll_settings`, `payroll_adjustments`, `payroll_snapshots`;
- confirm an open month returns `PERIOD_NOT_CLOSED` on calculate;
- close a healthy month, set a positive hourly rate and calculate revision 1;
- add one addition and one deduction, calculate revision 2 and confirm revision 1 points to the replacement;
- confirm base/total amounts and all stored financial values use minor units;
- confirm `/api/v1/payroll/periods/{month}` returns `Cache-Control: no-store`;
- confirm Payroll is unavailable when its module is disabled and automatically keeps Overtime/Vacation dependencies;
- confirm 603 Java tests and 37 Playwright scenarios pass.


## v27.31.1 static contract alignment acceptance

- confirm the five historical Maven contracts expect the canonical v27.31.0 frontend shape;
- confirm direct coverage serialization still supports `HOURS_ONLY`;
- confirm legacy usage promotion uses `openLegacyUsageMigration(focusId)` and an explicit preview argument;
- confirm linked usages are managed by absences while transitional deletion remains limited to not-yet-promoted legacy manual usages;
- confirm 626 Java tests and 41 Playwright scenarios pass;
- confirm Flyway remains V47.

## v27.31.0 canonical absence ledger acceptance

- create an overtime credit and confirm the FIFO balance;
- confirm `POST /api/v1/overtime/usages` returns `409 DIRECT_USAGE_RETIRED`;
- create full-day and partial TIME_OFF only through Unified Absence Composer;
- confirm the resulting usage has `sourceKind=ABSENCE`, the absence ID and read-only Overtime actions;
- edit the absence duration and confirm the same linked usage is reallocated;
- change the absence to Unpaid and confirm the linked usage disappears and FIFO balance returns;
- preview legacy MANUAL usages and verify exact shift matches become `FULL_DAY`;
- verify non-exact durations become `HOURS_ONLY` with “Интервал не указан”;
- migrate a legacy usage and confirm its usage ID and allocation rows remain unchanged;
- verify blocked closed-period/existing-absence rows are not migrated;
- verify `/timeoff` creates a canonical absence;
- confirm V42 checksum remains unchanged and V47 appears exactly once;
- confirm clean PostgreSQL applies V47 and both absence constraints allow `HOURS_ONLY` only on a single date with no invented times;
- confirm 625 Java tests and 41 Playwright scenarios pass;
- confirm Flyway advances to V47.

## Staging

- push the exact candidate tree to `test`;
- confirm the Maven gate executes all 603 tests with zero failures;
- confirm all 37 Playwright scenarios pass, including serialized ledger refresh, workflow reservation/posting, closed-period protection and explicit factual work;
- create an overtime credit while Vacation Planner is hidden, open Vacation and confirm the available compensatory-time balance refreshes without a page reload;
- save a timezone and wait for the full calendar/tasks/ledger/notification refresh before reloading; confirm no `Failed to fetch` console error;
- on desktop confirm linked usages are asserted in the visible table, then switch to mobile cards without duplicate-visibility ambiguity;
- create a DRAFT overtime-backed absence and confirm no usage exists; submit it and confirm `RESERVED`; approve it and confirm `POSTED`; cancel it and confirm the hours return;
- close the month, confirm ordinary absence/actual-work edits return `PERIOD_CLOSED`, add an append-only correction, then reopen it;
- inspect `/api/v1/ledger-integrity` and confirm `healthy=true`, no orphan usage and no allocation mismatch;
- add an explicit factual interval and confirm the time-compensation day reports `actualSource=EXPLICIT`;
- confirm fresh schedule apply reloads the authoritative month through the data layer;
- on a phone viewport, navigate away from the current month and confirm the contextual «Сегодня» button appears, returns to today and disappears;
- select a different calendar date, open Important Days and confirm the draft date already matches the selected day;
- verify important-event checkboxes are compact, an overnight Today card shows a separate two-date chip, and multiple schedule layers remain horizontally usable;
- refresh an already rendered month and confirm the old grid stays visible while the calm loading status is announced;
- confirm the planner shows the built-in Vacation, Time Off, Sick, Unpaid and Other types;
- assign a shift, create a full-day vacation over it and confirm the absence owns the cell visually while «По графику» still names the preserved shift;
- delete the absence and confirm the original shift reappears without reconstruction;
- configure an 8-hour time-off bank, create 09:00–13:00 partial time off and confirm the shift remains visible, a 4-hour bar appears and 4 hours remain;
- create a non-overlapping partial interval on the same day and reject an overlapping interval with `ABSENCE_OVERLAP`;
- confirm vacation overflow and time-off overflow return `VACATION_LIMIT_EXCEEDED` / `TIME_OFF_LIMIT_EXCEEDED`;
- switch to Monday-Friday vacation counting and confirm weekends remain visible but do not consume allowance;
- change the work-year boundary/carryover and verify vacation balance independently from the time-off hour bank;
- export partial time off and confirm it becomes a timed `.ics` event while full-day absence stays all-day;
- open Settings → External calendar and confirm the default export range renders without `localDateKey is not defined` or any page error;
- confirm every active nginx HTTP/HTTPS server block has an exact `/calendar-feed.ics` location with `access_log off` before issuing a token;
- create a private calendar subscription and copy the one-time URL;
- fetch the URL without a web session and confirm UTF-8 `text/calendar`, CRLF and `BEGIN:VCALENDAR`;
- rotate the subscription and confirm the previous URL returns 404;
- revoke the current URL and confirm it returns 404 without deleting DutyLog events;
- export a selected range and one important event; verify shifts, tasks, important events and absences remain read-only source data;
- confirm `Deploy staging` is green;
- verify calendar, note search/export/offline queued edits, tasks, overtime, modules, admin and Android API v1;
- verify schedule-template list seeds five presets exactly once and built-ins open as copies;
- preview a four-day cycle with an occupied date and confirm safe mode reports `SKIP_CONFLICT`;
- apply the preview and confirm manual edits remain possible afterward;
- create a companion layer in another IANA timezone and verify month/week/day projection plus server-owned visibility;
- open an important-event details card, choose Edit, save and confirm both details/editor modals are hidden before navigating to Settings;
- in Day mode call the same-date flow again and confirm the selected-day panel stays open without requiring the hidden month grid;
- from the hourly Day view press «Все детали дня», confirm Month mode and the full selected-day panel become visible, then open Notes;
- edit an existing note offline, reload offline, reconnect and confirm the pending `updateNote` queue drains and the text survives an online reload;
- confirm creating, pinning, moving and deleting notes remain disabled offline while editing an existing note remains enabled;
- create an important date, timed event and multi-day period; verify read-first details, all-day rail and hourly timeline;
- change canonical timezone and verify timed events keep canonical instants while all-day dates/periods stay floating;
- create all-day, point, same-day and overnight task plans and verify the hourly timeline uses exact duration;
- verify a deadline before planned end is rejected while a later deadline/reminder stays independent;
- verify the timed-task editor reports «Дедлайн не может быть раньше окончания запланированного интервала.» and keeps the modal open;
- change canonical timezone and confirm timed planning keeps source provenance while all-day dates stay floating;
- verify project suggestions/chips/filter/search and Inbox search across open/archived/local queued entries;
- verify `/api/tasks/board?from=...&to=...` still filters by deadline/date for older clients;
- verify `/api/tasks/board?scheduledFrom=...&scheduledTo=...` filters by planned overlap, including overnight intervals;
- verify the Web/PWA board date fields and «этот месяц» use the planned-range contract;
- verify `/actuator/info` shows staging, commit and build metadata;
- verify Shift Worker / Planner / Minimal navigation and hidden-route links;
- verify Dashboard / Compact / Focus on desktop and mobile;
- verify theme + palette independence and automatic persistence after reload;
- verify custom accent → Theme palette restores both theme accents without switching themes;
- verify «Вернуть цвета темы» also works while the select already says Theme palette;
- verify Theme / Preset / Custom palette status and reload persistence;
- verify Outline keeps a visible border while Ghost has no visible idle border/shadow and gains a hover surface;
- verify Secondary / Danger / Link / Icon preview variants, disabled state, keyboard focus and phone touch targets;
- verify no Classic selector remains and `shellMode=classic` from an old local cache still boots the single shell;
- verify Appearance remains open after reload and workspace/layout/palette persist;
- verify Overtime Next summary, Month/Year/All-time presets, daily/monthly chart keys and FIFO queue;
- verify `account-page` returns canonical `usages` and summary/chart show the same `+5 / −4 / +1` snapshot;
- verify reopening an already selected calendar day keeps the panel and timezone projection visible;
- verify the professional ledger table on desktop and detailed credit cards on a phone viewport;
- verify credit/usage editors, legacy migration and CSV/Excel export remain available;
- test the migration against the persistent staging database;
- optionally reset staging and verify a clean V1..latest install.

## v27.26.0 ledger smoke

- create an overtime credit and record its available FIFO balance;
- create an `OVERTIME_BANK` partial absence and confirm one locked `ABSENCE` usage appears;
- edit the absence duration and confirm the same source usage is reallocated;
- delete the absence and confirm the consumed minutes return;
- verify manual usage edit/delete returns `LINKED_USAGE_MANAGED_BY_ABSENCE`;
- create `UNPAID` and `SICK_PAY` absences and confirm they do not consume overtime;
- verify `/api/time-compensation` reports planned, worked, earned, used, covered, vacation, sick and unpaid values;
- verify V43 converted any legacy standalone balance into the oldest opening credit and left `day_entries` untouched.


## Production

- merge the tested tree into `main`/`master` without additional changes;
- confirm production resolved `staging-tested-tree-*`;
- confirm a verified pre-deploy dump was created;
- confirm health and smoke checks passed;
- verify production data remains intact;
- copy a recent backup off the VPS.

## Security and isolation

- staging and production `.env` files use different secrets;
- PostgreSQL is not attached to the shared edge network;
- app images are referenced by `@sha256:` digest;
- deployment SSH host keys use strict known-host checking;
- host `.env` files are not overwritten by CI;
- application container runs as non-root;
- production registration starts closed;
- no workflow runs `down -v` against production.

## Tag

```bash
git tag -a v27.37.1 -m "v27.37.1 — Vue Calendar & Timeline Strict Typecheck Hotfix"
git push origin v27.37.1
```
