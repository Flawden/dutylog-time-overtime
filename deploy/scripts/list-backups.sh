#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"

if [[ ! -d "$BACKUP_DIR" ]]; then
  echo "No backup directory: $BACKUP_DIR"
  exit 0
fi

find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'dutylog-*.dump' -o -name 'dutylog-*.sql.gz' -o -name 'dutylog-*.dump.gz' \) -printf '%TY-%Tm-%Td %TH:%TM  %s bytes  %p\n' | sort -r
