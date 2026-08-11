# v27.39.3 — Frontend Diagnostics Release Version Source Hotfix

## Trigger

The exact v27.39.2 staging frontend gate passed authentic lockfile verification, OpenAPI drift checking and strict `vue-tsc`. Vitest then ran 52 cases and failed exactly one assertion: `frontendDiagnostics.spec.ts` expected the v27.39.2 release while `RELEASE_VERSION` still evaluated to v27.39.1.

## Root cause

`frontend/vite.config.ts` maintained a second release literal independently from `frontend/package.json`. Release packaging updated package metadata and runtime/static version surfaces, but the Vite define feeding `src/platform/version.ts` remained one release behind. The diagnostics test correctly exposed the drift.

## Fix

- `vite.config.ts` imports committed frontend package metadata and derives `releaseVersion` from `packageMetadata.version`.
- `frontendDiagnostics.spec.ts` derives its expected release from that same package metadata rather than maintaining another literal.
- Release/static contracts forbid a hard-coded Vite release literal and require the package-derived diagnostics contract.

No HTTP/OpenAPI, backend business rule, PostgreSQL/Flyway, browser retry/timeout or diagnostics redaction behavior changes.

## Acceptance

Acceptance remains exact Node 20.18.1/npm 10.8.2 frontend gate with 52/52 Vitest, Maven 758/758, canary, clean 48/48 Chromium with zero flaky retries, immutable image, PostgreSQL V47 smoke and staging.
