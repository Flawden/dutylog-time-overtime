# Release checklist

Status: v27.30.1.

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



## Workspace, Layout & Theme Studio acceptance

- confirm UI Core reports contract v2 before feature bundles boot;
- confirm custom navigation keeps Today and Settings and never exceeds five primary items;
- confirm hidden routes remain reachable through generated secondary links;
- confirm Shift remains available while optional Today cards can be reordered/hidden;
- confirm Sidebar returns to the bottom mobile navigation below the desktop breakpoint;
- confirm Mobile Flow stays a single content column;
- confirm calendar compact/dots modes change presentation only;
- confirm grid decoration has `pointer-events: none`;
- confirm arbitrary CSS/JavaScript and unknown ids are not persisted;
- confirm Payroll, Ledger, Vacation and Calendar smoke paths remain green;
- confirm Flyway remains V46.

## Payroll Module Registry Contract Hotfix acceptance

- confirm `PayrollFoundationContractTest` reads the canonical key from `ModuleKeys.PAYROLL`;
- confirm `DutyLogModules` registers `PAYROLL` in `TIME_ACCOUNTING`;
- confirm the contract does not require the unrelated literal `ModuleService.PAYROLL`;
- confirm production Payroll service, API, V45 and browser scenario remain unchanged;
- confirm 603 Java tests and 37 Playwright scenarios pass.

## Payroll Foundation acceptance

- confirm Flyway applies V45 exactly once and creates `payroll_settings`, `payroll_adjustments`, `payroll_snapshots`;
- confirm an open month returns `PERIOD_NOT_CLOSED` on calculate;
- close a healthy month, set a positive hourly rate and calculate revision 1;
- add one addition and one deduction, calculate revision 2 and confirm revision 1 points to the replacement;
- confirm base/total amounts and all stored financial values use minor units;
- confirm `/api/v1/payroll/periods/{month}` returns `Cache-Control: no-store`;
- confirm Payroll is unavailable when its module is disabled and automatically keeps Overtime/Vacation dependencies;
- confirm 603 Java tests and 37 Playwright scenarios pass.

## Staging

