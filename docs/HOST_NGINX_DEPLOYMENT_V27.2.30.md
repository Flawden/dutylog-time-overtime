# Host nginx deployment — v27.2.30

DutyLog now uses the VPS-wide system nginx as the only public edge. Caddy remains in the repository only as a legacy/alternative example and is not started by the active CI/CD bootstrap.

## Target topology

```text
Internet
   |
   v
system nginx :80/:443
   |-- yaruga-trophy.ru        -> 127.0.0.1:18081 (YARUGA)
   |-- stage.yaruga-trophy.ru  -> 127.0.0.1:18082 (DutyLog staging)
   `-- dutylog.yaruga-trophy.ru-> 127.0.0.1:18083 (DutyLog production)
```

Each DutyLog environment has its own Compose project, PostgreSQL volume, log volume, credentials, backup directory and loopback port. PostgreSQL is never published to the host.

## Why loopback publication is safe

`deploy/compose/docker-compose.deploy.yml` publishes the application as:

```yaml
ports:
  - "${DUTYLOG_BIND_ADDRESS}:${DUTYLOG_BIND_PORT}:8080"
```

`check-deploy-env.sh` rejects any address other than `127.0.0.1`. Therefore ports `18082` and `18083` are reachable only from the VPS itself. The public firewall still exposes only SSH, HTTP and HTTPS.

## One-time staging setup

1. Create DNS `A stage -> 2.26.73.188`.
2. Prepare the deployment owner and directories:

```bash
sudo DUTYLOG_DEPLOY_ROOT=/opt/dutylog \
  DUTYLOG_DEPLOY_OWNER=dutylog-deploy \
  bash deploy/scripts/bootstrap-cicd-host.sh
```

3. Create `/opt/dutylog/staging/.env` from `.env.example`, replace all placeholders and keep:

```env
DUTYLOG_BIND_ADDRESS=127.0.0.1
DUTYLOG_BIND_PORT=18082
DUTYLOG_BASE_URL=https://stage.yaruga-trophy.ru
DUTYLOG_SECURITY_TRUST_PROXY_HEADERS=true
```

4. Install the nginx site once:

```bash
sudo cp deploy/nginx/dutylog-staging.conf.example /etc/nginx/sites-available/dutylog-staging
sudo ln -s /etc/nginx/sites-available/dutylog-staging /etc/nginx/sites-enabled/dutylog-staging
sudo nginx -t
sudo systemctl reload nginx
```

5. After DNS resolves, obtain the independent certificate:

```bash
sudo certbot --nginx -d stage.yaruga-trophy.ru
sudo nginx -t
```

6. Configure GitHub Environment `staging`, enable `DUTYLOG_DEPLOY_ENABLED=true`, and run/push the `Deploy staging` workflow.

If `nginx -t` reports `could not build optimal proxy_headers_hash`, add these directives once inside the global `http {}` block of `/etc/nginx/nginx.conf`, then retest before reloading:

```nginx
proxy_headers_hash_max_size 1024;
proxy_headers_hash_bucket_size 128;
```

This warning is not a syntax failure, but removing it keeps the shared proxy configuration predictable as more sites are added.

## Deployment checks

Every real deployment proves the release in this order:

1. container health inside Docker;
2. full application smoke test on `http://127.0.0.1:<bind-port>`;
3. full public smoke test through DNS, TLS and nginx;
4. immutable OCI version and Git-tree labels;
5. deployment state update only after every check passes.

The local smoke test distinguishes an application/container failure from an nginx/DNS/certificate failure.

## Forwarded IP safety

The application trusts proxy headers only because:

- Docker binds exclusively to `127.0.0.1`;
- nginx is the only process that can reach the published port;
- the supplied nginx configs overwrite `X-Real-IP` and `X-Forwarded-For` with `$remote_addr` instead of appending untrusted client values.

Changing `DUTYLOG_BIND_ADDRESS` to `0.0.0.0` is forbidden by preflight.

## Shared VPS resource limits

Staging defaults for the current 2 GiB VPS:

```env
DUTYLOG_APP_MEMORY_LIMIT=640m
DUTYLOG_DB_MEMORY_LIMIT=256m
```

Production defaults are higher and should normally be enabled after upgrading the VPS to at least 4 GiB RAM, or while staging is stopped. Docker JSON logs are rotated to prevent silent disk exhaustion.

## What CI/CD never changes

Remote deployment updates only `/opt/dutylog/staging` or `/opt/dutylog/production`. It does not edit `/etc/nginx`, does not request certificates, does not restart YARUGA, does not run a shared `docker compose down`, and does not overwrite host `.env` files.
