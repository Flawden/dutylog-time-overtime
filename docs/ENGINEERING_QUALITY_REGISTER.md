---
title: "DutyLog — Engineering Quality Register"
status: active
release_foundation: v27.37.3
created: 2026-08-04
updated: 2026-08-08
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
| Q-08 | Performance budgets и bundle diff | baseline `v27.37.0` | каждый frontend release; полный audit `v27.45.0` | fail-closed raw/gzip budget in Vite audit | ACTIVE |
| Q-09 | Accessibility acceptance | design system уже ACTIVE | каждый domain; полный audit `v27.45.0` | keyboard/focus/ARIA/contrast evidence | ACTIVE |
| Q-10 | Offline queue и reconnect correctness | `v27.38.0` | каждый offline-capable domain | offline/reconnect E2E | LOCKED |
| Q-11 | Integration secrets, CSP, cookies, source-map policy | `v27.39.0` | security audit `v27.45.0` | security checklist + headers/config evidence | LOCKED |
| Q-12 | Legacy owner/bridge retirement guards | каждый domain | финал `v27.40.0` | deleted files + negative release contracts | ACTIVE |
| Q-13 | Real backup restore rehearsal on clean PostgreSQL | preparation before freeze | mandatory `v27.45.0`; recurring after release | restore report + smoke evidence | BEFORE_RC |
| Q-14 | Upgrade and rollback compatibility with data written by new version | baseline each schema/domain change | full rehearsal `v27.45.0` | previous/new image matrix | ACTIVE |
| Q-15 | Dependency vulnerability review and update policy | `v27.35.0` policy | recurring; blocking audit `v27.45.0` | Dependabot/npm/Maven audit record | LOCKED |
| Q-16 | Pilot-user feedback and long-running stability | after `v27.44.0` | `v27.46.0` | pilot log, unresolved P0/P1 = 0 | BEFORE_RC |
| Q-17 | Production runbook, incident response and operator checklist | draft `v27.45.0` | final `v27.46.0` | versioned runbook | BEFORE_RC |

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

### Gate C — feature freeze после `v27.43.0`

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
