# v27.40.24 — Final Legacy Ownership Audit & Dead UI Surface Retirement

## Purpose

Prove the post-v27.40.23 ownership boundary against the actual source tree and retire dead UI surfaces without expanding the migration into product redesign or business-rule changes.

## Ownership result

After Vue readiness there are **zero legacy-owned user screens**. Vue owns shell/navigation, Today, Calendar/selected day, Absence & Time Bank, Productivity, Settings/Workspace, Payroll and Admin.

The audit distinguishes old code from UI debt:

- `dataLayer` remains the single offline mutation/sync owner and is infrastructure, not a legacy screen.
- limited pre-Vue recovery remains allowed until the Vue shell reports readiness;
- known live legacy presentation is limited to first-run onboarding and offline/sync UX;
- no post-Vue legacy route adapter is reintroduced.

## Dead surface retirement

- `nextTopbar` and `tabbar` remain server fallback markup before Vue readiness, then are physically removed by `shell-bootstrap.js` after successful Vue shell readiness.
- Settings ownership retirement also removes `legacyShiftModal` and `legacyTaskDeadlineModal` from the live DOM.
- Absence/Time Bank ownership retirement also removes `legacyOvertimeModal` and `legacyUsageMigrationModal` from the live DOM.
- The old CSS-only post-ready hiding contract for duplicate shell chrome is removed.

## Parity boundary

Shift/task legacy migration already has native Vue Settings ownership. Legacy overtime/usage migration API/data semantics are deliberately preserved; only orphan post-ready fallback DOM is retired. Native Vue access to any still-required overtime/usage migration flow is tracked for the Functional Parity Sweep before v27.40.x closes.

## Non-goals

No API/OpenAPI shape, Flyway migration, authentication/authorization, business calculation, retry/timeout policy or offline queue semantics change in this release.

## Acceptance surface

- Java test classes: 160
- JUnit `@Test` methods: 775
- Chromium Playwright scenarios: 48
- Vitest cases: 60
- OpenAPI: 124 operations / 130 schemas
- Flyway: V47

Exact Node 20.18.1/npm 10.8.2 frontend gate, Java 17 Maven, canary/full Chromium, immutable image, PostgreSQL migration smoke and staging deploy remain mandatory before the release is proven green.
