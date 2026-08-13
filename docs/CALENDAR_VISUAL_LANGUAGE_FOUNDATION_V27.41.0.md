# v27.41.0 — Calendar Visual Language Foundation

## Product goal

The month and week calendars use a stable visual grammar so a user can understand work, free time, Important Days, custom day markers and task load in a few seconds without reading repeated labels.

## Stable visual grammar

- Schedule-free days render a custom monochrome palm watermark at low opacity. The palm means **no shift in the active schedule**, not “Saturday/Sunday”; factual absence suppresses it.
- Shift days keep the canonical shift color as restrained tint, border/accent and glow. Full-day factual absence remains visually authoritative over the planned shift.
- Month cells use fixed non-competing zones: date at the upper-left, Important Day at the upper-right, custom day marker at the lower-left, and open-task count at the lower-right.
- Important Days preserve the configured `icon`; when no icon exists, the visual model falls back by event type (`★` Important Date, `◆` Event, `◇` Period). Multiple occurrences compact to `+N`.
- Week strip/agenda use the same shift/free-day semantics, replacing ambiguous dash-only free days with the palm while keeping shift labels where the larger layout can afford them.
- Weekend headers are softly distinguished as calendar context only; they do not decide whether a day is free.

## Accessibility and refresh behavior

Calendar cell `aria-label` keeps date, shift/absence/task/Important information and now explicitly identifies a schedule-free day and a custom day marker. The existing non-blocking `Обновляю календарь…` refresh state remains unchanged: stale-but-usable content stays visible while authoritative data refreshes.

## Boundaries

This release changes no HTTP/OpenAPI contract, Flyway migration, authentication/authorization rule, `dataLayer` offline mutation/sync ownership, first-run onboarding boundary or dependency graph. People Profiles and Shared Availability are intentionally deferred to v27.42.0; v27.41.0 only makes the calendar presentation/model ready for that future profile projection.
