# P1B2C3 — RU-KYA 2026 Reviewed Regional Statutory-Holiday Source Pack

Review date: 2026-09-04.

## Decision

Region: `RU-KYA` — Krasnoyarsk Krai.

Coverage:

- from `2026-01-01`;
- through `2026-12-31`;
- `complete=true`;
- regional positive facts: `0`.

This does **not** mean there are no public holidays in Krasnoyarsk Krai.
Federal Article-112 holidays remain owned by
`RuFederalStatutoryHolidayPolicy` and are evaluated before regional authority.

This pack states only that, for the reviewed 2026 window, the regional layer
adds no additional `NON_WORKING_PUBLIC_HOLIDAY` fact.

## Completeness basis

The current ConsultantPlus reference
`Нерабочие (праздничные) дни, установленные в субъектах Российской Федерации`
states that it presents non-working holiday days established by normative
legal acts of RF subjects.

Reviewed URL:

https://www.consultant.ru/document/cons_doc_LAW_311098/

At review date 2026-09-04 its subject list does not include Krasnoyarsk Krai.

The legal power for subject-level rules remains grounded in:

- TK RF Article 6:
  https://www.consultant.ru/document/cons_doc_LAW_34683/b5f8286871331a1188b20733154abe4957594b3b/
- Federal Law 125-FZ Article 4(7):
  https://www.consultant.ru/document/cons_doc_LAW_16218/ca78a0f4594e9666e8259f2b87a4df2e59a38cb4/

The negative conclusion is therefore a reviewed current legal-index result,
not an invented assumption that every region without explicit app data has
zero regional holidays.

## Source identities

Source-pack schema:

`DUTYLOG_REGIONAL_STATUTORY_HOLIDAY_SOURCE_PACK_V1`

Exact source-pack SHA-256:

`7ca56e78cb7c5342af5b73ad59a0326daf88d34d69e561e1825aaaa2ac3be9c3`

Semantic dataset fingerprint:

`3965ebb71bacbc610c799d81b96730399b0e0aba11779776fc9e58c608c27071`

The raw pack SHA and semantic fingerprint are deliberately separate.

## Taxonomy locks

The pack contains no:

- transferred rest day;
- transferred workday;
- employee rest day;
- commemorative day;
- professional holiday;
- municipal celebration.

For example, transferred federal rest-day mechanics remain schedule/rest-day
authority and never become a regional statutory public-holiday fact.

## Historical rule

The pack covers 2026 only.

2027 and later require a new legal review and a new source pack. The 2026 pack
is immutable and must not be rewritten in place.

## Runtime seed

Flyway V82 inserts exactly one immutable dataset manifest matching this pack.

There are intentionally no rows inserted into
`regional_statutory_holiday_date_facts` because the reviewed regional positive
fact set is empty.

## Payroll boundary

Still unchanged:

- HOLIDAY_PAY is not enabled;
- PayrollService is not wired;
- payroll money is unchanged;
- AnnualPaidVacationHolidayPolicy is unchanged;
- ProductionCalendar and LOCAL_OVERRIDE are not legal authority.

P1B3 remains blocked until the statutory/rest-day aggregation and immutable
payroll-reason provenance stage is implemented.
