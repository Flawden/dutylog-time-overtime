# v27.37.0 — Vue Calendar & Timeline

The second bounded domain migration moves DutyLog Today and the Calendar Month, Week and Day read surfaces to Vue 3 with strict TypeScript.

## Delivered

- canonical `GET /api/v1/calendar` adapter through the generated client;
- Pinia range/focus/mode store with versioned persistence and latest-read-wins protection;
- Today range loading anchored to authoritative `workDate`, not a stale persisted month;
- Month grid, Week agenda and 24-hour Day timeline;
- shift, task, timed important-event, partial-absence, reminder and calendar-layer timeline composition;
- read-only calendar-layer composition plus optimistic visibility mutation with rollback;
- typed Vue-to-legacy projection event;
- named compatibility bridge for the selected-day mutation editor;
- debounced legacy-to-Vue invalidation without reload loops during bridge-owned panel actions;
- Q-07 PWA previous-cache upgrade E2E and accepted ADR-006;
- Q-08 fail-closed raw/gzip browser-bundle budgets;
- 10 feature Vitest cases, 11 compile-gated Java contracts and two new Chromium scenarios.

## Ownership

Vue is the only runtime owner of Today and Calendar Month/Week/Day read DOM. Spring Boot remains the source of truth for shifts, tasks, important events, absences, reminders, overtime balances, calendar layers and every write.

The selected-day mutation editor remains a temporary compatibility island under `#calendarLegacyPanelHost`. It preserves the mature shift, note, important-date, schedule-template and overtime editing flows without owning Vue routing or the canonical range store.

## Unchanged boundaries

No OpenAPI operation/schema, PostgreSQL schema or Flyway migration changes. The generated contract remains 98 operations / 103 schemas and Flyway remains V47.
