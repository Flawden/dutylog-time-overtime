# Supply-chain maintenance

Status: v27.35.3.

Dependabot checks five maintained surfaces weekly:

- Maven dependencies at `/`;
- GitHub Actions at `/`;
- Docker base/runtime images at `/`;
- Playwright/E2E npm dependencies at `/`;
- Vue frontend npm dependencies at `/frontend`.

CI must remain green before merging an update. Review release notes and run the manual smoke checklist for framework, database, browser-runner or container-major updates.

## Vue frontend dependency policy

The v27.35.3 bootstrap uses exact Node `20.18.1`, exact npm `10.8.2` and exact direct dependency pins to generate a real npm lockfile before `npm ci`. The graph must contain registry tarballs, SHA-512 integrity and dependency/peer edges. Validation uploads that exact artifact, checks local launchers and `npm ls --all`, then executes OpenAPI drift, `vue-tsc`, 16 Vitest cases, Vite and the browser-bundle audit before Maven. Lockfile-only delivery is restored only after v27.35.3 commits the generated artifact.

Dependency updates must be isolated, update both manifest and lockfile, include vulnerability/release-note review and pass the complete frontend/backend/browser gates. Runtime CDN dependencies and relaxed same-origin CSP are forbidden shortcuts. The deployable supply-chain identity remains the immutable staging-tested application image digest.

## Immutable references

For maximum hardening, pin GitHub Actions to verified commit SHAs and production images to verified digests. Do not copy unverified hashes from an offline review. Resolve and verify them against upstream repositories/registries, then document the source and update procedure in the same commit.
