# DutyLog v27.36.0 — Vue Absence & Time Bank

Date: 2026-08-04
Baseline: v27.35.7
Migration manifest: `docs/migration/absence-time-bank-vue-migration-manifest.md`

## Purpose

This is the first product-domain Vue migration after Gate A. It replaces the runtime owners for Absence and Time Bank as one bounded domain while keeping Spring Boot authoritative for all business rules and persisted state.

## Migrated surface

- Absence journal, filters, balances and two-way Time Bank links.
- Unified full-day and partial-day Absence Composer with preview and compensation context.
- Time Bank overview, plan/fact/compensation, integrity and actual-work explanation.
- Responsive credit ledger, daily totals, chart, mobile disclosure cards and usage ownership.
- Exact-interval credit editor and reusable quick-scenario manager.
- FIFO source queue and future usage forecast.
- Typed Today/Calendar adapter commands for still-legacy callers.

## Backend authority retained

Spring Boot remains the sole owner of allowance arithmetic, overlap validation, compensation policy, exact credited minutes, ownership, FIFO reservation/posting/reversal, closed accounting periods and ledger integrity. Vue forecasts are explanations only; every write is revalidated by the backend.

## Generated API contract

The canonical OpenAPI document adds typed quick-scenario CRUD and named preview/type-summary schemas. The generator now distinguishes array item refs from direct refs and limits `allOf` inheritance to actual `allOf` entries. The committed result contains 98 operations and 103 schemas, including correct arrays for overtime credits, usages, allocations and absence preview rows.

## Q-06 concurrency boundary

- A monotonically increasing refresh token prevents an older read response from overwriting a newer screen state.
- A shared mutation lock rejects duplicate clicks while a domain write is pending.
- HTTP 409 produces a durable conflict message and refreshes the server model before retry.
- Preview requests are abortable and remain side-effect free.
- Playwright proves a native double click produces exactly one create request.

## Legacy retirement

`#view-vacation`, `#view-overtime` and the legacy Absence/credit modal owners are retired when the Vue workspace mounts. Legacy route functions return through the typed Vue domain before rendering. Named adapters remain only because Today and Calendar have not migrated yet; physical deletion of dead ordered-script blocks remains scheduled for v27.40.0.

## Verification baseline

- 142 Java test classes / 685 `@Test` methods.
- 45 Chromium Playwright scenarios.
- 26 Vitest cases.
- 98 generated operations / 103 generated schemas.
- Flyway V1–V47 unchanged.

## Release review

```text
Migrated: Absence Composer, absence journal, Time Bank overview/credits/usages/FIFO, exact credit and scenario editor.
Parity verified: backend lifecycle, balances, split projections, plan/fact, integrity, responsive ledger, two-way links and error paths.
Legacy removed: legacy Absence/Time Bank route and modal owners are retired; Vue is the only runtime owner.
Bridge removed/remaining: no legacy render bridge remains; named typed Today/Calendar commands remain until those domains migrate.
OpenAPI changes: typed quick-scenario CRUD, named AbsencePreviewItem/AbsenceTypeSummary and corrected array/allOf generation; 98 operations / 103 schemas.
PWA impact: versioned Vue assets and service-worker cache identity advance to 27.36.0; no offline mutation queue.
Rollback impact: application-image rollback to v27.35.7; no schema or data rollback.
Known limitations: physical deletion of dead ordered-script blocks waits for final legacy retirement after remaining callers migrate.
```
