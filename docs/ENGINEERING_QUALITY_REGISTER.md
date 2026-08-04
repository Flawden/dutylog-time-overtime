---
title: "DutyLog — Engineering Quality Register"
status: active
release_foundation: v27.35.1
created: 2026-08-04
updated: 2026-08-04
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
| Q-01 | Frontend lockfile, `npm ci`, pinned Node/npm | `v27.35.0`, executable fix `v27.35.1` | каждый CI/Docker build | clean checkout создаёт pinned dependency tree и локальные `vue-tsc`/`vitest`/`vite` launchers | DONE |
| Q-02 | OpenAPI-generated TypeScript types/client и drift gate | `v27.35.0` | каждый API change | CI падает при неперегенерированном contract | DONE |
| Q-03 | Vue error boundary, `unhandledrejection`, route/release/requestId diagnostics | `v27.35.0` | каждый Vue domain | controlled failure показывает recovery UI и correlation id | DONE |
| Q-04 | Migration manifest и parity matrix | `v27.35.0` template | `v27.36.0–v27.40.0` | manifest в repository + release checklist | DONE |
| Q-05 | ADR repository и обязательные решения | `v27.35.0` | архитектурные изменения | ADR accepted/superseded index | DONE |
| Q-06 | Optimistic concurrency / stale-write / double-submit policy | baseline `v27.36.0` | все редактируемые домены | 409/idempotency E2E и documented UX | LOCKED |
| Q-07 | PWA asset/version compatibility и upgrade E2E | baseline `v27.37.0` | каждый frontend release | previous cache → new release upgrade scenario | LOCKED |
| Q-08 | Performance budgets и bundle diff | baseline `v27.37.0` | каждый frontend release; полный audit `v27.45.0` | CI report/thresholds | LOCKED |
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

## Milestone gates

### Gate A — перед первой доменной миграцией

Q-02–Q-05 закрыты в `v27.35.0`; Q-01 окончательно закрывается `v27.35.1` после чистого CI/staging. Gate A открывается для `v27.36.0` только после этого зелёного прогона.

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
