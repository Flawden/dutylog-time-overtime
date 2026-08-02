# v27.28.0 — Payroll Foundation

Payroll Foundation is the first money layer built on the closed and integrity-checked time ledger introduced in V44. It does not recalculate calendar semantics in a second place: `PayrollService` consumes the posted-only `TimeCompensationService.payrollSource(...)` projection.

## Product boundary

The release supports:

- one owner-scoped hourly rate stored in minor currency units;
- one ISO-style three-letter currency code;
- preview for any month;
- final calculation only for a closed accounting period with a healthy ledger;
- paid time from actual work, approved/completed vacation, sick leave and overtime-backed time off;
- unpaid time as a visible source metric;
- append-only monetary additions and deductions;
- immutable versioned payroll snapshots;
- supersession links instead of overwriting an earlier calculation;
- a SHA-256 calculation hash over time inputs, rate, adjustments and close timestamp;
- a dedicated Payroll workspace with transparent time and money breakdowns.

The release intentionally does not include taxes, night coefficients, overtime multipliers, legal sick-pay formulae, average-vacation-pay formulae or employer-specific bonuses. Those rules must be layered on top of a trusted base calculation rather than mixed into the first financial schema.

## V45 schema

`V45__payroll_foundation.sql` adds three owner-scoped tables:

- `payroll_settings` — currency and hourly rate;
- `payroll_adjustments` — append-only additions/deductions;
- `payroll_snapshots` — immutable month revisions and source provenance.

All money is stored as integer minor units. No floating-point database columns are used. The migration is additive and contains no `DROP TABLE` or reverse SQL.

## Calculation contract

A final snapshot requires all three conditions:

1. the accounting month is `CLOSED`;
2. ledger integrity is healthy;
3. the hourly rate is greater than zero.

The base formula is:

```text
payable minutes = worked minutes
                + approved paid-absence minutes
                + append-only time adjustments

base pay = hourly rate × payable minutes ÷ 60

total pay = base pay + additions − deductions
```

Money rounding is performed once with `HALF_UP` to the nearest minor unit. Preview and snapshots expose every contributing minute and money subtotal.

## API

```http
GET   /api/v1/payroll/periods/{yyyy-MM}
PATCH /api/v1/payroll/settings
POST  /api/v1/payroll/adjustments
POST  /api/v1/payroll/periods/{yyyy-MM}/calculate
```

Legacy `/api/payroll/**` aliases are provided during the browser API transition. Read responses are private and `Cache-Control: no-store`.

## Snapshot revisions

Calculation never updates an existing revision. A new calculation creates revision `N + 1`; the preceding snapshot receives only a `superseded_by_id` pointer. Historical amounts, inputs, close timestamps and hashes remain inspectable.

## Security and privacy

- Every repository query is owner-scoped.
- The Payroll module depends on Overtime and Vacation and is guarded by the module registry.
- Browser writes retain session/CSRF protection.
- Financial read models use `no-store`.
- Raw session, calendar-feed tokens and secrets are never copied into payroll tables or hashes.
