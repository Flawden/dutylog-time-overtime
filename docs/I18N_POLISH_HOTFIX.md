# v26.6.10 — English i18n polish hotfix

Status: v26.6.10.

Scope: release stabilization only. No new features.

What changed:

- Dynamic settings panels are re-rendered after language changes.
- The calendar current-day marker uses CSS language selectors, so `сегодня` becomes `today` in English.
- Calendar summary uses the translation key `Итого:` → `Total:`.
- Working-time labels, autosave chip and browser notification status are rendered with explicit localized labels.
- Built-in shift names are localized in user-facing places while persisted/custom names are not modified.
- Quick-scenario and reminder empty states received an additional i18n pass.

Known boundary:

- User-created names, notes, task texts, scenario names and reasons are personal content and are not auto-translated.
