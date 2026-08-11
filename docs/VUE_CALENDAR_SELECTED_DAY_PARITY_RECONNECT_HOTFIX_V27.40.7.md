# DutyLog v27.40.7 — Selected-Day Parity & Offline Reconnect Ownership Hotfix

## Evidence

The v27.40.6 staging Playwright report completed all 48 scenarios with 43 clean passes, 1 retry-only flaky scenario, and 4 final failures. The failures clustered around the Vue selected-day retirement rather than representing four independent regressions.

## Shift projection parity

The native Vue selected-day panel keeps the current projected shift range and timezone, and now also renders the source shift range and source timezone. Cross-date source shifts preserve their original date boundaries, and the selected-day panel again exposes the shift work duration and break duration that the retired legacy renderer displayed.

## Overtime allocation parity

Cross-midnight overtime allocations no longer flatten a midnight boundary into `00:00`. The Vue panel uses explicit day-segment labels so the preceding day can end at `24:00` and the following day can start at `00:00`, matching the retired legacy allocation semantics.

## Notes layout contract alignment

The selected-day panel is intentionally a wide Vue workspace after v27.40.4. The old browser assertion required the editor to stack below the list, which described the retired narrow rail rather than the current desktop design. The browser contract now verifies that the list and editor are non-overlapping side-by-side regions within the module bounds while preserving horizontal-overflow and minimum-editor-width checks.

## Offline reconnect ownership

The v27.40.6 report captured two identical `PATCH /api/notes/{id}` requests during an offline-to-online transition: the canonical legacy `dataLayer` replayed the queued mutation while a plain Vue notes-component unmount also submitted the current note. The Vue component now performs an unmount save only when an actual debounce write is pending. A plain reconnect-driven unmount performs no mutation, so `dataLayer` remains the single offline mutation/sync owner.

## Guardrails

- No Playwright retries or timeouts were increased.
- No assertions were weakened to ignore HTTP/runtime failures.
- TypeScript strictness and `exactOptionalPropertyTypes` remain enabled.
- No second offline queue or reconnect sync owner was introduced.
- OpenAPI remains 118 operations / 120 schemas / `91b48b10fa56`.
- Flyway remains V47.
