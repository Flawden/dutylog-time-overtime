# v27.38.3 — Vue Productivity Strict Typecheck Hotfix

## Trigger

The first exact v27.38.2 frontend gate passed the authentic npm lockfile, pinned delivery foundation and generated OpenAPI drift gate, then `vue-tsc --noEmit` stopped on nine strict TypeScript errors.

## Fix boundary

- Today template no longer accesses `window.DutyLogVueDomains` directly; typed setup functions own task/important detail commands.
- Calendar tasks reuse `DutyLogApiSchemas.Task`, whose `allDay` and schedule fields are already canonical.
- Selected-day note autosave uses `ReturnType<typeof globalThis.setTimeout>` instead of assuming a browser-only numeric timer id.
- Productivity Pinia actions resolve selected-date defaults inside method bodies instead of parameter initializers that reference `this`.
- Optimistic DayNote projection preserves non-null `content` even though the update request accepts nullable content.

## Non-goals

No backend business rule, API/OpenAPI shape, PostgreSQL/Flyway state, retry/timeout policy, browser assertion or offline queue ownership changes.

## Acceptance

Exact frontend gate, Maven 751/751, `npm run test:e2e` 47/47 Chromium, immutable-image checks, clean PostgreSQL smoke and staging deployment remain required.
