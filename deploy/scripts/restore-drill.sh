#!/usr/bin/env bash
set -Eeuo pipefail

# Restores a DutyLog backup into an isolated temporary PostgreSQL container.
# The staging/production database, application container and permanent volume are never modified.
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
POSTGRES_DB="${POSTGRES_DB:?POSTGRES_DB is required}"
POSTGRES_USER="${POSTGRES_USER:?POSTGRES_USER is required}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
POSTGRES_IMAGE="${DUTYLOG_DRILL_POSTGRES_IMAGE:-postgres:16-alpine}"
REQUIRE_SOURCE_MATCH="${DUTYLOG_DRILL_REQUIRE_SOURCE_MATCH:-false}"

[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found: $COMPOSE_FILE" >&2; exit 2; }

BACKUP_FILE="${1:-}"
if [[ -z "$BACKUP_FILE" ]]; then
  BACKUP_FILE="$(
    find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.dump' -printf '%T@ %p\n' \
      | sort -nr | head -n 1 | cut -d' ' -f2-
  )"
fi
[[ -n "$BACKUP_FILE" && -f "$BACKUP_FILE" ]] || { echo "Backup file not found." >&2; exit 2; }
[[ "$BACKUP_FILE" == *.dump ]] || { echo "Restore drill currently accepts PostgreSQL custom .dump files only." >&2; exit 2; }
[[ -f "$BACKUP_FILE.sha256" ]] || { echo "Checksum file not found: $BACKUP_FILE.sha256" >&2; exit 1; }

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

SOURCE_CONTAINER="$(compose ps -q "$DB_SERVICE")"
[[ -n "$SOURCE_CONTAINER" ]] || { echo "Source database service is not running: $DB_SERVICE" >&2; exit 1; }
compose exec -T "$DB_SERVICE" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null

DRILL_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
DRILL_CONTAINER="dutylog-restore-drill-$DRILL_ID"
DRILL_VOLUME="dutylog_restore_drill_$DRILL_ID"
DRILL_USER="dutylog_drill"
DRILL_DB="dutylog_drill"
DRILL_PASSWORD="$(openssl rand -hex 24)"
SOURCE_COUNTS="$(mktemp)"
RESTORED_COUNTS="$(mktemp)"
SOURCE_TABLES="$(mktemp)"
RESTORED_TABLES="$(mktemp)"

