#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
[[ -f "$ENV_FILE" ]] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

if [[ "${DUTYLOG_BIND_ADDRESS:-}" != "127.0.0.1" ]]; then
  echo "Refusing local smoke test because DUTYLOG_BIND_ADDRESS is not 127.0.0.1." >&2
  exit 1
fi
if [[ ! "${DUTYLOG_BIND_PORT:-}" =~ ^[0-9]+$ ]]; then
  echo "DUTYLOG_BIND_PORT must be numeric." >&2
  exit 2
fi

LOCAL_URL="http://${DUTYLOG_BIND_ADDRESS}:${DUTYLOG_BIND_PORT}"
echo "Running loopback smoke test against $LOCAL_URL"
DUTYLOG_RELEASE_VERSION="${DUTYLOG_RELEASE_VERSION:-27.12.1}" \
DUTYLOG_SMOKE_REQUIRE_AUTH=true \
  bash deploy/scripts/smoke-test.sh "$LOCAL_URL"
