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

## Explicit factual work and closed-loop Overtime reconciliation

Staging smoke exposed two acceptance blockers: wall-clock Actual Work included the shift lunch, and a fact above the obligation did not affect the existing overtime bank. v27.45.1 closes both before acceptance.

The first explicit fact on a date inherits the dated shift's unpaid break by default; the user may override it. `workedMinutes = elapsed clock minutes - breakMinutes`, while overlap validation continues to use the real clock span. Additional fact intervals default to zero inherited break so one scheduled lunch is not silently subtracted twice.

For ordinary work, Day Truth deterministically reconciles `max(0, actual net minutes - required minutes)` into the existing FIFO Overtime authority. The projection is tagged `SYSTEM_ACTUAL_WORK` and is idempotent: editing the fact updates one credit, deleting an unused fact removes it, and shrinking/deleting a credit that has already been consumed fails closed with an actionable conflict. If a user already has a manual credit on that date, DutyLog does not silently create a second one. Holiday-classified work is deliberately excluded from ordinary Time Bank derivation and remains a separate compensation category.

The selected day owns direct `Удалить факт` and `Сбросить особый день` actions, so mistakes can be reversed where they were entered. The former diagnostic `Текущее отображение / Исходная смена` block is removed from the normal UI; shift mechanics remain available through the contextual shift action.

## Norm effect versus schedule time

A shortened day may establish a 7-hour required norm without specifying whether the actual shift ends earlier, starts later or is otherwise adjusted. Schedule-time override remains a separate truth. Future per-date work-time UX may coordinate both from one user action while keeping their persistence semantics separate.

## Cross-midnight fact and derived-credit ownership

A factual interval may now finish on another calendar date. The day surface keeps the start date from the opened context and reveals an optional end-date control only when needed; end-before-start without an explicit date keeps the convenient next-day inference. The backend stores `endDate` and limits the interval to 48 hours.

The source fact is not duplicated at midnight. Instead, `ActualWorkDayAllocationService` splits net minutes by calendar date for Day Truth, Time Compensation, Payroll source and Overtime reconciliation. Unpaid break is consumed from the earliest clock minutes, consistent with the existing calculated-overtime splitter. Derived `SYSTEM_ACTUAL_WORK` credits expose `sourceKind` and `editable=false`; Overtime shows them as automatic and routes back to the calendar day.

## Acceptance baseline

- OpenAPI: 130 operations / 136 schemas, `34d257319830`.
- Flyway: V50.
- Java source inventory: 808 `@Test` methods / 167 test classes.
- Playwright: 48.
- Vitest: 73.
- Browser budget: canonical Node 20.18.1/npm 10.8.2 measured **881901 B raw** after delivery/OpenAPI/typecheck/Vitest/Vite passed; total raw is narrowly rebaselined **875000 → 890000 B**. Total gzip stays **250000 B** and entry/per-chunk ceilings remain unchanged.
