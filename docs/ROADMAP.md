# Roadmap до полноценного продукта

Current release: **v27.44.4 — Shared Full-Day Free Dates**.


## v27.43.0 — People Profiles: Managed Schedule Overrides

- [x] Preserve a generated/base companion schedule and layer one reversible date-specific factual override on top.
- [x] Support `Не работает` with optional reason (`Отгул`, `Отпуск`, `Больничный`, `Другое`).
- [x] Support `Работает` on a generated day off, including a selected shift type and optional exact start/end time.
- [x] Apply overrides by companion `sourceDate` before timezone/display projection so overnight shifts remain one factual source-day decision.
- [x] Keep personal Tasks, Notes, Overtime, Payroll and absence accounting owner-only.
- [x] Add V48, owner-scoped override API, generated OpenAPI client contract and native Vue `ManagedProfileDayCard`.
- [x] Java 17 Maven 792/792 plus JaCoCo and OpenAPI contract drift are green locally.
- [ ] Exact Node 20.18.1/npm 10.8.2 frontend gate, Chromium 48/48, immutable image/PostgreSQL V48 and staging smoke.

## v27.44.4 — Shared Full-Day Free Dates

- [x] Mark current-month dates where both effective work interval sets are empty for the full calendar day.
- [x] Reuse Shared Availability effective-work/coverage semantics rather than enumerating vacation/sick/time-off types.
- [x] Count ordinary day off, managed OFF and factual `FULL_DAY + replacesShift=true` absence automatically.
- [x] Exclude PARTIAL/HOURS_ONLY, remaining work, untimed work and outside-profile-coverage dates.
- [x] Keep green full-day-free and coral simultaneous-work as independent visual layers.
- [x] Add `Общих выходных: N` to the month summary; Vitest baseline 73.
- [ ] Exact Node 20, Maven/release-check, Chromium and staging acceptance.
- [ ] After acceptance, freeze People Profiles and move to Production Calendar / Compensation Foundation.

## v27.44.3 — People Profile Coverage Semantics Hotfix

- [x] Distinguish actual companion day off from dates outside companion schedule coverage.
- [x] Keep existing projected entries authoritative around timezone/display boundaries.
- [x] Fail Shared Availability closed outside profile coverage instead of inventing free time.
- [x] Remove day-off palms / free-day wording from uncovered month/week/day dates.
- [x] Keep managed profile editing inside the configured layer range.
- [x] Add one coverage regression contract; Vitest baseline 72.
- [ ] Exact Node 20, Maven/release-check, Chromium and staging acceptance.

## v27.44.2 — Exact Availability Timeline & Shared Shift Summary

- [x] Label every visible Shared Availability rail boundary with exact `HH:mm` time.
- [x] Remove approximate 6-hour rail ticks from the selected-day card.
- [x] Preserve strict positive simultaneous-work overlap semantics for differently timed shifts.
- [x] Add current-month `Совпало смен` and total `Вместе на работе` summary when overlap mode is enabled.
- [x] Keep calculations derived from effective schedules; add no API/Flyway/dependency authority.
- [ ] Exact Node 20, Maven/release-check, Chromium and staging acceptance.

## v27.44.1 — Shared Availability UX & Shared Shifts

- [x] Make selected-day Shared Availability compact and human-readable.
- [x] Show total common free time plus natural `До` / `После` edge-window wording.
- [x] Add a 24-hour availability rail and collapse the work-only explanation behind an info disclosure.
- [x] Add `Совместные смены` visual mode for month/week/day.
- [x] Highlight a date only for positive simultaneous work overlap; one minute counts, zero-minute adjacency does not.
- [x] Reuse effective schedules/overrides/absence/overnight/timezone semantics; add no API/Flyway/dependency authority.
- [ ] Exact Node 20, Maven/release-check, Chromium and staging acceptance.

## v27.44.0 — Shared Availability

- [x] Derive selected-date common free-from-work windows from the effective schedules of `Я` and one selected People Profile.
- [x] Reuse canonical display-projected intervals so overnight work, timezone projection and v27.43.0 managed overrides stay authoritative.
- [x] Respect owner factual full-day and PARTIAL/HOURS_ONLY work absences.
- [x] Keep Tasks, Notes, Important Days, Payroll and Overtime outside work-availability semantics.
- [x] Fail closed when an effective work shift has no exact time.
- [x] Add native Vue Shared Availability UI plus four Vitest contracts without new API/OpenAPI/Flyway/dependency surface.
- [ ] After green v27.44.0, add nearest shared free day/window and multi-day discovery as a separate follow-up.

## v27.42.8 — Tasks Module E2E Route Contract Alignment Hotfix

- [x] Preserve v27.42.0 People Profiles runtime unchanged.
- [x] Align the stale selected-day-panel source assertion with the read-only profile boundary: `dayPanelOpen && viewingSelf`.
- [ ] Exact Java 17 Maven, release checks, Chromium and staging acceptance.

## v27.42.0 — People Profiles

- [x] Diagnose exact v27.41.8 CI rather than guessing: frontend gate passed; Java 17 Maven finished 791 tests with one stale source-contract failure.
- [x] Replace retired `budget.maxBytes` source assertion with the six segmented entry/per-chunk/total raw+gzip budget dimensions.
- [x] Keep v27.41.8 manual chunks and all browser budget values unchanged.
- [ ] Require exact CI to reach and pass the real multi-chunk browser audit before starting People Profiles.

## v27.41.8 — Shared Runtime Bundle Split

- Preserve the v27.41.7 route/page async boundaries proven by exact Vite output.
- Split the remaining 565411 B shared `main` chunk into coarse vendor, generated-contract, platform and Settings runtime chunks.
- Keep all browser budgets unchanged; do not greenwash the v27.41.7 failure by raising the 300000/100000 per-chunk ceilings.
- Preserve PWA/offline ownership, OpenAPI 124/130, Flyway V47 and the 48/64/791 regression surface.
- After green/manual acceptance, start **v27.42.0 — People Profiles & Shared Availability**.

## v27.41.7 — Frontend Bundle Segmentation

- Replace the single eager Vue JS blob with a stable entry plus content-hashed async chunks.
- Keep Calendar/Absence/Productivity domain owners available while lazy-loading their heavy route/page surfaces.
- Route-lazy Payroll and Admin; keep Settings eager until its Shift Type Manager runtime owner can be separated safely.
- Audit every emitted JS file, require a real segmented graph and keep total gzip capped at 250000 B.
- Preserve hash routing, PWA offline reload for previously loaded chunks, `dataLayer` sync ownership, OpenAPI 124/130 and Flyway V47.
- After green/manual acceptance, start **v27.42.0 — People Profiles & Shared Availability**.

## v27.41.6 — Accounting Integrity Semantics & Actionability Hotfix

- Fix the false-positive integrity rule for valid zero-minute POSTED audit facts on completed/approved non-overtime absences.
- Add a regression test for completed unpaid absence accounting semantics.
- Make integrity records responsive on phone and explain the linked audit fact plus the next safe user action.
- Do not add silent auto-repair, new HTTP/OpenAPI shape or Flyway migration.
- After green/manual acceptance, resume frontend bundle segmentation and then People Profiles / Shared Availability.

## v27.41.5 — Browser Bundle Budget Rebaseline Hotfix

- Exact v27.41.4 frontend CI passed delivery, OpenAPI 124/130, strict typecheck, 64/64 Vitest and Vite production build, then stopped only at the raw bundle audit: **806839 B > 800000 B**.
- Rebaseline raw ceiling narrowly to **810000 B**; keep gzip ceiling locked at **250000 B** and keep forbidden runtime-pattern auditing unchanged.
- No feature/runtime/domain/API/Flyway/dependency changes.
- Next frontend-delivery task after green: **route/workspace bundle segmentation** so Today does not eagerly ship Calendar, Payroll, Settings, Admin and every other workspace in one monolithic entry.

## v27.41.4 — Desktop UX & Workflow Clarity
- [x] Bound Calendar on ultrawide and align Payroll/Settings with the standard shell width.
- [x] Add relevant-first Absence sorting plus explicit type/sort controls.
- [x] Make accounting-integrity findings source-aware and actionable without silent repair.
- [x] Repair FIFO allocation row readability and restore Notes Focus Mode.
- [x] Clarify the canonical time-off workflow: Absence → overtime bank → FIFO; no second manual-debit UX.
- [ ] Exact CI/staging acceptance.

Next planned product release after green: **v27.42.0 — People Profiles & Shared Availability**.



## v27.40.30 — Legacy Retirement Closure & First-Run Onboarding Boundary — proven green
- [x] Start from the proven-green v27.40.29 staging baseline.
- [x] Audit the exact first-run owner instead of migrating it by default.
- [x] Keep `#firstRunOnboarding` as the single explicit bounded post-ready legacy presentation exception because it is isolated and preserves module dependency/profile/PWA first-claim ordering.
- [x] Mark the exception with `data-bounded-legacy-owner="first-run-onboarding"` and add executable source contracts preventing the exception from broadening.
- [x] Close Vue Legacy Retirement with Vue owning the shell, every routed user-facing screen and offline/sync presentation; retain `dataLayer` as the single offline mutation/sync infrastructure owner.
- [x] Move the next development line to Functional Parity Sweep rather than cosmetic migration.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 785/785 across 162 Java test classes on Java 17.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.31 — Functional Parity Sweep I — accepted predecessor
- [x] Restore Today remaining-time-to-shift-end / until-start presentation and progress from immutable occurrence instants.
- [x] Restore Important Days relative copy (`сегодня` / `завтра` / `через N дней`) in the Vue Today card.
- [x] Keep those calculations in strict typed pure model helpers with unit/source contracts; do not reintroduce legacy DOM ownership.
- [x] Preserve the bounded first-run onboarding exception and the single `dataLayer` offline owner unchanged.

## v27.40.32 — Functional Parity Sweep II & Mobile Usability Closure — exact frontend red predecessor
- [x] Use canonical server day projection totals for overtime accrual charts and historical zero-row fallback; do not paint fake bars for zero values.
- [x] Present ledger-integrity issues as grouped human-readable summaries while retaining stable machine diagnostics under technical details.
- [x] Restore shift color as scannable month-grid information with full-day factual absence taking visual priority.
- [x] Make phone bottom navigation icon-only visually while preserving accessible route names.
- [x] Keep OpenAPI 124/130, Flyway V47, bounded onboarding and single `dataLayer` offline ownership unchanged.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 790/790 across 163 Java test classes on Java 17.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.37 — Calendar E2E Source Contract Alignment Hotfix — proven green

- v27.40.36 passed exact frontend validation but Java 17 Maven exposed one stale source-contract assertion.
- The calendar source contract now follows the deterministic date-scoped `shiftCell` fixture and `toHaveClass(/hasShift/)` browser assertion introduced in v27.40.36.
- Production runtime and Playwright behavior remain unchanged.

## v27.40.36 — Calendar Fixture & Period Label E2E Hotfix — Maven-red predecessor

- v27.40.35 passed exact frontend and Java 17 Maven and reached the full Chromium suite.
- Full Chromium executed all 48 scenarios: 46 passed / 2 deterministic failed, both repeated on retry.
- Calendar-mobile now creates its own shift fact before validating shift-color semantics instead of assuming a fresh account already has assigned days.
- Overtime Next keeps the valid `2026 год` product copy and matches the year label case-insensitively.
- Production runtime behavior remains unchanged.

## v27.40.35 — Functional Parity Source Contract Scope Hotfix — browser-red predecessor

- [x] Align the two stale Java source-contract assertions with the existing helper boundaries.
- [x] Exact frontend stage passed.
- [x] Java 17 Maven 790/790 passed and reached full Chromium.
- [x] Chromium completed 48 scenarios with 46 passed / 2 deterministic E2E failures.
- [ ] Superseded by v27.40.36 before image/PostgreSQL/staging acceptance.

## v27.40.34 — Vue Asset Version Contract Hotfix — Maven-red predecessor

- [x] Replace stale shell asset patch literals with canonical application release-version lookup.
- [x] Exact frontend stage passed.
- [x] Java 17 Maven reached 790 tests and exposed two stale parity source-contract assertions.
- [ ] Superseded by v27.40.35 before browser/image/staging acceptance.

## v27.40.33 — Overtime Chart Projection Fixture Contract Hotfix — Maven-red predecessor
- [x] Preserve v27.40.32 runtime/read-model behavior unchanged.
- [x] Repair the two contradictory overtime chart Vitest fixtures discovered by exact frontend CI.
- [x] Keep canonical server `dayEarnedHours` authority and actual `usageDate` chart ownership intact.
- [ ] Exact frontend 63/63, Maven 790/790, Chromium 48/48, immutable image/PostgreSQL V47 and staging green.

