# v27.36.5 — Single-Pass CI & Final Vue Browser Parity Hotfix

## Failure evidence

The v27.36.4 Chromium run completed 43 of 45 scenarios. The remaining deterministic failures were:

1. `#ledgerThisMonth` did not expose `aria-pressed="true"` before the full refresh completed.
2. The absence composer reused an already-loaded zero-balance snapshot after an external overtime credit mutation.

## Product fix

- Month/year selection updates store ownership immediately and renders explicit ARIA boolean tokens.
- The composer refreshes the requested date and current range before reading planner types or overtime balance.
- Existing refresh sequencing still prevents an older request from overwriting a newer read model.

## Delivery fix

- Ordinary CI ignores only push events to `test`.
- Pull requests, tag pushes and pushes to every other branch keep the complete independent CI path.
- `Deploy staging` remains the sole push owner for `test` and retains frontend, Maven, static, Chromium, immutable-image and clean-PostgreSQL gates.

## Non-goals

No backend API, OpenAPI shape, generated client transport, PostgreSQL schema, Flyway migration, FIFO arithmetic, authentication or deployment topology change.
