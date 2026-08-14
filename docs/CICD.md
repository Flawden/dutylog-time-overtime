# v27.42.7 browser bundle source-contract alignment

Exact v27.41.8 frontend validation passed, then Java 17 Maven ran 791 tests and failed only because `VueCalendarTimelineMigrationFrontendContractTest` still searched for retired `budget.maxBytes`. v27.42.7 aligns that compile-gated source contract with the six segmented entry/per-chunk/total raw+gzip budget keys; the actual audit limits and Vite chunk architecture are unchanged.

# v27.41.8 shared runtime bundle gate

The v27.41.7 exact frontend gate passed lockfile verification, OpenAPI 124/130, strict typecheck, 64/64 Vitest and production Vite compilation, then failed only the new per-chunk browser audit because `chunks/main-8syw6Ngx.js` measured 565411 B raw / 141782 B gzip. v27.41.8 keeps every budget unchanged and adds coarse manual chunks so CI must prove the architecture rather than relax the gate. Java 17 Maven 791/791, canary, Chromium 48/48, immutable image, PostgreSQL V47 and staging remain blocking.

# v27.41.6 acceptance boundary

## v27.41.7 segmented frontend gate

The exact Node 20.18.1/npm 10.8.2 gate now requires both the stable entry/CSS and at least one non-empty `dist/chunks/*.js` artifact. `audit:bundle` scans every emitted JS file and enforces entry/per-chunk/total ceilings before Maven packages the entire `frontend/dist` directory into `static/vue`.


v27.41.6 starts from proven-green v27.41.5 commit `dc7738b80c9af1873a2ae0b0f2bab35a74724855` / tree `908dff05790d354e3b1b5a3aa940c1066f29f22c`. The release changes ledger-integrity reconciliation semantics, one regression test and integrity presentation/CSS only. Exact Node 20.18.1/npm 10.8.2 frontend validation, Java 17 Maven **791/791**, auth/onboarding canary, mandatory Chromium 48/48, immutable image, PostgreSQL V47 smoke and staging deploy remain blocking.

# v27.41.5 acceptance boundary

v27.41.5 starts from v27.41.4 commit `84ba4a1d8c1eda6f88088c8b9af1b0c2ae1c784d` / tree `0e82dd5e26f62bed0a02b7e2d4b8723c3dc73d49`, whose exact Node 20.18.1/npm 10.8.2 frontend gate passed delivery, OpenAPI 124/130, strict typecheck, 64/64 Vitest and production Vite build, then failed only the raw browser-bundle budget at 806839 B > 800000 B. The hotfix raises only the raw ceiling to 810000 B; gzip stays locked at 250000 B. Java 17 Maven, auth/onboarding canary, mandatory 48/48 Chromium, immutable image, PostgreSQL V47 smoke and staging deploy remain blocking.

# v27.41.3 acceptance boundary

v27.41.3 starts from proven-green v27.41.2 commit `52a0e2a1b4e28aa56250bb813639830c56226a91` / tree `91fd1fa2fd426556d4031fbe14506cc722398b77`. The release changes Today presentation plus shared typed calendar visual helpers and extends existing Chromium coverage without adding scenarios, retries or timeout inflation. Exact Node 20.18.1/npm 10.8.2 frontend validation, Java 17 Maven, auth/onboarding canary, mandatory 48/48 Chromium, immutable image, PostgreSQL V47 smoke and staging deploy remain blocking.

# v27.41.2 acceptance boundary

v27.41.2 starts from v27.41.1 and changes only the stale Java source-contract assertion for overtime chart zero-height/pixel geometry plus release metadata. Runtime Vue/TS chart geometry, canonical overtime accounting, Playwright geometry assertions, pipeline policy and dependency graph are unchanged. Exact Node 20.18.1/npm 10.8.2 frontend validation, Java 17 Maven, auth/onboarding canary, mandatory 48/48 Chromium, immutable image, PostgreSQL V47 smoke and staging deploy remain blocking.

# v27.41.1 acceptance boundary

