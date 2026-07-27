# DutyLog v27.14.0 — Multiple Daily Notes

## Goal

A calendar date is no longer limited to one mutable text field. Users can keep several independent Markdown notes on the same day without one edit overwriting another.

## Storage model

Flyway V36 creates `day_notes` with owner/date scoping, optional title, Markdown content, pinned state, stable ordering, optimistic row version and timestamps. Every non-empty legacy `day_entries.note` is migrated once as the first independent note. The old column remains a compatibility shadow for older web/mobile clients.

The compatibility bridge changes only the current primary note. It never deletes or overwrites sibling notes.

## API

Both legacy and v1 aliases expose:

- `GET /api/notes?date=yyyy-MM-dd`
- `GET /api/v1/notes?from=yyyy-MM-dd&to=yyyy-MM-dd`
- `POST /api/notes`
- `PATCH /api/notes/{id}`
- `POST /api/notes/{id}/move`
- `DELETE /api/notes/{id}`

All operations are owner-scoped and guarded by the Notes module.

## UI

The day panel contains a note list and one active editor. Users can:

- create several notes for one date;
- edit title and Markdown content independently;
- pin or unpin a note;
- move a note inside its pinned or regular group;
- delete one note without touching the others;
- see the note count on the calendar marker;
- open the active note in the full-screen editor.

Title and content changes inside one debounce window are merged into one PATCH, preventing the later input event from dropping the earlier field.

## Offline boundary

Loaded notes remain visible in the IndexedDB calendar snapshot while offline. Creation, editing, pinning, reordering and deletion are intentionally read-only until a server connection exists; no unsupported offline mutation is pretended to be durable.

## Export and compatibility

ZIP export now writes one Markdown file per independent note with note id, date, title, pinned state and order in frontmatter. Mobile/day APIs still expose `Day.note` as the primary-note shadow while `Day.notes` carries the complete collection.

## Regression coverage

- service tests for independent CRUD, ordering, pinning, owner isolation and legacy shadow behavior;
- MockMvc coverage for aliases, validation, module guards, CSRF and ownership;
- frontend contract coverage for dedicated note endpoints, merged debounce patches and offline read-only behavior;
- Playwright coverage for two notes on one day through pin, reorder, reload and deletion;
- PWA coverage for offline snapshot reading.

Flyway: V1–V36.
