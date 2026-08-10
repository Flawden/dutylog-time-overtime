# DutyLog v27.38.8 — Vue Shared Browser Parity Hotfix

## Evidence

The v27.38.7 full Playwright run starts normally, keeps the `auth-onboarding` canary green and completes with 22 passed / 25 failed. The failures are not 25 independent product defects; they collapse into four shared browser-parity root causes.

## Root causes and fixes

1. **Calendar selection helper (18 scenarios).** The shared `selectDate()` still expected legacy click-to-toggle behavior. Vue Calendar owns `focusDate`: clicking an already-focused date is idempotent and reopens the selected-day compatibility island. The helper now follows that public behavior and never expects `sel` to disappear.
2. **Productivity mutation snapshots (5 scenarios).** `structuredClone(this.taskDraft)` and `structuredClone(this.importantDraft)` ran on Pinia reactive proxies before `try`, throwing before any generated API POST and leaving mutation state stuck. Explicit plain snapshots now copy scalar fields plus nested subtasks/reminders safely.
3. **Task deadline presentation (1 scenario).** Task Board rendered schedule date/range but omitted the authoritative projected deadline time. Board metadata now includes `dueDate` and `dueTime` without recalculating timezone semantics in Vue.
4. **PWA first claim (1 scenario).** A first service-worker `controllerchange` could reload the application during onboarding. The boot path records whether the page was already controlled at load; first claim does not reload, while a later update of an already-controlled page retains the existing one-shot reload.

## Boundaries preserved

- Spring Boot remains source of truth for task/deadline/timezone/business rules.
- Generated OpenAPI transport remains the only Vue online API path.
- No Playwright timeout, retry, locator strictness, console/pageerror or HTTP-error policy is weakened.
- No PostgreSQL/Flyway change.
- Baseline remains 152 Java test classes / 751 JUnit `@Test` / 47 Playwright / 49 Vitest / Flyway V47.
- OpenAPI remains 101 operations / 106 schemas / `c48bfab2bcaf`.

## Acceptance

Required before release acceptance: exact Node 20.18.1/npm 10.8.2 frontend gate, Maven 751/751, `npm run test:e2e:canary`, full 47/47 Chromium, immutable image, clean PostgreSQL smoke and staging.
