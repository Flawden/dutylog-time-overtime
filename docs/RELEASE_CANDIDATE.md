# v27.2.4 — Calendar authoritative persistence hotfix

Status: infrastructure release candidate.

This release does not add product features. It establishes repeatable staging and production deployment around the frozen Android API v1.

## Included

- `test` branch deployment to an isolated staging database;
- `main`/`master` promotion to production;
- immutable GHCR image digests;
- Git-tree identity so merge commits can promote the same tested content;
- production refusal when the exact tree was not staging-tested;
- verified pre-deploy PostgreSQL backup;
- application health/smoke checks and image rollback;
- clean PostgreSQL migration smoke test in CI;
- disposable staging reset with a production guard;
- separate shared Caddy edge and per-environment internal networks;
- automatic build/commit/environment metadata in `/actuator/info`;
- unique service-worker cache identity per container build;
- OCI labels, SBOM and build provenance.

## Acceptance

1. `mvn test` and `release-check.sh` are green.
2. CI builds the Docker image and passes the clean PostgreSQL migration smoke.
3. A `test` push deploys staging and creates `staging-tested-tree-*` only after smoke succeeds.
4. A matching merge to `main`/`master` deploys the same digest.
5. An untested direct change in `main`/`master` fails before SSH deployment.
6. Production creates and verifies a backup before changing the app image.
7. Staging and production have different volumes and credentials.
8. `RESET_STAGING=RESET` deletes only staging.
9. A failed app health check attempts application-only rollback and preserves the backup.
