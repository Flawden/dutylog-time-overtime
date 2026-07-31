# v27.25.1 — Absence Preview Lambda Compile Hotfix

## Incident

GitHub Actions stopped during `maven-compiler-plugin:compile` before any Java or Playwright test could run:

```text
VacationPlannerService.java:[307,88]
local variables referenced from a lambda expression must be final or effectively final
```

`buildPreview(...)` iterated through a date range with an incremented loop variable and captured that variable directly inside the overlap-search lambda:

```java
for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
    overlaps.stream().filter(period -> covers(period, date));
}
```

Because the `for` update expression reassigns `date`, Java does not consider it effectively-final.

## Fix

Each iteration now snapshots the current value before the lambda:

```java
for (LocalDate date = range.from(); !date.isAfter(range.to()); date = date.plusDays(1)) {
    LocalDate previewDate = date;
    AbsencePeriod existing = overlaps.stream()
            .filter(period -> covers(period, previewDate))
            .findFirst()
            .orElse(null);
}
```

The same `previewDate` is used for vacation counting, planned-shift lookup, weekend projection and the preview item date. This keeps one coherent date value through the whole iteration.

## Regression protection

`AbsenceTimeOffOverhaulContractTest` and `release-check.sh` now require:

- `LocalDate previewDate = date;`;
- overlap lookup through `previewDate`;
- absence of direct `covers(period, date)` capture in that loop.

The additional Java contract advances the baseline to 109 test classes and 580 `@Test` methods. Playwright remains at 34 scenarios.

## Compatibility

The hotfix does not change:

- absence preview semantics;
- vacation or time-off balances;
- plan/fact calendar composition;
- Web or mobile API payloads;
- OpenAPI;
- PostgreSQL schema;
- Flyway V42;
- `.ics` projection;
- nginx configuration.

## Product direction after stabilization

After green CI, the next product release is `v27.26.0 — Unified Time & Compensation Ledger`: planned shifts, factual work, absences, overtime credits/usages and compensation sources become one reversible journal. Payroll Foundation follows after that ledger is stable.
