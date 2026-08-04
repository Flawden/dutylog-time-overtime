# v27.35.1 — Frontend Lockfile Executable Resolution Hotfix

GitHub Actions proved that the pinned Node/npm checks and OpenAPI drift gate were correct, but the first committed frontend lockfile did not carry npm's `bin` metadata for the local CLI packages. `npm ci` therefore installed package directories without creating `node_modules/.bin/vue-tsc`, and the frontend gate stopped with exit code 127 before TypeScript compilation.

## Correction

- `frontend/package-lock.json` explicitly records the released tarball and `bin` mapping for `vue-tsc`, `vitest`, `vite` and TypeScript.
- The delivery verifier checks those lockfile mappings and, after installation, checks the platform-local launchers in `node_modules/.bin`.
- CI and Docker fail immediately after `npm ci` when any required launcher is absent.
- `npm ls --all` runs before compilation so an invalid installed dependency tree cannot proceed.
- The gate keeps `npm ci`; it does not use `npx`, mutable `npm install`, global tools or a downloaded fallback.

## Scope

No product domain moves to Vue. OpenAPI, HTTP behavior, PostgreSQL, Flyway V47, FIFO, Payroll, diagnostics and the one-image topology remain unchanged. The hotfix only restores executable resolution and strengthens the reproducible-delivery gate introduced in v27.35.0.