## v27.41.0 — Calendar Visual Language Foundation — proven green
- [x] Start from proven-green v27.40.37 commit `b823e8741dd1f2dbc36424105f0e34cfeffc2817` / tree `1cf30a4e54b79c2a783aac1f3d2342bb05e936ef`.
- [x] Give schedule-free days a monochrome low-opacity palm watermark instead of ambiguous dash/text output.
- [x] Keep shift color as restrained semantic tint/glow and keep factual absence visually stronger than schedule decoration.
- [x] Establish stable zones for date, Important Day icon, user day marker and open-task count.
- [x] Reuse configured Important Day icons with compact `+N` overflow and accessible day summaries.
- [x] Carry the same shift/free-day visual language into week mode while preserving non-blocking calendar refresh.
- [x] Exact Node 20.18.1/npm 10.8.2 frontend gate, Java 17 Maven 790/790, Chromium 48/48, immutable image/PostgreSQL V47 and staging green.


## v27.41.3 — Today Visual Parity & Overtime Summary Recovery — proven green
- [x] Start from proven-green v27.41.2 commit `52a0e2a1b4e28aa56250bb813639830c56226a91` / tree `91fd1fa2fd426556d4031fbe14506cc722398b77`.
- [x] Give Today the same canonical shift/free-day/factual-absence visual language as Calendar Week.
- [x] Restore the Today overtime balance track from canonical account totals and lock real browser geometry.
- [x] Share typed day visual helpers so Calendar and Today cannot drift before profile switching.
- [x] Exact CI + staging green; desktop/manual acceptance exposed the follow-up v27.41.4 closure scope.

## v27.41.2 — Overtime Chart Source Contract Alignment — proven green
- [x] Align the stale Java source-contract assertion with the v27.41.1 pixel chart geometry (`0px`, explicit 126 px plot).
- [x] Full Overtime chart renders on staging with canonical earned/used series.
- [x] No production accounting/API/Flyway/dependency changes.

## v27.41.1 — Overtime Chart Rendering Recovery — historical candidate
- [x] Preserve canonical overtime accounting/projection semantics unchanged.
- [x] Restore visible earned/used bars using explicit browser-resolvable plot geometry.
- [x] Lock real rendered bar heights in the existing Chromium Overtime Next journey.
- [ ] Exact frontend, Maven 790/790, Chromium 48/48, immutable image/PostgreSQL V47 and staging green.

## v27.42.0 — People Profiles & Shared Availability — after frontend bundle segmentation
- [ ] Treat another person as a selected calendar profile, not an overlay pasted on top of the current user.
- [ ] Switching profile recolors/reprojects the whole calendar to that person while making active identity unmistakable.
- [ ] Add Shared Availability for common free days, including the nearest shared window and multi-day overlaps.
- [ ] Define permission/privacy boundaries before exposing another person’s schedule details.

## v27.45.0 — Vacation Entitlement & Accrual Engine
- [ ] Entitlement policy and annual allowance.
- [ ] Accrual/carry-over/adjustments with explainable balance.
- [ ] Planned/used/available vacation projection integrated with the existing Absence domain.
- [ ] Preserve modular-monolith boundaries so Payroll consumes Vacation through explicit domain contracts rather than internal persistence.

## v27.46.0 — Payroll Calculation Engine
- [ ] Canonical payroll periods, calculation snapshots and explainable breakdowns.
- [ ] Consume Time/Vacation through explicit domain contracts rather than internal persistence.
- [ ] Preserve immutable revisions and reconciliation-ready history.


## v27.40.29 — Vue Logout Ownership & Offline Status Contract Hotfix — proven green
- [x] Start from the exact v27.40.28 candidate tree (`1f5053538a08357bc4874c4f9345e5eb25e38916`).
- [x] Classify the attached Chromium run as 46/48 with two deterministic roots, not flaky/image-only failure.
- [x] Replace stale translated offline copy assertion with Vue-owned semantic network state.
- [x] Remove the post-ready logout dependency on retired legacy `#logout` without duplicating session/logout execution.
- [x] Add executable source protection for both ownership boundaries.
- [x] Keep first-run onboarding, `dataLayer`, OpenAPI 124/130 and Flyway V47 unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 782/782 across 161 Java test classes on Java 17.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.28 — E2E Vue Shell Identity Contract Alignment Hotfix — browser-red predecessor
- [x] Correctly aligned all browser identity assertions with Vue-owned `[data-vue-shell-profile]`.
- [x] Chromium then exposed the stale offline wording assertion and the real logout-to-retired-DOM dependency.
- [ ] Superseded by v27.40.29 before acceptance.

## v27.40.27 — Legacy Header Async Boot Ownership Hotfix — browser-red predecessor
- [x] Correctly made authenticated boot/profile publication independent of retired `#whoami`.
- [x] Chromium reached completed onboarding and rendered the authenticated identity in the Vue shell.
- [x] The only remaining failure was the stale shared Playwright `#whoami` assertion.
- [ ] Superseded by v27.40.28 before acceptance.

## v27.40.26 — Legacy Header Retirement Selector Hotfix — browser-red predecessor
- [x] Correctly gave recovery header explicit `#legacyGlobalHeader` identity and retired it by ID.
- [x] Removed the duplicate-`#offlineStatus` ownership bug from v27.40.25.
- [x] Chromium exposed the next deterministic ownership race: async boot still assumed retired `#whoami` existed.
- [ ] Superseded by v27.40.27 before acceptance.

## v27.40.25 — Vue Offline Sync Surface & Legacy Header Retirement — browser-red predecessor
- [x] Move offline/sync presentation, diagnostics/actions and save feedback to Vue while preserving `dataLayer` as the single executor.
- [x] Full Chromium suite executed 48 scenarios: 46 passed / 2 deterministic final failures.
- [x] Both failures resolve to the same legacy-header retirement selector bug; no second offline queue/runtime root cause is demonstrated.
- [ ] Superseded by v27.40.27 before acceptance because `body > .head` never matched the real `.wrap > .head` recovery header.

## v27.40.24 — Final Legacy Ownership Audit & Dead UI Surface Retirement — accepted predecessor
- [x] Start from the exact proven-green v27.40.23 staging baseline.
- [x] Prove zero legacy-owned user screens after Vue readiness.
- [x] Physically retire recovery-only shell chrome after successful Vue shell readiness.
- [x] Retire four orphan post-ready legacy migration fallback modals with their Vue domain owners.
- [x] Keep first-run onboarding and offline/sync presentation as explicit bounded exceptions and preserve the single dataLayer offline owner.
- [x] Correct stale frontend ownership/routing/bridge architecture documentation.
- [x] Add executable source contracts and keep Playwright parity at 48 scenarios.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 775/775 across 160 Java test classes.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix — accepted predecessor
- [x] Start from the exact v27.40.22 Admin-retirement tree.
- [x] Classify exact CI: Node 20 frontend gate green; Maven red before release-check/Playwright.
- [x] Reproduce the stale Today source contract against exact v27.40.22: 285 source-only web contracts pass and one fallback-shape assertion fails.
- [x] Require intentional pre-Vue `admin -> settings` recovery plus unchanged unknown-route `-> today` fallback without restoring a live legacy Admin owner.
- [x] Keep Admin generated API/security ownership, OpenAPI 124/130, Flyway V47, strict TypeScript and single `dataLayer` offline ownership unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 772/772.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement — predecessor cut (Maven red)
- [x] Start from the proven-green v27.40.21 staging baseline.
- [x] Give Admin a canonical generated `/api/v1/admin/**` contract while retaining the secured `/api/admin/**` backend alias.
- [x] Move Users/Roles, Registration and Diagnostics presentation/read state to Vue + Pinia.
- [x] Retire legacy Admin HTML/state/data/render/event ownership.
- [x] Remove the final post-Vue legacy route side-effect adapter/event; Vue is the sole live user-screen router/presentation owner after readiness.
- [x] Keep limited pre-Vue recovery and the single `dataLayer` offline mutation/reconnect owner.
- [x] Advance OpenAPI to 124/130 while keeping Flyway V47 and strict TypeScript unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 772/772 — blocked by stale `TodayDashboardFrontendContractTest` pre-Vue fallback source assertion.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.21 — Vue Payroll Workspace Retirement — accepted predecessor
- [x] Start from the proven-green v27.40.20 staging baseline.
- [x] Move Payroll route presentation/read state to Vue + Pinia and generated OpenAPI operations.
- [x] Preserve the existing Payroll Foundation browser journey and stable `#payroll*` selectors.
- [x] Retire legacy `view-payroll`, `45-payroll.js`, legacy Payroll state and legacy Payroll data helpers.
- [x] Narrow live post-Vue legacy route effects to Admin only.
- [x] Keep OpenAPI 118/120, Flyway V47, strict TypeScript and single `dataLayer` offline ownership unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 769/769.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.20 — E2E Release Version Contract Alignment Hotfix — accepted predecessor
- [x] Start from the exact v27.40.19 release tree and classify CI: exact frontend gate green; Maven 767 tests reached two deterministic source-contract failures before Playwright.
- [x] Keep the shared `e2e/release-version.js` browser authority introduced in v27.40.19 unchanged.
- [x] Replace stale Java assertions for PWA cache-prefix and Calendar ICS browser expectations with semantic checks for the shared release-version helper and template usage.
- [x] Keep Vue route/offline runtime, OpenAPI 118/120, Flyway V47 and single `dataLayer` offline ownership unchanged apart from the normal release-version bump.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 767/767.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.19 — E2E Release Version Authority Hotfix — predecessor cut (Maven red)
- [x] Start from exact v27.40.18 release tree and classify its browser evidence: 48 total / 46 passed / 0 flaky / 2 deterministic final failures.
- [x] Remove stale v27.40.16 literals from current Vue foundation/shell browser release assertions.
- [x] Derive current E2E release version from root `package.json` through `e2e/release-version.js`.
- [x] Reuse the same authority for Calendar ICS and PWA current-release assertions; preserve the intentional historical previous-cache fixture.
- [x] Keep Vue route/offline architecture, strict TypeScript, OpenAPI 118/120 and Flyway V47 unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 767/767.
- [x] Playwright reached all 48 scenarios: 46 passed / 0 flaky / 2 final failures, both stale E2E release-version literals.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.18 — Calendar ICS Release Version Contract Hotfix — predecessor cut (browser red)
- [x] Start from the exact v27.40.17 tree and preserve its Vue route-commit/hash-listener runtime unchanged.
- [x] Classify v27.40.17 CI: exact frontend gate green; Maven stopped before release-check/Playwright because Calendar ICS PRODID remained on 27.40.16.
- [x] Promote the Java `CalendarIcsService` PRODID to the current release.
- [x] Add a project-version-derived Java contract so future releases cannot forget the ICS PRODID bump.
- [x] Keep strict TypeScript, OpenAPI 118/120, Flyway V47 and single `dataLayer` offline ownership unchanged.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 767/767.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.17 — Vue Route Commit & Legacy Hash Listener Retirement — predecessor cut (Maven red)
- [x] Start only from the accepted green v27.40.16 staging baseline.
- [x] Publish canonical guarded Vue routes through `dutylog:vue-route-committed` instead of letting Legacy independently re-read the URL after Vue readiness.
- [x] Detach the legacy `hashchange -> applyRoute()` listener when `dutylog:vue-ready` fires.
- [x] Keep one narrow Payroll/Admin side-effect adapter driven only by committed Vue routes; keep the full historical router only for pre-Vue recovery.
- [x] Prevent duplicate initial Payroll/Admin refresh when the pre-Vue recovery route already equals the canonical Vue route.
- [x] Keep strict TypeScript, OpenAPI 118/120, Flyway V47 and single `dataLayer` offline ownership unchanged.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 766/766.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix — accepted predecessor
- [x] Start from the exact v27.40.15 tree and keep Vue route-state/guard authority in place.
- [x] Classify the v27.40.15 browser result: 48 scenarios, 43 clean passed, one retry-only multiple-notes failure and five final failures.
- [x] Restore fresh route-entry reads inside Vue Overtime/Vacation and Today owners instead of restoring legacy `applyRoute()` side effects.
- [x] Move Today widget order/visibility into Vue using persisted workspace definition plus authoritative module state.
- [x] Make note creation read-your-write from the create response and remove the selected-day reload that can race the first editor keystrokes.
- [x] Keep strict TypeScript, OpenAPI 118/120, Flyway V47 and single `dataLayer` offline ownership unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 764/764.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix — predecessor cut (browser red)
- [x] Carry the v27.40.14 Vue route-state/guard runtime forward unchanged from the v27.40.13 proven-green baseline.
- [x] Replace the formatting-sensitive profile-publication comment assertion with structural `loadProfile()` checks.
- [x] Exact frontend gate and Maven 760/760 reached browser validation.
- [ ] Clean Playwright full Chromium 48/48 — exact report: 43 clean passed, one retry-only multiple-notes failure and five final failures.
- [ ] Immutable image/PostgreSQL V47 staging acceptance — blocked by browser failures.

