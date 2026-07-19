# Staging environment

Status: v27.2.30.

Staging is disposable. Production is not.

The environments use separate Compose projects, PostgreSQL volumes, credentials, logs, administrators, backups and loopback ports:

```text
dutylog-staging      -> 127.0.0.1:18082
dutylog-production   -> 127.0.0.1:18083
```

System nginx terminates HTTPS and routes `stage.yaruga-trophy.ru` to staging. PostgreSQL is available only on the private per-environment Docker network.

## Deployment gate

`DUTYLOG_DEPLOY_ENABLED` is the explicit remote-deployment switch. Leave it unset or `false` while VPS, DNS, nginx, certificate, SSH credentials or host `.env` are incomplete. A push to `test` still runs Java tests, coverage, Playwright, image build and clean-PostgreSQL verification, but SSH deployment is skipped and no production-promotion tag is created.

Set it to `true` only after every value from `docs/CICD.md` is configured.

## Health sequence

A real staging deployment must pass:

```text
Docker health
-> http://127.0.0.1:18082 full smoke
-> https://stage.yaruga-trophy.ru full smoke
```

This makes nginx/DNS/TLS failures distinguishable from application failures.

## Reset staging

From `/opt/dutylog/staging`:

```bash
RESET_STAGING=RESET bash deploy/scripts/reset-staging.sh
```

This command refuses to run unless `DUTYLOG_ENVIRONMENT=staging`; the exact fail-closed message is `Refusing to reset a non-staging environment.` It removes staging Compose volumes and deployment state while preserving backup files. It cannot target production through a production `.env`.

Push `test` again to recreate the database from all Flyway migrations.

## Test data

Use synthetic users, shifts, notes, tasks and overtime entries. Keep public registration closed unless registration itself is under test. Never copy identifiable live production data into staging without access restriction and anonymization.
