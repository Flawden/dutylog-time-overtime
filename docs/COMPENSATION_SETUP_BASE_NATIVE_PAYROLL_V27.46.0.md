# DutyLog v27.46.0 — Compensation Setup & Base Native Payroll

## Product contract
Compensation terms use an **effective-month** history: a newer term never rewrites an older month.

The user describes compensation once; DutyLog prices the already-canonical Day Truth. Payroll never reinterprets Calendar storage.

- `HOURLY`: base amount = canonical payable minutes × configured hourly rate.
- `SALARY`: monthly salary is divided by Production Calendar norm only for the explainable hourly value; the base salary itself is prorated by required minutes actually covered by work or paid absence and is capped at the production norm.
- Work beyond a dated required norm does not inflate salary base. Holiday/night/overtime premiums are deliberately deferred to the next Native Pay Classification step.
- Compensation terms are effective by month. A newer term never rewrites older months.
- V45 `payroll_settings` remains a compatibility adapter; V52 migrates existing positive hourly rates into a `1970-01` HOURLY term.
- Closed calculations freeze pay mode, effective month, configured amount, production norm, salary coverage and effective hourly value in immutable snapshot provenance.

## Acceptance
1. Save `Оклад 80 000` effective from a selected month.
2. With production norm `167 ч` and full covered norm, base salary is exactly `80 000`, while effective hourly explanation is `80 000 / 167`.
3. Overtime above required minutes does not increase salary base before premium classification exists.
4. Paid absence fills salary coverage; uncovered required time prorates the base.
5. Changing a later compensation term leaves earlier months on their historical term.

## Historical safety and norm readiness
- The legacy `/payroll/settings` compatibility adapter creates a term for the user's current work-timezone month; it never rewrites the internal `1970-01` migration baseline.
- The native form always proposes the selected Payroll month as the effective month, even when its displayed values are inherited from an older term.
- SALARY preview/final calculation fails closed on incomplete schedule coverage; a partial Production Calendar denominator is never treated as a trustworthy salary norm.
