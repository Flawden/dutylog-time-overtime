# v27.28.1 — Payroll Module Registry Contract Hotfix

## CI failure

`mvn verify` reached the test phase and failed in `PayrollFoundationContractTest`.
The production registry was already correct:

```java
import static ru.daniil.shifts.module.ModuleKeys.*;

new ModuleContract(
        PAYROLL,
        ModuleCategory.TIME_ACCOUNTING,
        ...
)
```

The test incorrectly required the unrelated source literal:

```java
ModuleService.PAYROLL
```

That string is valid in controller guards, but it is not the canonical spelling used by
`DutyLogModules`, whose keys intentionally come from the static `ModuleKeys.*` import.

## Fix

The contract now verifies both semantic boundaries:

```java
public static final String PAYROLL = "payroll";
```

and:

```java
PAYROLL,
ModuleCategory.TIME_ACCOUNTING,
```

This keeps `ModuleKeys` as the stable key source and `DutyLogModules` as the single module registry
without forcing one implementation style into another class.

## Scope

Unchanged:

- `PayrollService`;
- Payroll controller and DTOs;
- OpenAPI;
- browser behavior;
- V45 and all previous migrations;
- PostgreSQL schema;
- 116 Java test classes / 603 tests / 37 Playwright scenarios.

This is a build-contract hotfix only.
