#!/usr/bin/env bash
set -Eeuo pipefail

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
EXPECTED_ENV="${1:-${DUTYLOG_ENVIRONMENT:-}}"
[[ -f "$ENV_FILE" ]] || { echo "Environment file not found: $ENV_FILE" >&2; exit 2; }

set -a
# DutyLog environment files must use shell-compatible KEY=value syntax.
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

ERRORS=0
fail() { echo "ERROR: $*" >&2; ERRORS=$((ERRORS + 1)); }
ok() { echo "OK:    $*"; }
placeholder() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == *change_me* || "$value" == *replace_me* || "$value" == *example.com* ]]
}
require_value() {
  local name="$1"
  local value="${!name:-}"
  if placeholder "$value"; then fail "$name is empty or still a placeholder"; else ok "$name is set"; fi
}
require_length() {
  local name="$1" minimum="$2"
  local value="${!name:-}"
  if (( ${#value} < minimum )); then fail "$name must be at least $minimum characters"; else ok "$name length is acceptable"; fi
}
require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then fail "$name must be a positive integer"; else ok "$name is a positive integer"; fi
}
require_memory_limit() {
  local name="$1"
  local value="${!name:-}"
  if [[ ! "$value" =~ ^[1-9][0-9]*[mMgG]$ ]]; then
    fail "$name must use Docker memory syntax such as 256m or 1g"
  else
    ok "$name has a valid memory limit"
  fi
}

case "${DUTYLOG_ENVIRONMENT:-}" in staging|production) ok "valid DUTYLOG_ENVIRONMENT" ;; *) fail "DUTYLOG_ENVIRONMENT must be staging or production" ;; esac
if [[ -n "$EXPECTED_ENV" && "${DUTYLOG_ENVIRONMENT:-}" != "$EXPECTED_ENV" ]]; then
  fail "environment file is for ${DUTYLOG_ENVIRONMENT:-unknown}, expected $EXPECTED_ENV"
fi

for name in DUTYLOG_PROJECT_NAME DUTYLOG_BASE_URL DUTYLOG_BIND_ADDRESS DUTYLOG_BIND_PORT \
  DUTYLOG_APP_MEMORY_LIMIT DUTYLOG_DB_MEMORY_LIMIT DUTYLOG_APP_PIDS_LIMIT DUTYLOG_DB_PIDS_LIMIT \
  POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DUTYLOG_ADMIN_USERNAME DUTYLOG_ADMIN_PASSWORD; do
  require_value "$name"
done
require_length POSTGRES_PASSWORD 20
require_length DUTYLOG_ADMIN_PASSWORD 20
require_memory_limit DUTYLOG_APP_MEMORY_LIMIT
require_memory_limit DUTYLOG_DB_MEMORY_LIMIT
require_positive_integer DUTYLOG_APP_PIDS_LIMIT
require_positive_integer DUTYLOG_DB_PIDS_LIMIT

if [[ "${POSTGRES_PASSWORD:-}" == "${DUTYLOG_ADMIN_PASSWORD:-}" ]]; then
  fail "database and administrator passwords must be different"
else
  ok "database and administrator passwords differ"
fi
if [[ "${DUTYLOG_BASE_URL:-}" != https://* ]]; then
  fail "DUTYLOG_BASE_URL must use HTTPS"
else
  ok "DUTYLOG_BASE_URL uses HTTPS"
fi

# The system nginx is the public edge. The application port must never bind to all interfaces.
if [[ "${DUTYLOG_BIND_ADDRESS:-}" != "127.0.0.1" ]]; then
  fail "DUTYLOG_BIND_ADDRESS must be exactly 127.0.0.1 behind the system nginx"
else
  ok "application bind address is loopback-only"
fi
if [[ ! "${DUTYLOG_BIND_PORT:-}" =~ ^[0-9]+$ ]]; then
  fail "DUTYLOG_BIND_PORT must be numeric"
elif (( DUTYLOG_BIND_PORT < 1024 || DUTYLOG_BIND_PORT > 65535 )); then
  fail "DUTYLOG_BIND_PORT must be between 1024 and 65535"
else
  ok "application bind port is valid"
fi
if [[ "${DUTYLOG_SECURITY_TRUST_PROXY_HEADERS:-false}" != "true" ]]; then
  fail "DUTYLOG_SECURITY_TRUST_PROXY_HEADERS must be true behind the supplied nginx config"
else
  ok "trusted proxy mode is enabled for the loopback-only nginx edge"
fi
if [[ "${DUTYLOG_SECURITY_RATE_LIMIT_ENABLED:-true}" != "true" ]]; then
  fail "DUTYLOG_SECURITY_RATE_LIMIT_ENABLED must remain true"
else
  ok "application authentication rate limiting is enabled"
fi

if [[ "${DUTYLOG_ENVIRONMENT:-}" == production ]]; then
  [[ "${DUTYLOG_PROJECT_NAME:-}" == dutylog-production ]] || fail "production project name must be dutylog-production"
  [[ "${DUTYLOG_BACKUP_BEFORE_DEPLOY:-}" == true ]] || fail "production requires DUTYLOG_BACKUP_BEFORE_DEPLOY=true"
  [[ "${DUTYLOG_REGISTRATION_DEFAULT_ENABLED:-false}" == false ]] || fail "production registration must start closed"
else
  [[ "${DUTYLOG_PROJECT_NAME:-}" == dutylog-staging ]] || fail "staging project name must be dutylog-staging"
fi

if (( ERRORS > 0 )); then
  echo "Deployment environment check failed: $ERRORS error(s)." >&2
  exit 1
fi
echo "Deployment environment check passed."
