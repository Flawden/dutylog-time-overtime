# v27.39.6 — Module Runtime Synchronization & Offline Reconnect Ownership Hotfix

The complete v27.39.5 Playwright artifact contains 48 scenarios: 44 passed and 4 failed. All four final failures are Task-module journeys. The failure screenshots and network trace show the Vue Settings module card still checked while the authoritative runtime/backend module state is disabled after first-run onboarding. The idempotent E2E helper therefore returns correctly, but `#view-tasks`, Quick Add and selected-day Tasks remain hidden.

The fix keeps the runtime module map authoritative. `SettingsWorkspace` merges shell module flags into its richer generated module catalog after bootstrap and whenever the shell publishes a new module snapshot. The module catalog metadata remains in Vue Settings; only current enablement is synchronized.

The same artifact contains one retry-only PWA offline failure. On reconnect the trace records two identical `PATCH /api/notes/{id}` requests for the same queued edit within milliseconds: one returns 200 and one returns 500, leaving one pending queue item on the first attempt. The migration already requires one existing legacy `dataLayer` queue, so Vue Productivity no longer starts another `online` flush. The dataLayer publishes `dutylog:offline-sync-complete` after its canonical sync finishes and Vue refreshes its read model from that completion signal.

No backend business rules, OpenAPI routes/schemas, Flyway migrations, Playwright retry/timeout policy or strict runtime/HTTP collectors are changed. Acceptance remains 48/48 with zero flaky retries.
