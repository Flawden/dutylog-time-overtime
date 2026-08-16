# DutyLog v27.44.4 — Shared Full-Day Free Dates

## Product rule

A shared full-day-free date means both people have **zero effective work minutes for the whole displayed calendar day**, and the companion profile schedule is known for that date.

This is deliberately not implemented as a list of absence names. The month projection reuses `sharedAvailabilityForDate(...).allDayFree`, which already derives effective work after owner factual absences, partial-hour subtraction, managed People Profile overrides, overnight clipping and coverage semantics.

Therefore:

- ordinary day off + ordinary day off -> shared full-day free;
- owner day off + companion managed `OFF` -> shared full-day free;
- factual `FULL_DAY + replacesShift=true` absence + companion day off -> shared full-day free;
- `PARTIAL` / `HOURS_ONLY` with remaining work -> not shared full-day free;
- untimed effective work -> fail closed;
- companion date outside `startDate` / `endDate` coverage -> fail closed.

## Visual language

- green/teal outline = together free from work for the full day;
- coral outline = at least one minute of simultaneous work, only while `Совместные смены` is enabled;
- shift/absence/day-off semantics keep their existing colors and content.

## Regression surface

- Vitest: **73** source cases.
- Java: **792 / 164**.
- Playwright: **48**.
- OpenAPI: **126 / 132**.
- Flyway: **V48**.
- Browser ceilings stay **855000 B raw / 250000 B gzip** until exact Node 20 measurement.