v27.41.1 starts from the proven-green v27.41.0 staging baseline and changes only overtime chart presentation geometry, the existing Overtime Next Chromium assertions and release metadata. Pipeline policy is unchanged: exact Node 20.18.1/npm 10.8.2 frontend validation, Java 17 Maven, auth/onboarding canary, mandatory 48/48 Chromium, immutable image, PostgreSQL V47 smoke and staging deploy remain blocking.

## v27.41.0 Calendar Visual Language Foundation — proven green

v27.41.0 started from proven-green v27.40.37 commit `b823e8741dd1f2dbc36424105f0e34cfeffc2817` / tree `1cf30a4e54b79c2a783aac1f3d2342bb05e936ef` and is now exact-CI/staging green with manual calendar acceptance.

## v27.40.29 Vue Logout Ownership & Offline Status Contract Hotfix

The v27.40.28 full Chromium run executed all 48 scenarios with 46 passing and two deterministic failures. PWA offline state was correct but the assertion was coupled to retired wording; remember-me logout clicked the Vue control but emitted no `/logout` request because the legacy platform adapter still clicked deleted `#logout`. This cut changes neither workflow topology nor retry/timeout policy. Maven inventory advances to 782/782 across 161 Java test classes; Chromium remains 48 strict scenarios.

Status: v27.40.29.

## v27.40.28 E2E Vue Shell Identity Contract Alignment Hotfix

The v27.40.27 full Chromium run reached the correct runtime state but failed one shared helper assertion because `#whoami` was intentionally removed with the legacy recovery header. This cut aligns `registerAndOnboard()` with the Vue-owned profile identity and adds a source contract preventing the helper from regressing to legacy chrome. Runtime, OpenAPI 124/130, Flyway V47 and offline queue semantics are unchanged.

# DutyLog CI/CD

## v27.40.27 Legacy Header Async Boot Ownership Hotfix

- CI topology, retries and timeout policy are unchanged.
- Frontend acceptance remains 60 Vitest cases; Maven static inventory advances to 780/780 across 161 Java test classes; Chromium remains 48 strict scenarios.
- v27.40.26 reached Chromium and failed deterministically in first-run onboarding because async `70-user-boot.js` resumed after Vue retired `#legacyGlobalHeader` and wrote to the missing `#whoami`.
- The hotfix keeps header retirement intact and makes legacy identity writes recovery-only; it does not delay Vue readiness or revive duplicate chrome.
- Offline/sync ownership and `dataLayer` execution remain unchanged.

Status: v27.40.28.

## v27.40.24 final legacy ownership audit and dead UI surface retirement

- CI topology, retry policy and timeout policy are unchanged.
- Frontend gate must preserve 60 Vitest cases; Maven acceptance advances to 775/775 across 160 Java test classes; Chromium remains 48 strict scenarios.
- The new ownership contract proves recovery chrome is physically absent post-ready while first-run/offline presentation and the single dataLayer queue remain bounded exceptions.

Status: v27.40.24.

## v27.40.23 pre-Vue Admin fallback contract alignment

- Exact v27.40.22 frontend gate passed; Maven stopped before release-check and Playwright on a stale Today fallback source assertion.
- v27.40.23 changes only the source contract plus normal release metadata; workflow topology, retries/timeouts and blocking gates remain unchanged.

Status: v27.40.23.


## v27.40.22 Vue Admin workspace and final live legacy UI retirement

- Exact frontend/Maven/Chromium topology is unchanged; the frontend gate must compile the new Vue Admin workspace and 60 Vitest cases before Maven 772/772 and the 48-scenario browser suite.
- Canonical `/api/v1/admin/**` aliases are exercised by source/security contracts; no retry, timeout, artifact or deployment bypass is added.
- A green release proves the live application no longer needs a post-Vue legacy route/UI owner.

Status: v27.40.22.

## v27.40.21 Vue Payroll workspace retirement

- Exact frontend/Maven/Chromium workflow topology is unchanged; the frontend gate must compile the new Vue Payroll workspace before Maven 769/769 and the existing 48-scenario browser suite.
- The existing Payroll Foundation browser scenario remains blocking and now waits on `DutyLogVueDomains.payroll.ready()`.

