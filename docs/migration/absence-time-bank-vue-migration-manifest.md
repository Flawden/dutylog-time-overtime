---
title: "Absence & Time Bank Vue migration manifest"
status: complete
owner: "DutyLog"
target_release: "v27.36.0"
legacy_removal_release: "v27.36.0"
created: "2026-08-04"
updated: "2026-08-05"
---

# Absence & Time Bank Vue migration manifest

## Domain owner

- Product owner: DutyLog product roadmap.
- Backend owner: Spring Boot absence, overtime, compensation and ledger services.
- Vue owner: `frontend/src/features/absence-time-bank/`.
- Final UI owner after this release: Vue.

## Legacy entry points

| Entry point | File/function/route | User-visible purpose | Removal evidence |
|---|---|---|---|
| Absence route | `#vacation`, `#view-vacation` | Journal and composer | Vue route root owns the screen; the legacy root is retired when the workspace mounts. |
| Time Bank route | `#overtime`, `#view-overtime` | Balance, credits, usage and FIFO | Vue route root owns the screen; the legacy root is retired when the workspace mounts. |
| Calendar/Today absence action | `openAbsenceComposer`, `openAbsenceEditor` | Cross-domain quick action | Named typed adapter delegates to `DutyLogVueDomains.absenceTimeBank`; no legacy render or data mutation occurs. |
| Calendar overtime action | `openOvertimeCreditModal` | Open exact-interval credit editor | Named typed adapter delegates to Vue. |
| Historical refresh hook | `loadLedgerPage` | Refresh after a legacy calendar mutation | Delegates to the Vue domain refresh and no longer renders the legacy ledger. |

## User journeys

| ID | Journey | Preconditions | Expected result | Error/retry behavior |
|---|---|---|---|---|
| J-01 | Create full-day absence | Absence module enabled | Preview, save and journal refresh use generated v1 operations | Validation stays in the modal; retry never duplicates a mutation. |
| J-02 | Create partial time off | Free overtime exists | Backend creates the absence-owned reservation/posting and FIFO allocation | Shortage and `409` are shown; the server model is refreshed. |
| J-03 | Edit/delete absence | Owned absence exists | Backend remains lifecycle owner; linked usage follows automatically | Concurrent change refreshes the journal before retry. |
| J-04 | Inspect Time Bank | Overtime module enabled | Signed totals, reserve, chart, credits, usage and FIFO are visible | Read failure keeps the shell and offers refresh. |
| J-05 | Create/edit/delete credit | User owns credit | Exact interval or manual hours are previewed and persisted | Double submit is blocked; conflict refreshes account. |
| J-06 | Reuse overtime scenario | Shift exists for selected day | Scenario fills the exact interval and remains editable in the same modal | Missing shift leaves the draft intact and explains the error. |
| J-07 | Follow two-way link | Absence-owned usage exists | Absence → usage and usage → absence preserve context | Missing historical row falls back to a refreshed read model. |
| J-08 | Forecast FIFO | Open source credits exist | Oldest source is consumed first and remaining/shortage is shown | Forecast is client-only explanation; save still revalidates on backend. |

## API endpoints

| operationId | Method/path | Generated request/response types | Mutation/idempotency notes |
|---|---|---|---|
| `getVacationPlanner` | GET `/api/v1/vacation-planner` | `VacationPlanner` | Read model only. |
| `previewAbsence` | POST `/api/v1/vacation-planner/preview` | `AbsencePreviewInput` → `AbsencePreview` | Abortable, side-effect free. |
| `createAbsencePeriod` | POST `/api/v1/vacation-planner/absences` | `AbsencePeriodInput` → `AbsencePeriod` | UI blocks duplicate submission; backend is authoritative. |
| `updateAbsencePeriod` | PATCH `/api/v1/vacation-planner/absences/{id}` | `AbsencePeriodPatch` → `AbsencePeriod` | `409` refreshes before retry. |
| `deleteAbsencePeriod` | DELETE `/api/v1/vacation-planner/absences/{id}` | — | Deleting absence reverses its owned usage on backend. |
| `overtimeAccount` | GET `/api/v1/overtime/account` | `OvertimeAccount` | Read model only. |
| `previewOvertimeCredit` | POST `/api/v1/overtime/preview` | `OvertimeCreditCreateRequest` → `OvertimeCreditPreview` | Abortable, side-effect free. |
| `createOvertimeCredit` | POST `/api/v1/overtime/credits` | `OvertimeCreditCreateRequest` → `OvertimeAccount` | Duplicate submit blocked. |
| `updateOvertimeCredit` | PATCH `/api/v1/overtime/credits/{id}` | `OvertimeCreditUpdateRequest` → `OvertimeAccount` | Backend protects allocated sources. |
| `deleteOvertimeCredit` | DELETE `/api/v1/overtime/credits/{id}` | — | Backend refuses unsafe deletion. |
| `timeCompensationSummary` | GET `/api/v1/time-compensation` | `TimeCompensationSummary` | Read model only. |
| `inspectLedgerIntegrity` | GET `/api/v1/ledger-integrity` | `LedgerIntegrity` | Read/reconciliation boundary remains server-owned. |
| `listActualWorkIntervals` | GET `/api/v1/actual-work` | `ActualWorkInterval[]` | Read model only in this screen. |
| `quickScenarios` | GET `/api/v1/quick-scenarios` | `QuickScenario[]` | Read model. |
| `createQuickScenario` | POST `/api/v1/quick-scenarios` | `QuickScenarioCreateRequest` → `QuickScenario` | Duplicate submit blocked. |
| `updateQuickScenario` | PATCH `/api/v1/quick-scenarios/{id}` | `QuickScenarioUpdateRequest` → `QuickScenario` | Server validation retained. |
| `deleteQuickScenario` | DELETE `/api/v1/quick-scenarios/{id}` | — | Explicit confirmation required. |

