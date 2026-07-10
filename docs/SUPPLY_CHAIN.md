# Supply-chain maintenance

Status: introduced in v27.0-rc4; current in v27.1.0.

Dependabot checks three ecosystems weekly:

- Maven dependencies;
- GitHub Actions;
- Docker base/runtime images.

CI must remain green before merging an update. Review release notes and run the manual smoke checklist for framework, database or container-major updates.

## Immutable references

For maximum hardening, pin GitHub Actions to verified commit SHAs and production images to verified digests. Do not copy unverified hashes from an offline review. Resolve and verify them against upstream repositories/registries, then document the source and update procedure in the same commit.
