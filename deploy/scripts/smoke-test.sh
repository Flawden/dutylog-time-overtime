#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${DUTYLOG_BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

need curl

echo "DutyLog smoke test: $BASE_URL"

echo "1) Actuator health"
curl -fsS "$BASE_URL/actuator/health" | grep -q '"status":"UP"'
echo "   ok"

echo "2) Login page"
curl -fsS "$BASE_URL/login.html" | grep -qi 'DutyLog'
echo "   ok"

echo "3) Manifest"
curl -fsS "$BASE_URL/manifest.json" | grep -qi 'DutyLog'
echo "   ok"

echo "4) Protected API returns unauthorized/redirected instead of crashing"
HTTP_CODE="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/admin/status")"
case "$HTTP_CODE" in
  200|401|302|403) echo "   ok ($HTTP_CODE)" ;;
  *) echo "Unexpected /api/admin/status status: $HTTP_CODE" >&2; exit 1 ;;
esac

echo "Smoke test passed."
