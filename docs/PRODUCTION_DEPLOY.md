# Production deployment

Status: v27.2.30.

The preferred path is the branch-based CI/CD pipeline in [`CICD.md`](CICD.md). Production is deployed behind the same VPS-wide system nginx that already serves other sites. DutyLog never owns public ports 80/443.

## One-time preparation

1. Upgrade the shared VPS to at least 4 GiB RAM before running YARUGA, DutyLog staging and DutyLog production continuously together.
2. Install Docker Engine/Compose and create a dedicated deployment account.
3. Point `stage.yaruga-trophy.ru` and `dutylog.yaruga-trophy.ru` to the VPS.
4. Run `deploy/scripts/bootstrap-cicd-host.sh`.
5. Create separate staging/production `.env` files.
6. Install the supplied nginx site files and obtain independent Certbot certificates.
7. Configure GitHub Environments and SSH/GHCR credentials.
8. Protect `main`/`master` and require production approval during early releases.

## Normal release

```text
push/merge to test -> automatic staging deployment
verify staging      -> merge unchanged tree to main/master
approve production  -> backup + same tested image digest
```

Production receives no source build. It pulls the same GHCR digest that already passed staging.

## Runtime boundaries

```text
nginx -> 127.0.0.1:18083 -> DutyLog production
                              -> private PostgreSQL
```

The preflight rejects `0.0.0.0` binding, requires backup-before-deploy, closed registration and trusted proxy mode behind the supplied nginx configuration.

## Emergency checks

```bash
cd /opt/dutylog/production
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production ps
docker compose --env-file .env -f deploy/compose/docker-compose.deploy.yml -p dutylog-production logs --tail=200 app
DUTYLOG_ENV_FILE=.env bash deploy/scripts/local-smoke-test.sh
bash deploy/scripts/smoke-test.sh "$(grep '^DUTYLOG_BASE_URL=' .env | cut -d= -f2-)"
```

## Backup and rollback

Every production update creates and verifies a custom-format PostgreSQL dump before replacing the app container. Keep encrypted off-host copies; a backup on the same VPS does not protect against disk loss.

```bash
cd /opt/dutylog/production
CONFIRM_ROLLBACK=ROLLBACK bash deploy/scripts/rollback-environment.sh
```

Application rollback does not reverse Flyway migrations. Destructive migration recovery requires a controlled database restore.
