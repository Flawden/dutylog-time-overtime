# Production Calendar Foundation — v27.45.0

## Purpose

Production Calendar is the authoritative layer that converts the **base schedule norm** into the **required production norm** for a month. It is deliberately separate from Absence, factual work and money calculation.

```text
BASE SCHEDULE NORM
        ↓
PRODUCTION CALENDAR
        ↓
PRODUCTION NORM
        ↓
ABSENCE / FACTUAL WORK
        ↓
FUTURE PAYROLL RULES
```

A non-working holiday therefore removes required norm; it is not recorded as an absence and does not reduce salary by itself.

## Independent effects

Every dated rule has two independent dimensions:

- `scheduleEffect`: `NONE` or `NORM_OVERRIDE` — changes the required norm for the date.
- `payrollEffect`: `NONE` or `HOLIDAY` — classifies work for future compensation logic.

Examples:

- non-working holiday: `HOLIDAY + NORM_OVERRIDE(0) + HOLIDAY`;
- holiday that remains a normal required workday: `HOLIDAY + NONE + HOLIDAY`;
- transferred workday on a base day off: `TRANSFERRED_WORKDAY + NORM_OVERRIDE(420) + NONE`.

No 8-hour day, multiplier or jurisdiction-specific legal rule is hard-coded by the foundation.

## Layering and provenance

`production_calendar_days` supports:

- `BASE` — reserved for future official/imported calendar material;
- `LOCAL_OVERRIDE` — owner-edited rule that wins for the same date.

Source provenance is retained through `source_type=CUSTOM|OFFICIAL|IMPORTED` and optional `source_ref`.

## API

- `GET /api/v1/production-calendar/months/{yyyy-MM}`
- `PUT /api/v1/production-calendar/days/{yyyy-MM-dd}`
- `DELETE /api/v1/production-calendar/days/{yyyy-MM-dd}`

All operations are authenticated/owner-scoped under the existing Shifts module. Mutations respect accounting-period locks.

## Monthly result

The month read model exposes:

- base norm;
- production norm;
- total adjustment;
- holiday reduction;
- shortened-day reduction;
- transferred-day adjustment;
- schedule coverage;
- effective rule and base→production minutes for every date.

## Payroll boundary

`PayrollPeriodDto` includes the Production Calendar month read model so the user can see the new norm next to existing payroll data. **v27.45.0 does not change the existing money formula.** Payroll Core will consume the production norm explicitly in the next phase rather than silently changing historical v27.28 behavior inside this foundation release.

## Acceptance baseline

- OpenAPI: 129 operations / 135 schemas, `6e23a5b4b53f`.
- Flyway: V49.
- Java source inventory: 797 `@Test` methods / 166 test classes.
- Playwright: 48.
- Vitest: 73.
- Browser budget: canonical Node 20.18.1/npm 10.8.2 measured **869227 B raw**; total raw is narrowly rebaselined to **875000 B**. Total gzip stays **250000 B** and entry/per-chunk ceilings remain unchanged.
