# DutyLog v27.7.0 — Time Foundation

## Product goal

Time Foundation separates calendar meaning from absolute time so future shifts, overtime intervals, reminders, calendar zoom and external sync can share one predictable model.

The release intentionally adds very little visible product complexity. Its purpose is to make time safe before Overtime 2.0 starts storing and consuming real intervals.

## Time taxonomy

DutyLog now treats time values according to their meaning instead of formatting every date-like value the same way.

### Floating local date

A floating date belongs to a calendar and must not move when the display timezone changes.

Examples:

- birthday;
- important date;
- note date;
- task without a precise absolute moment;
- vacation or time-off day.

These values remain `LocalDate` and are evaluated in the user's work calendar where a current day is required.

### Work-local wall clock

A work-local date and time describe what was entered on the user's work clock, for example a shift scheduled for `08:30 Europe/Chisinau`.

The local value is not an absolute moment until DutyLog resolves it with the persisted IANA work timezone.

### Absolute instant

An absolute instant is one point on the global timeline. Reminders, delivery deduplication, synchronization timestamps and future overtime allocations use `Instant`/`TIMESTAMPTZ`.

One instant can be projected into both user zones without changing the stored value.

### Duration and interval

A duration is measured between two absolute instants. It is not calculated only by subtracting wall-clock labels because daylight-saving transitions can remove or repeat an hour.

`WorkIntervalService` is the first shared resolver for this rule and is the integration boundary for Overtime 2.0.

## Two explicit IANA timezones

Every user now has two persisted IANA timezone identifiers.

### Work timezone

`workTimezone` owns:

- calendar today;
- task overdue rules;
- shift wall-clock interpretation;
- reminder calculation;
- future overtime and FIFO calculations;
- future legally meaningful work interval presentation.

### Display timezone

`displayTimezone` only projects absolute instants for the interface.

It is used for:

- synchronization timestamps;
- mobile-session activity;
- reminder display projections;
- future external-calendar timestamps.

Changing the display timezone does not rewrite dates, move birthdays, alter shifts or recalculate overtime.

Existing users are migrated with `display_timezone = work_timezone`, so v27.7.0 preserves the exact visual behaviour they had before choosing a separate display zone.
Legacy clients that only send `workTimezone` remain coupled while both saved zones are equal. Once the user explicitly chooses a different display timezone, later work-timezone updates no longer overwrite that independent preference.

## Central time boundary

`UserTimeService` is the single backend boundary for:

- resolving work and display `ZoneId` values;
- reading the current `Instant`;
- projecting one instant into either timezone;
- deriving work-local and display-local dates/times;
- converting a work-local wall clock into an absolute instant;
- deterministic daylight-saving handling.

Legacy `zone()`, `today()` and `now()` methods remain source-compatible and explicitly mean work time.

## Daylight-saving policy

IANA rules can make a local wall-clock value nonexistent or ambiguous.

DutyLog applies one deterministic policy:

- **gap / nonexistent time:** shift forward by the transition duration;
- **overlap / ambiguous time:** use the earlier valid offset.

Example for `Europe/Berlin` in 2026:

- nonexistent `2026-03-29 02:30` resolves to the first matching instant after the one-hour gap;
- ambiguous `2026-10-25 02:30` uses the earlier summer offset.

This policy is covered by unit tests and must remain stable unless a future product decision introduces explicit user choice.

## API contract

New authenticated read-only endpoints:

```text
GET /api/time/context
GET /api/v1/time/context
```

They return:

- `nowInstant`;
- `workTimezone` and `displayTimezone`;
- work/display local date-time projections;
- work/display dates;
- UTC offsets;
- `sameTimezone`.

The mobile user DTO also exposes both timezone identifiers.

Reminder DTOs keep the existing work-local `remindAt` field for compatibility and additionally expose:

- `remindAtInstant`;
- `workTimezone`;
- `displayAt`;
- `displayTimezone`.

