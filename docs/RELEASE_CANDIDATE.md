# v27.0-rc1 — Release Candidate

Status: v27.0-rc1.

This document freezes the current release candidate scope. No new product features should be added before the stable v27.0 release.

## Scope

Included in this RC:

- calendar, shift types and schedules;
- overtime, time off and FIFO balance;
- notes, tasks and important dates;
- notifications and Telegram integration;
- module-aware UI and backend guards;
- first-run onboarding;
- RU/EN web/PWA interface;
- themes, accent color and day emoji markers;
- admin users/roles/registration/diagnostics;
- local-first lite offline mode;
- security headers, production session-cookie hardening and release guardrails;
- GitHub Actions CI and release static checks.

Not included before stable v27.0:

- new analytics screens;
- paid-plan logic beyond the existing read-only account tier field;
- Google Calendar or external calendar integration;
- native mobile app;
- new Telegram commands;
- new charting/reporting modules.

## Acceptance checklist

Before marking v27.0 stable:

```bash
mvn -B --no-transfer-progress test
bash deploy/scripts/release-check.sh
```

On the deployment host:

```bash
./deploy/scripts/check-production-env.sh
./deploy/scripts/smoke-test.sh https://your-domain.example
```

Manual smoke:

- register a new user;
- verify login-language selection is preserved during onboarding;
- choose each onboarding preset and verify the active preset highlight;
- mark shifts on several days;
- add a note, task, important date and overtime entry;
- switch RU/EN and check dynamic settings/cards;
- disable and re-enable modules, confirming data is not deleted;
- test the hidden-blocks hint dismissal;
- log out and log back in;
- check admin system page, users, registration and diagnostics;
- create a PostgreSQL backup and verify restore procedure on a safe copy;
- run a basic PWA/offline reload and sync scenario.

## Decision rule

- No blockers found: tag stable `v27.0`.
- Minor non-blocking issues: document them and keep v27.0 if acceptable.
- Any startup, data-loss, security, migration, login, backup or offline-sync blocker: fix as `v27.0-rc2`.
