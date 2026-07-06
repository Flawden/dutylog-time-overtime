#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog PostgreSQL backup.
# Run from any directory:
#   ./deploy/scripts/backup-postgres.sh
#
# Creates a PostgreSQL custom-format dump that is suitable for pg_restore.
# The script reads .env from the project root when it exists.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

DB_SERVICE="${DUTYLOG_DB_SERVICE:-db}"
POSTGRES_DB="${POSTGRES_DB:-shift_calendar}"
POSTGRES_USER="${POSTGRES_USER:-shift_calendar}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
KEEP_LAST="${BACKUP_KEEP_LAST:-20}"

mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y-%m-%d_%H-%M-%S)"
OUT="$BACKUP_DIR/dutylog-$STAMP.dump"
TMP="$OUT.tmp"

echo "DutyLog backup"
echo "Project:  $PROJECT_ROOT"
echo "Service:  $DB_SERVICE"
echo "Database: $POSTGRES_DB"
echo "Output:   $OUT"

docker compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null

docker compose exec -T "$DB_SERVICE" \
  pg_dump \
    --format=custom \
    --compress=9 \
    --no-owner \
    --no-privileges \
    -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" > "$TMP"

mv "$TMP" "$OUT"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$OUT" > "$OUT.sha256"
fi

if [[ "$KEEP_LAST" =~ ^[0-9]+$ ]] && (( KEEP_LAST > 0 )); then
  mapfile -t OLD_BACKUPS < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'dutylog-*.dump' -printf '%T@ %p\n' | sort -nr | awk '{print $2}' | tail -n +$((KEEP_LAST + 1)))
  for file in "${OLD_BACKUPS[@]:-}"; do
    rm -f "$file" "$file.sha256"
  done
fi

SIZE="$(du -h "$OUT" | awk '{print $1}')"
echo "Backup saved: $OUT ($SIZE)"
