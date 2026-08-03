# v27.32.0 — Absence & Time Bank Experience

## Product decision

DutyLog has one canonical absence event and several read projections:

- **Отпуск и отсутствия** owns creation, editing, cancellation, date, type, coverage and compensation source.
- **Переработки** explains the time bank: earned credits, posted usage, future reservations, free balance and FIFO allocation detail.
- **Calendar** places the same event in time without duplicating ownership.

A linked overtime usage is never a second editable time-off record.

## Time bank workspace

The Overtime workspace now exposes four explicit views:

1. **Обзор** — earned, posted, reserved, free and the next open FIFO credit.
2. **Начисления** — the historical credit ledger, filters, chart, exports and credit editor.
3. **Использование банка** — read-only absence-backed usages with posting state and allocation detail.
4. **FIFO-детализация** — open-credit queue and a forecast for a requested future duration.

The forecast consumes current open credits oldest-first and shows the remaining free balance or shortage before an absence is saved. When an existing absence is edited, its own allocations are temporarily restored to forecast capacity so the reservation cannot compete with itself.

## Absence experience

The absence journal now has upcoming/history scope, type, status and text filters. Overtime-backed absences link directly to their bank usage. The composer shows an inline FIFO forecast when `OVERTIME_BANK` is selected.

Future planned/submitted usages are displayed as **reserved**; approved/completed usages are **posted**. The free balance remains the amount available for another absence after both groups.

## Product education

This release starts the in-product education layer:

- contextual guide entry points in both workspaces;
- an explanation of event ownership versus bank detail;
- actionable empty states;
- a repeatable time-bank guide;
- the first end-to-end educational journey.

## Compatibility

No new public API and no database migration are introduced. Canonical absence ownership, linked usage protection, FIFO allocation, Payroll and closed-period rules remain unchanged. Flyway stays at V47.

## Release tooling

The historical release-check matcher coprocess is explicitly closed and awaited, so a successful gate terminates instead of waiting indefinitely for helper input.
