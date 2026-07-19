#!/usr/bin/env bash
set -Eeuo pipefail

# Database dumps and checksums may contain sensitive account data.
umask 077

# Creates and verifies a PostgreSQL custom-format backup.
# Supported CI/CD variables:
#   DUTYLOG_ENV_FILE, DUTYLOG_COMPOSE_FILE, DUTYLOG_PROJECT_NAME,
#   DUTYLOG_DB_SERVICE, BACKUP_DIR, BACKUP_KEEP_LAST, BACKUP_PREFIX.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # Environment files used by DutyLog are shell-compatible KEY=value files.
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

COMPOSE_FILE="${DUTYLOG_COMPOSE_FILE:-docker-compose.prod.yml}"
PROJECT_NAME="${DUTYLOG_PROJECT_NAME:-dutylog}"
DB_SERVICE="${DUTYLOG_DB_SERVICE:-db}"
POSTGRES_DB="${POSTGRES_DB:-shift_calendar}"
POSTGRES_USER="${POSTGRES_USER:-shift_calendar}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
KEEP_LAST="${BACKUP_KEEP_LAST:-20}"
PREFIX="${BACKUP_PREFIX:-dutylog}"
PREFIX="$(printf '%s' "$PREFIX" | tr -cs 'A-Za-z0-9._-' '-')"

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

mkdir -p "$BACKUP_DIR"
chmod 0700 "$BACKUP_DIR"
STAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
OUT="$BACKUP_DIR/${PREFIX}-${STAMP}.dump"
TMP="$OUT.tmp"
trap 'rm -f "$TMP"' EXIT

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
trap - EXIT

if command -v sha256sum >/dev/null 2>&1; then
  (
    cd "$(dirname "$OUT")"
    sha256sum "$(basename "$OUT")" > "$(basename "$OUT").sha256"
    chmod 0600 "$(basename "$OUT").sha256"
  )
fi

if [[ "$KEEP_LAST" =~ ^[0-9]+$ ]] && (( KEEP_LAST > 0 )); then
  mapfile -t OLD_BACKUPS < <(
    find "$BACKUP_DIR" -maxdepth 1 -type f -name "*.dump" -printf '%T@ %p\n' \
      | sort -nr | awk '{print $2}' | tail -n +$((KEEP_LAST + 1))
  )
  for file in "${OLD_BACKUPS[@]:-}"; do
    [[ -n "$file" ]] && rm -f "$file" "$file.sha256"
  done
fi

SIZE="$(du -h "$OUT" | awk '{print $1}')"
echo "Backup verified and saved: $OUT ($SIZE)"
printf '%s\n' "$OUT"
