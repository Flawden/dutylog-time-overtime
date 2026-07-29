# Task Deadline Validation E2E Contract Hotfix

Status: v27.19.3.

## Problem

The v27.19.2 browser gate completed 27 of 28 scenarios. The only deterministic failure was an outdated assertion in `e2e/task-modules.spec.js`: it expected the legacy date-level validation text while the edited task already had a planned interval.

## Resolution

The scenario now expects the precise timed-task contract:

```text
Дедлайн не может быть раньше окончания запланированного интервала.
```

The runtime validation branch is unchanged. The older `Срок не может быть раньше времени задачи.` fallback remains valid for date/all-day cases and stays protected by Java service/controller tests.

## Scope

- one Playwright assertion updated;
- exact release metadata/cache versions advanced to v27.19.3;
- release documentation and gate assertions updated;
- no API, persistence, migration or production behavior changes;
- Flyway remains V37;
- baseline remains 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios.
