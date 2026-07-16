# Production deployment

Status: v27.2.4.

The preferred production path is the branch-based CI/CD pipeline in `docs/CICD.md`. Manual source builds remain available only for emergency/local administration.

## One-time preparation

1. Install Docker Engine and Docker Compose plugin.
2. Point production and staging DNS records to the VPS.
3. Run `deploy/scripts/bootstrap-cicd-host.sh`.
4. Create separate `.env` files for edge, staging and production.
5. Start the shared Caddy edge proxy.
6. Configure GitHub Environments and SSH/GHCR secrets.
7. Protect `main`/`master` and optionally require approval for the `production` environment.

## Normal deployment

```text
push/merge to test -> automatic staging deployment
verify staging      -> merge test into main/master
main                 -> backup + promotion of the same image digest
```

Production receives no source checkout and does not compile Maven. It pulls the same GHCR digest that already passed staging.

## Emergency manual checks

On the server:

```bash
cd /opt/dutylog/production
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production ps
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs --tail=200 app
bash deploy/scripts/smoke-test.sh "$(grep '^DUTYLOG_BASE_URL=' .env | cut -d= -f2-)"
```

## Backup

Production deployment performs a verified custom-format backup before updating an existing database. Keep additional off-host backups. A backup on the same VPS does not protect against disk loss.

## Rollback

```bash
cd /opt/dutylog/production
CONFIRM_ROLLBACK=ROLLBACK bash deploy/scripts/rollback-environment.sh
```

Application rollback does not reverse Flyway. See `docs/MIGRATION_SAFETY.md` before any destructive migration.
