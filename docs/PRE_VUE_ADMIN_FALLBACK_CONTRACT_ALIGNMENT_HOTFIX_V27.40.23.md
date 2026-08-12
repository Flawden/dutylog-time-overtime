# DutyLog v27.40.23 — Pre-Vue Admin Fallback Contract Alignment Hotfix

## CI evidence

The v27.40.22 exact frontend gate passed. Maven verify failed before release-check and Playwright. The saved GitHub log is virtualized and does not include the final Surefire summary, so the exact v27.40.22 tree was independently exercised with the source-only web JUnit contract surface.

Result before the fix: **285 passed / 1 failed**. The only failing source assertion is `TodayDashboardFrontendContractTest.shellExposesTodayAsTheDefaultPrimaryDestinationWithoutRemovingCalendar`, which still requires the pre-v27.40.22 literal `let active = VIEWS[name] ? name : "today"`.

## Alignment

The v27.40.22 runtime intentionally makes Admin Vue-only. Before Vue readiness, `#admin` is mapped to Settings; unknown routes still fall back to Today. v27.40.23 therefore changes the stale test to require both semantic fragments instead of restoring the retired Admin route owner.

No application routing, Admin API/security, offline queue, OpenAPI or Flyway behavior changes beyond normal release-version metadata.
