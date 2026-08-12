# DutyLog testing and JaCoCo coverage


## Vue frontend release gate

Before Maven, build the frontend assets that Maven packages into the application:

```bash
bash deploy/scripts/frontend-gate.sh
mvn clean verify
bash deploy/scripts/release-check.sh
```

The frontend gate enforces exact Node/npm versions, authentic committed-lockfile verification followed by `npm ci`, delivery/toolchain verification, generated OpenAPI drift detection, strict `vue-tsc`, 57 Vitest cases, the Vite production build and browser-bundle audit. v27.40.16 browser acceptance retains 48 strict Playwright scenarios. A plain Maven run without generated Vue assets is not the complete v27.40.16 release path. CI, Docker and staging use the same frontend boundary.

GitHub artifact persistence is diagnostic only. CI uploads compact JaCoCo XML/CSV for three days and uploads Playwright HTML/results only after failure; quota or upload errors cannot block later static checks, image build or migration smoke. Test execution itself remains fail-closed.

The complete browser baseline remains 47 Playwright scenarios. `v27.38.0` migrates Tasks, Notes & Important Days without adding browser scenarios: existing user journeys are updated only where Vue intentionally sends the same operation through generated `/api/v1/*`. Six productivity model Vitest cases and eight Java migration contracts advance the feature baseline to 49 Vitest cases and 751 JUnit `@Test` methods across 152 Java test classes. OpenAPI advances to 101 operations / 106 schemas. Payroll, Settings and Admin remain legacy-owned; the selected-day Calendar host now contains Vue-owned Tasks/Notes/Important bodies alongside the remaining bounded legacy controls.

## Two different ways to run tests

IntelliJ's green JUnit button and Maven's `verify` lifecycle are not the same operation.

### IntelliJ JUnit runner

Running a test class or the whole `src/test` tree with the green arrow:

- compiles production and test classes;
- runs JUnit tests;
- normally creates `target/classes` and `target/test-classes`;
- does **not** execute Maven plugins bound to `verify`;
- therefore does not create the JaCoCo HTML report.

This mode is useful while developing one test, but it is not the release gate.

### Maven release gate

Run from the project root, where `pom.xml` is located:

```bash
mvn clean verify
```

On Windows PowerShell or Command Prompt the command is the same:

```powershell
mvn clean verify
```

After a successful build, open:

```text
target/site/jacoco/index.html
```

The report is generated only after Maven reaches the `verify` phase. Running only
`mvn test`, IntelliJ Build, or a JUnit run configuration is not sufficient for this project.

## Running `verify` from IntelliJ without a system Maven command

1. Open **View → Tool Windows → Maven**.
2. Expand the DutyLog project.
3. Expand **Lifecycle**.
4. Double-click **clean**.
5. Double-click **verify**.
6. Refresh the project tree if `target/site` is not shown immediately.
7. Open `target/site/jacoco/index.html` in a browser.

IntelliJ can use its bundled Maven, so this also works when `mvn` is not available in
PowerShell.

## Fast development loop

For one class:

```bash
mvn -Dtest=TaskServiceTest test
```

For the complete release gate and coverage report:

```bash
mvn clean verify
bash deploy/scripts/release-check.sh
```

## What a green build means

A green build confirms the automated contracts covered by the suite. It does not replace
manual checks for browser permission prompts, operating-system notification appearance,
responsive layout, service-worker lifecycle, or a real PostgreSQL deployment.


## Coverage gate

`mvn clean verify` not only creates `target/site/jacoco/index.html`; it also fails the build if total JaCoCo coverage drops below:

- 88% instructions;
- 70% branches.

The thresholds intentionally sit below the current verified baseline (90% / 73%) to allow small refactors while preventing silent regression. Raise them only after a green CI run proves the new baseline.


## Browser E2E with Playwright

DutyLog v27.2.25 adds a separate Chromium suite. Install once and run:

```bash
npm install --no-audit --no-fund
npx playwright install chromium
npm run test:e2e
```

Playwright starts the isolated `e2e` Spring profile on port 4173. The profile uses only an in-memory H2 database. Reports and failure traces are stored under `playwright-report/` and `test-results/`. See [`PLAYWRIGHT_E2E.md`](PLAYWRIGHT_E2E.md).

## Browser parity continuation (v27.38.13)

The complete v27.38.12 Playwright artifact advances to 44 passed / 3 failed and confirms both v27.38.12 fixes: multiple daily notes and PWA upgrade are green. The remaining three Task scenarios persist data successfully, then Vue enters recovery with runtime error 15 and repeated null `parentNode` failures. Trace DOM provides the ownership fingerprint: after `data-vue-productivity=ready`, Vue-owned `#taskBoardCategory` contains the legacy `value="all"` option. Guard legacy Task renderers at write time and re-check ownership after async reads so a request started before retirement cannot complete into Vue-managed DOM. Acceptance still requires a clean 47/47 run; do not increase timeouts, retries or error allowlists.

## Browser parity continuation (v27.38.12)

The complete v27.38.11 Playwright artifact still reports 42 passed / 5 failed, but it exposes the missing evidence: the three Task failures enter the Vue recovery boundary after successful generated Task mutations, while the following selected-day/Board reads already contain the committed row. The shared frontend root is legacy `updateAccSummaries()` replacing Vue Teleport children in `#sumTasks/#sumNote/#sumImp`; the Notes failure shows the same ownership collision as a stale `—` summary while two Vue notes are visibly rendered. The PWA failure is a stale E2E preset key (`minimum`) rather than a service-worker lifecycle failure; use canonical `basic`. Acceptance still requires clean 47/47 with no retry-only pass.

## Browser parity continuation (v27.38.11)

The v27.38.10 full run reached 42 passed / 5 failed with no flaky retry. v27.38.11 keeps the suite strict and fixes three remaining shared ownership/lifecycle roots: a staged backend-authoritative Task DTO is merged into every accepted projection read until save sequencing settles, the multiple-notes reload waits for generated `/api/v1/calendar`, and first service-worker registration no longer adds a redundant explicit update check to the installation already started by `register()`. Acceptance requires a clean 47/47 run; a retry-only pass still needs diagnosis.

## Shared browser parity follow-up (v27.38.9)

The v27.38.7 full run ended at 22 passed / 25 failed. Treat the failures as four shared contracts: selected Calendar focus is idempotent, Pinia drafts must be converted to plain snapshots before browser cloning/transport, Task Board must expose projected deadline time, and first service-worker control must not reload onboarding. Run the fail-fast canary first, then the complete 47-scenario suite; do not raise timeouts or suppress browser/network errors.

## Browser preflight and fail-fast canary (v27.38.7)

Build the Vue bundle before Playwright. The root `pretest:e2e` hook fails immediately when `frontend/dist/dutylog-vue-app-shell.js` or `.css` is missing instead of spending a full browser timeout on 404s. On Windows, use the exact pinned frontend gate before Maven/browser tests:

```powershell
.\deploy\scripts\frontend-gate.ps1
mvn -B --no-transfer-progress verify
npm run test:e2e:canary
npm run test:e2e
```

The canary is only fail-fast ordering; the complete 47-scenario suite remains mandatory for release acceptance. Vue Productivity must not issue optional Tasks/Notes/Important reads until the shell has both an authoritative module snapshot and a completed onboarding profile; `MODULE_DISABLED` responses remain test failures rather than being allowlisted.
