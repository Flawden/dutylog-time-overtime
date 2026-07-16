# Calendar cache/render hotfix

Status: v27.2.4.

## Symptom

After pressing **Fill** the server stored the generated schedule, but the calendar could keep showing the older IndexedDB snapshot. In practice only the previously selected day and reminder markers appeared, while the rest of the freshly generated shifts became visible only after a later unrelated render.

A snapshot from another month could also be painted briefly while navigating between months.

## Root cause

`dataLayer.loadCalendar(...)` can deliver two bundles: a cached snapshot first and the authoritative network response second. `loadMonth()` applied both bundles to application state, but did not render after the network bundle. The screen therefore remained on the cached representation even though `state.days` already contained the server data.

The snapshot path also did not verify that the stored `y` and `m` matched the requested month.

## Fix

- every accepted calendar bundle now triggers `renderNotifications()` and `renderCalendar()`;
- the loading skeleton is cleared before rendering an accepted bundle;
- a cached snapshot is used only when its year and month exactly match the request;
- the generation guard still rejects late responses for a month that is no longer active;
- the error/finally path removes a stuck skeleton even when no bundle was applied.
