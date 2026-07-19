#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ROOT_DIR="${DUTYLOG_DEPLOY_ROOT:-/opt/dutylog}"
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
if ! id "$DEPLOY_OWNER" >/dev/null 2>&1; then
  echo "Deployment owner does not exist: $DEPLOY_OWNER" >&2
  exit 1
fi

install -d -m 0750 \
  "$ROOT_DIR/staging" "$ROOT_DIR/staging/backups" \
  "$ROOT_DIR/production" "$ROOT_DIR/production/backups"

[[ -e "$ROOT_DIR/staging/.env.example" ]] || \
  install -m 0600 "$PROJECT_ROOT/deploy/env/.env.staging.example" "$ROOT_DIR/staging/.env.example"
[[ -e "$ROOT_DIR/production/.env.example" ]] || \
  install -m 0600 "$PROJECT_ROOT/deploy/env/.env.production.cicd.example" "$ROOT_DIR/production/.env.example"

chown -R "$DEPLOY_OWNER":"$DEPLOY_OWNER" "$ROOT_DIR/staging" "$ROOT_DIR/production"

cat <<EOF_SUMMARY
CI/CD host directories prepared under $ROOT_DIR for owner $DEPLOY_OWNER.

Active edge architecture:
  system nginx :80/:443 -> 127.0.0.1:18082 (staging)
                         -> 127.0.0.1:18083 (production)

Next steps:
  1. Copy each .env.example to .env and replace every placeholder.
  2. Install the supplied nginx site files once as root and obtain Certbot certificates.
  3. Configure GitHub staging/production Environments as documented in docs/CICD.md.

This bootstrap does not install or start Caddy and does not modify nginx.
EOF_SUMMARY
