# ADR-002 — Monorepo and one production application image

- Status: accepted
- Date: 2026-08-04
- Release: v27.35.0

## Decision

Backend, frontend, tests and deployment remain in one repository and one release version. The Node stage builds Vue assets; Maven packages them into the Spring Boot JAR; production runs one immutable application image/container plus PostgreSQL.

## Consequences

There is no independently deployed frontend and no split-version compatibility matrix. Staging-tested image identity is promoted to production without rebuilding assets.
