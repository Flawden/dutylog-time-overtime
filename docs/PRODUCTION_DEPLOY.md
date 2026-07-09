# Production deploy

Status: v27.0-rc1.

Use this guide for a VPS deployment. For detailed operations and rollback, see `docs/PRODUCTION_RUNBOOK.md`.

## Requirements

- Docker and Docker Compose plugin;
- a domain pointing to the VPS;
- ports 80 and 443 open;
- Git access to the repository;
- strong unique passwords for PostgreSQL and the bootstrap administrator.

## Prepare files

```bash
sudo mkdir -p /opt/dutylog
sudo chown -R "$USER":"$USER" /opt/dutylog
cd /opt/dutylog
git clone <repo-url> .
git checkout v27.0-rc1
cp .env.production.example .env
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
```

Edit `.env` and set at least:

```env
DUTYLOG_DOMAIN=dutylog.example.com
POSTGRES_PASSWORD=<long-random-password>
SPRING_DATASOURCE_PASSWORD=<same-or-dedicated-db-password>
DUTYLOG_ADMIN_USERNAME=<admin-login>
DUTYLOG_ADMIN_PASSWORD=<long-random-admin-password>
DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=false
```

Keep Telegram disabled until the bot is configured:

```env
DUTYLOG_TELEGRAM_ENABLED=false
DUTYLOG_TELEGRAM_POLLING_ENABLED=false
```

## Preflight

```bash
./deploy/scripts/check-production-env.sh
bash deploy/scripts/release-check.sh
```

Fix all errors before starting.

## Start

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Check:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 app
docker compose -f docker-compose.prod.yml logs --tail=100 caddy
```

## Smoke test

```bash
./deploy/scripts/smoke-test.sh https://dutylog.example.com
```

Then open the app, log in as the bootstrap admin and verify the System page.

## Update

Before every update:

```bash
./deploy/scripts/backup-postgres.sh
git fetch --tags
git checkout <new-tag>
docker compose -f docker-compose.prod.yml up -d --build
./deploy/scripts/smoke-test.sh https://dutylog.example.com
```

Do not run `docker compose down -v` unless deleting the database is intentional.
