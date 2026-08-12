# DutyLog regression test baseline
Historical v27.40.18 browser evidence: exact frontend and Maven gates passed; Chromium executed 48 scenarios with 46 passed, zero flaky and two deterministic final failures caused by stale v27.40.16 E2E release literals.

Status: v27.40.22.

Historical checkpoint — Status: v27.2.31.

Current extension: v27.40.22 retires the final live legacy UI owner by moving Admin Users/Roles, Registration and Diagnostics to a typed Vue/Pinia workspace backed by canonical generated `/api/v1/admin/**` operations. The acceptance surface is **159 Java test classes / 772 `@Test` methods / 48 Chromium Playwright scenarios / 60 Vitest cases** with **124 operations / 130 schemas** and Flyway V47. After Vue readiness no legacy-owned user screen or post-Vue legacy route side-effect adapter remains.

Historical v27.40.21 extension: accepted green staging release retiring the live legacy Payroll owner.
Historical v27.40.20 extension: accepted green staging release after aligning E2E release-version source contracts.
Historical v27.40.19 extension: exact frontend green; Maven ran 767 tests and stopped on two stale source-contract assertions that still expected pre-helper E2E literals.
Historical v27.40.17 extension: route-commit/hash-listener retirement reached exact frontend green but was Maven-red because the Java Calendar ICS PRODID remained on 27.40.16.
Historical v27.40.16 extension: accepted green staging release restoring Vue route-entry freshness, Today workspace ownership and note create read-your-write after the route-guard cutover.

Historical v27.40.14 extension: Vue became authoritative for admin/module route guards and canonical blocked-route redirects after profile/module state is known; post-Vue legacy `applyRoute()` was narrowed to Payroll/Admin side effects. Its CI stopped at one stale formatting-sensitive Maven source assertion, not a demonstrated runtime regression.

Historical v27.40.13 extension: v27.40.13 moved canonical hash read/write/subscription out of the legacy snapshot/bridge and into Vue while leaving access guards temporarily in legacy `applyRoute()`.

Historical v27.40.12 extension: v27.40.12 retired dead generic modal/Productivity capabilities from the Vue↔legacy bridge after those flows moved to native Vue domains; the fallback command surface was narrowed to navigation/logout before v27.40.13 removed navigation from that bridge entirely.

Historical v27.40.7 extension: v27.40.7 uses the complete v27.40.6 Playwright report (**43 clean / 1 retry-only flaky / 4 final failed**) to restore selected-day shift/timezone projection parity, exact cross-midnight overtime-allocation labels, and reconnect note-write ownership. The one multiple-notes failure is classified as a stale narrow-rail geometry assertion because the v27.40.4 native selected-day workspace intentionally became wide at the fixed 1440px desktop viewport; the replacement assertion requires side-by-side non-overlap and bounded width. The acceptance surface remains **153 Java test classes / 758 `@Test` methods / 48 Chromium Playwright scenarios / 52 Vitest cases** with **118 operations / 120 schemas** and Flyway V47.

Historical v27.40.6 extension: the exact frontend-gate TS2379 in the native selected-day schedule preview was fixed by guaranteeing a defined Vue key with `item.date ?? `preview-${index}`` while keeping `exactOptionalPropertyTypes` unchanged.

Historical v27.40.5 extension: the Calendar/Timeline strict-source contract was aligned with the native selected-day domain introduced in v27.40.4 by requiring `CalendarDaySection` and the typed `openDay` callback.

Historical v27.40.4 extension: the live Calendar selected-day compatibility island was retired; `SelectedDayPanel.vue` owns the stable selected-day DOM while shift/marker writes still use the single existing offline `dataLayer` through a narrow adapter.

`v27.39.0` introduced one Vue Settings ownership Chromium scenario, three Settings/Workspace model Vitest cases and six source/architecture JUnit contracts, reaching 757 `@Test` methods. `v27.39.1` adds one narrow strict-template regression method, so the JUnit baseline is now 758 without changing the 153-class / 48-Playwright / 52-Vitest surface.

Historical v27.38.15 acceptance baseline: v27.38.15 uses the complete v27.38.14 Playwright report: all 47 scenarios ran, 46 passed, and `task-modules` remains the only failure on both attempts. v27.38.14 removed the earlier page-lifecycle flaky and reduced the disabled-Tasks runtime storm to one four-request wave, but the trace shows that wave immediately after the successful `tasks=false` module mutation. The surviving race is owned by the legacy month cache: `saveModuleEnabled()` refreshes the month, `dataLayer.loadCalendar()` may first apply an IndexedDB calendar snapshot captured while Tasks was still enabled, and `applyCalendarBundle()` previously allowed that month-scoped cache to roll the global module map back to `tasks=true` for one render turn while the backend was already disabled. v27.38.15 makes the already-loaded runtime module map authoritative over cached month bundles and makes the post-module-mutation month reload fresh/server-first. The application baseline remains 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases; OpenAPI remains 101 operations / 106 schemas and Flyway remains V47.

Historical foundation: v27.2.29 security baseline remains preserved by all later releases.

## v27.38.0 Vue Tasks, Notes & Important Days migration extension

- Baseline advances to 152 Java test classes / 751 `@Test` methods / 47 Chromium Playwright scenarios / 49 Vitest cases / Flyway V47.
- Existing 47 browser journeys remain strict; only intentional UI transport expectations move from compatibility `/api/*` aliases to generated `/api/v1/*` operations.
- Adds six pure productivity-model Vitest cases for tags, schedule duration, task drafts/subtasks, ordering and Important Event serialization.
- Adds eight source/architecture Java contracts for Vue ownership, generated transport, concurrency, selected-day parity, Tasks/Inbox, Important Days, offline/reconnect and legacy retirement.
- OpenAPI advances from 98 operations / 103 schemas to 101 operations / 106 schemas; PostgreSQL and Flyway remain unchanged at V47.

## v27.37.5 Vue Calendar Selected-Day Island Lifecycle Hotfix extension

- Parks the selected-day `#panel` compatibility island before CalendarPage unmount and reattaches the same DOM island on return.
- Adds no test method or scenario; accepted baseline remained 151 Java test classes / 743 `@Test` methods / 47 Chromium Playwright scenarios / 43 Vitest cases.
- v27.37.5 was not browser-accepted: the full Playwright run completed at 31/47, so v27.38.x inherits those remaining parity failures and must close them before acceptance.

## v27.37.2 Vue Calendar Boot Routing Null-Safety Hotfix extension

- `applyRoute()` must not dereference the retired legacy Calendar navigation controls.
- The existing Calendar/Timeline migration contract requires one null-safe selector collection and forbids the three historical direct `.style.visibility` dereferences.
- No new JUnit/Vitest/Playwright case is added; exact counts remain 151 / 743 / 47 / 43.

## v27.37.1 Vue Calendar & Timeline Strict Typecheck Hotfix extension

- Four new compile-gated Java contracts preserve explicit bridge parameter types, typed optional Pinia modes, in-body `this` resolution and strict compiler configuration.
- The GitHub-hosted runner routing amendment keeps one regression contract that requires all Linux Actions jobs to remain on `ubuntu-latest` and forbids `self-hosted`.
- The exact frontend gate remains blocking; no `vue-tsc` option, browser assertion or runtime contract is weakened.
- Playwright remains 47 scenarios and Vitest remains 43 cases.

## v27.37.0 Vue Calendar & Timeline extension

- `VueCalendarTimelineMigrationFrontendContractTest` binds generated API use, one Vue owner, stale-read protection, month/week/day composition, named editor bridge, PWA upgrade and bundle budgets.
- Nine Vitest cases cover date math, range normalization, selected-day composition, stale responses, range reuse and layer rollback.
- `vue-calendar-timeline-migration.spec.js` proves one Today/Calendar owner, Month/Week/Day parity and one selected-day editor island.
- `pwa-upgrade.spec.js` creates a synthetic v27.36.8 cache and proves v27.37.0 activation removes it before claiming the shell.
- Baseline: 150 Java classes / 738 `@Test` methods / 47 Playwright scenarios / 43 Vitest cases / Flyway V47.

## v27.36.8 Vue Read Sequencing Static Contract Alignment extension

- Three stale implementation-string assertions now bind to `readSequence` and the canonical snapshot Vitest name.
- Six compile-gated Java tests prevent the old sequence name or scenario text from returning.
- Runtime store/API code, Playwright scenarios and Vitest case count are unchanged.
- Baseline: 149 Java classes / 727 `@Test` methods / 45 Playwright scenarios / 33 Vitest cases / Flyway V47.


## v27.36.7 Time Bank Period Toggle Snapshot Stability extension

- Month/year toggles use a period-only loader and do not replace credits, usages or balance.
- Three store Vitest cases bind account identity, toggle-race ownership and full-refresh supersession.
- Six compile-gated Java contracts preserve the partial-load boundary and strict Chromium locator.
- Baseline: 148 Java classes / 721 `@Test` methods / 45 Playwright scenarios / 33 Vitest cases / Flyway V47.


## v27.36.6 Time Bank Usage-Date Chart Parity extension

- Earned chart buckets use credit work dates; used chart buckets use actual usage dates.
- Two Vitest cases bind daily usage-day ownership and yearly month folding.
- Five compile-gated Java source contracts prevent `credit.usedHours` double counting and preserve the strict Chromium locator.
- Baseline: 147 Java classes / 715 `@Test` methods / 45 Playwright scenarios / 31 Vitest cases / Flyway V47.

## v27.36.5 Single-Pass CI & Final Vue Browser Parity extension

- Explicit month/year ARIA tokens and optimistic period ownership.
- Fresh planner/account load before opening the absence composer.
- Two new Vitest cases and eight compile-gated Java source contracts.
- One full validation owner per push to `test`; PR and other branch CI remain complete.

## v27.36.4 Vue Absence & Time Bank Browser Parity extension

- One Vitest case proves the typed projection event payload.
- Eight source-only Java tests bind unique modal ownership, winning-refresh publication, restricted legacy projection rendering, route preservation, visible edit deletion, overview insights and selected-day Vue delegation.
- Existing 45 Playwright scenarios remain strict; no retry, timeout, locator or page-error policy is weakened.
- Baseline: 145 Java classes / 702 `@Test` methods / 45 Playwright scenarios / 27 Vitest cases / Flyway V47.

## v27.36.3 CI Artifact Quota Resilience extension

- Java test classes: 144.
- `@Test` methods: 694.
- Playwright scenarios: 45.
- Vitest cases: 26.
- Flyway remains V47.
- Adds five static workflow contracts for compact/non-blocking JaCoCo and failure-only Playwright artifacts.
- Changes no runtime, API, OpenAPI, dependency graph, schema or domain ownership.

## v27.36.2 Vue Timer Static Contract Compile Coverage extension

