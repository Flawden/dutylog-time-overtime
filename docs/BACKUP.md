# Backup and restore

DutyLog stores user data in PostgreSQL. The application code can be deleted and cloned again from Git, but the PostgreSQL volume must be protected with backups.

## What is stored where

```text
Git repository       application code, migrations, documentation
.env                 local secrets and server settings, not committed
Docker volume        PostgreSQL data
backups/             database dumps, not committed
```

Safe command:

```bash
docker compose down
```

Dangerous command:

```bash
docker compose down -v
```

The `-v` flag removes Docker volumes and can delete the database.

## Create a backup

From the project root:

```bash
./deploy/scripts/backup-postgres.sh
```

By default backups are saved to:

```text
./backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

The dump is created in PostgreSQL custom format. It is smaller than plain SQL and is intended for `pg_restore`.

The script also creates a checksum file when `sha256sum` is available:

```text
dutylog-YYYY-MM-DD_HH-MM-SS.dump.sha256
```

## List backups

```bash
./deploy/scripts/list-backups.sh
```

## Restore a backup

```bash
./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

The restore script asks for confirmation and stops the application container before restoring the database.

For non-interactive restore:

```bash
FORCE=true ./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

## Custom options

On a VPS with `docker-compose.prod.yml`, set this in `.env` so backup/restore scripts always use the production compose file:

```env
DUTYLOG_COMPOSE_FILE=docker-compose.prod.yml
```


```bash
BACKUP_DIR=/mnt/backups ./deploy/scripts/backup-postgres.sh
```

```bash
BACKUP_KEEP_LAST=30 ./deploy/scripts/backup-postgres.sh
```

```bash
STOP_APP=false ./deploy/scripts/restore-postgres.sh backups/file.dump
```

Supported restore formats:

```text
.dump
.dump.gz
.sql
.sql.gz
```

The recommended format is `.dump` created by `backup-postgres.sh`.

## Daily backup on VPS

Example systemd units are provided:

```text
deploy/systemd/dutylog-backup.service.example
deploy/systemd/dutylog-backup.timer.example
```

Typical installation path:

```bash
sudo cp deploy/systemd/dutylog-backup.service.example /etc/systemd/system/dutylog-backup.service
sudo cp deploy/systemd/dutylog-backup.timer.example /etc/systemd/system/dutylog-backup.timer
sudo systemctl daemon-reload
sudo systemctl enable --now dutylog-backup.timer
```

The example assumes the project is located at:

```text
/opt/dutylog
```

Change `WorkingDirectory`, `ExecStart`, and `BACKUP_DIR` if the project is stored elsewhere.

## Backup before updates

Before updating the application:

```bash
./deploy/scripts/backup-postgres.sh
git pull
docker compose up -d --build
```

Before restoring an older Git tag, also create a backup:

```bash
./deploy/scripts/backup-postgres.sh
git checkout v20.6
docker compose up -d --build
```

Database migrations usually move forward. Restoring old application code against a newer database can fail if the schema changed. For a full rollback, restore both the Git tag and a database backup from the same period.

## Moving local data to VPS

On the local machine:

```bash
./deploy/scripts/backup-postgres.sh
```

Copy the backup file to the VPS:

```bash
scp backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump user@server:/opt/dutylog/backups/
```

On the VPS:

```bash
cd /opt/dutylog
./deploy/scripts/restore-postgres.sh backups/dutylog-YYYY-MM-DD_HH-MM-SS.dump
```

## Minimal backup rule

For real usage, keep at least:

```text
1 recent local backup
1 backup before every update
1 copy outside the VPS
```