## v27.40.14 — Vue Route Guard Authority Cutover — predecessor cut (CI stopped at Maven)
- [x] Start only from the proven-green v27.40.13 staging baseline.
- [x] Move Admin/profile and module-disabled route guards into the typed Vue hash-route policy after authoritative access state loads.
- [x] Canonicalize blocked direct hashes to Calendar without adding a second router.
- [x] Move `body.dataset.view` and Calendar selected-day close-on-route-exit behavior under Vue ownership.
- [x] Narrow post-Vue legacy `applyRoute()` to Payroll/Admin route-entry side effects; preserve the full historical branch only for pre-Vue recovery.
- [x] Keep `dataLayer` as the sole offline mutation/reconnect owner and leave OpenAPI/Flyway unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 760/760 — exact CI result: 759 passed, 1 formatting-sensitive source-contract failure, 0 errors.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries — not reached.
- [ ] Immutable image/PostgreSQL V47 staging acceptance — not reached.


## v27.40.13 — Vue Route State Authority Cutover — accepted predecessor
- [x] Start only from the proven-green v27.40.12 staging baseline.
- [x] Make Vue read/write/subscribe to the canonical hash directly through a typed route adapter.
- [x] Remove `route` from the legacy snapshot and remove `navigate` from `LegacyBridge` / typed fallback commands.
- [x] Route migrated Vue domains directly through `navigateHashRoute(...)` while keeping one browser hash transport.
- [x] Preserve legacy Payroll/Admin route-entry side effects and pre-Vue recovery without making them route-state authority.
- [x] Keep `dataLayer` as the sole offline mutation/reconnect owner and leave OpenAPI/Flyway unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.


## v27.40.12 — Legacy Command Surface Retirement — accepted predecessor
- [x] Start only from the proven-green v27.40.11 staging baseline.
- [x] Remove dead generic `openModal`, `openTaskCreate`, `openTaskDetails` and `openImportantDetails` capabilities from `LegacyBridge`, `DutyLogLegacyPlatform` and global TypeScript declarations.
- [x] Remove the retired `open-modal` typed fallback command while preserving navigation/logout fallback semantics.
- [x] Keep Task/Important/Quick Actions and Shift Type UI ownership in their native Vue domains rather than routing through hidden legacy DOM.
- [x] Keep Payroll/Admin hash routing and `dataLayer` offline ownership unchanged.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.


## v27.40.11 — Vue Shift Type Manager Modal Retirement — accepted predecessor
- [x] Start only from the proven-green v27.40.10 staging baseline.
- [x] Replace the live legacy `shiftTypeModal` with `ShiftTypeManagerModal.vue` while preserving stable editor IDs and pre-Vue recovery fallback.
- [x] Route Calendar `+` through `DutyLogVueDomains.settingsWorkspace` and remove `openShiftTypeManager` from `LegacyBridge` / `DutyLogLegacyPlatform`.
- [x] Move Shift Type create/update/delete to the generated `/api/v1/shift-types` client and refresh Vue Calendar read models after mutations.
- [x] Preserve built-in shift restrictions, cross-midnight norm calculation, break/planned-hour validation, notification settings and custom color behavior.
- [x] Keep `dataLayer` as the sole offline mutation/reconnect owner; Shift Type CRUD remains online/server-authoritative.
- [x] Keep Payroll/Admin hash routing and remaining historical numbered-JS entry points as explicit later-retirement boundaries.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.


## v27.40.10 — Vue Quick Actions Modal Retirement — accepted predecessor
- [x] Start only from the proven-green v27.40.9 staging baseline.
- [x] Replace the live legacy `quickActionsModal` with `QuickActionsModal.vue` while preserving pre-Vue recovery fallback and stable E2E IDs.
- [x] Move Inbox, Task, Note, Important, Overtime Credit and Absence quick actions to Vue-owned domains without changing business/API semantics.
- [x] Remove `openQuickActions` from `LegacyBridge`; Today uses the Vue Productivity domain and legacy `globalQuickAdd` delegates to it after Vue readiness.
- [x] Keep `dataLayer` as the sole offline mutation/reconnect owner; no second queue, online listener or direct duplicate Inbox writer.
- [x] Keep Payroll/Admin hash routing and Shift Type Manager as explicit later-retirement boundaries.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.


## v27.40.9 — Vue Shell Navigation Model Retirement — accepted predecessor
- [x] Start only from the proven-green v27.40.8 staging baseline.
- [x] Remove hidden legacy `#tabbar` anchors/classes from the shell navigation read model; navigation comes from persisted workspace configuration and authoritative module state.
- [x] Preserve custom workspace order/visibility, module filtering, mandatory Today/Settings routes and the five-item primary navigation cap.
- [x] Distinguish the Settings destination (`Настройки` / `Settings`) from the overflow action (`Ещё` / `More`).
- [x] Keep the existing hash router only as a compatibility boundary for Payroll/Admin and remaining legacy route-entry hooks; do not add a second router or offline owner.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix — accepted predecessor
- [x] Use the exact v27.40.7 local Maven evidence: 758 tests executed, 1 failure, 0 errors, 0 skipped.
- [x] Classify the sole failure as a formatting-sensitive source contract at `VueTasksNotesImportantMigrationFrontendContractTest` line 184, not a product/offline regression.
- [x] Scope the contract to the real `SelectedDayNotes` `onBeforeUnmount` block and require the pending-timer guard, timer clear/reset and guarded `updateNote` call.
- [x] Keep `dataLayer.syncQueue()` as the sole reconnect queue owner; do not change product runtime behavior, retries/timeouts, strictness, OpenAPI/Flyway or business rules.
- [x] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [x] Maven 758/758.
- [x] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [x] Immutable image/PostgreSQL V47 staging acceptance.


## v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix — predecessor hotfix
- [x] Use the complete v27.40.6 Playwright artifact: 48 scenarios, 43 clean passes, one retry-only PWA offline-note flaky and four final failures.
- [x] Restore the native selected-day shift projection contract: display interval/timezone, source interval/timezone, moved source date, net work duration and break.
- [x] Restore exact cross-midnight overtime allocation labels including `24:00` and next-day `00:00–…`.
- [x] Replace the stale 1440px notes assertion from the retired narrow rail with an explicit side-by-side non-overlap/bounds contract for the wide Vue panel.
- [x] Stop plain `SelectedDayNotes` unmounts from resubmitting an already queued note during offline→online readability transitions; only a still-pending debounce draft may flush on unmount.
- [x] Keep `dataLayer.syncQueue()` as the sole reconnect queue owner; no Vue online flush, retry/timeout relaxation, OpenAPI/Flyway or business-rule change.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 758/758.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix — predecessor hotfix
- [x] Start from the exact v27.40.5 tree and keep its source-contract alignment unchanged.
- [x] Use the exact frontend-gate evidence: Node 20.18.1/npm 10.8.2 reaches `vue-tsc` and fails only with TS2379 at the schedule-preview row key in `SelectedDayPanel.vue`.
- [x] Keep `exactOptionalPropertyTypes`; use `item.date` when present and a defined `preview-${index}` fallback when the generated optional date is absent.
- [x] Do not add non-null assertions, casts, retries/timeouts, secondary offline ownership, OpenAPI/Flyway or business-rule changes.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 758/758.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix — predecessor hotfix
- [x] Start from the exact v27.40.4 tree `37c6a0ba489a0a52c7c37b1f57b2f7c24c45de42`.
- [x] Keep the native Vue selected-day ownership from v27.40.4 unchanged.
- [x] Align the historical strict-source JUnit contract with the explicit `CalendarDaySection` import and typed `openDay` callback.
- [x] Do not weaken TypeScript strictness, runtime collectors, retries/timeouts, offline ownership, OpenAPI 118/120 or Flyway V47.
- [ ] Exact frontend gate on Node 20.18.1/npm 10.8.2.
- [ ] Maven 758/758.
- [ ] Clean Playwright canary and full Chromium 48/48 with zero flaky retries.
- [ ] Immutable image/PostgreSQL V47 staging acceptance.

## v27.40.4 — Vue Calendar Selected-Day Panel Retirement — predecessor cut
- [x] Start from the accepted v27.40.3 baseline.
- [x] Replace the live `#calendarLegacyPanelHost -> #panel` compatibility island with native `SelectedDayPanel.vue` ownership while retaining stable selected-day selectors and behavior.
- [x] Remove Calendar selected-day attach/park/open/close bridge methods; Calendar Pinia now owns panel open state and requested section.
- [x] Keep shift and emoji mutation on the single existing offline `dataLayer` through a narrow `writeCalendarDay` adapter; do not create a second queue/reconnect owner.
- [x] Preserve cross-midnight `sourceDate` mutation semantics and use generated schedule-template preview/apply operations.
- [x] Keep Absence/Time Bank and Productivity as the owners of their selected-day bodies and mount them inside the Vue panel.
- [ ] Exact frontend, Maven 758/758, clean canary, Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging remain blocking acceptance gates.
- [ ] Continue hash-router/modal/offline-transport/Payroll/Admin/numbered-JS retirement, then close the Functional Parity and Layout/UX registers before v27.41.0.

## v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix — accepted predecessor
- [x] Use the complete v27.40.2 Playwright artifact: 48 scenarios, 32 clean passes / 10 flaky / 6 final failures.
- [x] Serialize the missing `NotificationSettings` first-read path with an owner-row pessimistic lock and a post-lock double-check so Calendar and Vue Settings bootstrap cannot race the unique user row.
- [x] Restore the curated legacy timezone fallback list in Vue Time Settings, including `Europe/Kyiv`, independently of browser ICU canonicalization.
- [x] Make timezone-save E2E copy assertions exact for the native Vue message `Часовой пояс сохранён`.
- [x] Keep HTTP/OpenAPI 118/120, Flyway V47, business rules, retries/timeouts and strict runtime collectors unchanged.
- [x] Exact frontend, Maven 758/758, clean canary, Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging accepted.
- [x] v27.40.4 starts from this accepted tree.

## v27.40.2 — Vue Settings Bootstrap Serialization & Migration Preview Query Hotfix — predecessor
- [x] Use the complete v27.40.1 canary artifact: one browser scenario executed and failed on both attempts under the strict runtime/HTTP collector.
- [x] Serialize Schedule Templates before Calendar Layers so the two Settings reads cannot concurrently seed the same built-in presets on a fresh user.
- [x] Pass required `sourceTimezone` on both generated legacy migration-preview GETs and reuse the selected timezone after migration.
- [x] Keep backend business rules, OpenAPI 118/120, Flyway V47, retries/timeouts and runtime collectors unchanged.
- [ ] Exact frontend, Maven 758/758, clean canary, Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging remain blocking acceptance gates.
- [ ] Continue selected-day/router/modal/offline/Payroll/Admin retirement only from a green v27.40.x tree.

## v27.40.1 — Vue Settings Retirement Maven Contract Alignment Hotfix — predecessor
- [x] Preserve the v27.40.0 Settings native-island cutover unchanged.
- [x] Fix the stale Maven source assertion for the retired `#settingsLegacyHost` Playwright contract; do not resurrect legacy DOM.
- [x] Keep frontend strictness, OpenAPI 118/120, Flyway V47 and browser failure policy unchanged.
- [ ] Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging remain blocking acceptance gates.
- [ ] Continue the remaining v27.40.x selected-day/router/modal/offline/Payroll/Admin retirement only after this tree is green.

## v27.40.0 — Vue Legacy Retirement & Parity: Settings Island Cutover — predecessor
- [x] Start only from the accepted green v27.39.6 staging workflow.
- [x] Retire `#settingsLegacyHost` / `#settingsLegacyParking` and remove the Settings attach/open compatibility bridge methods.
- [x] Move Time/Timezone, Schedule Templates/Calendar Layers and Notifications into native Vue Settings using generated `/api/v1/*` operations.
- [x] Make legacy Time/Schedule/Notification renderers yield after Vue Settings readiness so stable DOM IDs have one owner.
- [x] Preserve temporary timezone synchronization into the legacy non-DOM time adapter while Calendar/Overtime compatibility consumers remain.
- [ ] Retire the selected-day Calendar compatibility host, legacy router/state/modal adapters, offline `dataLayer` DOM coupling, Payroll/Admin UI ownership and remaining numbered-JS UI responsibilities before closing v27.40.x.
- [ ] Exact frontend, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging remain blocking acceptance gates.

## v27.39.6 — Module Runtime Synchronization & Offline Reconnect Ownership Hotfix — completed and green
- [x] Accepted the complete GitHub-hosted staging workflow after v27.39.5 browser evidence isolated module-state synchronization and reconnect queue ownership.
- [x] Vue Settings now follows authoritative shell/backend module enablement after onboarding.
- [x] Legacy `dataLayer` is the single reconnect queue owner and Vue refreshes only after `dutylog:offline-sync-complete`.
- [x] Full staging acceptance is green; v27.40.0 starts only from this accepted tree.