- Java test classes: 143.
- `@Test` methods: 689.
- Playwright scenarios: 45.
- Vitest cases: 26.
- Flyway remains V47.
- Repairs one malformed Java source assertion without changing its behavior.
- Extends the local source-only compiler from `*FrontendContractTest.java` to both frontend contracts and `*HotfixTest.java`.
- Changes no runtime, API, OpenAPI, dependency graph, schema or domain ownership.

## v27.36.1 Vue Browser Timer Handle Type extension

- Java test classes: 143.
- `@Test` methods: 689.
- Playwright scenarios: 45.
- Vitest cases: 26.
- Flyway remains V47.
- Adds four static contracts proving browser-specific timer scheduling/cancellation in both migrated editors.
- Changes no runtime delay, API, OpenAPI, dependency graph, schema or domain ownership.

## v27.36.0 Vue Absence & Time Bank extension

- Java test classes: 142.
- `@Test` methods: 685.
- Playwright scenarios: 45.
- Vitest cases: 26.
- Flyway remains V47.
- Adds eight static architecture contracts, eight model/store Vitest cases and one browser migration scenario.
- Protects one Vue runtime owner, typed generated operations, stale-read sequencing, duplicate-submit blocking, HTTP 409 refresh, responsive ledger parity and two-way absence/usage links.



## v27.35.7 Docker frontend OpenAPI build-context extension

- Java test classes: 141.
- `@Test` methods: 677.
- Playwright scenarios: 44.
- Vitest cases: 16.
- Flyway remains V47.
- Adds four static Docker/OpenAPI delivery contracts; runtime and dependency graph are unchanged.


## v27.35.6 Gate A historical static-contract alignment extension

- Java test classes: 140.
- `@Test` methods: 673.
- Playwright scenarios: 44.
- Vitest cases: 16.
- Flyway remains V47.
- Corrects four stale test-only expectations; runtime and dependency graph are unchanged.


## v27.35.5 Gate A quality-register lambda-capture compile extension

- Java test classes: 140.
- `@Test` methods: 673.
- Playwright scenarios: 44.
- Vitest cases: 16.
- Flyway remains V47.
- Adds an effectively-final source contract for the Q-02–Q-05 register scan; runtime behavior is unchanged.

## v27.35.4 Frontend gate static-contract Java escaping extension

- `AuthenticLockfileCommitGeneratedClientFixtureHotfixTest` now contains a valid Java string for the quoted `$FRONTEND_DIR` shell command.
- `release-check.sh` protects the exact escaped source contract before Maven.
- Production sources, frontend graph, API, Flyway and test-count baselines are unchanged.

## v27.35.3 Authentic lockfile commit and generated-client fixture extension

- `AuthenticLockfileCommitGeneratedClientFixtureHotfixTest` protects committed graph provenance, clean-checkout CI/Docker behavior, Q-01 completion and the fresh-response fixture.
- `generatedClient.spec.ts` keeps the two sequential typed operations but returns a new `Response` for every mocked fetch.
- Gate A remains acceptance-blocked only by the full CI/staging result of this release.

## v27.35.2 Authentic npm Lockfile Bootstrap extension

- `AuthenticNpmLockfileBootstrapHotfixTest` protects exact-toolchain generation, authentic graph checks, CI artifact upload and the Docker bootstrap boundary.
- `FrontendLockfileExecutableResolutionHotfixTest` now rejects shipping the synthetic lockfile while retaining launcher and no-`npx` guards.
- `VueDeliveryContractsDiagnosticsFoundationTest` keeps Q-01 active and Gate A blocked until the exact generated artifact is committed.
- Baseline advances to 139 Java test classes / 669 test methods / 44 Playwright scenarios / 16 Vitest cases; Flyway remains V47.

## v27.35.1 Frontend Lockfile Executable Resolution extension

The hotfix adds explicit npm `bin` metadata and post-install executable/dependency-tree gates while retaining the v27.35.0 generated-contract and diagnostics foundation.

## v27.35.0 Vue Delivery, Contracts & Diagnostics Foundation extension

- `VueDeliveryContractsDiagnosticsFoundationTest` protects lockfile/toolchain, `npm ci`, generated-contract drift, diagnostics, migration template, ADRs and Gate A completion.
- Frontend cases cover generated operation routing, path/query interpolation, requestId correlation, network failures and controlled recovery diagnostics.
- Existing Vue foundation browser coverage now proves the safe diagnostics snapshot and controlled recovery UI without weakening strict runtime collection.
- Baseline advances to 137 Java test classes / 661 test methods / 44 Playwright scenarios / 16 Vitest cases; Flyway remains V47.

## v27.34.4 Vue Secondary Navigation & Overtime Preview Contract Hotfix extension

- `VueSecondaryNavigationOvertimePreviewHotfixTest` protects both confirmed Chromium root causes.
- `OvertimeServiceTest` proves a zero-hour calculated draft previews successfully while persistence remains rejected.
- `OvertimeControllerTest` proves both legacy and `/api/v1` preview aliases return HTTP `200` with zero credited minutes.
- `vue-app-shell.spec.js` verifies active state through More and the secondary Tasks item.
- `overtime-scenario-manager.spec.js` requires the zero-hour shift draft preview to succeed before editing the interval.

## v27.34.3 Vue Shell E2E Navigation Compatibility Hotfix extension

- `VueShellE2eNavigationCompatibilityHotfixTest` rejects direct hidden-legacy chrome clicks in released E2E scenarios and requires the shared public navigation bridge.
- Stable Vue shell hooks cover brand, profile, more menu, logout and close actions.
- Shell-specific scenarios assert Vue chrome while legacy product screens remain authoritative.
- Calendar Sync expects the current ICS release identity.
- Baseline advances to 135 Java test classes / 648 test methods / 44 Playwright scenarios / 11 Vitest cases; Flyway remains V47.

## v27.34.2 Vue Browser Runtime Bundle Hotfix extension

- `VueBrowserRuntimeBundleHotfixTest` requires the production environment replacement and the generated-bundle audit while preserving strict Playwright page-error collection.
- `frontend/scripts/audit-browser-bundle.mjs` rejects residual `process.env`, CommonJS and Node path globals in `dutylog-vue-app-shell.js`.
- `npm run build` includes the audit, so both CI and Docker validate the same browser artifact.
- Baseline advances to 134 Java test classes / 647 test methods / 44 Playwright scenarios / 11 Vitest cases; Flyway remains V47.

## v27.34.1 Vue Strict Type Contract Hotfix extension

- `VueStrictOptionalTemplateContractHotfixTest` rejects explicit `undefined` native button bindings and the unsupported Vite `cssFileName` library option.
- `aria-current` now uses the valid ARIA false state for inactive routes; optional button `type` and `disabled` values are normalized before reaching native attributes.
- Rollup continues to emit `dutylog-vue-app-shell.css`; strict TypeScript and `exactOptionalPropertyTypes` remain enabled.
- Baseline advances to 133 Java test classes / 646 test methods / 44 Playwright scenarios / 11 Vitest cases; Flyway remains V47.

## v27.34.0 Vue App Shell & Design System extension

- `VueAppShellDesignSystemContractTest` protects shell ownership, immutable snapshots, named capabilities, responsive fallback and one-bundle packaging.
- Vue owns brand/profile/navigation/network chrome, shared modal/toast hosts and typed UI primitives; legacy product screens remain authoritative.
- Legacy route, workspace, network and safe profile changes publish frozen snapshots back into Pinia.
- Playwright verifies Vue navigation changes the released hash route, the matching legacy workspace stays functional and direct hash changes resynchronize Vue active state.
- Baseline advances to 132 Java test classes / 645 test methods / 44 Playwright scenarios / 11 Vitest cases; Flyway remains V47.

## v27.33.0 Vue Frontend Foundation & CI/CD extension

- Static contracts verify exact dependency pins, strict TypeScript, memory-history routing and the no-direct-DOM bridge rule.
- Packaging contracts verify Vite output is copied into `static/vue` and the Docker runtime remains one Spring Boot app image.
- Workflow contracts require the frontend gate before Maven in CI, staging and production validation.
- Vitest covers CSRF/error transport and bridge fallback behavior.
- Playwright waits for `window.__dutylogVueReady` and verifies the Vue runtime without replacing the legacy shell.

## v27.32.1 Time Bank Absence Navigation Hotfix extension

- `AbsenceTimeBankNavigationHotfixTest` protects the modal editor transition, stale-planner refresh and edit-aware FIFO preview.
- `absence-time-bank-experience.spec.js` remains the real browser reproduction: Bank Usage → Open absence → visible Composer → populated record → FIFO source.
- Maven baseline: 130 test classes / 635 test methods.
- Browser baseline remains 42 scenarios.
- Flyway remains V47.


## v27.32.0 Absence & Time Bank Experience extension

- `AbsenceTimeBankExperienceContractTest` protects the four bank views, posted/reserved/free metrics, ownership links, FIFO forecast and guide entry points.
- `absence-time-bank-experience.spec.js` verifies a planned absence reservation, read-only bank usage, two-way navigation, inline composer forecast and future FIFO calculation.
- historical Overtime browser tests explicitly select the Credits, Usage or FIFO view they exercise.
- Maven baseline: 129 test classes / 633 test methods.
- Browser baseline: 42 scenarios.

## v27.31.2 Canonical Absence Browser Contract Alignment Hotfix extension

- `canonical-absence-ledger.spec.js` verifies `DIRECT_USAGE_RETIRED` through `page.context().request`, so an expected 409 does not pollute the page-level failure collector.
- linked overtime usages are asserted as read-only `.overtimeLinkedUsage` projections; the owning absence is the only browser edit/delete route.
- the split-allocation deletion scenario proves the deleted absence disappears, the surviving absence remains visible, both credits remain and FIFO allocations are rebuilt correctly.
- `CanonicalAbsenceBrowserContractAlignmentHotfixTest` rejects restoration of stale `data-edit-usage` expectations.
- Maven baseline: 128 test classes / 628 test methods.
- Browser baseline remains 41 scenarios.
- Flyway remains V47.

## v27.31.1 Canonical Absence Static Contract Alignment Hotfix extension

- five stale source-string assertions now follow direct coverage serialization, `HOURS_ONLY`, explicit legacy-promotion functions and absence-owned linked usages;
- one focused guard rejects both the stale ternary coverage writer and retired standalone linked-usage actions;
- Maven baseline: 127 test classes / 626 test methods;
- browser baseline remains 41 scenarios;
- Flyway remains V47.

## v27.31.0 Canonical Absence Ledger & Legacy Retirement extension

- `CanonicalAbsenceLedgerLegacyRetirementContractTest` protects Composer-only creation, explicit legacy promotion, `HOURS_ONLY` truthfulness and the retained FIFO journal.
- `OvertimeControllerTest` proves direct usage writes return retirement conflicts and migration preserves the existing usage ID, source ownership and allocation minutes.
- `TelegramCommandServiceTest` confirms `/timeoff` creates a canonical absence instead of a detached usage.
- `canonical-absence-ledger.spec.js` creates time off through Composer, verifies `ABSENCE` ownership/FIFO allocation and restores the bank when the absence changes to Unpaid.
- Existing overtime browser scenarios now create canonical absences instead of raw usage rows.
- `CanonicalAbsenceLedgerLegacyRetirementContractTest` also pins the immutable V42 checksum and protects the forward-only V47 `HOURS_ONLY` constraints.
- Baseline advances to 126 Java test classes / 625 `@Test` methods / 41 Playwright scenarios.
- Flyway advances to V47; V42–V46 remain immutable.

