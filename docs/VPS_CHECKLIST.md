# VPS checklist

Status: v27.2.3.

## Host

- [ ] Docker Engine and Compose plugin are installed.
- [ ] Deployment SSH user can run Docker and owns `/opt/dutylog/staging` and `/opt/dutylog/production`.
- [ ] Firewall exposes only SSH, HTTP and HTTPS.
- [ ] Production and staging DNS records point to the VPS.
- [ ] External Docker network `dutylog_edge` exists.
- [ ] Shared Caddy edge is running.

## Isolation

- [ ] Staging and production use different database names and passwords.
- [ ] Staging and production use different bootstrap admin passwords.
- [ ] PostgreSQL services are attached only to internal Compose networks.
- [ ] CI cannot overwrite host `.env` files.

## GitHub

- [ ] Environments `staging` and `production` exist.
- [ ] SSH private key and strict known-host entries are configured.
- [ ] GHCR pull credentials are configured.
- [ ] Production branch is protected.
- [ ] Production approval is enabled for early releases.

## Acceptance

- [ ] Push to `test` deploys staging.
- [ ] Clean PostgreSQL migration smoke passes.
- [ ] Staging health and smoke pass.
- [ ] Merge to `main`/`master` promotes the same digest.
- [ ] Production backup is verified before deploy.
- [ ] Production health and smoke pass.
- [ ] Backup restore has been rehearsed on staging.
- [ ] A backup is copied outside the VPS.
