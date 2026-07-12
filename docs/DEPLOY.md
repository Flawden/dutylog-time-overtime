# Deployment guide

Status: v27.2.3.

The recommended deployment path is now automated and documented in [`CICD.md`](CICD.md).

## Production model

```text
feature branch -> test -> staging -> main/master -> production
```

Staging builds and validates one immutable GHCR image. Production deploys the same image digest after a verified PostgreSQL backup. Staging and production use separate Compose projects, volumes, credentials and databases.

## One-time setup

Use:

- [`CICD.md`](CICD.md) for GitHub Environments, secrets and branch workflows;
- [`PRODUCTION_DEPLOY.md`](PRODUCTION_DEPLOY.md) for server preparation;
- [`MIGRATION_SAFETY.md`](MIGRATION_SAFETY.md) before schema changes;
- [`BACKUP_RESTORE.md`](BACKUP_RESTORE.md) for recovery.

The old `docker-compose.prod.yml` remains available for manual/local emergency operation, but it is not the normal update path after v27.2.3.

Never run `docker compose down -v` against production.
