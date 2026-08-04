# DutyLog CI/CD

Status: v27.34.3.

DutyLog uses two long-lived deployment branches:

```text
feature/* -> test -> staging
                  |
                  +-> main/master -> production
```

`test` builds an immutable image, deploys it to staging and marks that exact Git tree as tested only after container health, loopback smoke and public HTTPS smoke checks pass. `main`/`master` never rebuilds the application: it resolves the matching `staging-tested-tree-*` image and deploys the same registry digest to production.

Until the staging VPS is ready, leave the `staging` Environment variable `DUTYLOG_DEPLOY_ENABLED` unset or `false`. The workflow still runs Maven/JaCoCo, release checks, Playwright, image build and clean-PostgreSQL migration verification, then records an explicit successful skip. It does not create a promotion tag.


## Browser-runtime bundle gate (v27.34.3)

Vite library mode must replace `process.env.NODE_ENV` with the literal production value. `npm run build` now executes `frontend/scripts/audit-browser-bundle.mjs` after Vite and rejects residual `process.env`, CommonJS `require(...)` / `module.exports`, `__dirname` or `__filename` in the generated browser JavaScript. Because Docker also runs `npm run build`, CI and the production image validate the same artifact. Playwright remains the final real-browser runtime gate.

## Strict Vue build compatibility hotfix (v27.34.1)

The frontend gate remains first and strict. v27.34.1 fixes the concrete template and Vite type failures found by GitHub Actions; CI must still execute real `vue-tsc`, Vitest and Vite before Maven. No fallback build or skipped typecheck is permitted.

## Vue app-shell build gate (v27.34.0)

Every validation workflow now builds the browser application before Maven packages resources:

```text
frontend npm install
→ vue-tsc
→ Vitest
→ Vite build
→ Maven verify/test
→ release-check
→ Playwright
→ Docker image
```

`frontend/dist/dutylog-vue-app-shell.{js,css}` is copied into the Spring Boot JAR under `static/vue`. The Dockerfile uses a Node build stage and a Maven build stage, but the runtime topology is unchanged: one non-root `dutylog-app` image/container plus PostgreSQL. Production still promotes the exact staging-tested image digest instead of rebuilding it.

Direct frontend dependencies are exact-pinned in `frontend/package.json`; the immutable staging image digest remains the deployable supply-chain identity.

## Authenticated smoke test (v27.2.31)

The deployment gate treats the application shell as protected content. It checks the anonymous browser redirect with `Accept: text/html`, obtains `XSRF-TOKEN` from `/login.html`, signs in through `/perform_login` with the environment bootstrap administrator and verifies the versioned shell with the resulting session cookie. Missing or rejected credentials fail deployment and rollback checks closed. API-style anonymous requests may still receive JSON `401`.


## Public edge architecture

The active deployment uses the VPS-wide system nginx, not a Caddy container:

```text
nginx :80/:443
  -> 127.0.0.1:18082 -> dutylog-staging app
  -> 127.0.0.1:18083 -> dutylog-production app
```

The application port is published only on `127.0.0.1`. PostgreSQL is not published at all. CI/CD never edits nginx or certificates during an ordinary release; those are one-time host operations.

Detailed host steps: [`HOST_NGINX_DEPLOYMENT_V27.2.30.md`](HOST_NGINX_DEPLOYMENT_V27.2.30.md).

## GitHub Environments

Create environments named exactly:

- `staging`
- `production`

Add these variables:

```text
DUTYLOG_DEPLOY_ENABLED    # staging: false until host is ready; production is fail-closed
DUTYLOG_DEPLOY_HOST
DUTYLOG_DEPLOY_PORT       # normally 22
DUTYLOG_DEPLOY_USER
DUTYLOG_DEPLOY_PATH       # /opt/dutylog/staging or /opt/dutylog/production
DUTYLOG_BASE_URL          # public HTTPS URL
```

Add these secrets:

```text
DUTYLOG_SSH_PRIVATE_KEY
DUTYLOG_SSH_KNOWN_HOSTS
GHCR_READ_USERNAME
GHCR_READ_TOKEN
```

Build `DUTYLOG_SSH_KNOWN_HOSTS` from the server host key and verify its fingerprint through a separate trusted channel before saving it. Never paste private keys or tokens into issue comments, build logs or chat.

