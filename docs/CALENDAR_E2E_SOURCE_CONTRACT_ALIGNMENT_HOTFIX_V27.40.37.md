# v27.40.37 — Calendar E2E Source Contract Alignment Hotfix

## CI evidence

The v27.40.36 exact frontend stage passed. The workflow then failed in `Build, test and enforce coverage` (`mvn -B --no-transfer-progress verify`) before release-check or Chromium started.

## Root cause

`FunctionalParitySweepIIMobileUsabilityContractTest.monthCalendarUsesShiftColorAsScannableDataOnMobile` still required `browser.contains(".cell.hasShift")`. v27.40.36 deliberately removed that literal selector when it stopped depending on ambient calendar data: the scenario now creates a shift for `today`, binds a date-scoped `shiftCell`, and checks `toHaveClass(/hasShift/)`. Therefore the production/browser behavior was correct while the Java source contract was guaranteed false.

## Fix

The Java contract now verifies the actual deterministic ownership boundary: the PUT day fixture, the date-scoped `shiftCell` locator, and the `hasShift` class assertion. No production Vue/TypeScript, backend, HTTP/OpenAPI, PostgreSQL/Flyway, auth, offline sync, onboarding, Playwright timeout/retry policy or browser behavior changes.
