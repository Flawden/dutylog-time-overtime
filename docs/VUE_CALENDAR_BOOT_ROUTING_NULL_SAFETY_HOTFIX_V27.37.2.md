# v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix

## Failure
The self-hosted staging run reached the real Chromium suite and many unrelated fresh-user scenarios timed out while waiting for first-run onboarding. The shared failure occurred before domain-specific test logic.

The remaining legacy route synchronizer still executed direct Calendar header writes:

```js
document.querySelector(...).style.visibility
```

After Vue ownership retirement, each query returns `null`, producing the literal `null.style` failure mode. The resulting exception occurs inside `loadProfile()` before `maybeShowOnboarding()`. `loadProfile()` catches the exception, so the overall application continues loading and backend traffic looks healthy, while every fresh-user E2E waits for `#firstRunOnboarding` until its 30-second timeout.

## Fix
`applyRoute()` now updates the retired header controls only when matching elements exist, using one `querySelectorAll(...).forEach(...)` operation. No legacy controls are restored.

## Regression
The existing `VueCalendarTimelineMigrationFrontendContractTest` requires the null-safe route-chrome synchronization and forbids all three historical direct `.style.visibility` dereferences. No new `@Test`, Playwright scenario or Vitest case is added.

## Unchanged boundaries
Spring Boot remains authoritative. Generated OpenAPI ownership, PostgreSQL, Flyway V47, npm dependency graph, retry policy, browser timeout, Calendar/Timeline Vue ownership and the selected-day editor compatibility island are unchanged.

Acceptance remains pending until the exact frontend, Maven, 47/47 Chromium, immutable-image, clean PostgreSQL and staging path is green.
