# v27.40.18 — Calendar ICS Release Version Contract Hotfix

## Evidence

The v27.40.17 staging run passed the exact frontend gate and failed in `mvn verify` before release-check or Playwright. Repository inspection found a deterministic version drift: `CalendarSyncControllerTest` expected the v27.40.17 ICS PRODID while `CalendarIcsService` still emitted v27.40.16.

## Fix

- Promote the runtime Calendar ICS `PRODID` to the v27.40.18 release version.
- Add `CalendarIcsReleaseVersionContractTest`, which derives the DutyLog project version from `pom.xml` and requires the Java ICS generator to embed that same version.
- Carry the v27.40.17 Vue route commit, route guard and legacy hash-listener retirement runtime forward unchanged.

## Non-goals

No route behavior, API shape, database migration, offline queue, retry, timeout or browser expectation is relaxed.
