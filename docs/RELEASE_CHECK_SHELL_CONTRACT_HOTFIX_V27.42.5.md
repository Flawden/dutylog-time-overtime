# v27.42.5 — Release Check Shell Contract Hotfix

## Exact failure
The exact v27.42.4 CI passed Maven and reached Release checks. Test inventory was already green at 792 `@Test` methods and 164 test classes. The recurring gate then failed on two release-check defects.

1. `release-check.sh` expected `ScheduleTemplateConcurrentSeedTest` to contain `concurrentFirstReadsSeedExactlyOnePresetSet`, while the committed regression method is `concurrentFirstListsSeedFivePresetsExactlyOnce`.
2. The v27.42.4 documentation assertion used double quotes around text containing backticks. Bash interpreted `@Test` as command substitution, emitted `@Test: command not found`, and searched for the corrupted text `792  methods`.

## Fix
- Require the real regression method name `concurrentFirstListsSeedFivePresetsExactlyOnce`.
- Quote the literal 792 `@Test` methods assertion with single quotes so backticks remain data.
- Keep the current baseline at 792 test methods / 164 test classes and align current release identity to v27.42.5.

## Boundary
No production Java/Vue runtime, People Profiles semantics, schedule-template `PESSIMISTIC_WRITE` locking, Flyway migration, HTTP/OpenAPI contract, dependency graph, browser bundle budget, retry or timeout behavior changes.
