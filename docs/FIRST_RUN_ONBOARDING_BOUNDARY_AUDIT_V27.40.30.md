# v27.40.30 — First-Run Onboarding Boundary Audit

## Decision

Keep `#firstRunOnboarding` as the **single bounded post-ready legacy presentation exception** for the remainder of v27.40.x. Do not migrate it to Vue merely to claim a numeric zero.

## Evidence from the exact v27.40.29 tree

- The live presentation owner is isolated to the server-shell overlay in `index.html` plus onboarding helpers in `20-data.js`; it does not own a hash route or a product screen.
- Vue already consumes the authoritative result through `profile.onboardingCompleted` and module state; Productivity uses that value as a readiness gate rather than reading onboarding DOM.
- Completion ordering is intentionally coupled to first-run safety: module update → module publication/refresh → profile onboarding completion → overlay release → service-worker registration.
- Module dependency enable/disable propagation is already implemented in the legacy onboarding draft and backed by the server module contract.
- The existing auth/onboarding Playwright scenario is the mandatory browser canary, so the exception is continuously exercised rather than hidden from acceptance.

## Boundary

`#firstRunOnboarding` is marked `data-bounded-legacy-owner="first-run-onboarding"`. No second marker is permitted. The exception may:

- render preset/module choices for a first-run user;
- persist the selected module state through the existing module API;
- mark the profile onboarding flag complete;
- release body scroll and start the existing PWA registration only after authoritative completion.

It may **not** own route state, normal Settings/module presentation, offline queue execution, shell identity/logout, or any routed user-facing screen after Vue readiness.

## Why migration is deferred

A Vue rewrite is technically possible because generated Profile and Modules operations already exist. It is not justified in this phase: it would touch the earliest authenticated boot path, dependency-aware module draft behavior and service-worker first-claim sequencing while providing no functional parity gain. This conflicts with the project rule that migration must remove a real competing owner, not chase a cosmetic architecture score.

## Exit criterion

Revisit native Vue onboarding only when there is product work that materially changes onboarding (the later Guided Onboarding / Product Education roadmap item) or when the bounded exception itself creates a demonstrated defect. Until then, it is an explicit compatibility surface, not unclassified debt.

## Next phase

Start the Functional Parity Sweep from the proven-green v27.40.30 baseline. First candidates remain Today shift-remaining-time parity, Important Days relative-day copy, selected-day parity, workspace/module persistence, cross-midnight/timezone behavior, navigation/reload and offline/reconnect acceptance.
