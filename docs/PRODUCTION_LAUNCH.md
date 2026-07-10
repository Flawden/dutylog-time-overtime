# Production launch

Status: v27.2.1.

This is the short path. Full details are in [`CICD.md`](CICD.md).

## One-time VPS setup

```bash
sudo DUTYLOG_DEPLOY_ROOT=/opt/dutylog \
  DUTYLOG_DEPLOY_OWNER=<ssh-deploy-user> \
  bash deploy/scripts/bootstrap-cicd-host.sh
```

Create and fill:

```text
/opt/dutylog/edge/.env
/opt/dutylog/staging/.env
/opt/dutylog/production/.env
```

Start the shared Caddy edge:

```bash
cd /opt/dutylog/edge
docker compose --env-file .env -f deploy/compose/docker-compose.edge.yml -p dutylog-edge up -d
```

Configure GitHub Environments `staging` and `production`, including SSH host-key data and GHCR pull credentials.

## First deployment

1. Push the candidate to `test`.
2. Confirm staging deploy and smoke tests are green.
3. Open the staging domain and verify core flows.
4. Merge the same tree to `main` or `master`.
5. Confirm production backup, deploy and smoke are green.

## Success criteria

- staging and production domains use HTTPS;
- databases and credentials are separate;
- production image is referenced by digest;
- production backup is verified before update;
- `/actuator/health` is `UP`;
- admin login, calendar, notes export and Android API v1 work;
- a recent backup exists outside the VPS.
