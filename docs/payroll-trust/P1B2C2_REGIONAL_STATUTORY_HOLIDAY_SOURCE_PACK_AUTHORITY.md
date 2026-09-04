# P1B2C2 — Regional Statutory Holiday Source-Pack / Import Authority

Reviewed legal boundary: 2026-09-04.

## Goal

P1B2C1 created the immutable regional legal dataset model.

P1B2C2 closes the supply-chain boundary between a reviewed legal source pack
and that immutable dataset.

No real regional holiday pack is seeded by this stage.

## Legal boundary

Federal non-working public holidays remain code-versioned under Article 112 TK
RF.

Additional non-working public holidays may be established by normative legal
acts of RF subjects. Religious holidays may also be declared non-working on
the relevant territory under Article 4(7) of Federal Law 125-FZ.

Current reference set:

- TK RF Article 112:
  https://www.consultant.ru/document/cons_doc_LAW_34683/98ef2900507766e70ff29c0b9d8e2353ea80a1cf/
- ConsultantPlus regional non-working holiday reference:
  https://www.consultant.ru/document/cons_doc_LAW_311098/
- Federal Law 125-FZ Article 4(7):
  https://www.consultant.ru/document/cons_doc_LAW_16218/ca78a0f4594e9666e8259f2b87a4df2e59a38cb4/

## Source-pack contract

Schema:

`DUTYLOG_REGIONAL_STATUTORY_HOLIDAY_SOURCE_PACK_V1`

Legal identity:

`NON_WORKING_PUBLIC_HOLIDAY`

A source pack contains:

- jurisdiction and region;
- exact coverage window;
- legal regime;
- legal basis;
- source revision;
- source reference;
- completeness flag;
- completeness evidence;
- zero or more exact positive regional statutory-holiday facts.

A complete pack with zero holiday facts is valid. That is necessary to prove
a negative regional-holiday result for a region/window where a complete legal
review finds no additional holidays.

## Two fingerprints

P1B2C2 deliberately keeps two different identities:

1. `fingerprint`
   - semantic dataset SHA-256 from normalized legal facts;
   - already introduced in P1B2C1.

2. `source_pack_sha256`
   - SHA-256 of the exact reviewed source-pack bytes;
   - supplied as an expected pin by the trusted caller;
   - verified before parsing;
   - frozen into the dataset provenance.

Formatting-only changes therefore do not silently replace an already imported
semantic dataset with a different source artifact.

## Strict parsing

The importer rejects:

- wrong schema;
- wrong legal identity;
- malformed JSON;
- duplicate JSON object keys;
- unknown top-level fields;
- unknown holiday fields;
- malformed dates;
- missing completeness evidence;
- SHA-256 mismatch;
- oversized packs;
- unsupported jurisdiction/region through the dataset authority.

Transferred rest days, employee rest days, commemorative days and professional
holidays are not representable as this legal identity.

## Historical safety

V81 adds source-pack provenance to the immutable dataset manifest:

- source pack schema;
- exact source-pack SHA-256;
- completeness evidence.

P1B2C1 rows without source-pack provenance fail closed after P1B2C2.
This is intentional trust hardening. P1B2C1 exposed no HTTP import and seeded
no regional data, so no automatic backfill is invented.

## Mutation boundary

`RegionalStatutoryHolidaySourcePackService.installTrusted(...)` is an internal
application-service boundary.

P1B2C2 adds:

- no HTTP endpoint;
- no admin UI;
- no automatic web fetch;
- no regional legal-data seed.

P1B2C3 will add actual reviewed/pinned regional source packs.

## Payroll boundary

Still unchanged:

- `HOLIDAY_PAY` is NOT enabled;
- PayrollService is NOT wired to statutory holiday authority;
- AnnualPaidVacationHolidayPolicy is NOT changed;
- ProductionCalendar and LOCAL_OVERRIDE are NOT legal authority.
