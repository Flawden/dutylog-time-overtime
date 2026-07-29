# DutyLog v27.19.0 — Tasks & Inbox Next

## Product goal

Tasks now have two independent time concepts:

- **Planned interval** — when the work should appear on the calendar (`all day`, point task, exact start/end or duration, including overnight intervals).
- **Deadline** — the latest acceptable completion moment and the anchor for reminders.

The release also promotes Project to first-class task metadata and turns Inbox into a searchable capture tray without adding friction to quick entry.

## Domain and API

Flyway **V37** adds nullable project and canonical planned-interval fields to `day_tasks`. Existing rows remain all-day tasks.

Task create/update/read payloads add:

```json
{
  "project": "DutyLog",
  "allDay": false,
  "scheduledStartDate": "2026-07-29",
  "scheduledStartTime": "18:33",
  "scheduledEndDate": "2026-07-29",
  "scheduledEndTime": "19:18",
  "scheduledDurationMinutes": 45,
  "scheduleAbsolute": true,
  "scheduledSourceTimezone": "Europe/Moscow"
}
```

Timed plans are stored as instants plus source-zone provenance and are reprojected with the canonical work timezone. All-day tasks remain floating calendar dates.

Validation guarantees:

- timed tasks require a start;
- an end requires both date and time;
- end is after start;
- maximum interval is seven days;
- deadline is not earlier than the planned end (or start for point tasks);
- overnight tasks appear on every covered calendar day.

`GET /api/tasks/metadata` now includes `projects`. `GET /api/tasks/board` accepts an additive `project` filter.

## UI

- Read-first task details lead with the planned interval and show source-zone provenance only when projection differs.
- Editor supports All day, exact start/end, point tasks and 15/30/45/60/90/120-minute presets.
- Project has suggestions, chips and a board filter.
- The hourly day uses real planned duration instead of inventing a 45-minute block from the deadline.
- Overnight intervals are split across calendar days while preserving one task.
- Inbox has local search across queued and server-backed entries.
- Mobile editor uses a phone-safe bottom-sheet layout and 44px duration targets.

## Regression contract

- 97 Java test classes;
- 507 `@Test` methods;
- 28 Playwright scenarios;
- Flyway V1–V37.

`tasks-inbox-next.spec.js` covers interval creation, duration presets, project/deadline separation, read-first details, project filtering, Inbox search, hourly placement and mobile editor availability.
