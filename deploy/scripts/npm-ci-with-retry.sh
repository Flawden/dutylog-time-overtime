#!/usr/bin/env bash
set -Eeuo pipefail

ATTEMPTS="${NPM_CI_ATTEMPTS:-3}"
DELAY_SECONDS="${NPM_CI_RETRY_DELAY_SECONDS:-15}"

if ! [[ "$ATTEMPTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "NPM_CI_ATTEMPTS must be a positive integer." >&2
  exit 2
fi

for attempt in $(seq 1 "$ATTEMPTS"); do
  echo "npm ci attempt ${attempt}/${ATTEMPTS}"
  if npm ci --no-audit --no-fund --prefer-online; then
    exit 0
  fi

  if [[ "$attempt" -eq "$ATTEMPTS" ]]; then
    echo "npm ci failed after ${ATTEMPTS} attempts." >&2
    exit 1
  fi

  echo "npm ci failed; retrying in ${DELAY_SECONDS}s..." >&2
  sleep "$DELAY_SECONDS"
done
