# v27.32.1 — Time Bank Absence Navigation Hotfix

## Failure reproduced

GitHub Actions completed the Maven suite and reached the new `absence-time-bank-experience.spec.js` browser journey. The Bank Usage card rendered the linked absence and the `Открыть отсутствие` action, but clicking it only changed the route to `#vacation`. The existing `editAbsence(...)` helper populated the inline editor and never mounted/opened `#absenceComposerModal`, so Playwright correctly observed the modal as hidden.

## Correct ownership transition

`openAbsenceEditor(...)` is now the single cross-workspace editor boundary. It:

1. resolves the `sourceAbsenceId` from the authenticated planner read model;
2. refreshes the planner when the record was created after the last load;
3. refreshes the Overtime ledger so FIFO uses current credit remainders;
4. populates the existing Unified Absence Composer without triggering inline scrolling;
5. mounts the shared form in `absenceComposerModal` and opens it;
6. requests the edit-aware preview with `excludePeriodId`, so the reservation does not compete with itself.

Inline editing inside «Отпуск и отсутствия» remains unchanged. Linked usages remain read-only in Overtime and still have no independent edit/delete controls.

## Compatibility

- No API change.
- No PostgreSQL or Flyway change; latest migration remains V47.
- No Payroll or FIFO ownership change.
- One Spring Boot application image/container remains the production topology.

## Next architecture stage

After the green v27.32.x baseline, DutyLog pauses new product features and performs the approved complete frontend transition to Vue 3 + TypeScript + Vite. The migration remains in the same repository, release, JAR/image and application container; PostgreSQL stays separate.
