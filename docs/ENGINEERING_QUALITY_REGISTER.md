---
title: "DutyLog — Engineering Quality Register"
status: active
release_foundation: v27.40.15
created: 2026-08-04
updated: 2026-08-10
---

# DutyLog — Engineering Quality Register

Этот документ хранит сквозные работы, которые нельзя честно закрыть одним feature release. У каждой записи есть первая привязка, повторяющийся gate и доказательство завершения.

## Статусы

- `LOCKED` — решение принято и привязано к релизу.
- `ACTIVE` — выполняется или уже действует как gate.
- `RECURRING` — проверяется регулярно.
- `BEFORE_RC` — обязательно до Release Candidate.
- `DONE` — подтверждено артефактом или фактическим прогоном.

## Реестр

| ID | Работа | Первая привязка | Повторяющийся gate / финальный срок | Доказательство | Статус |
|---|---|---|---|---|---|
| Q-01 | Frontend lockfile, `npm ci`, pinned Node/npm | `v27.35.0`; authentic graph promoted `v27.35.3` | каждый CI/Docker build | exact CI-generated lockfile committed; clean checkout uses `npm ci` without regeneration | DONE |
| Q-02 | OpenAPI-generated TypeScript types/client и drift gate | `v27.35.0` | каждый API change | CI падает при неперегенерированном contract | DONE |
| Q-03 | Vue error boundary, `unhandledrejection`, route/release/requestId diagnostics | `v27.35.0` | каждый Vue domain | controlled failure показывает recovery UI и correlation id | DONE |
| Q-04 | Migration manifest и parity matrix | `v27.35.0` template | `v27.36.0–v27.40.0` | manifest в repository + release checklist | DONE |
| Q-05 | ADR repository и обязательные решения | `v27.35.0` | архитектурные изменения | ADR accepted/superseded index | DONE |
| Q-06 | Optimistic concurrency / stale-write / double-submit policy | baseline `v27.36.0` | все редактируемые домены | sequence-token stale-read guard, mutation lock, durable 409 refresh, Vitest + Playwright double-submit evidence | DONE |
| Q-07 | PWA asset/version compatibility и upgrade E2E | baseline `v27.37.0` | каждый frontend release | ADR-006 + previous cache → current cache Chromium scenario | ACTIVE |
| Q-08 | Performance budgets и bundle diff | baseline `v27.37.0` | каждый frontend release; полный audit `v27.49.0` | fail-closed raw/gzip budget in Vite audit | ACTIVE |
| Q-09 | Accessibility acceptance | design system уже ACTIVE | каждый domain; полный audit `v27.49.0` | keyboard/focus/ARIA/contrast evidence | ACTIVE |
| Q-10 | Offline queue и reconnect correctness | `v27.38.0` | каждый offline-capable domain | existing dataLayer queue/cache reused through typed Vue bridge; offline/reconnect E2E | ACTIVE |
| Q-11 | Integration secrets, CSP, cookies, source-map policy | `v27.39.0` | security audit `v27.49.0` | ADR-008 + generated integration boundaries + headers/config evidence | ACTIVE |
| Q-12 | Legacy owner/bridge retirement guards | каждый domain | финал `v27.40.0` | deleted files + negative release contracts | ACTIVE |
| Q-13 | Real backup restore rehearsal on clean PostgreSQL | preparation before freeze | mandatory `v27.49.0`; recurring after release | restore report + smoke evidence | BEFORE_RC |
| Q-14 | Upgrade and rollback compatibility with data written by new version | baseline each schema/domain change | full rehearsal `v27.49.0` | previous/new image matrix | ACTIVE |
| Q-15 | Dependency vulnerability review and update policy | `v27.35.0` policy | recurring; blocking audit `v27.49.0` | Dependabot/npm/Maven audit record | LOCKED |
| Q-16 | Pilot-user feedback and long-running stability | after `v27.44.0` | `v27.46.0` | pilot log, unresolved P0/P1 = 0 | BEFORE_RC |
| Q-17 | Production runbook, incident response and operator checklist | draft `v27.49.0` | final `v27.50.0` | versioned runbook | BEFORE_RC |

## ADR backlog

Создать в `docs/architecture/adr/`:

| ADR | Решение | Целевой релиз |
|---|---|---|
| ADR-001 | Vue 3 + strict TypeScript как единственный frontend target | `v27.35.0` |
| ADR-002 | Монорепозиторий и один production application image | `v27.35.0` |
| ADR-003 | Spring Boot как source of truth для бизнес-правил | `v27.35.0` |
| ADR-004 | Incremental strangler migration и правила bridge | `v27.35.0` |
| ADR-005 | OpenAPI-generated frontend contract | `v27.35.0` |
| ADR-006 | PWA asset/version upgrade strategy | `v27.37.0` |
| ADR-007 | Optimistic concurrency and idempotency | `v27.36.0–v27.38.0` |
| ADR-008 | Production source maps and frontend diagnostics | `v27.39.0` |
| ADR-009 | Vue Router final URL strategy and legacy hash retirement | `v27.40.0` |
| ADR-010 | Expand/contract database migration and rollback compatibility | до `v27.45.0` |




