# v27.35.7 — Docker Frontend OpenAPI Build Context Hotfix

## Problem

The full frontend gate, Maven verify and all unit/static contracts passed, but the Docker frontend stage failed while the canonical backend OpenAPI YAML was absent from that stage during `npm run build`. The generated-contract drift check resolved the canonical backend document at `/src/main/resources/static/openapi/dutylog-v1.yaml`, while the frontend stage copied only `frontend/`.

## Resolution

- Copy `src/main/resources/static/openapi/dutylog-v1.yaml` into the frontend Docker stage at the canonical absolute path.
- Keep the copy after dependency installation so OpenAPI-only changes do not invalidate the `npm ci` layer.
- Keep `contract:check` inside `npm run build`; no drift gate is skipped or weakened.
- Add binding Java/static release guards for source path, ordering and one-image topology.

## Scope

No production Java behavior, Vue source, npm dependency graph, OpenAPI content, generated TypeScript contract, PostgreSQL schema, Flyway migration or domain owner changes.

## Acceptance

- Docker frontend stage passes authentic-lockfile verification, `npm ci`, `vue-tsc`, 16 Vitest cases, OpenAPI drift, Vite build and bundle audit.
- Maven packages the resulting Vue bundle into the existing Spring Boot application image.
- Full Docker image, clean PostgreSQL migration smoke and staging checks are green before Gate A is accepted.