- push the exact candidate tree to `test`;
- confirm the Maven gate executes all 603 tests with zero failures;
- confirm all 37 Playwright scenarios pass, including serialized ledger refresh, workflow reservation/posting, closed-period protection and explicit factual work;
- create an overtime credit while Vacation Planner is hidden, open Vacation and confirm the available compensatory-time balance refreshes without a page reload;
- save a timezone and wait for the full calendar/tasks/ledger/notification refresh before reloading; confirm no `Failed to fetch` console error;
- on desktop confirm linked usages are asserted in the visible table, then switch to mobile cards without duplicate-visibility ambiguity;
- create a DRAFT overtime-backed absence and confirm no usage exists; submit it and confirm `RESERVED`; approve it and confirm `POSTED`; cancel it and confirm the hours return;
- close the month, confirm ordinary absence/actual-work edits return `PERIOD_CLOSED`, add an append-only correction, then reopen it;
- inspect `/api/v1/ledger-integrity` and confirm `healthy=true`, no orphan usage and no allocation mismatch;
- add an explicit factual interval and confirm the time-compensation day reports `actualSource=EXPLICIT`;
- confirm fresh schedule apply reloads the authoritative month through the data layer;
- on a phone viewport, navigate away from the current month and confirm the contextual «Сегодня» button appears, returns to today and disappears;
- select a different calendar date, open Important Days and confirm the draft date already matches the selected day;
- verify important-event checkboxes are compact, an overnight Today card shows a separate two-date chip, and multiple schedule layers remain horizontally usable;
- refresh an already rendered month and confirm the old grid stays visible while the calm loading status is announced;
- confirm the planner shows the built-in Vacation, Time Off, Sick, Unpaid and Other types;
- assign a shift, create a full-day vacation over it and confirm the absence owns the cell visually while «По графику» still names the preserved shift;
- delete the absence and confirm the original shift reappears without reconstruction;
- configure an 8-hour time-off bank, create 09:00–13:00 partial time off and confirm the shift remains visible, a 4-hour bar appears and 4 hours remain;
- create a non-overlapping partial interval on the same day and reject an overlapping interval with `ABSENCE_OVERLAP`;
- confirm vacation overflow and time-off overflow return `VACATION_LIMIT_EXCEEDED` / `TIME_OFF_LIMIT_EXCEEDED`;
- switch to Monday-Friday vacation counting and confirm weekends remain visible but do not consume allowance;
- change the work-year boundary/carryover and verify vacation balance independently from the time-off hour bank;
- export partial time off and confirm it becomes a timed `.ics` event while full-day absence stays all-day;
- open Settings → External calendar and confirm the default export range renders without `localDateKey is not defined` or any page error;
- confirm every active nginx HTTP/HTTPS server block has an exact `/calendar-feed.ics` location with `access_log off` before issuing a token;
- create a private calendar subscription and copy the one-time URL;
- fetch the URL without a web session and confirm UTF-8 `text/calendar`, CRLF and `BEGIN:VCALENDAR`;
- rotate the subscription and confirm the previous URL returns 404;
- revoke the current URL and confirm it returns 404 without deleting DutyLog events;
- export a selected range and one important event; verify shifts, tasks, important events and absences remain read-only source data;
- confirm `Deploy staging` is green;
- verify calendar, note search/export/offline queued edits, tasks, overtime, modules, admin and Android API v1;
- verify schedule-template list seeds five presets exactly once and built-ins open as copies;
- preview a four-day cycle with an occupied date and confirm safe mode reports `SKIP_CONFLICT`;
- apply the preview and confirm manual edits remain possible afterward;
- create a companion layer in another IANA timezone and verify month/week/day projection plus server-owned visibility;
- open an important-event details card, choose Edit, save and confirm both details/editor modals are hidden before navigating to Settings;
- in Day mode call the same-date flow again and confirm the selected-day panel stays open without requiring the hidden month grid;
- from the hourly Day view press «Все детали дня», confirm Month mode and the full selected-day panel become visible, then open Notes;
- edit an existing note offline, reload offline, reconnect and confirm the pending `updateNote` queue drains and the text survives an online reload;
- confirm creating, pinning, moving and deleting notes remain disabled offline while editing an existing note remains enabled;
- create an important date, timed event and multi-day period; verify read-first details, all-day rail and hourly timeline;
- change canonical timezone and verify timed events keep canonical instants while all-day dates/periods stay floating;
- create all-day, point, same-day and overnight task plans and verify the hourly timeline uses exact duration;
- verify a deadline before planned end is rejected while a later deadline/reminder stays independent;
- verify the timed-task editor reports «Дедлайн не может быть раньше окончания запланированного интервала.» and keeps the modal open;
- change canonical timezone and confirm timed planning keeps source provenance while all-day dates stay floating;
- verify project suggestions/chips/filter/search and Inbox search across open/archived/local queued entries;
- verify `/api/tasks/board?from=...&to=...` still filters by deadline/date for older clients;
- verify `/api/tasks/board?scheduledFrom=...&scheduledTo=...` filters by planned overlap, including overnight intervals;
- verify the Web/PWA board date fields and «этот месяц» use the planned-range contract;
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

## v27.26.0 ledger smoke

- create an overtime credit and record its available FIFO balance;
- create an `OVERTIME_BANK` partial absence and confirm one locked `ABSENCE` usage appears;
- edit the absence duration and confirm the same source usage is reallocated;
- delete the absence and confirm the consumed minutes return;
- verify manual usage edit/delete returns `LINKED_USAGE_MANAGED_BY_ABSENCE`;
- create `UNPAID` and `SICK_PAY` absences and confirm they do not consume overtime;
- verify `/api/time-compensation` reports planned, worked, earned, used, covered, vacation, sick and unpaid values;
- verify V43 converted any legacy standalone balance into the oldest opening credit and left `day_entries` untouched.


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
git tag -a v27.30.1 -m "v27.30.1 — Unified Absence Composer & Calendar Projection"
git push origin v27.30.1
```
