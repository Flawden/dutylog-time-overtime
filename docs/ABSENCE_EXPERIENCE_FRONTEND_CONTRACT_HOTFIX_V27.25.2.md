# v27.25.2 — Absence Experience Frontend Contract Hotfix

## Incident

GitHub Actions compiled the application and executed all 580 tests from v27.25.1. One static frontend contract failed:

```text
VacationPlannerFrontendContractTest
.calendarComposesVacationIntoMonthWeekDayAndFocusedDetails:58
expected: <true> but was: <false>
```

The test still required the pre-overhaul literal:

```javascript
for (const absence of facts.absences)
```

The accepted v27.25.0 plan/fact frontend intentionally uses narrower projections instead:

```javascript
for (const absence of facts.absences.slice(0, 3))
for (const absence of facts.partialAbsences)
for (const absence of facts.absences.filter(item => item.coverage !== "PARTIAL"))
```

## Why the runtime is correct

- Week agenda is compact and displays at most three absence rows.
- Partial absences are timed events in the hourly Day view.
- Full-day absences are all-day items and retain planned-shift context.
- Absence rows remain editable through `editAbsenceFromOccurrence`.

The failure was therefore a stale string contract, not a product defect.

## Fix

`VacationPlannerFrontendContractTest` now verifies all three accepted composition paths and rejects the obsolete unbounded loop expectation. No production JavaScript is changed.

## Compatibility

The hotfix does not change:

- absence or time-off behavior;
- balances and overlap rules;
- Month / Week / Day rendering;
- HTTP API or OpenAPI;
- PostgreSQL schema or Flyway V42;
- `.ics` export;
- nginx configuration.

## Product direction

After green CI, DutyLog proceeds to `v27.26.0 — Unified Time & Compensation Ledger`, connecting planned work, factual work, absences, overtime credits/usages and compensation sources in one reversible journal.
