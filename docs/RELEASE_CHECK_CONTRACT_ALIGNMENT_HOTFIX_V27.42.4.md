# v27.42.4 — Release Check Contract Alignment Hotfix

## Exact failure
The exact v27.42.3 CI passed the Maven test gate and reached Release checks. The recurring release gate then failed with 10 static-contract errors: it still expected 791 `@Test` methods / 163 test classes, still described v27.42.3 as the older People Profiles source-contract hotfix, and still required a stale frontend lockfile-manifest SHA.

## Fix
- baseline is aligned to 792 `@Test` methods and 164 `*Test.java` classes;
- current release identity is v27.42.4 Release Check Contract Alignment Hotfix;
- historical checks retain the actual v27.42.1, v27.42.2 and v27.42.3 release identities;
- the generated frontend lockfile manifest is regenerated from the committed lockfile after the version bump.

## Boundary
No People Profiles runtime, schedule-template locking behavior, HTTP/OpenAPI contract, Flyway migration, dependency graph, browser bundle budget, retry or timeout behavior changes.
