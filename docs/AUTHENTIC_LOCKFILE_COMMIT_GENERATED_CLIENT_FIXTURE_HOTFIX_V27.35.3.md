# DutyLog v27.35.3 — Authentic Lockfile Commit & Generated Client Fixture Hotfix

## Purpose

Promote the authentic npm dependency graph produced by GitHub Actions run `30906521813` into the repository, restore clean-checkout `npm ci` as the only normal frontend install path, and repair the single Vitest fixture that reused one consumed `Response` object across two generated-client calls.

## Confirmed input

- Source artifact: `frontend-lockfile-test-and-package-30906521813.zip`.
- CI artifact lockfile SHA-256: `3f6c590948a62c506c2191c9b279f15712f5182148c5f3131e96d5a56bd54060`.
- Authentic graph: 134 transitive package entries, npm registry `resolved` URLs, SHA-512 `integrity`, and 43 dependency/optional/peer graph entries.
- The transitive package graph is preserved. Only the root application version is promoted from `27.35.2` to `27.35.3`.

## Changes

1. `frontend/package-lock.json` is committed and no longer ignored.
2. `frontend/generated-lockfile-manifest.txt` records artifact provenance and both source/committed hashes.
3. Normal CI, staging, production validation and Docker no longer regenerate the graph; they verify it and run `npm ci`.
4. The bootstrap script remains explicit maintenance tooling for future reviewed dependency updates only.
5. `generatedClient.spec.ts` creates a fresh `Response` for each mocked fetch call, matching browser `fetch` semantics and preventing `Body has already been read`.
6. Q-01 joins Q-02–Q-05 as DONE. Gate A may be accepted only after this exact release passes full CI and staging.

## Unchanged

No backend API, OpenAPI operation/schema, PostgreSQL schema, Flyway migration, Absence, Time Bank, FIFO, Payroll, approval, closed-period, CSRF/session or production topology change is introduced.

## Acceptance

- clean checkout contains the committed authentic lockfile;
- `npm ci` does not modify package or lockfile inputs;
- `vue-tsc` passes;
- all 16 Vitest cases pass;
- Vite bundle and browser audit pass;
- Maven, Playwright, Docker, clean PostgreSQL smoke and staging deploy pass;
- only after full green may `v27.36.0 — Vue Absence & Time Bank` begin.
