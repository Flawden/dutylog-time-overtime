# v27.42.1 — People Profiles Source Contract Alignment Hotfix

## Exact stale contract
`v27.42.0` intentionally mounts `SelectedDayPanel` only for the owner calendar: `dayPanelOpen && viewingSelf`. The same Java contract class already asserted that People Profiles boundary in its calendar-page test, but an older selected-day ownership test still required the obsolete literal `<SelectedDayPanel v-if="dayPanelOpen"`.

That old literal is absent from the actual v27.42.0 `CalendarPage.vue`, so the assertion evaluates false even though the runtime boundary is correct.

## Fix
The stale assertion now requires `<SelectedDayPanel v-if="dayPanelOpen && viewingSelf"`, matching the read-only People Profiles contract.

## Boundary
No Vue runtime behavior, HTTP/OpenAPI contract, Flyway migration, offline queue ownership, browser bundle boundary, budget, dependency, timeout or retry changes.