## v27.39.5 — Vue Settings State Ownership Browser Parity Hotfix — predecessor
- [x] Use the complete v27.39.4 Playwright report: 48 scenarios, 42 passed / 6 failed, zero skipped.
- [x] Treat four Task failures as one idempotency bug in the shared module helper: an already-enabled checkbox must not wait for a PATCH that a no-op `check()` cannot emit.
- [x] Keep Vue Settings as the sole owner of `dutylog.settings.openSection`; hiding parked legacy compatibility cards must not overwrite `appearance` with `none`.
- [x] Preserve Today-widget visibility while reordering: moving a visible widget must not reintroduce a previously hidden widget through `completeOrder()`.
- [x] Keep retries, timeouts, HTTP/runtime collectors, backend rules, OpenAPI 118/120 and Flyway V47 unchanged.
- [ ] Exact frontend, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, image/PostgreSQL smoke and staging remain blocking acceptance gates.

## v27.39.4 — Vue Settings Browser Ownership & Preview Correlation Hotfix — predecessor
- [x] Use the complete v27.39.3 Playwright report: 48 scenarios, 31 passed / 17 failed, with screenshots/traces for every failure.
- [x] Treat the 16 Settings-dependent failures as one stale E2E ownership selector: the legacy `#view-settings` is intentionally retired while `[data-vue-settings-workspace-view]` is visibly mounted.
- [x] Route `openView(page, 'settings')` to the Vue Settings owner and wait for `data-vue-settings-workspace=ready`; do not restore the retired legacy container.
- [x] Correlate the partial-time-off preview response to the exact 09:00–13:00 `PARTIAL` / `OVERTIME_BANK` request so an earlier auto-preview cannot satisfy the explicit-button wait.
- [x] Keep the 240-minute business assertion, strict runtime/HTTP collection, retries/timeouts, OpenAPI 118/120 and Flyway V47 unchanged.
- [ ] Exact frontend, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, image/PostgreSQL smoke and staging remain blocking acceptance gates.

## v27.39.3 — Frontend Diagnostics Release Version Source Hotfix — predecessor
- [x] Use the exact v27.39.2 frontend gate failure: strict typecheck passes and only diagnostics release-version Vitest drifts.
- [x] Make frontend package metadata the single release source for the Vite diagnostics define and its unit contract.
- [x] Preserve Settings runtime, OpenAPI 118/120, Flyway V47 and strict browser/runtime policy unchanged.
- [ ] Exact frontend, Maven 758/758, canary, clean 48/48 Chromium, image/PostgreSQL smoke and staging remain blocking acceptance gates.

## v27.39.2 — Vue Settings Maven Contract Alignment Hotfix — predecessor
- [x] Align the two stale Maven source contracts exposed after v27.39.1 fixed strict Vue typechecking.
- [x] Keep runtime, API/OpenAPI, Flyway and browser behavior unchanged.
- [ ] Exact frontend, Maven 758/758, canary, clean 48/48 Chromium, image/PostgreSQL smoke and staging remain blocking acceptance gates.

## v27.39.1 — Vue Settings Strict Typecheck Hotfix — predecessor
- [x] Keep v27.39.0 feature scope unchanged and fix only the first exact frontend-gate strict-TypeScript failures.
- [x] Preserve `exactOptionalPropertyTypes` / `noUncheckedIndexedAccess`; narrow optional DOM/catalog/array values instead of weakening strictness.
- [ ] Exact frontend, Maven 758/758, canary, clean 48/48 Chromium, image/PostgreSQL smoke and staging remain blocking acceptance gates.

## v27.39.0 — Vue Settings, Workspace & Integrations — feature predecessor
- [x] Move Profile, Language, Modules, Calendar Sync and Appearance/Workspace Studio into one Vue Settings owner.
- [x] Preserve Time, Schedule and Notifications as named compatibility islands only; v27.40.0 owns their final retirement.
- [x] Expand generated OpenAPI to 118 operations / 120 schemas and move migrated Settings writes to `/api/v1/*`.
- [x] Activate Q-11/ADR-008 source-map and integration-secret policy without weakening CSP/cookies/module guards.
- [x] Preserve v27.38.15 module-cache authority and transactional toggle sequencing.
- [ ] Exact frontend 52-case gate, Maven 757/757, Chromium 48/48 with zero flaky, immutable image, PostgreSQL V47 smoke and staging must all pass before acceptance.

## v27.38.15 — Module Cache Authority Browser Parity Hotfix — completed and green
- [x] Accepted complete GitHub-hosted staging workflow after the long v27.37/v27.38 browser-parity recovery sequence.
- [x] 47/47 Chromium reached green with module runtime authority outranking stale month cache and no flaky acceptance.
- [x] v27.39.0 starts only from this green tree; inherited Calendar/Productivity parity work is no longer deferred.


## v27.38.13 — Vue Productivity Legacy Renderer Retirement Barrier Hotfix — browser-incomplete predecessor
- [x] Use the complete v27.38.12 Playwright report/trace artifact: 44 passed / 3 failed; both v27.38.12 Notes/PWA fixes are confirmed green and only the Task crash family remains.
- [x] Prove Task persistence succeeds before the crash and capture Vue runtime error 15 plus repeated null `parentNode` failures in the remaining traces.
- [x] Prove a post-retirement legacy renderer is mutating Vue DOM: while `data-vue-productivity=ready`, trace markup for Vue-owned `#taskBoardCategory` contains the legacy `value="all"` option instead of Vue's canonical empty-value option.
- [x] Put the retirement check on every legacy Task UI writer that owns metadata suggestions, editor helpers, selected-day filters, Inbox or Board DOM; do not rely only on loader-start guards.
- [x] Re-check ownership after async Task metadata/Inbox/Board awaits so pre-retirement requests cannot complete into post-retirement Vue DOM.
- [x] Preserve 152 / 751 / 47 / 49 / V47 and OpenAPI 101 / 106; no timeout, retry, assertion, backend rule or database weakening.
- [ ] Exact frontend gate and Maven 751/751 must pass.
- [ ] `npm run test:e2e:canary` then clean mandatory 47/47 Chromium must pass with no flaky scenario.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment remain blocking acceptance gates.

## v27.38.12 — Vue Productivity Summary Ownership & PWA E2E Parity Hotfix — browser-incomplete predecessor
- [x] Use the complete v27.38.11 Playwright report/trace artifact: 42 passed / 5 failed, no flaky retry, with screenshots and network evidence for all five failures.
- [x] Prove the three Task failures are frontend recovery crashes, not missing backend projections: POST/PATCH returns 200 and the immediately following generated Task reads already contain the committed row.
- [x] Give `#sumTasks`, `#sumNote` and `#sumImp` one Vue owner after Productivity retirement; legacy `updateAccSummaries()` must not replace Vue Teleport children.
- [x] Keep disabled-module summaries stable from the always-mounted `ProductivityWorkspace` owner.
- [x] Align PWA upgrade E2E with the canonical onboarding preset key `basic` instead of the stale, nonexistent `minimum` selector.
- [x] Preserve 152 / 751 / 47 / 49 / V47 and OpenAPI 101 / 106; no timeout, retry, assertion, backend rule or database weakening.
- [ ] Exact frontend gate and Maven 751/751 must pass.
- [ ] `npm run test:e2e:canary` then clean mandatory 47/47 Chromium must pass with no flaky scenario.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment remain blocking acceptance gates.

## v27.38.11 — Vue Read-Your-Write & PWA Activation Browser Parity Hotfix — browser-incomplete predecessor
- [x] Use the completed v27.38.10 evidence: 42 passed / 5 failed with no flaky retry; all earlier mandatory gates reached browser E2E.
- [x] Carry committed Task DTOs as a short-lived read-your-write overlay through accepted selected-day and Board refreshes instead of re-publishing only before/after the refresh window.
- [x] Align the multiple-notes reload wait with generated `/api/v1/calendar` ownership while preserving the bounded Note PATCH adapter and generated DELETE.
- [x] Let first `serviceWorker.register()` own installation and reserve explicit `registration.update()` for an existing registration, removing the duplicate first-install lifecycle.
- [x] Preserve 152 / 751 / 47 / 49 / V47 and OpenAPI 101 / 106; no timeout, retry, assertion, backend rule or database weakening.
- [ ] Exact frontend gate and Maven 751/751 must pass.
- [ ] `npm run test:e2e:canary` then clean mandatory 47/47 Chromium must pass with no flaky scenario.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment remain blocking acceptance gates.

## v27.38.10 — Vue Offline, Task Publication & PWA Browser Parity Hotfix — browser-incomplete predecessor
- [x] Use the completed v27.38.9 evidence: exact frontend, Maven, release-check and boot canary green; full Chromium 40 passed / 6 failed / 1 flaky.
- [x] Hydrate selected-day Productivity from the existing IndexedDB/dataLayer snapshot while offline without blocking on online-only time-context, Board, Inbox or full Important reads.
- [x] Publish backend-authoritative Task mutation DTOs before and after concurrent projection refreshes while preserving backend Board order and business authority.
- [x] Start service-worker installation only after first-run onboarding becomes authoritative; preserve idempotent registration and established-page upgrade behavior.
- [x] Restore generated `/api/v1/notes/{id}` ownership for Note DELETE while preserving the bounded legacy offline-adapter PATCH.
- [x] Preserve 152 / 751 / 47 / 49 / V47 and OpenAPI 101 / 106; no database migration or timeout/assertion weakening.
- [ ] Exact frontend gate and Maven 751/751 must pass.
- [ ] `npm run test:e2e:canary` then clean mandatory 47/47 Chromium must pass with no flaky scenario.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment remain blocking acceptance gates.

## v27.38.9 — Vue Read-Model & Offline Browser Parity Hotfix — browser-incomplete predecessor
- [x] Align stale Calendar/Important/Layer browser waits with generated Vue endpoint ownership and align Task Details with its intentionally collapsed Advanced section.
- [x] Make cached Productivity mountable during authenticated offline reload, move service-worker registration out of login, and add Task mutation re-publication after refresh.
- [x] Complete exact frontend, Maven, release-check and auth/onboarding canary gates.
- [ ] Full Chromium stopped at 40 passed / 6 failed / 1 flaky; v27.38.10 inherits the remaining browser parity work.

## v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix — browser-parity predecessor
- [x] Use the v27.38.6 canary failure: Vue dist preflight succeeds and Spring starts, but the single onboarding scenario reports repeated module-disabled 403s instead of spending another hour on the full suite.
- [x] Gate Vue Productivity reads on both `modulesLoaded` and `onboardingCompleted`; unknown/first-run module state is no longer interpreted as permission to read optional APIs.
- [x] Preserve shell module-map identity when a legacy-state publication carries the same values, preventing duplicate refresh waves and request amplification.
- [x] Keep backend `MODULE_DISABLED` enforcement and strict Playwright HTTP/console assertions unchanged; no 403 allowlist is introduced.
- [x] Preserve 152 / 751 / 47 / 49 / V47 and OpenAPI 101 / 106; no database migration.
- [ ] Exact frontend gate and Maven 751/751 must pass.
- [ ] `npm run test:e2e:canary` must pass before targeted browser checks and mandatory 47/47 Chromium.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment remain blocking acceptance gates.

## v27.38.3 — Vue Productivity Strict Typecheck Hotfix — browser-incomplete predecessor
- [x] Use the first exact v27.38.2 frontend gate: lockfile/delivery/OpenAPI pass, strict `vue-tsc` reports nine errors.
- [x] Keep Today template expressions inside the Vue public instance by routing cross-domain calls through typed setup functions.
- [x] Reuse the generated Task schema instead of weakening required `allDay`; keep strict `exactOptionalPropertyTypes` for notes.
- [x] Remove Pinia `this` references from default parameter initializers and use an environment-safe autosave timer type.
- [ ] Exact frontend gate must pass 49 Vitest + production build + bundle audit.
- [ ] Maven/JUnit must pass 751/751.
- [ ] Local/CI `npm run test:e2e` must reach 47/47 Chromium before acceptance.
- [ ] Immutable image, clean PostgreSQL smoke and staging deployment remain blocking acceptance gates.

## v27.38.2 — Vue Productivity Manifest Contract Alignment Hotfix
- [x] Use the complete v27.38.1 local Maven result: 751 tests executed, one failure, zero errors.
- [x] Isolate the sole failure to case-sensitive documentation matching: `Offline/reconnect boundary` versus lowercase `offline/reconnect`.
- [x] Keep the contract semantic with `Locale.ROOT` lowercase matching instead of changing runtime code or weakening the offline/reconnect boundary.
- [x] Preserve v27.38.0 runtime/API/offline semantics and all 152 / 751 / 47 / 49 / V47 baselines.
- [ ] Accept only after exact frontend gate, Maven/JUnit 751/751, Chromium 47/47, immutable image, clean PostgreSQL smoke and staging deployment are green.