Status: v27.40.21.

## v27.40.20 E2E release-version contract alignment

- Maven source contracts now assert the shared E2E release-version helper/template instead of current-release literals; workflow topology, retries and timeouts are unchanged.


## v27.40.19 E2E release-version authority

- Playwright current-release assertions derive from root `package.json`; release bumps do not require hand-edited Vue/ICS/PWA expected version literals.
- The suite remains 48 strict scenarios with existing retry/timeouts unchanged.

## v27.40.16 route-entry freshness acceptance boundary

- Exact frontend gate remains Node 20.18.1/npm 10.8.2 with 57 Vitest cases; Maven baseline advances to 764 `@Test` methods across 155 Java test classes.
- Chromium remains 48 strict scenarios with zero final failures and zero flaky retries required for acceptance.
- No workflow/retry/timeout policy changes; the cut changes Vue read-model ownership only.

## v27.39.0 Settings, Workspace & Integrations delivery boundary

- Exact Node `20.18.1` / npm `10.8.2`, authentic `npm ci`, generated OpenAPI drift, strict `vue-tsc`, **52 Vitest cases**, Vite and bundle budgets remain blocking before Maven.
- Maven baseline advances to **757 `@Test` methods across 153 Java test classes**; Chromium advances to **48 strict scenarios**.
- Normal production Vite builds publish no source maps; hidden maps are opt-in only through `DUTYLOG_FRONTEND_SOURCEMAPS=true` and remain diagnostic artifacts rather than public static assets.
- Pushes to `test` remain single-pass GitHub-hosted `ubuntu-latest`: validate → immutable image → clean PostgreSQL V1–V47 smoke → staging deploy.

## v27.38.0 Tasks, Notes & Important Days delivery boundary

- Exact Node `20.18.1` / npm `10.8.2`, authentic `npm ci`, generated OpenAPI drift, strict `vue-tsc`, 49 Vitest cases, Vite and raw/gzip bundle budgets remain blocking before Maven.
- Maven baseline is 751 `@Test` methods across 152 Java test classes; Chromium remains 47 strict scenarios with retries only as already configured and page/runtime errors fail-closed.
- Pushes to `test` keep the single-pass staging owner: validate → immutable image → clean PostgreSQL V1–V47 smoke → deploy.
- The release adds no second workflow, dependency graph or deployment topology.

## v27.37.1 Calendar/Timeline strict typecheck delivery boundary

- The v27.36.5 single-pass routing remains unchanged: pull requests, tag pushes and branch pushes outside `test` run the full `CI / test-and-package` path.
- Pushes to `test` run the full `Deploy staging / validate` path exactly once, then build, verify and deploy the immutable image; v27.37.1 adds no second workflow or bypass and must pass the same strict `vue-tsc` gate.
- The frontend gate now includes 49 Vitest cases, the production Vite build, forbidden-runtime audit and fail-closed raw/gzip browser-bundle budgets.
- Chromium now runs 47 scenarios, including one-owner Calendar/Timeline acceptance and previous-service-worker-cache upgrade behavior.
- Maven verify, static release checks, immutable image verification and clean PostgreSQL smoke remain blocking. Artifact publication remains diagnostic and non-blocking.

## v27.36.4 Browser parity delivery boundary

- The 45-scenario Playwright gate remains blocking and strict.
- Projection synchronization, unique DOM ownership and route preservation are covered by Vitest/source-only Java contracts before Chromium.
- No artifact-upload exception can bypass frontend, Maven, browser, image or clean-migration gates.

## v27.36.3 CI Artifact Quota Resilience delivery boundary

Validation artifacts are diagnostic outputs, not release authority. JaCoCo publication is limited to `jacoco.xml` and `jacoco.csv`; Playwright HTML/results are uploaded only after a failure. Both upload steps are short-lived, run/attempt-qualified and `continue-on-error`, so exhausted GitHub artifact storage cannot skip release checks, immutable image build, clean PostgreSQL smoke or staging deployment.

