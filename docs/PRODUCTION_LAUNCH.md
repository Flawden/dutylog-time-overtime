# Production launch

Status: v27.2.30.

DutyLog production launches only after staging, backup/restore rehearsal and the shared VPS resource check are complete.

## Directories and environment files

```text
/opt/dutylog/staging/.env
/opt/dutylog/production/.env
```

There is no active `/opt/dutylog/edge` service. The existing system nginx owns ports 80/443.

## Public routing

```text
stage.yaruga-trophy.ru   -> nginx -> 127.0.0.1:18082
dutylog.yaruga-trophy.ru -> nginx -> 127.0.0.1:18083
```

Install the site examples from `deploy/nginx/` and issue separate Certbot certificates. Nginx configuration is a one-time host operation; ordinary GitHub deployments update only DutyLog containers and database migrations.

## Release path

1. Push to `test` and wait for all Java/Playwright/image/migration checks.
2. Verify real staging behavior and backup restore.
3. Merge the unchanged tested tree to `main`/`master`.
4. Approve the production GitHub Environment deployment.
5. Confirm backup, loopback smoke, public HTTPS smoke and immutable image metadata.

See [`CICD.md`](CICD.md) and [`HOST_NGINX_DEPLOYMENT_V27.2.30.md`](HOST_NGINX_DEPLOYMENT_V27.2.30.md).
