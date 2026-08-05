# DutyLog v27.36.2 — Vue Timer Static Contract Compile Coverage Hotfix

## Scope

This is a bounded test-compile and local-gate hotfix that closes the missing compile coverage. It changes no production Vue or Java behavior.

## Confirmed failure

`VueBrowserTimerHandleTypeHotfixTest` embedded a multiline TypeScript snippet inside an ordinary Java string literal. Maven stopped during `testCompile` with `unclosed string literal` and cascading parser errors. The local release gate did not detect it because it compiled only files ending in `FrontendContractTest.java`.

## Resolution

- Rename the contract to `VueBrowserTimerHandleTypeFrontendContractTest`.
- Normalize source whitespace with `replaceAll("\\s+", " ")` and compare a valid concatenated Java string.
- Compile both `*FrontendContractTest.java` and source-only `*HotfixTest.java` files in `release-check.sh`.
- Apply the same set to the hardcoded frontend asset-version scan.

## Preserved boundaries

- `window.setTimeout` / `window.clearTimeout` behavior is unchanged.
- Absence & Time Bank Vue ownership is unchanged.
- OpenAPI remains 98 operations / 103 schemas.
- Authentic npm graph remains 134 packages / 43 graph entries.
- PostgreSQL and Flyway remain unchanged at V47.
- Rollback requires no schema or data action.
