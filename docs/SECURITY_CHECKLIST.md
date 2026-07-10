# Security checklist

This checklist is for a personal production deployment of DutyLog.

## Secrets

- [ ] `.env` is not committed to Git.
- [ ] `.env.production.example` contains only placeholders.
- [ ] `./deploy/scripts/check-production-env.sh` passes before public launch.
- [ ] PostgreSQL password is long and unique.
- [ ] `DUTYLOG_ADMIN_USERNAME` is set to the expected administrator login.
- [ ] `DUTYLOG_ADMIN_PASSWORD` is long, unique and not reused anywhere else.
- [ ] Telegram bot token is not shown in screenshots, logs or diagnostics.
- [ ] Backups are not committed to Git.

## Server

- [ ] SSH uses a strong password or, preferably, keys.
- [ ] Only required ports are open: `22`, `80`, `443`.
- [ ] PostgreSQL is not exposed to the public internet.
- [ ] DutyLog app port `8080` is not exposed publicly in production compose.
- [ ] HTTPS is enabled through Caddy or nginx.

## Application

- [ ] Public registration does not create administrators automatically.
- [ ] Production starts with `DUTYLOG_REGISTRATION_DEFAULT_ENABLED=false`; registration is opened only intentionally in `Система`.
- [ ] Direct `POST /api/auth/register` returns `403` when registration is closed.
- [ ] Bootstrap admin from `DUTYLOG_ADMIN_USERNAME` can log in and sees `Система`.
- [ ] `Система` → `Пользователи и роли` contains only expected administrators.
- [ ] `Система` is visible only to administrator.
- [ ] Regular user receives `403` from `/api/admin/status`.
- [ ] CSRF cookie is present in the web interface.
- [ ] A browser session cannot authenticate `/api/mobile/**`; a valid bearer token can.
- [ ] Authentication rate limiting is enabled in production.
- [ ] Normal registration passwords require at least 8 characters.
- [ ] Browser responses include security headers (`CSP`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`).
- [ ] CSP does not allow inline scripts.
- [ ] Notes export returns `no-store`, contains only the current user's notes and respects configured limits.
- [ ] `SECURITY_AUDIT` events are present and do not include secrets/user note content.
- [ ] Production session cookie is `HttpOnly`, `Secure`, and `SameSite=Lax`.
- [ ] Disabled modules return `403 MODULE_DISABLED:<key>` from their API boundaries.
- [ ] `/api/mobile/sync` cannot write notes/overtime when those modules are disabled.
- [ ] Telegram link codes cannot link accounts after the Telegram module is disabled.
- [ ] Changing password revokes mobile sessions.
- [ ] `/actuator/health` is public, but detailed diagnostics are not public.
- [ ] `/actuator/info` contains only non-secret app metadata.

## Docker

- [ ] Use `docker-compose.prod.yml` on the VPS.
- [ ] `docker compose down` is used for safe stop.
- [ ] `docker compose down -v` is avoided unless database deletion is intentional.
- [ ] App container healthcheck is healthy.
- [ ] App process runs as non-root UID/GID `10001`.
- [ ] `app_logs` volume is mounted and rolling logs are bounded.
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
- [ ] Dependabot is enabled for Maven, GitHub Actions and Docker.


## Users and roles

- [ ] Public registration creates only `USER`.
- [ ] `Система` → `Пользователи и роли` shows all users and current admin count.
- [ ] Bootstrap env admin cannot be demoted to `USER`.
- [ ] Additional admin can be promoted/demoted by an existing admin.
- [ ] Admin password reset uses at least 12 characters and revokes mobile tokens.
- [ ] `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET` is `false` except during emergency recovery.
