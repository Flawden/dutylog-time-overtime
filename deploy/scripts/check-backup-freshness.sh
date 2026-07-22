#!/usr/bin/env bash
set -Eeuo pipefail

umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
[[ -f "$ENV_FILE" ]] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

COMPOSE_FILE="${DUTYLOG_COMPOSE_FILE:-deploy/compose/docker-compose.deploy.yml}"
PROJECT_NAME="${DUTYLOG_PROJECT_NAME:-dutylog-${DUTYLOG_ENVIRONMENT:-manual}}"
DB_SERVICE="${DUTYLOG_DB_SERVICE:-db}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
MAX_AGE_HOURS="${BACKUP_MAX_AGE_HOURS:-30}"
REQUIRE_CHECKSUM="${DUTYLOG_BACKUP_REQUIRE_CHECKSUM:-true}"
VALIDATE_ARCHIVE="${DUTYLOG_BACKUP_VALIDATE_ARCHIVE:-true}"

[[ "$MAX_AGE_HOURS" =~ ^[0-9]+$ ]] && (( MAX_AGE_HOURS > 0 )) \
  || { echo "BACKUP_MAX_AGE_HOURS must be a positive integer." >&2; exit 2; }
[[ -d "$BACKUP_DIR" ]] || { echo "Backup directory not found: $BACKUP_DIR" >&2; exit 1; }

LATEST="$(
  find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.dump' -printf '%T@ %p\n' \
    | sort -nr | head -n 1 | cut -d' ' -f2-
)"
[[ -n "$LATEST" && -f "$LATEST" ]] || { echo "No DutyLog .dump backup found in $BACKUP_DIR" >&2; exit 1; }
[[ -s "$LATEST" ]] || { echo "Latest backup is empty: $LATEST" >&2; exit 1; }

NOW="$(date +%s)"
MTIME="$(stat -c %Y "$LATEST")"
AGE_SECONDS=$((NOW - MTIME))
MAX_AGE_SECONDS=$((MAX_AGE_HOURS * 3600))
(( AGE_SECONDS >= 0 )) || { echo "Latest backup timestamp is in the future: $LATEST" >&2; exit 1; }
(( AGE_SECONDS <= MAX_AGE_SECONDS )) || {
  echo "Latest backup is stale: age=${AGE_SECONDS}s max=${MAX_AGE_SECONDS}s file=$LATEST" >&2
  exit 1
}

CHECKSUM="$LATEST.sha256"
if [[ -f "$CHECKSUM" ]]; then
  command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required." >&2; exit 2; }
  (
    cd "$(dirname "$LATEST")"
    sha256sum -c "$(basename "$CHECKSUM")" >/dev/null
  )
elif [[ "$REQUIRE_CHECKSUM" == "true" ]]; then
  echo "Checksum file not found: $CHECKSUM" >&2
  exit 1
fi

if [[ "$VALIDATE_ARCHIVE" == "true" ]]; then
  [[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found: $COMPOSE_FILE" >&2; exit 2; }
  compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
  }
  [[ -n "$(compose ps -q "$DB_SERVICE")" ]] || { echo "Database service is not running: $DB_SERVICE" >&2; exit 1; }
  compose exec -T "$DB_SERVICE" pg_restore --list < "$LATEST" >/dev/null
fi

AGE_MINUTES=$((AGE_SECONDS / 60))
SIZE_BYTES="$(stat -c %s "$LATEST")"
echo "BACKUP_HEALTHY file=$LATEST age_minutes=$AGE_MINUTES size_bytes=$SIZE_BYTES checksum=$([[ -f "$CHECKSUM" ]] && echo verified || echo skipped) archive=$([[ "$VALIDATE_ARCHIVE" == true ]] && echo valid || echo skipped)"
