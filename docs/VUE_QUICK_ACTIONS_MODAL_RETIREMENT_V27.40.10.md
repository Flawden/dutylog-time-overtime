# v27.40.10 — Vue Quick Actions Modal Retirement

## Baseline

v27.40.9 is the proven-green predecessor: exact frontend, Maven, Playwright 48/48 with zero flaky retries, immutable image and PostgreSQL V47 staging deployment are accepted.

## Ownership cut

`QuickActionsModal.vue` is the live owner after Vue Productivity readiness. The legacy `#quickActionsModal` source markup remains only as recovery/fallback DOM before Vue mounts and is removed by `retireDomainOwners("productivity")` once the Vue domain is ready.

The migrated modal preserves the established browser contract IDs: `quickActionsModal`, `quickActionText`, `quickActionInbox`, `quickActionTask`, `quickActionNote`, `quickActionImportant`, `quickActionCredit` and `quickActionUsage`.

## Actions

- Inbox capture continues through the existing Productivity offline adapter and therefore the single legacy `dataLayer` mutation queue.
- Task creation uses the native Vue Productivity editor.
- Note creation opens the native Vue selected-day Notes section.
- Important Day creation uses the native Vue Important editor.
- Overtime Credit and Absence open the native Absence & Time Bank domain editors.

## Bridge retirement

`openQuickActions` is removed from `LegacyBridge` and `DutyLogLegacyPlatform`. Vue Today calls `DutyLogVueDomains.productivity.openQuickActions()` directly. The old `globalQuickAdd` button remains a recovery-compatible entry; after Vue readiness its legacy handler delegates immediately to the Vue Productivity domain instead of opening the fallback modal.

## Boundaries retained

- `dataLayer` remains the single offline mutation/reconnect owner.
- Payroll/Admin remain legacy hash-router boundaries.
- Shift Type Manager remains a modal adapter for a later v27.40.x cut.
- OpenAPI and Flyway contracts are unchanged.
