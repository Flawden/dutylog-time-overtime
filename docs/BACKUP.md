# PostgreSQL backup operations

Status: v27.2.1.

The canonical recovery guide is [`BACKUP_RESTORE.md`](BACKUP_RESTORE.md).

Production deploy creates a verified custom-format dump before every update. Manual backup:

```bash
cd /opt/dutylog/production
bash deploy/scripts/backup-postgres.sh
```

List dumps:

```bash
bash deploy/scripts/list-backups.sh
```

Restore only with explicit confirmation:

```bash
CONFIRM_RESTORE=RESTORE \
  bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

The restore script creates a pre-restore backup by default. Keep at least one recent copy outside the VPS. Never commit database dumps and never use `down -v` in production.