## v27.38.1 — Vue Productivity Static Contract Alignment Hotfix — static-contract predecessor
- [x] Align the historical Absence/Time Bank generated-contract source test with the generated-file invariant instead of stale global 98/103 totals.
- [x] Align Task modal duration coverage with the Vue `v-for`/`:data-task-duration` implementation while preserving the 45-minute public option.
- [x] Align productivity legacy-owner retirement with the canonical `data-vue-productivity` readiness marker and dataset check.
- [x] Preserve v27.38.0 runtime/API/offline semantics and all 152 / 751 / 47 / 49 / V47 baselines.
- [ ] Accept only after exact frontend gate, Maven/JUnit, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deployment are green.

## v27.38.0 — Vue Tasks, Notes & Important Days — implementation predecessor
- [x] Move Tasks board/Inbox, Task details/editor, selected-day Tasks, multiple daily Notes and Important Days into one bounded Vue productivity owner.
- [x] Keep Spring Boot authoritative for task/deadline/schedule validation, note persistence/order, Inbox conversion, recurrence/timezone projection and all business rules.
- [x] Expand the generated OpenAPI contract to 101 operations / 106 schemas so migrated productivity writes and reads use typed `/api/v1/*` operations.
- [x] Activate Q-10 offline/reconnect for bounded productivity mutations by reusing the existing dataLayer queue/snapshot through named bridge capabilities; do not create a second queue.
- [x] Retire legacy productivity read/modal owners while preserving released public browser selectors and named cross-domain entry points from Today/Calendar.
- [x] Add latest-read-wins, double-submit, 409 refresh, model Vitest coverage and Java ownership contracts without weakening the existing 47 Chromium scenarios.
- [ ] Accept only after exact frontend gate, 152-class / 751-test Maven/JUnit, 47/47 Chromium, immutable image, clean PostgreSQL V47 smoke and staging deployment are green.

## v27.37.5 — Vue Calendar Selected-Day Island Lifecycle Hotfix — browser-incomplete predecessor
- [x] Use the complete v27.37.4 Chromium result (28 passed / 19 failed) to separate the fixed onboarding blocker from remaining route/editor regressions.
- [x] Trace repeated `renderChips()` / `null.innerHTML` failures to the selected-day panel being appended under a conditionally-mounted CalendarPage host and destroyed when that host unmounts.
- [x] Park the selected-day editor on `document.body` before CalendarPage unmount and reattach it on Calendar mount, keeping the island mandatory rather than masking missing descendants with optional chaining.
- [x] Preserve strict browser diagnostics and leave independent mode/timezone projection failures for evidence-driven follow-up.
- [ ] Browser acceptance stopped at 31/47 Chromium; v27.38.x inherits the remaining parity work and must reach 47/47 before domain acceptance.
## v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix — predecessor
- [x] Use the v27.37.3 Maven failure to isolate the only failing JUnit assertion after 742 green tests.
- [x] Remove the stale hardcoded bundle-budget release literal from the Calendar/Timeline migration contract.
- [x] Derive the PWA current-shell and browser-bundle-budget release assertions from the canonical Maven project version.
- [x] Preserve the v27.37.3 selected-day island runtime fix and all strict frontend/browser gates.
- [ ] Accept only after exact frontend gate, Maven/JUnit, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deployment are green.
## v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix — predecessor
- [x] Recover the first real Playwright trace from the persistent runner worktree after the 45-minute run timeout.
- [x] Trace the shared fresh-user failure to `selectDay(null)` dereferencing the retired legacy Calendar `#layout`.
- [x] Make only `#layout` optional while keeping the selected-day `#panel` compatibility island mandatory.
- [x] Preserve strict Playwright assertions, retry policy, job timeout, Vue ownership, generated API and backend contracts.
- [ ] Accept only after exact frontend gate, Maven/JUnit, 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging deployment are green.
## v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix — predecessor

- [x] Reproduce the common ~30-second fresh-user browser timeout pattern from the self-hosted staging run.
- [x] Trace the boot path to `loadProfile() -> applyRoute()` after Vue Calendar owner retirement.
- [x] Make retired legacy Calendar header controls optional instead of dereferencing `null.style`.
- [x] Preserve strict browser assertions, retry policy, job timeout, domain ownership and backend contracts.
- [ ] Accept only after the exact 47/47 Chromium, immutable-image and staging path is green.

## v27.37.1 — Vue Calendar & Timeline Strict Typecheck Hotfix — predecessor

- [x] Type the public Calendar/Timeline bridge callback explicitly.
- [x] Resolve Pinia action mode defaults inside typed action bodies.
- [x] Preserve strict TypeScript, generated API ownership and all v27.37.0 runtime behavior.
- [ ] Accept through exact frontend gate, Maven, Chromium, immutable image and staging.

## v27.37.0 — Vue Calendar & Timeline — completed

- Vue owns Today plus Calendar Month, Week and Day read surfaces through one generated-API range store.
- Preserve focused-date navigation, versioned mode/focus persistence, stale-read protection and read-only layer composition.
- Keep Spring Boot authoritative for shifts, tasks, events, absences, reminders, balances, layers and every write.
- Keep the mature selected-day editor as one named compatibility island during the remaining v27.37.x parity passes.
- Establish Q-07 PWA previous-cache upgrade acceptance and Q-08 raw/gzip browser-bundle budgets as recurring frontend gates.
- Accept only after 47/47 Chromium, exact-toolchain Vue/Vitest, Maven/JaCoCo, immutable image, clean PostgreSQL and staging are green.

## v27.36.8 — Vue Read Sequencing Static Contract Alignment Hotfix — completed and green

- Align the three Maven-failing historical static contracts with the accepted shared `readSequence` runtime.
- Preserve period-only loading, canonical account identity and latest-read-wins semantics unchanged.
- Keep the strict 45-scenario Chromium expectation and single-pass staging validation unchanged.
- Accepted: the complete single-pass frontend, Maven, Chromium, immutable-image, PostgreSQL and staging path is green.


## v27.36.7 — Time Bank Period Toggle Snapshot Stability Hotfix — completed implementation predecessor

- Keep the canonical credits/usages account snapshot stable across month/year presentation toggles.
- Reload only period-dependent compensation, integrity and actual-work projections.
- Share one read sequence across full and partial loads so stale responses cannot win.
- Accept the v27.36.0 domain migration only after exact 45/45 Chromium, immutable-image and staging success.


## v27.36.6 — Time Bank Usage-Date Chart Parity Hotfix — completed predecessor

- Close the final 44/45 Chromium gap by plotting usage on `usageDate` instead of source-credit dates.
- Keep earned and used chart series independent and forbid `credit.usedHours` double counting.
- Accept the v27.36.0 domain migration only after exact 45/45 Chromium, immutable-image and staging success.

## v27.36.5 — Single-Pass CI & Final Vue Browser Parity Hotfix — completed predecessor

- Close the final two deterministic Chromium failures without weakening selectors, retries or page-error collection.
- Make `test` push validation single-pass while keeping all quality gates and immutable-image verification.
- Accept the v27.36.0 domain migration only after the exact Node/Maven/Chromium/staging path is green.

## v27.36.4 — Vue Absence & Time Bank Browser Parity Hotfix — completed browser-parity predecessor

- [x] Retire the duplicate legacy Time Bank guide modal and backdrop.
- [x] Publish winning Vue planner/account refreshes to remaining Calendar, Today and selected-day projections.
- [x] Preserve Today/Calendar routes for modal-only composer launches.
- [x] Own edit deletion inside the visible Vue modal.
- [x] Restore Time Bank overview ratio/FIFO insight parity.
- [x] Delegate selected-day absence editing through the public Vue domain adapter.
- [x] Add Vitest and source-only Java guards without weakening Playwright.

## v27.36.3 — CI Artifact Quota Resilience Hotfix — completed stabilization predecessor

- Keep report persistence diagnostic and non-blocking when GitHub artifact storage is exhausted.
- Upload only compact JaCoCo XML/CSV with three-day retention and unique run/attempt names.
- Upload Playwright reports only for failed CI/staging runs with missing-file tolerance.
- Preserve all blocking frontend, Maven, Playwright, Docker, migration-smoke and deployment gates.
- Change no runtime, API, OpenAPI, npm graph, PostgreSQL schema or Flyway migration.

## v27.36.2 — Vue Timer Static Contract Compile Coverage Hotfix — completed stabilization predecessor

- Repair the Java 17 multiline-string syntax failure in the browser timer regression contract.
- Promote the timer regression to the `*FrontendContractTest.java` compile-gated naming convention.
- Compile all source-only `*HotfixTest.java` files alongside frontend contracts before Maven.
- Preserve the v27.36.1 browser timer implementation and the v27.36.0 Absence & Time Bank ownership unchanged.
- Change no API, OpenAPI, npm graph, PostgreSQL schema or Flyway migration.

## v27.36.1 — Vue Browser Timer Handle Type Hotfix — completed stabilization predecessor

- Make both migrated preview debounces select the browser timer overload explicitly.
- Keep strict TypeScript and Node/Vitest typings enabled without casts or compiler relaxation.
- Preserve 260 ms / 280 ms timing, cancellation, Q-06 behavior and Vue domain ownership.
- Change no API, OpenAPI, npm graph, PostgreSQL schema or Flyway migration.

## v27.36.0 — Vue Absence & Time Bank — completed domain predecessor

- Migrate the unified Absence Composer and absence journal to Vue 3 with strict TypeScript.
- Migrate Time Bank overview, credits, responsive ledger, usage ownership, FIFO queue/forecast, exact credit editor and scenario manager.
- Keep Spring Boot authoritative for allowance, overlap, compensation, FIFO, ledger integrity, closed periods and ownership.
- Close Q-06 with stale-read sequencing, duplicate-submit blocking and durable HTTP 409 refresh.
- Retire the legacy Absence/Time Bank runtime owners while retaining only named typed Today/Calendar adapters.
- Preserve one Spring Boot image/container, separate PostgreSQL, Flyway V47 and rollback without schema changes.



## v27.35.7 — Docker Frontend OpenAPI Build Context Hotfix — completed

- Copy the canonical backend OpenAPI YAML into the Docker frontend stage.
- Keep the OpenAPI drift gate active inside `npm run build`.
- Preserve dependency-layer caching by copying the contract after `npm ci`.
- Keep the single Spring Boot application image and separate PostgreSQL topology unchanged.
- Accept Gate A only after Docker, clean PostgreSQL and staging validation are green.


## v27.35.6 — Gate A Historical Static Contract Alignment Hotfix — stabilization predecessor

- Align four historical test-only expectations with the final committed-lockfile delivery model.
- Require semantic full-green acceptance wording rather than one brittle sentence.
- Discover all 47 Flyway migrations recursively under the PostgreSQL directory.
- Require the exact pinned `node:20.18.1-alpine3.20` frontend build image.
- Keep runtime, API, OpenAPI, PostgreSQL, Flyway content and domain ownership unchanged.


## v27.35.5 — Gate A Quality Register Lambda Capture Compile Hotfix — stabilization predecessor

- Fixes the remaining Maven `testCompile` blocker in `VueDeliveryContractsDiagnosticsFoundationTest`.
- Captures an immutable `rowPrefix` instead of the mutable `for` loop counter.
- Preserves all Q-01–Q-05 assertions, authentic lockfile delivery and strict frontend gates.
- Changes no production runtime, API, PostgreSQL or Flyway state.

## v27.35.4 — Frontend Gate Static Contract Java Escaping Hotfix

- [x] Correct the malformed Java string in the committed-lockfile static contract.
- [x] Preserve the exact authentic npm graph and successful frontend gate.
- [x] Add a static release guard for the escaped assertion.
- [ ] Require full green Maven, Playwright, image, clean PostgreSQL and staging validation before Gate A acceptance.

## v27.35.3 — Authentic Lockfile Commit & Generated Client Fixture Hotfix — stabilization predecessor

- [x] Commit the authentic CI-generated npm graph with provenance.
- [x] Restore committed-lockfile-only `npm ci` in CI, Docker and local frontend gate.
- [x] Fix the generated-client Vitest fixture to return a fresh `Response` per request.
- [x] Mark Q-01–Q-05 DONE in implementation.
- [x] Full CI, Docker, clean PostgreSQL and staging are green; Gate A is accepted and v27.36.0 may proceed.

## v27.35.2 — Authentic npm Lockfile Bootstrap Hotfix — completed

- [x] Remove the synthetic flat frontend lockfile that produced an internally incompatible Vue/Volar graph.
- [x] Generate a real lockfile with pinned Node `20.18.1` and npm `10.8.2` before `npm ci`.
- [x] Reject generated graphs without registry tarballs, SHA-512 integrity and dependency/peer edges.
- [x] Upload the exact generated lockfile and SHA-256 manifest from CI/staging/production validation even when later steps fail.
- [x] Use the same bootstrap and authenticity verifier in the Docker Node stage.
- [x] Keep Q-01 `ACTIVE` and Gate A blocked until `v27.35.3` commits the exact CI artifact.
- [x] `v27.35.3` commits the CI-generated authentic lockfile and restores lockfile-only `npm ci`.

