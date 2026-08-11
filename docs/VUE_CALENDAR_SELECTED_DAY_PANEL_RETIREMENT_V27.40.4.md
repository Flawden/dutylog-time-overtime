# v27.40.4 — Vue Calendar Selected-Day Panel Retirement

v27.40.3 is the accepted baseline. This cut retires the last Calendar selected-day DOM compatibility island instead of polishing the legacy panel in place.

## Ownership cut

Before this release Vue owned the Calendar read surface but physically re-parented the legacy `#panel` through `#calendarLegacyPanelHost`. Opening a day therefore crossed back into legacy `selectDay()` and legacy renderers for shift, marker, schedule, overtime and absence UI. Tasks, Notes and Important Days were already Vue-owned but were teleported into mounts created inside that legacy panel.

v27.40.4 makes `SelectedDayPanel.vue` the only selected-day DOM owner. `CalendarPage` opens/closes it through the Calendar Pinia store, and the stable browser/accessibility IDs remain in place (`#panel`, `#chips`, `#dayEmojiApply`, `#accSched`, `#dayAddCredit`, `#accVacation` and the productivity mounts). The old `#calendarLegacyPanelHost` attach/park lifecycle is removed from the Vue bridge and legacy platform.

## Mutation boundaries

This release deliberately does **not** create a second offline queue. Shift and day-marker writes use one narrow `writeCalendarDay(date, patch)` bridge operation that delegates to the existing legacy `dataLayer.putDay()`. That dataLayer remains the single IndexedDB/offline mutation and reconnect owner until its separate infrastructure retirement is designed. Vue refreshes the authoritative Calendar range after the write.

The selected-day Overtime and Absence actions use the already-native Absence & Time Bank domain. Tasks, Notes and Important Days keep their existing Vue Productivity owner and teleport into mounts rendered by the Vue selected-day panel. Schedule preview/apply uses the generated `/api/v1/schedule-templates/*` operations.

## Parity details locked in this cut

- projected/cross-midnight shifts still mutate their `sourceDate`, not the visually selected continuation date;
- active shift click still toggles the assignment off;
- day emoji presets/custom marker/clear retain their stable selectors and persistence flow;
- schedule preview defaults to 31 days, keeps safe overwrite semantics and preserves exact preview/apply business assertions;
- overtime credit/usage and absence entry points keep their established selected-day browser contracts;
- `body.panel-open` remains the mobile shell signal while the Vue panel is open.

## Retirement barriers

Legacy selected-day renderers yield after `data-vue-calendar-selected-day=ready` so stable IDs have one writer. The old Calendar root, including its legacy panel, is removed at runtime when Vue Calendar ownership becomes ready. Historical numbered JavaScript may still contain unreachable source markup/helpers until the later numbered-JS cleanup, but it no longer owns the live selected-day DOM.

## Non-goals

This release does not yet retire hash routing, the remaining modal adapters, the offline dataLayer transport/queue, Payroll/Admin ownership or every numbered JavaScript helper. It also does not close the Functional Parity / Layout Consistency register. Those remain v27.40.x blockers.

## Locked acceptance surface

- OpenAPI: 118 operations / 120 schemas / `91b48b10fa56`
- Flyway: V47
- Java: 153 test classes / 758 `@Test` methods
- Chromium Playwright: 48 scenarios
- Vitest: 52 cases
- exact frontend toolchain: Node 20.18.1 / npm 10.8.2

No retry, timeout, runtime-error collector or HTTP-failure policy is weakened. Full acceptance still requires the exact frontend gate, Maven Java 17 verify, clean canary, Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging.
