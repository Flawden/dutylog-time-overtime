# DutyLog v27.40.35 — Functional Parity Source Contract Scope Hotfix

v27.40.34 passed the exact Vue frontend stage and reached all 790 Java tests. Maven then exposed two stale source-contract assertions: one required calendar shift color to be assigned directly from `facts.shift.color` even though runtime already uses a `shiftColor` helper variable, and another searched for canonical `serverProjection?.dayEarnedHours` inside `ledgerChartColumns` even though that authority is intentionally encapsulated in `dayCreditTotals`. v27.40.35 changes no product runtime behavior; it aligns those contracts with the existing helper boundaries.

# DutyLog v27.40.34 — Vue Asset Version Contract Hotfix

v27.40.33 passed the exact frontend stage and reached Java 17 Maven, where two Vue shell source-contract classes still expected browser asset URLs ending in `v=27.40.32`. The runtime correctly ships the current release suffix, so the tests—not the application—were stale. v27.40.34 removes those patch-number literals and derives the expected suffix from the canonical application release version. No product calculation, API, Flyway, auth, offline, calendar or navigation behavior changes.

# DutyLog v27.40.33 — Overtime Chart Projection Fixture Contract Hotfix

v27.40.32 reached the exact frontend gate and failed two deterministic Vitest assertions because the affected fixtures overrode visible credit `hours` to 3 h / 2 h while silently retaining the helper's default canonical `dayEarnedHours=4`. The production v27.40.32 model is intentionally server-projection-first, so it correctly read those contradictory fixtures as 4 h + 4 h = 8 h. v27.40.33 changes no runtime behavior: it makes the fixtures internally coherent with the expected 3 h + 2 h = 5 h contract, then reruns the unchanged release surface.

Previous candidate: **DutyLog v27.40.32 — Functional Parity Sweep II & Mobile Usability Closure**.

# DutyLog v27.40.32 — Functional Parity Sweep II & Mobile Usability Closure

v27.40.31 restored Today shift timing and relative Important Days copy. v27.40.32 closes the remaining manual-staging parity/usability gaps before Vacation/Payroll expansion: overtime charts use canonical server daily projections and stop painting fake zero bars; ledger-integrity warnings are grouped into human-readable explanations with machine codes behind technical details; the month calendar again uses shift color as scannable information; and the phone bottom navigation becomes visually icon-only while preserving accessible route names. No API/Flyway, auth, onboarding, module, or offline `dataLayer` ownership changes are introduced.

## DutyLog v27.40.30 — Legacy Retirement Closure & First-Run Onboarding Boundary (historical)

v27.40.29 is the proven-green post-retirement baseline. v27.40.30 closes the Vue Legacy Retirement phase after a focused onboarding ownership audit: every routed user-facing screen, the shell and offline/sync presentation are Vue-owned after readiness; `dataLayer` remains the single offline mutation/sync infrastructure owner; `#firstRunOnboarding` is retained as one explicit bounded legacy presentation exception because rewriting that isolated boot overlay would add module/PWA first-claim risk without user-visible value. The next phase is Functional Parity Sweep, not another screen migration.

## DutyLog v27.40.29 — Vue Logout Ownership & Offline Status Contract Hotfix (historical)

v27.40.28 aligned shell identity E2E with the retired legacy header, then Chromium exposed two independent post-retirement leftovers: the PWA offline scenario still asserted the old translated word “оффлайн” while Vue intentionally renders “Нет сети”, and the Vue logout action still delegated to the deleted legacy `#logout` DOM button. v27.40.29 makes logout DOM-independent through the existing session/action boundary and gives the Vue offline status a language-independent `data-network-state` contract. First-run onboarding is deliberately untouched until this cut is proven green.

Previous browser-red contract hotfix: **DutyLog v27.40.28 — E2E Vue Shell Identity Contract Alignment Hotfix**.

# DutyLog v27.40.28 — E2E Vue Shell Identity Contract Alignment Hotfix

v27.40.27 fixed the real async boot race: onboarding now completes, the Vue shell renders the authenticated identity, and the retired recovery header stays gone. Exact Chromium then exposed one stale browser helper that still waited for the deleted `#whoami`. v27.40.28 changes no runtime behavior: `registerAndOnboard()` now asserts the Vue-owned `[data-vue-shell-profile]` identity and explicitly keeps E2E away from retired legacy chrome.

Previous browser-red runtime hotfix: **DutyLog v27.40.27 — Legacy Header Async Boot Ownership Hotfix**.

# DutyLog v27.40.27 — Legacy Header Async Boot Ownership Hotfix

v27.40.26 correctly retired `#legacyGlobalHeader`, then exposed a deeper async ownership race: `70-user-boot.js` resumed after Vue readiness and still wrote unconditionally to the retired `#whoami` node. That exception aborted authenticated boot before first-run onboarding could become visible. v27.40.27 makes legacy identity writes recovery-only and keeps authoritative profile publication independent of the retired header.

Previous browser-red hotfix: **DutyLog v27.40.26 — Legacy Header Retirement Selector Hotfix**.

# DutyLog v27.40.26 — Legacy Header Retirement Selector Hotfix

v27.40.25 reached the full Chromium suite with 46/48 passing. Both failures were the same deterministic runtime ownership bug: `shell-bootstrap.js` tried to retire `body > .head`, but the recovery header actually lives at `.wrap > .head`, leaving a second `#offlineStatus` in the DOM after Vue readiness. v27.40.26 gives that recovery header an explicit `#legacyGlobalHeader` identity, retires it by ID, and aligns shell/mobile E2E with the Vue-owned header. Offline queue execution remains exclusively in `dataLayer`; first-run onboarding is still the only live post-ready legacy presentation exception.

Previous browser-red ownership cut: **DutyLog v27.40.25 — Vue Offline Sync Surface & Legacy Header Retirement**.

# DutyLog v27.40.25 — Vue Offline Sync Surface & Legacy Header Retirement

v27.40.24 is the proven-green baseline. v27.40.25 moves the remaining offline/sync presentation into the Vue shell while preserving the existing `dataLayer` as the single IndexedDB/outbox/reconnect executor.

After Vue readiness the server-rendered `.head` and legacy `#offlineSyncDialog` are physically retired; Vue owns `#offlineStatus`, queue/failed diagnostics, manual sync actions and save-status feedback through narrow bridge capabilities. The recovery markup still exists before readiness. First-run onboarding is now the only intentionally live post-ready legacy presentation exception. OpenAPI stays 124/130 and Flyway stays V47.

Previous accepted ownership checkpoint: **DutyLog v27.40.24 — Final Legacy Ownership Audit & Dead UI Surface Retirement**.

# DutyLog v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix

v27.40.22 completed the live Admin migration, but exact CI stopped at Maven because `TodayDashboardFrontendContractTest` still asserted the old generic pre-Vue route fallback source line. v27.40.23 keeps the Vue Admin/runtime ownership unchanged and aligns that contract with the intended recovery policy: pre-Vue `#admin` falls back to Settings while unknown routes still fall back to Today. The acceptance surface remains 159 Java test classes / 772 tests / 48 Playwright scenarios / 60 Vitest cases, OpenAPI 124/130 and Flyway V47.

# DutyLog v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement

v27.40.21 is the proven-green staging baseline. v27.40.22 migrates the final live legacy UI domain — Admin Users/Roles, Registration and Diagnostics — into a typed Vue/Pinia workspace backed by generated `/api/v1/admin/**` operations. The historical `/api/admin/**` backend namespace remains a secured compatibility alias, but legacy Admin DOM/state/data/render ownership and the last post-Vue legacy route side-effect adapter are removed. After Vue readiness there are no legacy-owned user screens; limited pre-Vue recovery and the single `dataLayer` offline mutation/reconnect owner remain explicit infrastructure boundaries. OpenAPI advances to 124 operations / 130 schemas; Flyway remains V47.

# DutyLog v27.40.21 — Vue Payroll Workspace Retirement

v27.40.20 is the proven-green staging baseline. v27.40.21 retires Payroll as a live legacy UI owner: the route now renders from a typed Vue/Pinia domain using generated OpenAPI operations, while the old `view-payroll`, `45-payroll.js`, legacy Payroll state and legacy Payroll data helpers are removed. The existing Payroll browser journey and stable `#payroll*` selectors remain the parity contract. Post-Vue legacy route effects are now Admin-only; OpenAPI remains 118/120, Flyway remains V47, and `dataLayer` remains the sole offline mutation/reconnect owner.

# DutyLog v27.40.20 — E2E Release Version Contract Alignment Hotfix

v27.40.19 correctly centralized browser release-version expectations, but two historical Java source contracts still asserted the pre-helper E2E source shape and stopped Maven before Chromium could run. v27.40.20 keeps the runtime and browser helper unchanged, aligns those contracts with the shared authority, and adds no route/offline/API/schema change.

# DutyLog v27.40.19 — E2E Release Version Authority Hotfix

v27.40.18 reached all 48 Chromium scenarios and failed only two deterministic browser assertions because the Vue foundation/shell tests still hardcoded v27.40.16 while the released app correctly reported v27.40.18. v27.40.19 makes root `package.json` the canonical E2E release-version source and reuses it for Vue diagnostics, Calendar ICS and PWA cache-prefix acceptance. Runtime routing, OpenAPI 118/120, Flyway V47 and the single `dataLayer` offline owner remain unchanged.

# DutyLog v27.40.18 — Calendar ICS Release Version Contract Hotfix

v27.40.17 proved the Vue route-commit/hash-listener cut at the exact frontend gate, then stopped in Maven because the Java ICS generator still hardcoded the previous 27.40.16 PRODID while the release contract expected 27.40.17. v27.40.18 fixes that runtime version drift, preserves the route architecture unchanged, and adds a project-version-derived Java contract so future releases cannot silently forget the ICS PRODID. OpenAPI remains 118/120, Flyway remains V47, and `dataLayer` remains the sole offline mutation/reconnect owner.

# DutyLog v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement

v27.40.16 is the accepted green staging baseline. v27.40.17 removes the last post-Vue hash-routing competition: Vue publishes each guarded canonical route through `dutylog:vue-route-committed`, while the historical `hashchange -> applyRoute()` listener is detached at Vue readiness. Legacy consumes committed routes only for the two remaining Payroll/Admin side effects; the full old router remains available only before Vue readiness as recovery. OpenAPI remains 118/120, Flyway remains V47, strict frontend policy is unchanged, and the single legacy `dataLayer` remains the sole offline queue/reconnect owner.

# DutyLog v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix

