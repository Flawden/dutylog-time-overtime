# v27.36.8 — Vue Read Sequencing Static Contract Alignment Hotfix

## Incident

The local Maven verify for v27.36.7 compiled 148 test classes and executed 721 tests, then failed only three historical static assertions. Production code already used the accepted shared `readSequence`; the stale tests still searched for `refreshSequence` or the pre-v27.36.7 Vitest scenario name.

## Fix

- `VueAbsenceTimeBankMigrationTest` now binds duplicate-mutation and stale-read protection to `readSequence`.
- `VueAbsenceTimeBankBrowserParityHotfixTest` now requires projection publication after the winning shared read.
- `SinglePassCiFinalVueBrowserParityHotfixTest` now references the canonical snapshot-stability Vitest scenario.
- `VueReadSequencingStaticContractAlignmentHotfixTest` compile-gates the alignment and rejects reintroduction of `refreshSequence`.

## Scope

This is a test-contract and release-identity hotfix only. It changes no Vue store/API runtime, Spring Boot business rule, HTTP contract, OpenAPI shape, npm graph, database schema, Flyway migration, CI routing or Chromium expectation.

## Acceptance

The release remains accepted only after exact frontend gate, Maven/JaCoCo, 45/45 Chromium, immutable-image clean PostgreSQL smoke and staging deployment are green.
