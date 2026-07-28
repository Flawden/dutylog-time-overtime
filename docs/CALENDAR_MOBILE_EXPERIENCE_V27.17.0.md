# DutyLog v27.17.0 — Calendar Mobile Experience

## Product goal

Calendar is now one continuous time surface instead of a single month grid. DutyLog Next can move between **Month**, **Week** and **Day** without introducing a second calendar data model.

## What changed

- Added a persistent `Month / Week / Day` scale switch to the DutyLog Next calendar.
- Added a seven-day strip with shift color, selected/today states, task count, important-date signal and overtime summary.
- Added a focused weekly agenda for the selected date.
- Added an hourly day timeline with shift intervals, timed tasks, reminders, overtime entries, all-day items and a live current-time line.
- Added one-hand swipe navigation: one day in Day mode and one week in Week mode.
- Reused the existing month bundle, immutable shift occurrences, task board, reminders, important dates, notes and overtime ledger.
- Today Dashboard now opens the hourly Day scale; note creation still opens the complete selected-day editor.
- The complete existing day editor remains available through **All day details** and Classic remains an immediate fallback.
- Calendar mode and focused date are local UI preferences only; no domain data is duplicated.

## Navigation contract

- Month header arrows move one month in Month mode.
- The same arrows move one week in Week mode and one day in Day mode.
- Today keeps the current scale and focuses the current date.
- Selecting an adjacent-month day loads the authoritative month before rendering it.

## Compatibility

- No backend API change.
- No schema change; Flyway remains V36.
- Java 17 / Spring Boot 3.3.5.
- Regression baseline: 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.