The v27.40.15 staging browser run reached all 48 strict scenarios and exposed one common route-entry freshness regression after the v27.40.14 router narrowing, one Today workspace-ownership gap, and one retry-only note-create race. v27.40.16 keeps Vue route/guard authority in place, moves the missing fresh-read and workspace side effects into their actual Vue domain owners, and makes note creation read-your-write from the authoritative create response instead of reloading over a live editor draft. OpenAPI remains 118/120, Flyway remains V47, strict frontend policy is unchanged, and the single legacy `dataLayer` remains the sole offline queue/reconnect owner.

# DutyLog v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix

v27.40.14 passed the exact frontend gate and then executed all 760 Maven tests with exactly one failure in `VueRouteGuardAuthorityCutoverTest`: a source contract searched for one uninterrupted English comment sentence even though the correct `loadProfile()` implementation already called `publishLegacyPlatformState()` after `applyRoute()`. v27.40.15 keeps the route-guard/runtime cut unchanged and replaces that formatting-sensitive assertion with structural checks scoped to the real `loadProfile()` function. OpenAPI 118/120, Flyway V47, strict frontend policy and the single legacy `dataLayer` owner are unchanged.

# DutyLog v27.40.14 — Vue Route Guard Authority Cutover

v27.40.13 is the accepted green staging baseline. v27.40.14 moves admin/module route guards into Vue, canonicalizes blocked hashes to Calendar, moves the body route marker and Calendar panel route-exit behavior under Vue ownership, and narrows the post-Vue legacy router to Payroll/Admin side effects only. The historical full router remains as pre-Vue recovery, while the single legacy `dataLayer` remains the sole offline queue/reconnect owner.

# DutyLog v27.40.13 — Vue Route State Authority Cutover

v27.40.12 is the accepted green staging baseline. v27.40.13 removes the last circular route-state dependency from the migrated Vue shell: Vue now reads, writes and subscribes to the canonical URL hash directly, `DutyLogLegacySnapshot` no longer carries `route`, and `LegacyBridge.navigate()` is retired. The legacy hash listener remains only as a compatibility side-effect adapter for Payroll/Admin and pre-Vue recovery; the single legacy `dataLayer` remains the sole offline queue/reconnect owner.

# DutyLog v27.40.12 — Legacy Command Surface Retirement

v27.40.11 is the accepted green staging baseline. v27.40.12 removes obsolete modal/Productivity commands from the Vue↔legacy compatibility API after Quick Actions, Task/Important details/editors and Shift Type Manager have native Vue owners. `LegacyBridge` no longer exposes generic `openModal`, Task/Important opener capabilities or the `open-modal` fallback command. Payroll/Admin hash routing and the single legacy `dataLayer` remain explicit later-retirement boundaries.

# DutyLog v27.40.11 — Vue Shift Type Manager Modal Retirement

v27.40.10 is the accepted green staging baseline. v27.40.11 retires the live legacy Shift Type Manager modal: Calendar now opens a Vue-owned editor through the Settings domain, Shift Type CRUD uses generated OpenAPI operations, and the generic legacy bridge loses its `openShiftTypeManager` adapter. The legacy source modal remains only for pre-Vue recovery and is removed at Vue Settings readiness. Payroll/Admin routing and historical numbered-JavaScript recovery entry points remain explicit later-retirement boundaries.

# DutyLog v27.40.10 — Vue Quick Actions Modal Retirement

Starting from the proven-green v27.40.9 baseline, v27.40.10 removes the live legacy Quick Actions modal from the migrated Productivity/Today path. `QuickActionsModal.vue` now owns Inbox capture, Task, Note, Important Day, Overtime Credit and Absence actions while retaining the existing E2E selectors and module semantics. The old HTML modal remains only as pre-Vue recovery fallback and is removed from live DOM when Productivity becomes Vue-owned; `openQuickActions` is no longer part of the generic legacy bridge. The existing `dataLayer` remains the sole offline queue/sync owner, and Payroll/Admin routing plus Shift Type Manager remain explicit later-retirement boundaries.

# DutyLog v27.40.9 — Vue Shell Navigation Model Retirement

Starting from the proven-green v27.40.8 baseline, v27.40.9 removes the hidden legacy `#tabbar` DOM from the Vue shell navigation model. Primary navigation now follows persisted workspace preferences directly and available sections follow the authoritative module state; the fallback tabbar may still exist for recovery, but its CSS classes are no longer application state. The Settings destination is labelled `Настройки` / `Settings`, while the overflow control remains `Ещё` / `More`. Payroll/Admin hash routing and the single legacy `dataLayer` offline owner remain compatibility boundaries for later v27.40.x retirement cuts.

# DutyLog v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix

The v27.40.7 local Maven gate executed all 758 tests and failed exactly one static/source assertion in `VueTasksNotesImportantMigrationFrontendContractTest`: the test searched for a single uninterrupted English comment sentence, while the correct reconnect guard was split across two comment lines. v27.40.8 replaces that formatting-sensitive assertion with structural checks over the actual `onBeforeUnmount` block: only a pending debounce draft may clear its timer and call `updateNote`, while `dataLayer.syncQueue()` remains the sole reconnect queue owner. Product runtime behavior, retries/timeouts, strict TypeScript, OpenAPI 118/120 and Flyway V47 are unchanged.

# DutyLog v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix

The v27.40.6 staging browser report reached the full 48-scenario suite and exposed four deterministic selected-day parity failures plus one retry-only offline-note failure. v27.40.7 ports the lost shift/timezone and cross-midnight overtime rendering semantics into the native Vue panel, updates one desktop notes-layout assertion that was specific to the retired narrow rail, and closes the reconnect race where `SelectedDayNotes` could submit the same queued note beside `dataLayer.syncQueue()`. The Vue selected-day owner remains in place, the existing dataLayer remains the only offline queue/sync owner, and no retry, timeout, strictness, OpenAPI or Flyway relaxation is introduced.

# DutyLog v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix

The exact Node 20.18.1/npm 10.8.2 frontend gate for v27.40.5 exposed one strict-template error in `SelectedDayPanel.vue`: the generated schedule-preview schema allows `date?: string`, while Vue keys cannot receive `undefined` with `exactOptionalPropertyTypes`. v27.40.6 keeps the date key when available and provides a defined `preview-N` fallback when it is absent. Strict TypeScript, the v27.40.4 selected-day ownership cut, OpenAPI 118/120, Flyway V47 and the 153 / 758 / 48 / 52 acceptance surface remain unchanged.

# DutyLog v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix

v27.40.4 correctly introduced the explicit `CalendarDaySection` type into `CalendarTimelineWorkspace.vue`, but the historical v27.37.1 Maven source contract still required the older two-type import literal. v27.40.5 aligns that contract with the real native selected-day domain and adds an explicit `openDay(..., section?: CalendarDaySection | null)` assertion. Product behavior, strict TypeScript, OpenAPI 118/120, Flyway V47 and the 153 / 758 / 48 / 52 acceptance surface are unchanged.

# DutyLog v27.40.4 — Vue Calendar Selected-Day Panel Retirement

v27.40.4 retires the last live Calendar selected-day DOM compatibility island. The Calendar page now renders a native Vue `SelectedDayPanel` while preserving the stable selected-day contracts for shifts, markers, schedule templates, overtime, absences, Tasks, Notes and Important Days. Offline day writes still flow through the single existing `dataLayer` owner; OpenAPI 118/120, Flyway V47 and the 153 / 758 / 48 / 52 acceptance surface remain unchanged.

# DutyLog v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix

The v27.40.3 accepted predecessor serializes lazy `NotificationSettings` creation on the owner row, restores curated timezone aliases including `Europe/Kyiv`, and keeps the exact Vue timezone-save copy contract without changing OpenAPI 118/120, Flyway V47 or browser failure policy.

# DutyLog v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix

v27.40.1 reaches the strict Playwright canary and fails only because the newly native Settings bootstrap creates one first-user schedule preset race and omits the required `sourceTimezone` query on two generated migration-preview GETs. v27.40.2 serializes the two shared-preset list reads and supplies the authoritative work timezone to both preview operations without weakening HTTP/runtime collection, retries, backend rules, OpenAPI 118/120, Flyway V47 or the 153 / 758 / 48 / 52 regression surface.

# DutyLog v27.40.1 — Vue Settings Retirement Maven Contract Alignment Hotfix

v27.40.0 passed the exact frontend gate and then exposed one stale Maven source contract for the intentionally retired `#settingsLegacyHost`. v27.40.1 aligned that Java assertion only and carried the native Settings runtime forward unchanged into the browser canary.

# DutyLog v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover

v27.39.6 is the accepted green staging baseline. v27.40.0 starts the final Vue-retirement family by deleting the Settings compatibility host/parking bridge and moving Time, Schedule/Calendar Layers and Notifications into native Vue Settings backed by generated `/api/v1/*` operations. The milestone remains fail-closed: this cut does **not** claim full numbered-JavaScript retirement while selected-day Calendar, legacy routing/modal adapters, offline `dataLayer`, Payroll and Admin still have explicit compatibility ownership. Acceptance remains 48/48 Chromium with zero flaky retries, 52 Vitest, 758 JUnit, Flyway V47 and the immutable staging pipeline.

# DutyLog v27.39.4 — Vue Settings Browser Ownership & Preview Correlation Hotfix

The v27.39.0 Settings migration and v27.39.1–v27.39.3 compile/contract/version fixes remain intact. v27.39.4 consumes the complete 189 MB Playwright artifact: sixteen failures are one stale E2E selector for the intentionally retired `#view-settings`, while the only independent failure is an absence-preview response race. Browser helpers now target the Vue Settings owner, and the time-off scenario waits for the exact 09:00–13:00 preview request without weakening the 240-minute assertion. Runtime/API/Flyway scope is unchanged; acceptance remains 48 Playwright / 52 Vitest / 758 JUnit / V47.

# DutyLog v27.39.3 — Frontend Diagnostics Release Version Source Hotfix

The v27.39.0 Settings feature, v27.39.1 strict-TypeScript fixes and v27.39.2 Maven contract alignment remain unchanged. v27.39.3 removes the final duplicated frontend release literal exposed by the exact staging Vitest gate: Vite diagnostics now derive the release from the committed frontend package metadata instead of a hand-maintained string. Runtime/API/Flyway scope is unchanged; acceptance remains 48 Playwright / 52 Vitest / 758 JUnit / V47.

# DutyLog v27.39.2 — Vue Settings Maven Contract Alignment Hotfix

