#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${1:-${DUTYLOG_BASE_URL:-http://localhost:8080}}"
BASE_URL="${BASE_URL%/}"
TIMEOUT="${DUTYLOG_SMOKE_TIMEOUT:-10}"
VERSION="${DUTYLOG_RELEASE_VERSION:-27.14.2}"
SMOKE_USERNAME="${DUTYLOG_SMOKE_USERNAME:-${DUTYLOG_ADMIN_USERNAME:-}}"
SMOKE_PASSWORD="${DUTYLOG_SMOKE_PASSWORD:-${DUTYLOG_ADMIN_PASSWORD:-}}"
REQUIRE_AUTH="${DUTYLOG_SMOKE_REQUIRE_AUTH:-false}"
AUTHENTICATED=false
STATIC_JS=(
  "js/10-core.js"
  "js/20-data.js"
  "js/30-calendar.js"
  "js/40-overtime.js"
  "js/50-tasks.js"
  "js/60-settings.js"
  "js/70-user-boot.js"
)

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

contains_literal() {
  local haystack="$1" needle="$2"
  grep -Fq -- "$needle" <<< "$haystack"
}

contains_literal_ci() {
  local haystack="$1" needle="$2"
  grep -Fqi -- "$needle" <<< "$haystack"
}

header_value() {
  local name="$1" file="$2"
  awk -v wanted="$name" '
    BEGIN { IGNORECASE=1 }
    $1 == wanted ":" {
      sub(/^[^:]+:[[:space:]]*/, "")
      sub(/\r$/, "")
      value=$0
    }
    END { print value }
  ' "$file"
}

prepare_cookie_jar() {
  local source="$1" target="$2"
  if [[ "$BASE_URL" == http://127.0.0.1* || "$BASE_URL" == http://localhost* ]]; then
    # Production marks JSESSIONID Secure. The loopback probe reaches Tomcat over
    # HTTP behind nginx, so a copied jar relaxes only the local test transport.
    awk 'BEGIN { OFS="\t" }
      /^#HttpOnly_/ && NF >= 7 { $4="FALSE"; print; next }
      /^#/ { print; next }
      NF >= 7 { $4="FALSE"; print; next }
      { print }' "$source" > "$target"
  else
    cp "$source" "$target"
  fi
  chmod 0600 "$target"
}

need curl

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

COOKIE_JAR="$TMP_DIR/cookies.txt"
AUTH_COOKIE_JAR="$TMP_DIR/auth-cookies.txt"
LOGIN_HEADERS="$TMP_DIR/login.headers"
ROOT_HEADERS="$TMP_DIR/root.headers"
USERNAME_FILE="$TMP_DIR/username"
PASSWORD_FILE="$TMP_DIR/password"
CSRF_FILE="$TMP_DIR/csrf"
chmod 0700 "$TMP_DIR"

printf '%s' "$SMOKE_USERNAME" > "$USERNAME_FILE"
printf '%s' "$SMOKE_PASSWORD" > "$PASSWORD_FILE"
chmod 0600 "$USERNAME_FILE" "$PASSWORD_FILE"

echo "DutyLog smoke test: $BASE_URL"
echo "Expected version: $VERSION"

if [[ "$BASE_URL" == http://* && "$BASE_URL" != "http://localhost"* && "$BASE_URL" != "http://127.0.0.1"* ]]; then
  echo "WARN: public smoke test URL is not HTTPS: $BASE_URL" >&2
fi

echo "1) Actuator health"
HEALTH_JSON="$(fetch "$BASE_URL/actuator/health")"
contains_literal "$HEALTH_JSON" '"status":"UP"'
echo "   ok"

echo "2) Login page and external login runtime"
LOGIN_HTML="$(curl -fsS --max-time "$TIMEOUT" -c "$COOKIE_JAR" "$BASE_URL/login.html")"
contains_literal_ci "$LOGIN_HTML" 'DutyLog'
contains_literal "$LOGIN_HTML" "/js/login.js?v=$VERSION"
LOGIN_JS="$(fetch "$BASE_URL/js/login.js")"
contains_literal "$LOGIN_JS" 'languagePreference: currentLang'
echo "   ok"

echo "3) Browser entry point and authenticated app shell"
ROOT_CODE="$(curl -sS --max-time "$TIMEOUT" \
  -H 'Accept: text/html' \
  -D "$ROOT_HEADERS" \
  -o /dev/null \
  -w '%{http_code}' \
  "$BASE_URL/")"
ROOT_LOCATION="$(header_value Location "$ROOT_HEADERS")"
case "$ROOT_CODE" in
  301|302|303|307|308) ;;
  *) echo "Expected browser request to / to redirect, got HTTP $ROOT_CODE" >&2; exit 1 ;;
esac
case "$ROOT_LOCATION" in
  /login.html*|"$BASE_URL/login.html"*) ;;
  *) echo "Expected browser redirect to login page, got: ${ROOT_LOCATION:-missing}" >&2; exit 1 ;;
esac

