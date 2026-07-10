# Multi-stage build: compile with Maven, then run from a small JRE image.
FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && groupadd --system --gid 10001 dutylog \
    && useradd --system --uid 10001 --gid dutylog --home-dir /app --shell /usr/sbin/nologin dutylog \
    && mkdir -p /app/logs \
    && chown -R dutylog:dutylog /app \
    && rm -rf /var/lib/apt/lists/*
ENV SPRING_PROFILES_ACTIVE=prod
COPY --from=build --chown=dutylog:dutylog /app/target/dutylog-*.jar /app/dutylog.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/dutylog.jar"]
