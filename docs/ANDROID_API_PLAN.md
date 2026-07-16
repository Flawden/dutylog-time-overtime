# Android development plan

Current backend milestone: **v27.2.4 — Staging and CI/CD foundation**. Android API v1 remains frozen and unchanged from v27.1.0.

The server is ready to become the single source of truth for both the existing web/PWA client and a future native Android application. Android must target the versioned contract under `/api/v1/**`; legacy `/api/mobile/**` remains only for compatibility.

## Frozen v1 foundation

Authentication and device sessions:

```text
GET    /api/v1/mobile/auth/registration-status
POST   /api/v1/mobile/auth/register
POST   /api/v1/mobile/auth/login
POST   /api/v1/mobile/auth/refresh
POST   /api/v1/mobile/auth/logout
GET    /api/v1/mobile/auth/me
GET    /api/v1/mobile/auth/sessions
DELETE /api/v1/mobile/auth/sessions/{id}
```

Startup and offline queue:

```text
GET  /api/v1/mobile/bootstrap?from=&to=
POST /api/v1/mobile/sync
```

Versioned shared resources:

```text
/api/v1/calendar
/api/v1/days
/api/v1/tasks
/api/v1/important-days
/api/v1/overtime
/api/v1/shift-types
/api/v1/profile
/api/v1/modules
/api/v1/notifications
/api/v1/quick-scenarios
/api/v1/export/notes
```

The canonical machine-readable contract is available at:

```text
/openapi/dutylog-v1.yaml
```

Compatibility policy: v1 may receive additive optional fields and endpoints. Renaming/removing a field, changing an existing meaning, or introducing incompatible validation requires `/api/v2`.

## Recommended Android stack

```text
Kotlin
Jetpack Compose
Retrofit + OkHttp
Room
Android Keystore-backed token storage
WorkManager
AlarmManager only for exact alarms
```

Suggested layers:

```text
Compose UI
   ↓
ViewModel
   ↓
Repository
   ├─ Room (source of truth for UI)
   ├─ Retrofit (network)
   └─ sync_queue (durable offline operations)
```

The access token should normally live in memory and be restored from protected storage only when required. The refresh token belongs in Keystore-backed encrypted storage. One OkHttp authenticator should serialize refresh attempts, rotate the token pair, retry the original request once, and send the user to login when refresh fails.

## First Android milestone

Build a connected MVP before expanding offline coverage:

1. login and optional registration;
2. calendar month;
3. selected-day screen;
4. shift selection;
5. note editing;
6. tasks;
7. overtime account;
8. important dates;
9. profile and module settings;
10. mobile-session/device management.

The UI should read from Room even while online. Network responses update Room; Compose observes Room. This avoids replacing the data architecture when offline mode is enabled.

## Offline v1 behavior

Day changes already support:

- client-generated `operationId`;
- `baseVersion` optimistic concurrency;
- `APPLIED`, `ALREADY_APPLIED`, `CONFLICT`, and `REJECTED` results;
- one result per queue item;
- durable user-scoped idempotency records;
- version 0 reserved for a day that does not exist;
- persisted day versions starting at 1;
- lightweight empty day rows so clear/delete operations keep monotonic versions.

Android queue processing:

1. create an operation once and store it in Room;
2. never reuse its `operationId` for another action;
3. send queued operations in FIFO order;
4. remove `APPLIED` and `ALREADY_APPLIED` items;
5. pause or surface `CONFLICT` for user/server resolution;
6. keep retryable network failures;
7. move permanent `REJECTED` items to a visible failed queue;
8. refresh the affected day after each conflict.

## Additive work after the first client

These improvements can be added without breaking day-sync v1:

- idempotent task create/update/delete operations;
- idempotent important-date operations;
- idempotent overtime credit/usage operations;
- a delta/change cursor instead of reloading complete date ranges;
- tombstones for server-side deletions;
- local notification scheduling from Room;
- optional FCM wake-up hints later.

Overtime mutations must become idempotent before they are placed in an automatic retry queue, otherwise an uncertain network response could duplicate a credit or usage.

## Backend change rule during Android development

Android work should be approximately 85–90% client-side, but small additive backend changes are expected. Every backend change must:

- preserve existing v1 fields and meanings;
- update OpenAPI and contract tests in the same commit;
- remain owner-scoped and Bearer-compatible;
- return machine-readable error codes;
- keep migrations backward-compatible with the previous deployed application image.