## v27.30.2 Today Overtime Journal Contract Hotfix extension

- `TodayDashboardFrontendContractTest` now protects the released Journal route instead of requiring the removed direct credit-modal shortcut.
- `TodayOvertimeJournalContractHotfixTest` confirms the Today card label and `#overtime` route while keeping `openOvertimeCreditModal` owned by `40-overtime.js`.
- No production JavaScript, API, PostgreSQL or Flyway behavior changed.
- Baseline advances to 125 Java test classes / 620 `@Test` methods / 40 Playwright scenarios.
- Flyway remains V46.


## v27.30.1 Unified Absence Quick Access Integration extension

- `UnifiedAbsenceQuickAccessFrontendContractTest` protects neutral global access, direct Today access and contextual Overtime preselection.
- `today-dashboard.spec.js` opens the current-date composer from Today and confirms the global plus absence action remains available.
- Quick Add focus reaches the absence action when Vacation is enabled without Overtime or draft modules.
- The Today direct overtime-credit shortcut is replaced by absence access; credit creation remains in Quick Add and Overtime.
- Baseline advances to 124 Java test classes / 619 `@Test` methods / 40 Playwright scenarios.
- API, PostgreSQL and Flyway remain unchanged at V46.


## v27.30.0 Unified Absence Composer & Calendar Projection extension

- `UnifiedAbsenceComposerFrontendContractTest` protects one form, one modal mount, source routing and typed calendar projection.
- `unified-absence-composer.spec.js` creates a partial overtime-backed absence and a full-day sick leave through real browser and API boundaries.
- `overtime-editor-modals.spec.js` proves new usage entry points create a linked absence while old direct usages remain editable.
- Vacation allowance and the canonical FIFO bank retain their existing validation authority.
- Full-day facts preserve the underlying plan; partial facts remain time intervals over the plan.
- Pre-push full gates additionally protect fresh composer balances, the visible Quick Add route and glyph-aware vacation projection.
- IntelliJ all-tests order is protected by giving the dirtied Telegram detached-owner context a dedicated H2 database; closing it can no longer drop the shared `testdb` schema used by later controller tests.
- Baseline advances to 123 Java test classes / 616 `@Test` methods / 39 Playwright scenarios.
- PostgreSQL and Flyway remain unchanged at V46.

## v27.29.3 Custom Workspace Today Widget Order Persistence Hotfix extension

- Playwright reached 37/38 and proved Tasks was visible but returned below Shift after the profile autosave response.
- `ProfileController.safeTodayWidgets(...)` now preserves explicit allowed order and prepends Shift only when absent.
- `ProfileControllerTest` covers real HTTP response and stored JSON ordering for `tasks, shift, important`.
- `CustomWorkspaceTodayWidgetOrderPersistenceHotfixTest` protects the server-side order boundary and the browser DOM-order assertion.
- Baseline advances to 122 Java test classes / 614 `@Test` methods / 38 Playwright scenarios.
- API shape, PostgreSQL and Flyway V46 remain unchanged.

## v27.29.2 Custom Workspace Today Widget Inheritance Hotfix extension

- Maven is green at 611 tests; Playwright reached 37/38 and isolated the remaining failure to `todayTasksCard` retaining `workspaceHidden` after Custom Workspace creation.
- Custom Workspace now clones `todayWidgets` from the active preset together with navigation.
- The existing browser scenario asserts inherited Overtime and Tasks selections before editing them.
- Baseline advances to 121 Java test classes / 612 `@Test` methods / 38 Playwright scenarios.
- Runtime API, PostgreSQL and Flyway V46 remain unchanged.

## v27.29.1 Theme Package Token Scope Contract Hotfix extension

- `WorkspaceLayoutThemeStudioFrontendContractTest` now matches the actual runtime template literal instead of requiring Java-only escape backslashes in the JavaScript file.
- `ThemePackageTokenScopeContractHotfixTest` explicitly distinguishes runtime content from Java source escaping and rejects the former phantom-backslash expectation.
- Theme registry metadata, Midnight/Forest isolation, decoration packages and all 38 Playwright scenarios remain unchanged.
- Production JavaScript, CSS, profile persistence, API, PostgreSQL and Flyway V46 remain unchanged.
- Baseline advances to 120 Java test classes / 611 `@Test` methods / 38 Playwright scenarios.

## v27.29.0 Workspace, Layout & Theme Studio extension

- `WorkspaceLayoutThemeStudioFrontendContractTest` protects UI contract v2, bounded custom navigation, Today-card safety, layout/calendar CSS scoping, synchronous bootstrap and package isolation.
- `workspace-layout-theme-studio.spec.js` persists a custom route set, Today-card order, Sidebar layout, calendar density, layer dots and decoration through real profile autosave and reload.
- `ProfileControllerTest` proves the expanded whitelist normalizes contract v1 input to v2, rejects unknown enums and keeps mandatory entries.
- Existing `UiCoreWorkspaceFrontendContractTest` and `design-system-shell.spec.js` advance from UI Core v1 to v2 without restoring Classic or duplicating screens.
- Payroll, approval, ledger and calendar business logic remain unchanged; Flyway remains V46.
- Baseline advances to 119 Java test classes / 610 `@Test` methods / 38 Playwright scenarios.

## v27.28.3 Payroll Snapshot Hash Schema Validation Hotfix extension

- `PayrollSnapshotHashSchemaValidationHotfixTest` pins the released V45 SHA-256, protects the new V46 conversion and keeps the JPA mapping at non-null `VARCHAR(64)` semantics.
- V46 converts fixed-width values with `BTRIM(calculation_hash)` instead of modifying V45 or weakening Hibernate schema validation.
- The existing `ck_payroll_snapshot_hash` constraint remains the authority for exactly 64 lowercase hexadecimal characters.
- Payroll math, API, OpenAPI, UI, immutable revisions and all browser scenarios remain unchanged.
- Flyway advances to V46; baseline advances to 118 Java test classes / 605 `@Test` methods / 37 Playwright scenarios.

## v27.28.2 Calendar Persistence Reload Readiness Hotfix extension

- `CalendarPersistenceReloadReadinessHotfixTest` protects the calendar-navigation promise, helper export and both reload guards.
- `calendar-persistence.spec.js` waits for Month navigation, ledger projection and application idle before intentional reload.
- Week and Day header navigation share the same readiness promise without changing their visible behavior.
- The strict browser fixture remains unchanged and still rejects every unexpected console, page, HTTP and network failure.
- Production Payroll runtime, API, OpenAPI and Flyway remain unchanged at V45.
- Baseline advances to 117 Java test classes / 604 `@Test` methods / 37 Playwright scenarios.

## v27.28.1 Payroll Module Registry Contract Hotfix extension

- `PayrollFoundationContractTest` validates the stable key in `ModuleKeys` and the actual `DutyLogModules` registration shape.
- The contract no longer requires the unrelated literal `ModuleService.PAYROLL` inside the canonical registry source.
- Production Payroll runtime, API, OpenAPI, V45 and the browser scenario remain unchanged.
- Baseline remains 116 Java test classes / 603 `@Test` methods / 37 Playwright scenarios.

## v27.28.0 Payroll Foundation extension

- `PayrollFoundationServiceTest` proves open-period blocking, closed healthy calculation, minor-unit arithmetic, append-only adjustments and snapshot supersession.
- `PayrollFoundationContractTest` protects V45, canonical source boundaries, no-store API, module contract, UI and OpenAPI.
- `payroll-foundation.spec.js` exercises an eight-hour source, closed month, rate, addition, calculation and rendered revision through real API/UI routes.
- Baseline advances to 116 Java test classes / 603 `@Test` methods / 37 Playwright scenarios.

## v27.27.2 Ledger Browser State & Visibility Hotfix extension

- `LedgerBrowserStateVisibilityHotfixTest` protects Vacation route freshness, timezone-save readiness, expected-status console handling, month-safe overtime data and visible responsive selectors.
- Vacation navigation waits for `__dutylogVacationReady`; reload-sensitive timezone scenarios wait for `__dutylogTimeSettingsSaveReady` and application idle.
- Chromium's generic resource console message is ignored only when an explicitly marked expected status provides a one-use matching budget.
- Baseline advances to 114 Java test classes / 600 `@Test` methods / 36 Playwright scenarios.

## v27.27.1 Ledger Workflow Browser Contract Hotfix extension

- `LedgerWorkflowBrowserContractHotfixTest` protects route refresh, serialized ledger projections, explicit expected-status marking, current-month timezone data and application-idle reloads.
- Overtime navigation waits for `__dutylogLedgerRouteReady` and `__dutylogLedgerReady` instead of asserting stale hidden-view state.
- The runtime fixture still rejects every unmarked same-origin HTTP failure; only the intentional closed-period `409` carries the expected-status header.
- Baseline advances to 113 Java test classes / 599 `@Test` methods / 36 Playwright scenarios.


## v27.27.0 Ledger Integrity & Approval Workflow extension

- `LedgerIntegrityApprovalWorkflowServiceTest` proves draft → reserve → post → cancel, balance return, closed-period mutation guards and explicit factual work.
- `LedgerIntegrityApprovalWorkflowContractTest` protects V44, append-only audit, no-store APIs, workflow UI and OpenAPI.
- `ledger-integrity-approval-workflow.spec.js` exercises the complete browser/API lifecycle without force clicks or state bypasses.
- The release gate protects V44, seven workflow states, `RESERVED`/`POSTED`, period locks, actual-work APIs and exact baselines.
- Baseline advances to 112 Java test classes / 598 `@Test` methods / 36 Playwright scenarios.


## v27.26.2 Canonical Lineage Recovery extension

- `UnifiedTimeCompensationLedgerContractTest` protects the recovered V41/V42/V43 stack, Calendar Sync boot hardening, Calendar Comfort modal route and source-linked overtime ledger.
- Workspace Route browser contracts use `openView(page, "tasks")` and assert module state on `#view-tasks`, never on a workspace-hidden tab.
- The release gate requires exactly one V41, V42 and V43 migration and rejects `localDateKey(`, force-click panel navigation and stale Tasks tab module assertions.
- Product behavior is restored forward from the deployed v27.23.0 baseline; no duplicate migration or rollback SQL is introduced.
- Baseline advances to 110 Java test classes / 592 `@Test` methods / 35 Playwright scenarios.

## v27.26.1 Absence Request Constructor Compile Hotfix

