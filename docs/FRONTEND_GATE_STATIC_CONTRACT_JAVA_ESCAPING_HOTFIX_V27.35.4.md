# DutyLog v27.35.4 — Frontend Gate Static Contract Java Escaping Hotfix

## Confirmed failure

GitHub Actions completed the committed-lockfile frontend gate: authentic graph verification, `npm ci`, `vue-tsc`, all 16 Vitest cases and the Vite build. Maven then compiled all 161 production sources and stopped in `testCompile` on one malformed Java assertion in `AuthenticLockfileCommitGeneratedClientFixtureHotfixTest`.

The source embedded a shell command with unescaped Java quotes:

```java
assertTrue(gate.contains("npm --prefix "$FRONTEND_DIR" ci"));
```

## Fix

The expected shell fragment is represented as a valid Java string:

```java
assertTrue(gate.contains("npm --prefix \"$FRONTEND_DIR\" ci"));
```

No production code, dependency graph, API, schema, persistence or frontend behavior changes. The release gate additionally asserts the exact escaped source contract.

## Acceptance

- committed authentic lockfile remains unchanged except root release identity;
- frontend gate remains strict and lockfile-only;
- Maven `testCompile` must pass;
- complete Maven, Playwright, image, clean PostgreSQL and staging chain must become green before Gate A is accepted.
