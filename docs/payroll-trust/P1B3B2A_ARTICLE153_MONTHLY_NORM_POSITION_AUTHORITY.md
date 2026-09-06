# P1B3B2A — Article 153 monthly norm-position authority

## Exact parent

`ee89e13f180d2578c6347acbf5521e5e6d345305`

## Why this substage exists

P1B3B1 proved the statutory tariff floor but deliberately left salary
`WITHIN_MONTHLY_NORM / ABOVE_MONTHLY_NORM` unresolved.

Repository discovery at the exact P1B3B1 GREEN head proved:

- `HistoricalCompensationRateService` already owns date-effective HOURLY/SALARY
  identity and, for salary, resolves the complete source-month production norm;
- `ProductionCalendarService` owns that production norm and fails closed when
  salary schedule coverage is incomplete;
- `TimeCompensationService.payrollSource` is the canonical factual worked-minute
  source used by Payroll and includes factual work independently of whether
  ordinary hourly base later keeps overtime bank-first;
- P1B3A already owns the exact paid-REGULAR qualifying source pieces and legal
  cause for the future `HOLIDAY_PAY` earning;
- no existing service owns Article 153 salary monthly-norm position.

Therefore P1B3B2 is split internally:

1. **P1B3B2A** — monthly norm-position authority (this stage);
2. **P1B3B2B** — persistent employee election for the Article 153 other-rest-day
   branch, with its own DB lifecycle/audit proof.

## Authority semantics

`Article153MonthlyNormPositionAuthorityService` consumes P1B3A qualification.

### HOURLY

Every P1B3A qualified piece resolves to:

`NormPosition.NOT_APPLICABLE`.

No monthly salary norm is invented and the canonical Payroll worked source is
not queried merely to manufacture one.

### SALARY

The service resolves the same historical compensation authority already used by
salary hourly-value calculation and requires one positive source-month
`productionNormMinutes`.

It then consumes the canonical factual worked-minute month from
`TimeCompensationService.payrollSource` and calculates `workedMinutesBeforeDate`
for every calendar date.

A qualifying date is:

- `WITHIN_MONTHLY_NORM` when the entire factual worked day ends at or before the
  monthly norm;
- `ABOVE_MONTHLY_NORM` when the entire factual worked day starts at or after the
  monthly norm;
- **blocked** with `ARTICLE153_MONTHLY_NORM_BOUNDARY_AMBIGUOUS` when the monthly
  norm boundary is crossed inside that qualifying date.

The last case is intentionally fail-closed. Day totals alone do not prove the
intra-day ordering between a P1B3A qualifying piece and any other worked minute.
This stage never allocates that ambiguity by proportional split, weekday guess,
schedule duration or overtime-bank order.

## Identity

Every ready norm-position piece retains the original P1B3A `QualifiedPiece`,
compensation effective date, pay mode, production norm, factual worked minutes
before/on the date and a deterministic SHA-256 decision fingerprint over the
exact paid-source identity plus those authority inputs.

The decision fingerprint is provenance only. It is not yet a Payroll snapshot
freeze.

## Non-negotiable guards

- P1B3A remains the only HOLIDAY_PAY qualified-time/cause authority;
- P1B3B1 remains statutory-floor-only;
- no `PayrollService` wiring;
- no native `HOLIDAY_PAY` registry activation;
- no money change;
- no DB migration;
- no OpenAPI change;
- overtime remains bank-first;
- no reuse of overtime-bank `отгул` as Article 153 election;
- P1B3B2B election authority remains a separate next substage.
