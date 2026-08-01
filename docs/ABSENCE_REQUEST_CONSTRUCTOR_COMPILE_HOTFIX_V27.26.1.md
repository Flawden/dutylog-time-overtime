# v27.26.1 — Absence Request Constructor Compile Hotfix

## Failure

GitHub Actions compiled the production sources for v27.26.0 and stopped in Maven `testCompile` before any JUnit or Playwright scenario could run.

Four fixtures in `VacationPlannerServiceTest` still used the former nine-argument constructor shape:

```java
new AbsencePeriodCreateRequest(
    typeId, title, startDate, endDate, status, note,
    coverage, startTime, endTime
)
```

The Unified Time & Compensation Ledger extended the canonical request with an explicit tenth argument:

```java
compensationPolicy
```

The DTO intentionally exposes the compact six-argument compatibility constructor and the complete ten-argument record constructor. A nine-argument overload does not exist, so Java correctly rejected the stale fixtures.

## Fix

The four time-off fixtures now use the complete compensation-aware contract and explicitly preserve their domain meaning:

```java
new AbsencePeriodCreateRequest(
    timeOff.id(), title, date, date, status, null,
    coverage, startTime, endTime, "OVERTIME_BANK"
)
```

This covers:

- two non-overlapping partial time-off windows;
- the insufficient-overtime rejection case;
- a full-day time-off occurrence backed by previously earned time.

## Regression protection

`UnifiedTimeCompensationLedgerContractTest` verifies the compensation-aware fixtures. `release-check.sh` also parses every `AbsencePeriodCreateRequest(...)` call in `VacationPlannerServiceTest` and fails when a nine-argument invocation returns.

## Compatibility boundary

No production behavior changes in this hotfix:

- Unified ledger rules remain unchanged;
- linked FIFO usages remain absence-owned and reversible;
- HTTP API and OpenAPI remain unchanged;
- PostgreSQL schema remains unchanged;
- Flyway remains V43;
- no new migration is added.

## Expected CI baseline

- 110 Java test classes;
- 591 `@Test` methods;
- 35 Playwright scenarios.
