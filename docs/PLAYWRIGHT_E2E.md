# Playwright browser E2E

DutyLog v27.2.27 keeps the Chromium regression layer introduced in v27.2.25 and completes the first accordion hardening pass. Shift chips expose `aria-pressed`, while Notes and Marker `<details>` sections are explicitly expanded before Playwright fills or verifies their controls.

## What it protects

The browser suite exercises the packaged web application through the same HTML, JavaScript, Spring Security session, CSRF token and HTTP endpoints used by a real user:

- registration and automatic login;
- login-language persistence;
- first-run onboarding presets;
- shift, emoji and Markdown-note persistence;
- navigation across month boundaries and a full page reload;
- enabling, disabling and restoring the Tasks module without losing task data;
- mobile viewport usability;
- service-worker shell and IndexedDB snapshot startup while offline;
- browser `console.error`, uncaught page errors, failed same-origin requests and unexpected same-origin HTTP `4xx/5xx` responses on normal happy paths.

The Java suite remains authoritative for business rules, ownership, validation, security and API envelopes. Playwright protects the wiring between the browser and those contracts.

## Local prerequisites

- JDK 17 or newer;
- Maven;
- Node.js 20 or newer.

Install the exact Playwright test dependency declared in `package.json`:

```bash
npm install --no-audit --no-fund
npx playwright install chromium
```

Run the headless suite:

```bash
npm run test:e2e
```

Playwright automatically starts DutyLog with the isolated `e2e` Spring profile on:

```text
http://127.0.0.1:4173
```

The E2E profile uses an in-memory H2 database, open disposable registration, disabled rate limiting and disabled Telegram network integration. It never touches `./data/shifts.mv.db`.

For an already running E2E server on port 4173, the local Playwright configuration reuses it instead of starting a second process.

## Debugging

```bash
npm run test:e2e:headed
npm run test:e2e:ui
npm run test:e2e:report
```

Failure output is written to:

```text
playwright-report/
test-results/
```

Traces, screenshots and videos are retained only for failed tests. GitHub Actions uploads both directories as the `playwright-report` artifact.

## Service-worker test

Most browser tests block service workers deliberately so cache state cannot hide a normal UI regression. `pwa-offline.spec.js` creates its own service-worker-enabled context and verifies the offline shell separately.

## CI order

The CI gate runs in this order:

1. `mvn verify` and JaCoCo thresholds;
2. release static checks;
3. Chromium Playwright E2E;
4. deployment-image build;
5. clean PostgreSQL migration/container smoke test.

A browser regression therefore blocks the immutable deployment image from being accepted.

## Windows line endings

The repository contains `.gitattributes` and keeps text files as LF so shell scripts remain executable in Linux CI. After pulling this release into an existing Windows checkout, normalize the index once:

```powershell
git add --renormalize .
git status
```

The previous `LF will be replaced by CRLF` warnings are not test failures, but normalization prevents local Git settings from silently rewriting deployment scripts.
