#!/usr/bin/env bash
set -Eeuo pipefail

# Database dumps and checksums may contain sensitive account data.
umask 077

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
REQUIRE_CHECKSUM="${DUTYLOG_RESTORE_REQUIRE_CHECKSUM:-true}"

[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found: $COMPOSE_FILE" >&2; exit 2; }

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

case "$BACKUP_FILE" in
  *.dump|*.dump.gz|*.sql|*.sql.gz) ;;
  *) echo "Unsupported backup format: $BACKUP_FILE" >&2; exit 2 ;;
esac

compose up -d "$DB_SERVICE"
for _ in $(seq 1 40); do
  compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1 && break
  sleep 2
done
compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null

CHECKSUM="$BACKUP_FILE.sha256"
if [[ -f "$CHECKSUM" ]]; then
  command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required to verify $CHECKSUM" >&2; exit 2; }
  EXPECTED_SHA="$(awk 'NR == 1 {print $1}' "$CHECKSUM")"
  ACTUAL_SHA="$(sha256sum "$BACKUP_FILE" | awk '{print $1}')"
  if [[ ! "$EXPECTED_SHA" =~ ^[0-9a-fA-F]{64}$ || "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
    echo "Backup SHA-256 verification failed: $BACKUP_FILE" >&2
    exit 1
  fi
  echo "Backup SHA-256 verified: $BACKUP_FILE"
elif [[ "$REQUIRE_CHECKSUM" == "true" ]]; then
  echo "Checksum file is required but missing: $CHECKSUM" >&2
  exit 1
else
  echo "WARNING: restoring without a checksum because DUTYLOG_RESTORE_REQUIRE_CHECKSUM=false." >&2
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
APP_STOPPED=false
restart_app_on_exit() {
  local status=$?
  trap - EXIT
  if [[ "$APP_WAS_RUNNING" == true && "$APP_STOPPED" == true ]]; then
    echo "Starting DutyLog application after restore attempt..."
    if compose up -d "$APP_SERVICE"; then
      APP_STOPPED=false
    else
      echo "ERROR: failed to restart application service: $APP_SERVICE" >&2
      (( status == 0 )) && status=1
    fi
  fi
  exit "$status"
}
trap restart_app_on_exit EXIT

if [[ -n "$(compose ps --status running -q "$APP_SERVICE" || true)" ]]; then
  APP_WAS_RUNNING=true
  compose stop "$APP_SERVICE"
  APP_STOPPED=true
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
esac

if [[ "$APP_WAS_RUNNING" == true ]]; then
  compose up -d "$APP_SERVICE"
  APP_STOPPED=false
fi

trap - EXIT
echo "Restore completed. Run smoke-test.sh before reopening traffic."
