# Staging environment

Status: v27.2.28.

Staging is disposable. Production is not.

The environments use separate Compose projects, volumes, PostgreSQL databases, credentials, logs, administrators and application aliases:

```text
dutylog-staging      dutylog-production
staging PostgreSQL   production PostgreSQL
staging app logs     production app logs
```

The staging application never receives production database credentials. Do not copy a live production database into staging unless the copy is temporary, access-restricted and anonymized.

## Deployment gate

The GitHub Environment variable `DUTYLOG_DEPLOY_ENABLED` is the explicit remote-deployment switch. Leave it unset or `false` while the VPS, DNS, SSH credentials or host-local `.env` are not ready. A push to `test` still runs Java tests, coverage, Playwright, builds the immutable image and verifies clean PostgreSQL migrations, but remote SSH deployment is skipped successfully and no production-promotion tag is created.

Set it to `true` only after every variable and secret from `docs/CICD.md` is configured. Missing values then fail in a dedicated preflight step before any SSH connection.

## Reset staging

From `/opt/dutylog/staging`:

```bash
RESET_STAGING=RESET bash deploy/scripts/reset-staging.sh
```

This command refuses to run unless `DUTYLOG_ENVIRONMENT=staging`. It removes the staging Compose volumes and deployment state. Existing backup files are preserved. It cannot target production through a production `.env` file.

Push `test` again to recreate the database from Flyway V1 through the latest migration.

## Recommended test data

Use synthetic users, shifts, notes, tasks and overtime entries. Keep public registration closed unless registration itself is under test.

Staging should verify both:

- clean installation after a reset;
- upgrade of the persistent staging database from the previous build.

The reset script fails closed with `Refusing to reset a non-staging environment.` when pointed at production.
