#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog PostgreSQL restore.
# Usage:
#   ./deploy/scripts/restore-postgres.sh backups/dutylog-2026-07-06_12-00-00.dump
#
# By default the script asks for confirmation. For non-interactive restore:
#   FORCE=true ./deploy/scripts/restore-postgres.sh backups/file.dump

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

COMPOSE_FILE="${DUTYLOG_COMPOSE_FILE:-}"
COMPOSE_FILE_ARGS=()
if [[ -n "$COMPOSE_FILE" ]]; then
  COMPOSE_FILE_ARGS=(-f "$COMPOSE_FILE")
fi
compose() {
  docker compose "${COMPOSE_FILE_ARGS[@]}" "$@"
}

BACKUP_FILE="${1:-}"
if [[ -z "$BACKUP_FILE" ]]; then
  echo "Usage: $0 <backup.dump|backup.sql|backup.sql.gz>"
  exit 2
fi

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Backup file not found: $BACKUP_FILE"
  exit 2
fi

DB_SERVICE="${DUTYLOG_DB_SERVICE:-db}"
APP_SERVICE="${DUTYLOG_APP_SERVICE:-app}"
POSTGRES_DB="${POSTGRES_DB:-shift_calendar}"
POSTGRES_USER="${POSTGRES_USER:-shift_calendar}"
FORCE="${FORCE:-false}"
STOP_APP="${STOP_APP:-true}"

echo "DutyLog restore"
echo "Project:  $PROJECT_ROOT"
echo "Service:  $DB_SERVICE"
echo "Database: $POSTGRES_DB"
echo "Input:    $BACKUP_FILE"
echo

echo "WARNING: restore will overwrite the current DutyLog database."
echo "Make a fresh backup before continuing if this database contains useful data."

if [[ "$FORCE" != "true" ]]; then
  read -r -p "Type RESTORE to continue: " answer
  if [[ "$answer" != "RESTORE" ]]; then
    echo "Restore cancelled."
    exit 1
  fi
fi

compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null

APP_WAS_RUNNING="false"
if [[ "$STOP_APP" == "true" ]] && compose ps -q "$APP_SERVICE" >/dev/null 2>&1; then
  if [[ -n "$(compose ps -q "$APP_SERVICE")" ]]; then
    APP_WAS_RUNNING="true"
    echo "Stopping application service: $APP_SERVICE"
    compose stop "$APP_SERVICE" >/dev/null || true
  fi
fi

restore_custom() {
  compose exec -T "$DB_SERVICE" \
    pg_restore \
      --clean \
      --if-exists \
      --no-owner \
      --no-privileges \
      --single-transaction \
      -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB"
}

restore_sql() {
  compose exec -T "$DB_SERVICE" \
    psql \
      -v ON_ERROR_STOP=1 \
      -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB"
}

case "$BACKUP_FILE" in
  *.dump)
    cat "$BACKUP_FILE" | restore_custom
    ;;
  *.dump.gz)
    gzip -dc "$BACKUP_FILE" | restore_custom
    ;;
  *.sql.gz)
    gzip -dc "$BACKUP_FILE" | restore_sql
    ;;
  *.sql)
    cat "$BACKUP_FILE" | restore_sql
    ;;
  *)
    echo "Unsupported backup format. Use .dump, .dump.gz, .sql, or .sql.gz"
    exit 2
    ;;
esac

if [[ "$APP_WAS_RUNNING" == "true" ]]; then
  echo "Starting application service: $APP_SERVICE"
  compose up -d "$APP_SERVICE" >/dev/null
fi

echo "Restore completed."