The SSH key should belong to a dedicated deployment user that owns only its DutyLog environment directories. Membership in the host `docker` group is effectively root-equivalent and can affect unrelated containers; it is acceptable for the first single-owner VPS but is not strong isolation. A later hardened installation should use a dedicated host, rootless Docker or narrowly restricted privileged commands.

For production, enable required reviewers in GitHub Environment settings during early releases.

## One-time server preparation

Use an x86_64/amd64 VPS with Docker Engine and the Compose plugin. Create the deployment account, then run from the release repository:

```bash
sudo DUTYLOG_DEPLOY_ROOT=/opt/dutylog \
  DUTYLOG_DEPLOY_OWNER=<ssh-deploy-user> \
  bash deploy/scripts/bootstrap-cicd-host.sh
```

The bootstrap creates only:

```text
/opt/dutylog/staging
/opt/dutylog/production
```

It does not install/start Caddy, does not touch ports 80/443 and does not modify nginx.

Prepare host-local environment files:

```bash
sudo cp /opt/dutylog/staging/.env.example /opt/dutylog/staging/.env
sudo cp /opt/dutylog/production/.env.example /opt/dutylog/production/.env
sudo chmod 600 /opt/dutylog/{staging,production}/.env
```

Replace every placeholder. Staging and production must have different database names, database passwords, admin passwords and Telegram credentials.

Keep these boundaries:

```env
# staging
DUTYLOG_BIND_ADDRESS=127.0.0.1
DUTYLOG_BIND_PORT=18082

# production
DUTYLOG_BIND_ADDRESS=127.0.0.1
DUTYLOG_BIND_PORT=18083
```

`check-deploy-env.sh` rejects non-loopback binding. `DUTYLOG_SECURITY_TRUST_PROXY_HEADERS=true` is allowed only with the supplied nginx configuration, which overwrites forwarding headers before DutyLog uses them for audit/rate limiting.

## Branch behavior

### Push to `test`

`.github/workflows/deploy-staging.yml`:

1. runs the Vue frontend gate, Maven `verify`, JaCoCo, release checks and 44 Playwright scenarios;
2. calculates the exact Git tree hash;
3. builds one non-root `linux/amd64` image with OCI metadata, SBOM and provenance;
4. pushes immutable tree/commit tags to GHCR;
5. verifies that digest on clean PostgreSQL;
6. validates the `staging` GitHub Environment without printing secrets;
7. when disabled, records an explicit successful skip;
8. when enabled, deploys by digest and applies Flyway;
9. checks container health and full loopback smoke on port 18082;
10. checks the public HTTPS URL through nginx;
11. only then creates `staging-tested-tree-*`.

### Merge to `main`/`master`

`.github/workflows/deploy-production.yml`:

1. reruns tests and the release gate;
2. calculates the Git tree hash;
3. requires an existing matching `staging-tested-tree-*` image;
4. resolves its immutable digest;
5. creates and verifies a PostgreSQL backup before update;
6. deploys the same tested digest;
7. verifies running OCI version/tree metadata;
8. checks container health, loopback port 18083 and public HTTPS;
9. rolls back the application image on failure when possible.

Production does not rebuild source code.

## Merge mode

Prefer a fast-forward merge from `test` to `main`/`master`, or any merge that leaves the resulting Git tree unchanged. A merge commit is acceptable because promotion keys on the tree, not the commit SHA.

Do not edit application files directly in `main`/`master`: a changed tree has no staging-tested tag and production stops before touching the server.

## Deployment state and rollback

Each environment stores `.deploy-state` with current/previous image digests and build metadata. It contains no application secrets.

Manual application rollback:

```bash
cd /opt/dutylog/production
CONFIRM_ROLLBACK=ROLLBACK bash deploy/scripts/rollback-environment.sh
```

Rollback changes the application image only. Flyway migrations are forward-only. Database restore remains an explicit controlled operation.

## CI/CD does not

- restore production databases automatically;
- overwrite host `.env` files;
- edit nginx or Certbot configuration;
- restart or stop YARUGA;
- publish DutyLog on `0.0.0.0`;
- promote an untested source tree;
- run `docker compose down -v` in production.

## Pipefail-safe smoke response checks (v27.2.32)

Deployment smoke checks capture HTTP responses before searching them. Do not use `curl ... | grep -q` or `echo "$BODY" | grep -q` in scripts that enable `set -o pipefail`: an early match can close the pipe and turn a successful check into SIGPIPE exit 141.