- `VacationPlannerServiceTest` uses the full ten-argument `AbsencePeriodCreateRequest` for compensation-aware time off.
- `UnifiedTimeCompensationLedgerContractTest` protects the explicit `OVERTIME_BANK` fixture semantics.
- `release-check.sh` rejects any nine-argument constructor call in the service fixture source.
- Production runtime and Flyway remain unchanged at V43.

## v27.26.0 Unified Time & Compensation Ledger extension

- V43 migration/opening-credit/source-link contracts.
- Linked overtime usage lifecycle and manual mutation guards.
- Vacation Planner compensation-policy and canonical-bank integration.
- Plan → Fact → Compensation service/controller/OpenAPI contracts.
- Monthly unified frontend and locked linked-usage surface.
- Browser flow covering credit → linked time off → unpaid absence → deletion/restored balance.

## v27.25.2 Absence Experience Frontend Contract Hotfix extension

- `VacationPlannerFrontendContractTest` follows `facts.absences.slice(0, 3)` for the intentionally compact Week agenda.
- Hourly Day protects `facts.partialAbsences`; the all-day rail separately protects non-partial absences.
- The stale unbounded `for (const absence of facts.absences)` expectation is rejected.
- Product JavaScript, Web/v1 API, OpenAPI, V42 schema and 34 Playwright scenarios remain unchanged.
- Baseline advances to 109 Java test classes / 581 `@Test` methods / 34 Playwright scenarios.

## v27.25.1 Absence Preview Lambda Compile Hotfix extension

- `VacationPlannerService.buildPreview(...)` snapshots the incremented loop date as `previewDate` before using it in the overlap-search lambda.
- The compiler contract forbids `filter(period -> covers(period, date))` inside that mutable loop.
- Counted-day, shift-plan, overlap and preview-item projections all use the same per-iteration date snapshot.
- Product behavior, Web/v1 API, OpenAPI, V42 schema and 34 Playwright scenarios remain unchanged.
- Baseline advances to 109 Java test classes / 580 `@Test` methods / 34 Playwright scenarios.

## v27.25.0 Absence & Time-Off Overhaul extension

- Service tests cover full-day charging from the preserved shift, exact partial minutes, independent hour balances and overlap windows.
- Controller tests cover settings, built-in Time Off, preview/create/delete and calendar occurrence shape.
- Migration/static contracts protect V42, plan/fact DOM, full-weight absence cells and partial bars.
- Calendar export tests keep full-day absence all-day and project partial time off as a timed event.
- Playwright creates a partial time-off interval over a shift, verifies both plan and fact, then deletes it and restores the balance.

## v27.24.1 Calendar Comfort E2E Panel Contract Hotfix extension

- `calendar-comfort.spec.js` requires today's selected-day panel to be visible after the contextual Month-mode return.
- The scenario closes the modal through `#pClose`, waits for `#panel` to become hidden and verifies `with-panel` is removed before clicking `#next`.
- The mobile blocking backdrop remains a production contract; the hotfix does not use force-click or weaken Playwright actionability.
- Runtime JavaScript, API and Flyway V41 remain unchanged; baseline stays 108 Java classes / 569 `@Test` methods / 33 Playwright scenarios.

## v27.24.0 Calendar Comfort & Correctness extension

- `CalendarComfortFrontendContractTest` protects Today return, selected important-date ownership, overnight shift composition, refresh persistence/performance diagnostics and compact controls.
- `calendar-comfort.spec.js` covers the real phone viewport: leave current month → return to today → select another month/day → open Important Days → verify compact checkboxes.
- Calendar refresh keeps the last successful grid instead of replacing it with a full skeleton; diagnostics stay bounded to 20 in-memory samples.
- No API or schema migration; Flyway remains V41.
- Baseline advances to 108 Java classes / 569 `@Test` methods / 33 Playwright scenarios.

## v27.23.2 Calendar Sync Runtime Boot Hotfix extension

- `CalendarSyncFrontendContractTest` requires both default export-range boundaries to use `keyOf(...)`.
- The static contract rejects `localDateKey(` in `55-calendar-sync.js`.
- `release-check.sh` mirrors that guard before packaging.
- The shared browser fixture remains strict about uncaught page errors and unexpected same-origin failures.
- Baseline advances to 107 Java classes / 564 `@Test` methods / 32 Playwright scenarios; Flyway remains V41.

## v27.23.1 Calendar Sync JSON UTF-8 Contract Hotfix extension

- `CalendarSyncControllerTest` reads JSON with `getContentAsString(StandardCharsets.UTF_8)` instead of MockMvc's ISO-8859-1 fallback.
- The lifecycle test protects the real U+2026 token hint and cannot accept the `â¦` mojibake form.
- Runtime Java, HTTP API, nginx protection and Flyway V41 are unchanged.
- Baseline stays 107 Java classes / 563 `@Test` methods / 32 Playwright scenarios.


## v27.23.0 External Calendar Sync extension

- `CalendarIcsServiceTest` protects four-domain composition, UTF-8/CRLF/folding, recurrence, escaping, deduplication and payload limits.
- `CalendarSubscriptionServiceTest` protects 256-bit issuance, SHA-only persistence, bounded feed windows, rotation and revocation.
- `CalendarSyncControllerTest` protects auth/CSRF, owner scope, one-time secret disclosure, old-token invalidation, no-store and module boundaries.
- `CalendarSyncFrontendContractTest` protects responsive Settings wiring and one-time browser state.
- `external-calendar-sync.spec.js` covers issue → external fetch → range download → revoke → old URL 404.
- Baseline advances to 107 Java classes / 563 `@Test` methods / 32 Playwright scenarios; Flyway advances to V41.


## v27.22.2 Workspace Route E2E Navigation Hotfix extension

- `mobile-layout.spec.js`, `task-details.spec.js` and `task-modules.spec.js` use shared `openView(page, "tasks")` instead of clicking a workspace-hidden tab.
- The Tasks module persistence scenario checks `moduleHidden` on `#view-tasks`, leaving workspace placement to the selected workspace.
- `vacation-planner.spec.js` already passes; production JavaScript, HTTP API and Flyway V40 are unchanged.
- Baseline stays 103 Java classes / 544 `@Test` methods / 31 Playwright scenarios.

## v27.22.1 Vacation Planner Frontend Contract Hotfix extension

- `UiCoreWorkspaceFrontendContractTest` protects the actual Shift Worker `vacation` route and Today widget order.
- `VacationPlannerFrontendContractTest` protects the real `facts.absences` all-day composition and edit route instead of an invented `vacation-day` token.
- `ModuleServiceContractTest` derives persisted switchable-module expectations from `DutyLogModules.ALL`.
- Runtime JavaScript behavior, HTTP API and Flyway V40 are unchanged; baseline stays 103 / 544 / 31.

## v27.22.0 Vacation Planner extension

- `VacationPlannerServiceTest` protects defaults, calendar/weekday counting, work-year boundaries, carryover, conflicts, allowance, ownership and CRUD.
- `VacationPlannerControllerTest` protects Web/v1 routes, CSRF, validation, calendar aggregation and stable error codes.
- `VacationPlannerFrontendContractTest` protects bundle order, separate state/API and Month/Week/Day composition.
- `vacation-planner.spec.js` covers preview, save, calendar projection, edit and delete in Chromium.
- Baseline advances to 103 Java classes / 544 `@Test` methods / 31 Playwright scenarios; Flyway advances to V40.

## v27.21.2 Schedule Accordion E2E Selector Hotfix extension

- `helpers.openDayModuleById()` requires exactly one visible accordion and opens its direct summary.
- `schedule-templates-calendar-layers.spec.js` targets `#accSched` instead of the duplicated `data-day-module="shifts"` surface shared with `#accShift`.
- The generic `openDayModule()` remains strict; no `.first()` fallback can silently route a scenario to the wrong panel.
- Runtime JavaScript, HTTP API and Flyway remain unchanged; baseline stays 100 Java classes / 525 `@Test` methods / 30 Playwright scenarios.


## v27.21.1 Schedule Templates Frontend Contract Alignment Hotfix extension

- `CalendarMonthReloadContractTest` protects the complete fresh-reload chain across calendar, boot/data layer and cache-bypassing month API.
- `ScheduleTemplateFrontendContractTest` requires authoritative preview/apply payloads instead of browser-side weekday rotation and legacy `/api/days/fill`.
- `ScheduleTemplatesCalendarLayersFrontendContractTest` matches the real async API methods and `tplPreview` surface.
- Runtime JavaScript, HTTP API and Flyway remain unchanged; baseline stays 100 Java classes / 525 `@Test` methods / 30 Playwright scenarios.


## v27.21.0 Schedule Templates & Calendar Layers extension

- `ScheduleTemplatesAndLayersServiceTest` covers built-in seeding, immutable presets, safe conflicts, overwrite, weekday alignment, ownership, timezone projection, visibility and validation.
- `ScheduleTemplatesAndLayersControllerTest` protects Web/v1 CRUD, preview/apply, calendar aggregation, CSRF, authentication and foreign-resource boundaries.
- `ScheduleTemplatesCalendarLayersFrontendContractTest` protects bundle order, safe preview defaults and month/week/day layer composition.
- `schedule-templates-calendar-layers.spec.js` covers the real browser flow from preview and apply through layer creation, projection and server-owned visibility.
- Baseline advances to 100 Java classes / 525 `@Test` methods / 30 Playwright scenarios; Flyway advances to V39.

## v27.20.1 Important Event Modal & Offline Notes E2E Hotfix extension

- `important-timezone.spec.js` requires the details modal to remain hidden while editing and after save.
- `50-tasks.js` enforces a single-open-modal lifecycle and stops interactive board actions from reaching row navigation.
- `helpers.selectDate()` is idempotent when Month is hidden by Week/Day and restores the original mode after cross-date selection.
- `pwa-offline.spec.js` edits an existing note offline, verifies the coalesced `updateNote` queue and IndexedDB snapshot, reconnects, drains the queue and reloads the server-authoritative text.
- API and Flyway remain unchanged; baseline stays 97 Java classes / 513 `@Test` methods / 29 Playwright scenarios.

## v27.20.0 Notes & Important Events Next extension

- `ImportantDayServiceTest` covers timed source-zone preservation/reprojection, floating periods and invalid boundaries.
- `DayNoteServiceTest` covers owner-scoped title/content search and date ranges.
- `ImportantDayControllerTest` protects the additive timed-event JSON contract and reminders.
- `DayNoteControllerTest` protects the owner-scoped search alias, ranges and validation envelope.
- `MultipleDailyNotesFrontendContractTest` and `ImportantDatesTimezoneOvertimeFrontendContractTest` protect search/offline queue and editor/details contracts.
- `notes-important-events-next.spec.js` covers real event creation, read-first details, hourly placement and note search.
- Baseline is 97 Java classes / 513 `@Test` methods / 29 Playwright scenarios; Flyway advances to V38.

## v27.19.4 Ghost Button Transition E2E Stabilization Hotfix extension

