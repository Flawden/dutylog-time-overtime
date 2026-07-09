# VPS checklist

## Before launch

- [ ] VPS is created and accessible by SSH.
- [ ] Domain A-record points to the VPS IP.
- [ ] Docker and Docker Compose plugin are installed.
- [ ] Git is installed.
- [ ] Firewall allows only required public ports: `22`, `80`, `443`.
- [ ] Project is cloned to `/opt/dutylog` or another stable directory.
- [ ] `.env` is created from `.env.production.example`.
- [ ] Production passwords are changed.
- [ ] `DUTYLOG_ADMIN_USERNAME` and `DUTYLOG_ADMIN_PASSWORD` are set for the bootstrap administrator.
- [ ] `DUTYLOG_DOMAIN` is set correctly.
- [ ] Telegram token is added if Telegram is enabled.
- [ ] `deploy/caddy/Caddyfile` is created from the example.
- [ ] `./deploy/scripts/check-production-env.sh` passes or all warnings are understood.

## First start

- [ ] `docker compose -f docker-compose.prod.yml up -d --build` starts `db`, `app`, and `caddy`.
- [ ] `docker compose -f docker-compose.prod.yml ps` shows healthy/running containers.
- [ ] `docker compose -f docker-compose.prod.yml logs -f app` shows no startup errors.
- [ ] Application opens through HTTPS.
- [ ] `./deploy/scripts/smoke-test.sh https://domain` passes and confirms current static assets.
- [ ] Bootstrap admin from `.env` can log in.
- [ ] Publicly registered user does not have administrator access.
- [ ] `Система` → `Пользователи и роли` shows bootstrap env admin and expected admin count.
- [ ] `Система` page is visible to administrator and shows server version `26.6.2`.
- [ ] `/api/admin/status` returns healthy database status for administrator.
- [ ] Regular user does not see `Система`.
- [ ] Telegram linking works with `/start DL-XXXXXX` if Telegram is enabled.
- [ ] Test notification works if notifications are enabled.

## Backup readiness

- [ ] `./deploy/scripts/backup-postgres.sh` creates a `.dump` file.
- [ ] Backup directory is not committed to Git.
- [ ] Backup is copied outside the VPS.
- [ ] Daily backup timer is configured if needed.
- [ ] Restore procedure has been tested on a non-production database.

## Update routine

- [ ] Create backup.
- [ ] Pull new Git version.
- [ ] Build and restart containers.
- [ ] Run smoke test.
- [ ] Check app logs.
- [ ] Check admin system status.
- [ ] Check Telegram bot.

## Commands

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
git pull
docker compose -f docker-compose.prod.yml up -d --build
./deploy/scripts/smoke-test.sh https://your-domain.example
docker compose -f docker-compose.prod.yml logs -f app
```
