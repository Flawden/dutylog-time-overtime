# Production runbook

Status: v27.5.0.

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
DUTYLOG_ENV_FILE=.env bash deploy/scripts/local-smoke-test.sh
bash deploy/scripts/smoke-test.sh https://dutylog.yaruga-trophy.ru
```

## Backup and restore drill

```bash
cd /opt/dutylog/production
DUTYLOG_ENV_FILE=.env bash deploy/scripts/backup-postgres.sh
DUTYLOG_ENV_FILE=.env bash deploy/scripts/check-backup-freshness.sh
DUTYLOG_ENV_FILE=.env bash deploy/scripts/restore-drill.sh
bash deploy/scripts/list-backups.sh
```

Install the daily timer only after one successful manual run:

```bash
sudo bash deploy/scripts/install-backup-timer.sh \
  /opt/dutylog/production dutylog-deploy '*-*-* 03:30:00'
```

Copy recent backups off the VPS. Local retention alone does not protect against VPS loss.

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
CONFIRM_RESTORE=RESTORE DUTYLOG_ENV_FILE=.env bash deploy/scripts/restore-postgres.sh backups/<file>.dump
```

Do not automate a database restore after deployment failure. It can destroy writes made after the backup.

## Logs

```bash
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs -f app
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs -f db
```

Public HTTPS is handled by the VPS-wide system nginx. Ordinary deployments do not modify nginx, certificates or YARUGA.
