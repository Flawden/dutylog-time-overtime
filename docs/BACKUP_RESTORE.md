# Backup and restore

Status: v27.2.5.

Production deployment creates a verified PostgreSQL custom-format dump before every application update. Staging backup is optional because staging is disposable.

## List backups

On the relevant environment directory:

```bash
cd /opt/dutylog/production
bash deploy/scripts/list-backups.sh
```

Backups are stored in `./backups` unless `BACKUP_DIR` says otherwise. Every new dump is parsed with `pg_restore --list` before deployment continues. A SHA-256 sidecar is written when `sha256sum` is available.

## Manual backup

```bash
cd /opt/dutylog/production
bash deploy/scripts/backup-postgres.sh
```

Keep recent copies outside the VPS. A dump on the same disk is not disaster recovery.

## Restore

Database restore is deliberately manual and is never performed by CI/CD.

```bash
cd /opt/dutylog/production
CONFIRM_RESTORE=RESTORE \
  bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

Before replacing the database, the script checks an adjacent SHA-256 sidecar when present, validates custom-format archives with `pg_restore --list`, and creates another verified `pre-restore` backup unless `SKIP_PRE_RESTORE_BACKUP=true` is explicitly set.

After restore:

```bash
bash deploy/scripts/smoke-test.sh https://app.dutylog.example.com
```

## Safety rules

- test restore on staging before relying on it in an incident;
- never commit dumps or copy personal production data into ordinary staging;
- never use `docker compose down -v` in production;
- application rollback does not reverse Flyway migrations;
- do not automate database restore after a failed deployment because it may discard new writes.
