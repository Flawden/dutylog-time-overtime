# Release hardening

Status: v27.1.0.

This document is the stabilization checklist for DutyLog before a public or semi-public VPS launch. The current goal is not to add features, but to make the existing product predictable, testable and recoverable.

## Release gate

Before creating an archive or Git tag, run:

```bash
./deploy/scripts/release-check.sh
```

The script checks:

- frontend version consistency;
- split static JS order and cache-busting versions;
- absence of legacy runtime `app.js` references;
- service worker cache version;
- Spring `info.app.version` consistency;
- smoke-test version expectations;
- JavaScript syntax;
- manifest JSON;
- `pom.xml` parsing;
- shell script syntax;
- HTML/JS id references;
- Java brace balance;
- Flyway migration sequence;
- basic production-compose safety;
- reverse-proxy hardening headers.

CI runs the same release check after `mvn test`.

## What must stay frozen

During stabilization, avoid adding new large product features. Allowed changes:

- bug fixes;
- security fixes;
- test coverage;
- release scripts;
- documentation;
- UX copy polish;
- code cleanup that does not change behavior.

Risky refactors should be small and reversible.

## Required checks before VPS update

```bash
./deploy/scripts/backup-postgres.sh
./deploy/scripts/check-production-env.sh
./deploy/scripts/release-check.sh
```

After update:

```bash
./deploy/scripts/smoke-test.sh https://your-domain.example
```

Then manually check:

- login/logout;
- admin `Система` page;
- module settings;
- calendar load;
- selected-day panel;
- offline diagnostics;
- backup creation;
- Telegram only if enabled.

## Version policy

For each release, keep these in sync:

- `src/main/resources/static/js/10-core.js` → `DUTYLOG_VERSION`;
- `src/main/resources/static/service-worker.js` → cache name;
- `src/main/resources/static/index.html` → static query versions;
- `application.properties` and `application-prod.properties` → `info.app.version`;
- `SystemController` must use `info.app.version`, not a hardcoded string;
- `deploy/scripts/smoke-test.sh` expectations;
- docs and `CHANGES.md`.

## Migration policy

Production uses Flyway and `ddl-auto=validate`. New DB changes must be added as new migration files only. Existing migration files must not be edited after release.

`release-check.sh` verifies that migration versions are unique and gapless.

## Security baseline

Before public launch:

- do not expose app port `8080` in production compose;
- expose only Caddy/nginx ports `80/443`;
- keep registration closed unless intentionally onboarding users;
- set unique long passwords for PostgreSQL and bootstrap admin;
- keep Telegram token out of screenshots/logs;
- run with HTTPS;
- keep backups outside the VPS.