- `appearance-quality.spec.js` polls until the Ghost border transition reaches a fully transparent alpha channel.
- Border transparency is measured through Chromium rasterization instead of exact `rgba()` string serialization, so `oklab()` interpolation is valid.
- Outline remains required to expose a non-zero border alpha; Ghost hover and seven preview variants remain covered.
- Production CSS, API, Flyway and test counts are unchanged.

## v27.19.3 Task Deadline Validation E2E Contract Hotfix extension

- `task-modules.spec.js` expects the planned-interval deadline error emitted by the current editor contract.
- Legacy all-day/date validation remains covered by `TaskServiceTest` and `TaskControllerTest`.
- The prior CI run established 27 passing browser scenarios and one deterministic text mismatch; no runtime defect was observed.
- No test-count, API or Flyway changes.

## v27.19.2 Frontend Asset Contract Stability Hotfix extension

- Today/UI Core/Calendar/Design System asset assertions match stable paths plus `?v=` instead of a concrete release number.
- Runtime version consistency remains exact through `release-check.sh`, Service Worker cache naming and static asset query validation.
- The release gate rejects new `?v=X.Y.Z` literals in frontend contract Java sources.
- No test-count, API or Flyway changes.

## v27.19.1 Task Board Date Range Compatibility Hotfix extension

- `TaskServiceTest` keeps the legacy `from` / `to` deadline-or-date behavior and proves `scheduledFrom` / `scheduledTo` intersect overnight planning.
- `TaskControllerTest` protects both public query contracts on `/api/tasks/board`.
- `TasksInboxNextFrontendContractTest` ensures Web/PWA date fields send planned-range parameters instead of silently changing the old API meaning.
- Baseline remains 97 Java classes / 507 `@Test` methods / 28 Playwright scenarios; Flyway remains V37.


## v27.19.0 Tasks & Inbox Next extension

- `TaskServiceTest` covers planned duration, overnight day coverage, project filtering, invalid boundaries and timezone reprojection.
- `TasksInboxNextFrontendContractTest` protects editor, details, calendar, Inbox and responsive contracts.
- `tasks-inbox-next.spec.js` covers planned interval creation, project/deadline separation, project filtering, Inbox search, exact hourly placement and the mobile editor.
- `editor-modals.spec.js` now creates a real planned interval instead of using a deadline as a synthetic timeline start.
- Baseline is 97 Java classes / 507 `@Test` methods / 28 Playwright scenarios.
- Flyway advances to V37.


## v27.18.3 UI Settings & Button Variants Quality extension

- `appearance-quality.spec.js` covers custom accent → Theme palette reset, explicit same-value reset, reload and built-in theme switching.
- The scenario preserves a custom palette while workspace/layout change and compares computed Outline/Ghost border, shadow and hover behavior.
- `UiCoreWorkspaceFrontendContractTest` protects reset controls, semantic button tokens and separate Ghost/Outline CSS contracts.
- Baseline remains 96 Java classes / 500 `@Test` methods and grows to 27 Playwright scenarios.
- No Flyway or domain API change is added.


## v27.18.2 Overtime Snapshot Sync & Timezone E2E Stabilization extension

- `OvertimeAccountPageDto` now carries the canonical full `usages` list beside summary totals and paged credits.
- Service/controller assertions prove filtered credit pages never truncate usage allocations required by the chart.
- `overtime-next.spec.js` waits for the usage snapshot before verifying monthly and daily `−4 h` bars.
- Shared `selectDate()` is idempotent; `important-timezone.spec.js` reuses the exact assigned `data-date` after refresh.
- Baseline remains 96 / 500 / 26; no Flyway migration is added.


## v27.18.1 Overtime Next E2E Contract Hotfix extension

- `overtime-editor-modals.spec.js` targets the visible desktop delete action inside `#ledgerRows` and scopes post-delete assertions to the same presentation.
- `overtime-next.spec.js` verifies monthly `YYYY-MM` keys in All-time and daily `YYYY-MM-DD` keys after switching to Month.
- Production behavior is unchanged; the 96 / 500 / 26 baseline remains.


## v27.18.0 Overtime Next extension

- `OvertimeNextFrontendContractTest` protects the summary, period controls, chart, FIFO queue, mobile cards and preserved editors/exports.
- `overtime-next.spec.js` creates credits and a FIFO usage, verifies the 5h/4h/1h summary, switches the year preset, then proves the desktop table is replaced by detailed cards at a phone viewport.
- Existing overtime projection, editor, integrity and scenario tests remain unchanged.
- No Flyway or domain API change is required.

## v27.17.6 Classic Sunset extension

- `design-system-shell.spec.js` protects the single shell and injects legacy `shellMode=classic` into local storage before reload.
- Static contracts verify that Classic controls, backend whitelist entries and Classic-only CSS selectors are absent.
- Today remains the only default route and workspace navigation is the only primary-navigation authority.
- Recovery now relies on immutable Git/Docker rollback rather than a parallel in-app interface.
- Baseline remains 95 Java test classes / 496 `@Test` methods / 25 Chromium Playwright scenarios.


## v27.17.5 UI Core E2E Accordion Hotfix extension

- `design-system-shell.spec.js` no longer toggles the Appearance accordion blindly after reload.
- The scenario now proves that `#appearanceCard` restores `is-open`, the Classic selector is visible and `dutylog.settings.openSection` remains `appearance`.
- The Next → Classic → Next fallback contract is exercised only after the persisted accordion state is confirmed.
- Production UI behavior is unchanged; baseline remains 95 Java test classes / 496 `@Test` methods / 25 Chromium Playwright scenarios.

## v27.17.4 UI Core & Workspace Foundation extension

- `UiCoreWorkspaceFrontendContractTest` protects bundle order, declarative registries, semantic tokens, isolated theme selectors and profile whitelist fields.
- `design-system-shell.spec.js` now switches workspace, layout and palette, waits for automatic persistence, reloads and verifies the same configuration.
- Release checks verify every built-in theme package exposes the UI Core v1 token contract and does not leak selectors into another theme.
- Deployment smoke verifies all UI Core JS/CSS assets and the workspace/layout controls in the authenticated shell.
- Classic fallback remains covered in the same Playwright scenario.
- Baseline grows to 95 Java test classes / 496 `@Test` methods / 25 Chromium Playwright scenarios.

## v27.17.3 Java Contract Build Gate Hotfix extension

- fixes the escaped string literal in `CalendarMobileExperienceFrontendContractTest`;
- compiles every `*FrontendContractTest.java` with `javac` and local JUnit stubs inside `release-check.sh`;
- catches Java syntax errors before Maven, image build and remote deployment;
- keeps the 94 / 492 / 25 regression baseline unchanged.

## v27.17.2 Calendar Timeline Readability Hotfix extension

- `editor-modals.spec.js` now opens Day mode on desktop after creating a real timed task at `17:41`.
- The browser contract verifies the task card is at least 47 px high and both title/detail rectangles remain within the event bounds.
- `calendarExperienceVisualEnd(event)` reserves one visual hour for short non-shift events so readable cards cannot overlap the following lane item.
- Static frontend contracts protect the 48 px desktop floor, compact-event class and task time-first detail line.
- Test counts stay unchanged: 94 Java test classes / 492 `@Test` methods / 25 Chromium Playwright scenarios.

## v27.17.1 Calendar & Notes Quality Hotfix extension

- `multiple-daily-notes.spec.js` now runs at desktop width and proves that the notes editor stacks according to the selected-day rail width instead of the global viewport.
- `calendar-mobile-experience.spec.js` creates a real important date and protects the explicit «Весь день» rail in Day mode.
- Reminder projection uses `displayAt` / `remindAt` date identity, so delivery metadata from another date cannot appear on the focused day.
- `IMPORTANT_DAY` notification delivery is not rendered as a duplicate timed event over a shift.
- `editor-modals.spec.js` persists `17:41`, protecting one-minute deadline precision.
- Static contracts protect the container-query boundary, all-day composition and `step="60"` input contract.
- No schema or backend API change; Flyway remains V36.

## v27.17.0 Calendar Mobile Experience extension

- Month / Week / Day scale state and focused date survive reload as local UI preferences.
- Week mode exposes seven date targets and a selected-day agenda.
- Day mode projects immutable shift segments, tasks, reminders and overtime into one hourly timeline.
- Today Dashboard opens the Day scale; the complete selected-day editor and Classic remain fallbacks.
- `CalendarMobileExperienceFrontendContractTest` and `calendar-mobile-experience.spec.js` protect the new layer.
- No schema or backend API change; Flyway remains V36.

## v27.16.3 Time Settings Transaction Hotfix extension

- Shift-template inputs carry a revision counter, so active user edits survive timezone-save refresh renders.
- Debounced and manual built-in updates run through one promise queue; stale requests cannot repaint newer input.
- A completion only marks the exact captured draft revision as committed.
- The existing timezone E2E scenario remains the end-to-end guard for `08:30 → 06:30` reprojection.
- No schema or backend API change; Flyway remains V36.

## v27.16.1 Today Runtime & Repository Truth Hotfix extension

- `35-today.js` no longer resolves `openQuickActions` while the earlier bundle is being evaluated; the callback resolves it only after `50-tasks.js` has loaded.
- `TodayDashboardFrontendContractTest` rejects the direct forward-reference form.
- `release-check.sh` verifies both the safe deferred binding and the absence of the unsafe direct binding.
- The CI failure presented as 24 broken Playwright scenarios, but every scenario shared the same page error: `openQuickActions is not defined`.
- README, API, roadmap, release checklist and architecture documentation now match Java 17, v27.16.1 and Flyway V36.
- No schema or backend behavior change; Flyway remains V36.


## v27.16.0 Today Dashboard extension

- `TodayDashboardFrontendContractTest` protects the default `#today` route, additive composition over existing stores, instant-based shift progress and responsive dashboard layout.
- `today-dashboard.spec.js` creates a real task from the dashboard, verifies immediate composition, opens the selected calendar day and returns through the brand route.
- The dashboard reads existing calendar, shift occurrence, overtime, task and important-date state; no `/api/today` endpoint or duplicate persistence model was added.
- Active shift progress and countdown use immutable `startInstant` / `endInstant`, while visible dates and ranges stay projected in the selected DutyLog timezone.
- Mobile navigation remains five destinations: Today, Calendar, Overtime, Tasks and More. Important dates remain available from the dashboard and their full board.
- Flyway remains V36.

Historical extension: v27.2.31 adds an authenticated, CSRF-aware deployment smoke-test regression.

Historical extension: v27.2.30 adds host-nginx deployment, loopback publication and two-stage smoke-test guards.

This release converts the successful v27.2.6 manual acceptance pass into an automated safety net. The goal is not a vanity coverage percentage; every test names a product promise that must remain true.




## v27.15.0 Design System & Mobile Shell Foundation extension

