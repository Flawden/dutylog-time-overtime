# v27.40.28 — E2E Vue Shell Identity Contract Alignment Hotfix

## Evidence

The v27.40.27 Chromium report contains one failed scenario and one identical retry. Both time out in `e2e/helpers.js` waiting for `#whoami`. The failure screenshot shows first-run onboarding already closed, Today rendered, and the generated authenticated username visible in the Vue shell profile control. No runtime page exception is demonstrated by this failure.

## Root cause

`#whoami` belonged to `#legacyGlobalHeader`, which v27.40.26 intentionally retires after Vue readiness. v27.40.27 correctly made async boot tolerate that retirement. The shared Playwright registration/onboarding helper was therefore asserting a DOM node whose absence is now the intended architecture.

## Fix

`registerAndOnboard()` and every direct browser identity assertion now use the public Vue-owned `[data-vue-shell-profile] > b` boundary. The existing shell compatibility source contract requires that selector and forbids `page.locator('#whoami')` anywhere in the E2E tree.

No application runtime, onboarding persistence, HTTP/OpenAPI contract, Flyway migration, auth rule, offline queue executor, dependency graph, retry or timeout behavior changes in this cut.