The v27.39.0 Settings feature and v27.39.1 strict-TypeScript runtime fixes remain unchanged. v27.39.2 is a source-contract-only follow-up to the local Maven gate: it updates two stale Java assertions to the already-current PWA predecessor fixture and Settings readiness publication form. Runtime/OpenAPI/Flyway scope is unchanged; acceptance remains 48 Playwright / 52 Vitest / 758 JUnit / V47.

# DutyLog v27.39.1 — Vue Settings Strict Typecheck Hotfix

The v27.39.0 feature implementation remains intact. v27.39.1 is a narrow fail-closed strict-TypeScript follow-up for the first staging frontend-gate failure: Vue Settings/Workspace template bindings no longer leak optional `undefined` values into DOM attributes, catalog entries are checked before use, and Workspace Studio array movement is explicitly narrowed for `noUncheckedIndexedAccess`. OpenAPI remains 118 operations / 120 schemas and the runtime acceptance baseline remains 48 Playwright / 52 Vitest / 758 JUnit / Flyway V47.

# DutyLog v27.39.0 — Vue Settings, Workspace & Integrations

The accepted v27.38.15 green baseline advances into the Settings migration. Vue now owns Profile, Language, Modules, Calendar Sync and Appearance/Workspace Studio through generated API contracts, while Time, Schedule and Notifications remain explicit compatibility islands until v27.40.0. OpenAPI expands to 118 operations / 120 schemas, Q-11/ADR-008 disables public production source maps by default, and the release keeps the accepted module-cache authority rules intact. See `docs/VUE_SETTINGS_WORKSPACE_INTEGRATIONS_V27.39.0.md` for the migration boundary and acceptance requirements.

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

The v27.38.10 staging run reached **42/47 Chromium with no flaky scenario**. v27.38.11 closes three remaining shared parity roots without weakening browser policy: Task mutations now retain a backend-authoritative read-your-write overlay across accepted projection refreshes, the multiple-notes reload waits for the generated `/api/v1/calendar` owner, and first PWA registration no longer forces a redundant explicit update check during initial install.

Release acceptance still requires exact frontend, Maven 751/751, canary, clean 47/47 Chromium, immutable image, clean PostgreSQL V47 smoke and staging.

# v27.38.10 — Vue Offline, Task Publication & PWA Browser Parity Hotfix

- Uses the v27.38.9 full-browser result (40 passed / 6 failed / 1 flaky) to fix three shared roots instead of weakening individual scenarios: offline selected-day hydration, Task mutation publication across refresh sequencing, and service-worker registration during first-run onboarding.
- Preserves the existing offline dataLayer boundary: Note PATCH stays on `/api/notes/{id}`, generated Note DELETE stays on `/api/v1/notes/{id}`, and no second cache/queue is introduced.
- Preserves the 152 / 751 / 47 / 49 / V47 baseline and OpenAPI 101 / 106.
- Full acceptance remains fail-closed on exact frontend, Maven, canary, clean mandatory 47/47 Chromium, immutable image, clean PostgreSQL and staging.

# v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix

- Reduced the v27.38.8 browser-parity set by aligning generated Vue endpoint expectations, offline Productivity readiness, PWA first-install sequencing and Task read-model publication.
- Kept the legacy note alias where the existing offline adapter owns update mutations and preserved strict browser/runtime error collection.
- Full Chromium reached 40 passed / 6 failed / 1 flaky; v27.38.10 inherits the remaining parity work.

# v27.38.8 — Vue Shared Browser Parity Hotfix

- Fixes the four shared browser-parity defects exposed after v27.38.7 made the onboarding canary green: stale Calendar toggle semantics, reactive Proxy cloning before Productivity writes, missing board deadline time, and first service-worker claim reload during onboarding.
- Keeps Vue Calendar focused-date ownership idempotent, generated Productivity writes strict, backend-projected deadlines visible, and PWA upgrade reloads limited to pages that were already controlled at load.
- Preserves strict Playwright error collection and the 152 / 751 / 47 / 49 / V47 baseline; OpenAPI stays 101 / 106.
- Full acceptance remains fail-closed on exact frontend, Maven, canary, mandatory 47/47 Chromium, immutable image, clean PostgreSQL and staging.

# v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix

- Stops Vue Productivity from reading Tasks/Notes/Important APIs before the authoritative module snapshot and onboarding state are ready; disabled modules no longer generate `403 MODULE_DISABLED` storms during first-run or reload.
- Keeps identical legacy module snapshots referentially stable so unrelated legacy-state publications do not restart the same Productivity reads repeatedly.
- Preserves strict browser error collection: the fix removes invalid requests rather than teaching Playwright to ignore 403s. Backend module guards, OpenAPI, PostgreSQL/Flyway and the 152 / 751 / 47 / 49 baselines are unchanged.
- Full acceptance remains fail-closed on exact frontend, Maven, the boot canary, targeted browser checks and mandatory 47/47 Chromium.

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

GitHub Actions confirmed real progress: `vue-tsc` now launches, but the hand-built lockfile produced an incomplete Volar dependency graph and crashed inside `computedSfc` before checking DutyLog source. v27.35.2 removes the synthetic lockfile, regenerates an authentic npm graph with the exact pinned toolchain, verifies registry/integrity/dependency edges, then runs `npm ci`, `vue-tsc`, Vitest and Vite.

Every validation workflow uploads the generated `frontend/package-lock.json` and its SHA-256 manifest even when a later frontend step fails. Gate A is deliberately still blocked; the exact CI artifact must be committed in v27.35.3 before `v27.36.0 — Vue Absence & Time Bank` begins. Full details are in `docs/AUTHENTIC_NPM_LOCKFILE_BOOTSTRAP_HOTFIX_V27.35.2.md`.

# v27.35.1 — Frontend Lockfile Executable Resolution Hotfix

GitHub Actions accepted the pinned Node/npm toolchain and generated OpenAPI drift gate, then proved that the first frontend lockfile lacked npm `bin` metadata for the local `vue-tsc`, `vitest` and `vite` launchers. This hotfix restores those mappings and makes CI/Docker stop immediately when an executable or installed dependency is missing.

The gate remains lockfile-only: no `npx`, global compiler or mutable install fallback is permitted. Product behavior, API, PostgreSQL, Flyway V47, FIFO, Payroll and one-image deployment remain unchanged. The complete correction is in `docs/FRONTEND_LOCKFILE_EXECUTABLE_RESOLUTION_HOTFIX_V27.35.1.md`.

## Previous release: v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation

DutyLog established Gate A with exact Node/npm pins, `npm ci`, generated OpenAPI contracts, typed transport, diagnostics, recovery UI, migration manifests and ADR-001–ADR-005. v27.35.1 corrects the executable-resolution defect found by its first clean GitHub Actions run.

## Previous hotfix: v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix

The green stabilization hotfix separated non-persistent overtime draft preview from strict create/update validation and completed semantic active-route state for secondary Vue navigation. It changed no database schema, Flyway migration, domain ownership or deployment topology.

## Previous hotfix: v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix

The Vue shell now boots correctly, so the remaining red Chromium baseline revealed stale test ownership: historical scenarios were still trying to click the hidden legacy topbar, tabbar, brand and logout controls. This hotfix moves shared E2E navigation to the released public shell bridge and updates shell-specific scenarios to interact with visible Vue controls.

No legacy chrome is restored. Vue remains the visible shell owner, while existing product screens and business mutations remain legacy-owned until their bounded migration releases. Calendar Sync release identity coverage is also aligned with `27.34.3`.

## Previous hotfix: v27.34.2 — Vue Browser Runtime Bundle Hotfix

The browser-runtime hotfix removed the Node-only `process.env.NODE_ENV` reference and added a generated-bundle audit. v27.34.3 stabilizes the now-running shell against the historical E2E suite.

## Historical release: v27.34.2 — Vue Browser Runtime Bundle Hotfix

GitHub Actions compiled the Vue shell successfully, then the real Chromium baseline exposed a browser-runtime defect: Vite library mode left `process.env.NODE_ENV` in the generated JavaScript. Browsers do not provide Node's global `process`, so the shell failed before readiness and the strict Playwright fixture reported the same page error across nearly every scenario.

The hotfix replaces that environment expression at build time and audits the generated browser bundle for residual Node globals. Playwright error collection remains strict. Product behavior, API, database migration and deployment topology do not change; the Vue app shell from v27.34.0 remains the released feature surface.

## Previous hotfix: v27.34.1 — Vue Strict Type Contract Hotfix

The strict compiler hotfix preserved `exactOptionalPropertyTypes`, normalized optional native button attributes and removed the unsupported Vite 5 `cssFileName` library option. It passed the real TypeScript, Vitest and Vite build stages; v27.34.2 follows with the browser-runtime correction found by Chromium.

## Previous release: v27.34.0 — Vue App Shell & Design System

Vue has taken its first visible production territory in DutyLog. It now owns the application brand, profile entry, primary/secondary navigation, online state, responsive shell chrome and the first shared modal, toast and design-system primitives.

Legacy product screens remain authoritative in this release. Vue reaches Today, Calendar, Absences, Time Bank, Tasks, Settings and the other workspaces only through named bridge capabilities and receives route/workspace/profile changes through a frozen read model. The released hash router and business mutations therefore remain single-owner while migration continues.

The safe fallback is explicit: old chrome hides only after Vue readiness. Deployment remains one repository, one Spring Boot JAR/image, one `dutylog-app` container and one PostgreSQL container. No API or database migration is introduced; Flyway remains V47.

## Build after the Vue app shell

```bash
bash deploy/scripts/frontend-gate.sh
mvn -B --no-transfer-progress verify
npm ci
npm run test:e2e
```

Vite output is packaged into the same Spring Boot JAR. No frontend server or third DutyLog container is used in production.

## Previous release: v27.33.0 — Vue Frontend Foundation & CI/CD

DutyLog established Vue 3, TypeScript, Vite, Pinia, memory-history routing, a typed same-origin API client and an explicit legacy bridge inside the existing Spring Boot build. CI/CD gained frontend type, unit and bundle gates while production remained one application image/container plus PostgreSQL.

## Previous release: v27.32.1 — Time Bank Absence Navigation Hotfix

The bank-to-event link now completes the full ownership journey. Clicking **Открыть отсутствие** from Bank Usage refreshes the owning absence when necessary, opens Unified Absence Composer as a modal and immediately rebuilds the FIFO preview for that edited record.

The hotfix does not change the API, PostgreSQL, Payroll, canonical ownership or Flyway V47. It also records the next approved architecture stage: after the green v27.32.x baseline, DutyLog will migrate the entire frontend to Vue 3 + TypeScript + Vite inside the same repository, JAR/image and production app container.

## Previous release: v27.32.0 — Absence & Time Bank Experience

