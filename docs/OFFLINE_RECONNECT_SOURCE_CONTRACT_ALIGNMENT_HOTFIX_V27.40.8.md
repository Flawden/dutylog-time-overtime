# v27.40.8 — Offline Reconnect Source Contract Alignment Hotfix

## Evidence

The local v27.40.7 Maven run executed all **758** tests and reported exactly **1 failure / 0 errors / 0 skipped**:

`VueTasksNotesImportantMigrationFrontendContractTest.offlineReconnectUsesExistingDataLayerQueueAndCachedSelectedDayWithoutInventingASecondStore` at line 184.

The product source already contains the intended reconnect guard in `SelectedDayNotes.vue`: `onBeforeUnmount` writes only when a debounce timer is still pending. The failing assertion searched for the prose sentence `must not resubmit the already queued/current note beside dataLayer.syncQueue()` as one uninterrupted substring, but that comment is split across two source lines.

## Fix

The Maven contract now scopes checks to the actual `onBeforeUnmount` block and requires:

- `if (timer != null && currentNote.value)`;
- `globalThis.clearTimeout(timer)`;
- `timer = null`;
- guarded `store.updateNote(currentNote.value.id, ...)`.

No product runtime code changes. `dataLayer.syncQueue()` remains the sole reconnect queue owner. No retry/timeout, TypeScript strictness, OpenAPI 118/120, Flyway V47 or business-rule relaxation is introduced.
