# Calendar day identity hotfix

Status: v27.2.5.

Root cause: `sanitizeDayForModules()` removed `date` from every `DayDto`.
After a successful bulk fill, the authoritative `/api/calendar` response was sanitized and
all rows were assigned to `state.days[undefined]`. The database and backend response were correct;
the browser destroyed the identity field before rendering.

The sanitizer now preserves `date`, `version`, `updatedAt`, and overtime balance metadata.
Regression guards protect this invariant. Missing static resources now return 404 instead of being
logged as internal server errors.
