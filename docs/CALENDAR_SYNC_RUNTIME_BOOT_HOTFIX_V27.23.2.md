# v27.23.2 — Calendar Sync Runtime Boot Hotfix

## Incident

The `v27.23.1` Maven gate completed successfully, then the browser gate reported one passing scenario and thirty-one failures. The failures shared one browser-runtime issue rather than thirty-one independent product regressions:

```text
ReferenceError: localDateKey is not defined
    at calendarSyncDefaultRange (js/55-calendar-sync.js)
    at initCalendarSyncRange (js/55-calendar-sync.js)
```

`55-calendar-sync.js` initialized the default `.ics` export range at script load time and called a helper name that does not exist in the DutyLog browser bundle. The common Playwright fixture records every uncaught page error, so every scenario using that fixture failed at teardown. The offline PWA scenario uses a separate raw Playwright fixture and was the only scenario not protected by that shared runtime-error assertion.

## Fix

The range initializer now uses DutyLog's canonical browser date-key helper that is defined by `10-core.js` and loaded before `55-calendar-sync.js`:

```javascript
return {
  from:keyOf(start.getFullYear(), start.getMonth(), start.getDate()),
  to:keyOf(end.getFullYear(), end.getMonth(), end.getDate())
};
```

The hotfix intentionally does not use `Date#toISOString()` because the export controls represent floating local calendar dates; converting through UTC can move the date near timezone boundaries.

## Regression protection

- `CalendarSyncFrontendContractTest` requires both range boundaries to use `keyOf(...)`.
- The contract explicitly rejects `localDateKey(` in the calendar-sync bundle.
- `release-check.sh` enforces the same source-level guard before packaging.
- The existing shared Playwright fixture continues to fail on uncaught page errors, console errors and unexpected same-origin HTTP failures.

## Compatibility

- No HTTP API changes.
- No OpenAPI changes.
- No database changes; Flyway remains V41.
- No subscription token, `.ics`, nginx or security-contract changes.
- Regression baseline: 107 Java test classes / 564 `@Test` methods / 32 Playwright scenarios.
