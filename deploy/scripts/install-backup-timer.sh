#!/usr/bin/env bash
set -Eeuo pipefail

# Installs one environment-specific systemd service/timer for DutyLog backups.
# Usage: sudo bash deploy/scripts/install-backup-timer.sh /opt/dutylog/staging dutylog-deploy [calendar]

ROOT="${1:-}"
RUN_USER="${2:-}"
CALENDAR="${3:-*-*-* 03:30:00}"
SYSTEMD_DIR="${DUTYLOG_SYSTEMD_DIR:-/etc/systemd/system}"
SKIP_SYSTEMCTL="${DUTYLOG_SKIP_SYSTEMCTL:-false}"

[[ -n "$ROOT" && -d "$ROOT" ]] || { echo "DutyLog environment root is required." >&2; exit 2; }
ROOT="$(cd "$ROOT" && pwd)"
ENV_FILE="$ROOT/.env"
COMPOSE_FILE="$ROOT/deploy/compose/docker-compose.deploy.yml"
BACKUP_SCRIPT="$ROOT/deploy/scripts/backup-postgres.sh"
CHECK_SCRIPT="$ROOT/deploy/scripts/check-backup-freshness.sh"

[[ -f "$ENV_FILE" ]] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }
[[ -f "$COMPOSE_FILE" ]] || { echo "Compose file not found: $COMPOSE_FILE" >&2; exit 2; }
[[ -x "$BACKUP_SCRIPT" ]] || { echo "Backup script is not executable: $BACKUP_SCRIPT" >&2; exit 2; }
[[ -x "$CHECK_SCRIPT" ]] || { echo "Freshness script is not executable: $CHECK_SCRIPT" >&2; exit 2; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

ENVIRONMENT="${DUTYLOG_ENVIRONMENT:?DUTYLOG_ENVIRONMENT is required}"
PROJECT_NAME="${DUTYLOG_PROJECT_NAME:?DUTYLOG_PROJECT_NAME is required}"
RUN_USER="${RUN_USER:-$(stat -c %U "$ENV_FILE")}"
id "$RUN_USER" >/dev/null 2>&1 || { echo "System user not found: $RUN_USER" >&2; exit 2; }
RUN_GROUP="$(id -gn "$RUN_USER")"
SAFE_ENV="$(printf '%s' "$ENVIRONMENT" | tr -cs 'A-Za-z0-9_-' '-')"
UNIT="dutylog-${SAFE_ENV}-backup"
SERVICE_FILE="$SYSTEMD_DIR/$UNIT.service"
TIMER_FILE="$SYSTEMD_DIR/$UNIT.timer"

if [[ "$SYSTEMD_DIR" == "/etc/systemd/system" && "$EUID" -ne 0 ]]; then
  echo "Run as root to install units into /etc/systemd/system." >&2
  exit 2
fi

mkdir -p "$SYSTEMD_DIR"
SERVICE_TMP="$(mktemp)"
TIMER_TMP="$(mktemp)"
trap 'rm -f "$SERVICE_TMP" "$TIMER_TMP"' EXIT

cat > "$SERVICE_TMP" <<UNIT
[Unit]
Description=DutyLog $ENVIRONMENT PostgreSQL backup
Wants=docker.service
After=docker.service

[Service]
Type=oneshot
User=$RUN_USER
Group=$RUN_GROUP
WorkingDirectory=$ROOT
Environment=DUTYLOG_ENV_FILE=$ENV_FILE
Environment=DUTYLOG_COMPOSE_FILE=$COMPOSE_FILE
Environment=DUTYLOG_PROJECT_NAME=$PROJECT_NAME
UMask=0077
Nice=10
IOSchedulingClass=best-effort
IOSchedulingPriority=7
NoNewPrivileges=true
ExecStart=/usr/bin/bash $BACKUP_SCRIPT
ExecStartPost=/usr/bin/bash $CHECK_SCRIPT
UNIT

cat > "$TIMER_TMP" <<UNIT
[Unit]
Description=Scheduled DutyLog $ENVIRONMENT PostgreSQL backup

[Timer]
OnCalendar=$CALENDAR
Persistent=true
RandomizedDelaySec=15m
AccuracySec=1m
Unit=$UNIT.service

[Install]
WantedBy=timers.target
UNIT

install -m 0644 "$SERVICE_TMP" "$SERVICE_FILE"
install -m 0644 "$TIMER_TMP" "$TIMER_FILE"
trap - EXIT
rm -f "$SERVICE_TMP" "$TIMER_TMP"

echo "Installed: $SERVICE_FILE"
echo "Installed: $TIMER_FILE"

if [[ "$SKIP_SYSTEMCTL" != "true" ]]; then
  systemctl daemon-reload
  systemctl enable --now "$UNIT.timer"
  systemctl status "$UNIT.timer" --no-pager -l
  systemctl list-timers "$UNIT.timer" --no-pager
else
  echo "DUTYLOG_SKIP_SYSTEMCTL=true: unit files rendered without systemctl changes."
fi
