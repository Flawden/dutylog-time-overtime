# VPS checklist

## Before launch

- [ ] VPS is created and accessible by SSH.
- [ ] Domain points to the VPS IP.
- [ ] Docker is installed.
- [ ] Project is cloned to `/opt/dutylog` or another stable directory.
- [ ] `.env` is created from `.env.example`.
- [ ] Production passwords are changed.
- [ ] Telegram token is added if Telegram is enabled.
- [ ] `docker compose up -d --build` starts both `db` and `app`.
- [ ] `docker compose logs -f app` shows no startup errors.
- [ ] Application opens through HTTPS.
- [ ] First user is created and has administrator access.
- [ ] `Система` page is visible to administrator.
- [ ] `/api/admin/status` returns healthy status for database.
- [ ] Telegram linking works with `/start DL-XXXXXX`.
- [ ] Test notification works.

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
- [ ] Check logs.
- [ ] Check admin system status.
- [ ] Check Telegram bot.

## Commands

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
git pull
docker compose up -d --build
docker compose logs -f app
```
