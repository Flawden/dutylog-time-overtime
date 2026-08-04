# v27.35.5 — Gate A Quality Register Lambda Capture Compile Hotfix

## Failure

Maven compiled all production sources and reached test compilation, then rejected `VueDeliveryContractsDiagnosticsFoundationTest` because the mutable `for` loop counter `number` was captured directly inside a stream lambda. Java requires captured local variables to be final or effectively final.

## Fix

Each iteration now creates an immutable prefix:

```java
String rowPrefix = "| Q-0" + number + " ";
String row = register.lines()
        .filter(line -> line.startsWith(rowPrefix))
        .findFirst()
        .orElseThrow();
```

The assertions for Q-02 through Q-05 remain identical in meaning. No test is skipped, no status is hard-coded, and no runtime source changes.

## Scope

- test-only Java compile hotfix;
- release identity and cache version updated to `27.35.5`;
- authentic committed npm graph preserved;
- API, OpenAPI, PostgreSQL, Flyway V47, domain ownership and one-image topology unchanged.
