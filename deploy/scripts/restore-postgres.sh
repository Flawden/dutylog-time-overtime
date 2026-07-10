#!/usr/bin/env bash
set -Eeuo pipefail

# Manual PostgreSQL restore for one DutyLog environment.
# This script is intentionally never called by CI/CD.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
BACKUP_FILE="${1:-}"
[[ -f "$ENV_FILE" ]] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }
[[ -n "$BACKUP_FILE" ]] || { echo "Usage: $0 <backup.dump|backup.dump.gz|backup.sql|backup.sql.gz>" >&2; exit 2; }
[[ -f "$BACKUP_FILE" ]] || { echo "Backup file not found: $BACKUP_FILE" >&2; exit 2; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

COMPOSE_FILE="${DUTYLOG_COMPOSE_FILE:-deploy/compose/docker-compose.deploy.yml}"
PROJECT_NAME="${DUTYLOG_PROJECT_NAME:-dutylog-${DUTYLOG_ENVIRONMENT:-manual}}"
DB_SERVICE="${DUTYLOG_DB_SERVICE:-db}"
APP_SERVICE="${DUTYLOG_APP_SERVICE:-app}"
POSTGRES_DB="${POSTGRES_DB:?POSTGRES_DB is required}"
POSTGRES_USER="${POSTGRES_USER:?POSTGRES_USER is required}"

# Compose interpolation requires an image even while only db is used.
export DUTYLOG_IMAGE="${DUTYLOG_IMAGE:-ghcr.io/invalid/invalid@sha256:0000000000000000000000000000000000000000000000000000000000000000}"
compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

if [[ "${CONFIRM_RESTORE:-}" != "RESTORE" ]]; then
  if [[ -t 0 ]]; then
    echo "WARNING: this replaces the current ${DUTYLOG_ENVIRONMENT:-unknown} database."
    read -r -p "Type RESTORE to continue: " answer
    [[ "$answer" == RESTORE ]] || { echo "Restore cancelled."; exit 1; }
  else
    echo "Set CONFIRM_RESTORE=RESTORE for a non-interactive restore." >&2
    exit 1
  fi
fi

compose up -d "$DB_SERVICE"
for _ in $(seq 1 40); do
  compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1 && break
  sleep 2
done
compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null

if [[ -f "$BACKUP_FILE.sha256" ]]; then
  command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required to verify $BACKUP_FILE.sha256" >&2; exit 2; }
  EXPECTED_SHA="$(awk 'NR == 1 {print $1}' "$BACKUP_FILE.sha256")"
  ACTUAL_SHA="$(sha256sum "$BACKUP_FILE" | awk '{print $1}')"
  if [[ ! "$EXPECTED_SHA" =~ ^[0-9a-fA-F]{64}$ || "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
    echo "Backup SHA-256 verification failed: $BACKUP_FILE" >&2
    exit 1
  fi
  echo "Backup SHA-256 verified: $BACKUP_FILE"
fi

case "$BACKUP_FILE" in
  *.dump)
    compose exec -T "$DB_SERVICE" pg_restore --list < "$BACKUP_FILE" >/dev/null
    ;;
  *.dump.gz)
    gzip -dc "$BACKUP_FILE" | compose exec -T "$DB_SERVICE" pg_restore --list >/dev/null
    ;;
esac

if [[ "${SKIP_PRE_RESTORE_BACKUP:-false}" != "true" ]]; then
  echo "Creating a verified pre-restore backup of the current database..."
  DUTYLOG_ENV_FILE="$ENV_FILE" \
  DUTYLOG_COMPOSE_FILE="$COMPOSE_FILE" \
  DUTYLOG_PROJECT_NAME="$PROJECT_NAME" \
  BACKUP_PREFIX="pre-restore-${DUTYLOG_ENVIRONMENT:-manual}" \
    bash deploy/scripts/backup-postgres.sh
fi

APP_WAS_RUNNING=false
if [[ -n "$(compose ps -q "$APP_SERVICE" || true)" ]]; then
  APP_WAS_RUNNING=true
  compose stop "$APP_SERVICE"
fi

restore_custom() {
  compose exec -T "$DB_SERVICE" pg_restore \
    --clean --if-exists --no-owner --no-privileges --single-transaction \
    -U "$POSTGRES_USER" -d "$POSTGRES_DB"
}
restore_sql() {
  compose exec -T "$DB_SERVICE" psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
}

case "$BACKUP_FILE" in
  *.dump) cat "$BACKUP_FILE" | restore_custom ;;
  *.dump.gz) gzip -dc "$BACKUP_FILE" | restore_custom ;;
  *.sql) cat "$BACKUP_FILE" | restore_sql ;;
  *.sql.gz) gzip -dc "$BACKUP_FILE" | restore_sql ;;
  *) echo "Unsupported backup format." >&2; exit 2 ;;
esac

if [[ "$APP_WAS_RUNNING" == true ]]; then
  compose up -d "$APP_SERVICE"
fi

echo "Restore completed. Run smoke-test.sh before reopening traffic."
