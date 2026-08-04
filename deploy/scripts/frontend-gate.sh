#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
ATTEMPTS="${DUTYLOG_FRONTEND_INSTALL_ATTEMPTS:-3}"
DELAY_SECONDS="${DUTYLOG_FRONTEND_RETRY_DELAY_SECONDS:-15}"
EXPECTED_NODE="$(tr -d '\r\n' < "$FRONTEND_DIR/.node-version")"
EXPECTED_NPM="$(tr -d '\r\n' < "$FRONTEND_DIR/.npm-version")"

for required in package.json package-lock.json .node-version .npm-version; do
  if [[ ! -f "$FRONTEND_DIR/$required" ]]; then
    echo "Vue frontend delivery file is missing: $FRONTEND_DIR/$required" >&2
    exit 1
  fi
done
if ! [[ "$ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "DUTYLOG_FRONTEND_INSTALL_ATTEMPTS must be a positive integer." >&2
  exit 2
fi

ACTUAL_NODE="$(node --version | sed 's/^v//')"
ACTUAL_NPM="$(npm --version)"
if [[ "$ACTUAL_NODE" != "$EXPECTED_NODE" ]]; then
  echo "Vue frontend requires Node $EXPECTED_NODE, found $ACTUAL_NODE." >&2
  exit 1
fi
if [[ "$ACTUAL_NPM" != "$EXPECTED_NPM" ]]; then
  echo "Vue frontend requires npm $EXPECTED_NPM, found $ACTUAL_NPM." >&2
  exit 1
fi

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

npm --prefix "$FRONTEND_DIR" ls --all >/dev/null
npm --prefix "$FRONTEND_DIR" run verify:delivery
npm --prefix "$FRONTEND_DIR" run contract:check
npm --prefix "$FRONTEND_DIR" run typecheck
npm --prefix "$FRONTEND_DIR" run test:unit
npm --prefix "$FRONTEND_DIR" run build
git -C "$PROJECT_ROOT" diff --exit-code -- frontend/package.json frontend/package-lock.json frontend/src/generated/dutylog-api.ts

test -s "$FRONTEND_DIR/dist/dutylog-vue-app-shell.js"
test -s "$FRONTEND_DIR/dist/dutylog-vue-app-shell.css"

echo "Vue frontend reproducible delivery gate passed."
