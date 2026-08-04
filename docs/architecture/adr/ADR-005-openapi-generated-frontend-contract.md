# ADR-005 — OpenAPI-generated frontend contract

- Status: accepted
- Date: 2026-08-04
- Release: v27.35.0

## Context

Handwritten request/response interfaces drift from the canonical backend API and fail late in browser scenarios.

## Decision

`src/main/resources/static/openapi/dutylog-v1.yaml` is the canonical contract. `frontend/scripts/generate-openapi-contract.mjs` deterministically generates schema and operation types. CI, Docker and local frontend gates run `contract:check` and fail on drift. Domain clients use `operationId` through the generated typed client.

## Consequences

Generated files are committed for review and reproducible archives but never edited manually. An OpenAPI change and its regenerated TypeScript contract belong to the same commit.
