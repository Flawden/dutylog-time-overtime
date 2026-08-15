# DutyLog v27.44.0 — Shared Availability

Selected-date common free-from-work windows for `Я` plus one selected People Profile.

- Owner work: canonical `shiftOccurrences.displayStart/displayEnd`.
- Managed-profile work: canonical `CalendarLayerEntry.displayStart/displayEnd`.
- v27.43.0 WORK/OFF overrides are already reflected before display projection.
- Owner factual full-day absence removes work busy time; PARTIAL/HOURS_ONLY removes only its exact interval.
- Tasks, Notes, Important Days, Payroll and Overtime are not inputs.
- Untimed effective work fails closed.

Regression surface: Java 792/164, Playwright 48, Vitest 70, OpenAPI 126/132, Flyway V48, browser total budget 835000 B raw / 250000 B gzip.
