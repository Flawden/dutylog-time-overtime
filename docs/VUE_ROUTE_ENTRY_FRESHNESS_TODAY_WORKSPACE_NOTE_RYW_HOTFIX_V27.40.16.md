# DutyLog v27.40.16 — Vue Route-Entry Freshness, Today Workspace & Note Read-Your-Write Hotfix

## Evidence

The v27.40.15 staging Playwright report contains 48 scenarios: 43 clean passes, one retry-only multiple-notes failure and five final failures. Network traces show the Overtime/Vacation failures reading the account before test mutations and not issuing a fresh generated account read on route entry after the v27.40.14 router narrowing. The Today failure shows workspace visibility/order still depended on legacy DOM side effects. The retry-only notes trace shows the second note PATCH carrying the default title after a post-create selected-day reload raced the first editor keystrokes.

## Fix

The note path now has explicit **read-your-write** semantics.

- `AbsenceTimeBankWorkspace` calls `refresh()` on Overtime/Vacation entry, restoring freshness in the Vue owner rather than restoring legacy `applyRoute()` rendering.
- `CalendarTimelineWorkspace` treats Today as a dashboard freshness boundary and performs `refresh(true)` on entry.
- `TodayPage` renders the actual widget list from the persisted workspace definition and authoritative module map, so both DOM order and visibility are Vue-owned.
- `productivityStore.createNote()` uses the returned create DTO as immediate selected-day state and does not issue the follow-up reload that can overwrite a live draft.

## Invariants

- Vue remains route-state and route-guard authority.
- Payroll/Admin remain the only post-Vue legacy route-entry side-effect boundary.
- `dataLayer` remains the sole offline mutation/reconnect queue owner.
- OpenAPI remains 118 operations / 120 schemas (`91b48b10fa56`).
- Flyway remains V47.
- Strict TypeScript, browser error collection, retries and timeouts are unchanged.

## Acceptance

Runtime acceptance requires exact Node 20.18.1/npm 10.8.2 frontend gate, Maven 764/764 on Java 17, Playwright canary plus full Chromium 48/48 with zero flaky retries, immutable image/PostgreSQL V47 smoke and green staging deploy.
