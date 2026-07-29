# Release checklist

Status: v27.18.3.

## Local gate

```bash
mvn -B --no-transfer-progress test
bash deploy/scripts/release-check.sh
```

When Docker is available:

```bash
docker build -t dutylog:release-check .
bash deploy/scripts/migration-smoke-test.sh dutylog:release-check
```

## Staging

- push the exact candidate tree to `test`;
- confirm `Deploy staging` is green;
- verify calendar, notes export, tasks, overtime, modules, admin and Android API v1;
- verify `/actuator/info` shows staging, commit and build metadata;
- verify Shift Worker / Planner / Minimal navigation and hidden-route links;
- verify Dashboard / Compact / Focus on desktop and mobile;
- verify theme + palette independence and automatic persistence after reload;
- verify custom accent → Theme palette restores both theme accents without switching themes;
- verify «Вернуть цвета темы» also works while the select already says Theme palette;
- verify Theme / Preset / Custom palette status and reload persistence;
- verify Outline keeps a visible border while Ghost has no visible idle border/shadow and gains a hover surface;
- verify Secondary / Danger / Link / Icon preview variants, disabled state, keyboard focus and phone touch targets;
- verify no Classic selector remains and `shellMode=classic` from an old local cache still boots the single shell;
- verify Appearance remains open after reload and workspace/layout/palette persist;
- verify Overtime Next summary, Month/Year/All-time presets, daily/monthly chart keys and FIFO queue;
- verify `account-page` returns canonical `usages` and summary/chart show the same `+5 / −4 / +1` snapshot;
- verify reopening an already selected calendar day keeps the panel and timezone projection visible;
- verify the professional ledger table on desktop and detailed credit cards on a phone viewport;
- verify credit/usage editors, legacy migration and CSV/Excel export remain available;
- test the migration against the persistent staging database;
- optionally reset staging and verify a clean V1..latest install.

## Production

- merge the tested tree into `main`/`master` without additional changes;
- confirm production resolved `staging-tested-tree-*`;
- confirm a verified pre-deploy dump was created;
- confirm health and smoke checks passed;
- verify production data remains intact;
- copy a recent backup off the VPS.

## Security and isolation

- staging and production `.env` files use different secrets;
- PostgreSQL is not attached to the shared edge network;
- app images are referenced by `@sha256:` digest;
- deployment SSH host keys use strict known-host checking;
- host `.env` files are not overwritten by CI;
- application container runs as non-root;
- production registration starts closed;
- no workflow runs `down -v` against production.

## Tag

```bash
git tag -a v27.18.3 -m "v27.18.3 — UI Settings & Button Variants Quality Hotfix"
git push origin v27.18.3
```
