# v27.38.0 — Vue Tasks, Notes & Important Days

`v27.38.0` migrates the productivity domain from legacy DOM ownership to one Vue feature while preserving Spring Boot as the business owner and the existing offline queue as infrastructure owner.

## Vue-owned surfaces

- Tasks board and filters.
- Inbox presentation and conversion flow.
- Task details and editor.
- Selected-day Tasks.
- Multiple daily Notes editor/list/search controls.
- Important Days board, details and editor.
- Selected-day Important Dates.
- Cross-domain Task/Important launches from Today and Calendar.

## State and transport

`frontend/src/features/productivity` contains the typed generated-API adapter, Pinia store, pure domain helpers and Vue components. Independent read sequence counters implement latest-read-wins. The store owns only view state and editor drafts; backend DTOs remain authoritative.

The OpenAPI generator advances to 101 operations / 106 schemas. Vue writes use generated `/api/v1/*` operations. Compatibility aliases remain server-side and the existing `dataLayer` is reached only through named bridge methods for offline-safe mutations.

## Q-10 offline/reconnect

No second IndexedDB/localStorage queue is introduced. When offline, selected-day productivity data is read from the existing cached calendar snapshot, note edits/task completion/Inbox capture use the existing queue, and note creation is disabled because the current queue does not provide safe client identity allocation for new notes. The `online` event flushes the queue and refreshes authoritative reads.

## Rollback

There is no Flyway migration. Roll back the application image to accepted `v27.37.5`; PostgreSQL remains on V47 and requires no database rollback.
