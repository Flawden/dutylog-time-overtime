# Flyway migration safety

Status: v27.2.2.

Production schema changes are owned only by Flyway:

```properties
spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
```

## Rules

1. Never edit a migration that has already reached staging or production.
2. Add a new sequential migration for every change.
3. Prefer additive, backward-compatible changes.
4. Avoid dropping columns/tables in the same release that stops using them.
5. Backfill data separately before adding `NOT NULL` or restrictive constraints.
6. Test clean install and upgrade on staging.
7. Preserve a verified pre-deploy backup outside the application volume.

## Expand and contract

Safe sequence:

1. add the new column/table;
2. deploy code that can understand old and new forms;
3. backfill existing rows;
4. switch reads/writes;
5. remove the old structure only in a later release.

This keeps application rollback possible after most deployments.

## Pipeline protection

CI builds the real container and starts it against a fresh PostgreSQL instance. This proves that all migrations V1..latest can create a clean database.

The persistent staging database proves the upgrade path from the prior staged build. Production deployment creates a custom-format `pg_dump`, verifies it with `pg_restore --list`, and only then starts the new image.

Automatic rollback restores the previous application image, not the database. Automatic database restore is intentionally forbidden because it can discard writes made after deployment.
