# Git workflow for DutyLog

Status: v27.2.1.

## Branches

```text
main/master production source tree (use the branch that exists in the repository)
test       staging source tree
feature/*  isolated work
fix/*      isolated fixes
```

Normal flow:

```bash
git switch -c feature/android-calendar
# work and commit
git push -u origin feature/android-calendar
# merge feature into test after review
# staging deploys automatically
# merge the tested test tree into main/master
# production promotes the same image automatically
```

Do not develop directly in `main`/`master`. Production requires a matching staging-tested Git tree and fails before deployment when one does not exist.

## Tags

Create a release tag after production has passed smoke checks:

```bash
git tag -a v27.2.1 -m "v27.2.1 — Staging and CI/CD foundation"
git push origin v27.2.1
```

## Data is not Git

Git stores source, migrations, configuration templates and documentation. It does not store `.env`, Docker volumes, database dumps, logs, tokens or personal data.

Never use `docker compose down -v` in production. Code rollback and database restore are separate operations.
