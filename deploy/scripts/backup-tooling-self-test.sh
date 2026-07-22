#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

TMP="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP"
}
trap cleanup EXIT

fail() {
  echo "BACKUP TOOLING SELF-TEST FAILED: $*" >&2
  exit 1
}

mkdir -p "$TMP/bin" "$TMP/backups" "$TMP/systemd" "$TMP/environment/deploy/compose" "$TMP/environment/deploy/scripts"
LOG="$TMP/docker.log"

cat > "$TMP/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "${FAKE_DOCKER_LOG:?}"
ARGS=" $* "

if [[ "$ARGS" == *" ps --status running -q app "* ]]; then
  echo fake-app-container
  exit 0
fi
if [[ "$ARGS" == *" ps -q db "* ]]; then
  echo fake-db-container
  exit 0
fi
if [[ "$ARGS" == *" pg_isready "* ]]; then
  exit 0
fi
if [[ "$ARGS" == *" pg_dump "* ]]; then
  printf 'FAKE_POSTGRES_CUSTOM_DUMP\n'
  exit 0
fi
if [[ "$ARGS" == *" pg_restore --list "* ]]; then
  cat >/dev/null
  exit 0
fi
if [[ "$ARGS" == *" pg_restore "* && "$ARGS" == *" --clean "* ]]; then
  cat >/dev/null
  exit "${FAKE_RESTORE_EXIT:-0}"
fi
if [[ "$ARGS" == *" stop app "* || "$ARGS" == *" up -d app "* || "$ARGS" == *" up -d db "* ]]; then
  exit 0
fi

echo "Unexpected fake docker invocation: $*" >&2
exit 91
DOCKER
chmod +x "$TMP/bin/docker"

cat > "$TMP/test.env" <<EOF_ENV
DUTYLOG_ENVIRONMENT=selftest
DUTYLOG_PROJECT_NAME=dutylog-selftest
POSTGRES_DB=dutylog_selftest
POSTGRES_USER=dutylog_selftest
POSTGRES_PASSWORD=selftest-password
BACKUP_DIR=$TMP/backups
BACKUP_KEEP_LAST=2
DUTYLOG_DB_SERVICE=db
DUTYLOG_APP_SERVICE=app
EOF_ENV
: > "$TMP/compose.yml"

# Rotation starts with two old valid-looking pairs. The new backup must leave two total.
printf old-one > "$TMP/backups/dutylog-2026-01-01T00-00-00Z.dump"
printf old-two > "$TMP/backups/dutylog-2026-01-02T00-00-00Z.dump"
(
  cd "$TMP/backups"
  sha256sum dutylog-2026-01-01T00-00-00Z.dump > dutylog-2026-01-01T00-00-00Z.dump.sha256
  sha256sum dutylog-2026-01-02T00-00-00Z.dump > dutylog-2026-01-02T00-00-00Z.dump.sha256
)
touch -d '2026-01-01 UTC' "$TMP/backups/dutylog-2026-01-01T00-00-00Z.dump" "$TMP/backups/dutylog-2026-01-01T00-00-00Z.dump.sha256"
touch -d '2026-01-02 UTC' "$TMP/backups/dutylog-2026-01-02T00-00-00Z.dump" "$TMP/backups/dutylog-2026-01-02T00-00-00Z.dump.sha256"

PATH="$TMP/bin:$PATH" FAKE_DOCKER_LOG="$LOG" \
DUTYLOG_ENV_FILE="$TMP/test.env" DUTYLOG_COMPOSE_FILE="$TMP/compose.yml" \
  bash deploy/scripts/backup-postgres.sh >/dev/null

BACKUP_COUNT="$(find "$TMP/backups" -maxdepth 1 -type f -name '*.dump' | wc -l | tr -d ' ')"
[[ "$BACKUP_COUNT" == 2 ]] || fail "rotation kept $BACKUP_COUNT dumps instead of 2"
LATEST="$(find "$TMP/backups" -maxdepth 1 -type f -name '*.dump' -printf '%T@ %p\n' | sort -nr | head -n1 | cut -d' ' -f2-)"
[[ -s "$LATEST" && -f "$LATEST.sha256" ]] || fail "new backup or checksum is missing"
[[ "$(stat -c %a "$LATEST")" == 600 ]] || fail "backup permissions are not 0600"
(
  cd "$(dirname "$LATEST")"
  sha256sum -c "$(basename "$LATEST").sha256" >/dev/null
) || fail "new backup checksum is invalid"

PATH="$TMP/bin:$PATH" FAKE_DOCKER_LOG="$LOG" \
DUTYLOG_ENV_FILE="$TMP/test.env" DUTYLOG_COMPOSE_FILE="$TMP/compose.yml" \
BACKUP_MAX_AGE_HOURS=1 \
  bash deploy/scripts/check-backup-freshness.sh | grep -q '^BACKUP_HEALTHY ' \
  || fail "fresh backup health check failed"

# Restore failure must still bring a previously-running application back up.
: > "$LOG"
set +e
PATH="$TMP/bin:$PATH" FAKE_DOCKER_LOG="$LOG" FAKE_RESTORE_EXIT=17 \
DUTYLOG_ENV_FILE="$TMP/test.env" DUTYLOG_COMPOSE_FILE="$TMP/compose.yml" \
CONFIRM_RESTORE=RESTORE SKIP_PRE_RESTORE_BACKUP=true \
  bash deploy/scripts/restore-postgres.sh "$LATEST" >/dev/null 2>&1
RESTORE_STATUS=$?
set -e
[[ "$RESTORE_STATUS" == 17 ]] || fail "restore failure status was $RESTORE_STATUS instead of 17"
grep -Fq 'stop app' "$LOG" || fail "restore did not stop the running app"
grep -Fq 'up -d app' "$LOG" || fail "restore failure did not restart the app"

# Render systemd units in a temporary directory without touching the host manager.
cp "$TMP/test.env" "$TMP/environment/.env"
cp "$TMP/compose.yml" "$TMP/environment/deploy/compose/docker-compose.deploy.yml"
cp deploy/scripts/backup-postgres.sh "$TMP/environment/deploy/scripts/backup-postgres.sh"
cp deploy/scripts/check-backup-freshness.sh "$TMP/environment/deploy/scripts/check-backup-freshness.sh"
chmod +x "$TMP/environment/deploy/scripts/"*.sh
DUTYLOG_SYSTEMD_DIR="$TMP/systemd" DUTYLOG_SKIP_SYSTEMCTL=true \
  bash deploy/scripts/install-backup-timer.sh "$TMP/environment" "$(id -un)" '*-*-* 04:00:00' >/dev/null
[[ -f "$TMP/systemd/dutylog-selftest-backup.service" ]] || fail "systemd service was not rendered"
[[ -f "$TMP/systemd/dutylog-selftest-backup.timer" ]] || fail "systemd timer was not rendered"
grep -Fq "ExecStart=/usr/bin/bash $TMP/environment/deploy/scripts/backup-postgres.sh" \
  "$TMP/systemd/dutylog-selftest-backup.service" || fail "systemd service has the wrong backup command"
grep -Fq 'OnCalendar=*-*-* 04:00:00' "$TMP/systemd/dutylog-selftest-backup.timer" \
  || fail "systemd timer calendar was not rendered"

echo "Backup tooling self-test passed."
