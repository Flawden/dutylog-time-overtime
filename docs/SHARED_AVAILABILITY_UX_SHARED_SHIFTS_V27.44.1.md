# DutyLog v27.44.1 — Shared Availability UX & Shared Shifts

## UX

The selected-day card now leads with total common free-from-work time, human day-edge wording (`До` / `После`), compact windows and a 24-hour rail. The long semantics paragraph is collapsed behind `Только рабочие графики ⓘ`.

## Shared shifts visual mode

`Совместные смены` toggles a derived overlap layer:

- month cells: distinct coral outline/glow;
- week strip/agenda: the same overlap color;
- day timeline: exact `Работаем вместе` interval bars.

A date qualifies only when effective work intervals overlap for more than zero minutes. One minute is sufficient. `08:00–14:00` next to `14:00–22:00` is not overlap.

## Authority

All calculations reuse v27.44.0 effective schedule inputs and therefore inherit managed WORK/OFF overrides, factual/full-day and partial work absence handling, overnight clipping and display-timezone projection. No backend endpoint, database table or second schedule truth is added.

## Regression surface

- Vitest: **71** source cases.
- Java: **792 / 164**.
- Playwright: **48**.
- OpenAPI: **126 / 132**.
- Flyway: **V48**.
- Browser total ceilings remain **845000 B raw / 250000 B gzip** for the first exact v27.44.1 measurement.
