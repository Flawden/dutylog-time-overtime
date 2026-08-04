# v27.34.3 — Vue Shell E2E Navigation Compatibility Hotfix

## Incident

The v27.34.2 browser-runtime correction removed the `process is not defined` cascade and allowed the Vue shell to boot. Chromium then exposed the next migration boundary problem: twenty-one historical E2E scenarios still clicked hidden legacy chrome (`#tabbar`, `#nextTopbar`, `.brandLockup`, `#logout`) even though v27.34.0 intentionally transferred visible shell ownership to Vue.

A separate Calendar Sync assertion still pinned the old `27.33.0` ICS `PRODID`, and two Vue shell scenarios assumed a route was enabled by the default onboarding preset instead of declaring the required full preset or navigating through the released bridge.

## Fix

- E2E navigation now uses the public `DutyLogVuePlatform.navigateLegacy(...)` capability with `DutyLogLegacyPlatform.navigate(...)` as the readiness fallback.
- The shared `openView(...)` and module-toggle helpers no longer click hidden legacy navigation.
- Vue shell controls expose stable release-owned hooks for brand, profile, more menu, logout and close actions.
- Shell-specific tests use visible Vue chrome; they still assert that legacy chrome is hidden after readiness.
- Module-dependent shell scenarios declare the `full` onboarding preset explicitly.
- Calendar Sync expects the current release identity in the generated ICS feed.
- No production business rule, API, database schema or deployment topology changes.

## Ownership rule

Legacy product screens remain authoritative during the strangler migration. Vue owns visible shell interaction. Tests may reach product routes through the public bridge, but must not restore or force-click retired chrome.

## Acceptance

- strict `vue-tsc`, 11 Vitest cases and Vite browser-bundle audit pass;
- 135 Java test classes / 648 `@Test` methods pass;
- all 44 Playwright scenarios pass with no hidden-legacy navigation clicks;
- Flyway remains V47;
- one Spring Boot application image/container remains unchanged.
