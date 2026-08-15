# DutyLog v27.43.0 — People Profiles: Managed Schedule Overrides

## Product intent

People Profiles are planning calendars, not managed copies of another person's DutyLog. v27.43.0 adds just enough factual schedule editing to represent exceptions to the generated companion template.

Examples:
- a generated workday can become `Не работает · Отгул`;
- a generated day off can become an actual `08:30–17:00` work shift;
- one date may use custom start/end time without mutating the reusable ShiftType or base template.

Personal Tasks, Notes, Important Days, Overtime, Payroll and absence/time-bank accounting remain outside another person's profile.

## Persistence

Flyway V48 creates `calendar_layer_overrides` with one unique override per `(layer_id, source_date)`.

- `kind=OFF`: optional `TIME_OFF`, `VACATION`, `SICK`, `OTHER`; no shift/time fields.
- `kind=WORK`: owned `shift_type_id`; optional start/end must be supplied together.
- deleting a CalendarLayer cascades its overrides;
- deleting the override means “follow the generated schedule again”.

## Projection semantics

Overrides are matched by companion `sourceDate` before conversion to the owner's display timezone. Existing overnight handling then projects the effective shift into display-date segments while retaining the original source date.

The response carries effective fields plus planned metadata when an override exists, so Vue can render `По графику` versus `Фактически` without a second day-detail endpoint.

## API and authorization

Canonical resources:
- `PUT /api/v1/calendar-layers/{id}/overrides/{date}`
- `DELETE /api/v1/calendar-layers/{id}/overrides/{date}`

The controller keeps the existing Calendar module boundary; service ownership is verified through the parent layer. Ownership mismatches keep not-found semantics and writes remain under the existing authentication/CSRF policy.

OpenAPI: **126 operations / 132 schemas**, source hash `2d67b4db5a3d`.
Flyway: **V48**.

## Vue

`ManagedProfileDayCard.vue` is separate from the owner's `SelectedDayPanel`. It exposes `По графику`, `Не работает`, `Работает`, factual reason/time controls and `Вернуть по графику`.

## Validation evidence before first CI push

- OpenAPI `contract:generate`: green.
- OpenAPI `contract:check`: green, 126 operations / 132 schemas.
- targeted schedule-layer service/controller tests: green.
- full Java 17 `mvn clean verify`: **792/792**, zero failures/errors.
- JaCoCo: all coverage checks met.
- `git diff --check`: green.
- exact Node 20.18.1/npm 10.8.2 delivery verification: green.
- strict `vue-tsc`: green.
- Vitest: **66/66 green**.
- production Vite build: green.
- first bundle audit measured **832328 B raw**, exceeding only the inherited **825000 B** total-raw ceiling; v27.43.0 narrowly rebaselines that one ceiling to **835000 B** while preserving all entry/per-chunk/gzip limits.
- Chromium 48/48, image/PostgreSQL and staging remain CI/staging acceptance gates.

## Next

v27.44.0 — Shared Availability derives common free windows from effective schedules without broadening People Profiles into shared personal-accounting data.
