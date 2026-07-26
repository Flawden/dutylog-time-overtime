# DutyLog v27.12.1 — Midnight Projection Contract Hotfix

## Failure reproduced

The full Maven run reached 460 tests and failed only in
`OvertimeServiceTest.ровныеСуткиДелятсяПополамМеждуДвумяДатами`:

```text
expected: <2> but was: <3>
```

The application did not lose or invent minutes. The regression test was still
counting persisted source-credit rows, while v27.12 returns current-timezone
daily projection slices.

## Why three projected rows are valid

An exact 24-hour entry `03.07 08:00 → 04.07 08:00` historically persists as two
immutable 12-hour source credits:

```text
source A: 03.07 08:00 → 03.07 20:00 = 12 h
source B: 03.07 20:00 → 04.07 08:00 = 12 h
```

The v27.12 display layer then splits source B at the current-zone midnight:

```text
03.07 08:00 → 20:00 = 12 h
03.07 20:00 → 24:00 = 4 h
04.07 00:00 → 08:00 = 8 h
```

Therefore the user-facing civil-day totals are:

```text
03.07 = 16 h
04.07 = 8 h
```

All 1440 minutes, source IDs, allocation IDs, FIFO order and account balance
remain unchanged.

## Fix

- Replaced the stale projected-row-count assertion with explicit source-storage
  and civil-day projection invariants.
- The test now proves two persisted 12-hour source credits, projected daily
  totals of `16/8`, and an unchanged 24-hour balance.
- Clarified API documentation and the form hint for exact 24-hour intervals.
- No production schema change and no Flyway migration; V34 remains current.
