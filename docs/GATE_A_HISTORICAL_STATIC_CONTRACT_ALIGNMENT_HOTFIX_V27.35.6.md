# DutyLog v27.35.6 — Gate A Historical Static Contract Alignment Hotfix

Date: 2026-08-04

## Confirmed failures

The full Maven suite reached JUnit and exposed four stale test-only expectations:

1. bootstrap acceptance asserted one exact sentence instead of the released semantic wording;
2. the launcher hotfix test still expected `frontend/package-lock.json` to remain ignored after promotion;
3. the delivery foundation counted only the migration directory root although V1–V47 live under `postgresql/`;
4. the Vue foundation still expected mutable `node:20-alpine` instead of the exact pinned image.

## Resolution

- acceptance now requires both `passes full CI and staging` and `only after full green`;
- `.gitignore` must not contain `frontend/package-lock.json`;
- migration discovery uses `Files.walk` and counts only `V\d+__*.sql`;
- Docker contract requires `node:20.18.1-alpine3.20`.

## Scope boundary

This release changes test-only contracts, release identity and documentation. It does not change production Java behavior, Vue runtime, authentic npm dependency resolution, API/OpenAPI shape, PostgreSQL schema, Flyway files, security boundaries, domain ownership or the one-image topology.

## Acceptance

Gate A remains accepted only after full green Maven, Chromium, Docker, clean PostgreSQL and staging validation of v27.35.6.