DutyLog now presents one absence through the right lens in each workspace. **Отпуск и отсутствия** owns the event and every mutation. **Переработки** is the service-grade time bank: overview, earned credits, posted and reserved usage, FIFO allocations and a forecast of what will be consumed next.

The release adds direct navigation between the event and its bank projection, distinguishes free balance from future reservations, and starts the product-education layer with contextual guides and actionable empty states. No API or schema change is required; Flyway remains V47.

## Previous release: v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix


GitHub Actions confirmed Maven 626/626, then found two stale Playwright expectations that still treated absence-owned FIFO usages as independently editable Overtime rows. v27.31.2 aligns the browser flows with the released ownership model: an intentional retired direct-usage probe runs through Playwright APIRequestContext, linked usages remain read-only in Overtime, and the owning absence is edited or deleted through Unified Absence Composer.

Production JavaScript, API behavior, PostgreSQL, FIFO, Payroll and Flyway V47 are unchanged.

## Previous release: v27.31.1 — Canonical Absence Static Contract Alignment Hotfix

GitHub Actions confirmed that the v27.31.0 production sources compile, then found five historical Maven source-string assertions that still described the retired frontend shape. v27.31.1 aligns those contracts with direct coverage serialization, `HOURS_ONLY`, explicit legacy-promotion functions and absence-owned linked usages.

Production JavaScript, API behavior, PostgreSQL, FIFO, Payroll and Flyway V47 are unchanged.

## Previous release: v27.31.0 — Canonical Absence Ledger & Legacy Retirement

DutyLog now has one canonical write model for all absences. Vacation, overtime-backed time off, sick leave, unpaid leave and custom categories are created and edited through Unified Absence Composer. The Overtime workspace remains the journal for earned hours, FIFO allocations, used hours and remaining balance, but no longer creates a detached manual usage.

Old MANUAL usages can be previewed and promoted into TIME_OFF absences in place. Exact planned-shift matches become full-day absences; other durations become the honest transition state `HOURS_ONLY — Интервал не указан`. Existing usage IDs and FIFO allocation rows are preserved.

Forward-only Flyway V47 extends the released V42 absence coverage/shape constraints to allow `HOURS_ONLY` rows without inventing start/end times. It does not rewrite existing data; Payroll, Unified Ledger and closed-period protection remain unchanged.

## Previous release: v27.30.2 — Today Overtime Journal Contract Hotfix

This hotfix aligns the Today Dashboard source contract with the released v27.30.1 behavior: the overtime card action is **Журнал** and opens the Overtime workspace, while credit creation remains available from global Quick Add and the Overtime workspace. Production routing was already correct; only the stale Maven assertion and its focused regression coverage changed.

No API, schema, Payroll, Unified Ledger, absence, calendar-projection or production JavaScript behavior changes. Flyway remains V46.

## Previous release: v27.30.1 — Unified Absence Quick Access Integration

DutyLog exposes the unified absence composer from the places where users naturally start an action. The global plus menu opens a neutral absence flow, the Today dashboard has a direct current-date shortcut, and the Overtime workspace retains its contextual `TIME_OFF / OVERTIME_BANK` entry. The former Today direct overtime-credit shortcut was intentionally replaced; credit creation remains available from Quick Add and Overtime.

## Previous release: v27.30.0 — Unified Absence Composer & Calendar Projection

DutyLog now has one absence flow for vacation, overtime-backed time off, sick leave, unpaid leave and custom categories. The existing Vacation Planner form is reused as a modal or workspace editor, so every entry point reaches the same validation, balance preview, approval state and calendar projection.

Full-day absence becomes the factual state while preserving the planned shift underneath. Partial absence remains an interval over the shift. Vacation allowance and the FIFO overtime bank are enforced by their existing canonical services; sick and unpaid leave consume no balance. New time-off creation from Overtime now creates a linked absence instead of a detached raw usage.

Flyway remains V46. Payroll, Unified Ledger posting, approval workflow and API payloads remain compatible.

The dirtied Telegram detached-owner integration context now uses its own H2 database, so arbitrary IntelliJ test ordering cannot drop the shared `testdb` schema used by later controller tests.

## Previous stabilization: v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix

`v27.29.3` fixes the remaining Workspace Studio browser failure after v27.29.2 made the inherited Today cards visible. The frontend already moved Tasks above Shift, but the profile sanitizer forced Shift back to index zero when autosave returned from `/api/profile`.

The server now preserves an explicit allowed Today-card order and only inserts the mandatory Shift card when it is missing. The strict browser fixture, Payroll, Unified Ledger, PostgreSQL and Flyway V46 remain unchanged.

## Previous stabilization: v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix

`v27.29.2` made Custom Workspace inherit the active preset's Today cards together with its navigation, so Shift Worker keeps Shift, Overtime, Tasks and Important before the user starts editing.

## Previous stabilization: v27.29.1 — Theme Package Token Scope Contract Hotfix


`v27.29.1` repairs the only Maven failure in the Workspace Studio release. The production registry already emitted the correct `tokenScope` selector template `html[data-ui-theme="${id}"]`; the Java static contract mistakenly searched the JavaScript source for literal backslashes that existed only for Java escaping.

The hotfix corrects the assertion and adds a focused regression guard. Workspace Studio runtime, themes, layouts, profile persistence, Payroll, PostgreSQL and Flyway V46 are unchanged.

# v27.29.0 — Workspace, Layout & Theme Studio

DutyLog UI Core now has a real personalization studio instead of only fixed presets. Users can create a custom workspace, order and hide primary routes, order and hide optional Today cards, choose Sidebar or Mobile Flow layouts, and tune calendar density and schedule-layer presentation.

The studio remains safe by construction: one DOM/business layer, whitelisted profile fields, no arbitrary CSS or JavaScript, mandatory critical routes/cards, pointer-free decorations and generated links to every enabled section outside the primary navigation.

Flyway remains V46. Payroll, approval, ledger and calendar business logic are unchanged.

# v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix

`v27.28.3` fixes the clean-PostgreSQL startup blocker discovered after the otherwise green v27.28.2 pipeline. Flyway V45 created the immutable snapshot hash as `CHAR(64)`, while the established JPA mapping validates it as `VARCHAR(64)`.

Forward-only V46 converts the column with `BTRIM(...)`, preserves the released V45 checksum, `NOT NULL` and the 64-character lowercase hexadecimal constraint, and does not change Payroll calculations or API behavior.

# v27.28.2 — Calendar Persistence Reload Readiness Hotfix

`v27.28.2` closes the final reload race left in the green Payroll line: calendar navigation now publishes a bounded readiness promise, and reload-sensitive persistence scenarios wait for calendar, ledger and network idle without weakening the strict browser-error fixture.

Runtime Payroll behavior, V45 and the 37-scenario product surface stay unchanged.

## Previous stabilization: v27.28.1 — Payroll Module Registry Contract Hotfix

- Repairs the single Maven failure in `PayrollFoundationContractTest`.
- The contract now validates the canonical `ModuleKeys.PAYROLL = "payroll"` key and the real `DutyLogModules` registration.
- Production Payroll logic, API, OpenAPI, PostgreSQL and Flyway V45 are unchanged.
- Automated baseline remains 116 Java test classes / 603 `@Test` methods / 37 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.28.0 — Payroll Foundation

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

DutyLog now has its first trusted money layer. A closed, integrity-checked month can be calculated from canonical Plan → Fact → Compensation data using one hourly rate, append-only additions/deductions and immutable versioned snapshots. Financial values are stored in minor units; the initial release deliberately excludes taxes and employer-specific coefficients.

Key entry points:

- UI: `#payroll`;
- read model: `GET /api/v1/payroll/periods/{yyyy-MM}`;
- settings: `PATCH /api/v1/payroll/settings`;
- adjustment: `POST /api/v1/payroll/adjustments`;
- immutable calculation: `POST /api/v1/payroll/periods/{yyyy-MM}/calculate`.

# v27.27.2 — Ledger Browser State & Visibility Hotfix

This release stabilizes the remaining browser state boundaries after the v27.27.1 workflow hotfix.

- Vacation Planner refreshes its overtime-backed balance whenever the route opens.
- Timezone save exposes a full readiness promise before reload-sensitive scenarios continue.
- Expected `409 PERIOD_CLOSED` console noise is consumed only for explicitly marked requests.
- Overtime month filters and responsive ledger selectors are deterministic at month boundaries.
- Automated baseline: 114 Java test classes / 600 `@Test` methods / 36 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.27.1 — Ledger Workflow Browser Contract Hotfix

- Overtime now refreshes its ledger projections whenever the route opens after Vacation Planner changes.
- Integrity reconciliation and time-compensation reads are serialized instead of racing over FIFO repair.
- Browser tests wait for real ledger/application readiness, mark only intentional `409 PERIOD_CLOSED`, and use current-month timezone dates.
- Posted-compensation E2E scenarios explicitly use `APPROVED`; strict unexpected HTTP/console failure detection remains enabled.
- API, OpenAPI, database and Flyway remain at V44.
- Automated baseline: 113 Java test classes / 599 `@Test` methods / 36 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.27.0 — Ledger Integrity & Approval Workflow

- Gives absences a real lifecycle from draft through submission, approval, cancellation and completion.
- Reserves compensatory hours before approval and posts them only when the absence becomes final.
- Records reversals and late corrections in an append-only audit trail.
- Adds integrity reconciliation, closeable accounting periods and explicit factual work intervals.
- Closed months freeze payroll-affecting planned shifts across day edits, mobile sync, bulk fill and schedule templates while still allowing notes and day markers.
- Produces a stable payroll-ready time snapshot without calculating money yet.
- Automated baseline advances to 112 Java test classes / 598 `@Test` methods / 36 Playwright scenarios.

> Previous release: **v27.26.2 — Canonical Lineage Recovery**.
>
> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.26.2 — Canonical Lineage Recovery

- Reunifies the accidentally split DutyLog history into one forward-only canonical release.
- Restores V41 External Calendar Sync, V42 Absence & Time-Off and V43 Unified Time & Compensation Ledger on top of the currently deployed workspace-route line.
- Preserves the consolidated UTF-8/browser-boot calendar-sync hardening and the real mobile modal-panel E2E route.
- Keeps the canonical Tasks route contract: hidden workspace tabs are never force-clicked, and module state is asserted on `#view-tasks`.
- Adds a lineage integrity contract so future packaging cannot silently lose V41–V43 or the plan/fact/compensation stack.
- Automated baseline advances to 110 Java test classes / 592 `@Test` methods / 35 Playwright scenarios.

