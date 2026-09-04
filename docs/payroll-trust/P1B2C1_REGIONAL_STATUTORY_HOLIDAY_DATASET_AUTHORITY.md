# P1B2C1 — Regional Statutory Holiday Dataset Authority Foundation

Reviewed legal boundary: 2026-09-04.

## Architecture decision

P1B2C discovery selects a HYBRID authority:

- Federal statutory public holidays remain code-versioned in `RuFederalStatutoryHolidayPolicy`.
- Regional statutory public-holiday truth uses separate immutable, source-versioned complete datasets.
- `WorkJurisdictionTerm` selects the employee's effective region on the date.
- `ProductionCalendarDay` is NOT legal authority.
- `LOCAL_OVERRIDE` is NEVER legal authority.

## Negative proof

A negative classification is allowed only when one and only one fingerprint-valid dataset covers the exact region/date and declares `complete=true`.
Missing, overlapping, incomplete or integrity-invalid authority fails closed.

## Historical rule

Dataset manifests and date facts are immutable. If multiple immutable datasets overlap the same region/date, resolution fails closed as AMBIGUOUS instead of silently selecting a newer legal truth.

## Import boundary

P1B2C1 adds only a trusted application-service installation boundary. It adds no HTTP endpoint, no admin UI and no regional data seed. P1B2C2 will source-lock and install concrete regional datasets.

## Payroll boundary

P1B2C1 does NOT enable HOLIDAY_PAY, calculate payroll money, alter payroll semantic freeze, alter AnnualPaidVacationHolidayPolicy or use ProductionCalendar as legal truth.

## Current legal references

Article 112 TK RF:
https://www.consultant.ru/document/cons_doc_LAW_34683/98ef2900507766e70ff29c0b9d8e2353ea80a1cf/

Federal Law 125-FZ, Article 4(7):
https://www.consultant.ru/document/cons_doc_LAW_16218/ca78a0f4594e9666e8259f2b87a4df2e59a38cb4/

ConsultantPlus 2026 production-calendar commentary:
https://www.consultant.ru/law/ref/calendar/proizvodstvennye/
