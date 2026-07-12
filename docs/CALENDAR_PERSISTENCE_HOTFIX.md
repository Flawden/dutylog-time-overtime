# Calendar persistence hotfix

Status: v27.2.3.

## Symptom

After filling a month with a schedule, navigating to another month and back (or reloading in another browser) could show only previously existing day rows while the newly generated schedule disappeared.

## Fix

- `DayEntryService.fillSchedule` now persists the complete generated batch with `saveAllAndFlush` before creating the response DTOs.
- The web client reloads the active month after a successful bulk fill, so the displayed state is confirmed by the server and the offline snapshot is refreshed.
- Month loading uses a generation guard; a late response for an old month is ignored instead of being applied to the currently visible grid.

## Regression coverage

- A 31-day schedule survives `EntityManager.clear()`, which simulates a new HTTP request/F5/another browser.
- `overwriteExistingShift=false` preserves an existing shift.
- A static browser contract test protects server reload after fill and stale-response rejection.
