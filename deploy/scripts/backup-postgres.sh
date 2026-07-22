#!/usr/bin/env bash
set -Eeuo pipefail

# Database dumps and checksums may contain sensitive account data.
umask 077

# Creates, verifies and rotates PostgreSQL custom-format backups.
# Supported variables:
#   DUTYLOG_ENV_FILE, DUTYLOG_COMPOSE_FILE, DUTYLOG_PROJECT_NAME,
#   DUTYLOG_DB_SERVICE, BACKUP_DIR, BACKUP_KEEP_LAST, BACKUP_PREFIX.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
[[ -f "$ENV_FILE" ]] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }

set -a
# Environment files used by DutyLog are shell-compatible KEY=value files.
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

COMPOSE_FILE="${DUTYLOG_COMPOSE_FILE:-deploy/compose/docker-compose.deploy.yml}"
PROJECT_NAME="${DUTYLOG_PROJECT_NAME:-dutylog-${DUTYLOG_ENVIRONMENT:-manual}}"
DB_SERVICE="${DUTYLOG_DB_SERVICE:-db}"
POSTGRES_DB="${POSTGRES_DB:?POSTGRES_DB is required}"
POSTGRES_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
KEEP_LAST="${BACKUP_KEEP_LAST:-20}"
PREFIX="${BACKUP_PREFIX:-dutylog}"
PREFIX="$(printf '%s' "$PREFIX" | tr -cs 'A-Za-z0-9._-' '-')"

[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found: $COMPOSE_FILE" >&2; exit 2; }
[[ "$KEEP_LAST" =~ ^[0-9]+$ ]] || { echo "BACKUP_KEEP_LAST must be a non-negative integer." >&2; exit 2; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required." >&2; exit 2; }
command -v flock >/dev/null 2>&1 || { echo "flock is required." >&2; exit 2; }

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

mkdir -p "$BACKUP_DIR"
chmod 0700 "$BACKUP_DIR"

# Prevent a timer, deployment and a manual operator from writing backups concurrently.
LOCK_FILE="$BACKUP_DIR/.backup.lock"
exec 9>"$LOCK_FILE"
chmod 0600 "$LOCK_FILE"
if ! flock -n 9; then
  echo "Another DutyLog backup is already running: $LOCK_FILE" >&2
  exit 1
fi

STAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
OUT="$BACKUP_DIR/${PREFIX}-${STAMP}.dump"
TMP="$OUT.tmp"
CHECKSUM="$OUT.sha256"
CHECKSUM_TMP="$CHECKSUM.tmp"
cleanup_partial() {
  rm -f "$TMP" "$CHECKSUM_TMP"
}
trap cleanup_partial EXIT

echo "DutyLog PostgreSQL backup"
echo "Project:     $PROJECT_NAME"
echo "Environment: ${DUTYLOG_ENVIRONMENT:-unknown}"
echo "Database:    $POSTGRES_DB"
echo "Output:      $OUT"

DB_CONTAINER="$(compose ps -q "$DB_SERVICE")"
if [[ -z "$DB_CONTAINER" ]]; then
  echo "Database service is not running: $DB_SERVICE" >&2
  exit 1
fi

compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null
compose exec -T "$DB_SERVICE" \
  pg_dump --format=custom --compress=9 --no-owner --no-privileges \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" > "$TMP"

if [[ ! -s "$TMP" ]]; then
  echo "Backup is empty; refusing to publish it." >&2
  exit 1
fi

# Verify that PostgreSQL can parse the archive before it is accepted.
compose exec -T "$DB_SERVICE" pg_restore --list < "$TMP" >/dev/null
mv "$TMP" "$OUT"
chmod 0600 "$OUT"

(
  cd "$(dirname "$OUT")"
  sha256sum "$(basename "$OUT")" > "$(basename "$CHECKSUM_TMP")"
)
mv "$CHECKSUM_TMP" "$CHECKSUM"
chmod 0600 "$CHECKSUM"

if (( KEEP_LAST > 0 )); then
  mapfile -t OLD_BACKUPS < <(
    find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.dump' -printf '%T@ %p\n' \
      | sort -nr | awk '{print $2}' | tail -n +$((KEEP_LAST + 1))
  )
  for file in "${OLD_BACKUPS[@]:-}"; do
    [[ -n "$file" ]] && rm -f -- "$file" "$file.sha256"
  done
fi

trap - EXIT
SIZE="$(du -h "$OUT" | awk '{print $1}')"
echo "Backup verified and saved: $OUT ($SIZE)"
printf '%s\n' "$OUT"
