# DutyLog Android API v1

Version: **27.2.2**

`/api/v1/**` is the first frozen mobile contract. Existing v1 response fields are additive-only. A breaking rename, removal, semantic change or incompatible validation change requires `/api/v2`.

## Compatibility

- Existing web/PWA endpoints remain available.
- Legacy `/api/mobile/**` remains available and returns `Deprecation: true` plus a successor link.
- New Android clients must use `/api/v1/mobile/**` and versioned shared endpoints such as `/api/v1/tasks`.
- Every `/api/v1/**` response includes `X-DutyLog-Api-Version: v1`.

## Authentication

The mobile chain is stateless and Bearer-only. Browser `JSESSIONID` never authenticates `/api/v1/mobile/**`.

Public endpoints:

- `GET /api/v1/mobile/auth/registration-status`
- `POST /api/v1/mobile/auth/register`
- `POST /api/v1/mobile/auth/login`
- `POST /api/v1/mobile/auth/refresh`
- `POST /api/v1/mobile/auth/logout`

Registration returns the first access/refresh token pair immediately. Access tokens live for 30 minutes; refresh tokens live for 45 days and rotate on refresh. `lastUsedAt` is written at most once per five minutes per token to avoid unnecessary database writes during background sync.

## Stable errors

All controller, validation, authentication, authorization and rate-limit failures use this envelope:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Проверь данные формы",
  "error": "Проверь данные формы",
  "fields": {"username": "required"},
  "requestId": "a1b2c3d4",
  "timestamp": "2026-07-10T10:00:00Z"
}
```

Android must branch on `code`, never on localized `message`. `error` is retained only for old web compatibility.

## Bootstrap

`GET /api/v1/mobile/bootstrap?from=YYYY-MM-DD&to=YYYY-MM-DD`

Returns `apiVersion`, server time, current user and the existing calendar aggregate. Day records now include:

- `version` — optimistic sync version (`0` means no row; persisted rows start at `1`);
- `updatedAt` — server timestamp.

## Idempotent sync

`POST /api/v1/mobile/sync`

```json
{
  "operations": [
    {
      "operationId": "550e8400-e29b-41d4-a716-446655440000",
      "baseVersion": 3,
      "day": {
        "date": "2026-07-10",
        "note": "Updated offline"
      }
    }
  ]
}
```

Rules:

- `operationId` is generated once on the device and never reused for a different action;
- repeating the same successful operation returns `ALREADY_APPLIED` and does not write twice;
- `baseVersion` must match the current server version;
- stale writes return an item-level `CONFLICT` with `VERSION_CONFLICT`;
- module/business rejections return item-level `REJECTED`;
- operations without an actual field mutation return `REJECTED / NO_CHANGES`;
- one conflict does not hide successful neighbouring operations;
- clearing a day leaves a lightweight empty version row so versions remain monotonic.

The idempotency table stores identifiers and technical outcomes only. It does not store note/task content.

## OpenAPI

Canonical contract:

- runtime: `/openapi/dutylog-v1.yaml`
- source: `src/main/resources/static/openapi/dutylog-v1.yaml`

## Future sync work

v1 freezes day synchronization. Offline task/important-date/overtime operation types and delta cursors may be added later as additive v1 endpoints/fields, but existing day semantics must not change.
