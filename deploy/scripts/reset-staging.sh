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

if [[ "${DUTYLOG_ENVIRONMENT:-}" != "staging" || "${DUTYLOG_PROJECT_NAME:-}" != "dutylog-staging" ]]; then
  echo "Refusing to reset a non-staging environment." >&2
  exit 1
fi
if [[ "${RESET_STAGING:-}" != "RESET" ]]; then
  echo "Set RESET_STAGING=RESET to delete the staging database and logs." >&2
  exit 1
fi

COMPOSE_FILE="${DUTYLOG_COMPOSE_FILE:-deploy/compose/docker-compose.deploy.yml}"
PROJECT_NAME="${DUTYLOG_PROJECT_NAME:-dutylog-staging}"
export DUTYLOG_IMAGE="${DUTYLOG_IMAGE:-ghcr.io/invalid/invalid@sha256:0000000000000000000000000000000000000000000000000000000000000000}"

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" -p "$PROJECT_NAME" down -v --remove-orphans
rm -f .deploy-state .deploy.lock
echo "Staging database and logs deleted. Existing backup files were preserved. Push test again to recreate the database from migrations."