> Previous stable advanced release: **v27.26.1 — Absence Request Constructor Compile Hotfix**.
>
> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.26.1 — Absence Request Constructor Compile Hotfix

- Repairs Maven `testCompile` after `AbsencePeriodCreateRequest` gained the explicit `compensationPolicy` argument.
- Moves four stale Vacation Planner fixtures from the removed nine-argument shape to the full ten-argument contract.
- Preserves `OVERTIME_BANK` semantics for partial, full-day and insufficient-balance scenarios.
- Adds Java/static and release-gate protection against returning to the stale constructor shape.
- Keeps runtime behavior, HTTP API, OpenAPI, PostgreSQL and Flyway V43 unchanged.
- Automated baseline advances to 110 Java test classes / 591 `@Test` methods / 35 Playwright scenarios.

> Previous product release: **v27.26.0 — Unified Time & Compensation Ledger**.
>
> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.25.2 — Absence Experience Frontend Contract Hotfix

- Aligns the stale Vacation Planner Java/static contract with the accepted plan/fact frontend implementation.
- Protects the bounded Week agenda, timed partial-absence projection and full-day all-day rail separately.
- Does not change production JavaScript, API, database schema or Flyway V42.
- Automated baseline advances to 109 Java test classes / 581 `@Test` methods / 34 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.25.1 — Absence Preview Lambda Compile Hotfix

- Fixes the Java compiler error in the absence preview loop without changing product behavior.
- Each loop iteration snapshots its `LocalDate` before the overlap-search lambda, keeping the preview date stable and effectively final.
- Adds regression protection against capturing the incremented loop variable directly.
- Keeps the v27.25.0 plan/fact model, API, Flyway V42 and 34 Playwright scenarios unchanged; Java baseline advances to 580 tests.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.25.0 — Absence & Time-Off Overhaul

- Separates the planned shift from the factual day status without deleting or rewriting calendar rows.
- Full-day vacation, time off, sickness and custom absences can visually replace the shift while preserving it for norms, salary and statistics.
- Partial time off keeps the shift visible, stores an exact local interval and charges an independent minute balance.
- Adds `VACATION_DAYS`, `TIME_OFF_HOURS` and `NONE` balance policies, plan/fact day details, monthly absence summaries and timed `.ics` projection.
- Flyway advances to V42; automated baseline advances to 109 Java test classes / 579 `@Test` methods / 34 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix

- Aligns the new calendar comfort Playwright scenario with the real mobile modal-panel lifecycle.
- After `↺ Сегодня` selects today in Month mode, the scenario closes `#panel` through `#pClose` before using month navigation.
- Keeps the blocking backdrop, contextual Today behavior, API and Flyway V41 unchanged.
- Automated baseline remains 108 Java test classes / 569 `@Test` methods / 33 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.24.0 — Calendar Comfort & Correctness

- Contextual «Сегодня» return instead of a permanently hidden mobile control.
- Selected calendar date becomes the default important-day date; reminder checkboxes follow the design system.
- Overnight Today cards separate compact time from the two-date range.
- Refresh keeps the existing month visible, adds a calm status and captures bounded in-memory load metrics.
- Multiple schedule layers use compact accessible pills instead of verbose controls.
- Flyway remains V41; automated baseline: 108 Java test classes / 569 `@Test` methods / 33 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.23.2 — Calendar Sync Runtime Boot Hotfix

- Removes the uncaught `localDateKey is not defined` browser error from calendar-sync range initialization.
- Uses DutyLog's canonical local `keyOf(...)` date helper, preserving floating dates across timezones.
- Adds Java/static release guards so the undefined helper cannot return unnoticed.
- Keeps External Calendar Sync API, token security, nginx hardening and Flyway V41 unchanged.
- Automated baseline: 107 Java test classes / 564 `@Test` methods / 32 Playwright scenarios.

# v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix

- Decodes MockMvc JSON explicitly as UTF-8 in the calendar subscription lifecycle test.
- Protects the real `prefix…suffix` token-hint contract without changing production responses.
- Keeps External Calendar Sync runtime, API, nginx hardening and Flyway V41 unchanged.
- Automated baseline remains 107 Java test classes / 563 `@Test` methods / 32 Playwright scenarios.

# v27.23.0 — External Calendar Sync

- Export one important event or a selected date range as standards-compliant UTF-8 `.ics`.
- Subscribe Google Calendar, Outlook, Apple Calendar or another compatible client to a private read-only DutyLog feed.
- Feed links use a 256-bit bearer secret shown only when issued; DutyLog stores only SHA-256 plus a short hint.
- Rotation immediately invalidates the old link; revocation removes access without deleting calendar data.
- Flyway V41; automated baseline: 107 Java test classes / 563 `@Test` methods / 32 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.22.2 — Workspace Route E2E Navigation Hotfix

- Updated stale Playwright task flows to use the shared workspace-route `openView()` helper.
- Tasks stay outside Shift Worker primary navigation by design; the hotfix does not re-add the tab or alter runtime behavior.
- Module toggling is asserted on `#view-tasks`, independently from workspace placement.
- Runtime behavior, API, Flyway V40 and the 103 / 544 / 31 regression baseline remain unchanged.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.22.1 — Vacation Planner Frontend Contract Hotfix

- Aligned three stale Java/static contracts with the accepted Vacation Planner runtime.
- Shift Worker now explicitly protects the `vacation` route and actual Today widget order.
- Calendar composition now checks the real all-day absence path instead of an invented CSS class.
- Module persistence derives its expected count from the canonical module registry instead of a hardcoded pre-vacation number.
- Runtime behavior, API, Flyway V40 and the 103 / 544 / 31 regression baseline remain unchanged.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.22.0 — Vacation Planner

- Added a separate vacation/absence domain instead of encoding leave as a shift.
- Added annual allowance, carryover, configurable work year and calendar-day / Monday-Friday counting.
- Added 14 / 28 / 35 day presets, conflict-aware preview, hard overlap protection and work-year allowance validation.
- Added owner-scoped absence types, calendar projections and a responsive unified-shell planner.
- Flyway advances to V40; regression baseline advances to 103 Java test classes, 544 `@Test` methods and 31 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# v27.21.2 — Schedule Accordion E2E Selector Hotfix

- Added an ID-specific accordion helper for browser scenarios where one product module owns multiple day panels.
- Routed the schedule-template E2E through `#accSched` instead of the ambiguous `[data-day-module="shifts"]` selector.
- Preserved strict locator behavior: duplicate module surfaces still fail unless the scenario names the intended accordion.
- No production runtime, API, database or Flyway changes; the baseline remains 100 Java test classes, 525 `@Test` methods and 30 Playwright scenarios.

> Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**.

# DutyLog

Current release: **v27.34.2 — Vue Browser Runtime Bundle Hotfix**

DutyLog — приложение для учёта смен, переработок, отгулов, задач, важных событий, заметок и напоминаний. Оно объединяет календарь смен, журнал переработок, задачи дня, Markdown-заметки, Telegram-бота и PWA-интерфейс в одном Spring Boot backend.

## Историческая точка восстановления: v27.26.2 — Canonical Lineage Recovery

**v27.26.2** восстановил единую каноническую линию после случайного возврата ветки к раннему v27.22.x/v27.23.x baseline. Релиз объединяет актуальный workspace-route контракт, External Calendar Sync, Calendar Comfort, Absence & Time-Off Overhaul и Unified Time & Compensation Ledger, сохраняя Flyway V41–V43 без дублирования.

### Предыдущий hotfix: v27.23.2 — Calendar Sync Runtime Boot Hotfix

**v27.23.2** исправил browser-runtime инициализацию диапазона `.ics`, не меняя API, nginx-защиту и Flyway V41.

### Базовый продуктовый релиз: v27.23.0 — External Calendar Sync

**v27.23.0** добавляет безопасный исходящий календарный контур: разовый `.ics`-экспорт и приватную read-only подписку со строгой ротацией и отзывом токена. Поставляемые nginx-конфиги отключают access log для точного `/calendar-feed.ics`, чтобы bearer URL не попадал в журналы edge-прокси.

### Предыдущий hotfix: v27.22.2 — Workspace Route E2E Navigation Hotfix

**v27.22.2** перевёл устаревшие browser-сценарии задач на общий workspace-route `openView(page, "tasks")`, не возвращая Tasks в основную навигацию Shift Worker и проверяя module state на самой view.

### Предыдущий hotfix: v27.22.1 — Vacation Planner Frontend Contract Hotfix

**v27.22.1** синхронизировал три Java/static контракта с принятым runtime Vacation Planner.

### Базовый продуктовый релиз: v27.22.0 — Vacation Planner

**v27.22.0** добавляет отдельный планировщик отпусков и отсутствий:

- отпуск не является сменой и не меняет рабочие часы, статистику смен или FIFO переработок;
- годовая норма, перенос остатка, начало рабочего года и способ подсчёта настраиваются пользователем;
- доступны шаблоны 14 / 28 / 35 дней и произвольный период;
- preview заранее показывает списываемые дни, пересечения со сменами, другие отсутствия и остаток;
- пересечение отсутствий и превышение лимита блокируются с понятным кодом ошибки; конкурентные записи сериализуются по пользователю, а изменение правил проверяет все сохранённые рабочие годы;
- больничный, отпуск без содержания и пользовательские типы существуют в той же независимой модели;
- календарь показывает отсутствие в месяце, неделе, почасовом дне и панели выбранной даты.

Схема обновлена до **Flyway V40**. Автоматическая база: **103 Java-тестовых класса, 544 `@Test` метода и 31 Playwright browser scenario**.

### Предыдущий hotfix: v27.21.2 — Schedule Accordion E2E Selector Hotfix

**v27.21.2** устраняет последнее падение полного Playwright gate после Schedule Templates & Calendar Layers:

- общий `openDayModule()` остаётся строгим и по-прежнему ловит неоднозначные module-key селекторы;
- новый `openDayModuleById()` открывает ровно один указанный `<details>`;
- сценарий шаблонов графика обращается к `#accSched`, а не к двум секциям модуля `shifts` одновременно;
- `.first()` не используется, поэтому тест не может случайно открыть обычный редактор смен вместо шаблонов.

Production-код и схема не менялись. Flyway остаётся **V39**, автоматическая база — **100 Java-тестовых классов, 525 `@Test` методов и 30 Playwright browser scenarios**.

### Предыдущий hotfix: v27.21.1 — Schedule Templates Frontend Contract Alignment Hotfix

**v27.21.1** синхронизировал четыре статических frontend-контракта с data-layer fresh reload, server-owned preview/apply, реальными async API methods и `tplPreview`.

