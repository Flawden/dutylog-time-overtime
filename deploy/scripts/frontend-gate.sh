#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
ATTEMPTS="${DUTYLOG_FRONTEND_INSTALL_ATTEMPTS:-3}"
DELAY_SECONDS="${DUTYLOG_FRONTEND_RETRY_DELAY_SECONDS:-15}"

for required in package.json package-lock.json generated-lockfile-manifest.txt .node-version .npm-version .npmrc; do
  if [[ ! -s "$FRONTEND_DIR/$required" ]]; then
    echo "Vue frontend committed delivery input is missing: $FRONTEND_DIR/$required" >&2
    exit 1
  fi
done

node "$FRONTEND_DIR/scripts/verify-authentic-lockfile.mjs"
rm -rf "$FRONTEND_DIR/node_modules"

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "Vue frontend npm ci attempt ${attempt}/${ATTEMPTS}"
  if npm --prefix "$FRONTEND_DIR" ci --no-audit --no-fund --prefer-online; then
    break
  fi
  if [[ "$attempt" -eq "$ATTEMPTS" ]]; then
    echo "Vue frontend npm ci failed after ${ATTEMPTS} attempts." >&2
    exit 1
  fi
  echo "Vue frontend npm ci failed; retrying in ${DELAY_SECONDS}s..." >&2
  sleep "$DELAY_SECONDS"
done

for command in vue-tsc vitest vite; do
  if [[ ! -e "$FRONTEND_DIR/node_modules/.bin/$command" ]]; then
    echo "Vue frontend local executable is missing after npm ci: node_modules/.bin/$command" >&2
    exit 1
  fi
done

node "$FRONTEND_DIR/scripts/verify-authentic-lockfile.mjs"
npm --prefix "$FRONTEND_DIR" ls --all >/dev/null
npm --prefix "$FRONTEND_DIR" run verify:delivery
npm --prefix "$FRONTEND_DIR" run contract:check
npm --prefix "$FRONTEND_DIR" run typecheck
npm --prefix "$FRONTEND_DIR" run test:unit
npm --prefix "$FRONTEND_DIR" run build
git -C "$PROJECT_ROOT" diff --exit-code --   frontend/package.json   frontend/package-lock.json   frontend/generated-lockfile-manifest.txt   frontend/src/generated/dutylog-api.ts

test -s "$FRONTEND_DIR/dist/dutylog-vue-app-shell.js"
test -s "$FRONTEND_DIR/dist/dutylog-vue-app-shell.css"

echo "Vue frontend committed-lockfile delivery gate passed."
