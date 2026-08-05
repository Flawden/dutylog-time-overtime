# v27.36.4 — Vue Absence & Time Bank Browser Parity Hotfix

## Trigger

The first complete Chromium parity run for the migrated domain executed 45 scenarios and reported 37 passing and 8 deterministic failures. Backend mutations returned 200/201; the failures were presentation ownership and cross-domain projection gaps rather than persistence or business-rule failures.

## Corrected browser contracts

1. **Unique modal ownership.** The retired legacy `timeBankGuideModal` and backdrop are physically removed when Vue assumes the domain, preventing duplicate global IDs.
2. **Vue → legacy projection synchronization.** A typed `dutylog:absence-time-bank-projection` event is emitted only by the winning refresh sequence. Legacy consumes the authoritative planner/account snapshot and refreshes only Calendar, Today and selected-day projections.
3. **Route preservation.** Composer launches from Today, Calendar and quick-add remain on the originating route. Explicit Vacation and Time Bank launches still navigate to their domain route.
4. **Visible edit ownership.** Deletion of an edited absence is available inside the Vue modal; the covered journal-row action has a separate diagnostic hook.
5. **Time Bank overview parity.** Usage ratio and next FIFO credit are visible from Overview; the selected period label remains in Credits.
6. **Selected-day delegation.** Legacy calendar occurrence actions call the public Vue absence editor adapter instead of manipulating retired modal DOM.

## Guardrails

- No `force` clicks, test skips, timeout inflation or locator weakening.
- No legacy `renderVacationPlanner()` or `renderOvertimeControls()` call from the projection synchronizer.
- No direct mutable legacy-state access from Vue.
- No offline mutation queue or duplicate domain owner.
- Spring Boot remains authoritative for absence overlap, compensation, FIFO, ownership and closed-period rules.

## Data and deployment impact

- OpenAPI operations/schemas: unchanged.
- PostgreSQL/Flyway: unchanged at V47.
- npm dependency graph: unchanged.
- PWA: release/cache identity only.
- Rollback: application-image/commit revert; no schema or data rollback.
