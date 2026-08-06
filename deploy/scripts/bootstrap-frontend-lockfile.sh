#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
ATTEMPTS="${DUTYLOG_FRONTEND_INSTALL_ATTEMPTS:-3}"
DELAY_SECONDS="${DUTYLOG_FRONTEND_RETRY_DELAY_SECONDS:-15}"
EXPECTED_NODE="$(tr -d '\r\n' < "$FRONTEND_DIR/.node-version")"
EXPECTED_NPM="$(tr -d '\r\n' < "$FRONTEND_DIR/.npm-version")"

for required in package.json .node-version .npm-version .npmrc scripts/verify-authentic-lockfile.mjs; do
  if [[ ! -f "$FRONTEND_DIR/$required" ]]; then
    echo "Vue frontend bootstrap file is missing: $FRONTEND_DIR/$required" >&2
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

rm -rf "$FRONTEND_DIR/node_modules"
rm -f "$FRONTEND_DIR/package-lock.json" "$FRONTEND_DIR/generated-lockfile-manifest.txt"

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "Vue frontend authentic lockfile generation attempt ${attempt}/${ATTEMPTS}"
  if npm --prefix "$FRONTEND_DIR" install --package-lock-only --ignore-scripts --no-audit --no-fund --prefer-online; then
    break
  fi
  if [[ "$attempt" -eq "$ATTEMPTS" ]]; then
    echo "Vue frontend authentic lockfile generation failed after ${ATTEMPTS} attempts." >&2
    exit 1
  fi
  echo "Vue frontend lockfile generation failed; retrying in ${DELAY_SECONDS}s..." >&2
  sleep "$DELAY_SECONDS"
done

node "$FRONTEND_DIR/scripts/verify-authentic-lockfile.mjs"
LOCK_SHA="$(sha256sum "$FRONTEND_DIR/package-lock.json" | awk '{print $1}')"
PACKAGE_COUNT="$(node -e 'const l=require(process.argv[1]); console.log(Object.keys(l.packages ?? {}).length - 1)' "$FRONTEND_DIR/package-lock.json")"
cat > "$FRONTEND_DIR/generated-lockfile-manifest.txt" <<MANIFEST
release=27.37.1
node=$ACTUAL_NODE
npm=$ACTUAL_NPM
lockfileVersion=3
packages=$PACKAGE_COUNT
sha256=$LOCK_SHA
source=npm-install-package-lock-only
nextAction=review-lockfile-diff-and-commit-in-a-dedicated-dependency-change
MANIFEST

echo "Authentic npm lockfile maintenance artifact generated: $PACKAGE_COUNT packages, SHA-256 $LOCK_SHA"
