# Release checklist

Status: v26.6.9.

This checklist is used before creating an archive, Git tag or VPS deployment. DutyLog is currently a web/PWA inside a Spring Boot monolith. There is no native mobile app in this release.

## 1. Local release gate

Run the bundled release check:

```bash
./deploy/scripts/release-check.sh
```

It covers version consistency, frontend static checks, manifest, shell scripts, basic Java structure, Flyway migration sequence and production config safety.

## 2. Automated tests

When Maven is available:

```bash
mvn -B --no-transfer-progress test
```

GitHub Actions runs this automatically on push and pull request, then runs `release-check.sh`.

## 3. Production preflight

Before first launch or update on VPS:

```bash
cp .env.production.example .env
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
./deploy/scripts/check-production-env.sh
```

Fix errors before launching. Warnings are allowed only if they are intentional and understood.

## 4. Backup before update

```bash
./deploy/scripts/backup-postgres.sh
./deploy/scripts/list-backups.sh
```

Copy at least one recent backup outside the VPS.

## 5. Deploy/update

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Do not run `docker compose down -v` unless deleting the database is intentional.

## 6. Smoke test after update

```bash
./deploy/scripts/smoke-test.sh https://your-domain.example
```

The smoke test checks:

- `/actuator/health`;
- `login.html`;
- app shell;
- manifest;
- service worker cache version;
- split JS assets;
- public registration status endpoint;
- protected admin API does not crash.

## 7. Manual smoke

Check in browser:

- login/logout;
- calendar opens;
- selected-day panel opens;
- module settings open;
- admin sees `Система`;
- regular user does not see `Система`;
- server version is `26.6.9`;
- registration status is expected;
- Telegram status matches `.env`;
- language switch still works;
- theme settings still work.

## 8. Offline/PWA smoke

DevTools → Network → Offline:

1. Open the app online and wait for calendar data.
2. Open offline sync diagnostics.
3. Disable network.
4. Reload page.
5. Confirm calendar opens from local snapshot.
6. Change selected-day shift or note.
7. Confirm queue counter grows.
8. Enable network.
9. Confirm queue syncs and diagnostics update.

On phone, repeat a minimal PWA scenario: open online → install/open as PWA → go offline → reload → edit note → go online → sync.

## 9. Security smoke

- Public registration creates only `USER`.
- Closed registration returns `403` for direct `POST /api/auth/register`.
- `/api/admin/**` requires `ADMIN`.
- Bootstrap env admin cannot be demoted.
- Password reset revokes mobile sessions.
- `/actuator/health` is public but detailed diagnostics are not public.
- Production compose does not publish app port `8080`.
- Browser security headers are present.
- Production session cookie is `HttpOnly`, `Secure`, and `SameSite=Lax`.
- Disabled modules are guarded in normal API and aggregated mobile sync.

## 10. Git

```bash
git add -A
git commit -m "chore: review security hardening"
git tag -a v26.6.9 -m "v26.6.9 — properties and tests hotfix"
```
