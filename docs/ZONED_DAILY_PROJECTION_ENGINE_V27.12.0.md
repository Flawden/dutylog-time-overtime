# DutyLog v27.12.0 — Zoned Daily Projection Engine

## Purpose

Overtime credits and FIFO allocations already have immutable absolute intervals. v27.12.0 adds the missing view layer: those intervals are now split at midnight in the user's current canonical IANA timezone.

The database is not rewritten when the user changes timezone. Only the calendar-day projection changes.

## Example

Persisted source interval:

```text
03.07 22:00 → 04.07 02:00 Europe/Moscow
absolute: 03.07 19:00Z → 03.07 23:00Z
```

Projection in Europe/Moscow:

```text
03.07 +2 h
04.07 +2 h
```

Projection in Asia/Tbilisi:

```text
03.07 +1 h
04.07 +3 h
```

Projection in Asia/Yekaterinburg:

```text
04.07 +4 h
```

The total credit remains four hours in every zone.

## FIFO integrity

Allocations remain attached to the same credit IDs and absolute source minutes. Each allocation is independently split by current-zone midnight for display. Therefore:

- changing timezone never rebuilds FIFO;
- total used minutes remain unchanged;
- deleting a time-off request still restores the same absolute minutes;
- a projected row can show only the part used on that local day;
- edit/delete protection uses the full persisted credit, not one projected slice.

## API

`OvertimeCreditRowDto.workedDate`, `hours`, `usedHours`, `remainingHours`, `displayStart` and `displayEnd` now describe one current-timezone daily slice for exact credits.

The additive `projection` object exposes:

- original source date and range;
- slice index/count inside the persisted credit;
- row index/count on the current local day;
- day earned/used/remaining totals;
- full persisted credit earned/used/remaining totals;
- whether the row has exact absolute provenance.

Legacy quantity-only credits remain one floating row because DutyLog cannot invent a missing source instant.

## UI

The ledger shows:

- projected local date and exact local interval;
- a projection part badge when one credit crosses local midnight;
- one daily subtotal per current-zone date;
- source-zone date/range as secondary context;
- edit/delete actions guarded by full source-credit usage.

The selected-day panel and calendar totals use the same projected rows.

## Regression scenarios

1. `22:00–02:00` in UTC+3 projects as `2/2`.
2. The same absolute interval in UTC+4 projects as `1/3`.
3. In UTC+5 it projects as `0/4` and the empty first day disappears.
4. Reverse timezone movement restores the earlier split.
5. Partial FIFO allocation is split by the same current-zone boundaries.
6. Total earned, used and balance values never change because of timezone projection.
7. Date filters and exports operate on projected local dates.
8. Legacy rows without exact provenance remain unchanged.
