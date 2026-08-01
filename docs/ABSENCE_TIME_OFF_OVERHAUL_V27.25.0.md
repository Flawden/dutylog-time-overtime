# v27.25.0 — Absence & Time-Off Overhaul

## Product contract

DutyLog now treats a calendar day as two related facts:

1. the planned shift from the work schedule;
2. the factual status that explains what actually happened.

A vacation, time-off period or sickness never deletes or rewrites the planned shift. Full-day absences can visually replace it in Month/Week/Day views while the original shift remains available for work norms, salary calculation, statistics and later audit. Partial time off keeps the shift visible and adds an exact local interval.

## Balance policies

Absence types use one explicit policy:

- `VACATION_DAYS` — consumes the annual vacation allowance;
- `TIME_OFF_HOURS` — consumes the independent time-off minute bank;
- `NONE` — records the factual status without consuming either balance.

The built-in `TIME_OFF` preset uses `TIME_OFF_HOURS`. A full-day time-off period charges the planned shift net duration when one exists, otherwise the configured default time-off day duration. A partial period charges exactly `endTime - startTime`.

## Coverage

- `FULL_DAY` may span multiple dates, has no start/end time and can replace the visual shift surface.
- `PARTIAL` is restricted to one date, requires a valid start/end time and never removes the planned shift from the UI.
- Full-day periods conflict with any overlapping absence.
- Partial periods may coexist on one date when their intervals do not overlap.

Stable conflicts include:

- `ABSENCE_OVERLAP`;
- `VACATION_LIMIT_EXCEEDED`;
- `TIME_OFF_LIMIT_EXCEEDED`;
- `ABSENCE_TYPE_IN_USE`.

## Calendar presentation

- A full-day factual absence receives the visual weight of a shift rather than a secondary umbrella marker.
- The preserved planned shift is shown as context in the cell and day details.
- Partial time off is rendered as a separate interval bar while the planned shift remains visible.
- Week and Day modes use the same factual-priority contract.
- Monthly summaries include full-day counts and partial-hour totals per absence type.

## External calendar

Full-day absences remain all-day RFC 5545 events. Partial time off is exported as a timed event in the owner's canonical work timezone. The description may include the preserved planned shift; export remains read-only.

## Persistence

Flyway V42 additively extends:

- `vacation_settings` with an independent time-off balance and default full-day duration;
- `absence_types` with balance policy and full-day visual replacement behavior;
- `absence_periods` with coverage, start/end time and charged minutes.

No shift row is migrated, deleted or rewritten.

## Regression baseline

- 109 Java test classes;
- 579 `@Test` methods;
- 34 Playwright scenarios;
- Flyway V42.
