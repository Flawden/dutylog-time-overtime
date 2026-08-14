# v27.41.8 — Shared Runtime Bundle Split

## Why
Exact v27.41.7 frontend CI proved workspace/page segmentation but failed only the new per-chunk audit because `chunks/main-8syw6Ngx.js` remained **565411 B raw / 141782 B gzip**, above the locked **300000 B raw / 100000 B gzip** ceiling. The failure is useful evidence: route chunks exist, but shared runtime ownership is still too concentrated.

## Change
`frontend/vite.config.ts` now uses an explicit `manualChunks` function with four coarse, architecture-aligned shared chunks:

- `vendor` — Vue/Pinia/router and other npm runtime code;
- `api-contract` — generated OpenAPI contract code;
- `platform` — DutyLog platform/router/bridge/API infrastructure;
- `settings-workspace` — the intentionally eager Settings runtime owner required by Calendar shift-type actions.

Existing content-hashed route/page chunks from v27.41.7 remain. The split is intentionally coarse; it does not create one-file micro-chunks.

## Budgets
No budget is raised:

- entry: 750000 B raw / 230000 B gzip;
- each non-entry chunk: 300000 B raw / 100000 B gzip;
- complete JS graph: 825000 B raw / 250000 B gzip.

The audit still scans every emitted JavaScript file for forbidden Node/CommonJS runtime patterns and still requires a segmented graph.

## Boundaries
No HTTP/OpenAPI, Flyway, auth, accounting, PWA ownership or offline queue semantics change. `dataLayer` remains the sole offline mutation/sync executor. People Profiles / Shared Availability remain the next product release after this delivery cut is green.

## Acceptance
- exact Node 20.18.1 / npm 10.8.2 delivery and typecheck;
- 64/64 Vitest;
- production Vite build with every JS chunk inside the unchanged budgets;
- Java 17 Maven 791/791;
- auth/onboarding canary;
- Chromium 48/48, 0 flaky;
- immutable image + PostgreSQL/Flyway V47 smoke + staging deploy.
