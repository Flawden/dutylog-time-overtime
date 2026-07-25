# DutyLog v27.9.0 — Overtime Interval Engine

## Product goal

DutyLog no longer answers only “how many hours were used”. It can explain **which exact source minutes were consumed**, in FIFO order, and can restore the same history deterministically when a time-off entry is edited or deleted.

## Canonical timezone

v27.9.0 exposes one user-facing IANA timezone. The historical `workTimezone` and `displayTimezone` fields remain in the API/database for compatibility, but are persisted and returned with the same value.

- Floating dates remain dates and never shift.
- Absolute overtime instants are rendered in the current canonical timezone.
- Changing the timezone reprojects existing absolute overtime without rewriting its UTC identity.
- The timezone in which a source interval was originally entered remains stored as `sourceTimezone`.

## Integer-minute authority

The ledger keeps backward-compatible decimal `hours`, but FIFO authority is now integer minutes:

- `overtime_credits.credited_minutes`;
- `overtime_usages.requested_minutes`;
- `overtime_allocations.allocated_minutes`.

This prevents floating-point drift and supports 15, 30, 45 and other partial-hour values deterministically.

## Exact FIFO provenance

Every exact allocation may contain:

- source credit;
- usage/time-off entry;
- allocated minutes;
- exact `startAtInstant` and `endAtInstant`;
- original `sourceTimezone`;
- `reconstructed` marker.

Example:

```text
Time off: 8 h

24.07 17:00–24:00 — 7 h
25.07 00:00–01:00 — 1 h
```

The browser splits a cross-midnight allocation into readable calendar-day rows while the database keeps one continuous absolute interval.

## Deterministic rebuild

Create, update and delete of a time-off entry rebuild the owner’s FIFO ledger in stable order:

```text
credit work date → credited start instant → credit id
usage date → usage id
```

Deleting an earlier time-off entry returns the same source minutes and shifts later usages back to the earliest available intervals.

## Legacy migration wizard

Historical rows without trustworthy instants are never guessed automatically.

The wizard allows the user to:

1. choose an explicit source IANA timezone;
2. preview every local interval and its projected credited interval;
3. select only migratable rows;
4. confirm conversion;
5. rebuild all existing allocations from the reconstructed sources.

Hours and account balance do not change. Migrated allocations are labelled `reconstructed` so DutyLog never pretends historical provenance was originally recorded.

Rows without precise local `startAt`/`endAt` remain blocked and continue to work as quantity-only legacy credits.

## Shift duration UX

The selected-day shift card explains net work and break separately:

```text
Рабочее время смены: 8 ч
Обед в смене: 30 мин
```

This replaces the ambiguous “actual duration 8 h 30 min” label.

## Database

Flyway V31 adds exact minute and interval fields, backfills minute totals, reconstructs allocations only where v27.8 already stored a trustworthy absolute source, and adds pair integrity constraints and indexes.

## API

New endpoints, available through legacy and `/api/v1` aliases:

```text
POST /api/overtime/legacy-credits/preview
POST /api/overtime/legacy-credits/migrate
```

Overtime account DTOs now expose minutes, exact source intervals, display projections, source timezone and reconstruction state.

## Security and ownership

- migration candidates are always selected from the authenticated owner’s credits;
- supplied foreign IDs are ignored rather than exposed;
- module guards, authentication and CSRF rules remain unchanged;
- no endpoint accepts arbitrary credit data from another account.

## Non-goals

v27.9.0 does not:

- invent instants for manual quantity-only credits;
- create a full HR approval workflow;
- generate legal documents automatically;
- attach a historical timezone to every old calendar shift;
- introduce recursive calendars or external sync.

## Acceptance checklist

- Exact allocation ranges are visible from both the time-off and source-credit sides.
- Cross-midnight ranges are split into readable day segments.
- Minute totals remain exact after repeated rebuilds.
- Deleting a usage restores source minutes and later FIFO order.
- Source credits keep the established `workDate → id` FIFO order; partial timezone migration never reorders same-day history.
- Changing the canonical timezone reprojects existing exact overtime.
- Legacy preview changes nothing until confirmation.
- Migrated rows keep the same hours and balance.
- Unmigratable rows are explained and remain usable.
- Shift card shows net work and break separately.

- Migration safety: preview may list all legacy rows, but persistence rejects an empty selection.
