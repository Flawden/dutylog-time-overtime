# Production launch

Status: v26.6.5.

This is the short first-launch procedure for DutyLog on a VPS. For daily operations and rollback, use `docs/PRODUCTION_RUNBOOK.md`.

## Success criteria

- App opens on HTTPS domain.
- Bootstrap admin from `.env` can log in.
- Public registration creates only `USER`.
- Admin sees `Система`.
- Regular user does not see `Система`.
- `/actuator/health` returns `UP`.
- `./deploy/scripts/smoke-test.sh https://domain` passes.
- PostgreSQL backup is created and copied outside the VPS.
- PWA/offline smoke works.
- Telegram is checked if enabled, or explicitly disabled.

## 1. Prepare VPS directory

```bash
sudo mkdir -p /opt/dutylog
sudo chown -R "$USER":"$USER" /opt/dutylog
cd /opt/dutylog
```

```bash
git clone <repo-url> .
git checkout v26.6.5
```

Check that the domain points to the server:

```bash
getent hosts dutylog.example.com
```

## 2. Create production env

```bash
cp .env.production.example .env
nano .env
```

Minimum values to replace:

```env
DUTYLOG_DOMAIN=dutylog.example.com
POSTGRES_PASSWORD=<long-random-password>
SPRING_DATASOURCE_PASSWORD=<long-random-password>
DUTYLOG_ADMIN_USERNAME=<admin-login>
DUTYLOG_ADMIN_PASSWORD=<long-random-admin-password>
DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=false
```

Keep Telegram disabled until configured:

```env
DUTYLOG_TELEGRAM_ENABLED=false
DUTYLOG_TELEGRAM_POLLING_ENABLED=false
```

## 3. Prepare Caddy

```bash
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
```

The default Caddy config uses `DUTYLOG_DOMAIN`, HTTPS and basic security headers. Caddy does not provide rate limiting out of the box; for public deployments choose nginx rate limit, fail2ban or a Caddy plugin.

## 4. Preflight

```bash
./deploy/scripts/check-production-env.sh
./deploy/scripts/release-check.sh
```

Fix errors before launch.

## 5. First start

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Check containers and logs:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 app
docker compose -f docker-compose.prod.yml logs --tail=100 caddy
```

## 6. Smoke test

```bash
./deploy/scripts/smoke-test.sh https://dutylog.example.com
```

## 7. First login

Open:

```text
https://dutylog.example.com
```

Log in with the bootstrap admin from `.env`. Then check:

- `Система` is visible;
- server version is `26.6.5`;
- database status is `ok`;
- public registration has the expected status;
- module settings open;
- onboarding does not appear for existing users and appears for new users as expected.

## 8. Backup immediately after launch

```bash
./deploy/scripts/backup-postgres.sh
./deploy/scripts/list-backups.sh
```

Copy the backup outside the VPS:

```bash
scp /opt/dutylog/backups/dutylog-*.dump user@other-host:/safe/place/
```

## 9. PWA/offline smoke

- Open the site on a phone.
- Log in.
- Open calendar and wait for data.
- Install as PWA if offered.
- Disable network.
- Reload.
- Confirm calendar opens from local snapshot.
- Change a note.
- Enable network and confirm sync.

## 10. Telegram smoke if enabled

Create a link code in DutyLog profile and send the bot:

```text
/start DL-XXXXXX
```

Check:

```text
/today
/tasks
/balance
/help
```