## Settings, Workspace & Integrations migration note — v27.39.0

- Vue owns Settings navigation plus Profile, Language, Modules, Calendar Sync and Appearance/Workspace Studio.
- Time, Schedule and Notifications are native Vue Settings surfaces in v27.40.0; the Settings legacy host/parking bridge is retired. Remaining v27.40.x blockers are selected-day Calendar, routing/modal adapters, offline dataLayer coupling, Payroll/Admin and numbered-JS UI ownership.
- Settings writes use the generated `/api/v1/*` client. Telegram receives a canonical `/api/v1/telegram` alias; calendar bearer URLs are held only in volatile Vue state after issue/rotation and are never written to localStorage or diagnostics.
- Q-11 becomes ACTIVE: ADR-008 disables public production source maps by default and permits only explicit hidden diagnostic maps. Existing CSP/cookie/backend module guards remain unchanged.
- OpenAPI advances to 118 operations / 120 schemas; Flyway remains V47.

## Browser parity continuation — v27.38.13

- Full v27.38.11 Playwright traces prove the remaining Task failures are Vue recovery crashes after successful 200 mutations/projections, not backend projection loss.
- Q-06/Q-12: legacy selected-day summary rendering now yields `#sumTasks/#sumNote/#sumImp` to the always-mounted Vue Productivity owner, eliminating cross-owner DOM mutation.
- Q-07: the PWA upgrade scenario uses canonical onboarding preset key `basic`; no service-worker timeout or claim behavior is weakened.
- No quality gate, timeout, retry, runtime-error collector, backend business rule, database schema or OpenAPI shape is weakened.

## Browser parity continuation — v27.38.11

- v27.38.10 reached 42 passed / 5 failed with no flaky retry after the mandatory gates reached Browser E2E.
- v27.38.11 keeps strict acceptance and fixes Task read-your-write projection sequencing, stale generated-Calendar waits in the remaining browser paths, and duplicate first-install PWA update lifecycle.
- No quality gate, timeout, retry, runtime-error collector, backend business rule, database schema or OpenAPI shape is weakened.

## Shared browser parity note — v27.38.9

- The complete v27.38.7 browser run reached 22/47 and reduced the remaining migration noise to four root causes rather than 25 independent failures.
- Q-12: the shared browser helper now follows Vue Calendar focused-date semantics without restoring legacy toggle ownership.
- Q-06/Q-10: Productivity mutations snapshot reactive drafts into plain data before generated API writes; no duplicate queue or transport is introduced.
- Q-07: first service-worker claim no longer reloads onboarding, while updates of an already-controlled page keep the one-shot reload contract.
- Task Board exposes backend-projected deadline date/time; Spring Boot remains authoritative for deadline timezone semantics.
- Acceptance remains blocked on exact frontend, 751/751 Maven, canary, 47/47 Chromium, image, clean PostgreSQL and staging.

## Vue Productivity ownership/browser parity note — v27.38.7

- The v27.38.6 fail-fast canary proved the Vue bundle and boot path are now present, then exposed a module-readiness race: optional Productivity reads were issued before module/onboarding authority settled and were rejected by the backend after the Minimum preset disabled those modules.
- Q-12 stays fail-closed: Vue now waits for `modulesLoaded && onboardingCompleted` instead of weakening `MODULE_DISABLED` backend guards or browser error assertions.
- Repeated legacy-state publications keep an identical shell module map by reference, preventing the same Productivity refresh from being restarted by unrelated profile/route/language publications.
- Q-10 remains one-queue and backend-authoritative; PostgreSQL/Flyway stay at V47 and OpenAPI stays 101 / 106.
- Acceptance still requires exact frontend, 751/751 Maven, the boot canary, 47/47 Chromium, image, clean PostgreSQL and staging evidence.

## Vue Productivity strict typecheck note — v27.38.3

- The first exact v27.38.2 frontend gate passed authentic lockfile, delivery and OpenAPI drift checks before strict TypeScript found nine migration-boundary errors.
- v27.38.3 preserves strict typing: generated Task owns required task shape, Pinia actions resolve `this`-dependent defaults inside action bodies, and nullable note patch input is normalized before optimistic response-state projection.
- Today cross-domain commands no longer reference global `window` from template instance scope; selected-note autosave uses the environment-specific timeout return type.
- Acceptance remains fail-closed on exact frontend, Maven, 47/47 Chromium via `npm run test:e2e`, image, PostgreSQL and staging gates.

## Vue Productivity static contract alignment note — v27.38.2

- The first v27.38.0 Maven run reached all 751 JUnit methods and isolated exactly three source-contract mismatches.
- Historical Absence/Time Bank coverage no longer hardcodes global generated-contract totals that belong to the drift gate.
- Productivity duration/readiness assertions now follow Vue's dynamic binding and canonical dataset marker without changing runtime ownership.
- Acceptance remains fail-closed on exact frontend, Maven, Chromium, image, PostgreSQL and staging gates.

