# DutyLog v27.44.3 — People Profile Coverage Semantics Hotfix

## Bug

A selected companion profile previously reused `calendarScheduleFree(...)` for every calendar date. For an external profile `dayFactsForProfile(...)` has no personal absence data, so a date with no projected layer entry looked exactly like a real no-shift day. That is valid **inside** the configured companion schedule range, but wrong **outside** `startDate` / `endDate`, where the correct state is “schedule unknown”.

This became especially visible after Shared Availability: an uncovered date could be presented as all-day free together.

## Fix

- `CalendarLayer` now types the backend-provided `startDate` / `endDate` metadata.
- `profileDateCovered(...)` first trusts a real projected entry, then checks the declared range. This preserves timezone/display-edge rows.
- Month/week/day only render a day-off state when the date is covered.
- Shared Availability returns `PROFILE_OUTSIDE_COVERAGE` and no free/busy windows outside coverage.
- The selected-day card explains the missing schedule instead of claiming availability.
- Managed profile day editing is hidden outside layer bounds.

## Regression surface

- Vitest: **72** source cases.
- Java: **792 / 164**.
- Playwright: **48**.
- OpenAPI: **126 / 132**.
- Flyway: **V48**.
- Browser ceilings remain **855000 B raw / 250000 B gzip** pending exact Node 20 measurement.
