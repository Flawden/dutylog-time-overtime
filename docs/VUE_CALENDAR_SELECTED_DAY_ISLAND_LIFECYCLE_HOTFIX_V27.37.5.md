# v27.37.5 — Vue Calendar Selected-Day Island Lifecycle Hotfix

## Evidence
The v27.37.4 self-hosted Chromium run completed the full 47-scenario suite in about 18 minutes instead of hitting the former 45-minute job timeout. Result: 28 passed, 19 failed. The earlier fresh-user onboarding blocker is therefore closed.

Multiple remaining failures contain the same browser exception. One exact stack is:

```text
TypeError: Cannot set properties of null (setting 'innerHTML')
  at renderChips (.../js/50-tasks.js?v=27.37.4:2046:17)
  at renderSelectedDayModules (.../js/20-data.js?v=27.37.4:317:3)
  at refreshModuleAwareData (.../js/20-data.js?v=27.37.4:413:23)
  at saveModuleEnabled (.../js/20-data.js?v=27.37.4:390:11)
```

The same `renderChips()` failure occurs from `saveTimeSettings()`, and related scenarios report `Cannot set properties of null (setting 'hidden')`.

## Root cause
`#chips` is not an independent optional widget. It is a mandatory descendant of the preserved legacy selected-day `#panel`. During Calendar migration, the legacy Calendar DOM is removed and `#panel` is preserved, then attached to the Vue `#calendarLegacyPanelHost`.

However, `CalendarTimelineWorkspace` conditionally renders `CalendarPage` with `v-else-if`. Leaving Calendar therefore unmounts `CalendarPage` and removes `#calendarLegacyPanelHost`. Because the legacy `#panel` had been physically appended into that host, the browser removes the entire externally-attached compatibility island with the Vue host. Later Settings/Tasks/module flows still legitimately call selected-day renderers and find `#chips`/other panel descendants missing.

## Fix
The public legacy bridge gains one explicit lifecycle operation:

```text
parkCalendarEditor()
```

`CalendarPage` calls it from `onBeforeUnmount()`. The adapter hides `#panel` and reparents it to `document.body` before Vue removes its host. On the next Calendar mount, the existing `attachCalendarEditor("calendarLegacyPanelHost")` path reparents that same panel back into Vue's host.

The fix intentionally does **not** make `#chips`, `#panel` or their selected-day descendants optional. Their disappearance is an ownership/lifecycle defect and must remain observable.

## Scope
No backend business logic, API/OpenAPI contract, database schema, Flyway migration, timeout, retry policy or browser assertion is changed. The known independent Calendar mode/timezone projection failures are not guessed at in this hotfix and remain for the next evidence-driven pass if they survive.
