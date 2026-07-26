# DutyLog v27.11.2 — E2E Stability Hotfix

## Problem

The v27.11.1 Maven gate was green, but two Playwright scenarios failed:

1. The shift editor test reloaded the page immediately after the assignment PUT. `toggleShift()` correctly started an authoritative calendar refresh, but the test navigation aborted that fetch and the shared fixture treated the resulting `Failed to fetch` console message as an application failure.
2. The next-day timezone test expected a long source range with repeated years, while the UI intentionally renders the source range compactly and exposes the canonical source date on a separate line.

## Fix

- Wait for the `/api/calendar` refresh before page reload.
- Assert `03.07 23:00–04.07 07:00` and `2026-07-03` separately.
- Keep production code, occurrence calculations and Flyway V33 unchanged.

## Regression boundary

- 85 Java test classes.
- 442 `@Test` methods.
- 19 Playwright scenarios.
