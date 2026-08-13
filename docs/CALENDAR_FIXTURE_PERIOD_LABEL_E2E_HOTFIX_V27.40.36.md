# v27.40.36 — Calendar Fixture & Period Label E2E Hotfix

## CI evidence

The exact v27.40.35 pipeline passed the frontend gate and Java 17 Maven, then ran the full 48-scenario Chromium suite. The final result was 46 passed / 2 failed, and both failures repeated identically on retry.

## Root causes

`calendar-mobile-experience.spec.js` asserted that a fresh account already contained a `.cell.hasShift`. The trace shows the canonical calendar response had `days: []` and `shiftOccurrences: []`, so the product correctly rendered no shift cells. The test now creates one shift through the normal selected-day UI/API flow before checking the `hasShift` class, `--shift-color`, accessible label and hidden mobile shift text.

`overtime-next.spec.js` selected the year period successfully and rendered `2026 год`, but the assertion used the case-sensitive regex `/Год|Year/`. The product copy is valid Russian; the test now checks `/год|year/i` without changing runtime text.

## Fix boundary

No production Vue/TypeScript, backend calculation, HTTP/OpenAPI, PostgreSQL/Flyway, auth, offline sync, onboarding, retry policy or timeout is changed. The hotfix only supplies deterministic test data where required, aligns the locale assertion with existing copy, and advances release metadata.
