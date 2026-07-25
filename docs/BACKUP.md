# PostgreSQL backup operations

Status: v27.7.0.

> The active private-beta environment is currently `/opt/dutylog/staging`; production examples remain for the future dedicated server. Staging already uses the same backup, checksum, freshness and isolated restore-drill tooling.

The canonical recovery guide is [`BACKUP_RESTORE_OPERATIONS_V27.5.0.md`](BACKUP_RESTORE_OPERATIONS_V27.5.0.md).

Create and verify a custom-format dump:

```bash
cd /opt/dutylog/production
DUTYLOG_ENV_FILE=.env bash deploy/scripts/backup-postgres.sh
```

Check backup age, SHA-256 and archive readability:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/check-backup-freshness.sh
```

Rehearse recovery without modifying the live database:

```bash
DUTYLOG_ENV_FILE=.env bash deploy/scripts/restore-drill.sh
```

Real restore only with explicit confirmation:

```bash
CONFIRM_RESTORE=RESTORE DUTYLOG_ENV_FILE=.env \
  bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

The real restore creates a pre-restore backup by default and restarts an application that was running even when restore fails. Keep at least one recent encrypted copy outside the VPS. Never commit database dumps and never use `down -v` in production.
