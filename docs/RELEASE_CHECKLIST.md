# Release checklist

Status: v27.2.4.

## Local gate

```bash
mvn -B --no-transfer-progress test
bash deploy/scripts/release-check.sh
```

When Docker is available:

```bash
docker build -t dutylog:release-check .
bash deploy/scripts/migration-smoke-test.sh dutylog:release-check
```

## Staging

- push the exact candidate tree to `test`;
- confirm `Deploy staging` is green;
- verify calendar, notes export, tasks, overtime, modules, admin and Android API v1;
- verify `/actuator/info` shows staging, commit and build metadata;
- test the migration against the persistent staging database;
- optionally reset staging and verify a clean V1..latest install.

## Production

- merge the tested tree into `main`/`master` without additional changes;
- confirm production resolved `staging-tested-tree-*`;
- confirm a verified pre-deploy dump was created;
- confirm health and smoke checks passed;
- verify production data remains intact;
- copy a recent backup off the VPS.

## Security and isolation

- staging and production `.env` files use different secrets;
- PostgreSQL is not attached to the shared edge network;
- app images are referenced by `@sha256:` digest;
- deployment SSH host keys use strict known-host checking;
- host `.env` files are not overwritten by CI;
- application container runs as non-root;
- production registration starts closed;
- no workflow runs `down -v` against production.

## Tag

```bash
git tag -a v27.2.4 -m "v27.2.4 — Staging and CI/CD foundation"
git push origin v27.2.4
```
