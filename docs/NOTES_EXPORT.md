# Notes export

Status: introduced in v27.0-rc4; current in v27.1.0.

DutyLog can export all non-empty day notes for the authenticated user as a ZIP archive suitable for Obsidian, backup or AI-assisted analysis.

## Use

Open Profile and press **Download all notes (.zip)** / **Скачать все заметки (.zip)**.

Endpoint:

```http
GET /api/export/notes
```

The archive contains:

```text
2025/2025-12-31.md
2026/2026-07-03.md
README.md
```

Each note receives YAML front matter with its date and, when available, shift name and emoji. The note body is preserved as Markdown.

## Security and limits

- Authentication is required.
- Rows are selected by owner in the database.
- The response is marked `no-store`.
- ZIP entry paths are generated from dates, not user input.
- YAML strings are escaped.
- Blank notes are skipped.
- Export count and uncompressed-byte limits are checked before streaming.

Production limits:

```env
DUTYLOG_EXPORT_NOTES_MAX_COUNT=10000
DUTYLOG_EXPORT_NOTES_MAX_UNCOMPRESSED_BYTES=52428800
```

Export is intentionally still available when the Notes module is switched off. A module switch hides features; it does not delete data or remove data portability.
