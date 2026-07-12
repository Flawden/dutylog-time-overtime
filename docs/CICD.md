# DutyLog CI/CD

Status: v27.2.3.

DutyLog uses two long-lived deployment branches:

```text
feature/* -> test -> staging
                  |
                  +-> main/master -> production
```

`test` builds an immutable image, deploys it to staging and marks that exact image as tested only after health and smoke checks pass. `main`/`master` never rebuilds the application. It resolves the `staging-tested-tree-<git-tree>` tag and deploys the same registry digest to production.

A direct change in `main`/`master` that was not tested in staging fails closed because no matching tested tree tag exists.

## GitHub Environments

Create environments named exactly:

- `staging`
- `production`

Add these environment variables to both:

```text
DUTYLOG_DEPLOY_HOST
DUTYLOG_DEPLOY_PORT       # usually 22
DUTYLOG_DEPLOY_USER
DUTYLOG_DEPLOY_PATH       # /opt/dutylog/staging or /opt/dutylog/production
DUTYLOG_BASE_URL          # https://test... or https://app...
```

Add these environment secrets to both:

```text
DUTYLOG_SSH_PRIVATE_KEY
DUTYLOG_SSH_KNOWN_HOSTS
GHCR_READ_USERNAME
GHCR_READ_TOKEN
```

Build `DUTYLOG_SSH_KNOWN_HOSTS` from the server host key and verify its fingerprint out of band before saving it, for example:

```bash
ssh-keyscan -p 22 your-vps.example.com
```

`GHCR_READ_TOKEN` needs permission to read the repository package. The SSH key should belong to an unprivileged deployment user that can access Docker and owns its environment directory. The workflow's built-in `GITHUB_TOKEN` pushes images; the server credential only pulls them.

For production, enable required reviewers in GitHub Environment settings during the first releases. The workflow validates tests and release checks first; only the separate deployment job then waits for approval. This avoids approving a build that has not passed validation yet.

## First server preparation

Use an x86_64/amd64 VPS for this release. Install Docker Engine and the Compose plugin. Clone or copy the release repository once, then run:

```bash
sudo DUTYLOG_DEPLOY_ROOT=/opt/dutylog DUTYLOG_DEPLOY_OWNER=<ssh-deploy-user> bash deploy/scripts/bootstrap-cicd-host.sh
```

Ensure the SSH deployment user can run Docker (for example, add it to the `docker` group and start a new login session). Then prepare host-local environment files:

```bash
sudo cp /opt/dutylog/staging/.env.example /opt/dutylog/staging/.env
sudo cp /opt/dutylog/production/.env.example /opt/dutylog/production/.env
sudo cp /opt/dutylog/edge/.env.example /opt/dutylog/edge/.env
sudo chmod 600 /opt/dutylog/{staging,production,edge}/.env
```

Replace every domain/password/username placeholder. Leave the all-zero `DUTYLOG_IMAGE` bootstrap sentinel unchanged: CI overrides it with an immutable digest during each deployment, while the valid sentinel lets DB-only maintenance commands parse Compose before the first release. Staging and production must use different database passwords, admin passwords, database names and Telegram credentials.

Start the shared Caddy edge proxy:

```bash
cd /opt/dutylog/edge
docker compose --env-file .env -f deploy/compose/docker-compose.edge.yml -p dutylog-edge up -d
```

The current workflow publishes `linux/amd64`; add QEMU and an ARM64 platform before using an ARM VPS. The shared external network contains only the application containers and Caddy. Each PostgreSQL service remains on its own Compose-internal network.

## Branch behavior

### Push to `test`

`.github/workflows/deploy-staging.yml`:

1. runs Maven tests and the release gate;
2. calculates the Git tree hash;
3. builds one non-root image with OCI metadata, SBOM and provenance;
4. pushes immutable `tree-*` and `sha-*` tags to GHCR;
5. deploys the image by digest to staging;
6. applies Flyway against the staging database;
7. runs health and public smoke checks;
8. only then creates `staging-tested-tree-*`.

### Merge to `main`/`master`

`.github/workflows/deploy-production.yml`:

1. reruns tests and the release gate;
2. calculates the Git tree hash;
3. requires an existing `staging-tested-tree-*` image;
4. resolves its immutable digest;
5. creates and verifies a PostgreSQL backup before update;
6. verifies the running container OCI version and exact Git tree;
7. deploys that exact digest;
8. waits for health and runs smoke checks;
9. rolls the application image back on failure when possible.

Production does not rebuild source code.

## Merge mode

Prefer a fast-forward merge from `test` to `main`/`master`, or a merge that does not modify the resulting tree. A merge commit is acceptable because promotion keys on the Git tree, not the commit SHA.

Do not edit files directly in `main`/`master`. A changed tree has no staging-tested tag and production deployment stops before touching the server.

## Deployment state and rollback

Each environment stores `.deploy-state` with current and previous image digests and build metadata. It contains no application secrets.

Manual application rollback:

```bash
cd /opt/dutylog/production
CONFIRM_ROLLBACK=ROLLBACK bash deploy/scripts/rollback-environment.sh
```

Rollback changes the application image only. Flyway migrations are forward-only. A destructive or incompatible migration requires a controlled database restore; see `docs/MIGRATION_SAFETY.md` and `docs/BACKUP_RESTORE.md`.

## What CI does not do automatically

- it does not restore production databases automatically;
- it does not delete staging data unless explicitly requested;
- it does not overwrite host `.env` files;
- it does not promote an untested source tree;
- it does not run `docker compose down -v` in production.
