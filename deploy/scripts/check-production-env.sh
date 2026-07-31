#!/usr/bin/env bash
set -Eeuo pipefail

# Production preflight for the CI/CD runtime behind the VPS-wide system nginx.
# Usage: ENV_FILE=/opt/dutylog/production/.env ./deploy/scripts/check-production-env.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${ENV_FILE:-.env}"
ERRORS=0
WARNINGS=0

fail() { echo "ERROR: $*" >&2; ERRORS=$((ERRORS + 1)); }
warn() { echo "WARN:  $*" >&2; WARNINGS=$((WARNINGS + 1)); }
ok() { echo "OK:    $*"; }

for file in \
  deploy/compose/docker-compose.deploy.yml \
  deploy/nginx/dutylog-production.conf.example \
  deploy/scripts/check-deploy-env.sh \
  deploy/scripts/backup-postgres.sh \
  deploy/scripts/restore-postgres.sh \
  deploy/scripts/local-smoke-test.sh \
  deploy/scripts/smoke-test.sh \
  deploy/scripts/production-smoke-test.sh; do
  [[ -f "$file" ]] && ok "found $file" || fail "missing $file"
done

if [[ ! -f "$ENV_FILE" ]]; then
  fail "production environment file not found: $ENV_FILE"
else
  if DUTYLOG_ENV_FILE="$ENV_FILE" bash deploy/scripts/check-deploy-env.sh production; then
    ok "production environment passed strict deployment checks"
  else
    fail "production environment failed strict deployment checks"
  fi
fi

if grep -Fq '${DUTYLOG_BIND_ADDRESS:-127.0.0.1}:${DUTYLOG_BIND_PORT' deploy/compose/docker-compose.deploy.yml; then
  ok "Compose publishes the app through a configurable loopback binding"
else
  fail "Compose is missing the loopback-only application binding"
fi
if grep -Fq 'DUTYLOG_EDGE_NETWORK' deploy/compose/docker-compose.deploy.yml; then
  fail "active deployment Compose still depends on the legacy Caddy edge network"
else
  ok "active deployment Compose has no Caddy edge-network dependency"
fi
if grep -Fq 'proxy_pass http://127.0.0.1:18083;' deploy/nginx/dutylog-production.conf.example; then
  ok "production nginx example targets loopback port 18083"
else
  fail "production nginx example does not target loopback port 18083"
fi
if grep -Fq 'proxy_set_header X-Forwarded-For $remote_addr;' deploy/nginx/dutylog-production.conf.example; then
  ok "production nginx overwrites client-supplied forwarding headers"
else
  fail "production nginx must overwrite X-Forwarded-For"
fi
if grep -Fq 'location = /calendar-feed.ics {' deploy/nginx/dutylog-production.conf.example \
    && grep -Fq 'access_log off;' deploy/nginx/dutylog-production.conf.example; then
  ok "production nginx suppresses access logs for calendar bearer URLs"
else
  fail "production nginx must disable access logging for /calendar-feed.ics"
fi

for command in docker curl; do
  if command -v "$command" >/dev/null 2>&1; then
    ok "command available: $command"
  else
    warn "command not found in this shell: $command"
  fi
done
if command -v docker >/dev/null 2>&1 && ! docker compose version >/dev/null 2>&1; then
  warn "Docker Compose plugin is not available"
fi

echo
if (( ERRORS > 0 )); then
  echo "Production preflight failed: $ERRORS error(s), $WARNINGS warning(s)." >&2
  exit 1
fi

echo "Production preflight passed: 0 errors, $WARNINGS warning(s)."
