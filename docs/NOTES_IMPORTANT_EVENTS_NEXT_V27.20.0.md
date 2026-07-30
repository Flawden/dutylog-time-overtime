# Notes & Important Events Next — v27.20.0

## Notes

- Existing notes can be edited from the local IndexedDB snapshot while offline; edits are coalesced into the `updateNote` queue and synchronized on reconnect.
- Search is available through `GET /api/notes/search?q=&from=&to=&limit=` and matches title or Markdown content while remaining owner-scoped.
- Search results jump to the note's calendar day and activate the exact independent note.
- The day editor keeps the first note active, preserves pin/order/independent saves, fullscreen Markdown and export through `/api/export/notes`.

## Important events

The historical `important_days` resource remains source-compatible and now supports three explicit modes:

- `IMPORTANT_DATE` — floating all-day date;
- `EVENT` — all-day or timed event;
- `PERIOD` — all-day or timed multi-day interval.

Timed entities preserve source-local date/time, source IANA timezone and canonical instants. All-day entities remain floating civil dates when the user's timezone changes. Optional place, description, icon, category, color and per-event reminder offsets are stored with the entity.

Flyway V38 adds the new columns without rewriting historical rows. Existing rows remain `IMPORTANT_DATE`, all-day and floating.

## Presentation

- The Important screen is read-first: cards open details; editing is an explicit action.
- The editor handles type, interval, all-day/timed semantics, timezone, metadata, recurrence and reminder presets.
- Timed events appear inside the hourly calendar; floating events and periods appear in the all-day rail.
- The Today and week views open the same read-first details.
- Global quick capture opens the new editor with the selected calendar date and draft title preserved.
- The historical timezone browser scenario now targets the modal contract instead of removed inline fields.

## Regression baseline

- 97 Java test classes;
- 513 `@Test` methods;
- 29 Playwright scenarios;
- Flyway V1–V38.
