# DutyLog v27.8.0 — Zoned Work Intervals

## Product goal

Time Foundation introduced explicit work and display IANA timezones. v27.8.0 makes that architecture visible in the two places where a local clock actually represents a real interval: dated shifts and newly calculated overtime credits.

The central promise is:

```text
Work schedule:  08:30–17:00 Asia/Yekaterinburg
Display zone:   Europe/Moscow
UI projection:  06:30–15:00 Europe/Moscow
```

The stored work meaning is not rewritten when the display timezone changes. Both projections refer to the same absolute start/end instants.

## Time ownership rules

### Work timezone owns semantics

The work timezone determines:

- which local date owns a shift;
- how a shift type's local start/end clock is resolved;
- where an overnight interval crosses work midnight;
- how new calculated overtime inputs are interpreted;
- actual elapsed duration across daylight-saving transitions.

### Display timezone owns presentation

The display timezone only projects an already resolved instant. It can change:

- the visible clock time;
- the visible local date when an interval crosses a timezone boundary;
- whether an interval appears to cross display midnight.

It cannot change:

- the source work date;
- the shift type or schedule pattern;
- the stored absolute identity;
- existing floating dates such as tasks, notes, birthdays or important dates.

## Dated shift API contract

`DayDto` appends nullable `shiftInterval`. The existing constructor remains source-compatible.

`shiftInterval` contains:

```text
startInstant / endInstant
workStart / workEnd
displayStart / displayEnd
workTimezone / displayTimezone
breakMinutes
elapsedMinutes / netMinutes
crossesWorkMidnight / crossesDisplayMidnight
sameTimezone
```

A day without a shift, or a shift type without both start/end times, returns `shiftInterval = null`.

The absolute interval is derived from the persisted day date and shift-type clock values on every read. Changing only the display timezone therefore requires no database rewrite.

v27.8.0 still treats the account work timezone as the owner of every dated shift; it does not persist a separate source timezone per historical day. Changing the work timezone intentionally reinterprets dated shifts, while travel and temporary viewing should use the display timezone.

## Shift interface

- Calendar cells keep the shift name and expose a display-zone clock on desktop when zones differ.
- The selected-day panel shows the display projection first and the original work projection below it.
- Identical work/display zones collapse to one row.
- Saving timezone settings reloads the active month so projections update immediately.
- Mobile layouts keep the full selected-day projection but suppress the extra compact clock inside small calendar cells.

## Absolute overtime identity

Flyway V30 adds nullable fields to `overtime_credits`:

```text
start_at_instant TIMESTAMPTZ
end_at_instant   TIMESTAMPTZ
source_timezone  VARCHAR(80)
```

The three values form one identity and must be either all present or all absent.

For new calculated credits:

1. the entered local start/end values are resolved in the current work timezone;
2. elapsed minutes are measured between absolute instants;
3. break and planned minutes are deducted;
4. split rows retain their own absolute boundaries and the original source zone;
5. overlap protection compares absolute intervals.

This makes spring-forward and fall-back intervals correct even when wall-clock subtraction differs by one hour.

## Editing and timezone stability

A calculated credit owns its persisted `sourceTimezone`.

- Opening and saving an unchanged credit after changing the account work timezone must not move its instants.
- Editing the interval continues interpreting its wall-clock values in that original source timezone.
- A legacy credit without source timezone remains legacy-local when only metadata is changed.
- An explicit interval edit of a legacy row may establish a new absolute identity using the current work timezone.

## Legacy boundary

V30 deliberately does not backfill historical calculated credits. Their original timezone was never stored, so converting them through the user's current timezone could silently shift real history.

Legacy rows therefore:

- keep `start_at_instant`, `end_at_instant` and `source_timezone` null;
- continue showing their stored local range;
- use the previous local overlap fallback;
- are not automatically moved by display timezone changes.

New absolute and legacy-local rows can coexist safely.

## Overtime interface

When absolute identity exists, the ledger displays:

- the range projected into `displayTimezone` as the primary value;
- the original work-local range and `sourceTimezone` as secondary context when zones differ.

Legacy rows keep their existing presentation.

## DST policy

The deterministic Time Foundation rule remains unchanged:

- nonexistent local time is shifted forward through the gap;
- ambiguous local time uses the earlier valid offset.

Durations are then measured between the resulting instants. For example, `00:00–08:00 Europe/Berlin` on the 2026 spring transition represents seven actual hours.

## Database migration

`V30__zoned_work_intervals.sql`:

- adds the three nullable absolute-identity fields;
- adds a partial owner/interval index;
- adds a pair constraint requiring a complete identity;
- performs no historical UPDATE/backfill.

Flyway sequence is continuous from V1 through V30.

## Offline boundary

The month payload now contains calculated shift projections, but local/offline writes continue sending the existing shift/day identifiers. Projections are refreshed from the server after synchronization or timezone changes.

No conflict-resolution or operation-log schema is changed in this release.

## Non-goals

v27.8.0 does not implement:

- exact FIFO interval slices and provenance;
- automatic overtime creation from a shift's actual departure time;
- mass conversion of historical overtime;
- task deadlines as absolute instants;
- external calendar synchronization;
- recurring-event timezone rules;
- changing the source timezone of an existing interval through the UI;
- persisting a separate historical source timezone on every dated shift.

Those are intentionally separated from this release. The next major overtime step is the full Overtime Interval Engine.

## Automated acceptance

- `WorkIntervalServiceTest` proves work/display projection and DST elapsed duration.
- `DayEntryServiceTest` proves dated shifts expose exact work/display values.
- `OvertimeServiceTest` proves absolute persistence, display projection, DST duration and source-zone stability during edits.
- `PostgreSqlMigrationContractTest` proves V30 avoids guessed legacy backfill.
- `ZonedWorkIntervalsFrontendContractTest` protects the UI boundary.
- Playwright verifies `08:30 Asia/Yekaterinburg` becomes `06:30 Europe/Moscow` after selecting the dated shift.

## Manual staging acceptance

1. Set work timezone to `Asia/Yekaterinburg` and display timezone to `Europe/Moscow`.
2. Assign the built-in day shift `08:30–17:00` to a date.
3. Confirm the selected-day panel shows `06:30–15:00 Europe/Moscow` and `08:30–17:00 Asia/Yekaterinburg`.
4. Change only display timezone and confirm the work projection and calendar date do not change.
5. Create a new calculated overtime interval in the work zone.
6. Confirm the ledger shows display range first and source range below it.
7. Reload and verify both projections remain stable.
8. Confirm historical overtime rows without absolute identity remain unchanged.
9. Verify FIFO balances and allocations are unchanged.
10. Check desktop and 320–430 px layouts for overflow.