### Базовый продуктовый релиз: v27.21.0 — Schedule Templates & Calendar Layers

**v27.21.0** отделяет повторяемые правила планирования от фактических записей календаря:

- пользовательские шаблоны графиков поддерживают циклы от 1 до 64 шагов, выравнивание от даты начала или по дням недели и пять неизменяемых встроенных пресетов;
- preview заранее показывает `APPLY / OVERWRITE / SAME / SKIP_CONFLICT`, а безопасное применение по умолчанию не трогает занятые дни;
- явный overwrite меняет только смену и сохраняет остальные данные дня;
- календарные слои отображают чужой или вспомогательный повторяющийся график как read-only проекцию в Month / Week / Day;
- слой хранит имя, цвет, IANA timezone, шаблон, anchor, границы дат, порядок и серверную видимость;
- пользовательскую смену нельзя удалить, пока её использует хотя бы один шаблон графика.

Схема обновлена до **Flyway V39**. Автоматическая база: **100 Java-тестовых классов, 525 `@Test` методов и 30 Playwright browser scenarios**.

### Предыдущий релиз: v27.20.2 — Calendar Day Details E2E Flow Hotfix

**v27.20.2** закрыл последнее падение полного Playwright gate после Notes & Important Events Next: сценарий использует реальную кнопку «Все детали дня» вместо прямого обращения к скрытому Month-only модулю.

### Предыдущий hotfix: v27.20.1 — Important Event Modal & Offline Notes E2E Hotfix

**v27.20.1** закрыл single-modal lifecycle важных событий, mode-aware выбор даты и полный offline→sync контракт существующей заметки.

### Базовый продуктовый релиз: v27.20.0 — Notes & Important Events Next

**Заметки** получили поиск по заголовкам и Markdown-тексту, переход к точному дню/заметке, экспорт и безопасное оффлайн-редактирование существующих записей через синхронизируемую очередь.

**Важные события** получили режимы `IMPORTANT_DATE / EVENT / PERIOD`, all-day/timed семантику, исходную IANA-зону, canonical instants, read-first карточки, полноценный редактор, почасовой календарь и индивидуальные напоминания.

### Предыдущий hotfix: v27.19.4 — Ghost Button Transition E2E Stabilization Hotfix

**v27.19.4** стабилизировал semantic alpha-проверку Ghost/Outline в Chromium без изменения production CSS.

### Предыдущий hotfix: v27.19.3 — Task Deadline Validation E2E Contract Hotfix

**v27.19.3** синхронизировал planned-interval deadline validation с browser-контрактом:

- timed-задача сохраняет точную ошибку «Дедлайн не может быть раньше окончания запланированного интервала.»;
- all-day/date fallback остаётся отдельным контрактом;
- production-валидация, Tasks API и данные не менялись;
- `release-check.sh` защищает planned-interval E2E-сообщение.

### Предыдущий hotfix: v27.19.2 — Frontend Asset Contract Stability Hotfix

**v27.19.2** стабилизировал статические frontend-контракты после cache-busting обновлений:

- Today, UI Core, Calendar Experience и Design System по-прежнему проверяют точные имена ассетов и порядок загрузки;
- тесты больше не зашивают конкретный номер релиза в `?v=...`;
- `release-check.sh` отклоняет новые hardcoded semantic versions в `*FrontendContractTest.java`;
- runtime-ассеты, Service Worker, backend metadata и smoke-проверки строго привязаны к версии релиза;
- API, бизнес-логика задач и схема данных не менялись.

### Предыдущий hotfix: v27.19.1 — Task Board Date Range Compatibility Hotfix

**v27.19.1** сохранил обратную совместимость фильтров доски задач и отделил её от нового планового диапазона:

- `from` / `to` снова фильтруют по дедлайну, а без дедлайна — по дате задачи;
- `scheduledFrom` / `scheduledTo` фильтруют по пересечению запланированного интервала;
- экран задач использует плановый диапазон для полей дат и пресета «этот месяц»;
- старые Web/API/mobile-вызовы не меняют смысл после обновления;
- overnight-интервалы попадают в каждый пересекаемый плановый день.

### Базовый продуктовый релиз: v27.19.0 — Tasks & Inbox Next


**v27.19.0** превращает задачи из списка сроков в полноценный план дня:

- запланированное время отделено от дедлайна и напоминания;
- доступны весь день, точечная задача, точные интервалы и быстрые длительности 15/30/45/60/90/120 минут;
- интервалы сохраняют абсолютные instants и исходный IANA timezone, а all-day задачи остаются плавающими датами;
- ночная задача появляется на каждом покрытом дне и корректно делится на почасовом календаре;
- read-first карточка показывает план крупно, отдельно — проект, дедлайн, напоминание и исходную проекцию;
- проекты участвуют в metadata, подсказках, поиске, chips и фильтрах доски;
- Inbox получил поиск по локальной очереди и серверным записям;
- мобильный редактор получил безопасную bottom-sheet компоновку и крупные duration presets.

Схема обновлена до **Flyway V37**. Автоматическая база: **97 Java-тестовых классов, 507 `@Test` методов и 28 Playwright browser scenarios**.

Предыдущие продуктовые релизы:

- **v27.18.3 — UI Settings & Button Variants Quality Hotfix** — исправил Theme palette reset и развёл Ghost/Outline;
- **v27.18.2 — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix** — синхронизировал usage snapshot и стабилизировал выбор дня;
- **v27.18.1 — Overtime Next E2E Contract Hotfix** — выровнял responsive delete selectors и дневные/месячные chart keys;
- **v27.18.0 — Overtime Next** — добавил balance-first сводку, периоды, тренд, FIFO-очередь, desktop-таблицу и мобильные карточки;
- **v27.17.6 — Classic Sunset** — удалил второй пользовательский shell и оставил единый UI Core;
- **v27.17.5 — UI Core E2E Accordion Hotfix** — восстановил state-aware browser gate;
- **v27.17.4 — UI Core & Workspace Foundation** — добавил UI Core v1, workspaces, layouts, независимые темы и палитры;
- **v27.17.3 — Java Contract Build Gate Hotfix** — вернул зелёный `testCompile` и добавил быстрый `javac`-gate;
- **v27.17.2 — Calendar Timeline Readability Hotfix** — исправил читаемость коротких timed-events на desktop;
- **v27.17.1 — Calendar & Notes Quality Hotfix** — исправил container-aware заметки, all-day rail и минутную точность срока;
- **v27.17.0 — Calendar Mobile Experience** — добавил Month / Week / Day и почасовой день;
- **v27.16.3 — Time Settings Transaction Hotfix** — сериализовал ручное и автоматическое применение времени;
- **v27.16.2 — Next Route & Time Settings E2E Hotfix** — выровнял E2E с Today-first навигацией;
- **v27.16.1 — Today Runtime & Repository Truth Hotfix** — устранил `openQuickActions` load-order crash;
- **v27.16.0 — Today Dashboard** — ежедневный рабочий экран;
- **v27.15.0 — Design System & Mobile Shell Foundation** — заложил дизайн-систему и переходный Classic fallback;
- **v27.14.2 — Calendar Notes Persistence E2E Hotfix** — закрепил новый Notes CRUD в календарной регрессии;
- **v27.14.1 — Mobile Notes Tombstone Hotfix** — сохранил versioned tombstone Android API v1;
- **v27.14.0 — Multiple Daily Notes** — добавил независимые заметки, pin/reorder/delete и offline snapshot.

## История временного фундамента

- v27.7.0 — Time Foundation
- v27.7.1 — Task & Ledger Layout Hotfix
- v27.8.0 — Zoned Work Intervals
- v27.8.1 — Timezone Projection Refresh Hotfix
- v27.9.2 — Overtime Ledger Integrity Hotfix
- v27.9.3 — Overtime Preflight Integrity Hotfix
- v27.9.4 — Overtime Split Projection Contract Hotfix
- v27.9.0 — Overtime Interval Engine
- v27.11.0 — Shift Occurrences & Calendar Projection
- v27.11.3 — Shift Template & Reminder Timezone Hotfix
- v27.11.4 — Task Deadline & Reminder Timezone Hotfix
- v27.12.0 — Zoned Daily Projection Engine
- v27.12.1 — Midnight Projection Contract Hotfix
- v27.13.0 — Temporal Consistency & Legacy Cleanup
- v27.10.0 — Task Details
- v27.11.1 — CI & Contract Hotfix
- v27.11.2 — E2E Stability Hotfix

## Возможности

- Календарь смен с неизменными абсолютными экземплярами, timezone-проекцией, переносом на соседний день и типами `Дневная`, `Ночная`, `Выходной`.
- Модульный режим: пользователь может включать и выключать Notes, Tasks, Overtime, Important dates, Notifications, Telegram и Scenarios без удаления данных.
- Первый запуск: новый пользователь выбирает нужные модули через спокойный onboarding, а не сразу попадает в перегруженный интерфейс.
- Шаблоны графиков: пять встроенных пресетов, пользовательские циклы до 64 шагов, preview, безопасное применение и явное перезаписывание конфликтов.
- Календарные слои: read-only графики других людей или вспомогательные расписания с цветом, IANA timezone, границами и общей серверной видимостью.
- Несколько независимых Markdown-заметок на каждый день с названиями, закреплением, сортировкой, полноэкранным редактором, живым превью и ZIP-экспортом для Obsidian/резервной копии.
- Единый DutyLog UI Core: адаптивная фирменная шапка, нижняя мобильная навигация, workspaces, layouts, независимые темы и палитры.
- Персонализация: светлая/тёмная/системная тема, акцентный цвет и emoji-маркеры дней без хранения картинок.
- Задачи дня с all-day/point/interval-планированием, точной длительностью, проектами, независимыми дедлайнами и напоминаниями, read-first деталями, категориями, тегами, приоритетами и одноуровневыми подзадачами.
- Универсальный быстрый ввод: запись во «Входящие», заготовка задачи, дополнение заметки на сегодня или форма важного дня.
- Компактный сворачиваемый лоток «Входящие» с offline-очередью и преобразованием записи в задачу.
- Важные даты: разовые, ежемесячные и ежегодные события.
- Журнал переработок и отгулов с поминутным FIFO, точными исходными интервалами и provenance каждого списания.
- Расчёт переработки по интервалу: начало, конец, обед и вычитаемый план; старые local-only записи можно безопасно привязать к IANA-зоне через мастер миграции.
- Быстрые сценарии для типовых переработок.
- Уведомления в браузере и Telegram.
- Telegram-бот с видимым меню команд, постоянной клавиатурой быстрых действий и timezone-aware сводками.
- Профиль пользователя, смена пароля и управление мобильными сессиями.
- Версионированный Android API v1 с Bearer-токенами, OpenAPI, idempotency keys и optimistic conflict detection.
- Служебная диагностика состояния приложения, сервера, базы данных и Telegram-интеграции в отдельном профиле администратора.
- Скрипты резервного копирования и восстановления PostgreSQL.
- Staging/production CI/CD с immutable GHCR images, проверенными backup, health/smoke gates и application rollback.

