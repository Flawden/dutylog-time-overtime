# Monthly holiday-pay source entry

The Payroll workspace has an expandable source-review form for the selected month.
It loads on demand and uses the authenticated owner's Payroll module permission.
GET/PUT `/api/v1/payroll/periods/{month}/holiday-review` (legacy `/api/payroll` alias)
never accept an owner identifier. Responses are not cached.

The form records an explicit review of the entire pay system, including an empty
component set. Every enabled component needs INCLUDE/EXCLUDE and documentary
classification grounds. Existing classification authorities are immutable:
corrections need a new component version. Each INCLUDE needs a source-backed
amount, period (hour = exactly 60 minutes, or payroll month with reviewed minutes)
and separate **additional** percentages for enhanced pay and another rest day.
100% is 10000 BPS of additional pay, not a total multiplier. Display names and
configured percentages never supply a missing monetary formula.

When a local HOLIDAY rule exists, its document kind, reference and revision must
also be recorded. Existing local authorities are immutable; changing their source
requires a new pricing version. The source service stores no classification basis
for that local authority, so the form does not invent or request one.

The user separately records the documented applicability of the local tariff when
another rest day is chosen. This is a pay-system rule; it is **not** the employee's
election for a particular work event. Event-specific rest-day election, reviewed
monthly norm positions, holiday qualification and source packs remain independent
requirements of the existing Article153 chain. Saving this form does not certify
legal truth, make every work event payable, close a month or recalculate Payroll.
The displayed ready state is the remuneration source resolver at month start;
a different intramonth policy may still block work on a later date.

A configuration token binds owner, month, effective component inventory including
disabled versions, classification sources, compensation, local holiday policy and
review revision. The owner is locked and the whole write runs in REPEATABLE_READ.
Classification, local-source and remuneration writes roll back together if the
complete rule set fails. A stale token/revision fails before writes. A monetary
review correction appends a new revision; frozen Payroll history is not rewritten.
Unexpected persisted-document corruption retains server-error semantics.

The public input decoder rejects unknown/missing fields, duplicate JSON keys,
trailing content, numeric strings and fractional integers. Null optional local
source is allowed. Currency amounts and percentage inputs in Vue use exact
hundredths, accepting decimal comma or point without rounding extra decimals.
Errors retain entered form data; reloading deliberately replaces it with saved
sources. Changing month prevents an earlier response from replacing the new form.

## Validation

Added eight real-H2 workspace scenarios: explicit empty review, complete component
review, component/local-source atomic rollback, stale settings, owner isolation, immutable classification
and append-only review history. Added strict HTTP input-decoder regression and
five decimal-input tests. Added two browser presentation scenarios exercising the
form's payload and stale-conflict behavior; browser fixtures do not substitute for
the real-domain transaction tests.

Before committing, run canonical Node 20.18.1 / npm 10.8.2 frontend delivery/build,
unit tests, Maven clean verify and `npx playwright test payroll-holiday-review.spec.js`.
The existing Article153PayrollScenarioTest remains the broader domain/HTTP/history
regression. Clean PostgreSQL V90 and exact-commit deployment evidence are separate
acceptance gates; no migration is added by this form.