- `DesignSystemMobileShellFrontendContractTest` protects the layered CSS load, branded shell DOM, accessible nav icons, server-safe enum and reduced-motion/mobile boundaries.
- `ProfileControllerTest` protects persistence of `themeConfig.shellMode=classic` and rejection of unknown shell values.
- `design-system-shell.spec.js` protects the default DutyLog Next shell, fixed mobile navigation, no horizontal overflow, ARIA current state and an instant Next → Classic → Next fallback.
- The design layer is additive: existing calendar, overtime, tasks, notes, notifications and API handlers are unchanged.
- Flyway remains V36.

## v27.14.2 Calendar Notes Persistence E2E Hotfix extension

- `calendar-persistence.spec.js` creates the first note through the visible `#noteAdd` empty-state action.
- The scenario awaits `POST /api/notes` followed by the debounced `PATCH /api/notes/{id}` instead of waiting for the retired legacy day-note `PUT`.
- The same browser flow still protects shift selection, emoji persistence, month navigation and a full authoritative reload.
- Flyway remains V36 and the production note/tombstone implementation is unchanged.


## v27.14.1 Mobile Notes Tombstone Hotfix extension

- `MobileSyncServiceTest.clearCreatesAVersionedTombstoneSoStaleOfflineCreatesCannotOverwriteIt` protects monotonic optimistic versions after clearing the last note.
- `MobileSyncServiceTest.explicitClearFlagsWinOverValuesInTheSamePatch` protects clear precedence while retaining the empty v1 row.
- `MobileSyncControllerTest.legacyClearDeletesEmptyRowWhileV1ClearKeepsVersionedTombstone` protects the intentional legacy/v1 behavioural split through real HTTP contracts.
- `DayNoteService` receives an explicit `preserveEmptyDayEntry` policy only from versioned sync; normal note CRUD and legacy sync semantics remain unchanged.
- Flyway remains V36.


## v27.14.0 Multiple Daily Notes extension

- `DayNoteServiceTest` protects independent siblings, primary compatibility shadow, pin/order semantics, deletion promotion and owner isolation.
- `DayNoteControllerTest` covers legacy/v1 aliases, validation, range reads, module guards, CSRF and ownership.
- `MultipleDailyNotesFrontendContractTest` protects the list/editor boundary, dedicated endpoints, merged debounce patches, migration and offline read-only behavior.
- `multiple-daily-notes.spec.js` exercises two notes through edit, pin, reorder, reload and individual deletion.
- `pwa-offline.spec.js` proves cached notes remain readable while mutations are disabled offline.
- Flyway V36 migrates non-empty `day_entries.note` rows exactly once and keeps the legacy field as a primary-note shadow.

## v27.13.0 Temporal Consistency & Legacy Cleanup extension

- `OvertimeServiceTest.compatibilitySummaryAndLedgerUseProjectionAndNeverReviveLegacyDayHours` protects projected zero values and removes legacy `day_entries` fallback behaviour.
- `OvertimeServiceTest.canonicalPreviewUsesProfileTimezoneForDstGapAndOverlap` protects deterministic DST gap/overlap calculation in the canonical user zone.
- `OvertimeControllerTest.previewUsesCanonicalProfileTimezoneThroughLegacyAndV1Aliases` protects both preview routes.
- `QuickScenarioServiceTest.fixedTimeScenarioRebasesAcrossExtremeZonesAndRoundTrips` protects signed day offsets and UTC+14 ↔ UTC−11 round trips.
- `TemporalConsistencyFrontendContractTest` protects projected month totals, canonical preview wiring and the signed-offset scenario editor.
- Flyway V35 adds `quick_scenarios.end_day_offset`; overtime/FIFO rows are unchanged.


## v27.12.1 Midnight Projection Contract Hotfix extension

- `OvertimeServiceTest.ровныеСуткиХранятсяПополамНоПроецируютсяПоКалендарнымДням` distinguishes persisted 12/12 source credits from the current-zone civil-day projection.
- An exact `08:00 → 08:00` 24-hour source remains two immutable 12-hour credits, while the ledger projection correctly totals 16 hours before midnight and 8 hours after midnight in the same timezone.
- The test verifies the invariant that all 1440 earned minutes and the account balance remain unchanged.
- Flyway remains at V34; no production schema or FIFO rewrite is involved.


## v27.12.0 Zoned Daily Projection Engine extension

- `OvertimeServiceTest.dailyProjectionRedistributesExactMinutesWithoutMovingFifo` protects `2/2 → 1/3 → 0/4` projection and unchanged earned/used/balance totals.
- `OvertimeServiceTest.accountPageFiltersByProjectedCalendarDate` protects server-side date filters against stale source dates.
- `OvertimeDailyProjectionFrontendContractTest` protects daily subtotals and source-credit edit/delete guards.
- `overtime-daily-projection.spec.js` reproduces timezone movement with a partial FIFO usage through the real API and ledger UI.
- Flyway remains at V34 because this is a pure projection layer over already absolute intervals.


## v27.11.4 Task Deadline & Reminder Timezone Hotfix extension

- `TaskServiceTest` protects `14:10 Asia/Yekaterinburg → 12:10 Europe/Moscow`, one unchanged `dueInstant`, midnight date crossing and explicit legacy migration.
- `ProfileControllerTest` proves the canonical profile update automatically reprojects timed deadlines.
- `NotificationServiceTest` proves a task reminder keeps the same absolute instant after projection.
- `TelegramNotificationServiceTest` proves Telegram prefers `remindAtInstant` instead of reinterpreting projected wall-clock time.
- `TaskDeadlineTimezoneFrontendContractTest` protects source-deadline rendering, authoritative task refresh and the migration wizard.
- `task-details.spec.js` reproduces the reported overdue-task scenario through the real UI.
- Flyway V34 adds nullable deadline snapshot columns without guessing historical zones.


## v27.11.3 Shift Template & Reminder Timezone Hotfix extension

- `ShiftTypeServiceTest` protects template rebasing from UTC+5 to UTC+3 and confirms untimed day-off templates are unchanged.
- `ProfileControllerTest` proves a canonical timezone update persists the projected built-in times.
- `NotificationServiceTest` proves a next-day projected shift reminder is calculated from `shiftStartInstant`.
- `ShiftOccurrenceFrontendContractTest` protects authoritative template refresh and occurrence-based reminder wiring.
- `important-timezone.spec.js` checks template values, immutable occurrence projection and notification time in one user journey.
- Flyway remains V33.

## v27.11.2 E2E Stability Hotfix extension

- `editor-modals.spec.js` waits for the post-assignment `/api/calendar` response before navigating, so an aborted in-flight refresh cannot become a false browser-console failure.
- `important-timezone.spec.js` protects both the compact source range and canonical source date without coupling the test to an unused long-date presentation.
- Production shift occurrence logic and Flyway V33 are unchanged.


## v27.11.1 Shift Occurrences & Calendar Projection extension

- `ShiftOccurrenceServiceTest` proves absolute identity survives a timezone move, `08:30 GMT+5` becomes `06:30 GMT+3`, a late shift can move completely to the next date, unrelated note saves do not guess legacy zones, and explicit migration affects only selected rows.
- `ShiftOccurrenceFrontendContractTest` protects occurrence segmentation, projected-date indexing, migration UI, authoritative refresh and Service Worker activation.
- `important-timezone.spec.js` covers both same-day reprojection and a complete `03 July 23:00 → 04 July 01:00` date move.
- OpenAPI documents the shift occurrence and legacy migration contracts.
- Flyway V33 adds immutable occurrence snapshot columns and an overlap index.


## v27.10.0 Task Details extension

- `TaskServiceTest` protects description persistence, clearing, search participation, length validation and owner isolation.
- `TaskControllerTest` covers authoritative single-task reads through legacy and `/api/v1` routes, including foreign-owner `404` behaviour.
- `TaskDetailsFrontendContractTest` protects the separate read-first modal, explicit edit boundary, description rendering and offline snapshot fallback.
- `task-details.spec.js` verifies details open from a card, description persistence, checklist interaction and reload behaviour.
- Flyway V32 adds nullable `day_tasks.description` without turning quick capture into a mandatory structured form.


## v27.9.4 Overtime Split Projection Contract Hotfix extension

- Cross-midnight credit E2E asserts the seven-hour selected-day segment and the full eight-hour account balance independently.
- Credit usage references expose `allocationPartIndex` and `allocationPartCount`.
- Paged ledger rendering no longer depends on a previously loaded full overtime account to show split-part badges.
- Backend and frontend contracts verify stable split metadata and rendering.

## v27.9.3 Overtime Preflight Integrity Hotfix extension

- `OvertimeServiceTest` proves a rejected usage create does not add a ghost row and a rejected usage edit preserves the original hours, reason and allocation.
- `TaskAndShiftEditorsFrontendContractTest` protects the intentional `delete entire time-off` wording.
- `overtime-editor-modals.spec.js` explicitly sets zero break and zero planned deduction before asserting an eight-hour overnight credit.
- Flyway remains at V31 because this hotfix changes command ordering and tests, not schema.


## v27.9.2 Overtime Ledger Integrity Hotfix extension

- `OvertimeServiceTest` protects two credits and two usages when one split time-off is deleted.
- `OvertimeLedgerIntegrityFrontendContractTest` protects per-allocation fallback, detached-fragment rendering and whole-time-off labels.
- `overtime-editor-modals.spec.js` verifies the complete staging scenario and checks the surviving account through the API.
- Flyway remains at V31 because the hotfix changes transactional and browser behaviour, not schema.

## v27.9.1 Overtime Allocation Rendering Hotfix extension

- `OvertimeIntervalEngineFrontendContractTest` proves exact ranges use the defined `formatDateHuman` helper and forbids the missing `formatDate` symbol.
- `overtime-editor-modals.spec.js` creates `17:00–01:00`, consumes all eight hours and verifies both midnight-split labels while the shared fixture rejects browser page errors.
- `release-check.sh` executes `allocationRangeLabels()` in a Node VM with a cross-midnight allocation.
- Flyway remains at V31.

## v27.9.0 Overtime Interval Engine extension

- `OvertimeServiceTest` proves exact source-minute ranges, timezone reprojection, deterministic restoration after deletion and legacy reconstruction.
- `OvertimeControllerTest` covers preview/migrate aliases, module/CSRF boundaries and exact reconstructed API output.
- `OvertimeIntervalEngineFrontendContractTest` guards one timezone UI, exact range rendering, cross-midnight segmentation, migration wizard and shift work/break wording.
- `PostgreSqlMigrationContractTest` and release-check keep Flyway continuous through V31.
- Existing browser scenarios continue to verify authoritative calendar refresh using the single canonical timezone.

## v27.8.1 Timezone Projection Refresh extension

- `TimezoneProjectionRefreshFrontendContractTest` protects profile-before-calendar boot ordering, fresh calendar propagation, IndexedDB snapshot bypass and the joint calendar/ledger refresh.
- `important-timezone.spec.js` now reproduces the staging bug with an existing dated shift before changing zones, then proves the card refreshes to `08:30 Asia/Yekaterinburg → 06:30 Europe/Moscow` without stale `Europe/Kyiv`.
- Flyway remains at V30 because the fix changes projection refresh order, not persisted data.

