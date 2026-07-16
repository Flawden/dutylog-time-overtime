# Production runbook

Status: v27.2.5.

## Normal release

1. Merge completed work into `test`.
2. Wait for `Deploy staging` to finish.
3. Test the staging site and persistent staging migration.
4. Merge the same tree into the repository's production branch (`main` or `master`).
5. Approve the protected `production` GitHub Environment when approval is enabled.
6. Confirm backup, health and smoke steps are green.

Production is never rebuilt independently. It pulls the staging-tested image digest.

## Check production

```bash
cd /opt/dutylog/production
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production ps
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs --tail=200 app
bash deploy/scripts/smoke-test.sh https://app.dutylog.example.com
```

## Backup

```bash
cd /opt/dutylog/production
bash deploy/scripts/backup-postgres.sh
bash deploy/scripts/list-backups.sh
```

Copy recent backups off the VPS.

## Application rollback

```bash
cd /opt/dutylog/production
CONFIRM_ROLLBACK=ROLLBACK bash deploy/scripts/rollback-environment.sh
```

This changes the application image only. It does not reverse Flyway.

## Database restore

Restore is manual and should first be rehearsed on staging:

```bash
cd /opt/dutylog/production
CONFIRM_RESTORE=RESTORE bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

Do not automate a database restore after deployment failure. It can destroy writes made after the backup.

## Logs

```bash
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs -f app
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs -f db
```

The shared edge proxy lives in `/opt/dutylog/edge`.
