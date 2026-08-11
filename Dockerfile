# Multi-stage build: install the committed authentic npm graph, compile Vue,
# package it into Spring Boot, then run one non-root app image.
FROM node:20.18.1-alpine3.20 AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json frontend/generated-lockfile-manifest.txt \
     frontend/.npmrc frontend/.node-version frontend/.npm-version ./
COPY frontend/scripts ./scripts
RUN test "$(node --version)" = "v$(cat .node-version)" \
    && test "$(npm --version)" = "$(cat .npm-version)" \
    && node ./scripts/verify-authentic-lockfile.mjs \
    && npm ci --no-audit --no-fund --prefer-online \
    && test -e node_modules/.bin/vue-tsc \
    && test -e node_modules/.bin/vitest \
    && test -e node_modules/.bin/vite \
    && npm ls --all >/dev/null
COPY frontend ./
COPY src/main/resources/static/openapi/dutylog-v1.yaml \
     /src/main/resources/static/openapi/dutylog-v1.yaml
RUN node ./scripts/verify-authentic-lockfile.mjs \
    && npm run verify:delivery \
    && npm run typecheck \
    && npm run test:unit \
    && npm run build \
    && test -s dist/dutylog-vue-app-shell.js \
    && test -s dist/dutylog-vue-app-shell.css

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /app

ARG DUTYLOG_BUILD_ID=local
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
COPY --from=frontend-build /frontend/dist ./frontend/dist
# A unique service-worker body per immutable image prevents stale PWA shell caches
# even when multiple CI builds share the same semantic release version.
RUN find src/main/resources/static -type f -name '*.js' -exec \
      sed -i "s/__DUTYLOG_BUILD_ID__/${DUTYLOG_BUILD_ID}/g" {} + \
    && mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG DUTYLOG_BUILD_VERSION=27.40.1-local
ARG DUTYLOG_BUILD_COMMIT=local
ARG DUTYLOG_BUILD_TREE=local
ARG DUTYLOG_BUILD_TIME=unknown
ARG DUTYLOG_SOURCE_URL=unknown
LABEL org.opencontainers.image.title="DutyLog: Time & Overtime" \
      org.opencontainers.image.version="$DUTYLOG_BUILD_VERSION" \
      org.opencontainers.image.revision="$DUTYLOG_BUILD_COMMIT" \
      org.opencontainers.image.source-tree="$DUTYLOG_BUILD_TREE" \
      org.opencontainers.image.created="$DUTYLOG_BUILD_TIME" \
      org.opencontainers.image.source="$DUTYLOG_SOURCE_URL"
ENV SPRING_PROFILES_ACTIVE=prod \
    DUTYLOG_BUILD_VERSION=$DUTYLOG_BUILD_VERSION \
    DUTYLOG_BUILD_COMMIT=$DUTYLOG_BUILD_COMMIT \
    DUTYLOG_BUILD_TREE=$DUTYLOG_BUILD_TREE \
    DUTYLOG_BUILD_TIME=$DUTYLOG_BUILD_TIME
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && groupadd --system --gid 10001 dutylog \
    && useradd --system --uid 10001 --gid dutylog --home-dir /app --shell /usr/sbin/nologin dutylog \
    && mkdir -p /app/logs \
    && chown -R dutylog:dutylog /app \
    && rm -rf /var/lib/apt/lists/*
COPY --from=backend-build --chown=dutylog:dutylog /app/target/dutylog-*.jar /app/dutylog.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/dutylog.jar"]
