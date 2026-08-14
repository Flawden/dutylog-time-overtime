# v27.42.6 — Release Check Current-Version Contract Hotfix

## Exact failure
The v27.42.5 pipeline passed Maven, Java/Vitest/Playwright inventory baselines and the earlier shell-contract fixes, then failed Release checks because recurring guard blocks still required v27.42.4 values in the service worker, browser bundle budget and generated lockfile manifest.

## Fix
Recurring current release artifact assertions use `${VERSION}` instead of copied semantic version literals. Lockfile-manifest assertions use the already computed `LOCKFILE_ACTUAL_SHA`. Historical release identity checks remain literal so lineage is still verified.

## Boundary
No production business logic, People Profiles behavior, schedule-template concurrency locking, persistence schema, OpenAPI contract, Flyway migration, timeout or retry behavior changes.
