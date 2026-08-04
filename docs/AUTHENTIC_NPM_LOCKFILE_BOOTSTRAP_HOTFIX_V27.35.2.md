# v27.35.2 — Authentic npm Lockfile Bootstrap Hotfix

GitHub Actions proved that the v27.35.1 launcher fix exposed a deeper defect: the committed frontend lockfile was a synthetic flat package table rather than npm's complete dependency graph. `vue-tsc` launched but crashed inside `@vue/language-core` / Volar before producing any project diagnostics.

This hotfix removes that synthetic file from source and lets the exact pinned toolchain — Node `20.18.1` and npm `10.8.2` — generate a real lockfile from exact direct dependency versions. The generated file must include npm registry tarballs, SHA-512 integrity values and dependency/peer edges before `npm ci` is allowed to run.

## Bootstrap boundary

```text
clean checkout
→ delete synthetic lockfile and node_modules
→ npm install --package-lock-only --ignore-scripts
→ verify registry/integrity/dependency graph
→ npm ci
→ npm ls --all
→ vue-tsc
→ Vitest
→ Vite build and browser-bundle audit
```

The generated `frontend/package-lock.json` and a manifest containing its SHA-256 are uploaded from every validation workflow with `if: always()`. That exact artifact is the only acceptable input for `v27.35.3`.

## Gate status

Gate A remains blocked. Q-02–Q-05 remain complete, but Q-01 stays `ACTIVE` until the exact CI-generated lockfile is committed and a clean checkout proves `npm ci` without regeneration. `v27.36.0 — Vue Absence & Time Bank` must not start before that proof.

## Non-goals

- No product domain moves to Vue.
- No backend API, OpenAPI operation, PostgreSQL schema or Flyway migration changes.
- No `npx`, global compiler, skipped typecheck or mutable dependency ranges.
- The bootstrap is temporary and must be retired in `v27.35.3`.