## Tasks, Notes & Important Days migration note — v27.38.0

- Vue owns Tasks, Notes, Important Days and Inbox presentation while Spring Boot remains the business/source-of-truth owner.
- Q-10 is ACTIVE: the migrated feature reuses the existing dataLayer queue/cache for offline note edits, task completion and Inbox capture instead of creating a second queue.
- Independent latest-read-wins sequences, duplicate-submit guards and HTTP 409 refresh remain fail-closed.
- OpenAPI advances to 101 operations / 106 schemas; PostgreSQL and Flyway remain unchanged at V47.
- Acceptance requires exact 49 Vitest / 751 Maven / 47 Chromium plus immutable-image, clean-PostgreSQL and staging evidence.

## Calendar & Timeline strict typecheck note — v27.37.1

The first exact Node/npm frontend gate exposed four strict compiler errors at newly introduced bridge/action boundaries. v27.37.1 adds explicit callback parameter types and resolves Pinia action defaults inside method bodies without weakening `strict`, `noImplicitAny`, the generated API boundary, or runtime ownership.

## Calendar & Timeline migration note — v27.37.0

- Vue owns Today plus Month, Week and Day read surfaces; Spring Boot remains the business and write owner.
- One named legacy selected-day editor island is retained and explicitly scheduled for v27.37.x retirement work.
- Q-07 starts with accepted ADR-006 and a Chromium previous-cache activation scenario.
- Q-08 starts with versioned raw/gzip browser-bundle ceilings enforced by the existing build audit.
- Acceptance remains blocked on exact 43 Vitest / 738 Maven / 47 Chromium, immutable-image and staging evidence.

## Read-sequencing static contract alignment note — v27.36.8

- Maven identified exactly three stale historical assertions after the v27.36.7 runtime refactor.
- All three now bind to the accepted shared-read semantics rather than deleted implementation strings.
- A dedicated compile-gated contract forbids regression to `refreshSequence`.
- Product runtime, domain authority and deployment topology are unchanged.


## Period-toggle snapshot stability note — v27.36.7

- Presentation-only month/year toggles never replace the canonical overtime account.
- Period-dependent summaries load separately and remain fail-closed.
- Full refreshes and rapid toggles share one latest-read-wins sequence.
- Acceptance remains blocked on exact 45/45 Chromium, immutable image and staging.


## Usage-date chart parity note — v27.36.6

- The exact v27.36.5 run reached 44/45 Chromium and isolated one chart projection mismatch.
- Earned values remain grouped by credit work date; used values are grouped by actual usage date.
- Aggregate `credit.usedHours` is forbidden inside dated chart aggregation to prevent double counting.
- `test` push validation keeps one owner (`Deploy staging`); no blocking gate is downgraded or deleted.

## Browser parity note — v27.36.4

- The full browser suite reached the migrated domain: 37/45 passed and eight deterministic failures identified incomplete DOM retirement and projection synchronization.
- Vue now publishes an authoritative post-refresh planner/account snapshot; only remaining legacy projection surfaces consume it.
- Legacy domain route renderers remain forbidden from the synchronizer, preserving one runtime owner.
- Acceptance remains blocked on a fully green exact CI/staging run.

## Delivery reliability note — v27.36.3

GitHub Actions artifact uploads are best-effort diagnostics: compact JaCoCo XML/CSV and failure-only Playwright reports use short retention and cannot block the release chain. Build, test, browser, image, migration-smoke and deployment gates remain mandatory and fail-closed.

## Milestone gates

### Gate A — перед первой доменной миграцией

Q-01–Q-05 реализованы и приняты: полный CI, Docker image build, clean PostgreSQL smoke и staging `v27.35.7` зелёные. Gate A закрыт; `v27.36.0` является первой разрешённой доменной миграцией.

### Gate B — перед `v27.40.0`

- все domain manifests завершены;
- временные bridges имеют нулевой остаток или отдельное блокирующее решение;
- PWA/performance/accessibility gates действуют в CI.

### Gate C — feature freeze после `v27.48.0`

- новые product features запрещены;
- допускаются onboarding, reliability, security, performance, accessibility и defect fixes.

### Gate D — перед Release Candidate

- restore rehearsal;
- clean install / upgrade / rollback rehearsal;
- security and dependency audit;
- performance and accessibility audit;
- production runbook;
- unresolved P0/P1 = 0.

## Отдельные заметки, не привязанные к одной версии

1. Restore rehearsal должен повторяться после существенных schema changes, а не только один раз перед 1.0.
2. Performance budget сначала фиксирует baseline и отклонения; жёсткие thresholds вводятся после двух-трёх Vue-доменов.
3. Accessibility является свойством design system и каждого компонента, а не финальной косметической проверкой.
4. Security review должен охватывать backend, frontend, PWA, calendar bearer URLs, Telegram secrets, sessions, cookies и CI secrets.
5. Любой новый cross-domain global store, runtime bridge или обход generated API contract требует ADR.
