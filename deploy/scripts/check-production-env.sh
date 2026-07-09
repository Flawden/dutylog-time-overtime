#!/usr/bin/env bash
set -Eeuo pipefail

# DutyLog production preflight.
# Usage:
#   ./deploy/scripts/check-production-env.sh
#   ENV_FILE=.env.production ./deploy/scripts/check-production-env.sh
#
# The script is intentionally conservative: it checks configuration files before
# the first VPS start, admin bootstrap checks and before risky updates. It does not print secrets.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${ENV_FILE:-.env}"
ERRORS=0
WARNINGS=0

fail() {
  echo "ERROR: $*" >&2
  ERRORS=$((ERRORS + 1))
}

warn() {
  echo "WARN:  $*" >&2
  WARNINGS=$((WARNINGS + 1))
}

ok() {
  echo "OK:    $*"
}

require_file() {
  local path="$1"
  if [[ -f "$path" ]]; then
    ok "found $path"
  else
    fail "missing $path"
  fi
}

is_placeholder() {
  local value="${1:-}"
  [[ -z "$value" || "$value" == *change_me* || "$value" == *example.com* || "$value" == "dutylog.example.com" ]]
}

require_value() {
  local name="$1"
  local value="${!name:-}"
  if is_placeholder "$value"; then
    fail "$name is empty or still looks like a placeholder"
  else
    ok "$name is set"
  fi
}


require_admin_username() {
  local name="$1"
  local value="${!name:-}"
  if is_placeholder "$value"; then
    fail "$name is empty or still looks like a placeholder"
    return
  fi
  if [[ ${#value} -lt 3 || ${#value} -gt 40 || ! "$value" =~ ^[A-Za-zА-Яа-яЁё0-9_.-]+$ ]]; then
    fail "$name must be 3-40 chars: letters, digits, dot, dash or underscore"
  else
    ok "$name is valid"
  fi
}

require_secret() {
  local name="$1"
  local value="${!name:-}"
  if is_placeholder "$value"; then
    fail "$name is empty or still looks like a placeholder"
    return
  fi
  if (( ${#value} < 20 )); then
    fail "$name is too short; use a long random value"
  else
    ok "$name looks non-empty and long enough"
  fi
}

require_distinct() {
  local left_name="$1"
  local right_name="$2"
  local left_value="${!left_name:-}"
  local right_value="${!right_name:-}"
  if [[ -n "$left_value" && -n "$right_value" && "$left_value" == "$right_value" ]]; then
    fail "$left_name and $right_name must be different secrets"
  else
    ok "$left_name and $right_name are distinct"
  fi
}

require_domain_value() {
  local name="$1"
  local value="${!name:-}"
  if is_placeholder "$value"; then
    fail "$name is empty or still looks like a placeholder"
    return
  fi
  if [[ "$value" == http://* || "$value" == https://* || "$value" == */* ]]; then
    fail "$name should be a bare domain, not a URL: $value"
  else
    ok "$name looks like a bare domain"
  fi
}

require_command() {
  if command -v "$1" >/dev/null 2>&1; then
    ok "command available: $1"
  else
    warn "command not found: $1"
  fi
}

echo "DutyLog production preflight"
echo "Project: $PROJECT_ROOT"
echo "Env:     $ENV_FILE"
echo

require_file "docker-compose.prod.yml"
require_file ".env.production.example"
require_file "deploy/caddy/Caddyfile.example"
require_file "deploy/scripts/backup-postgres.sh"
require_file "deploy/scripts/restore-postgres.sh"
require_file "deploy/scripts/smoke-test.sh"

if [[ ! -f "$ENV_FILE" ]]; then
  fail "production env file not found: $ENV_FILE"
else
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a

  require_domain_value DUTYLOG_DOMAIN
  require_value POSTGRES_DB
  require_value POSTGRES_USER
  require_secret POSTGRES_PASSWORD
  require_value SPRING_DATASOURCE_URL
  require_value SPRING_DATASOURCE_USERNAME
  require_secret SPRING_DATASOURCE_PASSWORD
  require_admin_username DUTYLOG_ADMIN_USERNAME
  require_secret DUTYLOG_ADMIN_PASSWORD
  require_distinct DUTYLOG_ADMIN_PASSWORD POSTGRES_PASSWORD
  require_distinct DUTYLOG_ADMIN_PASSWORD SPRING_DATASOURCE_PASSWORD
  if [[ "${DUTYLOG_ADMIN_FORCE_PASSWORD_RESET:-false}" == "true" ]]; then
    warn "DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true; use only for emergency admin password recovery and disable after login"
  else
    ok "DUTYLOG_ADMIN_FORCE_PASSWORD_RESET is not enabled"
  fi

  if [[ "${SPRING_PROFILES_ACTIVE:-}" != "prod" ]]; then
    fail "SPRING_PROFILES_ACTIVE should be 'prod' for VPS launch"
  else
    ok "SPRING_PROFILES_ACTIVE=prod"
  fi

  if [[ "${DUTYLOG_TELEGRAM_ENABLED:-false}" == "true" ]]; then
    require_value DUTYLOG_TELEGRAM_BOT_USERNAME
    require_secret DUTYLOG_TELEGRAM_BOT_TOKEN
    if [[ "${DUTYLOG_TELEGRAM_POLLING_ENABLED:-false}" != "true" ]]; then
      warn "Telegram is enabled, but polling is disabled. This is OK only if another delivery mode is configured."
    else
      ok "Telegram polling enabled"
    fi
  else
    ok "Telegram disabled for launch"
  fi
fi

if [[ -f "deploy/caddy/Caddyfile" ]]; then
  ok "found deploy/caddy/Caddyfile"
else
  warn "deploy/caddy/Caddyfile is missing; copy deploy/caddy/Caddyfile.example before starting production compose"
fi

if [[ -d "backups" ]]; then
  ok "backup directory exists"
else
  warn "backup directory does not exist yet; backup script will create it"
fi

if grep -Eq '^[[:space:]]+-[[:space:]]+"?8080:8080"?' docker-compose.prod.yml; then
  fail "docker-compose.prod.yml exposes app port 8080 publicly; production should expose only Caddy ports 80/443"
else
  ok "production compose does not publish app port 8080"
fi

if [[ -f "deploy/caddy/Caddyfile" ]]; then
  if grep -q "Strict-Transport-Security" deploy/caddy/Caddyfile && grep -q "X-Content-Type-Options nosniff" deploy/caddy/Caddyfile; then
    ok "Caddyfile contains basic security headers"
  else
    warn "Caddyfile is missing one or more recommended security headers"
  fi
fi

require_command docker
require_command curl

if command -v docker >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then
    ok "docker compose plugin available"
  else
    warn "docker compose plugin not available or not running"
  fi
fi

echo
if (( ERRORS > 0 )); then
  echo "Production preflight failed: $ERRORS error(s), $WARNINGS warning(s)." >&2
  exit 1
fi

echo "Production preflight passed: 0 errors, $WARNINGS warning(s)."
