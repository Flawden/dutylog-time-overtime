# Native Workday / Day Truth Integration — v27.45.1

## Product intent

> **Пользователь описывает реальность. DutyLog сам реализует последствия.**
>
> The user describes reality. DutyLog derives the consequences.

v27.45.1 is the first architectural integration release built around that product north star. It does not delete the existing Overtime, Time Bank, Production Calendar or Payroll domains; it stops requiring the user to think in those domains for the normal act of describing one workday.

## Day Truth read model

`GET /api/v1/workdays/{date}` returns one owner-scoped read model containing:

- dated generated/current shift identity and schedule times;
- base schedule norm;
- Production Calendar required norm and effective special-day rule;
- explicit Actual Work state and intervals;
- absence minutes already projected by Time Compensation;
- existing Overtime earned/used movements;
- the existing fact label.

`WorkdayTruthService` stores nothing. Existing domain services remain authoritative.

## Canonical required norm

Production Calendar required minutes now feed Time Compensation and the Payroll source projection. This closes the important semantic gap discovered in v27.45.0:

```text
base shift norm          8 h
shortened-day obligation 7 h
full-day time off        7 h coverage
```

A holiday with zero required minutes likewise does not need an absence merely to satisfy a non-existent work obligation.

## Native selected-day surface

Calendar selected-day details now expose a compact Day Truth card. The selected date is already known. From that context the user can:

- open the existing shift editor;
- record explicit factual work through the existing Actual Work API;
- open the existing absence composer with the date prefilled;
- create/update/delete the Production Calendar local rule for the date.

Production Calendar rules are marked in Month, Week and Day Calendar views.

## Payroll boundary

Payroll keeps the monthly explanation:

```text
Base schedule norm
Calendar adjustment
Production norm
```

but normal Production Calendar mutation is removed from Payroll. This prevents two competing everyday editors for the same real-world date.

## Explicit factual work and Overtime boundary

Explicit Actual Work becomes directly reachable from the workday, but v27.45.1 does **not** automatically post an Overtime credit. Existing users may already have manual credits and the current credit model has its own exact interval/FIFO semantics. The Day Truth surface therefore exposes fact-vs-obligation deltas and clearly marks an unposted positive delta instead of silently double-crediting it.

The next integration stage owns deterministic candidate derivation, duplicate detection/reconciliation and one-time posting into the existing Overtime authority.

## Norm effect versus schedule time

A shortened day may establish a 7-hour required norm without specifying whether the actual shift ends earlier, starts later or is otherwise adjusted. Schedule-time override remains a separate truth. Future per-date work-time UX may coordinate both from one user action while keeping their persistence semantics separate.

## Acceptance baseline

- OpenAPI: 130 operations / 136 schemas, `08589423f031`.
- Flyway: V49.
- Java source inventory: 799 `@Test` methods / 166 test classes.
- Playwright: 48.
- Vitest: 73.
- Browser budget: canonical Node 20.18.1/npm 10.8.2 measured **881901 B raw** after delivery/OpenAPI/typecheck/Vitest/Vite passed; total raw is narrowly rebaselined **875000 → 890000 B**. Total gzip stays **250000 B** and entry/per-chunk ceilings remain unchanged.