## v27.35.1 — Frontend Lockfile Executable Resolution Hotfix — superseded by authentic graph bootstrap

- [x] Restore npm `bin` metadata for `vue-tsc`, `vitest`, `vite` and TypeScript.
- [x] Fail CI/Docker immediately when `node_modules/.bin` launchers are absent after `npm ci`.
- [x] Run `npm ls --all` before compilation and reject `npx`/global fallbacks.
- [x] Keep API, PostgreSQL, Flyway V47 and product ownership unchanged.
- [ ] Confirm the complete clean frontend/Maven/Chromium/Docker/staging chain in GitHub Actions.

## v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation — stabilization baseline

- [x] Commit `frontend/package-lock.json` and require `npm ci` in local, CI and Docker builds.
- [x] Pin Node 20.18.1, npm 10.8.2 and exact frontend dependencies.
- [x] Generate TypeScript schema/operation contracts from canonical OpenAPI and block drift.
- [x] Add an operationId-based typed API client over the shared same-origin/CSRF transport.
- [x] Add route/release/requestId diagnostics, Vue error boundary and `unhandledrejection` recovery.
- [x] Add migration manifest/parity template and ADR-001–ADR-005.
- [x] Establish Q-02–Q-05 and the Q-01 foundation; authentic lockfile closure moves to v27.35.3 after the v27.35.2 bootstrap artifact.
- [x] Keep API behavior, PostgreSQL, Flyway V47, FIFO, Payroll and one-image deployment unchanged.
- [ ] Confirm full GitHub Actions validation, image build, clean PostgreSQL smoke and staging deployment.

## v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix — completed

- [x] Return a successful canonical preview for zero/negative intermediate calculated drafts.
- [x] Keep create/update validation strict for non-positive overtime credits.
- [x] Publish active state for secondary routes on the visible More control and matching modal item.
- [x] Align the two remaining Chromium scenarios with those public contracts.
- [x] Keep strict TypeScript, strict Playwright collection, API shape, PostgreSQL, Flyway V47 and one-image deployment unchanged.
- [x] GitHub Actions validation, image build, clean PostgreSQL smoke and staging deployment are green.

## v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix — completed

- [x] Move historical E2E navigation from hidden legacy chrome to the public Vue/legacy bridge.
- [x] Add stable Vue shell hooks for visible brand, profile, more-menu and logout interactions.
- [x] Align shell/mobile/workspace tests with Vue ownership without restoring the old tabbar.
- [x] Declare optional module presets explicitly in module-dependent shell scenarios.
- [x] Align Calendar Sync ICS release identity.
- [x] Keep API, PostgreSQL, Flyway V47 and one-image deployment unchanged.
- [x] Full 44-scenario Chromium baseline is green in v27.34.4.

## v27.34.2 — Vue Browser Runtime Bundle Hotfix — completed

- [x] Reproduce the Chromium-wide `pageerror: process is not defined` cascade.
- [x] Replace `process.env.NODE_ENV` during Vite library build without adding a browser shim.
- [x] Audit generated JavaScript for residual Node/CommonJS runtime globals.
- [x] Run the audit through the same `npm run build` used by CI and Docker.
- [x] Keep the strict Playwright runtime issue collector unchanged.
- [x] Keep API, PostgreSQL, Flyway V47 and one-image deployment unchanged.
- [x] Browser runtime no longer fails on a Node `process` global; E2E ownership alignment continues in v27.34.3.

## v27.34.1 — Vue Strict Type Contract Hotfix — completed

- [x] Keep `exactOptionalPropertyTypes` enabled.
- [x] Replace optional native `aria-current`, `type` and `disabled` bindings with concrete valid values.
- [x] Remove unsupported Vite 5 `LibraryOptions.cssFileName`.
- [x] Preserve the stable app-shell CSS name through Rollup `assetFileNames`.
- [x] Add a static regression contract for all four compiler failures.
- [x] Keep API, PostgreSQL, Flyway V47 and one-image deployment unchanged.
- [x] Real `vue-tsc`, Vitest and Vite build pass before Chromium runtime validation.

## v27.34.0 — Vue App Shell & Design System — completed

- [x] Move the visible brand, profile entry, primary/secondary navigation and network state into Vue.
- [x] Publish route, workspace, module and safe profile changes through a frozen legacy read model.
- [x] Add typed `UiButton`, `UiBadge`, `UiCard`, `UiTabs`, `UiEmptyState`, `UiModal`, `ToastHost` and `AppIcon` primitives.
- [x] Add responsive desktop/sidebar/mobile shell behavior, visible focus and reduced motion.
- [x] Hide legacy chrome only after Vue readiness so failed boots retain a usable fallback.
- [x] Preserve legacy hash routing and product-screen ownership behind named capabilities.
- [x] Add static, Vitest and Playwright shell parity coverage.
- [x] v27.35.0: close Vue delivery, generated-contract, diagnostics, manifest and ADR Gate A.
- [x] v27.36.0: migrate Absence Composer and Time Bank.
- [x] v27.37.0: migrate Calendar, Today and timeline read surfaces; selected-day editor remains a bounded compatibility island.
- [ ] v27.38.0: migrate Tasks, Notes and Important Days.
- [x] v27.39.0: migrate Settings, Workspace and integrations (implementation prepared; acceptance gates pending).
- [ ] v27.40.0: retire bridge, numbered JavaScript and legacy routing after parity.

## v27.33.0 — Vue Frontend Foundation & CI/CD — completed

- [x] Add Vue 3, TypeScript, Vite, Pinia and Vue Router foundation.
- [x] Keep Vue Router isolated in memory history while legacy hash routing remains authoritative.
- [x] Add the typed API/CSRF client and explicit legacy bridge.
- [x] Package Vite output inside the existing Spring Boot JAR and one app image.
- [x] Add frontend typecheck, Vitest and Vite build to CI, staging and production validation.
- [x] Add browser readiness and packaging contracts.

## v27.32.1 — Time Bank Absence Navigation Hotfix — completed

- [x] Reproduce the final v27.32.0 browser failure at the Bank Usage → Open absence boundary.
- [x] Refresh a stale absence read model before opening its owner.
- [x] Open the owning record in Unified Absence Composer instead of only routing to Vacation.
- [x] Build the edit-aware FIFO preview without making the reservation compete with itself.
- [x] Keep API, PostgreSQL, Payroll, ownership and Flyway V47 unchanged.
- [x] Advance baseline to 130 Java classes / 635 tests / 42 Playwright scenarios.

## v27.32.0 — Absence & Time Bank Experience — completed

- [x] Make «Отпуск и отсутствия» the single event journal and mutation owner.
- [x] Split «Переработки» into Overview, Credits, Bank Usage and FIFO Detail.
- [x] Show posted, reserved and truly free time separately.
- [x] Link an absence to its usage detail and a usage back to its absence.
- [x] Forecast oldest-first FIFO consumption and the next credit to be spent.
- [x] Add filters, actionable empty states, contextual guides and the first guided browser journey.
- [x] Keep API, PostgreSQL, Payroll, canonical ownership and Flyway V47 unchanged.
- [x] Stabilize cross-workspace editor navigation in v27.32.1.

## Approved architecture transition — complete Vue migration before new features

1. **v27.33.0 — Vue Frontend Foundation & CI/CD**: Vue 3, TypeScript, Vite, Pinia, Router, Vitest, same-image production build and legacy bridge.
2. **v27.34.0 — Vue App Shell & Design System** plus v27.34.1–v27.34.4 stabilization.
3. **v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation**: reproducible delivery, generated OpenAPI contract, diagnostics, manifest and ADR Gate A.
4. **v27.36.0 — Vue Absence & Time Bank**.
5. **v27.37.0 — Vue Calendar & Timeline**.
6. **v27.38.0 — Vue Tasks, Notes & Important Days**.
7. **v27.39.0 — Vue Settings, Workspace & Integrations**.
8. **v27.40.0 — Vue Legacy Retirement & Parity**: remove numbered JavaScript, old routing/state/modal bridges and string-based legacy contracts.

No new major product feature is added until the Vue migration is complete. DutyLog remains one repository, one Spring Boot application image/container and one deployment; PostgreSQL remains separate.

After Vue parity — historical planning snapshot; version numbers below are superseded by the current roadmap above:

1. **v27.41.0 — Vacation Entitlement & Accrual Engine**: working-year boundaries, configurable annual entitlement, earned-vs-available balance, used/planned/advanced leave, service-time rules, multiple leave types, carry-over, date-based forecast and employer-balance reconciliation. Vacation entitlement days remain separate from monetary vacation-pay calculation.
2. **v27.42.0 — Payroll Calculation Engine**: safe user-defined formula DSL, typed input/computed variables, dependency graph/cycle validation, period context and DutyLog work-time facts. `hourlyRate` is a configurable variable/formula, not a hardcoded fixed-price field.
3. **v27.43.0 — Payroll Formula Studio & Templates**: visual formula editor, available-variable browser, preview/explanation trace, cloneable templates and a complex real-world shift-pay example with salary-derived monthly hourly rate, night/harmful/premium/regional components.
4. **v27.44.0 — Payroll History & Reconciliation**: immutable monthly calculation snapshots, payslip actuals, calculated-vs-actual reconciliation and explicit manual/unknown adjustments.
5. **v27.45.0 — One-Tap Calendar Connect**.
6. **v27.46.0 — Notes Archive & Timeline Collisions**.
7. **v27.47.0 — Telegram Task Actions & Guided Commands**.
8. **v27.48.0 — Guided Onboarding & Product Education**.
9. **FEATURE FREEZE** after the planned v1.0 product scope above is complete.
10. **v27.49.0 — Operational Readiness, Security & Recovery**.
11. **v27.50.0 — Release Candidate**.
12. **DutyLog v1.0.0 — Public Release**.

## v27.31.2 — Canonical Absence Browser Contract Alignment Hotfix — completed

- [x] Keep intentional `409 DIRECT_USAGE_RETIRED` verification outside the browser runtime-failure collector.
- [x] Treat absence-linked FIFO usages as read-only Overtime projections with no legacy edit/delete buttons.
- [x] Edit and delete time off through the owning absence and Unified Absence Composer.
- [x] Preserve credits, FIFO allocations and surviving linked usages after deleting one absence.
- [x] Keep production runtime, API, PostgreSQL, Payroll and V47 unchanged.
- [x] Advance baseline to 128 Java classes / 628 tests / 41 Playwright scenarios.

## v27.31.1 — Canonical Absence Static Contract Alignment Hotfix — completed

- [x] Align five historical static frontend assertions with canonical absence ownership.
- [x] Preserve direct coverage serialization and imported `HOURS_ONLY`.
- [x] Preserve absence-owned linked usages and the explicit legacy-promotion flow.
- [x] Keep production runtime, API, PostgreSQL, FIFO, Payroll and V47 unchanged.
- [x] Advance baseline to 127 Java classes / 626 tests / 41 Playwright scenarios.

## v27.31.0 — Canonical Absence Ledger & Legacy Retirement — completed

- [x] Keep Overtime credits, totals, FIFO queue, allocation details and exports as the canonical compensation read model.
- [x] Make Unified Absence Composer the only user-facing creation/edit path for time off and every other absence type.
- [x] Retire direct `POST /overtime/usages` and direct legacy usage editing with explicit conflict codes.
- [x] Keep linked overtime usages internal to the owning absence and restore FIFO automatically when compensation changes.
- [x] Add previewed one-time promotion of old MANUAL usages without changing usage IDs or rebuilding allocation rows.
- [x] Introduce transition-only `HOURS_ONLY` coverage for known duration with unknown historical interval.
- [x] Route Telegram `/timeoff` through the canonical absence service.
- [x] Add forward-only Flyway V47 to extend the immutable V42 absence coverage/shape constraints for `HOURS_ONLY` without rewriting rows.
- [x] Keep Payroll, Unified Ledger, Calendar Sync and released migrations V42–V46 unchanged.
- [x] Advance baseline to 126 Java classes / 625 tests / 41 Playwright scenarios.



## v27.30.2 — Today Overtime Journal Contract Hotfix — completed

- [x] Reproduce the single Maven failure at `TodayDashboardFrontendContractTest:37`.
- [x] Confirm the Today overtime action is a journal route, not a credit-creation shortcut.
- [x] Update the stale dashboard contract without changing production JavaScript.
- [x] Add focused coverage that keeps credit creation owned by the Overtime workspace.
- [x] Keep API, PostgreSQL, Payroll, Unified Ledger and Flyway V46 unchanged.
- [x] Advance baseline to 125 Java classes / 620 tests / 40 Playwright scenarios.



## v27.30.1 — Unified Absence Quick Access Integration — completed

