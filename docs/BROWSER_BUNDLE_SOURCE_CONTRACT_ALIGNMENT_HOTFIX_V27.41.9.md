# v27.41.9 — Browser Bundle Source Contract Alignment Hotfix

## Exact failure
The pushed v27.41.8 commit `3fab0e35d65d6028f48a98dcfb9575753b6c8798` passed the exact frontend typecheck/unit/build gate. Java 17 Maven then completed all 791 tests with one failure in `VueCalendarTimelineMigrationFrontendContractTest.pwaUpgradeAndBundleBudgetsBecomeRecurringFrontendGates` at line 204.

The stale assertion required `audit-browser-bundle.mjs` to contain `budget.maxBytes`, a key retired when v27.41.7 moved the browser gate from one monolithic bundle ceiling to entry, per-chunk and total raw/gzip ceilings.

## Fix
The Java source contract now requires the six current audit dimensions:

- `budget.maxEntryBytes`
- `budget.maxEntryGzipBytes`
- `budget.maxChunkBytes`
- `budget.maxChunkGzipBytes`
- `budget.maxTotalBytes`
- `budget.maxTotalGzipBytes`

It also rejects the obsolete `budget.maxBytes` source shape so the contract cannot silently regress to the monolithic model.

## Boundary
No production Vue/TypeScript/Vite logic, manual chunk ownership, browser budget value, service-worker caching policy, HTTP/OpenAPI contract, Flyway migration, auth/accounting behavior, offline `dataLayer` ownership, dependency, timeout or retry changes. The purpose of v27.41.9 is to unblock CI so the unchanged real browser-bundle audit can finally judge the v27.41.8 shared-runtime split.
