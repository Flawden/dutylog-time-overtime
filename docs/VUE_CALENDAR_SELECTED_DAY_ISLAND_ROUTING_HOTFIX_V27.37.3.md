# v27.37.3 — Vue Calendar Selected-Day Island Routing Hotfix

## Evidence
The persistent Playwright artifact from the timed-out v27.37.2 run contains the concrete browser exception:

```text
TypeError: Cannot read properties of null (reading 'classList')
    at selectDay (.../js/30-calendar.js?v=27.37.2)
    at selectDayWithExperience (...)
    at applyRoute (...)
    at loadProfile (...)
```

Fresh-user onboarding exists in the DOM but remains hidden because `loadProfile()` reaches `applyRoute()` before `maybeShowOnboarding()`. On the default Today route, `applyRoute()` calls `selectDay(null)`.

## Root cause
Vue Calendar/Timeline retirement preserves the mature selected-day editor as the one bounded `#calendarLegacyPanelHost > #panel` compatibility island, but removes the old Calendar read surface containing `#layout`. Legacy `selectDay()` still assumed that wrapper existed:

```js
$("layout").classList.toggle("with-panel", !!k);
```

That assumption is no longer valid after the Vue owner is ready.

## Fix
Only the retired wrapper becomes optional:

```js
$("layout")?.classList.toggle("with-panel", !!k);
```

The next line deliberately remains strict:

```js
$("panel").hidden = !k;
```

`#panel` is the explicit compatibility island. Silently making it optional would hide an architecture regression instead of detecting it.

## Regression and scope
The existing `VueCalendarTimelineMigrationFrontendContractTest` now requires the null-safe `#layout` access, forbids the old strict dereference and requires the strict `#panel` line. No new test method or browser scenario is added.

No API, generated OpenAPI contract, business rule, PostgreSQL schema, Flyway migration, npm dependency graph, retry policy, browser timeout or CI runner routing changes. Acceptance remains pending until all 47 Chromium scenarios and the complete immutable-image/staging path are green.