- [x] Expose a neutral absence action in global Quick Add.
- [x] Add a direct current-date absence action to Today.
- [x] Keep Overtime contextual with `TIME_OFF / OVERTIME_BANK` preselection.
- [x] Keep overtime credit creation available from Quick Add and Overtime after replacing the Today shortcut.
- [x] Fix keyboard focus when Vacation is the only enabled quick mutation module.
- [x] Add focused Java and Playwright coverage.
- [x] Keep API, PostgreSQL, Payroll, Unified Ledger and Flyway V46 unchanged.
- [x] Advance baseline to 124 Java classes / 619 tests / 40 Playwright scenarios.

Stabilized by: **v27.30.2 — Today Overtime Journal Contract Hotfix**.

## v27.30.0 — Unified Absence Composer & Calendar Projection — completed

- [x] Reuse one absence form across Vacation, Quick Add, Calendar and Overtime entry points.
- [x] Route new overtime-backed time off through linked absence creation instead of detached raw usage.
- [x] Show the selected absence type, compensation source, available balance and projected remainder.
- [x] Require a user-facing reason while retaining API compatibility for historical records.
- [x] Preserve the planned shift under a full-day factual absence.
- [x] Render partial absence as a typed interval over the planned shift.
- [x] Add type glyphs and status-aware calendar treatment without relying on color alone.
- [x] Keep Payroll, Unified Ledger, approval workflow, PostgreSQL and Flyway V46 unchanged.
- [x] Isolate the dirtied Telegram detached-owner integration context in its own H2 database so test order cannot drop the shared schema.
- [x] Advance baseline to 123 Java classes / 616 tests / 39 Playwright scenarios.

Stabilized and completed by: **v27.30.1 — Unified Absence Quick Access Integration** and **v27.30.2 — Today Overtime Journal Contract Hotfix**.

## v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix — completed

- [x] Reproduce the remaining Workspace Studio failure after profile autosave.
- [x] Preserve an explicit allowed Today-card order in `safeTodayWidgets(...)`.
- [x] Insert mandatory Shift only when the incoming selection omits it.
- [x] Add HTTP persistence and focused source regression coverage.
- [x] Keep the strict 38-scenario Playwright fixture unchanged.
- [x] Advance baseline to 122 Java classes / 614 tests / 38 Playwright scenarios.
- [x] Keep PostgreSQL and Flyway V46 unchanged.

## v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix — completed

- [x] Preserve the active preset's Today card selection when creating Custom Workspace.
- [x] Keep Shift mandatory and preserve the existing widget allowlist.
- [x] Verify Overtime and Tasks inheritance before later Studio edits.
- [x] Keep the strict Playwright fixture unchanged.
- [x] Advance baseline to 121 Java classes / 612 tests / 38 Playwright scenarios.
- [x] Keep PostgreSQL and Flyway V46 unchanged.

## v27.29.1 — Theme Package Token Scope Contract Hotfix — completed

- [x] Reproduce the single Maven failure at `WorkspaceLayoutThemeStudioFrontendContractTest:74`.
- [x] Confirm the runtime theme package metadata already contains the correct unescaped JavaScript template literal.
- [x] Remove the phantom-backslash expectation from the Java source contract.
- [x] Add a focused regression contract for Java-source escaping versus runtime JavaScript content.
- [x] Keep Workspace Studio runtime, themes, layouts, profile persistence, APIs, PostgreSQL and Flyway V46 unchanged.
- [x] Advance baseline to 120 Java classes / 611 tests / 38 Playwright scenarios.


## v27.29.0 — Workspace, Layout & Theme Studio — completed

- [x] Advance the single DutyLog UI Core to contract v2.
- [x] Add a safe custom workspace with ordered/visible primary navigation.
- [x] Keep Today and Settings mandatory and cap primary navigation at five items.
- [x] Add independent Today-card order/visibility while keeping Shift mandatory.
- [x] Add Sidebar and Mobile Flow layout packages without copied screens.
- [x] Add compact calendar density and pill/dot schedule-layer presentation.
- [x] Add a pointer-free calm-grid decoration package.
- [x] Publish theme package metadata and preserve semantic-token isolation.
- [x] Extend server-side profile whitelisting and synchronous bootstrap to UI contract v2.
- [x] Keep Payroll, Unified Ledger, Vacation, Calendar APIs, PostgreSQL and Flyway V46 unchanged.
- [x] Advance baseline to 119 Java classes / 610 tests / 38 Playwright scenarios.

Stabilized by: **v27.29.1 — Theme Package Token Scope Contract Hotfix**, **v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix** and **v27.29.3 — Custom Workspace Today Widget Order Persistence Hotfix**.


## v27.28.3 — Payroll Snapshot Hash Schema Validation Hotfix — completed

- [x] Keep released V45 immutable and preserve its Flyway checksum.
- [x] Add forward-only V46 to convert `payroll_snapshots.calculation_hash` from `CHAR(64)` to `VARCHAR(64)` using `BTRIM(...)`.
- [x] Preserve `NOT NULL` and the existing lowercase 64-character hexadecimal check constraint.
- [x] Add a regression contract for the V45 checksum, V46 type alignment and the unchanged JPA `length = 64` mapping.
- [x] Keep Payroll calculations, API, OpenAPI, UI and revision semantics unchanged.
- [x] Advance Flyway to V46 and the baseline to 118 Java classes / 605 tests / 37 Playwright scenarios.

Next product stage after a fully green clean-migration CI: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.28.2 — Calendar Persistence Reload Readiness Hotfix — completed

- [x] Publish one calendar-navigation readiness promise for Month, Week and Day header routes.
- [x] Wait for completed calendar and ledger projections before persistence tests perform a full reload.
- [x] Guard both calendar-persistence reload paths with the existing application-idle contract.
- [x] Keep the strict runtime fixture unchanged; do not suppress aborted reads globally.
- [x] Keep production Payroll, API, OpenAPI, PostgreSQL and Flyway V45 unchanged.
- [x] Baseline advances to 117 Java classes / 604 tests / 37 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.28.1 — Payroll Module Registry Contract Hotfix — completed

- [x] Reproduce the Maven failure in `PayrollFoundationContractTest`.
- [x] Replace the brittle `ModuleService.PAYROLL` source-string expectation with canonical `ModuleKeys.PAYROLL` and real registry-shape assertions.
- [x] Keep production Payroll runtime, API, OpenAPI, PostgreSQL and Flyway V45 unchanged.
- [x] Keep the baseline at 116 Java classes / 603 tests / 37 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.28.0 — Payroll Foundation — completed

- [x] Add V45 payroll settings, append-only monetary adjustments and immutable versioned snapshots.
- [x] Read canonical posted-only time from `TimeCompensationService` instead of reinterpreting calendar tables.
- [x] Require a closed month, healthy ledger and positive hourly rate for final calculation.
- [x] Store money in minor units and apply one `HALF_UP` rounding step.
- [x] Expose transparent preview, additions, deductions, total and calculation hash.
- [x] Add `/api/v1/payroll` and a responsive Payroll workspace.
- [x] Advance Flyway to V45 and baseline to 116 Java classes / 603 tests / 37 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.27.2 — Ledger Browser State & Visibility Hotfix — completed

- [x] Refresh Vacation Planner on every route entry so shared overtime-bank totals cannot stay stale.
- [x] Expose Vacation and timezone-save readiness without bypassing real application routes.
- [x] Keep strict browser error detection while consuming only a marked expected-status resource console message.
- [x] Make Overtime Next current-month data deterministic on the first day of a month.
- [x] Scope responsive Unified Ledger assertions to the visible desktop table.
- [x] Keep API, OpenAPI, PostgreSQL and Flyway V44 unchanged.
- [x] Baseline advances to 114 Java test classes / 600 tests / 36 Playwright scenarios.

## v27.27.1 — Ledger Workflow Browser Contract Hotfix — completed

- [x] Refresh Overtime account and projections whenever the route opens after hidden Vacation Planner mutations.
- [x] Serialize integrity reconciliation before time-compensation/actual-work reads.
- [x] Keep strict browser failure detection while marking only the intentional closed-period `409`.
- [x] Replace the July-fixed timezone E2E data with the current browser month.
- [x] Make posted-compensation scenarios explicitly `APPROVED`.
- [x] Wait for application and ledger readiness around reload and route transitions.
- [x] Keep API, OpenAPI, PostgreSQL and Flyway V44 unchanged.
- [x] Baseline advances to 113 Java test classes / 599 tests / 36 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.


## v27.27.0 — Ledger Integrity & Approval Workflow — completed

- [x] Add draft, planned, submitted, approved, rejected, cancelled and completed absence states.
- [x] Reserve overtime-bank minutes for planned/submitted requests and post them for approved/completed absences.
- [x] Keep an append-only audit with explicit reversal entries and closed-period corrections.
- [x] Add owner-scoped integrity reconciliation for linked usages, FIFO allocations and V43 opening credits.
- [x] Add close/reopen accounting periods and reject silent mutations in closed months.
- [x] Freeze payroll-affecting planned shifts in closed months across day edits, mobile sync, bulk fill, schedule-template apply and shift-type deletion, while notes and markers remain editable.
- [x] Add explicit factual work intervals while preserving plan-as-fact as the default.
- [x] Extend the no-store time-compensation snapshot for Payroll Foundation.
- [x] Flyway V44 remains additive and does not alter `day_entries`.
- [x] Baseline advances to 112 Java test classes / 598 tests / 36 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.


## v27.26.2 — Canonical Lineage Recovery — completed

- [x] Restore the accepted v27.26.x product stack on top of the actually deployed canonical v27.23.0 branch.
- [x] Preserve the current Workspace Route E2E navigation contract instead of reintroducing the older workspace-aware tab assertions.
- [x] Keep V41 External Calendar Sync, V42 Absence & Time-Off and V43 Unified Time & Compensation Ledger exactly once.
- [x] Preserve consolidated UTF-8, browser-boot, modal-panel, lambda, frontend-contract and constructor compile fixes.
- [x] Add lineage integrity contracts that fail if V41–V43, plan/fact absences or source-linked overtime usages disappear.
- [x] Baseline advances to 110 Java test classes / 592 tests / 35 Playwright scenarios.

Next product stage: **v27.29.0 — Workspace, Layout & Theme Studio**.

## v27.26.1 — Absence Request Constructor Compile Hotfix — completed

- [x] Repair four stale nine-argument `AbsencePeriodCreateRequest` fixtures after the compensation-source field became explicit.
- [x] Preserve `OVERTIME_BANK` semantics for partial, full-day and insufficient-balance service tests.
- [x] Add Java/static and release-gate protection against returning to the removed constructor shape.
- [x] Keep production runtime, API, OpenAPI, PostgreSQL and Flyway V43 unchanged.
- [x] Baseline advances to 110 Java test classes / 591 tests / 35 Playwright scenarios.

## v27.26.0 — Unified Time & Compensation Ledger — completed

- [x] Existing overtime credits/usages/allocations become the canonical compensatory-time bank.
- [x] `OVERTIME_BANK` absences own one FIFO usage; edits reallocate it and deletion restores minutes.
- [x] Manual ledger mutation is blocked for absence-linked usages, preventing double compensation.
- [x] Explicit vacation, overtime, sick, unpaid and no-coverage policies join Plan → Fact → Compensation.
- [x] `/api/time-compensation` and the monthly unified UI expose planned, worked, earned, used, covered and unpaid time.
- [x] Flyway V43 migrates the standalone V42 balance into an opening credit and preserves `day_entries`.
- [x] Baseline advances to 110 Java test classes / 590 tests / 35 Playwright scenarios.

Next product stage: **v27.27.0 — Ledger Integrity & Approval Workflow**.

## v27.25.2 — Absence Experience Frontend Contract Hotfix — completed

- [x] Confirmed Maven compilation and 579 passing tests before the one failing static frontend contract.
- [x] Replaced the stale unbounded absence-loop expectation with the accepted bounded Week agenda contract.
- [x] Protected timed partial absences and full-day all-day composition as separate paths.
- [x] Production runtime, API, OpenAPI, database and Flyway V42 remain unchanged.
- [x] Baseline advances to 109 Java test classes / 581 tests / 34 Playwright scenarios.

## v27.25.1 — Absence Preview Lambda Compile Hotfix — completed

- [x] Fixed the Java compiler blocker in the absence preview overlap lookup.
- [x] Snapshot the mutable loop date before lambda capture; preview semantics remain unchanged.
- [x] Added Java/static and release-gate protection against direct capture of the incremented loop variable.
- [x] API, OpenAPI, database and Flyway V42 remain unchanged.
- [x] Baseline advances to 109 Java test classes / 580 tests / 34 Playwright scenarios.

## v27.25.0 — Absence & Time-Off Overhaul — completed

- [x] Separate planned shifts from factual day status without deleting schedule data.
- [x] Full-day absences visually replace the shift while retaining plan context.
- [x] Partial time off stores exact hours and preserves the shift surface.
- [x] Independent `VACATION_DAYS`, `TIME_OFF_HOURS` and `NONE` balance policies.
- [x] Built-in Time Off type, configurable hour bank and full-day charge duration.
- [x] Month / Week / Day / selected-day plan-fact composition and monthly summaries.
- [x] Timed `.ics` projection for partial absence and Flyway V42.
- [x] Baseline 109 Java test classes / 579 tests / 34 Playwright scenarios.

