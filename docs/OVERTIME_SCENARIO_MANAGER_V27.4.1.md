# v27.4.1 — Overtime scenario manager

This release introduces a **single-window scenario manager** inside the shared overtime credit editor.

## User flow

- The credit editor keeps a compact scenario dropdown.
- The gear button or the final dropdown item opens scenario management in the same modal.
- “Save current values as a scenario” converts the current shift-anchored interval, break, plan and reason into a prefilled scenario draft.
- Creating or editing a scenario never opens a second modal over the first one.
- Returning to the overtime form preserves the date, interval, break, plan, hours and reason already entered.

## Settings cleanup

The obsolete quick-scenario card and navigation item were removed from Settings. Scenario data and API endpoints are unchanged, so existing scenarios continue to work without migration.

## Draft conversion limits

The current persistence model anchors scenario start to either shift start or shift end. Therefore, automatic conversion from a filled credit form is offered when the form start matches one of those anchors. Arbitrary manual starts can still be represented by creating a scenario directly in the manager.

## Regression coverage

- Java frontend contracts verify the Settings cleanup and single-modal DOM contract.
- Playwright verifies create and edit operations through the real quick-scenario API and confirms that the original credit form is restored.
