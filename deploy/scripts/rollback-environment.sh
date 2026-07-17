#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="${DUTYLOG_ENV_FILE:-.env}"
STATE_FILE="${DUTYLOG_STATE_FILE:-.deploy-state}"
if [[ ! -f "$ENV_FILE" || ! -f "$STATE_FILE" ]]; then
  echo "Both $ENV_FILE and $STATE_FILE are required." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
# shellcheck disable=SC1090
. "$STATE_FILE"
set +a

if [[ -z "${PREVIOUS_IMAGE:-}" ]]; then
  echo "No previous image is recorded." >&2
  exit 1
fi
if [[ "${CONFIRM_ROLLBACK:-}" != "ROLLBACK" ]]; then
  echo "Set CONFIRM_ROLLBACK=ROLLBACK to perform an application rollback." >&2
  exit 1
fi

echo "Rolling ${DUTYLOG_ENVIRONMENT:-unknown} back from ${CURRENT_IMAGE:-unknown} to $PREVIOUS_IMAGE"
echo "This changes the application image only. Flyway migrations are not reversed."

exec bash deploy/scripts/deploy-environment.sh \
  --environment "${DUTYLOG_ENVIRONMENT:?}" \
  --image "$PREVIOUS_IMAGE" \
  --release-version "${PREVIOUS_RELEASE_VERSION:-27.2.15}" \
  --build-version "${PREVIOUS_BUILD_VERSION:-rollback}" \
  --tree "${PREVIOUS_TREE:?Previous deployment tree is missing; automatic rollback is unsafe}" \
  --commit "${PREVIOUS_COMMIT:-unknown}" \
  --build-time "${PREVIOUS_BUILD_TIME:-unknown}" \
  --env-file "$ENV_FILE" \
  --base-url "${DUTYLOG_BASE_URL:-}"