Clients should use `remindAtInstant` as the durable identity and the display projection for presentation.

## Database migration V29

`V29__time_foundation.sql`:

1. adds non-null `users.display_timezone` and backfills it from `work_timezone`;
2. adds nullable `telegram_notification_deliveries.remind_at_instant TIMESTAMPTZ`;
3. deliberately does **not** reinterpret existing local delivery timestamps because the original timezone was never stored and the account timezone may have changed;
5. changes new Telegram delivery identity to `(user_id, reminder_id, remind_at_instant)`;
6. adds an absolute-time index and a partial legacy-local index for rows without an instant.

The original local `remind_at` is retained for diagnostics and compatibility. Runtime deduplication checks absolute identity first and only uses the legacy local key for rows whose `remind_at_instant` is null.

## Notification and task behaviour

- Reminder filtering and ordering compare absolute instants.
- Telegram scans use one UTC clock window and project its boundaries into each user's work timezone only to select candidate calendar dates.
- New Telegram delivery identities survive changes to work or display timezone; legacy rows remain conservatively deduplicated by their original local key.
- Task overdue calculations use the user's work-local current date and time instead of the server operating-system timezone.
- Browser timestamps that represent real instants are formatted through the configured display timezone.

## Work interval resolver

`WorkIntervalService` resolves a work date, start time, end time and break into:

- work-local start/end;
- absolute start/end instants;
- elapsed minutes;
- break minutes;
- net minutes;
- overnight flag;
- source work timezone.

An end time that is not after the start time belongs to the next calendar day. Equal start/end therefore represents a full 24-hour interval.

Elapsed minutes are measured between instants, so an eight-hour wall-clock range across a DST transition can represent seven or nine actual hours.

## Frontend settings

The time settings panel now contains separate selectors for work and display timezones, plus a shortcut to make display equal to work.

The live preview shows both clocks and offsets. The browser-zone detection action fills both selectors but does not save until the user confirms.

The calendar continues to derive today from `workTimezone`. Absolute UI timestamps use `displayTimezone` through the shared `formatAbsoluteInstant()` helper.

## Offline boundary

This release does not rewrite the offline operation model. Floating dates remain floating and existing local queues keep their current payloads.

Future operations that contain an absolute moment must carry an ISO-8601 instant and, when wall-clock reconstruction matters, the original IANA timezone. Conflict resolution and cross-device merge remain part of the planned Offline Engine.

## Acceptance checklist

1. Existing users receive a display timezone equal to their previous work timezone.
2. Work and display zones can be saved independently and survive reload.
3. Legacy work-only updates keep both zones coupled until display timezone is chosen explicitly.
4. Changing display zone does not change calendar today or any stored floating date.
5. `/api/time/context` and `/api/v1/time/context` expose one instant with two projections.
6. Invalid timezone identifiers are rejected by the profile API.
7. Task overdue state follows work timezone, not server timezone.
8. Reminder ordering and past filtering use absolute instants.
9. New Telegram deliveries deduplicate by `TIMESTAMPTZ`; legacy rows with no trustworthy instant continue to deduplicate by their original local key.
10. DST gaps and overlaps follow the documented deterministic policy.
11. Overnight and DST-crossing work intervals produce correct elapsed minutes.
12. Synchronization and session timestamps with an explicit instant render in display timezone; legacy Inbox timestamps without an offset remain unchanged.
13. Existing birthdays, important dates, notes, tasks, shifts and overtime records are not mass-converted.

## Non-goals

v27.7.0 does not include:

- Overtime Interval Engine or interval FIFO;
- migration of every historical local field to an instant;
- editing a reminder in display timezone;
- per-event timezone selection;
- flight/travel itinerary logic;
- Google Calendar, Outlook or CalDAV synchronization;
- recurring-event timezone semantics;
- full offline conflict resolution;
- a week/day timeline redesign.

These features can now build on one explicit time contract instead of inventing their own conversion rules.
