#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_REF="${1:-}"
if [[ -z "$IMAGE_REF" ]]; then
  echo "Usage: $0 <local-or-registry-image>" >&2
  exit 2
fi
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 2; }

SUFFIX="${GITHUB_RUN_ID:-$$}-${RANDOM}"
NETWORK="dutylog-migration-$SUFFIX"
DB="dutylog-migration-db-$SUFFIX"
APP="dutylog-migration-app-$SUFFIX"

cleanup() {
  docker rm -f "$APP" "$DB" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

APP_USER="$(docker image inspect --format '{{.Config.User}}' "$IMAGE_REF")"
if [[ -z "$APP_USER" || "$APP_USER" == "0" || "$APP_USER" == "root" || "$APP_USER" == "0:0" ]]; then
  echo "Application image must declare a non-root user, got: ${APP_USER:-empty}" >&2
  exit 1
fi

docker network create "$NETWORK" >/dev/null
docker run -d --name "$DB" --network "$NETWORK" \
  -e POSTGRES_DB=dutylog_ci \
  -e POSTGRES_USER=dutylog_ci \
  -e POSTGRES_PASSWORD=dutylog_ci_password \
  postgres:16-alpine >/dev/null

for _ in $(seq 1 40); do
  if docker exec "$DB" pg_isready -U dutylog_ci -d dutylog_ci >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec "$DB" pg_isready -U dutylog_ci -d dutylog_ci >/dev/null

docker run -d --name "$APP" --network "$NETWORK" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://$DB:5432/dutylog_ci" \
  -e SPRING_DATASOURCE_USERNAME=dutylog_ci \
  -e SPRING_DATASOURCE_PASSWORD=dutylog_ci_password \
  -e DUTYLOG_ADMIN_USERNAME=ci-admin \
  -e DUTYLOG_ADMIN_PASSWORD=ci-admin-password-12345 \
  -e DUTYLOG_REGISTRATION_DEFAULT_ENABLED=false \
  -e DUTYLOG_SECURITY_RATE_LIMIT_ENABLED=true \
  "$IMAGE_REF" >/dev/null

for _ in $(seq 1 90); do
  if docker exec "$APP" curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "Clean PostgreSQL migration and container startup passed."
    exit 0
  fi
  if [[ "$(docker inspect --format '{{.State.Status}}' "$APP")" == "exited" ]]; then
    break
  fi
  sleep 2
done

echo "Migration smoke test failed. Application logs:" >&2
docker logs "$APP" >&2 || true
exit 1
