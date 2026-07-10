#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ROOT_DIR="${DUTYLOG_DEPLOY_ROOT:-/opt/dutylog}"
EDGE_NETWORK="${DUTYLOG_EDGE_NETWORK:-dutylog_edge}"
DEPLOY_OWNER="${DUTYLOG_DEPLOY_OWNER:-${SUDO_USER:-$(id -un)}}"

for command in docker install; do
  command -v "$command" >/dev/null 2>&1 || { echo "Required command missing: $command" >&2; exit 2; }
done
docker compose version >/dev/null

case "$(uname -m)" in
  x86_64|amd64) ;;
  *) echo "The bundled CI workflow currently publishes linux/amd64 images; this host is $(uname -m)." >&2; exit 1 ;;
esac

if [[ ! -w "$(dirname "$ROOT_DIR")" && "${EUID:-$(id -u)}" -ne 0 ]]; then
  echo "Run with sudo or set DUTYLOG_DEPLOY_ROOT to a writable path." >&2
  exit 1
fi

install -d -m 0750 "$ROOT_DIR/staging" "$ROOT_DIR/production" "$ROOT_DIR/edge/deploy/compose" "$ROOT_DIR/edge/deploy/caddy"
install -m 0644 "$PROJECT_ROOT/deploy/compose/docker-compose.edge.yml" "$ROOT_DIR/edge/deploy/compose/docker-compose.edge.yml"
install -m 0644 "$PROJECT_ROOT/deploy/caddy/Caddyfile.cicd" "$ROOT_DIR/edge/deploy/caddy/Caddyfile.cicd"

[[ -e "$ROOT_DIR/staging/.env.example" ]] || install -m 0600 "$PROJECT_ROOT/deploy/env/.env.staging.example" "$ROOT_DIR/staging/.env.example"
[[ -e "$ROOT_DIR/production/.env.example" ]] || install -m 0600 "$PROJECT_ROOT/deploy/env/.env.production.cicd.example" "$ROOT_DIR/production/.env.example"
[[ -e "$ROOT_DIR/edge/.env.example" ]] || install -m 0600 "$PROJECT_ROOT/deploy/env/.env.edge.example" "$ROOT_DIR/edge/.env.example"

if ! id "$DEPLOY_OWNER" >/dev/null 2>&1; then
  echo "Deployment owner does not exist: $DEPLOY_OWNER" >&2
  exit 1
fi
chown -R "$DEPLOY_OWNER":"$DEPLOY_OWNER" "$ROOT_DIR/staging" "$ROOT_DIR/production" "$ROOT_DIR/edge"

docker network inspect "$EDGE_NETWORK" >/dev/null 2>&1 || docker network create "$EDGE_NETWORK" >/dev/null

echo "CI/CD host directories prepared under $ROOT_DIR for owner $DEPLOY_OWNER"
echo "1. Copy each .env.example to .env and replace every placeholder."
echo "2. Start the shared edge proxy from $ROOT_DIR/edge."
echo "3. Configure GitHub staging/production Environments as documented in docs/CICD.md."
