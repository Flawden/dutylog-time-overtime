# v27.22.0 — Vacation Planner

## Product contract

Vacation is rendered beside shifts but is stored as a separate absence domain. Creating, editing or deleting an absence never writes `day_entries.shift_type_id`, changes planned work hours or participates in overtime FIFO.

## Data model

Flyway `V40__vacation_planner.sql` adds:

- `vacation_settings` — annual allowance, carryover, count mode and work-year boundary;
- `absence_types` — owner-scoped built-in and custom absence categories;
- `absence_periods` — planned or approved inclusive date ranges.

Default types are seeded once per owner: Vacation, Sick leave, Unpaid leave and Other. Only Vacation consumes the allowance by default.

## Counting and validation

- count mode is `CALENDAR_DAYS` or `WEEKDAYS`;
- weekdays mean Monday-Friday; country holidays are intentionally not guessed;
- work-year start month/day is configurable, with day limited to 1–28;
- available days = annual allowance + carryover;
- presets: 14, 28 and 35 calendar days;
- preview reports counted days, shift conflicts, absence conflicts and the most constrained intersected work-year balance;
- shift conflicts are warnings and do not destroy either record;
- overlapping absence periods are rejected;
- the allowance meaning of a custom type cannot be changed after periods use it;
- allowance-consuming periods cannot exceed the applicable work-year balance;
- allowance-sensitive writes are serialized per owner so concurrent requests cannot overbook the same balance;
- settings changes are validated against every stored work year, not only the year containing today;
- a single period may cross a work-year boundary and is validated independently in each year.

## API

Both Web and stable v1 aliases are available:

- `GET /api/v1/vacation-planner`
- `PATCH /api/v1/vacation-planner/settings`
- `GET|POST /api/v1/vacation-planner/types`
- `PATCH|DELETE /api/v1/vacation-planner/types/{id}`
- `POST /api/v1/vacation-planner/preview`
- `POST /api/v1/vacation-planner/absences`
- `PATCH|DELETE /api/v1/vacation-planner/absences/{id}`

The calendar response gains additive `absences[]` day projections. Existing shift and day payload semantics are unchanged.

## UI

The unified shell adds:

- a Vacation navigation destination;
- available/planned/remaining summary;
- period editor with 14/28/35-day presets;
- per-day preview and conflict warnings;
- settings for allowance, carryover, count mode and work-year boundary;
- custom absence types;
- selected-day accordion;
- Month, Week and hourly Day composition.

## Regression baseline

- Flyway: V40
- Java test classes: 103
- `@Test` methods: 544
- Playwright scenarios: 31
