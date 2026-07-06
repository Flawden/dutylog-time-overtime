# Deployment guide

This document describes a simple production deployment for DutyLog on a VPS using Docker Compose and PostgreSQL.

## Requirements

- Linux VPS.
- Docker and Docker Compose plugin.
- Domain name pointing to the VPS.
- HTTPS reverse proxy, for example nginx or Caddy.
- A filled `.env` file.

## Prepare server directory

```bash
sudo mkdir -p /opt/dutylog
sudo chown -R $USER:$USER /opt/dutylog
cd /opt/dutylog
```

Clone the repository:

```bash
git clone <repo-url> .
```

Copy environment file:

```bash
cp .env.example .env
nano .env
```

Set strong values for:

```env
POSTGRES_PASSWORD=
SPRING_DATASOURCE_PASSWORD=
DUTYLOG_TELEGRAM_BOT_TOKEN=
```

`POSTGRES_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` must match when using the default compose configuration.

## Start application

```bash
docker compose up -d --build
```

Check containers:

```bash
docker compose ps
```

Check logs:

```bash
docker compose logs -f app
```

## Reverse proxy

An nginx example is available at:

```text
deploy/nginx/shift-calendar.conf.example
```

The application container exposes port `8080`. Put nginx or Caddy in front of it and enable HTTPS.

## Telegram bot

For long polling, a domain is not required. Enable the bot in `.env`:

```env
DUTYLOG_TELEGRAM_ENABLED=true
DUTYLOG_TELEGRAM_BOT_TOKEN=123456:telegram-token
DUTYLOG_TELEGRAM_BOT_USERNAME=your_bot_username
DUTYLOG_TELEGRAM_POLLING_ENABLED=true
DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED=true
```

Restart after changes:

```bash
docker compose up -d --build
```

## Update application

Always create a backup before updating:

```bash
./deploy/scripts/backup-postgres.sh
git pull
docker compose up -d --build
```

## Safe stop and dangerous stop

Safe stop:

```bash
docker compose down
```

Dangerous stop:

```bash
docker compose down -v
```

The `-v` flag removes Docker volumes and can delete the PostgreSQL database.

## Restore from backup

```bash
./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

More details are in [`BACKUP.md`](BACKUP.md).
