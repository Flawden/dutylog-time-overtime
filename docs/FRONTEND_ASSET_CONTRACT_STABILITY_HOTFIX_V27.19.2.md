# v27.19.2 — Frontend Asset Contract Stability Hotfix

## Problem

The v27.19.1 runtime correctly bumped every cache-busting query to `?v=27.19.1`, but four static Java frontend contracts still searched for `?v=27.19.0`. Maven therefore reported missing Today, UI Platform, Calendar Experience and Design System assets even though the files and their order in `index.html` were correct.

## Resolution

- Asset contracts now assert the stable asset path followed by `?v=`.
- Bundle-order contracts still compare exact path positions.
- Runtime release validation still requires every static asset to use the exact current release version.
- `release-check.sh` scans all `*FrontendContractTest.java` files and fails when a semantic version is hardcoded after `?v=`.

## Compatibility

No application behavior, endpoint, database migration or persisted data changes. Flyway remains V37.

## Regression baseline

- 97 Java test classes
- 507 `@Test` methods
- 28 Playwright scenarios
