# DutyLog v27.21.0 — Schedule Templates & Calendar Layers

## Purpose

The release separates reusable planning rules from dated calendar truth. A template describes a cycle; applying it writes ordinary dated shifts that remain manually editable. A calendar layer projects another person's repeating schedule in read-only form and never mutates the owner's days.

## Schedule templates

- Five built-in presets: 2/2, Day/Night/48, Five-day week, Day/72 and Night/72.
- User templates with 1–64 ordered shift steps.
- `CYCLE_START` alignment uses an anchor date; `WEEKDAY` requires exactly seven steps and always maps Monday to step 0.
- Preview classifies every date as `APPLY`, `OVERWRITE`, `SAME` or `SKIP_CONFLICT`.
- Occupied dates are skipped unless `overwriteExistingShift=true` is explicitly supplied.
- Applying a template writes standard `DayEntry` shifts and preserves the rest of each day's metadata.
- Built-in presets are immutable. Editing one in the UI creates a user-owned copy.

## Calendar layers

A layer stores only projection rules:

- name and color;
- source IANA timezone;
- schedule template and anchor date;
- start date and optional stop date;
- visibility and order.

Timed shifts are resolved in the layer timezone and projected into the user's display timezone. Untimed/off steps remain floating civil dates. The resulting occurrences are read-only and appear in month, week and hourly day views.

## API

- `GET|POST /api/v1/schedule-templates`
- `PATCH|DELETE /api/v1/schedule-templates/{id}`
- `POST /api/v1/schedule-templates/{id}/preview`
- `POST /api/v1/schedule-templates/{id}/apply`
- `GET|POST /api/v1/calendar-layers`
- `PATCH|DELETE /api/v1/calendar-layers/{id}`

The unversioned Web/PWA aliases remain available under `/api/schedule-templates` and `/api/calendar-layers`.

## Database

Flyway `V39__schedule_templates_and_calendar_layers.sql` creates:

- `schedule_templates`;
- `schedule_template_steps`;
- `calendar_layers`.

The migration is additive. Existing calendar data is not rewritten.

## Safety contracts

- Every resource is owner-scoped; foreign IDs return `NOT_FOUND`.
- Template application is limited by the existing calendar range validator.
- A template cannot be deleted while a layer references it.
- A custom shift type cannot be deleted while a schedule template references it.
- Companion occurrences are derived, read-only and never copied into `day_entries`.
- Visibility is stored on the server so all clients agree.
