# VPS checklist

Status: v27.2.30.

## Host

- [ ] Ubuntu/Debian x86_64 host has Docker Engine and Compose plugin.
- [ ] Dedicated SSH deployment user owns `/opt/dutylog/staging` and `/opt/dutylog/production`.
- [ ] Firewall exposes only SSH, HTTP and HTTPS.
- [ ] YARUGA remains on `127.0.0.1:18081`.
- [ ] DutyLog staging uses `127.0.0.1:18082`.
- [ ] DutyLog production uses `127.0.0.1:18083`.
- [ ] DNS records point to the VPS.
- [ ] System nginx is the only process listening on public ports 80/443.
- [ ] Independent Certbot certificates exist for staging and production.
- [ ] `nginx -t` succeeds before every reload.

## Isolation

- [ ] Docker Compose rejects any DutyLog bind address other than `127.0.0.1`.
- [ ] PostgreSQL services publish no host ports.
- [ ] Staging and production use different database names/passwords.
- [ ] Staging and production use different bootstrap admin passwords.
- [ ] CI cannot overwrite host `.env` files.
- [ ] Docker memory/PID limits fit the shared VPS.
- [ ] Production is not enabled continuously on the current 2 GiB VPS unless staging is stopped; 4 GiB+ is recommended.

## Nginx

- [ ] `deploy/nginx/dutylog-staging.conf.example` is installed for staging.
- [ ] `deploy/nginx/dutylog-production.conf.example` is installed for production.
- [ ] Both configs overwrite `X-Real-IP` and `X-Forwarded-For` with `$remote_addr`.
- [ ] Staging proxies to 18082; production proxies to 18083.
- [ ] No Caddy container is started by the active deployment.

## GitHub

- [ ] Environments `staging` and `production` exist.
- [ ] Staging `DUTYLOG_DEPLOY_ENABLED` stays false until host preparation is complete.
- [ ] Strict known-host entries and a dedicated SSH private key are configured.
- [ ] GHCR pull credentials are configured.
- [ ] Production branch is protected.
- [ ] Production approval is enabled for early releases.

## Acceptance

- [ ] Push to `test` deploys staging.
- [ ] Clean PostgreSQL migration smoke passes.
- [ ] Loopback smoke on 18082 passes.
- [ ] Public staging HTTPS smoke passes.
- [ ] Merge to `main`/`master` promotes the same digest.
- [ ] Production backup is verified before deploy.
- [ ] Loopback smoke on 18083 and public production smoke pass.
- [ ] Backup restore has been rehearsed on staging.
- [ ] Encrypted backup exists outside the VPS.