## Стек

- Java 17
- Spring Boot 3.3.5
- Spring Web, Data JPA, Security, Validation
- PostgreSQL + Flyway для production
- H2 для локальной разработки
- HTML/CSS/JavaScript без frontend-фреймворка
- PWA: manifest, service worker, installable web shell
- Docker Compose
- Telegram Bot API через long polling

## Архитектура

Основной backend — монолит Spring Boot с чётким разделением по слоям:

```text
web/       HTTP-контроллеры и API
service/   бизнес-логика
model/     JPA-сущности
repo/      Spring Data repositories
telegram/  Telegram-бот, команды, привязка и доставка уведомлений
config/    безопасность, диагностика запросов, Bearer-auth
static/    PWA-интерфейс
```

Подробная схема модулей и границ ответственности описана в [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Быстрый локальный запуск

Нужны JDK 17+ и Maven.

```bash
mvn spring-boot:run
```

После запуска приложение доступно по адресу:

```text
http://localhost:8080
```

В dev-режиме используется H2-база в папке `./data`.

## Запуск через Docker Compose

Для локального Docker-запуска:

```bash
cp .env.example .env
docker compose up -d --build
```

Для VPS используется CI/CD runtime за уже установленным системным Nginx. Приложение публикуется только на loopback:

```text
stage.yaruga-trophy.ru   -> nginx -> 127.0.0.1:18082
dutylog.yaruga-trophy.ru -> nginx -> 127.0.0.1:18083
```

Первичная настройка описана в [`docs/HOST_NGINX_DEPLOYMENT_V27.2.30.md`](docs/HOST_NGINX_DEPLOYMENT_V27.2.30.md). Старый `docker-compose.prod.yml` с Caddy оставлен только как legacy/manual вариант и не используется активными workflow.

Безопасная остановка:

```bash
docker compose down
```

Команда ниже удаляет Docker volumes и может стереть базу данных:

```bash
docker compose down -v
```

## Резервные копии

Создать и проверить backup PostgreSQL:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/backup-postgres.sh
```

Проверить свежесть, checksum и читаемость последней копии:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/check-backup-freshness.sh
```

Безопасно отрепетировать восстановление в отдельном временном PostgreSQL:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/restore-drill.sh
```

Настоящее восстановление выбранного окружения выполняется только вручную с явным подтверждением:

```bash
CONFIRM_RESTORE=RESTORE DUTYLOG_ENV_FILE=.env \
  bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

Ежедневный systemd timer устанавливается отдельным скриптом. Подробный runbook: [`docs/BACKUP_RESTORE_OPERATIONS_V27.5.0.md`](docs/BACKUP_RESTORE_OPERATIONS_V27.5.0.md).

## Production-профиль

В production используется PostgreSQL и Flyway-миграции. Hibernate работает в режиме валидации схемы, поэтому изменения БД должны оформляться новыми файлами миграций в `src/main/resources/db/migration`.

Для боевого запуска подготовлены:

- `deploy/compose/docker-compose.deploy.yml` — отдельный staging/production runtime с loopback-портами;
- `deploy/nginx/dutylog-staging.conf.example` и `dutylog-production.conf.example` — маршруты общего системного Nginx;
- `.github/workflows/deploy-staging.yml` и `deploy-production.yml` — автоматическая доставка immutable images;
- `deploy/env/.env.staging.example` и `.env.production.cicd.example` — серверные шаблоны окружений;
- `deploy/scripts/local-smoke-test.sh` — проверка контейнера до DNS/TLS/Nginx;
- `docker-compose.prod.yml` и `deploy/caddy/*` — прежний legacy/manual вариант, не активный CI/CD;
- `docs/PRODUCTION_RUNBOOK.md` — первый запуск, обновление, откат и emergency backup;
- `docs/SECURITY_CHECKLIST.md` — чеклист безопасности.

Пароли в compose настроены fail-hard: пустые production-пароли не должны приводить к тихому запуску небезопасной конфигурации.

Перед первым запуском на VPS можно прогнать production preflight:

```bash
./deploy/scripts/check-production-env.sh
```

## Telegram

Telegram-бот работает внутри основного backend. Для включения задайте:

```env
DUTYLOG_TELEGRAM_ENABLED=true
DUTYLOG_TELEGRAM_BOT_TOKEN=123456:telegram-token
DUTYLOG_TELEGRAM_BOT_USERNAME=your_bot_username
DUTYLOG_TELEGRAM_POLLING_ENABLED=true
DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED=true
```

Подключение пользователя выполняется через одноразовый код в профиле DutyLog:

```text
/start DL-123456
```

Команды бота:

```text
/today       что сегодня
/tomorrow    что завтра
/week        ближайшие 7 дней
/tasks       открытые задачи
/task        добавить задачу
/done        закрыть задачу
/ppr         начислить переработку
/timeoff     списать отгул
/balance     остаток переработок
/help        помощь
```

## Безопасность

- Web-интерфейс работает через `JSESSIONID` и CSRF-защиту.
- Изменяющие web-запросы отправляют `X-XSRF-TOKEN`.
- `/api/mobile/**` работает отдельной stateless security chain и принимает только `Authorization: Bearer <accessToken>`; browser `JSESSIONID` для неё не подходит.
- Production-регистрация по умолчанию закрыта, а login/registration/mobile-login ограничены app-level rate limiter.
- Структурированные `SECURITY_AUDIT` события не содержат пароли, токены, Telegram-коды или заметки.
- Notes export owner-scoped, bounded, streamed and marked `Cache-Control: no-store`.
- Refresh tokens хранятся только в виде SHA-256-хэшей.
- Пароли пользователей хранятся через BCrypt.
- Диагностический endpoint не раскрывает секреты: Telegram token, пароли и URL базы данных не отдаются.

## Документация

- [`CHANGES.md`](CHANGES.md) — история версий.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — архитектура приложения.
- [`docs/API.md`](docs/API.md) — HTTP API.
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) — Git-история, теги и откаты.
- [`docs/CICD.md`](docs/CICD.md) — ветки `test`/`main`/`master`, GitHub Environments и автоматический deploy.
- [`docs/STAGING.md`](docs/STAGING.md) — изоляция и безопасный сброс тестовой среды.
- [`docs/MIGRATION_SAFETY.md`](docs/MIGRATION_SAFETY.md) — правила Flyway и защита production-данных.
- [`docs/BACKUP.md`](docs/BACKUP.md) — резервные копии и восстановление PostgreSQL.
- [`docs/DEPLOY.md`](docs/DEPLOY.md) — запуск на VPS через Docker Compose.
- [`docs/PRODUCTION_RUNBOOK.md`](docs/PRODUCTION_RUNBOOK.md) — эксплуатация, обновление и откат на VPS.
- [`docs/PRODUCTION_LAUNCH.md`](docs/PRODUCTION_LAUNCH.md) — короткий сценарий первого запуска на VPS.
- [`docs/SECURITY_CHECKLIST.md`](docs/SECURITY_CHECKLIST.md) — чеклист безопасности перед публикацией.
- [`docs/SECURITY_REVIEW.md`](docs/SECURITY_REVIEW.md) — обзор security hardening текущей стабилизации.
- [`docs/SECURITY_CONSOLIDATION.md`](docs/SECURITY_CONSOLIDATION.md) — сводка закрытых security-находок v27.0-rc4.
- [`docs/NOTES_EXPORT.md`](docs/NOTES_EXPORT.md) — формат и ограничения ZIP-экспорта заметок.
- [`docs/SUPPLY_CHAIN.md`](docs/SUPPLY_CHAIN.md) — Dependabot и правила обновления зависимостей/образов.
- [`docs/ADMIN_BOOTSTRAP.md`](docs/ADMIN_BOOTSTRAP.md) — безопасное создание стартового администратора через env.
- [`docs/REGISTRATION_SETTINGS.md`](docs/REGISTRATION_SETTINGS.md) — управление публичной регистрацией из админки.
- [`docs/USER_ROLES.md`](docs/USER_ROLES.md) — пользователи, роли ADMIN/USER и будущий задел FREE/PAID/VIP.
- [`docs/PERSONALIZATION.md`](docs/PERSONALIZATION.md) — темы, акцентный цвет и Unicode emoji-маркеры дней.
- [`docs/VPS_CHECKLIST.md`](docs/VPS_CHECKLIST.md) — чеклист боевого запуска.
- [`docs/ANDROID_API_PLAN.md`](docs/ANDROID_API_PLAN.md) — мобильный API.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — идеи развития.
- [`docs/PRODUCT_COPY.md`](docs/PRODUCT_COPY.md) — стиль пользовательских текстов.
- [`docs/OFFLINE_MODE.md`](docs/OFFLINE_MODE.md) — offline-режим, локальный снимок и очередь синхронизации.
- [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md) — ручная проверка web/PWA-монолита перед релизом и VPS-деплоем.
- [`docs/REGRESSION_TEST_BASELINE.md`](docs/REGRESSION_TEST_BASELINE.md) — карта ручных сценариев и автоматических regression-тестов, запуск `mvn verify` и JaCoCo.
- [`docs/CALENDAR_MOBILE_EXPERIENCE_V27.17.0.md`](docs/CALENDAR_MOBILE_EXPERIENCE_V27.17.0.md) — контракт месяца, недели и почасового дня.
- [`docs/TIME_SETTINGS_TRANSACTION_HOTFIX_V27.16.3.md`](docs/TIME_SETTINGS_TRANSACTION_HOTFIX_V27.16.3.md) — защита черновика формы и сериализация ручного/автоматического применения времени.
- [`docs/NEXT_ROUTE_TIME_SETTINGS_E2E_HOTFIX_V27.16.2.md`](docs/NEXT_ROUTE_TIME_SETTINGS_E2E_HOTFIX_V27.16.2.md) — выравнивание E2E с Today route и базовая отмена debounce.
- [`docs/TODAY_RUNTIME_HOTFIX_V27.16.1.md`](docs/TODAY_RUNTIME_HOTFIX_V27.16.1.md) — причина каскадного падения Playwright и контракт исправления forward-reference между frontend bundles.
- [`docs/SHIFT_OCCURRENCES_CALENDAR_PROJECTION_V27.11.0.md`](docs/SHIFT_OCCURRENCES_CALENDAR_PROJECTION_V27.11.0.md) — абсолютные экземпляры смен, перенос по локальным датам и миграция legacy-строк.
- [`docs/TASK_DETAILS_V27.10.0.md`](docs/TASK_DETAILS_V27.10.0.md) — read-first детали задачи, описание, owner-scoped GET и границы редактора.
- [`docs/OVERTIME_SPLIT_PROJECTION_CONTRACT_HOTFIX_V27.9.4.md`](docs/OVERTIME_SPLIT_PROJECTION_CONTRACT_HOTFIX_V27.9.4.md) — устойчивые номера частей split-отгула в ledger DTO и корректный midnight E2E-контракт.
- [`docs/OVERTIME_PREFLIGHT_INTEGRITY_HOTFIX_V27.9.3.md`](docs/OVERTIME_PREFLIGHT_INTEGRITY_HOTFIX_V27.9.3.md) — preflight-проверка отгулов до мутации и синхронизация CI-контрактов.
- [`docs/OVERTIME_LEDGER_INTEGRITY_HOTFIX_V27.9.2.md`](docs/OVERTIME_LEDGER_INTEGRITY_HOTFIX_V27.9.2.md) — атомарная пересборка FIFO, инварианты журнала и ясное удаление целого отгула.
- [`docs/OVERTIME_ALLOCATION_RENDERING_HOTFIX_V27.9.1.md`](docs/OVERTIME_ALLOCATION_RENDERING_HOTFIX_V27.9.1.md) — исправление runtime-рендера точных межсуточных списаний.
- [`docs/OVERTIME_INTERVAL_ENGINE_V27.9.0.md`](docs/OVERTIME_INTERVAL_ENGINE_V27.9.0.md) — поминутный FIFO, точные интервалы и мастер миграции legacy overtime.
- [`docs/TIMEZONE_PROJECTION_REFRESH_V27.8.1.md`](docs/TIMEZONE_PROJECTION_REFRESH_V27.8.1.md) — hotfix authoritative refresh после смены work/display timezone.
- [`docs/ZONED_WORK_INTERVALS_V27.8.0.md`](docs/ZONED_WORK_INTERVALS_V27.8.0.md) — контракт абсолютных смен, work/display-проекций и новых timezone-aware начислений переработки.
- [`docs/TASK_LEDGER_LAYOUT_HOTFIX_V27.7.1.md`](docs/TASK_LEDGER_LAYOUT_HOTFIX_V27.7.1.md) — контракт исправления карточек задач и действий журнала переработок.
- [`docs/TIME_FOUNDATION_V27.7.0.md`](docs/TIME_FOUNDATION_V27.7.0.md) — контракт рабочего/display времени, абсолютных моментов, DST и будущих рабочих интервалов.
- [`docs/TASK_POLISH_CONSISTENCY_V27.6.3.md`](docs/TASK_POLISH_CONSISTENCY_V27.6.3.md) — контракт релиза качества задач: сроки, open-first, прогресс, подзадачи и mobile polish.
- [`docs/TASK_SUBTASKS_V27.6.2.md`](docs/TASK_SUBTASKS_V27.6.2.md) — продуктовый и технический контракт одноуровневых подзадач.
- [`docs/RELEASE_CANDIDATE.md`](docs/RELEASE_CANDIDATE.md) — что проверено в v27.2.5 и как принимать RC.
- [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) — короткая пользовательская инструкция.
- [`docs/PRODUCTION_DEPLOY.md`](docs/PRODUCTION_DEPLOY.md) — пошаговый production deployment.
- [`docs/BACKUP_RESTORE.md`](docs/BACKUP_RESTORE.md) — резервное копирование и восстановление.
- [`docs/RELEASE_HARDENING.md`](docs/RELEASE_HARDENING.md) — фаза стабилизации, release gate и правила freeze.
- [`docs/CODE_CLEANUP.md`](docs/CODE_CLEANUP.md) — правила безопасной чистки кода во время стабилизации.
- [`docs/UX_RELEASE_POLISH.md`](docs/UX_RELEASE_POLISH.md) — UX-полировка релизной стабилизации.
- [`docs/UX_COMPACT_HOTFIX.md`](docs/UX_COMPACT_HOTFIX.md) — компактная правка экрана модулей и панели дня после UX-polish.
- [`docs/UX_CONSOLE_HOTFIX.md`](docs/UX_CONSOLE_HOTFIX.md) — скрытие технических деталей от обычных пользователей и чистка console-noise.
- [`docs/TEST_CONFIG_HOTFIX.md`](docs/TEST_CONFIG_HOTFIX.md) — правка `.properties`, тестовых ожиданий и cascade-зависимостей модулей.
- [`docs/ONBOARDING_TODAY_HOTFIX.md`](docs/ONBOARDING_TODAY_HOTFIX.md) — выделение выбранного onboarding-набора и более заметный текущий день.
- [`docs/DAY_HINT_DISMISS_HOTFIX.md`](docs/DAY_HINT_DISMISS_HOTFIX.md) — различение сегодняшнего и выбранного дня, закрываемая подсказка скрытых блоков.
- [`docs/UI_ALIGNMENT_TEST_HOTFIX.md`](docs/UI_ALIGNMENT_TEST_HOTFIX.md) — стабильное выравнивание правых controls в настройках и правка компиляции тестов.
- [`docs/NOTIFICATION_ADMIN_NAV_HOTFIX.md`](docs/NOTIFICATION_ADMIN_NAV_HOTFIX.md) — выравнивание уведомлений и навигация в админке.


