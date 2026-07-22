# v27.5.0 — Backup and recovery hardening

Status: production-readiness infrastructure release.

## What this release proves

DutyLog PostgreSQL backups are not considered usable merely because a `.dump` file exists. The supported cycle is:

1. create a custom-format dump;
2. parse it with `pg_restore --list`;
3. publish it atomically with mode `0600`;
4. create and verify SHA-256;
5. restore it into an isolated PostgreSQL 16 container;
6. verify public tables and the latest successful Flyway migration;
7. remove the temporary container and volume;
8. confirm the live DutyLog environment was not modified.

The first staging drill completed with 19 matching public tables and Flyway V25.

## Manual backup

Run as the unprivileged deployment user:

```bash
cd /opt/dutylog/staging
sudo -u dutylog-deploy env \
  DUTYLOG_ENV_FILE=/opt/dutylog/staging/.env \
  bash deploy/scripts/backup-postgres.sh
```

The script now defaults to `deploy/compose/docker-compose.deploy.yml`, prevents concurrent backup writers with `flock`, verifies the archive, creates SHA-256 and rotates old dumps according to `BACKUP_KEEP_LAST`.

## Backup health

```bash
cd /opt/dutylog/staging
sudo -u dutylog-deploy env \
  DUTYLOG_ENV_FILE=/opt/dutylog/staging/.env \
  bash deploy/scripts/check-backup-freshness.sh
```

A healthy result starts with:

```text
BACKUP_HEALTHY
```

Default maximum age is 30 hours. Configure it with:

```env
BACKUP_MAX_AGE_HOURS=30
DUTYLOG_BACKUP_REQUIRE_CHECKSUM=true
DUTYLOG_BACKUP_VALIDATE_ARCHIVE=true
```

## Isolated restore drill

The drill does not call the destructive environment restore script. It creates a temporary PostgreSQL container with no network and no published ports:

```bash
cd /opt/dutylog/staging
sudo -u dutylog-deploy env \
  DUTYLOG_ENV_FILE=/opt/dutylog/staging/.env \
  bash deploy/scripts/restore-drill.sh
```

Pass an explicit dump when needed:

```bash
bash deploy/scripts/restore-drill.sh backups/dutylog-YYYY-MM-DDTHH-MM-SSZ.dump
```

A successful run prints `RESTORE DRILL PASSED`.

The script always attempts to remove only resources whose generated names begin with:

```text
dutylog-restore-drill-
dutylog_restore_drill_
```

A row-count difference from the live database is a warning by default because live data can change after a backup. To require an exact match during a quiet maintenance window:

```bash
DUTYLOG_DRILL_REQUIRE_SOURCE_MATCH=true bash deploy/scripts/restore-drill.sh
```

## Real environment restore

Real restore replaces the selected environment database and requires explicit confirmation:

```bash
cd /opt/dutylog/staging
CONFIRM_RESTORE=RESTORE \
DUTYLOG_ENV_FILE=.env \
  bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

Safety properties:

- checksum is required by default;
- a verified pre-restore backup is created unless explicitly disabled;
- the application is stopped before replacement;
- an EXIT recovery trap restarts an application that was running even when `pg_restore` fails;
- the custom dump restore uses `--single-transaction`.

Never run a real restore merely because an application deployment failed. Application rollback and database restore solve different problems.

## Daily systemd timer

Install the timer for staging:

```bash
cd /opt/dutylog/staging
sudo bash deploy/scripts/install-backup-timer.sh \
  /opt/dutylog/staging \
  dutylog-deploy \
  '*-*-* 03:30:00'
```

The generated unit names are environment-specific:

```text
dutylog-staging-backup.service
dutylog-staging-backup.timer
```

Inspect them with:

```bash
systemctl status dutylog-staging-backup.timer --no-pager -l
systemctl list-timers dutylog-staging-backup.timer --no-pager
journalctl -u dutylog-staging-backup.service -n 100 --no-pager
```

Trigger one manual timer service run:

```bash
systemctl start dutylog-staging-backup.service
systemctl status dutylog-staging-backup.service --no-pager -l
```

## Retention and off-site copies

`BACKUP_KEEP_LAST` protects disk space but is not an off-site backup. A dump stored only under `/opt/dutylog/.../backups` is lost together with the VPS disk. Before production launch, keep at least one recent encrypted copy outside this VPS and test that the copied file still passes SHA-256 validation.
