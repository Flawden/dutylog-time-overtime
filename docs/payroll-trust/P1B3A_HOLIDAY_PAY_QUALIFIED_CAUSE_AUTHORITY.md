# P1B3A — HOLIDAY_PAY qualified cause authority

## Scope

P1B3A establishes one machine-owned **qualified-time + legal-cause** authority for the future
`HOLIDAY_PAY` earning. It deliberately does **not** calculate money and does **not** wire
`HOLIDAY_PAY` into `PayrollService` or the native qualified-quantity support registry yet.

Exact parent for this stage:

`74d51e8d8514493d3040e3702e6b55fa9de1901a`

## Why the existing `SourcePiece.holiday` bit is not enough

The existing ordinary-premium source already gives canonical paid `REGULAR` pieces and excludes
`OVERTIME` from ordinary Payroll. That source is reused as the **time/slice authority**.

However the old `holiday` bit is a pay-classification dimension. P1B discovery proved that the
real economic earning observed on payslips is "праздничные и выходные": it can be caused by either
of two legally distinct facts:

- a statutory non-working public holiday;
- an employee-specific rest day.

Therefore P1B3A never derives `HOLIDAY_PAY` from `piece.holiday()`.

## P1B3A authority

`HolidayPayQualifiedCauseAuthorityService`:

1. asks `OrdinaryWorkPremiumSourceService` for the canonical paid `REGULAR` pieces for each payroll date;
2. ignores dates with zero paid REGULAR pieces;
3. requires deep source identity for every non-zero piece;
4. resolves both:
   - `StatutoryPublicHolidayAuthorityService`;
   - `EmployeeRestDayAuthorityService`;
5. fails the whole month closed if either required authority is unresolved;
6. labels every qualifying piece with exactly one immutable semantic cause:
   - `PUBLIC_HOLIDAY`;
   - `EMPLOYEE_REST_DAY`;
   - `BOTH`;
7. counts a `BOTH` piece once, never twice;
8. retains **both** resolved authority records on each qualified piece, including the negative
   authority where only one legal cause applies.

This stage therefore proves qualified minutes and exact cause identity without inventing money.

## Legal lock for later P1B3 money

Fresh verification on 2026-09-05 used the current consolidated Labour Code text and official
amending act:

- TK RF Article 153, current consolidated edition dated 2026-05-25:
  `https://www.consultant.ru/document/cons_doc_LAW_34683/a6a0176ee414c56cbffecc3d3fe9c161603a3b35/`
- Federal Law 30.09.2024 No. 339-FZ, official publication:
  `https://publication.pravo.gov.ru/document/0001202409300031`
- Constitutional Court 28.06.2018 No. 26-P:
  `https://www.consultant.ru/document/cons_doc_LAW_301326/`

P1B3 money must not collapse these legal branches:

- salary employee inside monthly norm;
- salary employee above monthly norm;
- hourly/daily/piece-rate pay;
- employer/contract/local rule above statutory minimum;
- employee election of another rest day;
- compensation/stimulating payments required by the applicable pay system.

Those economic questions are **P1B3B+**, not guessed in P1B3A.

## Non-negotiable invariants

- paid `REGULAR` slices only;
- unpaid breaks never resurrect;
- `OVERTIME` remains bank-first;
- no second pay classifier;
- no Saturday/Sunday guess;
- no negative statutory-holiday inference without complete regional authority;
- no Payroll money change;
- no DB migration;
- no OpenAPI change;
- `HOLIDAY_PAY` remains unsupported by `PayrollNativeQualifiedQuantityService` until the money +
  immutable snapshot path is proven.
