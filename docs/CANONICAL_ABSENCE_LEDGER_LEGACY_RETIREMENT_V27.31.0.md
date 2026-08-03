# v27.31.0 — Canonical Absence Ledger & Legacy Retirement

## Product decision

DutyLog now has one canonical write model for every absence. Vacation, overtime-backed time off, sick leave, unpaid leave and custom absence types are created and edited through Unified Absence Composer. The Overtime workspace remains the canonical read model for earned hours, FIFO usage, allocations and remaining balance, but it no longer accepts a new standalone manual usage.

The boundary is explicit:

```text
Overtime credit -> earned time and FIFO supply
Absence period  -> why/when the user did not work
Linked usage    -> internal OVERTIME_BANK compensation owned by the absence
```

## Retired direct usage writes

`POST /api[/v1]/overtime/usages` now returns `409 DIRECT_USAGE_RETIRED`.

`PATCH /api[/v1]/overtime/usages/{id}` now returns `409 LEGACY_USAGE_MUST_BE_MIGRATED`.

A linked `ABSENCE` usage remains immutable from Overtime endpoints and is recalculated only by editing its owner absence. Existing unlinked `MANUAL` usages remain visible and deletable during the transition, but cannot be edited as if they were canonical absences.

## Unified creation and editing

Every user-facing “use overtime” action opens Unified Absence Composer with:

```text
Type: TIME_OFF
Coverage: FULL_DAY or PARTIAL
Compensation: OVERTIME_BANK
Date / interval / reason
```

The absence service creates, reserves, posts, reallocates or deletes the linked FIFO usage. Changing an overtime-backed absence to Unpaid, Sick or another non-bank policy returns the consumed minutes to FIFO automatically.

## Legacy usage migration

The Overtime workspace exposes one bounded migration flow:

```http
POST /api/v1/overtime/legacy-usages/preview
POST /api/v1/overtime/legacy-usages/migrate
```

Preview classifies each old manual usage without guessing:

- exact match with the planned shift duration -> `FULL_DAY`;
- every other positive duration -> `HOURS_ONLY`;
- closed accounting period or an already active absence on that date -> blocked.

Migration creates a canonical TIME_OFF absence and promotes the existing usage in place by assigning `sourceKind=ABSENCE` and `sourceAbsenceId`. Existing usage ID and FIFO allocation rows are preserved; no credit is consumed twice and no allocation history is rebuilt.

## Honest unknown intervals

`HOURS_ONLY` is a transition-only coverage state for imported legacy data. It means:

```text
The charged duration is known.
The original start/end interval is not known.
```

The calendar and absence list display the duration plus “Интервал не указан”. The state cannot be selected for a new absence. While editing an imported record, the user may keep it unchanged or convert it to `FULL_DAY` or a real `PARTIAL` interval, after which canonical balance and calendar rules apply.

## Overtime journal retained

The release does not remove the Overtime workspace. It keeps:

- credit creation and editing;
- earned / used / remaining totals;
- credit cards and daily/monthly charts;
- FIFO queue and allocation details;
- CSV/XLS export;
- linked absence usages as read-only compensation rows;
- legacy migration controls while manual usages remain.

## Telegram

`/timeoff` now creates a canonical TIME_OFF absence. Full-day and explicit interval forms are accepted. A numeric duration is accepted only when it exactly matches the planned shift and can therefore be represented honestly as a full-day absence; arbitrary direct hour usage is rejected.

## Compatibility and storage

Forward-only Flyway V47 updates only the two released V42 check constraints on `absence_periods`. It adds `HOURS_ONLY` to the coverage allowlist and permits a single-date row with known `charged_minutes` but null start/end times. V47 does not rewrite existing data, and V42 remains checksum-pinned and immutable.

Payroll, Unified Ledger, approval workflow, closed-period protection, Calendar Sync, profile/workspace settings and PostgreSQL schemas are unchanged.

## Regression baseline

- 126 Java test classes;
- 625 `@Test` methods;
- 41 Chromium Playwright scenarios;
- Flyway V47.
