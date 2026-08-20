# DutyLog v27.46.1 — Temporal Work Context & Native Pay Pricing

## Purpose

v27.46.1 extends the v27.46.0 Payroll foundation from base compensation into historical work interpretation, overlapping pay classification, bank-first overtime settlement and configurable premium pricing.

> The user describes reality. DutyLog derives the consequences.

## Canonical flow

`Reality → Temporal Work Context → Classification → Time Bank / Settlement → Pricing → Payroll Snapshot`

Temporal context owns historical timezone interpretation. Classification owns factual REGULAR / NIGHT / HOLIDAY / OVERTIME dimensions. Time Bank owns fungible overtime balance and FIFO consumption. Pricing resolves effective-dated rules and historical compensation rates. Payroll assembles money and freezes immutable revisions.

## Temporal Work Context

Work timezone is effective-dated. Changing today's timezone does not rewrite historical interpretation. Actual Work may carry source timezone plus exact start/end instants; legacy rows remain explicitly distinguishable until reconstructed.

## Classification and provenance

REGULAR / NIGHT / HOLIDAY / OVERTIME are overlapping dimensions. Cross-midnight work retains source-workday ordinal continuity for overtime thresholds. System-derived overtime stores factual provenance slices and FIFO allocations materialize canonical consumed offsets.

Pricing multipliers do not live in provenance.

## Bank-first settlement

Earned overtime enters the Time Bank first. It becomes money only through an explicit settlement. Settlement consumes the same canonical FIFO bank but is not a paid absence and does not inflate ordinary HOURLY base.

## Pricing

Pricing rules are effective-dated and country-neutral. Missing rules remain different from explicit zero-premium rules. Settlement and ordinary NIGHT/HOLIDAY pricing produce deterministic deep fingerprints over factual source identity, resolved pricing policy and historical rate identity.

## Payroll

HOURLY ordinary base excludes banked overtime. Explicit settlement money and ordinary NIGHT/HOLIDAY premium are separate additive components.

Flyway V59–V61 extends immutable Payroll snapshots with settlement aggregates/fingerprint and ordinary-premium minutes/reference-base/premium/fingerprint. Historical revisions receive neutral backfills and retain their original total money.

Payroll UI explains ordinary premium separately from overtime settlements. `totalPayMinor` remains backend authority.

## Contract and regression baseline

- OpenAPI: 138 operations / 144 schemas.
- OpenAPI SHA-256: `1c76051d23596643e6cd2c92a248bfa7126c0e7a33c62587cea3c62a11d38352`.
- Flyway source tail: V61.
- Java: 1006 `@Test` methods / 216 test classes.
- Vitest: 80 cases.
- Playwright: 48 scenarios.
- Canonical frontend: Node 20.18.1 / npm 10.8.2.
- Production JS graph: 924966 B raw under the accepted 925000 B ceiling; gzip remains below 250000 B.

## Remaining release proof

Source, targeted implementation proofs, local full Maven/JaCoCo and release-check are complete. Before v27.46.1 is called fully green, the remaining release gates are Chromium 48/48, immutable-image startup on clean PostgreSQL executing Flyway V1→V61, and final staging acceptance.
