# Deployment guide

This guide describes the recommended production deployment for DutyLog on a VPS using Docker Compose, PostgreSQL and Caddy.

## Requirements

- Linux VPS with SSH access.
- Domain name pointing to the VPS IP.
- Docker and Docker Compose plugin.
- Git.
- Open ports: `80`, `443`, and `22` for SSH.
- Filled production `.env` file.

## Recommended production layout

```text
/opt/dutylog
├─ docker-compose.prod.yml
├─ .env
├─ deploy/caddy/Caddyfile
├─ backups/
└─ src/...
```

The production compose file runs:

```text
caddy  -> HTTPS reverse proxy, public ports 80/443
app    -> DutyLog Spring Boot, internal port 8080 only
db     -> PostgreSQL, internal Docker network only
```

## Prepare server directory

```bash
sudo mkdir -p /opt/dutylog
sudo chown -R "$USER":"$USER" /opt/dutylog
cd /opt/dutylog
```

Clone the repository:

```bash
git clone <repo-url> .
```

Create environment file:

```bash
cp .env.production.example .env
nano .env
```

Set at least:

```env
DUTYLOG_DOMAIN=dutylog.example.com
POSTGRES_DB=dutylog
POSTGRES_USER=dutylog
POSTGRES_PASSWORD=long-random-password
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/dutylog
SPRING_DATASOURCE_USERNAME=dutylog
SPRING_DATASOURCE_PASSWORD=long-random-password
```

If Telegram is enabled:

```env
DUTYLOG_TELEGRAM_ENABLED=true
DUTYLOG_TELEGRAM_BOT_TOKEN=123456:telegram-token
DUTYLOG_TELEGRAM_BOT_USERNAME=your_bot_username
DUTYLOG_TELEGRAM_POLLING_ENABLED=true
DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED=true
```

Prepare Caddy config:

```bash
cp deploy/caddy/Caddyfile.example deploy/caddy/Caddyfile
```

## First start

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Check containers:

```bash
docker compose -f docker-compose.prod.yml ps
```

Check logs:

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

Run smoke test:

```bash
./deploy/scripts/smoke-test.sh https://your-domain.example
```

## Register first user

Open:

```text
https://your-domain.example
```

Create the first account. On a new installation, the first registered user becomes administrator. The administrator sees the `Система` section in the header.

## Local Docker run

For local Docker testing without Caddy:

```bash
cp .env.example .env
docker compose up -d --build
```

The app will be available at:

```text
http://localhost:8080
```

## Update application

Always create a backup before updating:

```bash
cd /opt/dutylog
./deploy/scripts/backup-postgres.sh
git pull
docker compose -f docker-compose.prod.yml up -d --build
./deploy/scripts/smoke-test.sh https://your-domain.example
```

## Rollback

If the update did not include DB migrations, code rollback may be enough:

```bash
git checkout v20.7
docker compose -f docker-compose.prod.yml up -d --build
```

If the update applied DB migrations, restore matching DB backup too:

```bash
git checkout v20.7
./deploy/scripts/restore-postgres.sh backups/dutylog-before-update.dump
docker compose -f docker-compose.prod.yml up -d --build
```

## Backup

Create backup:

```bash
./deploy/scripts/backup-postgres.sh
```

List backups:

```bash
./deploy/scripts/list-backups.sh
```

Restore:

```bash
./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

More details: [`BACKUP.md`](BACKUP.md).

## Safe stop and dangerous stop

Safe stop:

```bash
docker compose -f docker-compose.prod.yml down
```

Dangerous stop:

```bash
docker compose -f docker-compose.prod.yml down -v
```

The `-v` flag removes Docker volumes and can delete the PostgreSQL database.

## More operations

See:

- [`PRODUCTION_RUNBOOK.md`](PRODUCTION_RUNBOOK.md) — first launch, updates, rollback and emergency backup.
- [`SECURITY_CHECKLIST.md`](SECURITY_CHECKLIST.md) — security checklist before public usage.
- [`VPS_CHECKLIST.md`](VPS_CHECKLIST.md) — compact launch checklist.
