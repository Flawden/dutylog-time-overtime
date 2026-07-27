# DutyLog v27.16.0 — Today Dashboard

## Goal

Turn DutyLog Next from a redesigned calendar shell into a useful daily home screen without duplicating domain logic or persistence.

## Product contract

`#today` is the default route and composes the current day from already-authoritative state:

- immutable shift occurrences and their projected local ranges;
- total earned, used and available overtime hours;
- open tasks assigned to the current day;
- upcoming important dates;
- existing task, note, overtime and quick-add flows.

The calendar remains an independent primary destination and opens the exact selected date from the dashboard.

## Shift card

The dashboard never reconstructs a dated shift from a mutable template. Active/next/completed state, progress and countdown use `startInstant` and `endInstant` from the existing occurrence model.

Visible time and timezone remain the current display projection. Untimed shift types such as a day off remain floating calendar-day markers.

## No parallel backend

There is intentionally no `/api/today` endpoint and no dashboard table. `35-today.js` is a presentation composition layer over the same data already loaded for calendar, overtime, tasks and important dates.

Mutations continue through existing editors and services:

- task editor and task details;
- day-note CRUD;
- overtime credit modal;
- important-date board;
- global quick actions.

## Navigation

DutyLog Next keeps five primary destinations on mobile:

1. Today;
2. Calendar;
3. Overtime;
4. Tasks;
5. More.

Important dates are reachable from the Today Dashboard and their existing full board.

## Offline behavior

The dashboard renders from the same IndexedDB-backed calendar snapshot as the calendar. It remains readable from cached state; write actions keep their existing online/offline guards.

## Regression protection

- `TodayDashboardFrontendContractTest` protects route, composition boundaries, instant-based shift progress and responsive CSS.
- `today-dashboard.spec.js` protects the real mobile flow: default dashboard, task creation, immediate dashboard update, calendar opening and brand navigation.
- Existing shell regression continues to protect Next ↔ Classic switching, safe-area bottom navigation and horizontal overflow.

## Schema

No schema change. Flyway remains V1–V36.