## История контрольных точек

Ниже сохранены названия опубликованных релизов, на которые опираются regression-contracts и эксплуатационная документация:

- **v27.2.5 — Calendar day identity hotfix**
- **v27.2.10 — Task board status validation hotfix**
- **v27.2.11 — Task priority regression test correction**
- **v27.2.12 — Important dates regression suite**
- **v27.2.13 — Shift types and calendar patterns regression suite**
- **v27.2.14 — Quick scenarios and overtime API regression suite**
- **v27.2.15 — Structured module-disabled error envelope hotfix**
- **v27.2.16 — Profile and administration regression suite**
- **v27.2.17 — Admin test context bootstrap hotfix**
- **v27.2.18 — Mobile auth and sync lifecycle regression suite**
- **v27.2.19 — PostgreSQL migration and CI version hotfix**
- **v27.2.20 — Telegram bot regression and delivery hardening suite**
- **v27.2.21 — Telegram date validation and test harness hotfix**
- **v27.2.22 — Security infrastructure regression and auth hardening suite**
- **v27.2.23 — Security test contract and secret-safe error logging hotfix**
- **v27.2.24 — Coverage floor and startup/module regression suite**
- **v27.2.25 — Playwright browser E2E regression baseline**
- **v27.2.26 — Playwright selector, accordion and line-ending hotfix**
- **v27.2.27 — Playwright marker accordion hotfix**
- **v27.2.28 — Staging deployment gate and diagnostics hardening**
- **v27.2.29 — Final security and product audit hardening**
- **v27.2.30 — Host nginx CI/CD deployment hardening**
- **v27.2.31 — Authenticated deployment smoke-test hotfix**
- **v27.4.0 — Unified overtime editors**
- **v27.4.1 — Overtime scenario manager**
- **v27.4.2 — Timezone simplification and critical regression pack**
- **v27.4.3 — Reminder timezone and sync UX bugfix**
- **v27.5.1 — Telegram commands and mobile sync status bugfix**
- **v27.5.2 — Telegram command menu and quick actions**
- **v27.6.0 — Mobile Tasks & Inbox UX**
- **v27.6.1 — Quick Capture Polish**
- **v27.6.3 — Polish & Consistency**
- **v27.6.2 — Tasks & Subtasks**
- **v27.5.0 — Backup and recovery hardening**

## Текущая стратегия развёртывания

DutyLog пока работает как закрытая beta на `https://stage.yaruga-trophy.ru`. Отдельный production на общем VPS сознательно не поднимается: сервер уже обслуживает YARUGA, а постоянный третий Spring Boot/PostgreSQL-контур оставил бы слишком мало запаса по памяти.

Текущий рабочий процесс:

- ветка `test` собирает immutable image, запускает все проверки и автоматически обновляет staging;
- staging защищён HTTPS, health/smoke gates и ежедневным PostgreSQL backup через systemd timer;
- isolated restore drill уже доказал восстановление схемы, Flyway и пользовательских таблиц без вмешательства в живую базу;
- production workflow, rollback и отдельные environment-шаблоны сохраняются в репозитории, но будут активированы только на отдельном более мощном сервере и собственном домене;
- YARUGA и её контейнеры не участвуют в DutyLog deployment.

Текущий релиз — **v27.34.2 Vue Browser Runtime Bundle Hotfix**: Vue остаётся владельцем видимой оболочки DutyLog, а Vite library build теперь гарантированно заменяет Node-only `process.env.NODE_ENV` до публикации browser bundle. Каждый frontend build дополнительно проверяет готовый JavaScript на остаточные Node globals. Продуктовые экраны пока остаются legacy-owned; API и схема не меняются, Flyway остаётся V47.

## Служебный профиль администратора

Диагностика не показывается в обычных пользовательских настройках. Администратор видит в шапке кнопку `Система`, где доступны пользователи и роли, версия интерфейса и сервера, состояние БД, Service Worker, Telegram-интеграция, переключатель публичной регистрации и безопасный отчёт без секретов.

Публичная регистрация больше не выдаёт `ADMIN` автоматически. На новой production-установке первый администратор задаётся в `.env`:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
```

При старте backend создаёт этого пользователя, если его ещё нет, или повышает существующего пользователя с таким логином до `ADMIN`. После первого создания пароль можно менять в приложении; обычный рестарт не возвращает старый env-пароль. Для аварийного восстановления доступен `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true`. Все остальные регистрации получают только `USER`.

### Публичная регистрация

Публичная регистрация обычных пользователей управляется из админского раздела `Система` → `Публичная регистрация`. Когда переключатель выключен, страница входа скрывает вкладку регистрации, а backend возвращает `403` даже на прямой запрос `POST /api/auth/register`.

Администраторы через публичную регистрацию не создаются. Дополнительных админов можно назначить только из закрытого раздела `Система` → `Пользователи и роли`.



## Module contracts

Since v25.3 the module registry has explicit developer contracts. See `docs/MODULE_CONTRACTS.md`.


CI permission stabilization in v27.2.5:

- GitHub Actions runs release checks through `bash ./deploy/scripts/release-check.sh`.
- CI no longer fails when executable bits are lost on Windows/archive checkouts.
