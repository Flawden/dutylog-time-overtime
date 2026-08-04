# v27.35.0 — Vue Delivery, Contracts & Diagnostics Foundation

This release closes Gate A before the first Vue domain migration.

## Delivered

- frontend lockfile and `npm ci` in local gate, CI and Docker;
- pinned Node 20.18.1 / npm 10.8.2 and exact dependencies;
- deterministic OpenAPI → TypeScript schema/operation generation;
- committed generated contract and drift gate;
- operationId-based typed API client;
- request/release/route/requestId diagnostics;
- Vue error boundary, global error handler and unhandled rejection capture;
- controlled recovery UI without suppressing strict browser failures;
- migration manifest/parity template;
- ADR repository with ADR-001–ADR-005;
- dependency and vulnerability policy;
- repository copies of the binding Vue Migration Standard and Engineering Quality Register.

## Gate A evidence

Q-01 through Q-05 are marked DONE. `v27.36.0 — Vue Absence & Time Bank` may begin only after this release is fully green in CI and staging.

## Non-goals

No product domain moves to Vue in this release. API behavior, PostgreSQL, Flyway V47, FIFO, absence ownership, payroll and one-image topology remain unchanged.
