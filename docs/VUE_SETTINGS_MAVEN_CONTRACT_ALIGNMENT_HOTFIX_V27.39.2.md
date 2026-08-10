# v27.39.2 — Vue Settings Maven Contract Alignment Hotfix

## Trigger

Local `mvn -B --no-transfer-progress verify` on v27.39.1 compiled 161 production classes and 153 test classes, then executed all 758 tests. Exactly two static/source assertions failed; 756 tests passed and there were no runtime errors.

## Alignment

1. `VueCalendarTimelineMigrationFrontendContractTest` now binds the PWA upgrade scenario to `dutylog-shell-v27.38.15-synthetic-previous`, matching the live Playwright fixture and release-check contract.
2. `VueSettingsWorkspaceMigrationFrontendContractTest` now binds readiness publication to `document.documentElement.setAttribute("data-vue-settings-workspace", "ready")`, matching the existing Settings retirement bridge.

No production runtime behavior changes in this hotfix. TypeScript strictness, generated OpenAPI, backend rules, Flyway V47 and browser acceptance policy remain unchanged.

## Acceptance

The release is accepted only after exact frontend gate, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL smoke and staging are green.
