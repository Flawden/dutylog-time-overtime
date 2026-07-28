# DutyLog v27.16.3 — Time Settings Transaction Hotfix

## Failure

The browser suite reached 23 passed / 1 failed. The remaining scenario entered `08:30`, clicked the explicit built-in shift apply button, but the form returned to `10:30`.

The profile `PUT` response was not the end of the timezone transaction: calendar, task, ledger and notification refreshes continued asynchronously and called `renderTimeSettings()` again. At the same time, a debounced built-in update could already be in flight. Cancelling only the pending timer therefore did not protect the user's current form values.

## Contract

- A shift-template edit increments a local revision immediately on `input`.
- While that revision is uncommitted, settings renders preserve the current form draft.
- Debounced and manual built-in updates share a serial promise queue.
- Newer captured input invalidates stale queued work.
- An older in-flight request may finish on the server, but it cannot repaint the UI; the newer queued operation applies last.
- Only the exact revision captured by a successful latest operation is marked committed.

## Scope

Frontend transaction handling only. No database migration and no backend API changes. Flyway remains V36.
