# Security checklist

This checklist is for a personal production deployment of DutyLog.

## Secrets

- [ ] `.env` is not committed to Git.
- [ ] `.env.production.example` contains only placeholders.
- [ ] `./deploy/scripts/check-production-env.sh` passes before public launch.
- [ ] PostgreSQL password is long and unique.
- [ ] Telegram bot token is not shown in screenshots, logs or diagnostics.
- [ ] Backups are not committed to Git.

## Server

- [ ] SSH uses a strong password or, preferably, keys.
- [ ] Only required ports are open: `22`, `80`, `443`.
- [ ] PostgreSQL is not exposed to the public internet.
- [ ] DutyLog app port `8080` is not exposed publicly in production compose.
- [ ] HTTPS is enabled through Caddy or nginx.

## Application

- [ ] First account is the expected administrator.
- [ ] `Система` is visible only to administrator.
- [ ] Regular user receives `403` from `/api/admin/status`.
- [ ] CSRF cookie is present in the web interface.
- [ ] Changing password revokes mobile sessions.
- [ ] `/actuator/health` is public, but detailed diagnostics are not public.
- [ ] `/actuator/info` contains only non-secret app metadata.

## Docker

- [ ] Use `docker-compose.prod.yml` on the VPS.
- [ ] `docker compose down` is used for safe stop.
- [ ] `docker compose down -v` is avoided unless database deletion is intentional.
- [ ] App container healthcheck is healthy.
- [ ] Database container healthcheck is healthy.

## Backup

- [ ] `./deploy/scripts/backup-postgres.sh` creates a backup.
- [ ] Restore was tested at least once.
- [ ] Daily backup timer is configured if needed.
- [ ] At least one recent backup is stored outside the VPS.

## Updates

- [ ] Backup is created before every update.
- [ ] Git tag is known before deployment.
- [ ] Smoke test passes after update.
- [ ] Logs are checked after update.
