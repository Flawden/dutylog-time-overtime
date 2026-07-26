# DutyLog v27.9.3 — Overtime Preflight Integrity Hotfix

## Problem

v27.9.2 correctly planned FIFO replacements before destructive allocation writes, but `createUsage` inserted the new usage before the capacity check performed by the rebuild. In a normal web request Spring rolls the transaction back; however, a wider caller transaction that catches the domain exception could still observe the managed row before the outer rollback. The regression test exposed this as two usages instead of one.

A historical frontend contract also still searched for `удалить списание`, although v27.9.2 intentionally renamed the destructive action to `удалить весь отгул`.

## Contract

1. Compute available credited minutes and total requested usage minutes before inserting or mutating a usage.
2. Exclude the edited usage from the existing total and add the proposed replacement minutes.
3. Reject over-capacity commands before any managed entity state changes.
4. Keep the full in-memory FIFO planning and post-persistence integrity verification as the second defence.
5. Preserve the whole-time-off deletion wording introduced by v27.9.2.
6. Make browser test deductions explicit (`break=0`, `planned=0`).

## Non-goals

- No schema change.
- No FIFO ordering change.
- No balance migration.
- No change to the meaning of a split time-off.

## Acceptance

- A failed create leaves the previous usage count unchanged.
- A failed update leaves the original usage fields and allocation unchanged.
- Maven frontend contracts accept `удалить весь отгул`.
- The overnight E2E credit deterministically produces eight hours.
- Flyway remains continuous through V31.
