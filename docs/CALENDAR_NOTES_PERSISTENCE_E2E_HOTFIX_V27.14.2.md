# DutyLog v27.14.2 — Calendar Notes Persistence E2E Hotfix

## Problem

The legacy calendar persistence scenario opened the Notes module and immediately filled `#noteEdit`, then waited for `PUT /api/days/{date}`. Multiple Daily Notes intentionally hides the editor while the selected day has no concrete note and persists note content through dedicated endpoints. The test therefore timed out before any save request could be sent.

## Fix

The scenario now performs the same actions as a real user:

1. open the Notes module;
2. click `#noteAdd`;
3. await `POST /api/notes` with HTTP 201;
4. fill the now-visible editor;
5. await the debounced `PATCH /api/notes/{id}`;
6. navigate to the next month and back;
7. fully reload the application and verify the note, emoji and shift.

## Scope

- E2E contract and release metadata only.
- No production JavaScript or backend behavior changed.
- No database migration; Flyway remains V36.
- Baseline remains 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.