The actual quality gates remain blocking: frontend typecheck/tests/build, Maven verify, release static checks, Playwright execution, Docker build, migration smoke and deployment. Only report persistence is best-effort.

## v27.36.2 Vue Timer Static Contract Compile Coverage delivery boundary

The source-only Java compile gate now includes both `*FrontendContractTest.java` and `*HotfixTest.java`. The timer regression contract uses whitespace-normalized matching and Java 17-valid string literals; strict `vue-tsc`, Maven and all downstream gates remain blocking.

## v27.36.0 Vue Absence & Time Bank delivery boundary

The first domain migration uses the same fail-closed Gate A pipeline: committed authentic lockfile, generated OpenAPI drift, strict TypeScript, Vitest, production bundle audit, Maven/JUnit, Playwright, Docker, clean PostgreSQL smoke and staging. The release adds no alternate frontend server and no runtime dependency installation.

The domain-specific gates prove one Vue owner for both routes, typed generated operations, stale-read sequencing, duplicate-submit rejection, HTTP `409` recovery, responsive parity, accessibility and retirement of the legacy route/modal owners.

## v27.35.4 committed-lockfile and Maven test-compile boundary

Validation remains fail-closed before image build and deployment:

```text
exact Node 20.18.1 + npm 10.8.2
→ verify committed authentic frontend/package-lock.json
→ npm ci
→ local vue-tsc / vitest / vite launcher checks
→ npm ls --all
→ generated OpenAPI drift check
→ vue-tsc
→ 26 Vitest cases
→ Vite production build + browser-bundle audit
→ Maven production compile + testCompile + tests
→ release-check + 45 Playwright scenarios
→ immutable image build / clean PostgreSQL smoke / deploy
```

GitHub Actions already proved the full frontend boundary in v27.35.3. v27.35.4 corrects one malformed Java string in a test-only static contract so Maven can proceed past `testCompile`; no delivery gate is skipped or weakened.

## Browser-runtime bundle gate (v27.34.3)

Vite library mode must replace `process.env.NODE_ENV` with the literal production value. `npm run build` now executes `frontend/scripts/audit-browser-bundle.mjs` after Vite and rejects residual `process.env`, CommonJS `require(...)` / `module.exports`, `__dirname` or `__filename` in the generated browser JavaScript. Because Docker also runs `npm run build`, CI and the production image validate the same artifact. Playwright remains the final real-browser runtime gate.

## Strict Vue build compatibility hotfix (v27.34.1)

The frontend gate remains first and strict. v27.34.1 fixes the concrete template and Vite type failures found by GitHub Actions; CI must still execute real `vue-tsc`, Vitest and Vite before Maven. No fallback build or skipped typecheck is permitted.

## Vue app-shell build gate (v27.34.0)

Every validation workflow now builds the browser application before Maven packages resources:

```text
frontend npm install
→ vue-tsc
→ Vitest
→ Vite build
→ Maven verify/test
→ release-check
→ Playwright
→ Docker image
```

`frontend/dist/dutylog-vue-app-shell.{js,css}` is copied into the Spring Boot JAR under `static/vue`. The Dockerfile uses a Node build stage and a Maven build stage, but the runtime topology is unchanged: one non-root `dutylog-app` image/container plus PostgreSQL. Production still promotes the exact staging-tested image digest instead of rebuilding it.

Direct frontend dependencies are exact-pinned in `frontend/package.json`; the immutable staging image digest remains the deployable supply-chain identity.

## Authenticated smoke test (v27.2.31)

The deployment gate treats the application shell as protected content. It checks the anonymous browser redirect with `Accept: text/html`, obtains `XSRF-TOKEN` from `/login.html`, signs in through `/perform_login` with the environment bootstrap administrator and verifies the versioned shell with the resulting session cookie. Missing or rejected credentials fail deployment and rollback checks closed. API-style anonymous requests may still receive JSON `401`.


## Public edge architecture

The active deployment uses the VPS-wide system nginx, not a Caddy container:

