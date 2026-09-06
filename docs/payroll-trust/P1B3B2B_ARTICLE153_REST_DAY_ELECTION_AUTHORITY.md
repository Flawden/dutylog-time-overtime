# P1B3B2B — Article 153 other-rest-day election authority

## Exact parent

`c17a683bbbb09dd45ea4e66cda4b67dd6c0127a7`

## Purpose

P1B3A owns the exact paid REGULAR work pieces and legal cause
`PUBLIC_HOLIDAY / EMPLOYEE_REST_DAY / BOTH`.

P1B3B1 owns only the source-locked Article 153 statutory additional-tariff
matrix.

P1B3B2A owns salary `WITHIN_MONTHLY_NORM / ABOVE_MONTHLY_NORM` and explicitly
fails closed when a monthly norm boundary crosses inside a qualifying date.

P1B3B2B adds the remaining employee-choice fact needed by that policy: a
persistent, source-locked election of the Article 153 **another rest day**
branch for one exact P1B3A-qualified work event/date.

## Why ordinary DutyLog "отгул" is not reused

DutyLog's existing overtime bank is already a separate accounting domain:

- `overtime_credits` accrue banked overtime minutes;
- `overtime_usages` consume those minutes;
- `overtime_allocations` assign usages FIFO to credits;
- Vacation Planner may create an `OVERTIME_BANK` usage for a time-off absence.

Those rows prove bank accrual/consumption. They do **not** prove that the
employee elected the Article 153 compensation branch for a specific
weekend/public-holiday work event.

Therefore V83 creates `article153_rest_day_elections` as a separate domain.
The migration does not reference or rewrite overtime-bank rows.

## Source identity

One election is unique by:

`(user_id, work_date, source_identity)`

where `source_identity` is exactly one of:

- `EXPLICIT:<actual_work_interval_id>`;
- `PLAN_DERIVED:<day_entry_id>`.

The row also freezes:

- source evidence start/end instants and timezone;
- P1B3A qualified cause;
- aggregate qualified minutes for the event/date;
- SHA-256 `source_event_fingerprint` over the complete ordered P1B3A source
  pieces plus statutory-public-holiday and employee-rest-day provenance.

If current P1B3A evidence no longer matches the stored fingerprint, the
authority fails closed with `ARTICLE153_REST_DAY_ELECTION_SOURCE_CHANGED`.

## Lifecycle

The only positive legal-choice state introduced here is `ELECTED`.

A persisted row may be changed to `REVOKED` only through the explicitly named
`revokeForCorrection` path with a nonblank correction reason. The row is never
deleted and keeps the election/revocation timestamps and source fingerprint.

`REVOKED` is deliberately **not** treated as enhanced-pay choice. Resolution
returns `ARTICLE153_REST_DAY_ELECTION_REVOKED_REQUIRES_REVIEW` so later stages
must explicitly decide any legally permissible replacement, consumption,
termination settlement or payroll correction semantics.

Re-election after revoke is not authorized in this stage.

## Important current boundary

Creating/resolving an election currently requires the owning P1B3A payroll
month to be fully ready. P1B3B2B does not add a second day-level legal
qualification engine. That preserves P1B3A as the single qualified cause/time
authority. A later user-facing workflow may add a P1B3A-owned date-level entry
point without duplicating its logic.

## Non-negotiable guards

- P1B3A qualified cause authority unchanged;
- P1B3B1 statutory floor unchanged;
- P1B3B2A norm-position authority unchanged;
- no `PayrollService` wiring;
- no native `HOLIDAY_PAY` registry activation;
- no payroll money change;
- no OpenAPI/UI change;
- no snapshot freeze yet;
- no automatic scheduled rest-day creation;
- no overtime-bank credit/usage/allocation mutation;
- no synthetic historical election backfill;
- V83 is additive only.

## Still required before HOLIDAY_PAY activation

P1B3B2B closes the explicit Article 153 other-rest-day election fact, but later
Payroll Trust stages must still prove at least:

1. local/collective/employment-contract higher Article 153 rate authority;
2. applicable compensation/stimulating remuneration-system components;
3. mapping of active election/norm position into the economic policy without
   double paying ordinary/base treatment;
4. immutable snapshot freeze of cause, norm position, election, legal source,
   rate source, rule fingerprint and calculation-base composition;
5. any user-facing election/rest-day scheduling lifecycle without conflating it
   with the overtime bank.
