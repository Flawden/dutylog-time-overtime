# v27.41.1 — Overtime Chart Rendering Recovery

## Evidence

The proven-green v27.41.0 staging UI shows a populated yearly overtime chart with month labels and real ledger rows, while the plotted bars are visually absent. The model already exposes non-zero earned/used totals and the existing browser contract verifies those titles/data semantics.

## Root cause boundary

The remaining defect is presentation geometry, not accounting: `TimeBankPage.vue` mapped bar values to percentage heights and `.overtimeChartColumn__bars` itself used `height: 100%` inside a grid track. The chart could therefore retain valid columns/titles while a browser resolved the nested percentage geometry to an unusable rendered height.

## Fix

- Keep `ledgerChartColumns`, canonical server daily projection authority and actual usage-date aggregation unchanged.
- Give the bar plot an explicit 126 px height and map non-zero values to bounded pixel heights; zero remains exactly zero.
- Expose `data-earned-hours` / `data-used-hours` for deterministic diagnostics.
- Extend the existing Overtime Next Chromium scenario to assert real `getBoundingClientRect().height` for earned and used bars in year/month views, while retaining the zero-bar contract.

No Spring Boot calculation, HTTP/OpenAPI, PostgreSQL/Flyway, auth, offline queue, onboarding, Calendar Visual Language, dependency, timeout or retry policy changes.
