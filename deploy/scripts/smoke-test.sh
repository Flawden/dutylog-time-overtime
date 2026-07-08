#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${DUTYLOG_BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"
TIMEOUT="${DUTYLOG_SMOKE_TIMEOUT:-10}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 2
  fi
}

fetch() {
  curl -fsS --max-time "$TIMEOUT" "$@"
}

status_code() {
  curl -sS --max-time "$TIMEOUT" -o /dev/null -w '%{http_code}' "$@"
}

need curl

echo "DutyLog smoke test: $BASE_URL"

if [[ "$BASE_URL" == http://* && "$BASE_URL" != "http://localhost"* && "$BASE_URL" != "http://127.0.0.1"* ]]; then
  echo "WARN: public smoke test URL is not HTTPS: $BASE_URL" >&2
fi

echo "1) Actuator health"
fetch "$BASE_URL/actuator/health" | grep -q '"status":"UP"'
echo "   ok"

echo "2) Login page"
fetch "$BASE_URL/login.html" | grep -qi 'DutyLog'
echo "   ok"

echo "3) App shell"
fetch "$BASE_URL/" | grep -qi 'DutyLog'
echo "   ok"

echo "4) Manifest"
fetch "$BASE_URL/manifest.json" | grep -qi 'DutyLog'
echo "   ok"

echo "5) Service worker"
fetch "$BASE_URL/service-worker.js" | grep -q 'dutylog-shell-v24.0.2'
echo "   ok"

echo "6) Static assets"
fetch "$BASE_URL/app.js" | grep -q 'DUTYLOG_VERSION = "24.0.2"'
fetch "$BASE_URL/app.css" | grep -q ':root'
echo "   ok"

echo "7) Public registration status endpoint"
fetch "$BASE_URL/api/auth/registration-status" | grep -q '"enabled"'
echo "   ok"

echo "8) Protected API returns unauthorized/redirected/forbidden instead of crashing"
HTTP_CODE="$(status_code "$BASE_URL/api/admin/status")"
case "$HTTP_CODE" in
  200|401|302|403) echo "   ok ($HTTP_CODE)" ;;
  *) echo "Unexpected /api/admin/status status: $HTTP_CODE" >&2; exit 1 ;;
esac

echo "Smoke test passed."
