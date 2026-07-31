# v27.26.0 — Unified Time & Compensation Ledger

## Goal

DutyLog now treats schedule, factual absence and compensation as one reversible time-accounting flow:

```text
planned shift / day off
        ↓
factual work or absence
        ↓
coverage source and ledger movement
        ↓
future payroll projection
```

The release does **not** calculate salary. It creates the authoritative minute-based read model that Payroll Foundation will consume without reinterpreting calendar events.

## Canonical overtime bank

`overtime_credits`, `overtime_usages` and `overtime_allocations` remain the only authority for compensatory time.

A time-off absence covered by `OVERTIME_BANK` creates one source-linked usage:

- `source_kind = ABSENCE`;
- `source_absence_id = absence_periods.id`;
- FIFO allocations use the oldest available credit first;
- editing the absence updates and reallocates the same usage;
- deleting the absence deletes the linked usage and restores the balance;
- the linked usage cannot be edited or deleted from the manual overtime ledger.

One earned minute therefore cannot be silently spent twice or paid and used as time off without an explicit future settlement operation.

## Absence compensation policies

Each absence stores one explicit policy:

| Policy | Meaning |
| --- | --- |
| `VACATION_ALLOWANCE` | consumes the vacation-day allowance |
| `OVERTIME_BANK` | consumes FIFO overtime minutes |
| `SICK_PAY` | marks sick-pay treatment for Payroll Foundation |
| `UNPAID` | marks unpaid planned minutes |
| `NONE` | informational absence without a separate coverage source |

Built-in Vacation, Time Off, Sick and Unpaid types keep fixed semantics. Custom types may use overtime, sick, unpaid or no coverage according to their configured purpose.

## Unified monthly read model

Authenticated endpoint:

```http
GET /api/time-compensation?from=2026-07-01&to=2026-07-31
GET /api/v1/time-compensation?from=2026-07-01&to=2026-07-31
```

The response joins:

- planned minutes from shift occurrences;
- factual absence minutes;
- earned overtime;
- manual and absence-linked usage;
- vacation days;
- sick minutes;
- unpaid minutes;
- compensated minutes;
- per-day fact and compensation labels.

The endpoint is owner-scoped, requires the Overtime module, is limited by the canonical date-range validator and returns `Cache-Control: no-store`.

## V43 migration

`V43__unified_time_compensation_ledger.sql`:

1. adds compensation metadata to `absence_periods`;
2. adds source ownership to `overtime_usages`;
3. converts the legacy V42 standalone time-off balance into the oldest FIFO opening credit with a deterministic UTC accounting interval;
4. creates source-linked usages for existing V42 time-off absences;
5. resets the deprecated standalone settings balance to zero;
6. leaves `day_entries` and all planned shifts untouched.

Allocation rows for migrated linked usages are repaired transactionally on the first canonical overtime-account read. Read paths that can perform this one-time repair use writable transactions.

The deprecated `timeOffBalanceHours` request field remains wire-compatible but no longer mutates a second balance. New clients use the Overtime ledger.

## Frontend

The Overtime workspace now includes a monthly Plan → Fact → Compensation card. Selecting a meaningful day opens the canonical calendar day panel.

Vacation Planner exposes a coverage source for the absence draft. Linked overtime usages are visibly marked as managed by the absence and do not expose manual edit/delete buttons.

## Safety invariants

- planned shifts are never deleted by an absence;
- one absence owns at most one linked overtime usage;
- one linked usage belongs to exactly one absence;
- linked usage minutes equal the absence compensated minutes;
- all overtime allocations remain FIFO and minute-authoritative;
- deleting an absence restores consumed overtime;
- manual ledger actions cannot mutate an absence-owned usage;
- unpaid and sick absences do not consume overtime;
- salary values are not guessed in this release.

## Rollback note

V43 is forward-only. Do not drop columns, delete migration history or run reverse SQL.

An emergency application rollback to v27.25.x is possible only as a short operational measure: the forward schema remains compatible, but the old UI does not understand linked usage ownership and the migrated standalone time-off setting has been reset. Prefer deploying a corrected v27.26.x image rather than continuing absence/overtime edits on v27.25.x after V43 has run.

## Regression baseline

- 110 Java test classes;
- 590 `@Test` methods;
- 35 Playwright scenarios;
- Flyway V43.

Next product stage: `v27.27.0 — Payroll Foundation`.
