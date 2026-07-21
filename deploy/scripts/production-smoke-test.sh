#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${1:-${DUTYLOG_BASE_URL:-}}"
BASE_URL="${BASE_URL%/}"

if [[ -z "$BASE_URL" ]]; then
  echo "DUTYLOG_BASE_URL or the first URL argument is required." >&2
  exit 2
fi
if [[ "$BASE_URL" != https://* ]]; then
  echo "Production smoke test requires an https:// URL: $BASE_URL" >&2
  exit 2
fi
if [[ -z "${DUTYLOG_SMOKE_USERNAME:-${DUTYLOG_ADMIN_USERNAME:-}}" || -z "${DUTYLOG_SMOKE_PASSWORD:-${DUTYLOG_ADMIN_PASSWORD:-}}" ]]; then
  echo "Production smoke credentials are required." >&2
  exit 2
fi

DUTYLOG_SMOKE_REQUIRE_AUTH=true \
  bash "$(dirname "$0")/smoke-test.sh" "$BASE_URL"
