# v27.38.4 — Vue Productivity Ownership & Boot Browser Parity Hotfix

## Why this release exists

The v27.38.3 exact frontend and Maven gates progressed far enough to expose a browser-wide migration boundary problem. A source audit showed that the remaining failures were not one isolated selector: several legacy callbacks could still touch DOM retired by Vue, domain readiness markers could become visible before typed commands existed, Calendar projection lost some mature month-cell parity, and local E2E could be started without a built Vue bundle.

## Runtime corrections

- Install Calendar and Productivity typed domains before their legacy owners are retired.
- Yield legacy Notes/Tasks/Important and Calendar experience/layer renderers after Vue ownership is ready.
- Keep selected-day `#panel` as the only compatibility island; Quick Note navigates to the canonical date, opens the panel and expands the Vue Notes section through a named bridge command.
- Preserve Quick Task/Important payloads instead of writing into removed legacy editor nodes.
- Load backend `workDate` and `workTimezone` before Productivity reports ready; failed reads do not produce false-green readiness.
- Reuse the existing dataLayer snapshot for offline Calendar reads; no second offline store or business authority is introduced.
- Render backend-projected full/partial absence facts in Vue Month, stop legacy layer/mode renderers from duplicating Vue markup, and close the selected-day panel before mobile period navigation.
- Place shifts by display interval rather than source date, treat a next-day `00:00` end as exclusive, and carry timed task end date/time across midnight.

## Delivery and acceptance corrections

- `deploy/scripts/frontend-gate.ps1` mirrors the pinned frontend gate natively on Windows.
- `verify-delivery-foundation.mjs` resolves npm portably on Windows.
- Playwright `pretest:e2e` refuses to start without the built Vue JS/CSS assets.
- CI/staging run `auth-onboarding` as a fail-fast browser canary before the complete suite.
- The PWA upgrade fixture seeds its synthetic previous cache before the current service worker is allowed to register.

## Non-goals

This hotfix does not change backend business rules, OpenAPI shape, PostgreSQL/Flyway, FIFO/absence ownership, browser timeouts, Playwright retry policy or strict console/page-error collection. Full 47/47 Chromium remains required before acceptance.

## Locked baseline

- Java test classes: 152
- `@Test` methods: 751
- Playwright scenarios: 47
- Vitest cases: 49
- OpenAPI: 101 operations / 106 schemas
- Flyway: V47
