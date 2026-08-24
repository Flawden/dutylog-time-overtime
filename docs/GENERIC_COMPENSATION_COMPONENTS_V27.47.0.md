# DutyLog v27.47.0 — Generic Compensation Components

## Goal

v27.47.0 turns recurring allowances and bonuses into a native Payroll concept without turning user-entered labels into hardcoded business semantics.

The invariant is:

> **User-owned name → explicit machine formula/base → backend Payroll consequence → immutable explainable snapshot.**

## Configuration model

A stable compensation component owns effective-month versions.

Each version stores:

- arbitrary user-owned `displayName`;
- calculation type: `FIXED_AMOUNT` or `PERCENT_OF_BASE`;
- percentage base where applicable: `EARNED_BASE_PAY` or `NOMINAL_SALARY`;
- fixed amount/currency or percentage rate;
- historical `enabled` state.

Disabling a component does not delete history. The effective resolver still exposes the disabled version, while Payroll skips it before formula evaluation.

There is intentionally no generic component DELETE workflow.

## API

Authenticated owner-scoped operations are exposed under `/api/v1/payroll/compensation-components`:

- history/list;
- stable-component create;
- effective-month resolution;
- exact-month version upsert.

OpenAPI is **145 operations / 151 schemas** with SHA-256:

`56442081179218567fafea52e21e0e7b4fc00e9b96789b1058715809225df78f`

## Payroll semantics

Generic component earnings are a distinct acyclic phase:

`Base Pay → Generic Components → Time-derived Premiums → Overtime Settlement → Manual Adjustments → Total`

`EARNED_BASE_PAY` resolves to the current base-pay result only. It does not include generic component earnings, later premiums, settlement money or manual additions.

`NOMINAL_SALARY` requires a salary-mode nominal salary. Using that base in an incompatible context fails closed with `PAYROLL_COMP_COMPONENT_BASE_UNAVAILABLE`.

Fixed payments require matching Payroll currency.

The backend remains the only authority for calculated money and `totalPayMinor`.

## Immutable snapshot

Flyway **V63** extends Payroll snapshots with:

- component count;
- generic component earnings aggregate;
- deterministic component fingerprint;
- frozen `payroll_snapshot_compensation_component_lines`.

Frozen lines store scalar component/version identity, user display name, formula configuration, resolved reference base and calculated result.

They deliberately do not depend on mutable current component configuration.

The browser E2E proves:

1. revision 1 is calculated with the original arbitrary label and 10% rate;
2. current configuration is renamed and changed to 20%;
3. live preview follows the new configuration;
4. revision 2 freezes the new configuration;
5. re-reading revision 1 still returns the original name, rate and amount.

## UI

Payroll Settings exposes generic compensation components without internal IDs or legally loaded names.

Neutral presets only prefill one of the existing formula families:

- percent of earned base pay;
- percent of nominal salary;
- fixed payment.

The preset has no persistent identity after component creation.

Payroll explainability displays generic component total and individual calculated lines. Historical revisions render their frozen snapshot lines, never the current mutable configuration.

## Persistence

Flyway advances from V61 to **V63**:

- `V62__generic_compensation_components.sql` — stable components and effective-month versions;
- `V63__payroll_snapshot_compensation_components.sql` — immutable snapshot aggregate and frozen component lines.

## Regression / delivery identity

- Java: **1052 @Test methods / 227 test classes**
- Vitest: **88**
- Playwright: **52**
- OpenAPI: **145 / 151**
- Flyway: **V63**
- Browser ceiling: **970000 B raw / 250000 B gzip**
- Canonical generic-compensation development slices and the 52nd Chromium journey are staging-green.

## Explicitly outside v27.47.0

v27.47.0 does not introduce:

- arbitrary Excel-like formulas;
- cyclic component-to-component dependency graphs;
- qualified-time formulas;
- eligible-earnings phase aggregates;
- tax/jurisdiction policy;
- Payment Ledger;
- multi-currency within one Payroll period;
- legally loaded harmfulness/regional/etc. preset semantics.

Those richer calculation-base semantics begin with **v27.48.0 — Real Payroll Bases & Qualified-Time Formulas**.
