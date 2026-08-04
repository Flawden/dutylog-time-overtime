# Supply-chain maintenance

Status: v27.35.1.

Dependabot checks five maintained surfaces weekly:

- Maven dependencies at `/`;
- GitHub Actions at `/`;
- Docker base/runtime images at `/`;
- Playwright/E2E npm dependencies at `/`;
- Vue frontend npm dependencies at `/frontend`.

CI must remain green before merging an update. Review release notes and run the manual smoke checklist for framework, database, browser-runner or container-major updates.

## Vue frontend dependency policy

The v27.35.1 frontend uses a committed lockfile, exact Node `20.18.1`, exact npm `10.8.2`, exact direct dependency pins and `npm ci` in CI, Docker and the local frontend gate. The lockfile carries explicit npm tarball and `bin` metadata for `vue-tsc`, `vitest`, `vite` and TypeScript. Validation checks the resulting local launchers, runs `npm ls --all`, then executes the deterministic OpenAPI drift check, `vue-tsc`, 16 Vitest cases, Vite and the generated-browser-bundle audit before Maven.

Dependency updates must be isolated, update both manifest and lockfile, include vulnerability/release-note review and pass the complete frontend/backend/browser gates. Runtime CDN dependencies and relaxed same-origin CSP are forbidden shortcuts. The deployable supply-chain identity remains the immutable staging-tested application image digest.

## Immutable references

For maximum hardening, pin GitHub Actions to verified commit SHAs and production images to verified digests. Do not copy unverified hashes from an offline review. Resolve and verify them against upstream repositories/registries, then document the source and update procedure in the same commit.
