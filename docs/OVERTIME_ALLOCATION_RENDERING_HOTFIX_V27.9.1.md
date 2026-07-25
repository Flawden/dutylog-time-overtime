# DutyLog v27.9.1 — Overtime Allocation Rendering Hotfix

## Problem

After creating an exact overtime usage whose FIFO allocation crossed midnight, the selected-day panel called `allocationRangeLabels()`. That function referenced a nonexistent global helper named `formatDate`, which raised:

```text
ReferenceError: formatDate is not defined
```

The exception aborted the rest of the calendar/day-details render. The click itself could update internal state, but the UI retained the previously highlighted shift and made the newly selected day appear unselectable.

## Fix

`allocationRangeLabels()` now uses the existing, hoisted helper:

```text
formatDateHuman(yyyy-MM-dd)
```

The exact interval remains split at calendar midnight:

```text
30.04.2026 17:00–24:00
01.05.2026 00:00–01:00
```

No backend, database or FIFO semantics changed.

## Regression protection

- Java frontend contract forbids `formatDate(startDate/endDate)` and requires `formatDateHuman`.
- The overtime editor Playwright scenario creates an eight-hour overnight credit, consumes all eight hours and verifies both split labels.
- The shared Playwright fixture treats any page error as a failure.
- The release gate executes the real `allocationRangeLabels()` function in a Node VM before packaging.

## Acceptance

1. Create or migrate an exact overtime interval crossing midnight.
2. Consume the interval with a usage/time-off entry.
3. Select another calendar day and then return to the usage day.
4. The selected cell and right-side shift details must follow the click.
5. The usage details must show both midnight-split ranges.
6. DevTools must contain no `formatDate is not defined` error.

## Non-goals

- No Flyway migration; schema remains V31.
- No changes to FIFO ordering, minute allocation, balances or legacy migration.
- No redesign of calendar selection or shift cards.
