# DutyLog v27.46.2 — Effective-Dated Pricing Settings & Payroll Verticals

## Purpose

v27.46.2 promotes the pricing foundation from v27.46.1 into a public, human-facing and browser-proven Payroll capability.

The product rule remains:

> The user describes reality. DutyLog derives the consequences.

## Effective-dated Pricing Terms

Public owner-scoped API:

- `GET /api/v1/payroll/pricing/terms`
- `PUT /api/v1/payroll/pricing/terms/{effectiveFrom}`
- `DELETE /api/v1/payroll/pricing/terms/{effectiveFrom}`

`PUT` replaces the complete aggregate for the exact effective date. Future effective dates are valid. `rules: []` is explicit base-only pricing. Deleting one exact version allows the preceding effective policy to become active again where applicable.

Pricing remains source-work-date based, so changing a current policy does not silently reinterpret historical work or immutable Payroll revisions.

## Human-facing Pricing Settings

Payroll exposes native settings for:

- NIGHT premium;
- HOLIDAY premium;
- OVERTIME cash-settlement tiers.

The UI uses percentages and hours rather than basis points, internal rule codes or `exclusiveGroup`. A new dated version starts from the active policy, and unknown advanced rules are preserved.

DutyLog does not insert implicit statutory coefficients.

## NIGHT and HOLIDAY

Actual Work is classified first. Pricing is applied afterwards.

Ordinary NIGHT and HOLIDAY work use the historical compensation rate and the effective Pricing policy belonging to the source work date. Payroll adds the premium delta separately from ordinary base pay.

Both verticals are proven through browser-visible Payroll preview and immutable snapshot.

## OVERTIME remains bank-first

Factual overtime first becomes Time Bank balance.

Banked overtime by itself does **not** create Payroll settlement money.

Only explicit cash settlement:

1. consumes canonical Time Bank minutes through FIFO;
2. retains source-work provenance;
3. resolves source-date historical compensation and Pricing;
4. applies configured overtime tiers;
5. creates settlement base plus premium money;
6. freezes deep pricing identity in the immutable Payroll revision.

Worked overtime and paid overtime therefore remain separate facts.

## Release contract

- OpenAPI: **141 operations / 147 schemas**.
- OpenAPI SHA-256: `19f794b9af9676dc21698f7a92e340c55c8d181dcae817395512c9d4cc063f46`.
- Flyway: **V61**; no v27.46.2 migration.
- Java: **1015 `@Test` methods / 219 test classes**.
- Vitest: **80 cases**.
- Playwright: **51 scenarios**.
- Browser raw ceiling: **945000 B**.
- Browser total gzip ceiling: **250000 B**.
- Canonical frontend runtime: Node **20.18.1**, npm **10.8.2**.

## Scope boundary

v27.46.2 does not add a generic allowance/component engine, tax engine, jurisdiction policy pack or multi-currency Payroll period.

The next Payroll step is generic compensation components with explicit semantic calculation bases.
