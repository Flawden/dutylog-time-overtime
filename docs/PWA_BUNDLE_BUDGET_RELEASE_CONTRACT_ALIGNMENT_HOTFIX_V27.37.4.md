# v27.37.4 — PWA Bundle Budget Release Contract Alignment Hotfix

## Evidence
The v27.37.3 `mvn -B --no-transfer-progress verify` run compiled 161 production sources and 151 test sources, then executed the suite until one static contract failed. The final Maven summary was 743 tests with exactly one failure and no errors. The failing method was:

```text
VueCalendarTimelineMigrationFrontendContractTest
  .pwaUpgradeAndBundleBudgetsBecomeRecurringFrontendGates
```

The production bundle budget already declared the current release, but the test still asserted a historical literal:

```java
assertTrue(budget.contains("\"release\": \"27.37.1\""));
```

## Root cause
The PWA/bundle-budget regression contract encoded release identity twice: once in `pom.xml` and again as hardcoded literals in the test. Release promotion updated the product/budget identity but left one test literal behind, creating configuration drift inside the test suite itself.

## Fix
The existing test method now derives `releaseVersion` from the DutyLog project version in `pom.xml` and uses that value for both current-shell and browser-bundle-budget assertions:

```java
String releaseVersion = projectVersion();
assertTrue(pwa.contains("dutylog-shell-v" + releaseVersion + "-"));
assertTrue(budget.contains("\"release\": \"" + releaseVersion + "\""));
```

No new test method is added. This fixes the drift mechanism rather than promoting the stale literal from one release number to another.

## Scope
The v27.37.3 selected-day island runtime fix is unchanged. No backend behavior, API/OpenAPI contract, database schema, Flyway migration, npm dependency graph, timeout, retry policy or Playwright strictness changes. Acceptance remains pending until the exact CI path is green, including all 47 Chromium scenarios.
