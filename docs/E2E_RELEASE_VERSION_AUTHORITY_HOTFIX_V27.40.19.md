# DutyLog v27.40.19 — E2E Release Version Authority Hotfix

## Failure evidence

The v27.40.18 staging Playwright report executed all 48 scenarios: 46 passed and two failed deterministically on both attempts. Both failures compared correct v27.40.18 runtime diagnostics against stale hardcoded v27.40.16 expectations in `vue-frontend-foundation.spec.js` and `vue-app-shell.spec.js`. No flaky retry was required and no product/runtime assertion failed beyond release metadata.

## Fix

`e2e/release-version.js` derives `releaseVersion` from root `package.json`. Current-release browser assertions use that value instead of embedding release literals. The same authority is used for Vue shell/foundation diagnostics, Calendar ICS PRODID browser acceptance and the current PWA cache prefix. The deliberately old `27.38.15-synthetic-previous` cache fixture remains literal because it models a prior release.

## Boundaries

This hotfix does not change Vue route state/guards/route-entry ownership, Payroll/Admin compatibility effects, offline `dataLayer` ownership, API shape, Flyway schema, retries or timeouts.
