# v27.41.3 — Today Visual Parity & Overtime Summary Recovery

## Baseline

Exact proven-green predecessor: commit `52a0e2a1b4e28aa56250bb813639830c56226a91`, tree `91fd1fa2fd426556d4031fbe14506cc722398b77`.

## Problem

The full Overtime chart was restored in v27.41.2, but staging still showed two Today-only parity gaps. The home overtime card rendered authoritative earned/used/balance numbers beside an inert track, and the seven-day hero strip stayed visually neutral even though Calendar Week already encoded shift color, factual absence and schedule-free days.

## Contract

Today now consumes the same canonical day visual semantics as Calendar Week. Shared typed helpers own factual full-day absence selection and CSS-variable projection; both surfaces keep factual absence stronger than shift decoration and use `calendarScheduleFree` for the palm rather than weekday assumptions.

The compact Today strip additionally places configured Important Day glyphs, the user's day marker and open-task count in stable zones. Calendar-layer overlays are deliberately not added to the Today strip; People Profiles will replace overlay-first schedule comparison in v27.42.0.

The Today overtime track uses the existing account snapshot only: `balanceHours / totalEarnedHours`, clamped to 0–100%. It changes no credit, usage, FIFO or projection calculation. The existing Today Playwright scenario seeds a deterministic credit and verifies real rendered width with `getBoundingClientRect()`, then assigns a deterministic shift and verifies shift tint plus a schedule-free palm. No timeout/retry increase or new browser scenario is introduced.

## Unchanged boundaries

- OpenAPI: 124 operations / 130 schemas.
- Flyway: V47.
- `dataLayer`: sole offline mutation/outbox/reconnect executor.
- First-run onboarding: bounded legacy compatibility owner unchanged.
- Auth/roles/CSRF/session behavior: unchanged.
- Dependency graph: unchanged.
