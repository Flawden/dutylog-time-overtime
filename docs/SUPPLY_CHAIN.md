# Supply-chain maintenance

Status: v27.34.3.

Dependabot checks five maintained surfaces weekly:

- Maven dependencies at `/`;
- GitHub Actions at `/`;
- Docker base/runtime images at `/`;
- Playwright/E2E npm dependencies at `/`;
- Vue frontend npm dependencies at `/frontend`.

CI must remain green before merging an update. Review release notes and run the manual smoke checklist for framework, database, browser-runner or container-major updates.

## Vue frontend dependency policy

The v27.34.3 frontend keeps the v27.34.0 dependency set and exact direct pins. Validation runs `vue-tsc`, 11 Vitest cases, Vite and a generated-browser-bundle audit before Maven; Docker repeats the same audited `npm run build` in an isolated Node stage. The deployable supply-chain identity remains the immutable staging-tested application image digest promoted to production.

A future dependency update must update the exact pin, pass all frontend/backend/browser gates and produce a new staging-tested digest. Do not weaken the same-origin CSP or add CDN runtime dependencies to avoid a build failure.

## Immutable references

For maximum hardening, pin GitHub Actions to verified commit SHAs and production images to verified digests. Do not copy unverified hashes from an offline review. Resolve and verify them against upstream repositories/registries, then document the source and update procedure in the same commit.
