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
  local name="$1" value="${!name:-}"
  if placeholder "$value"; then fail "$name is empty or still a placeholder"; else ok "$name is set"; fi
}
require_length() {
  local name="$1" minimum="$2" value="${!name:-}"
  if (( ${#value} < minimum )); then fail "$name must be at least $minimum characters"; else ok "$name length is acceptable"; fi
}

case "${DUTYLOG_ENVIRONMENT:-}" in staging|production) ok "valid DUTYLOG_ENVIRONMENT" ;; *) fail "DUTYLOG_ENVIRONMENT must be staging or production" ;; esac
if [[ -n "$EXPECTED_ENV" && "${DUTYLOG_ENVIRONMENT:-}" != "$EXPECTED_ENV" ]]; then
  fail "environment file is for ${DUTYLOG_ENVIRONMENT:-unknown}, expected $EXPECTED_ENV"
fi

for name in DUTYLOG_PROJECT_NAME DUTYLOG_APP_ALIAS DUTYLOG_BASE_URL POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD DUTYLOG_ADMIN_USERNAME DUTYLOG_ADMIN_PASSWORD; do
  require_value "$name"
done
require_length POSTGRES_PASSWORD 20
require_length DUTYLOG_ADMIN_PASSWORD 20

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
if [[ "${DUTYLOG_ENVIRONMENT:-}" == production ]]; then
  [[ "${DUTYLOG_PROJECT_NAME:-}" == dutylog-production ]] || fail "production project name must be dutylog-production"
  [[ "${DUTYLOG_APP_ALIAS:-}" == dutylog-production-app ]] || fail "production alias must be dutylog-production-app"
  [[ "${DUTYLOG_BACKUP_BEFORE_DEPLOY:-}" == true ]] || fail "production requires DUTYLOG_BACKUP_BEFORE_DEPLOY=true"
  [[ "${DUTYLOG_REGISTRATION_DEFAULT_ENABLED:-false}" == false ]] || fail "production registration must start closed"
else
  [[ "${DUTYLOG_PROJECT_NAME:-}" == dutylog-staging ]] || fail "staging project name must be dutylog-staging"
  [[ "${DUTYLOG_APP_ALIAS:-}" == dutylog-staging-app ]] || fail "staging alias must be dutylog-staging-app"
fi

if (( ERRORS > 0 )); then
  echo "Deployment environment check failed: $ERRORS error(s)." >&2
  exit 1
fi
echo "Deployment environment check passed."
