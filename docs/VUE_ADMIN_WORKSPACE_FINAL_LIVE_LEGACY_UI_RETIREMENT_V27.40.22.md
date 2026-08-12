# DutyLog v27.40.22 — Vue Admin Workspace & Final Live Legacy UI Retirement

## Goal

Retire the final live legacy-owned user screen without creating a new parallel API, route or offline authority. Admin Users/Roles, Registration and Diagnostics move to Vue/Pinia and generated OpenAPI operations.

## Ownership cut

After Vue readiness, all user-facing routes are Vue-owned. The legacy Admin section, browser Admin state/API helpers, Admin render/event handlers and the post-Vue route side-effect adapter are removed. `dutylog:vue-route-committed` is no longer needed because there is no legacy UI consumer for committed routes.

Pre-Vue recovery remains intentionally limited to screens that still have a compatible recovery renderer. A pre-Vue `#admin` request temporarily shows Settings until Vue mounts and applies the guarded canonical Admin route.

## Canonical Admin API

The backend keeps `/api/admin/**` for compatibility and adds ADMIN-protected `/api/v1/admin/**` aliases for six generated operations: status, users, role update, password reset, registration read and registration update. The OpenAPI contract advances to 124 operations / 130 schemas with SHA-256 `8bb0573339f0db88a5539da4a45a746503fe6b88bebee54c50766e621f0a8464`.

## Safety boundaries

- Spring Security and backend Admin checks remain authoritative; Vue guards are navigation UX only.
- Strict TypeScript stays enabled.
- Flyway remains V47.
- `dataLayer` remains the sole offline mutation/reconnect owner; Admin adds no offline queue.
- Existing Admin selectors remain stable where browser/user contracts depend on them.

## Acceptance

Static baseline: 159 Java test classes, 772 JUnit `@Test` methods, 48 Playwright scenarios, 60 Vitest cases, OpenAPI 124/130 and Flyway V47. Exact Node 20.18.1/npm 10.8.2, Java 17, Chromium, immutable-image and PostgreSQL staging gates remain mandatory before the release is called green.

## Next

If staging is green, v27.40.x moves to a Final Legacy Ownership Audit followed by the Functional Parity Sweep and Layout/UX Consistency Sweep; no further user-screen migration is assumed necessary until that audit proves otherwise.
