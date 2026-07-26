# DutyLog v27.9.2 — Overtime Ledger Integrity Hotfix

## Problem

A time-off usage may consume several source credits. Deleting that usage must remove all of its allocation parts, but must never delete the source credits or another usage. A browser formatter failure must also never leave a partially rendered ledger that looks like records disappeared.

## Backend contract

- Build the complete FIFO replacement plan in memory before deleting stored allocations.
- Persist the replacement only after the plan fully satisfies all surviving usages.
- After persistence verify the exact credit ID set, surviving usage ID set, requested minutes per usage and maximum minutes per credit.
- Deleting a split usage removes the whole usage and all of its allocation parts, then deterministically rebuilds later usages.
- The transaction rolls back on any planning, persistence or invariant failure.

## Frontend contract

- Build ledger rows in a detached `DocumentFragment` and commit them with one `replaceChildren` call.
- Allocation formatting errors degrade to a warning on one interval instead of aborting the table.
- A split usage displays `часть N/M` in each source row.
- Destructive controls say `удалить весь отгул`, because the action operates on the usage, not on one visible allocation part.
- Confirmation explains that credits remain and minutes return to their balance.

## Acceptance scenario

1. Create a 3-hour credit and a 5-hour credit.
2. Create a 4-hour time-off; it must split across both credits.
3. Create a second 3-hour time-off.
4. Delete the first time-off.
5. Both credits must remain.
6. The second time-off must remain and move to the oldest available minutes.
7. Balance must equal earned minutes minus the surviving usage.
8. The ledger must render all rows without a partial-table state.

## Database

No migration. Flyway remains continuous through V31.
