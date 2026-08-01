# Canonical Lineage Recovery — v27.26.2

## Why this release exists

The active `test` branch was accidentally continued from a green v27.22.2/v27.23.0 archive after the project had already reached the advanced v27.26.x line. This was a real source-tree rollback, not only a version-label problem: V42, V43, plan/fact absences and the unified compensation ledger were absent from the resumed branch.

`v27.26.2` is a forward-only recovery release. Its patch is based on the currently deployed canonical v27.23.0 tree and restores the preserved advanced v27.26.1 implementation while retaining the newer Workspace Route E2E navigation contract.

## Recovered stack

- V41 External Calendar Sync, including UTF-8 response decoding, local-date range initialization and nginx bearer-URL log suppression.
- Calendar Comfort, including contextual `↺ Сегодня`, selected important-day date ownership, overnight date ranges, calm refresh and the real mobile modal-panel route.
- V42 Absence & Time-Off Overhaul with immutable planned shifts, full-day factual replacement, partial intervals and Month/Week/Day plan/fact composition.
- V43 Unified Time & Compensation Ledger with absence-owned FIFO usages, reversible deletion, manual mutation guards and the Plan → Fact → Compensation read model.
- The v27.26.1 compensation-aware request constructor compile fix.
- The canonical v27.22.2 Workspace Route behavior: `openView(...)` owns hidden-route navigation and `#view-tasks` owns module visibility assertions.

## Migration contract

V41, V42 and V43 are present exactly once and remain forward-only. No replacement or duplicate migration is added. `day_entries` is not altered by the recovery release. Existing databases already at V43 receive no schema change; databases at V41 apply V42 and V43 normally during deployment.

## Release boundary

This release intentionally adds no approval workflow, payroll rules or new monetary calculations. Its purpose is to restore the accepted product stack and one canonical history before `v27.27.0 — Ledger Integrity & Approval Workflow`.

## Verification

The release gate protects:

- one copy each of V41, V42 and V43;
- Calendar Sync hardening and absence of `localDateKey(`;
- modal-panel close-before-navigation behavior without `force: true`;
- full/partial absence plan/fact composition;
- V43 source-linked overtime usages and `/api/v1/time-compensation`;
- Workspace Route E2E assertions on `#view-tasks`;
- 110 Java test classes, 592 `@Test` methods and 35 Playwright scenarios.