if [[ -z "$SMOKE_USERNAME" || -z "$SMOKE_PASSWORD" ]]; then
  if [[ "$REQUIRE_AUTH" == "true" ]]; then
    echo "Authenticated smoke test credentials are required but missing." >&2
    exit 2
  fi
  echo "   browser redirect ok; authenticated shell skipped (credentials not supplied)"
else
  CSRF_TOKEN="$(awk '$6 == "XSRF-TOKEN" { value=$7 } END { print value }' "$COOKIE_JAR")"
  if [[ -z "$CSRF_TOKEN" ]]; then
    echo "Login page did not issue XSRF-TOKEN cookie." >&2
    exit 1
  fi
  printf '%s' "$CSRF_TOKEN" > "$CSRF_FILE"
  chmod 0600 "$CSRF_FILE"

  prepare_cookie_jar "$COOKIE_JAR" "$AUTH_COOKIE_JAR"
  LOGIN_CODE="$(curl -sS --max-time "$TIMEOUT" \
    -b "$AUTH_COOKIE_JAR" -c "$COOKIE_JAR" \
    -D "$LOGIN_HEADERS" \
    -o /dev/null \
    -w '%{http_code}' \
    --data-urlencode "username@$USERNAME_FILE" \
    --data-urlencode "password@$PASSWORD_FILE" \
    --data-urlencode "_csrf@$CSRF_FILE" \
    "$BASE_URL/perform_login")"
  LOGIN_LOCATION="$(header_value Location "$LOGIN_HEADERS")"
  case "$LOGIN_CODE" in
    302|303) ;;
    *) echo "Smoke-test login returned HTTP $LOGIN_CODE" >&2; exit 1 ;;
  esac
  if [[ "$LOGIN_LOCATION" == *"/login.html?error"* ]]; then
    echo "Smoke-test login was rejected." >&2
    exit 1
  fi

  prepare_cookie_jar "$COOKIE_JAR" "$AUTH_COOKIE_JAR"
  if ! awk '$6 == "JSESSIONID" { found=1 } END { exit found ? 0 : 1 }' "$AUTH_COOKIE_JAR"; then
    echo "Smoke-test login did not issue JSESSIONID." >&2
    exit 1
  fi
  AUTHENTICATED=true
  APP_HTML="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/")"
  contains_literal_ci "$APP_HTML" 'DutyLog'
  contains_literal "$APP_HTML" "app.css?v=$VERSION"
  for asset in "${STATIC_JS[@]}"; do
    contains_literal "$APP_HTML" "$asset?v=$VERSION"
  done
  if contains_literal "$APP_HTML" 'app.js?v='; then
    echo "Unexpected legacy app.js reference in app shell" >&2
    exit 1
  fi
  echo "   ok"
fi

echo "4) Manifest"
MANIFEST_JSON="$(fetch "$BASE_URL/manifest.json")"
contains_literal_ci "$MANIFEST_JSON" 'DutyLog'
echo "   ok"

echo "5) Service worker"
SERVICE_WORKER_JS="$(fetch "$BASE_URL/service-worker.js")"
contains_literal "$SERVICE_WORKER_JS" "dutylog-shell-v$VERSION"
echo "   ok"

echo "6) Protected static assets"
if [[ "$AUTHENTICATED" == "true" ]]; then
  CORE_JS="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/js/10-core.js")"
  contains_literal "$CORE_JS" "DUTYLOG_VERSION = \"$VERSION\""
  for asset in "${STATIC_JS[@]:1}"; do
    curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/$asset" >/dev/null
  done
  APP_CSS="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/app.css")"
  contains_literal "$APP_CSS" ':root'
  echo "   ok"
else
  echo "   skipped (authenticated session not available)"
fi

echo "7) Public registration status endpoint"
REGISTRATION_STATUS="$(fetch "$BASE_URL/api/auth/registration-status")"
contains_literal "$REGISTRATION_STATUS" '"enabled"'
echo "   ok"

echo "8) Protected API returns unauthorized/redirected/forbidden instead of crashing"
HTTP_CODE="$(status_code "$BASE_URL/api/admin/status")"
case "$HTTP_CODE" in
  401|302|403) echo "   ok ($HTTP_CODE)" ;;
  *) echo "Unexpected /api/admin/status status: $HTTP_CODE" >&2; exit 1 ;;
esac

echo "9) Authenticated read-only API contract"
if [[ "$AUTHENTICATED" == "true" ]]; then
  PROFILE_JSON="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/api/profile")"
  contains_literal "$PROFILE_JSON" '"username"'
  contains_literal "$PROFILE_JSON" '"workTimezone"'

  MODULES_JSON="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/api/modules")"
  contains_literal "$MODULES_JSON" '"enabled"'

  SESSIONS_JSON="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/api/profile/sessions")"
  contains_literal "$SESSIONS_JSON" '['

  AUTH_ME_JSON="$(curl -fsS --max-time "$TIMEOUT" -b "$AUTH_COOKIE_JAR" "$BASE_URL/api/auth/me")"
  contains_literal "$AUTH_ME_JSON" '"username"'
  echo "   ok"
else
  echo "   skipped (authenticated session not available)"
fi

echo "Smoke test passed."
