# v27.37.2 — Vue Calendar Boot Routing Null-Safety Hotfix

## Symptom

The self-hosted staging run reached the real Chromium suite and many unrelated fresh-user scenarios failed after roughly 32.4 seconds, then retried and failed again. Backend registration, authentication, modules, profile and calendar reads completed quickly, but the expected onboarding mutations never followed.

## Root cause

`v27.37.0` correctly lets the Vue Calendar/Timeline owner retire the legacy header controls `#prev`, `#todayBtn` and `#next`. During the same page boot, the legacy profile loader still calls `applyRoute()` because hash routing remains the temporary application-level navigation owner.

`applyRoute()` unconditionally dereferenced those retired nodes:

```text
document.querySelector(...).style.visibility
```

After Vue ownership retirement, each query returns `null`. The resulting exception occurs inside `loadProfile()` before `maybeShowOnboarding()`. `loadProfile()` catches the exception, so the overall application continues loading and backend traffic looks healthy, while every fresh-user E2E waits for `#firstRunOnboarding` until its 30-second timeout.

## Fix

`applyRoute()` now collects any surviving legacy month-navigation controls with one selector and updates only the nodes that exist. It does not recreate the controls and does not transfer Calendar ownership back to legacy JavaScript.

The existing `VueCalendarTimelineMigrationFrontendContractTest` is extended to require the null-safe collection and forbid the three direct `.style.visibility` dereference shapes. No new test method is added, so the baseline remains:

```text
151 Java test classes
743 @Test methods
47 Chromium Playwright scenarios
43 Vitest cases
Flyway V47
```

## Non-goals

This hotfix does not change API/OpenAPI, Spring Boot business authority, PostgreSQL/Flyway, npm dependencies, Calendar/Timeline range ownership, the selected-day compatibility island, browser selectors, retries or timeout values.

## Acceptance

The release remains acceptance-pending until the exact staging path is green: frontend gate, Maven verify, 47/47 Chromium, immutable image verification, clean PostgreSQL smoke and staging deployment.
