# v27.4.0 — Unified overtime editors

DutyLog now exposes one overtime-credit editor and one time-off-usage editor from both the selected calendar day and the overtime ledger.

## Calendar

The selected-day panel keeps only compact **Earn** and **Use** actions plus the entries already recorded for that date. Opening an editor from the calendar pre-fills the selected date.

## Ledger

The overtime ledger exposes **Add overtime** and **Add time off** actions. Editing a credit or usage opens the same editor directly and no longer changes the current calendar month.

## Credit editor

The shared credit editor supports date, quick-scenario selection, short or full intervals, break, planned hours, calculated/manual hours and reason. Existing scenarios are selected from a dropdown; scenario management remains in Settings until v27.4.1.

## Usage editor

The shared usage editor supports date, hours and reason, and previews the available balance before and after the change. Updating an existing usage accounts for its original hours before calculating the preview.

## Mobile behavior

Editors reuse the application modal system, become bottom/full-height sheets on narrow screens and hide the bottom tab bar while open.
