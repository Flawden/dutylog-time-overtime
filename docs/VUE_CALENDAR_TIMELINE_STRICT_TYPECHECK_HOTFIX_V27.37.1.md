# v27.37.1 — Vue Calendar & Timeline Strict Typecheck Hotfix

The first exact Node 20.18.1 / npm 10.8.2 frontend gate for v27.37.0 reached `vue-tsc --noEmit` and found four strict compiler errors in the new Calendar/Timeline boundary.

## Fixed

- `CalendarTimelineWorkspace.vue` now explicitly types the public domain callback parameters as `date: string` and `mode?: CalendarMode`.
- `calendarTimelineStore.openDate` accepts an optional mode and resolves `mode ?? this.mode` inside the Pinia action body.
- `calendarTimelineStore.goToday` follows the same in-body resolution and no longer references `this` in a default parameter initializer.
- Four compile-gated Java contracts preserve the exact source boundary and keep strict TypeScript enabled.

## Unchanged

No runtime behavior, API endpoint, OpenAPI operation/schema, generated transport, route owner, compatibility island, browser expectation, npm dependency, PostgreSQL schema or Flyway migration changes. The release exists only to make the already-designed v27.37.0 Calendar/Timeline implementation valid under the mandatory strict TypeScript gate.
