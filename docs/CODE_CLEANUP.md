# Code cleanup

Status: v27.1.0.

DutyLog is in release stabilization. This document defines the current cleanup rules so the project can be polished without accidentally turning cleanup into a new feature cycle.

## Goal

Improve maintainability while preserving behavior:

- no new product features;
- no database shape changes unless required for a bug/security fix;
- no risky rewrites during release stabilization;
- small, reviewable refactors only;
- every cleanup must pass `deploy/scripts/release-check.sh`.

## Frontend runtime layout

The frontend is intentionally split into ordered browser scripts instead of one large `app.js`:

```text
10-core.js       state, constants, i18n, themes, shared helpers
20-data.js       API wrappers, CSRF, IndexedDB snapshot, offline queue
30-calendar.js   month grid, markdown, selected-day panel
40-overtime.js   overtime ledger, credits, usages, scenarios
50-tasks.js      tasks, important days, day-panel task sections
60-settings.js   settings, shifts, notifications, modules, diagnostics
70-user-boot.js  boot, routing, profile, notes fullscreen, Telegram UI
```

These files still share the browser global scope. They are not ES modules yet. The loading order in `index.html` is therefore part of the contract and is verified by `release-check.sh`.

## Cleanup done in v26.4

- Reworded frontend file headers so they describe the current split runtime instead of the old monolithic `app.js` history.
- Removed stale runtime comments that still mentioned `app.js` as the active frontend entrypoint.
- Centralized the split-asset list in `release-check.sh` and `smoke-test.sh` arrays, reducing repeated hardcoded checks.
- Added a local release gate check that verifies the exact JS order and version in `index.html`.
- Added a local release gate check that fails on legacy runtime `app.js` references in `static/*.html`, `static/*.js`, or `static/*.css`.
- Kept behavior unchanged: this release is cleanup and verification hardening only.

## Known technical debt to postpone

These are useful later, but should not block release stabilization:

- Convert static JS files to real ES modules or bundle them with a small build step.
- Split `Dtos.java` into feature-focused DTO files.
- Move large controller/service classes into smaller package-level components.
- Replace some broad global frontend state with feature-owned state objects.
- Add more integration tests around module guards and offline queue edge cases.

## Safe cleanup rules

Allowed during stabilization:

- renaming comments and docs;
- removing dead comments;
- extracting repeated script checks;
- adding tests/checks that do not change runtime behavior;
- small internal helper extraction with no API shape change.

Avoid during stabilization:

- changing endpoint contracts;
- changing migration history;
- changing offline queue format unless required for a bug;
- changing auth/session behavior without a dedicated security review;
- large package reshuffles that make release bugs harder to trace.
