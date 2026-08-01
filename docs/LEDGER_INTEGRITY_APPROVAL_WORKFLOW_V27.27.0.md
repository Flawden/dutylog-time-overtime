# v27.27.0 — Ledger Integrity & Approval Workflow

## Purpose

This release turns the unified time ledger into a workflow-safe, auditable foundation for payroll. It does not calculate money. It makes the time snapshot trustworthy enough for the next release to consume.

## Workflow

Absences support `DRAFT`, `PLANNED`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED` and `COMPLETED`.

- `DRAFT`, `REJECTED` and `CANCELLED` do not consume balances and are not projected as factual calendar absences.
- `PLANNED` and `SUBMITTED` reserve overtime-bank minutes.
- `APPROVED` and `COMPLETED` post the linked usage.
- Editing or cancelling writes an explicit reversal before a new audit state is appended.

## Accounting periods

Each owner/month can be `OPEN` or `CLOSED`. Closed months reject ordinary absence, overtime, factual-work and planned-shift mutations with `PERIOD_CLOSED`. Direct day edits, bulk schedule fill, schedule-template apply and deletion of a shift type that is still assigned inside a closed month all use the same period guard. Notes and day markers remain editable because they do not change the payroll snapshot. A late correction is represented by an append-only `MANUAL_ADJUSTMENT` instead of rewriting history silently.

## Integrity

`GET /api/v1/ledger-integrity` reconciles:

- one absence-owned usage per active `OVERTIME_BANK` absence;
- expected reservation/posting state;
- absence minutes versus requested usage minutes;
- FIFO allocation totals versus usage totals;
- orphan linked usages;
- duplicate V43 opening credits;
- append-only audit and accounting-period state.

## Actual work

Explicit factual intervals are optional. Without them, DutyLog keeps the simple plan-as-fact rule. When intervals exist, they become the factual source for that date, including overnight work up to 48 hours.

## Migration

Flyway V44 is additive. It expands absence statuses, adds usage posting state, audit entries, accounting periods and factual intervals. It does not modify `day_entries` and has no reverse SQL.
