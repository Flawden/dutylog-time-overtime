# v27.0-rc4 — Security consolidation

Status: v27.0-rc4.

This release candidate freezes the current product scope and consolidates the notes-export and security review work. No new product features should be added before stable v27.0.

## Scope

Included in this RC:

- calendar, shifts, overtime/time off and FIFO balance;
- notes, tasks, important dates, notifications and Telegram;
- owner-scoped ZIP export of all non-empty Markdown notes;
- module-aware UI/backend guards and first-run onboarding;
- RU/EN web/PWA interface and personalization;
- admin users, roles, registration and diagnostics;
- local-first lite offline mode;
- split browser-session and stateless bearer-only mobile security chains;
- bounded app-level authentication rate limiting;
- structured security-event logging and rolling production logs;
- CSP without inline-script permission;
- owner/IDOR, export, rate-limit and mobile-boundary regressions;
- Dependabot and non-root application container;
- GitHub Actions CI and release static checks.

Not included before stable v27.0:

- new analytics or paid-plan logic;
- external calendar integrations;
- native mobile app UI;
- new Telegram commands;
- multi-instance infrastructure;
- MFA/account lockout;
- live penetration testing or DAST automation.

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

- register a new user and verify the selected login language reaches onboarding;
- verify production registration starts closed unless explicitly opened by an admin;
- choose each onboarding preset;
- create shifts, a note, task, important date and overtime entry;
- download the notes ZIP and open it in Obsidian or inspect the Markdown files;
- verify the ZIP contains only the current user's notes;
- switch RU/EN and inspect dynamic settings/cards;
- disable/re-enable modules and confirm data is preserved;
- test hidden-block hint dismissal;
- log out/in and verify password/session behavior;
- verify a browser session cannot authenticate `/api/mobile/**`;
- verify a valid mobile bearer token can use mobile/shared APIs;
- check admin users, registration and diagnostics;
- create a PostgreSQL backup and rehearse restore on a safe copy;
- run a basic PWA/offline reload and sync scenario;
- inspect `SECURITY_AUDIT` / request logs for expected events without secrets.

## Decision rule

- No blockers: tag stable `v27.0`.
- Minor non-blocking issues: document them and continue RC observation.
- Any startup, data-loss, security, migration, login, export, backup or offline-sync blocker: fix as `v27.0-rc5`.