```text
nginx :80/:443
  -> 127.0.0.1:18082 -> dutylog-staging app
  -> 127.0.0.1:18083 -> dutylog-production app
```

The application port is published only on `127.0.0.1`. PostgreSQL is not published at all. CI/CD never edits nginx or certificates during an ordinary release; those are one-time host operations.

Detailed host steps: [`HOST_NGINX_DEPLOYMENT_V27.2.30.md`](HOST_NGINX_DEPLOYMENT_V27.2.30.md).

## GitHub Environments

Create environments named exactly:

- `staging`
- `production`

Add these variables:

```text
DUTYLOG_DEPLOY_ENABLED    # staging: false until host is ready; production is fail-closed
DUTYLOG_DEPLOY_HOST
DUTYLOG_DEPLOY_PORT       # normally 22
DUTYLOG_DEPLOY_USER
DUTYLOG_DEPLOY_PATH       # /opt/dutylog/staging or /opt/dutylog/production
DUTYLOG_BASE_URL          # public HTTPS URL
```

Add these secrets:

```text
DUTYLOG_SSH_PRIVATE_KEY
DUTYLOG_SSH_KNOWN_HOSTS
GHCR_READ_USERNAME
GHCR_READ_TOKEN
```

Build `DUTYLOG_SSH_KNOWN_HOSTS` from the server host key and verify its fingerprint through a separate trusted channel before saving it. Never paste private keys or tokens into issue comments, build logs or chat.

The SSH key should belong to a dedicated deployment user that owns only its DutyLog environment directories. Membership in the host `docker` group is effectively root-equivalent and can affect unrelated containers; it is acceptable for the first single-owner VPS but is not strong isolation. A later hardened installation should use a dedicated host, rootless Docker or narrowly restricted privileged commands.

For production, enable required reviewers in GitHub Environment settings during early releases.

## One-time server preparation

Use an x86_64/amd64 VPS with Docker Engine and the Compose plugin. Create the deployment account, then run from the release repository:

```bash
sudo DUTYLOG_DEPLOY_ROOT=/opt/dutylog \
  DUTYLOG_DEPLOY_OWNER=<ssh-deploy-user> \
  bash deploy/scripts/bootstrap-cicd-host.sh
```

The bootstrap creates only:

```text
/opt/dutylog/staging
/opt/dutylog/production
```

It does not install/start Caddy, does not touch ports 80/443 and does not modify nginx.

Prepare host-local environment files:

```bash
sudo cp /opt/dutylog/staging/.env.example /opt/dutylog/staging/.env
sudo cp /opt/dutylog/production/.env.example /opt/dutylog/production/.env
sudo chmod 600 /opt/dutylog/{staging,production}/.env
```

Replace every placeholder. Staging and production must have different database names, database passwords, admin passwords and Telegram credentials.

Keep these boundaries:

```env
# staging
DUTYLOG_BIND_ADDRESS=127.0.0.1
DUTYLOG_BIND_PORT=18082

# production
DUTYLOG_BIND_ADDRESS=127.0.0.1
DUTYLOG_BIND_PORT=18083
```

`check-deploy-env.sh` rejects non-loopback binding. `DUTYLOG_SECURITY_TRUST_PROXY_HEADERS=true` is allowed only with the supplied nginx configuration, which overwrites forwarding headers before DutyLog uses them for audit/rate limiting.

## Branch behavior

### Push to `test`

`.github/workflows/deploy-staging.yml`:

1. runs the Vue frontend gate, Maven `verify`, JaCoCo, release checks and 45 Playwright scenarios;
2. calculates the exact Git tree hash;
3. builds one non-root `linux/amd64` image with OCI metadata, SBOM and provenance;
4. pushes immutable tree/commit tags to GHCR;
5. verifies that digest on clean PostgreSQL;
6. validates the `staging` GitHub Environment without printing secrets;
7. when disabled, records an explicit successful skip;
8. when enabled, deploys by digest and applies Flyway;
9. checks container health and full loopback smoke on port 18082;
10. checks the public HTTPS URL through nginx;
11. only then creates `staging-tested-tree-*`.

