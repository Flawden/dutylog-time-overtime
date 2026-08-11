# v27.40.5 — Selected-Day Strict Type Contract Alignment Hotfix

## Failure evidence

Local Maven verification of v27.40.4 executed all 758 JUnit tests and produced exactly one failure: `VueCalendarTimelineStrictTypecheckHotfixTest.workspaceBridgeCallbackCarriesExplicitGeneratedDomainTypes`. The v27.37.1 historical source contract still required the literal import `CalendarMode, DutyLogCalendarTimelineDomain`, while v27.40.4 correctly added `CalendarDaySection` because the newly native selected-day domain exposes `openDay(date, section)`.

This was a stale static/source contract, not a product regression. The workspace retained the explicit `openDate(date: string, mode?: CalendarMode)` signature and strict compiler configuration.

## Fix

The JUnit source contract now requires:

- `CalendarDaySection`, `CalendarMode` and `DutyLogCalendarTimelineDomain` in the workspace type import;
- the existing explicit `openDate(date: string, mode?: CalendarMode)` callback;
- the new explicit `openDay(date: string, section?: CalendarDaySection | null)` callback;
- strict TypeScript compiler configuration without `any`, unsafe non-null assertions or relaxed flags.

No Vue product behavior, offline mutation ownership, HTTP/OpenAPI shape, Flyway migration, business rule, retry, timeout or runtime/browser collector is changed by this hotfix.

## Acceptance surface

- Java test classes: 153
- JUnit `@Test`: 758
- Playwright Chromium scenarios: 48
- Vitest: 52
- OpenAPI: 118 operations / 120 schemas (`91b48b10fa56`)
- Flyway: V47

Exact frontend Node 20.18.1/npm 10.8.2, Maven 758/758, Playwright canary/full, immutable image and PostgreSQL staging smoke remain mandatory before v27.40.5 is accepted.
