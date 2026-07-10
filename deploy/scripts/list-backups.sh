#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
[[ -d "$BACKUP_DIR" ]] || { echo "No backup directory: $BACKUP_DIR"; exit 0; }

find "$BACKUP_DIR" -maxdepth 1 -type f \
  \( -name '*.dump' -o -name '*.dump.gz' -o -name '*.sql' -o -name '*.sql.gz' \) \
  -printf '%TY-%Tm-%Td %TH:%TM  %s bytes  %p\n' | sort -r
