# v27.42.0 — People Profiles

## Product contract
Calendar has one active person context at a time: `Я` or one configured read-only People Profile. Profiles never overlay complete schedules in the same day cell.

## Owner context
`Я` retains canonical DutyLog data: shifts, tasks, absences, important dates, notes, overtime and day editing.

## People Profile context
A profile projects only its repeating schedule for month/week/day. Owner-only facts and edit actions are hidden. The selected profile is persisted locally and falls back to `Я` if unavailable.

## Compatibility boundary
The existing `calendar_layers` table and `/api/v1/calendar-layers` generated-client contract remain the persistence/transport boundary in v27.42.0. The `visible` flag controls whether a profile appears in the calendar switcher. No Flyway or OpenAPI shape change is introduced.

## Out of scope
Shared Availability / common free windows are a later release and must not reintroduce full-calendar overlays.
