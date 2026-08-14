# v27.42.3 — Concurrent Schedule Seed Test Isolation Hotfix

## Exact failure
v27.42.2 reached the Java 17 Maven suite and ran 792 tests with exactly one failure:

`UserAdminServiceTest.paginationClampsNegativePageAndTinyOrHugeSizes` expected 22 users but observed 23.

The new `ScheduleTemplateConcurrentSeedTest` had already committed its unique `schedule-seed-race-*` owner and five templates in the shared Spring/H2 test context. Because that class was not transactional and did not discard its context, the later admin pagination test saw one unrelated persisted user.

## Fix
`ScheduleTemplateConcurrentSeedTest` is now annotated with `@DirtiesContext(classMode = AFTER_CLASS)`. Spring discards the test ApplicationContext and its H2 database after the concurrency class, preserving cross-class isolation while still allowing the two worker transactions to exercise the real locking behavior.

## Boundary
The v27.42.2 production `PESSIMISTIC_WRITE` owner lock is unchanged. No product runtime, People Profiles behavior, HTTP/OpenAPI contract, Flyway schema, offline owner, bundle budget, dependency, timeout, retry or Playwright behavior is changed.
