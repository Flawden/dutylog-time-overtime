# v27.40.25 — Vue Offline Sync Surface & Legacy Header Retirement

## Purpose

v27.40.25 moves the remaining offline/sync presentation and save-status feedback into the Vue application shell without moving offline queue authority out of the existing `dataLayer` infrastructure.

After successful Vue readiness, the server-rendered legacy `.head` and `#offlineSyncDialog` are physically removed. Vue then owns the visible `#offlineStatus`, the sync/diagnostics modal and save feedback. The server-rendered equivalents remain available only as pre-Vue recovery.

## Ownership boundary

```text
Vue AppShell / OfflineSyncModal
        │
        │ immutable status + narrow commands
        ▼
LegacyBridge / DutyLogLegacyPlatform
        │
        ▼
existing dataLayer
        │
        ├─ IndexedDB snapshot
        ├─ offline mutation queue
        ├─ failed operations
        ├─ sync lock
        └─ reconnect sync
```

`dataLayer.syncQueue()` remains the single offline queue executor. Vue does not create an IndexedDB store, outbox, reconnect loop or second queue flush path.

## Vue-owned presentation

`AppShell.vue` now owns the visible `#offlineStatus` control. It renders online/offline, pending, failed, stale, syncing and cross-tab lock state from the immutable legacy snapshot.

`OfflineSyncModal.vue` owns the post-ready sync UI while preserving stable browser selectors:

- `#offlineSyncDialog`;
- `#offlineSyncMeta`;
- `#offlinePendingList`;
- `#offlineFailedList`;
- `#offlineDiagnosticsList`;
- `#offlineDiagnosticsCopy`;
- `#offlineSyncFeedback`;
- `#offlineSyncNow`;
- `#offlineFailedRetryAll`;
- `#offlineExport`;
- `#offlineFailedClear`;
- `#offlineSyncClose`;
- `data-failed-retry` / `data-failed-remove`.

The modal can inspect queue/failed/lock diagnostics and can request manual sync, retry, removal, failed-list clearing, diagnostics copy and local-data export only through named bridge operations.

## Save feedback

The historical `setSave()` call surface has many callers outside Vue. Deleting the legacy header without a replacement would silently discard saving/error feedback.

After Vue shell readiness, `setSave()` therefore publishes the typed `dutylog:save-feedback` event instead of writing `#saveState`. `App.vue` routes that event to the shell store and the Vue header renders the result. Before Vue readiness, `setSave()` keeps the server-rendered `#saveState` recovery behavior.

## Legacy recovery

`index.html` intentionally still contains the old `.head`, `#offlineStatus` and `#offlineSyncDialog` so a failed/late Vue boot has a usable recovery surface.

On `dutylog:vue-ready`, `shell-bootstrap.js`:

1. removes recovery-only `nextTopbar` / `tabbar`;
2. removes `body > .head`;
3. removes the server-rendered `#offlineSyncDialog`;
4. clears `syncDialogOpen`;
5. publishes `data-vue-offline-sync="ready"` and `data-vue-shell="ready"`.

Once `data-vue-offline-sync="ready"` is present, legacy offline status rendering and legacy offline-dialog click/keyboard handlers yield. Queue execution continues unchanged in `dataLayer`.

## Remaining legacy presentation

After this cut, **first-run onboarding is the only intentionally live post-ready legacy presentation exception**. It is planned as a separate ownership cut so onboarding behavior is not mixed into offline/sync migration risk.

## Non-goals

v27.40.25 changes no:

- HTTP/OpenAPI operation or schema;
- Flyway migration;
- backend business rule;
- authentication/authorization boundary;
- timeout or retry policy;
- queue operation semantics;
- reconnect owner;
- IndexedDB schema;
- first-run onboarding behavior.

OpenAPI remains 124 operations / 130 schemas and Flyway remains V47.

## Acceptance

Release acceptance remains blocking:

- exact Node 20.18.1 / npm 10.8.2 frontend gate;
- Java 17 Maven verify with 778 tests across 161 test classes;
- Playwright canary;
- full Chromium 48/48 with zero failed and zero flaky;
- immutable image build;
- PostgreSQL/Flyway V1–V47 smoke;
- staging deploy.

Static/source validation is not sufficient to declare the release green.
