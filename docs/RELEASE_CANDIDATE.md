# v27.1.0 — Android API contract freeze

Status: release candidate for the server contract used by the future Android application.

This release is based on the v27.0-rc4 security consolidation. It does not add a native UI. It freezes the backend surface that Android development can safely target.

## Included

- stable `/api/v1/**` namespace with additive-only compatibility rules;
- stateless Bearer-only `/api/v1/mobile/**` security boundary;
- mobile registration, login, refresh, logout and device/session management;
- bootstrap aggregate for a bounded date range;
- machine-readable error envelope with request IDs;
- day `version` and `updatedAt` fields;
- idempotent sync operations with durable user-scoped operation IDs;
- item-level `APPLIED`, `ALREADY_APPLIED`, `CONFLICT` and `REJECTED` results;
- optimistic conflict detection with `baseVersion`;
- canonical OpenAPI and contract regression tests;
- legacy mobile endpoint deprecation headers without removal;
- bounded cleanup of old idempotency records;
- throttled mobile-session last-used writes.

## Compatibility rule

Within `/api/v1`, changes may add optional fields/endpoints but may not rename/remove fields, change existing meanings, tighten accepted values incompatibly or change status semantics. Breaking work starts under `/api/v2`.

## Acceptance

```bash
mvn -B --no-transfer-progress test
bash deploy/scripts/release-check.sh
```

Manual checks:

1. mobile registration returns access/refresh tokens;
2. browser JSESSIONID cannot authenticate `/api/v1/mobile/**`;
3. valid Bearer token can use mobile and shared `/api/v1/**` endpoints;
4. repeated `operationId` does not write twice;
5. stale `baseVersion` returns item-level `VERSION_CONFLICT`;
6. validation/auth failures contain `code`, `message`, `requestId` and `timestamp`;
7. legacy `/api/mobile/**` still works and advertises deprecation;
8. Flyway upgrades an existing production-shaped database through V22;
9. OpenAPI file is packaged at `/openapi/dutylog-v1.yaml`.

Any authentication, migration, conflict/idempotency or data-isolation failure blocks Android development and requires v27.1.1.
