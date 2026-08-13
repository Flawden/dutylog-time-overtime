# v27.41.2 — Overtime Chart Source Contract Alignment

## Failure
The v27.41.1 CI job stopped in Java Maven tests before Playwright. The production Vue chart had intentionally moved zero-height bars from `0%` to `0px` and introduced an explicit 126 px plot, while `FunctionalParitySweepIIMobileUsabilityContractTest` still required the old literal `return "0%"`.

## Fix
The Java source contract now asserts the intended pixel model: `CHART_PLOT_HEIGHT_PX = 126`, zero values return `0px`, and non-zero values normalize through `Math.round(ratio * CHART_PLOT_HEIGHT_PX)`.

## Boundary
No production Vue/TS model, accounting semantics, HTTP/OpenAPI, Flyway, auth, offline/dataLayer, onboarding, dependency, timeout or retry behavior changes. Browser geometry assertions from v27.41.1 remain intact.