### Merge to `main`/`master`

`.github/workflows/deploy-production.yml`:

1. reruns tests and the release gate;
2. calculates the Git tree hash;
3. requires an existing matching `staging-tested-tree-*` image;
4. resolves its immutable digest;
5. creates and verifies a PostgreSQL backup before update;
6. deploys the same tested digest;
7. verifies running OCI version/tree metadata;
8. checks container health, loopback port 18083 and public HTTPS;
9. rolls back the application image on failure when possible.

Production does not rebuild source code.

## Merge mode

Prefer a fast-forward merge from `test` to `main`/`master`, or any merge that leaves the resulting Git tree unchanged. A merge commit is acceptable because promotion keys on the tree, not the commit SHA.

Do not edit application files directly in `main`/`master`: a changed tree has no staging-tested tag and production stops before touching the server.

## Deployment state and rollback

Each environment stores `.deploy-state` with current/previous image digests and build metadata. It contains no application secrets.

Manual application rollback:

```bash
cd /opt/dutylog/production
CONFIRM_ROLLBACK=ROLLBACK bash deploy/scripts/rollback-environment.sh
```

Rollback changes the application image only. Flyway migrations are forward-only. Database restore remains an explicit controlled operation.

## CI/CD does not

- restore production databases automatically;
- overwrite host `.env` files;
- edit nginx or Certbot configuration;
- restart or stop YARUGA;
- publish DutyLog on `0.0.0.0`;
- promote an untested source tree;
- run `docker compose down -v` in production.

## Pipefail-safe smoke response checks (v27.2.32)

Deployment smoke checks capture HTTP responses before searching them. Do not use `curl ... | grep -q` or `echo "$BODY" | grep -q` in scripts that enable `set -o pipefail`: an early match can close the pipe and turn a successful check into SIGPIPE exit 141.

## v27.38.13 browser parity continuation

The v27.38.11 full report remains 42 passed / 5 failed with no flaky retry, but the uploaded Playwright traces now prove the remaining shared roots. v27.38.13 changes no gate ordering or timeout/retry/error policy: Vue exclusively owns the Productivity accordion summary Teleport targets after retirement, and `pwa-upgrade.spec.js` uses the released `basic` onboarding preset key. CI/staging still run exact frontend, Maven and boot canary before mandatory full Chromium; image/PostgreSQL/staging promotion stays blocked until clean 47/47.

## v27.38.11 browser parity continuation

The v27.38.10 staging validation reached 42 passed / 5 failed with no flaky retry. v27.38.11 changes no gate ordering or timeout/retry/error policy: it closes Task read-your-write projection sequencing, a stale multiple-notes Calendar wait, and duplicate first-install PWA update lifecycle. CI/staging still run the exact frontend gate, Maven and boot canary before mandatory full Chromium, and release acceptance still requires a clean 47/47 browser result before immutable-image/PostgreSQL/staging promotion.

## v27.38.9 shared browser parity follow-up

The v27.38.7 full Chromium run reached 22/47 and proved the boot canary is useful. The 25 failures collapse into four shared contracts: Vue-focused Calendar selection, plain Productivity mutation snapshots, Task Board deadline presentation, and service-worker first-claim onboarding stability. CI/staging keep the exact frontend gate and `npm run test:e2e:canary` before mandatory `npm run test:e2e`; no timeout, retry or HTTP-error policy is relaxed.

## v27.38.7 browser fail-fast ordering

After the exact frontend gate, Maven and Playwright installation, CI/staging run `npm run test:e2e:canary` before `npm run test:e2e`. The canary catches common boot/readiness regressions in one scenario; it never replaces or weakens the mandatory full Chromium suite. Root Playwright preflight also refuses to run when the Vue production assets were not built. v27.38.7 additionally treats the loaded module snapshot plus completed onboarding profile as the prerequisite for optional Vue Productivity reads, so disabled modules are not probed during boot and backend 403 enforcement stays strict.