## v27.8.0 Zoned Work Intervals extension

Automated coverage now additionally verifies:

- one dated work shift resolves to stable UTC start/end instants;
- `08:30–17:00 Asia/Yekaterinburg` projects to `06:30–15:00 Europe/Moscow` without changing work semantics;
- day/month API responses carry work/display projections and elapsed/net minutes;
- display-timezone saves refresh the active month without rewriting schedule records;
- new calculated overtime credits persist absolute interval identity and source IANA timezone;
- duration and overlap checks use actual instants across DST transitions;
- unchanged edits cannot move an existing overtime interval after the account work timezone changes;
- interval edits retain the credit's original source timezone;
- V30 leaves historical local-only overtime unmodified;
- the ledger prefers display projection while retaining source work context.

Manual staging acceptance checks shift projections, timezone reload behaviour, new overtime rows, legacy-row stability and unchanged FIFO balances.


## v27.7.1 Task and ledger layout hotfix extension

Automated coverage now additionally verifies:

- day-task cards use a stable checkbox/body/delete grid;
- inline subtasks occupy the content row without the old mobile offset;
- the overtime table exposes a dedicated Actions header and credit-level action cell;
- FIFO usage controls are explicitly labelled as usage edit/delete actions.


## v27.7.0 Time Foundation extension

Automated coverage now additionally verifies:

- persisted work and display IANA timezone settings, validation and reload behaviour;
- one absolute server instant projected through `/api/time/context` and `/api/v1/time/context`;
- deterministic DST gap and overlap resolution;
- overnight, 24-hour and DST-crossing work intervals measured in real elapsed minutes;
- notification ordering/filtering by `Instant` and explicit display projections;
- Telegram delivery deduplication by `remind_at_instant` with a legacy-local fallback for records that predate absolute identity;
- task overdue calculation in the user's work timezone;
- browser calendar dates remaining work-zone based while absolute UI timestamps use display timezone;
- Flyway V29 continuity and the new `TIMESTAMPTZ` migration contract.

Manual staging acceptance additionally checks that changing display timezone never rewrites birthdays, important dates, notes, task dates, shift source data or legacy overtime rows. Absolute shift and new overtime display projections are allowed to change.

## v27.6.3 task polish and consistency extension

- `TaskServiceTest` protects final-state parent/subtask deadline rules, same-day acceptance, clearing and open-first day/range order.
- `TaskControllerTest` proves stable errors and compatible behaviour through legacy and `/api/v1` routes.
- `TaskAndShiftEditorsFrontendContractTest` protects V28, client validation, graphical progress and completed grouping.
- `task-modules.spec.js` covers invalid deadline feedback, persisted subtask dates, progressbar semantics and immediate optimistic reordering.
- Flyway V28 adds a nullable date-only deadline without expanding subtasks into recursive tasks.

## v27.6.2 tasks and subtasks extension

- `TaskServiceTest` covers ordered creation, reconciliation, checklist-text search, owner-scoped child updates and explicit parent completion.
- `TaskControllerTest` protects create/update payloads, the versioned child PATCH route, module guards and foreign-owner `404` behaviour.
- `TaskAndShiftEditorsFrontendContractTest` protects the one-level editor, compact progress and non-recursive persistence contract.
- `task-modules.spec.js` covers the browser flow `0/2 → 1/2 → 2/2`.
- Flyway V27 creates ordered cascade-owned `task_subtasks`; recursive nesting is intentionally absent.


## v27.6.1 quick capture polish extension

- `MobileTasksInboxFrontendContractTest` now protects the collapsed Inbox tray, direct universal draft field and module-aware note/important-date actions.
- `task-modules.spec.js` preserves the complete `+ → text → Inbox → task` path without the removed intermediate capture modal.
- Quick add remains visible when any of Tasks, Notes, Important dates or Overtime is enabled.
- Existing Inbox API, IndexedDB idempotency, task conversion and Flyway V26 remain unchanged.


## v27.6.0 mobile tasks and Inbox extension

- `InboxServiceTest` covers quick capture, idempotent client operation ids, owner isolation, archive/restore, delete and atomic conversion into a structured task.
- `InboxControllerTest` covers `/api/inbox` and `/api/v1/inbox`, module guards, CSRF, authentication, validation and foreign-id indistinguishability.
- `MobileTasksInboxFrontendContractTest` protects the dedicated task modal, mobile full-screen editor, floating quick action, explicit overtime wording and IndexedDB `captureInbox` queue.
- `TaskServiceTest` now covers lower-case category/tag normalisation, metadata suggestions, tag-aware search and service-level text length enforcement.
- `task-modules.spec.js` uses the new task editor and adds the complete quick-capture → Inbox → task browser flow.
- Flyway V26 creates `day_task_tags` and `inbox_items` while preserving all v27.5.2 Telegram and v27.5.0 backup/recovery checks.


## v27.5.2 Telegram detached-owner hotfix

- `TelegramLinkDetachedOwnerIntegrationTest` reproduces the polling boundary where the repository/service transaction has ended before the command handler reads the linked account.
- The test proves the persisted IANA timezone remains readable from the returned detached `AppUser`, preventing the production `LazyInitializationException` seen in `/today`, `/tomorrow` and quick-action aliases.
- The repository entity graph is the regression boundary; no Open Session in View workaround or global eager mapping is introduced.


## v27.5.2 Telegram command menu and quick actions extension

- `TelegramBotServiceTest` verifies the `setMyCommands` HTTP payload, descriptions and fail-closed registration guards.
- Every bot response carries a persistent compact reply keyboard with six safe read-only actions.
- `TelegramCommandServiceTest` proves button labels dispatch to the same timezone-aware handlers as slash commands.
- v27.5.1 coverage for partial `/today` and `/tomorrow` summaries and compact mobile synchronization remains preserved.


## v27.5.0 backup and recovery extension

- Backup creation fails closed without the environment or active Compose file, prevents concurrent writers and atomically publishes a verified dump plus SHA-256.
- Retention is regression-tested with a fake Docker boundary, so the newest backup and checksum survive while old pairs are removed.
- Backup freshness checks enforce age, checksum and archive readability contracts.
- A failed real restore restarts an application that was running before the attempt.
- The isolated restore drill uses generated temporary container/volume names, no network and no published ports, and always performs exact cleanup from its own process.
- Systemd service/timer rendering is regression-tested without modifying the CI host service manager.


## v27.4.3 reminder timezone and sync UX extension

- Browser reminder API responses include an absolute UTC instant derived from the user's saved IANA timezone.
- Frontend delivery compares the absolute instant with `Date.now()` and uses the DutyLog timezone for source-date polling.
- Task create/edit paths accept and persist a 3-minute reminder.
- Overtime credits use explicit start/end fields only; the short text interval is absent from runtime HTML and JavaScript.
- Manual synchronization visibly enters a busy state and reports a final result.
- The browser regression baseline includes 12 Playwright scenarios.

## v27.4.2 timezone and critical regression extension

- `remember-me.spec.js`: persistent-cookie restoration in a fresh browser context, parallel bootstrap reads and logout revocation.
- `important-timezone.spec.js`: compact generated timezone selector, explicit profile save, removal of manual region/offset controls and persistence after reload.
- `editor-modals.spec.js`: task field persistence plus custom shift create/edit/assign/reload.
- `smoke-test.sh`: authenticated read-only profile, module, session and identity checks after the existing CSRF login.
- `production-smoke-test.sh`: HTTPS-only authenticated wrapper suitable for post-deploy production verification.

## Manual acceptance captured

The following behaviours were verified manually before this baseline was created:

- a shift and emoji marker persist while Notes and Overtime are disabled;
- notes survive day/month navigation, refresh, logout/login and module disable/enable;
- exported Markdown ZIP contains the persisted note;
- Telegram status is not requested while the Telegram module is off;
- task reminder controls are disabled while Notifications is off;
- browser reminders are delivered for tasks and shifts;
- schedule fill, templates, overtime, FIFO usage, English UI, appearance settings and password minimum continue to work.

## Automated matrix

| Product promise | Automated guard |
|---|---|
| Calendar days retain identity and do not collapse into `state.days[undefined]` | `CalendarMonthReloadContractTest` |
| Fill persists every date and a fresh calendar read returns it | `CalendarFillPersistenceContractTest`, `DayEntryServiceTest` |
| Disabled Notes/Overtime do not block shift or marker saves | `DayModuleIsolationTest` |
| Hidden note/overtime data is preserved and reappears after re-enable | `DayModuleIsolationTest` |
| Notes export is owner-scoped, bounded and valid | `ExportControllerTest`, `NoteExportServiceTest` |
| Shift/task/important-day/digest reminder times are calculated correctly | `NotificationServiceTest` |
| Completed tasks and silent shift types create no reminder | `NotificationServiceTest` |
| Reminder data never leaks between users | `NotificationServiceTest` |
| Notifications API is guarded by its module and validates input | `NotificationControllerTest` |
| Telegram/Notifications dependency cascade remains consistent | `ModuleDependencyTest` |
| Task reminder fields persist and stale lead minutes are cleared | `TaskReminderServiceTest` |
| Task CRUD, day/range lists, board filters and pagination remain correct | `TaskServiceTest`, `TaskControllerTest` |
| Task API keeps validation, CSRF, authentication, module and ownership boundaries | `TaskControllerTest` |
| Important-day CRUD, owner-scoped lists and validation remain correct | `ImportantDayServiceTest`, `ImportantDayControllerTest` |
| Monthly 31st-day and yearly leap-day recurrence rules remain stable | `ImportantDayServiceTest` |
| Important dates API keeps v1 aliases, CSRF, authentication, module and ownership boundaries | `ImportantDayControllerTest` |
| Built-in and custom shift types keep defaults, CRUD, ownership and deletion semantics | `ShiftTypeServiceTest`, `ShiftTypeControllerTest` |
| 2/2, day/night/48 and weekday-aware 5/2 patterns survive month, year and leap-day boundaries | `CalendarPatternServiceTest`, `CalendarPatternControllerTest` |
| Schedule fill preserves notes, emoji and overtime while overwrite rules remain stable | `CalendarPatternServiceTest` |
| Browser presets keep their canonical sequences and rotate 5/2 by the selected weekday | `ScheduleTemplateFrontendContractTest` |
| Disabling Notifications tears down browser polling | `BrowserNotificationFrontendContractTest` |
| A stale `MODULE_DISABLED:notifications` response cannot become a recurring 403 loop | `BrowserNotificationFrontendContractTest` |
| Task reminder controls reflect the Notifications module | `BrowserNotificationFrontendContractTest` |
| Quick-scenario defaults, CRUD, FIXED_TIME consistency and deletion semantics remain stable | `QuickScenarioServiceTest`, `QuickScenarioControllerTest` |
| Quick-scenario API keeps v1 aliases, validation, CSRF, authentication, module and ownership boundaries | `QuickScenarioControllerTest` |
| Overtime interval splitting, overlap protection and FIFO remain correct | `OvertimeServiceTest` |
| Overtime pages, filters, exports, usage reallocation and deletion rules remain stable | `OvertimeAccountQueryServiceTest`, `OvertimeControllerTest` |
| Overtime API keeps legacy/v1 aliases, validation, CSRF, authentication, module and ownership boundaries | `OvertimeControllerTest` |
| Mobile/web authentication boundary, ownership and module guards remain enforced | `MobileSecurityBoundaryTest`, `OwnershipIsolationTest`, `ModuleSecurityTest` |
| Mobile access/refresh rotation, expiry, logout and session ownership remain stable | `MobileAuthServiceTest`, `MobileAuthLifecycleControllerTest`, `ProfileSessionControllerTest` |
| Android v1 operation ids are owner-scoped and applied exactly once | `MobileSyncServiceTest`, `MobileSyncControllerTest`, `MobileV1ContractTest` |
| Android v1 conflicts, tombstones and per-item rejections preserve neighbouring writes | `MobileSyncServiceTest`, `MobileSyncControllerTest` |
| PostgreSQL foreign keys never target tables missing from the migration history | `PostgreSqlMigrationContractTest`, `migration-smoke-test.sh` |

