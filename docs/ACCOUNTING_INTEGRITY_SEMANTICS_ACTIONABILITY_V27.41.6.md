# v27.41.6 — Accounting Integrity Semantics & Actionability Hotfix

## Root cause

`LedgerIntegrityService.recordAbsenceTransition()` intentionally writes a zero-minute `POSTED` audit fact for approved/completed non-overtime absences such as unpaid leave. The previous `inspect()` rule treated any active audit on a non-overtime absence as `INACTIVE_ABSENCE_HAS_ACTIVE_AUDIT`, even though `COMPLETED` is itself a posted accounting state. That produced false warnings for valid historical/current unpaid absences.

## Semantic fix

- `INACTIVE_ABSENCE_HAS_ACTIVE_AUDIT` now means the workflow is actually inactive, independent of compensation source.
- Missing active-audit detection remains scoped to overtime-backed active absences so V44-era non-overtime history is not retroactively required to contain audit rows.
- When an active audit exists, its posting state is validated for every compensation source.
- Expected audit minutes are negative compensated minutes for overtime-bank absences and exactly zero for non-overtime accounting facts.
- A completed unpaid regression fixture proves a zero-minute `POSTED` audit is healthy.

## Actionability and mobile

The integrity UI now shows the linked audit entry state and a conservative next step. It never silently repairs historical accounting data. The record grid uses a specificity-safe responsive rule so mobile renders one integrity record per row instead of inheriting the desktop flex layout.

## Boundaries

No HTTP/OpenAPI shape, Flyway migration, auth/authorization, offline ownership, FIFO authority or dependency graph changes. OpenAPI remains 124/130 and Flyway remains V47.
