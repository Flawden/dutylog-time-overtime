# DutyLog v27.44.2 — Exact Availability Timeline & Shared Shift Summary

## Exact selected-day rail

The rail no longer uses approximate `00 / 06 / 12 / 18 / 24` guidance. Every visible free-time edge is labelled with its exact `HH:mm` boundary. When shared-work overlay is enabled, shared-work segment edges are included too. Day edges stay explicit as `00:00` and `24:00`; close internal labels alternate vertical lanes on narrow screens.

## Shared shift month summary

`Совместные смены` continues to use strict positive interval intersection. Shift equality is irrelevant: `06:00–15:00` and `08:30–17:00` count because they overlap `08:30–15:00`; `06:00–15:00` and `15:00–23:00` do not.

While the mode is active, highlighted month dates are accompanied by a footer:

- `Совпало смен: N` — count of focus-month calendar dates with positive simultaneous work;
- `Вместе на работе: H ч M мин` — sum of exact shared-work windows on those dates.

Overnight work remains display-date clipped by the existing v27.44.x projection.

## Authority and baselines

No backend/API/database/dependency authority is added. OpenAPI remains **126/132**, Flyway **V48**, Java **792/164**, Playwright **48**, Vitest **71**. Browser ceilings remain **850000 B raw / 250000 B gzip** until exact Node 20 CI measurement.
