#!/usr/bin/env bash
set -Eeuo pipefail

# Validates GitHub Actions deployment configuration without printing secret values.
# Staging may be deliberately disabled until a VPS and GitHub Environment are ready.

write_output() {
  local name="$1" value="$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf '%s=%s\n' "$name" "$value" >> "$GITHUB_OUTPUT"
  fi
}

write_summary() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"
  fi
}

lower() {
  printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]'
}

ENABLED="$(lower "${DUTYLOG_DEPLOY_ENABLED:-false}")"
case "$ENABLED" in
  false|0|no|off|'')
    write_output configured false
    write_summary '### Staging deployment skipped'
    write_summary ''
    write_summary 'The immutable image was still built and verified on a clean PostgreSQL database.'
    write_summary 'Set the GitHub Environment variable `DUTYLOG_DEPLOY_ENABLED=true` after the staging VPS, SSH credentials and host `.env` are ready.'
    echo "Remote deployment is disabled by DUTYLOG_DEPLOY_ENABLED=${DUTYLOG_DEPLOY_ENABLED:-false}."
    exit 0
    ;;
  true|1|yes|on) ;;
  *)
    echo "DUTYLOG_DEPLOY_ENABLED must be true or false." >&2
    exit 2
    ;;
esac

required=(
  DUTYLOG_DEPLOY_ENVIRONMENT
  DUTYLOG_DEPLOY_HOST
  DUTYLOG_DEPLOY_USER
  DUTYLOG_DEPLOY_PATH
  DUTYLOG_BASE_URL
  DUTYLOG_SSH_PRIVATE_KEY
  DUTYLOG_SSH_KNOWN_HOSTS
  DUTYLOG_GHCR_USERNAME
  DUTYLOG_GHCR_TOKEN
)
missing=()
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done
if (( ${#missing[@]} > 0 )); then
  echo "Deployment is enabled, but required GitHub Environment values are missing:" >&2
  printf '  - %s\n' "${missing[@]}" >&2
  write_summary '### Deployment configuration is incomplete'
  write_summary ''
  for name in "${missing[@]}"; do
    write_summary "- Missing: \`$name\`"
  done
  exit 2
fi

case "$DUTYLOG_DEPLOY_ENVIRONMENT" in
  staging|production) ;;
  *) echo "DUTYLOG_DEPLOY_ENVIRONMENT must be staging or production." >&2; exit 2 ;;
esac
[[ "$DUTYLOG_DEPLOY_PATH" == /* ]] || { echo "DUTYLOG_DEPLOY_PATH must be absolute." >&2; exit 2; }
[[ "$DUTYLOG_BASE_URL" == https://* ]] || { echo "DUTYLOG_BASE_URL must use HTTPS." >&2; exit 2; }
[[ "$DUTYLOG_DEPLOY_HOST" != *[[:space:]]* ]] || { echo "DUTYLOG_DEPLOY_HOST must not contain whitespace." >&2; exit 2; }
[[ "$DUTYLOG_DEPLOY_USER" =~ ^[A-Za-z_][A-Za-z0-9._-]*$ ]] || { echo "DUTYLOG_DEPLOY_USER has an invalid format." >&2; exit 2; }

SSH_PORT="${DUTYLOG_DEPLOY_PORT:-22}"
[[ "$SSH_PORT" =~ ^[0-9]+$ ]] || { echo "DUTYLOG_DEPLOY_PORT must be numeric." >&2; exit 2; }
(( SSH_PORT >= 1 && SSH_PORT <= 65535 )) || { echo "DUTYLOG_DEPLOY_PORT must be between 1 and 65535." >&2; exit 2; }

if [[ "$DUTYLOG_SSH_PRIVATE_KEY" != *"BEGIN "*"PRIVATE KEY"* ]]; then
  echo "DUTYLOG_SSH_PRIVATE_KEY does not look like a private key." >&2
  exit 2
fi
if [[ "$DUTYLOG_SSH_KNOWN_HOSTS" != *"ssh-"* && "$DUTYLOG_SSH_KNOWN_HOSTS" != *"ecdsa-"* ]]; then
  echo "DUTYLOG_SSH_KNOWN_HOSTS does not contain a recognized SSH host key." >&2
  exit 2
fi
if (( ${#DUTYLOG_GHCR_TOKEN} < 8 )); then
  echo "DUTYLOG_GHCR_TOKEN is unexpectedly short." >&2
  exit 2
fi

write_output configured true
write_summary '### Deployment configuration validated'
write_summary ''
write_summary "Environment: \`$DUTYLOG_DEPLOY_ENVIRONMENT\`"
write_summary "Target: \`$DUTYLOG_DEPLOY_USER@$DUTYLOG_DEPLOY_HOST:$DUTYLOG_DEPLOY_PATH\`"
echo "Deployment configuration validated for $DUTYLOG_DEPLOY_ENVIRONMENT."
