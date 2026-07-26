# DutyLog v27.11.0 — Shift Occurrences & Calendar Projection

## Goal

A concrete dated shift is a real interval, not a template that can be reinterpreted every time the user changes timezone.

Example:

```text
Source occurrence: 03.07.2026 08:30–17:00 Asia/Yekaterinburg
Absolute identity: 03:30Z–12:00Z
Current projection: 03.07.2026 06:30–15:00 Europe/Kyiv
```

Each dated row becomes an immutable absolute occurrence. The `ShiftType` remains a reusable wall-clock template. `DayEntry` stores the immutable occurrence snapshot created when that template is assigned to a date.

## Persisted occurrence snapshot

Flyway V33 adds to `day_entries`:

- `shift_start_instant` / `shift_end_instant`;
- `shift_source_timezone`;
- source date/start/end wall-clock snapshot;
- break and net-minute snapshot.

Changing the user timezone never rewrites these values. Editing a shift template never rewrites historical occurrences.

## Calendar projection

`CalendarRangeDto.shiftOccurrences` contains every absolute occurrence intersecting the requested display range, including occurrences whose source date is outside that range.

The frontend projects and segments each occurrence by display-local calendar date:

```text
03 July 23:00–04 July 07:00 Europe/Kyiv
→ 04 July 01:00–09:00 Asia/Yekaterinburg
```

An occurrence crossing display midnight can appear as multiple visual segments, while remaining one database row.

Untimed shift types such as `Выходной` remain floating calendar-day markers.

## Legacy shifts

Existing dated shifts from earlier releases do not have a trustworthy source timezone.

Two safe paths exist:

1. before the user changes the canonical timezone, DutyLog automatically freezes every legacy shift in the old timezone;
2. the migration modal allows explicit source-zone preview and selected-row migration.

Saving an unrelated note or emoji never silently guesses a legacy shift timezone.

## Task Details and PWA cache

The v27.10.0 read-first task details flow remains unchanged. This release hardens Service Worker activation:

- old shell caches are deleted inside the activation lifetime;
- the new worker claims clients after cleanup;
- the page asks for an update and reloads once on controller change;
- clicking a task body still opens details; only the explicit Edit action opens the editor.

## Non-goals

- multiple independent work calendars;
- per-shift manual timezone selection during ordinary creation;
- rewriting historical occurrences when a template changes;
- changing floating tasks, notes or important dates when timezone changes.
