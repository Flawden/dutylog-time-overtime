# v27.24.0 — Calendar Comfort & Correctness

## Goal

Turn the calendar from a collection of capable features into a calm daily workspace without starting the final production-optimization cycle.

## Contextual return to today

The old mobile design system hid `#todayBtn` unconditionally. The control is now hidden by default and appears only on the Calendar route when the active Month, Week or Day context no longer represents today. Returning to today restores the current period, focuses the current date and removes the control again.

## Important-day date ownership

The selected calendar date is the authoritative contextual creation date. Opening the Important Days day module can no longer reuse a stale date left by an earlier draft. Dedicated board creation still starts from today.

## Overnight Today card

A timed shift keeps a compact `HH:MM–HH:MM` primary line. When start and end dates differ, a separate date-range chip shows both dates. This avoids squeezing two dates, timezone and hours into one phone-width line.

## Calm loading and diagnostics

The first calendar load keeps the skeleton. Later refreshes preserve the last successful grid, mark it as refreshing and expose a polite live status. Each load records a bounded in-memory metric with duration, cache origin and success state, dispatches `dutylog:calendar-load`, and logs only slow metadata. No user calendar content is collected or transmitted.

The final performance-hardening cycle remains separate; this release establishes measurement and removes destructive refresh flicker.

## Multiple schedules

Companion layers use one horizontally scrollable pill strip. Each layer has a color marker, truncated name, compact visible/hidden signal and full accessible label. Server-owned visibility and read-only projection remain unchanged.

## Compatibility

- No HTTP API changes.
- No OpenAPI schema changes.
- No database migration; Flyway remains V41.
- External Calendar Sync security boundaries remain unchanged.
- Baseline: 108 Java test classes / 569 `@Test` methods / 33 Playwright scenarios.
