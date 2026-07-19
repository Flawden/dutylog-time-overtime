#!/usr/bin/env bash
set -Eeuo pipefail

# Runs on a CI runner and deploys the immutable image to one prepared host.
# Secrets are supplied through environment variables and are never copied into the repository.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

required=(
  DUTYLOG_DEPLOY_HOST DUTYLOG_DEPLOY_USER DUTYLOG_DEPLOY_PATH
  DUTYLOG_SSH_PRIVATE_KEY DUTYLOG_SSH_KNOWN_HOSTS
  DUTYLOG_GHCR_USERNAME DUTYLOG_GHCR_TOKEN
  DUTYLOG_DEPLOY_ENVIRONMENT DUTYLOG_IMAGE_REF DUTYLOG_RELEASE_VERSION
  DUTYLOG_BUILD_VERSION DUTYLOG_BUILD_TREE DUTYLOG_BUILD_COMMIT DUTYLOG_BUILD_TIME
)
missing=()
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done
if (( ${#missing[@]} > 0 )); then
  echo "Required deployment environment variables are empty:" >&2
  printf '  - %s\n' "${missing[@]}" >&2
  exit 2
fi

case "$DUTYLOG_DEPLOY_ENVIRONMENT" in staging|production) ;; *) echo "Invalid deployment environment" >&2; exit 2 ;; esac
[[ "$DUTYLOG_IMAGE_REF" =~ @sha256:[0-9a-fA-F]{64}$ ]] || { echo "Image must be pinned by digest" >&2; exit 2; }
[[ "$DUTYLOG_DEPLOY_PATH" == /* ]] || { echo "DUTYLOG_DEPLOY_PATH must be absolute" >&2; exit 2; }

SSH_PORT="${DUTYLOG_DEPLOY_PORT:-22}"
BASE_URL="${DUTYLOG_BASE_URL:-}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR/.ssh"
printf '%s\n' "$DUTYLOG_SSH_PRIVATE_KEY" > "$TMP_DIR/.ssh/key"
printf '%s\n' "$DUTYLOG_SSH_KNOWN_HOSTS" > "$TMP_DIR/.ssh/known_hosts"
chmod 600 "$TMP_DIR/.ssh/key" "$TMP_DIR/.ssh/known_hosts"

SSH=(ssh -p "$SSH_PORT" -i "$TMP_DIR/.ssh/key" -o BatchMode=yes -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$TMP_DIR/.ssh/known_hosts")
REMOTE="$DUTYLOG_DEPLOY_USER@$DUTYLOG_DEPLOY_HOST"

printf -v Q_PATH '%q' "$DUTYLOG_DEPLOY_PATH"
printf -v Q_REG_USER '%q' "$DUTYLOG_GHCR_USERNAME"
"${SSH[@]}" "$REMOTE" "mkdir -p $Q_PATH/backups"

# Only deployment code is replaced. The remote .env, backups and state remain host-local.
tar -czf "$TMP_DIR/deploy-bundle.tgz" \
  deploy/compose/docker-compose.deploy.yml \
  deploy/scripts/deploy-environment.sh \
  deploy/scripts/backup-postgres.sh \
  deploy/scripts/check-deploy-env.sh \
  deploy/scripts/list-backups.sh \
  deploy/scripts/restore-postgres.sh \
  deploy/scripts/rollback-environment.sh \
  deploy/scripts/reset-staging.sh \
  deploy/scripts/smoke-test.sh
cat "$TMP_DIR/deploy-bundle.tgz" | "${SSH[@]}" "$REMOTE" "tar -xzf - -C $Q_PATH"

# Registry credential is sent only through stdin to Docker on the target host.
printf '%s' "$DUTYLOG_GHCR_TOKEN" | "${SSH[@]}" "$REMOTE" "docker login ghcr.io -u $Q_REG_USER --password-stdin >/dev/null"

printf -v Q_ENV '%q' "$DUTYLOG_DEPLOY_ENVIRONMENT"
printf -v Q_IMAGE '%q' "$DUTYLOG_IMAGE_REF"
printf -v Q_RELEASE '%q' "$DUTYLOG_RELEASE_VERSION"
printf -v Q_BUILD '%q' "$DUTYLOG_BUILD_VERSION"
printf -v Q_TREE '%q' "$DUTYLOG_BUILD_TREE"
printf -v Q_COMMIT '%q' "$DUTYLOG_BUILD_COMMIT"
printf -v Q_TIME '%q' "$DUTYLOG_BUILD_TIME"
printf -v Q_URL '%q' "$BASE_URL"

REMOTE_COMMAND="cd $Q_PATH && chmod +x deploy/scripts/*.sh && bash deploy/scripts/deploy-environment.sh --environment $Q_ENV --image $Q_IMAGE --release-version $Q_RELEASE --build-version $Q_BUILD --tree $Q_TREE --commit $Q_COMMIT --build-time $Q_TIME --env-file .env --base-url $Q_URL"
"${SSH[@]}" "$REMOTE" "$REMOTE_COMMAND"
