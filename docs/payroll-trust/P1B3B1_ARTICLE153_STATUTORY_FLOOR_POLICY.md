# P1B3B1 — Article 153 statutory economic floor policy

## Exact parent

`b15a13a1dcd25bc755c3da08568cbe3974cb8a63`

## Why this stage exists

P1B3A already owns the paid-`REGULAR` qualified cause:
`PUBLIC_HOLIDAY / EMPLOYEE_REST_DAY / BOTH`.

P1B3B discovery then proved:

- `HistoricalCompensationRateService` already owns date-effective HOURLY/SALARY
  base-rate identity;
- salary hourly value already derives from the source month's complete
  production norm;
- existing ordinary/base Payroll is bank-first and does not promote `OVERTIME`
  factual minutes into ordinary base pay;
- existing generic `PayPricingRule(HOLIDAY)` is not sufficient as Article 153
  authority because it exposes only one additive premium and cannot represent
  salary-within-norm, salary-above-norm and another-rest-day branches;
- no machine-owned fact currently represents the employee's Article 153 choice
  of another rest day for a specific qualifying work event;
- ordinary overtime-bank `отгул` is a different fact and must never be reused
  as the Article 153 election.

Therefore this stage introduces a source-locked **statutory floor only**. It does
not calculate Payroll money.

## Legal lock

Current consolidated Article 153 verified on 2026-09-05:

- TK RF Article 153:
  `https://www.consultant.ru/document/cons_doc_LAW_34683/a6a0176ee414c56cbffecc3d3fe9c161603a3b35/`
- Federal Law 30.09.2024 No. 339-FZ:
  `https://publication.pravo.gov.ru/document/0001202409300031`
  (effective 2025-03-01)
- Constitutional Court 28.06.2018 No. 26-P:
  `https://www.consultant.ru/document/cons_doc_LAW_301326/`

P1B3B1 is bounded to calendar 2026 and fails closed outside that window.

## Incremental tariff semantics

`additionalTariffBps` is the tariff amount the future `HOLIDAY_PAY` vertical
would add **on top of existing ordinary/base treatment**:

| Pay mode | Norm position | Choice | Statutory additional tariff |
|---|---|---|---:|
| HOURLY | N/A | enhanced pay | +1.00x |
| SALARY | within monthly norm | enhanced pay | +1.00x |
| SALARY | above monthly norm | enhanced pay | +2.00x |
| HOURLY | N/A | another rest day | +0.00x |
| SALARY | within monthly norm | another rest day | +0.00x |
| SALARY | above monthly norm | another rest day | +1.00x |

This table is deliberately **not final payable money**.

## Still required before HOLIDAY_PAY activation

Later authorities must still prove:

1. source-piece `WITHIN_MONTHLY_NORM / ABOVE_MONTHLY_NORM` classification;
2. actual employee election of another Article 153 rest day, with lifecycle and
   audit identity;
3. collective/local/employment-contract higher rate, if any;
4. applicable remuneration-system compensation/stimulating components;
5. immutable snapshot freeze of cause, norm position, election, legal source,
   rate source, policy/rule fingerprint and calculation-base composition.

## Non-negotiable guards

- no PayrollService wiring;
- no native HOLIDAY_PAY support;
- no money change;
- no DB migration;
- no OpenAPI change;
- no reuse of overtime-bank `отгул` as Article 153 election;
- no reuse of legacy `SourcePiece.holiday()` as legal qualification;
- P1B3A remains the only qualified-time/cause authority;
- overtime stays bank-first.
