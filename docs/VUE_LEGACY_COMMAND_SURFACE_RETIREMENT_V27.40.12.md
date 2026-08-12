# v27.40.12 — Legacy Command Surface Retirement

## Baseline

The release starts from the proven-green v27.40.11 staging tree.

## Retired compatibility capabilities

Quick Actions, Task/Important editor/detail flows and Shift Type Manager already have native Vue owners. Their old bridge capabilities are therefore dead API surface rather than recovery behavior. v27.40.12 removes `openModal`, `openTaskCreate`, `openTaskDetails` and `openImportantDetails` from `LegacyBridge`, `DutyLogLegacyPlatform` and the global TypeScript declaration.

The typed `dutylog:legacy-command` fallback remains only for `navigate` and `logout`; the obsolete `open-modal` variant is removed. Existing legacy JavaScript may still call its local functions during pre-Vue recovery, but Vue no longer reaches those functions through the generic platform adapter.

## Boundaries intentionally retained

- Payroll/Admin continue to use the released hash-router compatibility path.
- `dataLayer` remains the sole IndexedDB/offline mutation and reconnect queue owner.
- Calendar day writes and Productivity offline adapters remain narrow transport capabilities until the offline boundary is retired deliberately.
- OpenAPI remains 118 operations / 120 schemas and Flyway remains V47.

## Acceptance

The release is accepted only after the exact frontend gate, Maven 758/758, clean canary, Chromium 48/48 with zero flaky retries, immutable image/PostgreSQL V47 smoke and staging deployment are green.
