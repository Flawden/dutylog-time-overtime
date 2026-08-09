# Calendar & Timeline Vue migration manifest

```yaml
domain: calendar-timeline
target_release: "v27.37.0"
follow_up_release: "v27.37.5"
acceptance: "47/47 Chromium and full deployment path green"
status: accepted-green
frontend_owner: Vue 3 + TypeScript
business_owner: Spring Boot
transport: generated OpenAPI client
rollback: application image rollback to green v27.36.8
```

## Ownership after v27.37.0

Vue owns Today, Month, Week and Day read surfaces, focused-date navigation, 24-hour timeline composition, calendar-layer visibility controls and the canonical calendar range store.

Spring Boot remains the source of truth for dated shifts, tasks, important events, absences, reminders, overtime balances, calendar layers and all writes.

The legacy selected-day editor compatibility island remains temporarily attached through named bridge capabilities. It preserves the mature shift, note, important-date, schedule-template and overtime editors while the read surfaces move to Vue. The island never owns Vue navigation or the canonical range store and is scheduled for retirement during the remaining v27.37.x parity passes.

## Read model and concurrency

`GET /api/v1/calendar?from=&to=` is normalized into one range bundle. The store protects concurrent reads with `readSequence`, persists mode/focus with versioned keys and publishes the winning snapshot to legacy mutation adapters. Today explicitly anchors its range to authoritative `workDate`, preventing a persisted historical month from emptying the dashboard.

Legacy mutation paths request one queued Vue refresh. Bridge-owned selected-day open/close operations suppress that invalidation, preventing redundant network reads and feedback loops.

## Acceptance boundary

- Month grid includes adjacent Monday-to-Sunday weeks.
- Week and Day preserve the same focused date.
- Day timeline composes shifts, timed tasks, important events, partial absences, reminders and read-only layers across a full 24-hour rail.
- Today uses the authoritative work-date range.
- Layer visibility writes through the generated client and rolls back on failure.
- Legacy read renderers yield after `data-vue-calendar-timeline=ready`.
- One selected-day editor island remains attached exactly once.
- PWA activation removes only previous DutyLog shell caches.
- Browser bundle raw/gzip ceilings fail the frontend build when exceeded.
- Existing browser expectations remain strict; no selectors, retries or page-error policies are weakened.

## v27.37.2 boot-routing null-safety follow-up

The Calendar Vue owner still retires the old month-navigation controls. The remaining legacy hash router must therefore treat those controls as optional while it continues to publish route state during the migration. `applyRoute()` now synchronizes visibility only for controls that still exist, preventing profile boot from aborting before first-run onboarding. Ownership, range state, selected-day compatibility island, API and persistence remain unchanged.

## v27.37.1 strict typecheck follow-up

The exact frontend gate exposed implicit-`any` callback parameters and `this` in Pinia default parameter initializers. The follow-up explicitly types the public bridge callback and resolves optional action modes inside action bodies. Ownership, read models, compatibility island, API and browser acceptance remain unchanged.