cleanup() {
  local status=$?
  trap - EXIT
  echo
  echo "=== CLEANUP TEMPORARY RESTORE ENVIRONMENT ==="
  docker rm -f "$DRILL_CONTAINER" >/dev/null 2>&1 || true
  docker volume rm "$DRILL_VOLUME" >/dev/null 2>&1 || true
  rm -f "$SOURCE_COUNTS" "$RESTORED_COUNTS" "$SOURCE_TABLES" "$RESTORED_TABLES"
  if (( status == 0 )); then
    echo "Temporary container and volume removed."
  else
    echo "Restore drill failed; temporary resources were still removed." >&2
  fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

collect_counts() {
  local container="$1"
  local user="$2"
  local database="$3"
  local password="$4"

  docker exec -i -e PGPASSWORD="$password" "$container" \
    psql -X -qAt -v ON_ERROR_STOP=1 -U "$user" -d "$database" <<'SQL'
CREATE OR REPLACE FUNCTION pg_temp.dutylog_table_counts()
RETURNS TABLE(table_name text, row_count bigint)
LANGUAGE plpgsql
AS $$
DECLARE
  item record;
BEGIN
  FOR item IN
    SELECT schemaname, tablename
    FROM pg_tables
    WHERE schemaname = 'public'
    ORDER BY tablename
  LOOP
    RETURN QUERY EXECUTE format(
      'SELECT %L::text, count(*)::bigint FROM %I.%I',
      item.schemaname || '.' || item.tablename,
      item.schemaname,
      item.tablename
    );
  END LOOP;
END
$$;
SELECT table_name || '|' || row_count
FROM pg_temp.dutylog_table_counts()
ORDER BY table_name;
SQL
}

echo "=== RESTORE DRILL INPUT ==="
stat -c '%A %U:%G %s bytes %y %n' "$BACKUP_FILE"

echo
echo "=== VERIFY BACKUP CHECKSUM ==="
(
  cd "$(dirname "$BACKUP_FILE")"
  sha256sum -c "$(basename "$BACKUP_FILE").sha256"
)

echo
echo "=== CREATE ISOLATED TEMPORARY POSTGRESQL ==="
docker volume create "$DRILL_VOLUME" >/dev/null
docker run -d \
  --name "$DRILL_CONTAINER" \
  --network none \
  --memory "${DUTYLOG_DRILL_MEMORY_LIMIT:-320m}" \
  --memory-swap "${DUTYLOG_DRILL_MEMORY_SWAP_LIMIT:-448m}" \
  --pids-limit "${DUTYLOG_DRILL_PIDS_LIMIT:-128}" \
  -e POSTGRES_USER="$DRILL_USER" \
  -e POSTGRES_PASSWORD="$DRILL_PASSWORD" \
  -e POSTGRES_DB="$DRILL_DB" \
  -v "$DRILL_VOLUME:/var/lib/postgresql/data" \
  "$POSTGRES_IMAGE" >/dev/null

echo "Container: $DRILL_CONTAINER"
echo "Volume:    $DRILL_VOLUME"
echo "Network:   none"
echo "Ports:     none"

echo
echo "=== WAIT FOR TEMPORARY POSTGRESQL ==="
READY=false
for _ in $(seq 1 40); do
  if docker exec "$DRILL_CONTAINER" pg_isready -U "$DRILL_USER" -d "$DRILL_DB" >/dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 2
done
if [[ "$READY" != true ]]; then
  echo "Temporary PostgreSQL did not become ready." >&2
  docker logs --tail 100 "$DRILL_CONTAINER" >&2 || true
  exit 1
fi
echo "Temporary PostgreSQL is ready."

echo
echo "=== RESTORE BACKUP ==="
docker exec -i -e PGPASSWORD="$DRILL_PASSWORD" "$DRILL_CONTAINER" \
  pg_restore --exit-on-error --no-owner --no-privileges \
  -U "$DRILL_USER" -d "$DRILL_DB" < "$BACKUP_FILE"
echo "Restore command completed."

echo
echo "=== RESTORED DATABASE INTEGRITY ==="
RESTORED_TABLE_COUNT="$(docker exec -e PGPASSWORD="$DRILL_PASSWORD" "$DRILL_CONTAINER" \
  psql -X -qAt -v ON_ERROR_STOP=1 -U "$DRILL_USER" -d "$DRILL_DB" \
  -c "SELECT count(*) FROM pg_tables WHERE schemaname='public';")"
[[ "$RESTORED_TABLE_COUNT" =~ ^[0-9]+$ ]] && (( RESTORED_TABLE_COUNT > 0 )) \
  || { echo "Restored database contains no public tables." >&2; exit 1; }

FLYWAY_STATE="$(docker exec -e PGPASSWORD="$DRILL_PASSWORD" "$DRILL_CONTAINER" \
  psql -X -qAt -v ON_ERROR_STOP=1 -U "$DRILL_USER" -d "$DRILL_DB" \
  -c "SELECT version || '|' || description || '|' || CASE WHEN success THEN 'success' ELSE 'failed' END FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;")"
[[ "$FLYWAY_STATE" == *"|success" ]] || { echo "Restored Flyway state is not successful: $FLYWAY_STATE" >&2; exit 1; }
echo "Public tables: $RESTORED_TABLE_COUNT"
echo "Latest Flyway: $FLYWAY_STATE"

collect_counts "$SOURCE_CONTAINER" "$POSTGRES_USER" "$POSTGRES_DB" "$POSTGRES_PASSWORD" > "$SOURCE_COUNTS"
collect_counts "$DRILL_CONTAINER" "$DRILL_USER" "$DRILL_DB" "$DRILL_PASSWORD" > "$RESTORED_COUNTS"
cut -d'|' -f1 "$SOURCE_COUNTS" > "$SOURCE_TABLES"
cut -d'|' -f1 "$RESTORED_COUNTS" > "$RESTORED_TABLES"

if ! diff -u "$SOURCE_TABLES" "$RESTORED_TABLES"; then
  echo "Restored public table set differs from the source database." >&2
  exit 1
fi
echo "Public table set matches the source database."

if diff -u "$SOURCE_COUNTS" "$RESTORED_COUNTS" >/dev/null; then
  echo "Table row counts match the current source database."
else
  echo "WARNING: table row counts differ from the live source database." >&2
  echo "This can be normal when staging/production changed after the backup was created." >&2
  diff -u "$SOURCE_COUNTS" "$RESTORED_COUNTS" || true
  [[ "$REQUIRE_SOURCE_MATCH" == "true" ]] && exit 1
fi

echo
echo "=== RESTORE DRILL RESULT ==="
echo "RESTORE DRILL PASSED"
