---
title: "<Domain> Vue migration manifest"
status: draft
owner: "<team/person>"
target_release: "v27.xx.0"
legacy_removal_release: "v27.xx.0"
created: "YYYY-MM-DD"
updated: "YYYY-MM-DD"
---

# <Domain> Vue migration manifest

## Domain owner

- Product owner:
- Backend owner:
- Vue owner:
- Final UI owner after this release: Vue

## Legacy entry points

| Entry point | File/function/route | User-visible purpose | Removal evidence |
|---|---|---|---|
|  |  |  |  |

## User journeys

| ID | Journey | Preconditions | Expected result | Error/retry behavior |
|---|---|---|---|---|
| J-01 |  |  |  |  |

## API endpoints

| operationId | Method/path | Generated request/response types | Mutation/idempotency notes |
|---|---|---|---|
|  |  |  |  |

## Server invariants

- Business rules that remain owned by Spring Boot:
- Permission rules:
- Conflict/`409` rules:
- Closed-period/ledger/payroll implications:

## Offline/PWA behavior

- Cache behavior:
- Queue/reconnect behavior:
- Previous-shell/new-assets compatibility:
- Upgrade test:

## Accessibility requirements

- Keyboard route:
- Focus restoration:
- Screen-reader names/status:
- Reduced motion:
- Contrast/non-color meaning:

## Existing tests

| Layer | Test/spec | Protected behavior | Keep/replace/delete |
|---|---|---|---|
| Java |  |  |  |
| Vitest |  |  |  |
| Playwright |  |  |  |

## Vue target modules

```text
frontend/src/features/<domain>/
├── api/
├── components/
├── composables/
├── pages/
├── stores/
├── types/
└── tests/
```

## Temporary bridge capabilities

| Capability | Typed payload | Why required | Regression test | Removal release |
|---|---|---|---|---|
| none | — | — | — | — |

## Legacy files to delete

- [ ] Render functions/listeners
- [ ] Legacy HTML nodes
- [ ] Domain CSS
- [ ] Legacy modal/editor owner
- [ ] Old E2E helpers/selectors
- [ ] Temporary bridge capability

## Rollback expectations

- Previous image:
- Data written by new version:
- Known rollback limitation:
- PWA/service-worker handling:

## Known non-goals

-

## Parity matrix

| Scenario | Legacy baseline | Vue implementation | Component test | E2E | Accessibility | Offline | Status |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  | yes/no | planned |

## Release review

```text
Migrated:
Parity verified:
Legacy removed:
Bridge removed/remaining:
OpenAPI changes:
PWA impact:
Rollback impact:
Known limitations:
```
