# DutyLog v27.9.4 — Overtime Split Projection Contract Hotfix

## Problem

v27.9.3 fixed the preflight integrity regression, but two Playwright expectations still described the UI incorrectly. A calculated interval from `17:00` to `01:00` is intentionally split at midnight, so the selected start day contains `+7 h`, not the full `+8 h`; the full balance is still eight hours.

The split badge renderer also depended on `state.overtimeAccount.usages`. Direct API changes followed by a paged ledger load populated only credit rows, so `part 1/2` and `part 2/2` disappeared even though the backend allocations were correct.

## Contract

1. Keep midnight splitting as the authoritative per-day projection.
2. Test the selected-day segment separately from the account-wide balance.
3. Ledger usage references carry stable split-part metadata through `allocationPartIndex` and `allocationPartCount`.
4. Render split badges from the paged response first, with the full-account allocation list only as a compatibility fallback.
5. Deleting one split time-off must preserve every credit and every other usage.

## Non-goals

- No change to FIFO ordering.
- No change to credit totals or midnight splitting.
- No database migration.
- No new user-facing destructive action.

## Acceptance

- `17:00–01:00` renders `+7 h` on the start day and `+8 h` in the account balance.
- A ledger loaded only through `/api/overtime/account-page` shows `part 1/2` and `part 2/2`.
- Split metadata remains stable after account reload.
- The existing delete-one-split-usage scenario reaches the deletion step and verifies surviving data.
- Flyway remains continuous through V31.
