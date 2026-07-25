# DutyLog v27.7.1 — Task & Ledger Layout Hotfix

## Goal

Remove two confirmed desktop regressions without changing Time Foundation or overtime accounting semantics.

## Task-card contract

- The day-task card uses three columns: checkbox, flexible content, delete action.
- The delete action remains in the top-right corner for short and long titles.
- Inline subtasks start under the task content and never receive the old mobile left offset.
- No task or subtask data contract changes are introduced.

## Overtime-ledger contract

- Credit-level actions live in a dedicated `Действия / Actions` column.
- FIFO usage actions remain attached to the usage they modify, but their labels explicitly say `ред. списание` and `удалить списание`.
- Deleting a credit still requires its usages to be removed first.
- No database, FIFO, balance or API behaviour changes are introduced.

## Non-goals

- Shift projection into display timezone.
- Overtime interval storage or minute-level FIFO.
- Mobile card redesign.
- Any Flyway migration.

## Manual acceptance

1. Open a day with a task that has subtasks and a long title.
2. Confirm checkbox left, title/body centred and delete `×` pinned right.
3. Expand subtasks at desktop and 320–430 px widths; confirm no horizontal overflow.
4. Open the overtime ledger and confirm the final header says `Действия`.
5. Confirm credit edit/delete are in the final column.
6. Confirm usage controls inside `Куда списано` explicitly say they edit/delete a usage.