## Server invariants

- Business rules that remain owned by Spring Boot: allowance arithmetic, shift/absence overlap, exact credited minutes, compensation policy, FIFO reservation/posting/reversal, accounting-period integrity and ownership.
- Permission rules: every read and mutation remains same-origin authenticated and ownership-scoped.
- Conflict/`409` rules: closed period, allocated-credit protection and stale/concurrent domain state are never converted to optimistic frontend success.
- Closed-period/ledger/payroll implications: Vue only explains the result; it does not reproduce or bypass ledger/payroll rules.

## Offline/PWA behavior

- Cache behavior: the versioned Spring Boot image serves the Vue bundle under the existing PWA cache identity.
- Queue/reconnect behavior: financial/time mutations are not queued offline; transport errors remain retryable read/mutation errors.
- Previous-shell/new-assets compatibility: release identity invalidates old assets while the backend remains API-compatible.
- Upgrade test: normal CI bundle audit plus Playwright shell/runtime error collectors.

## Accessibility requirements

- Keyboard route: navigation, tabs, modal controls, forms, tables and disclosure cards are keyboard reachable.
- Focus restoration: shared `UiModal` traps/restores focus; two-way links move to the requested domain owner.
- Screen-reader names/status: tabs use `role=tab`, alerts use `role=alert`, forecasts use `aria-live`, form inputs have labels.
- Reduced motion: route and tab transitions respect `prefers-reduced-motion`; focus scroll is non-essential.
- Contrast/non-color meaning: status, reserve, shortage and integrity always include text, not color alone.

## Existing tests

| Layer | Test/spec | Protected behavior | Keep/replace/delete |
|---|---|---|---|
| Java | `VacationPlanner*`, overtime/ledger services and controllers | Backend source of truth and lifecycle | Keep. |
| Java | `VueAbsenceTimeBankMigrationTest` | Ownership, generated client, Q-06 and retirement contracts | Add. |
| Vitest | `model.spec.ts` | FIFO, split projection, scenario calculation | Add. |
| Vitest | `absenceTimeBankStore.spec.ts` | stale reads, preview races, double submit, `409`, linked focus | Add. |
| Playwright | existing absence/overtime suites | Product parity | Update to Vue owner and generated v1 calls. |
| Playwright | `vue-absence-time-bank-migration.spec.js` | runtime retirement and duplicate-submit boundary | Add. |

## Vue target modules

```text
frontend/src/features/absence-time-bank/
├── api/absenceTimeBankApi.ts
├── components/
│   ├── AbsenceComposer.vue
│   ├── AbsencePage.vue
│   ├── AbsenceTimeBankWorkspace.vue
│   ├── CreditEditor.vue
│   └── TimeBankPage.vue
├── stores/absenceTimeBankStore.ts
├── types/domain.ts
├── types/model.ts
└── *.spec.ts
```

## Temporary bridge capabilities

| Capability | Typed payload | Why required | Regression test | Removal release |
|---|---|---|---|---|
| `openAbsenceComposer` | `AbsenceComposerOpenOptions` | Today/Calendar are not yet Vue domain owners | Java architecture contract + Playwright quick actions | Their domain migration release |
| `openAbsenceEditor` | absence id | Calendar day drawer remains legacy-owned | Existing calendar/absence E2E | Vue Calendar release |
| `openCreditEditor` | optional local date | Calendar day overtime action remains legacy-owned | Overtime editor E2E | Vue Calendar release |
| `openTimeBankUsage` | optional absence id | Two-way route from journal | Migration E2E | Removed when shell route state replaces bridge |
| `refresh` | none | Historical `loadLedgerPage` callers after calendar mutations | Daily-projection E2E | Vue Calendar release |

## Legacy files to delete

- [x] Legacy route render/listener ownership (retired at runtime; route functions delegate before rendering)
- [x] Legacy route HTML nodes (removed when Vue workspace mounts)
- [x] Legacy modal/editor ownership for Absence and credit flows
- [x] Old E2E route helpers/selectors for the two migrated roots
- [ ] Named cross-domain adapters required by Today/Calendar
- [ ] Dead source blocks in ordered legacy bundles; final physical deletion belongs to `v27.40.0` after all callers migrate

## Rollback expectations

