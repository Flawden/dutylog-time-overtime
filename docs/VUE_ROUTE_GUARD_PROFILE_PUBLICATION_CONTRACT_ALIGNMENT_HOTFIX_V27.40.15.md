# v27.40.15 — Route Guard Profile Publication Contract Alignment Hotfix

## Evidence

The exact v27.40.14 GitHub Actions validate job passed the frontend gate and then executed all **760 Maven tests**. Exactly one test failed, with **0 errors / 0 skipped**: `VueRouteGuardAuthorityCutoverTest.postVueLegacyRouterOnlyKeepsPayrollAndAdminSideEffects` at line 40. Browser E2E did not run because Maven stopped the validate job.

## Root cause

The product code already performs the required profile/access publication:

```js
state.profile = p;
// ...
applyRoute();
publishLegacyPlatformState();
```

The failing JUnit assertion did not inspect that behavior. It searched for the uninterrupted English sentence `Profile load still must publish authoritative access state`, while the comment was wrapped across two source lines.

## Fix

The contract now scopes itself to the real `loadProfile()` function and requires:

- profile assignment (`state.profile = p`);
- the compatibility `applyRoute()` call;
- a later `publishLegacyPlatformState()` call;
- publication to appear after `applyRoute()` in source order.

No application runtime logic is changed. Route guards, hash ownership, strict TypeScript, OpenAPI **118/120**, Flyway **V47**, retries/timeouts and the single legacy `dataLayer` offline queue/reconnect owner remain unchanged.
