# DutyLog v27.13.0 — Temporal Consistency & Legacy Cleanup

## Goal

This release closes the remaining split-brain time paths after the absolute-interval and daily-projection work. Authoritative storage remains absolute instants and integer minutes; every calendar-day total is derived from the current canonical IANA timezone.

## Calendar and compatibility API

- Calendar month totals use projected overtime rows, never `day_entries.overtime_hours` or `time_off_hours`.
- A valid projected zero remains zero; stale legacy values can no longer reappear through a fallback.
- `/api/overtime/summary` and `/api/overtime/ledger` now use the same current-timezone projection as `/api/overtime/account`.
- Compatibility ledger dates are current local dates. Shift and note metadata are attached only when a `DayEntry` exists on that projected date.

## Canonical interval preview

- `POST /api/overtime/preview` and `/api/v1/overtime/preview` calculate elapsed, break, planned and credited minutes on the server.
- The browser no longer interprets `datetime-local` through the computer timezone.
- DST gaps are shifted forward by the transition gap and overlaps use the earlier offset, matching persisted credit creation.
- The create request remains authoritative even when a preview response is delayed or cancelled.

## Fixed-time quick scenarios

- `FIXED_TIME` scenarios support signed day offsets from `-2` through `+2`.
- Profile timezone changes preserve the timezone-change anchor moment and update both wall-clock time and relative day offset.
- Extreme-zone movement such as UTC+14 to UTC−11 is covered, including an A → B → A round trip.
- Legacy `endNextDay` remains in the wire and database contract as a compatibility alias.

## Deliberately floating values

Birthdays, important dates, notes, markers, date-only task deadlines, subtask dates, time-off request dates and daily digest wall-clock settings remain civil calendar values. They are not converted to instants because timezone movement must not change their intended local date.

## Database

Flyway V35 adds `quick_scenarios.end_day_offset`, backfilled from `end_next_day`. It does not rewrite overtime, task, shift or FIFO rows.

## Regression invariants

- Projected monthly totals equal the projected ledger rows in the same date range.
- A fully used projected day remains zero and cannot revive legacy hours.
- Summary, ledger, account, CSV and Excel share current-timezone credit slices.
- Browser preview and persisted calculation share one server-side DST policy.
- FIXED_TIME scenario projection supports two days before, previous, same, next and following day.
- Total earned, used and remaining minutes and FIFO provenance remain unchanged by timezone movement.

Current baseline: 88 Java test classes, 467 `@Test` methods and 21 Playwright scenarios. Flyway V1–V35.
