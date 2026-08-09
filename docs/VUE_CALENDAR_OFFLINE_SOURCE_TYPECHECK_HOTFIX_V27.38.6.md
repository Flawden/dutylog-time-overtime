# v27.38.6 — Vue Calendar Offline Source Typecheck Hotfix

## Failure

The exact Node 20.18.1/npm 10.8.2 frontend gate reached `vue-tsc` and failed because `CalendarTimelineWorkspace.vue` imported `installCalendarTimelineOfflineSource`, while `calendarTimelineStore.ts` did not export or implement that contract. The missing contextual type also produced the secondary implicit-`any` error for `focusDate`.

## Fix

The Calendar store now owns a typed, replaceable offline snapshot source. `CalendarTimelineWorkspace` installs the existing legacy `dataLayer` adapter before Calendar ownership retirement and restores the previous source on unmount. A failed generated-API range read may use the cached snapshot only for offline/network failures; ordinary HTTP/business failures remain visible.

No second offline database or queue is introduced. Spring Boot and the generated OpenAPI client remain authoritative online.

## Acceptance

Before browser execution: exact frontend gate must pass (`verify:delivery`, OpenAPI drift, `vue-tsc`, 49 Vitest, Vite build), then Maven must remain 751/751. Run the one-test E2E canary before the full 47-test Chromium suite.