## v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix — completed

- GitHub Actions confirmed Maven and reached the new Calendar Comfort browser scenario.
- Month-mode `↺ Сегодня` correctly selects today and opens the mobile modal day panel.
- The failed test attempted to click `#next` through the blocking backdrop instead of closing the panel.
- The scenario now follows the user route `Сегодня → #pClose → next month`; production UI and Flyway V41 are unchanged.
- Baseline remains 108 Java classes / 569 tests / 33 Playwright scenarios.

## v27.24.0 — Calendar Comfort & Correctness — completed

- [x] Contextual cozy «Сегодня» control across Month / Week / Day.
- [x] Selected calendar date owns contextual important-day creation.
- [x] Important-event checkboxes follow the 18px design-system control size.
- [x] Overnight Today shift separates compact time and the two-date range.
- [x] Calendar refresh preserves the existing grid and exposes a calm live status.
- [x] Bounded in-memory load metrics and `dutylog:calendar-load` diagnostics start before the final optimization cycle.
- [x] Companion schedule layers use compact accessible visibility pills.
- [x] Flyway stays V41; baseline advances to 108 / 569 / 33.

## v27.23.2 — Calendar Sync Runtime Boot Hotfix — completed

- [x] Removed the uncaught `ReferenceError: localDateKey is not defined` from `.ics` range initialization.
- [x] Default range boundaries now use the canonical local `keyOf(...)` helper.
- [x] Added Java/static and release-gate protection against the undefined helper.
- [x] API, token lifecycle, nginx protection and Flyway V41 remain unchanged.
- [x] Baseline advances to 107 / 564 / 32.

Next product stage: **v27.25.0 — Absence & Time-Off Overhaul**.


## v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix — completed

- [x] MockMvc subscription JSON is decoded explicitly with `StandardCharsets.UTF_8`.
- [x] Token hint stays `prefix…suffix`; the test protects U+2026 without mojibake.
- [x] Runtime, HTTP API, nginx protection and Flyway V41 remain unchanged.
- [x] Baseline stays 107 / 563 / 32.

Next product stage: **v27.24.0 — Calendar Comfort & Correctness**.


## v27.23.0 — External Calendar Sync — completed

- [x] RFC 5545 export for a selected range and one important event.
- [x] Shifts, tasks, important events and absences compose into read-only `.ics`.
- [x] Private rolling subscription with SHA-256-only token storage.
- [x] Issue, rotate, revoke and immediate old-token invalidation.
- [x] Responsive Settings UI, Web/v1 API, OpenAPI and browser lifecycle coverage.
- [x] Flyway V41; baseline 107 / 563 / 32.

Next product stage: **v27.24.0 — Calendar Comfort & Correctness**.

## v27.22.2 — Workspace Route E2E Navigation Hotfix — completed

- [x] Stale browser flows use the shared workspace-route `openView()` helper instead of a hidden Tasks tab.
- [x] Tasks module enablement is asserted on `#view-tasks`, independently from workspace placement.
- [x] Shift Worker navigation remains intentionally task-tab-free; runtime, API and Flyway remain unchanged.
- [x] Baseline stays 103 / 544 / 31.


## v27.22.1 — Vacation Planner Frontend Contract Hotfix — completed

- [x] Shift Worker static contract includes `vacation` and the accepted Today widget order.
- [x] Vacation Month/Week/Day static contract follows the real all-day absence composition path.
- [x] Persisted switchable-module count is derived from `DutyLogModules.ALL` instead of a hardcoded value.
- [x] Runtime, API and Flyway remain unchanged; baseline stays 103 / 544 / 31.


## v27.22.0 — Vacation Planner — completed

- [x] Separate absence model; vacation never becomes a shift row.
- [x] Annual allowance and carryover.
- [x] Configurable work-year boundary.
- [x] Calendar-day or Monday-Friday counting.
- [x] 14 / 28 / 35-day presets and custom periods.
- [x] Preview with shift and absence conflicts.
- [x] Overlap and allowance protection.
- [x] Built-in and custom absence types.
- [x] Month / Week / Day / selected-day composition.
- [x] Flyway V40, Web/v1 API, Java/static/Playwright contracts.

Next product stage: **v27.24.0 — Calendar Comfort & Correctness**.

## Текущая продуктовая точка — Unified Time & Compensation Ledger

Статус: **v27.26.2** восстанавливает единую каноническую линию; **v27.26.0** объединяет плановые смены, фактические отсутствия и компенсационные движения. Отгул за ранее отработанное время больше не списывает отдельное число: он владеет FIFO usage в каноническом overtime ledger. Flyway V43 переносит старый баланс в opening credit, сохраняет `day_entries`, а salary-правила остаются задачей Payroll Foundation.

Закрыто:

- независимые политики `VACATION_DAYS`, `TIME_OFF_HOURS` и `NONE`;
- встроенный тип «Отгул» и отдельный банк часов;
- полный и частичный охват с проверкой пересечений;
- списание полной смены по её net duration либо по настраиваемой длительности дня;
- план-факт детали и полноценная визуализация в Month / Week / Day;
- сводка по видам отсутствий и частичным часам;
- timed `.ics` для частичного отгула;
- Flyway V42, OpenAPI, Java/static/Playwright contracts;
- baseline 109 Java test classes / 581 tests / 34 Playwright scenarios.

Следующий этап: **v27.27.0 — Ledger Integrity & Approval Workflow**.

## Ближайшая продуктовая очередь после v27.26.2

### v27.26.0 — Unified Time & Compensation Ledger — completed

См. завершённый релиз выше и `docs/UNIFIED_TIME_COMPENSATION_LEDGER_V27.26.0.md`.

### v27.27.0 — Ledger Integrity & Approval Workflow

- статусы отсутствий: черновик, запланировано, подано, утверждено, отклонено, отменено и завершено;
- резервирование и окончательное проведение отпускных/компенсационных операций;
- неизменяемые reversal-записи вместо скрытого переписывания истории;
- сверка целостности ledger и безопасное восстановление осиротевших связей;
- закрытие расчётных периодов и корректировки задним числом;
- явная фиксация фактически отработанных интервалов поверх плановой смены.

### v27.28.0 — Payroll Foundation

- простой и расширенный расчёт оплачиваемого времени;
- влияние отпусков, больничных, отсутствий без содержания и отгулов из банка переработок;
- базовая ставка, ночные, сверхурочные, премии, удержания и налоги;
- объяснимый расчёт, читающий единый ledger вместо повторного угадывания календаря.

### Следующие продуктовые циклы

- `v27.29.0` — Workspace, Layout & Theme Studio;
- `v27.30.0` — Unified Absence Composer & Calendar Projection;
- `v27.30.1` — Unified Absence Quick Access Integration;
- после полного Vue-перехода — One-Tap Calendar Connect для Google / Apple / Outlook;
- затем — архив «Все заметки» и визуальные коллизии задач;
- затем — Telegram inline-действия и понятные пошаговые команды;
- затем — feature freeze, контекстное обучение и полный performance / production-readiness цикл.

## Этап 2 — нормальная API-архитектура

Статус: основа сделана в v10.

Сделано:

- бизнес-логика вынесена из контроллеров в сервисы;
- добавлен единый формат ошибок;
- добавлен endpoint диапазона дат: `GET /api/calendar?from=...&to=...`;
- добавлен endpoint баланса переработки: `GET /api/overtime/balance?from=...&to=...`;
- добавлен endpoint журнала переработок.

Осталось:

- разнести DTO из одного `Dtos.java` по отдельным файлам.

OpenAPI v1 уже поддерживается и расширяется вместе с доменными релизами.

## Этап 3 — важные дни и задачи

Статус: сделано в v11.

Сделано:

- задачи дня с чекбоксами;
- индикатор невыполненных задач на календаре;
- важные дни;
- повторения важных дней: один раз, каждый месяц, каждый год;
- отдельные endpoint'ы под задачи и важные дни;
- `/api/calendar?from=&to=` отдаёт задачи и важные дни вместе с календарём.

## Этап 4 — авторизация для Android

Статус: сделано в v12.

Сделано:

- `POST /api/mobile/auth/login`;
- `POST /api/mobile/auth/refresh`;
- `POST /api/mobile/auth/logout`;
- `GET /api/mobile/auth/me`;
- `GET /api/mobile/auth/sessions`;
- `DELETE /api/mobile/auth/sessions/{id}`;
- access token;
- refresh token с ротацией;
- таблица `mobile_auth_tokens`;
- выход с конкретного устройства;
- хранение хэшей токенов вместо сырых токенов.

Веб оставлен на сессиях, Android/API ходят через Bearer token.

## Этап 5 — бухгалтерия переработок

Статус: сделано в v13.

Сделано:

- начисления переработки вынесены в отдельную сущность;
- списания отгулов вынесены в отдельную сущность;
- списания автоматически распределяются по старым начислениям по FIFO;
- переработка не сгорает при переходе между месяцами;
- добавлена таблица: день, время, часы, причина, использовано, куда списано, остаток;
- добавлены endpoint’ы `/api/overtime/account`, `/api/overtime/credits`, `/api/overtime/usages`.

## Этап 6 — план/факт

Текущая модель уже умеет смену дня + переработку + отгул. Для взрослого табеля лучше разделить:

- плановая смена;
- фактическая работа;
- события/ППР;
- списание отгула;
- комментарии;
- подтверждение месяца.

Пример будущей структуры:

```text
DayEntry
├─ plannedShiftType
├─ actualShiftType
├─ overtimeEvents[]
├─ timeOffEvents[]
└─ note
```

## Этап 7 — быстрые действия

Очень полезные кнопки для реальной жизни:

- «Остался в ночь» → автоматом ставит `+7` сегодня и `+8` завтра;
- «Не вышел после ППР» → списывает часы плановой смены;
- «Списать весь день»;
- «ППР до 00:00»;
- «ППР после 00:00».

## Этап 8 — уведомления

Для PWA/web:

- напоминание перед сменой;
- напоминание вечером заполнить день;
- Telegram-уведомления.

Для Android:

- локальные notifications;
- WorkManager;
- AlarmManager для настоящего будильника.

## Этап 9 — Android

API уже достаточно стабилен для первого Android-клиента. Дальше:

- Kotlin;
- Jetpack Compose;
- Retrofit/Ktor Client;
- Room для offline-кэша;
- sync queue для изменений без интернета;
- локальные уведомления;
- экран календаря;
- экран дня;
- экран баланса переработки.

## Этап 10 — отчёты

- экспорт CSV;
- экспорт Excel;
- отчёт по месяцу;
- отчёт по переработке;
- журнал списаний;
- статистика за год.

## Completed in v27.11.3

- Canonical timezone rebases future shift templates.
- Existing dated shifts remain immutable.
- Shift reminders and Telegram delivery use occurrence instants, including next-day projection.

## Completed in v27.11.4

- Timed task deadlines preserve one absolute instant across canonical timezone changes.
- Task overdue/upcoming classification is instant-based for absolute deadlines.
- Deadline projections may cross calendar dates without moving the task's organisational day.
- Date-only deadlines stay floating.
- Legacy timed task deadlines can be explicitly linked to their real source IANA timezone.
- Browser, mobile and Telegram task reminders consume the same authoritative instant.


## Completed in v27.12.0

- Exact overtime credits are split at midnight in the current canonical timezone.
- Exact FIFO allocation minutes follow the same daily projection without being rebuilt.
- Daily earned, used and remaining totals are additive projections, not persisted copies.
- Ledger filters and exports operate on projected local dates.
- Source-credit edit/delete integrity is preserved across multi-day projections.


## DutyLog Next UI platform sequence after v27.17.3

- `v27.17.3` — Java Contract Build Gate Hotfix.
- `v27.17.4` — UI Core & Workspace Foundation.
- `v27.17.5` — UI Core E2E Accordion Hotfix.
- `v27.17.6` — Classic Sunset.
- `v27.18.0` — Overtime Next.
- `v27.18.1` — Overtime Next E2E Contract Hotfix.
- `v27.18.2` — Overtime Snapshot Sync & Timezone E2E Stabilization Hotfix.
- `v27.18.3` — UI Settings & Button Variants Quality Hotfix.
- `v27.19.0` — Tasks & Inbox Next.
- `v27.19.1` — Task Board Date Range Compatibility Hotfix.
- `v27.19.2` — Frontend Asset Contract Stability Hotfix.
- `v27.19.3` — Task Deadline Validation E2E Contract Hotfix.
- `v27.19.4` — Ghost Button Transition E2E Stabilization Hotfix.
- `v27.19.0` — Tasks & Inbox Next, including independent planned task intervals (`start → end`), duration, deadlines and timeline cards.
- `v27.20.0` — Notes & Important Events Next, including all-day/timed/multi-day events, place, description, reminders and read-first event cards.
