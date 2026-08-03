#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
ATTEMPTS="${DUTYLOG_FRONTEND_INSTALL_ATTEMPTS:-3}"
DELAY_SECONDS="${DUTYLOG_FRONTEND_RETRY_DELAY_SECONDS:-15}"

if [[ ! -f "$FRONTEND_DIR/package.json" ]]; then
  echo "Vue frontend package is missing: $FRONTEND_DIR/package.json" >&2
  exit 1
fi
if ! [[ "$ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "DUTYLOG_FRONTEND_INSTALL_ATTEMPTS must be a positive integer." >&2
  exit 2
fi

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "Vue frontend npm install attempt ${attempt}/${ATTEMPTS}"
  if npm --prefix "$FRONTEND_DIR" install --no-audit --no-fund --package-lock=false --prefer-online; then
    break
  fi
  if [[ "$attempt" -eq "$ATTEMPTS" ]]; then
    echo "Vue frontend dependency installation failed after ${ATTEMPTS} attempts." >&2
    exit 1
  fi
  echo "Vue frontend install failed; retrying in ${DELAY_SECONDS}s..." >&2
  sleep "$DELAY_SECONDS"
done

npm --prefix "$FRONTEND_DIR" run typecheck
npm --prefix "$FRONTEND_DIR" run test:unit
npm --prefix "$FRONTEND_DIR" run build

test -s "$FRONTEND_DIR/dist/dutylog-vue-app-shell.js"
test -s "$FRONTEND_DIR/dist/dutylog-vue-app-shell.css"

echo "Vue frontend gate passed."