- Previous image: `v27.35.7` can be redeployed without database rollback.
- Data written by new version: uses unchanged backend endpoints and schema; previous UI can read it.
- Known rollback limitation: a browser may briefly hold a newer cached bundle until the service worker observes the older image identity.
- PWA/service-worker handling: release identity changes to `27.36.0`; no new offline mutation queue is introduced.

## Known non-goals

- No new absence, overtime, payroll or approval business rule.
- No database/Flyway change.
- No offline mutation queue.
- No redesign of Calendar or Today domain ownership.

## Parity matrix

| Scenario | Legacy baseline | Vue implementation | Component test | E2E | Accessibility | Offline | Status |
|---|---|---|---|---|---|---|---|
| Absence journal/filter | Legacy planner list | `AbsencePage.vue` | store filters | existing planner suites | labelled search/filter | read retry | DONE |
| Full/partial composer | Legacy embedded form/modal | `AbsenceComposer.vue` | model/store | absence composer suites | labelled modal/form | no queue | DONE |
| Preview and balances | Legacy preview card | typed preview + context | FIFO model | planner/time-off suites | `aria-live` | retry | DONE |
| Time Bank overview | Legacy ledger overview | Vue metrics/plan-fact/integrity | model | ledger approval suite | text statuses | read retry | DONE |
| Credits/table/cards/chart | Legacy table/cards/chart | Vue responsive ledger | chart model | Overtime Next suite | table + disclosure cards | read retry | DONE |
| Exact credit editor | Legacy credit modal | `CreditEditor.vue` | store/model | editor/scenario suites | labelled modal/form | no queue | DONE |
| Usage ownership | Legacy usage cards | absence-owned usage tab | store | canonical ledger suites | explicit owner text | read retry | DONE |
| FIFO queue/forecast | Legacy FIFO view | Vue queue and forecast | FIFO model | experience/next suites | live textual result | client explanation | DONE |
| Two-way links | Legacy hash/focus | typed domain bridge | store | experience + migration suites | named buttons | n/a | DONE |
| Q-06 stale/double submit | implicit legacy behavior | sequence token + mutation lock + `409` refresh | store tests | migration suite | durable alerts | no queue | DONE |
| Runtime owner retirement | dual legacy roots | Vue owner + retired roots | Java contract | migration suite | one active owner | n/a | DONE |

## Release review

```text
Migrated: Absence Composer, absence journal, Time Bank overview/credits/usages/FIFO, exact credit and scenario editor.
Parity verified: backend lifecycle, balances, split projections, plan/fact, integrity, responsive ledger, two-way links and error paths.
Legacy removed: legacy Absence/Time Bank route and modal owners are retired; Vue is the only runtime owner.
Bridge removed/remaining: no legacy render bridge remains; only named typed cross-domain commands from un-migrated Today/Calendar remain.
OpenAPI changes: typed AbsencePreview, v1 quick-scenario CRUD, and generated contract regenerated to 98 operations / 103 schemas.
PWA impact: versioned Vue assets only; no offline mutation queue.
Rollback impact: image rollback only; no schema/data migration.
Known limitations: physical deletion of dead ordered-script blocks waits for final legacy retirement after their remaining callers migrate.
```

## Browser parity follow-up — v27.36.4

The first complete Chromium run after migration reached all 45 scenarios and exposed eight deterministic presentation-parity gaps. v27.36.4 closes them without changing the migration owner or backend contract:

- retired legacy guide DOM is physically removed;
- Vue publishes winning planner/account refreshes to remaining legacy projections;
- Today/Calendar modal launches preserve route context;
- edit deletion is owned by the visible Vue modal;
- Time Bank overview exposes ratio and next FIFO source;
- selected-day absence actions delegate to the Vue editor.

Full acceptance remains conditional on green exact CI, Chromium, Docker, clean PostgreSQL and staging.

## Final Chromium parity follow-up — v27.36.5

The v27.36.4 run passed 43/45 scenarios and isolated two remaining deterministic gaps. v27.36.5:

- publishes explicit `true`/`false` ARIA tokens for month/year ownership immediately;
- refreshes planner/account context before composer draft derivation;
- preserves stale-refresh sequence ownership and the v27.36.4 projection bridge;
- removes duplicate push validation without removing any blocking test or deployment smoke.

Acceptance remains conditional on the exact Node 20, Maven, 45-scenario Chromium, immutable-image and clean-PostgreSQL staging path.


## Usage-date chart parity follow-up — v27.36.6

The exact v27.36.5 run passed 44/45 scenarios and isolated the final deterministic gap in Time Bank chart bucketing. v27.36.6:

- keeps earned values on credit `workedDate`;
- moves used values to actual `usage.usageDate`;
- excludes aggregate `credit.usedHours` from dated chart events;
- preserves day buckets in month mode and folds both event kinds into `YYYY-MM` in year mode;
- changes no domain owner, backend endpoint, OpenAPI schema, PostgreSQL schema or Flyway migration.

Acceptance remains conditional on the exact Node 20, Maven, 45-scenario Chromium, immutable-image and clean-PostgreSQL staging path.
