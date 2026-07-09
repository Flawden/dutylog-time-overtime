# Production runbook

This runbook is the practical checklist for launching and maintaining DutyLog on a VPS.

## 1. First launch on a clean VPS

Recommended directory:

```bash
sudo mkdir -p /opt/dutylog
sudo chown -R "$USER":"$USER" /opt/dutylog
cd /opt/dutylog
```

Clone the repository:

```bash
git clone <repo-url> .
```

Create production environment file:

```bash
cp .env.production.example .env
nano .env
```

Minimum values to replace:

```env
DUTYLOG_DOMAIN=dutylog.example.com
POSTGRES_PASSWORD=...
SPRING_DATASOURCE_PASSWORD=...
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
DUTYLOG_TELEGRAM_BOT_TOKEN=...
```

If Telegram is not ready yet, keep it disabled:

```env
DUTYLOG_TELEGRAM_ENABLED=false
DUTYLOG_TELEGRAM_POLLING_ENABLED=false
```

Copy Caddy config:

```bash
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
```

Run production preflight:

```bash
./deploy/scripts/check-production-env.sh
```

Start production stack:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Check status:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app
```

Open:

```text
https://your-domain.example
```

The administrator is no longer selected by “first registration”. Set it explicitly in `.env` before startup:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
```

The backend creates this account on first startup or promotes it to `ADMIN` if it already exists. Since v22.3 normal restart keeps the current password; set `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true` only for emergency recovery. Every public registration creates a regular `USER`.

## 2. Post-launch checks

Run smoke test. In v26.4 it verifies split static asset versions and service worker cache version:

```bash
DUTYLOG_BASE_URL=https://your-domain.example ./deploy/scripts/smoke-test.sh
```

Then check manually:

- registration and login;
- calendar opens;
- admin sees `Система` in the header;
- `Система` shows database status `ok`;
- regular user does not see `Система`;
- Telegram linking works if Telegram is enabled;
- backup script creates a dump.

## 3. Safe update

Always create a backup before updating:

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
git pull
docker compose -f docker-compose.prod.yml up -d --build
./deploy/scripts/smoke-test.sh https://your-domain.example
```

Check logs:

```bash
docker compose -f docker-compose.prod.yml logs --tail=200 app
```

## 4. Rollback

Code rollback without database rollback can fail if the new version has already applied migrations. The safest rollback is:

1. checkout the previous Git tag;
2. restore a database backup from the same period;
3. rebuild containers.

Example:

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
git checkout <previous-good-tag>
./deploy/scripts/restore-postgres.sh backups/dutylog-before-update.dump
docker compose -f docker-compose.prod.yml up -d --build
```

For small frontend-only fixes, a code-only rollback may be enough:

```bash
git checkout <previous-good-tag>
docker compose -f docker-compose.prod.yml up -d --build
```

## 5. Emergency backup

If something looks wrong but the database is still reachable, take a backup before touching anything:

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
```

Then copy it outside the VPS:

```bash
scp /opt/dutylog/backups/dutylog-*.dump user@other-host:/safe/place/
```

## 6. Logs

Application logs:

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

Database logs:

```bash
docker compose -f docker-compose.prod.yml logs -f db
```

Reverse proxy logs:

```bash
docker compose -f docker-compose.prod.yml logs -f caddy
```

## 7. Safe and dangerous commands

Safe stop:

```bash
docker compose -f docker-compose.prod.yml down
```

Dangerous stop:

```bash
docker compose -f docker-compose.prod.yml down -v
```

The `-v` flag removes Docker volumes and can delete PostgreSQL data.

## 8. Regular maintenance

Recommended routine:

- update OS packages;
- keep Docker images up to date;
- create backup before every DutyLog update;
- keep at least one backup outside the VPS;
- periodically test restore on a non-production database;
- check admin `Система` page after every update.
