# DutyLog v27.40.20 — E2E Release Version Contract Alignment Hotfix

## Incident

v27.40.19 passed the exact frontend gate but Maven ran 767 tests with two deterministic failures before Playwright. Both failing tests were source contracts written for the pre-v27.40.19 E2E source shape.

## Fix

The PWA contract now verifies that `e2e/pwa-upgrade.spec.js` imports `releaseVersion` from `./release-version` and uses `dutylog-shell-v${releaseVersion}-`. The shell/Calendar Sync contract now verifies the same shared helper import and the `${releaseVersion}` ICS PRODID expectation. No browser runtime behavior is reverted.

## Ownership

The v27.40.17 Vue route-commit/hash-listener retirement remains intact. `dataLayer` remains the sole offline mutation/reconnect owner. OpenAPI remains 118 operations / 120 schemas, hash `91b48b10fa56`; Flyway remains V47.
