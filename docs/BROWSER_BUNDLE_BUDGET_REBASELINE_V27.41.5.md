# Browser Bundle Budget Rebaseline — v27.41.5

## Trigger

The exact v27.41.4 frontend gate completed the authentic Node 20.18.1 / npm 10.8.2 install and delivery verification, OpenAPI 124/130 drift check, strict `vue-tsc`, all 64 Vitest cases and the production Vite build. `audit:bundle` then measured `dist/dutylog-vue-app-shell.js` at **806839 B raw** (about **195.76 kB gzip**) and failed the historical **800000 B raw** ceiling.

## Decision

- Rebaseline only `maxBytes`: **800000 → 810000 B**.
- Keep `maxGzipBytes`: **250000 B**.
- Keep forbidden browser-runtime pattern checks unchanged.
- Do not remove v27.41.4 UX behavior merely to recover 6839 raw bytes.
- Do not treat repeated ceiling increases as the long-term fix.

The new raw ceiling leaves only 3161 B above the measured v27.41.4 build, so the gate remains intentionally tight.

## Follow-up

The current Vite entry remains effectively monolithic. The next frontend-delivery task is route/workspace bundle segmentation so opening Today does not require eager delivery of Calendar, Absence/TimeBank, Payroll, Settings, Admin and every other workspace in the same entry chunk. That work should be isolated from this hotfix because it changes loading/PWA asset behavior and deserves its own exact CI + browser acceptance.

## Unchanged boundaries

No HTTP/OpenAPI shape, Flyway migration, authentication/session rule, accounting/FIFO rule, offline/dataLayer ownership, onboarding boundary, dependency graph, retry policy or Playwright timeout is changed by v27.41.5.