## Running the gate

```bash
mvn verify
bash deploy/scripts/release-check.sh
```

JaCoCo HTML output (generated by Maven `verify`, not by IntelliJ's plain JUnit runner):

```text
target/site/jacoco/index.html
```

Detailed Windows/IntelliJ instructions: [`docs/TESTING.md`](TESTING.md).

GitHub Actions uploads the same directory as the `jacoco-report` artifact even when a later CI step fails.

## Interpretation

A green test suite means the listed contracts still hold. It does not replace exploratory/manual testing for browser permissions, operating-system notification presentation, responsive layout, service-worker lifecycle or real PostgreSQL deployment. Those remain acceptance checks at release boundaries.


## v27.2.16 profile and administration extension

- `ProfileControllerTest`: safe profile reads, full/clearing updates, locale, onboarding, Theme Builder allow-list, normalization, clamping, corrupt stored JSON, validation, authentication and CSRF.
- `ProfileSessionControllerTest`: owner-scoped device lists, token secrecy, one-session revocation, CSRF, IDOR-safe `404`, and revoke-all after password changes.
- `AppSettingsServiceTest`: default/database registration sources, audit metadata and legacy boolean parsing.
- `UserAdminServiceTest`: search, filters, pagination, current/bootstrap flags, promotion/demotion safety, last-admin protection, password reset and session revocation.
- `AdminControllerContractTest`: operational status secrecy, full admin API contract, stable error envelopes, registration toggle and CSRF.


## v27.2.17 admin test context hotfix

- `UserAdminServiceTest` no longer sets an incomplete bootstrap-admin property pair on the full Spring context.
- The service is constructed inside the transactional test with an explicit bootstrap username, so bootstrap-admin protections remain covered without invoking production bootstrap side effects.


## v27.2.18 mobile auth and sync lifecycle extension

- `MobileAuthServiceTest`: token hashing, login validation, refresh rotation, expiry, access authentication, last-used throttling, logout, session flags, owner isolation and device normalization.
- `MobileAuthLifecycleControllerTest`: legacy/v1 auth routes, refresh replay rejection, logout by access or refresh, owner-scoped device management and stable validation envelopes.
- `MobileSyncServiceTest`: direct validation, idempotent replay, owner-scoped operation ids, optimistic conflicts, no-op/module rejection, malformed-date batch isolation, clear precedence, tombstones and foreign-shift rejection.
- `MobileSyncControllerTest`: HTTP-level malformed-item isolation, same-batch duplicate ids, bean validation and legacy-delete versus v1-tombstone compatibility.
- The documented v27.2.17 baseline is corrected to 193 `@Test` methods; v27.2.18 contains 223.


## v27.2.19 PostgreSQL migration and CI metadata hotfix

- `PostgreSqlMigrationContractTest` scans the ordered PostgreSQL Flyway chain and validates every `REFERENCES` target against tables created by the same or an earlier migration.
- The clean PostgreSQL Docker smoke test remains the authoritative runtime check.
- CI, staging and production derive release metadata from `pom.xml`; semantic versions are no longer duplicated as stale literals in workflow files.

## v27.2.20 Telegram bot and delivery hardening

- `TelegramCommandServiceTest` locks command aliases, parsing, task/overtime/time-off mutations, summaries and invalid input.
- `TelegramBotServiceTest` locks polling offsets, link codes, unlinked chats, command delivery, malformed updates, fail-closed Telegram responses and token redaction.
- `TelegramNotificationServiceTest` locks due windows, deduplication, retry semantics, per-link isolation and reminder formatting.
- `TelegramControllerTest` locks module guards, link-code/status/settings/unlink endpoints, authentication and CSRF.


## v27.2.21 Telegram date-validation hotfix

- Impossible explicit Telegram dates are converted to the stable `BAD_REQUEST` API contract.
- Telegram HTTP test expectations are fully registered before the first mock request.


## v27.2.22 security infrastructure and auth hardening

- `ApiVersionFilterTest`: stable v1 metadata and legacy mobile deprecation headers.
- `SecurityHeadersFilterTest`: CSP, HSTS, frame, referrer, permissions and MIME-sniffing protections.
- `RequestDiagnosticsFilterTest`: trusted/generated request IDs, reflection rejection and failure-path correlation.
- `BearerTokenAuthenticationFilterTest`: public-route exclusions, user/admin authorities, token touch, invalid-token envelopes and case-insensitive Bearer schemes.
- `AuthenticationRateLimitFilterTest`: shared web/legacy/v1 alias buckets, independent IPs, forwarded/real IP handling and window reset.
- `SecurityEventLoggerTest`: structured audit fields, request context, IP precedence, control-character flattening and bounded values.
- `ApiErrorInfrastructureTest`: defaults, module metadata, writer contract, exception factories and hidden 500 details.
- `SecurityInfrastructureContractTest`: integrated headers, mobile version lifecycle, JSON 401/403 responses and correlation IDs.

## v27.2.23 security test contract and secret-safe logging hotfix

- JSON error responses are asserted by media-type compatibility, so an explicit UTF-8 charset remains valid.
- HTML navigation tests send `Accept: text/html`, while `/api/**` continues to use stable JSON 401 responses.
- Unexpected exception logs retain request correlation and exception type but omit throwable messages and stack traces that may contain secrets.



## v27.2.24 coverage floor and startup/module extension

- `AdminBootstrapServiceTest`: absent/partial configuration, credential validation, first admin creation, default-shift seeding, promotion, forced reset and one-time legacy cleanup.
- `ModuleRegistryContractTest`: normalized lookup, unique stable keys/orders, known acyclic dependencies and immutable contract lists.
- `ModuleServiceContractTest`: regular/admin visibility, locked modules, null/unknown updates, persistence, dependency activation and structured disabled-module errors.
- `CurrentUserServiceTest`: missing, unknown and valid principals.
- `NoteExportServiceTest`: count/select race limits, blank filtering, audit event, ZIP layout and YAML scalar escaping.
- `mvn verify` now enforces at least 88% instruction coverage and 70% branch coverage at bundle level.
- Baseline: 61 test classes and 327 `@Test` methods.


## v27.2.25 Playwright browser E2E extension

- `auth-onboarding.spec.js`: registration, automatic login, language persistence, Minimum preset and no repeated onboarding after reload.
- `calendar-persistence.spec.js`: shift, emoji and Markdown note persistence across month navigation and an authoritative reload.
- `task-modules.spec.js`: Tasks module activation, task creation/completion, disable/enable survival and restored UI state.
- `mobile-layout.spec.js`: phone viewport width, calendar selection and selected-day panel usability.
- `pwa-offline.spec.js`: active service worker, controlled reload, IndexedDB snapshot and offline shell startup.
- Happy-path fixtures reject browser console errors, uncaught exceptions, failed same-origin requests and unexpected same-origin HTTP errors.
- Java baseline remains 61 classes / 327 `@Test`; browser baseline is 5 Playwright tests.


## v27.2.26 Playwright selector and accordion hotfix

- Shift chips expose `aria-pressed` as the stable selected-state contract; E2E no longer assumes a non-existent visual `.on` class.
- `openDayModule` expands closed `<details>` blocks before tests interact with Notes controls.
- Calendar persistence and PWA offline tests now wait for the real debounced `PUT /api/days/{date}` only after the editor is visible.
- `.gitattributes` enforces LF in the repository and protects Linux deployment scripts from Windows line-ending conversion.
- Baselines remain 61 Java classes / 327 `@Test` methods and 5 Playwright tests.

## v27.2.27 Playwright marker accordion hotfix

- `calendar-persistence.spec.js` explicitly opens `data-day-module="core"` before filling the custom marker input.
- After an authoritative reload, the scenario reopens Notes and Marker before asserting persisted editor values.
- PWA/offline, onboarding, mobile layout and task-module scenarios remain unchanged.
- Baselines remain 61 Java classes / 327 `@Test` methods and 5 Playwright tests.


## v27.2.28 staging deployment gate and diagnostics hardening

- `Deploy staging` independently enforces `mvn verify`, the JaCoCo floor, release checks and all five Playwright scenarios before building an immutable image.
- The image build and clean PostgreSQL migration smoke test run even when no VPS is configured.
- Remote staging deployment is gated by the GitHub Environment variable `DUTYLOG_DEPLOY_ENABLED=true`.
- A disabled gate is a successful, explicit skip and cannot create `staging-tested-tree-*`; production promotion therefore remains impossible until a real staging smoke test succeeds.
- Enabled but incomplete environments fail before SSH with a list of missing setting names and without secret values.
- Production uses the same preflight but remains fail-closed.
- Baselines remain 61 Java classes / 327 `@Test` methods and 5 Playwright scenarios.

## v27.2.29 final security and product audit hardening

- `WebSessionInvalidationTest` proves that password changes and admin-role demotion invalidate existing browser sessions before another protected request is authorized.
- `WebAccountStateFilterTest` locks matching/stale auth-version behavior and keeps Bearer principals outside the browser-session check.
- `ClientIpResolverTest` and the expanded `AuthenticationRateLimitFilterTest` prove that untrusted forwarding headers cannot split brute-force buckets while managed edge headers remain usable.
- `ProfilePasswordTest`, `UserAdminServiceTest` and `AdminBootstrapServiceTest` lock the unified password minimum and auth-version increments.
- `MobileAuthTokenCleanupServiceTest` keeps expired mobile-session rows bounded.
- Flyway adds V23 for `users.auth_version`.
- Baseline: 65 Java test classes / 340 `@Test` methods and 5 Playwright scenarios.

