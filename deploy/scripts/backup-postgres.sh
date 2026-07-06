#!/usr/bin/env bash
set -euo pipefail

# Запускать из корня проекта: ./deploy/scripts/backup-postgres.sh
# Скрипт создаёт gzip-бэкап PostgreSQL из docker-compose сервиса db.

BACKUP_DIR="${BACKUP_DIR:-./backups}"
mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y-%m-%d_%H-%M-%S)"
OUT="$BACKUP_DIR/shift-calendar-$STAMP.sql.gz"

# Значения можно брать из .env. По умолчанию используются compose-дефолты.
POSTGRES_DB="${POSTGRES_DB:-shift_calendar}"
POSTGRES_USER="${POSTGRES_USER:-shift_calendar}"

docker compose exec -T db pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$OUT"
echo "Backup saved: $OUT"
