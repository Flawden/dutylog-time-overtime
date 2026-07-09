# Backup and restore

Status: v27.0-rc1.

DutyLog production data is stored in PostgreSQL. Backups should be created before every update and copied outside the VPS.

## Create backup

```bash
./deploy/scripts/backup-postgres.sh
./deploy/scripts/list-backups.sh
```

Backups are stored in `./backups` by default.

## Copy backup off the VPS

Example:

```bash
scp /opt/dutylog/backups/dutylog-*.dump user@backup-host:/safe/place/
```

## Restore

Restore only when you understand that the current database may be replaced.

```bash
./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

After restore:

```bash
docker compose -f docker-compose.prod.yml restart app
./deploy/scripts/smoke-test.sh https://your-domain.example
```

## Restore test policy

Before stable release, test restore on a safe copy or staging machine at least once. A backup that was never restored is only a hope, not a recovery plan.

## Safety notes

- Do not commit backups to Git.
- Do not store backups only on the same VPS.
- Keep at least one recent backup outside the server.
- Avoid `docker compose down -v` unless data deletion is intentional.
