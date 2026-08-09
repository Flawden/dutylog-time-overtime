# Tasks, Notes & Important Days Vue migration manifest

```yaml
domain: productivity
target_release: "v27.38.0"
status: acceptance-pending
frontend_owner: Vue 3 + TypeScript
business_owner: Spring Boot
transport: generated OpenAPI client
offline_owner: existing legacy dataLayer queue/snapshot adapter
rollback: application image rollback to green v27.37.5
```

## Ownership after v27.38.0

Vue owns the Tasks board, Inbox presentation, Task details/editor, selected-day Tasks, selected-day multiple Notes, Important Days board/details/editor and selected-day Important Dates presentation. Vue owns only UI state, form drafts, filters, read sequencing, optimistic presentation and orchestration.

Spring Boot remains the source of truth for task validation, deadlines, schedule projection, subtask completion, recurrence, important-event timezone projection, note ordering/persistence, Inbox conversion and every persisted business rule. The migration does not duplicate canonical work-time, absence, FIFO, payroll or recurrence calculations in the browser.

The legacy productivity read/modal owners are retired after Vue boot. Public IDs required by released browser flows are preserved by the Vue owner while legacy cross-domain entry points delegate through the named `DutyLogVueDomains.productivity` adapter.

## Generated API contract

The canonical OpenAPI contract expands from 98 operations / 103 schemas to 101 operations / 106 schemas so the generated TypeScript client fully describes the already-released backend aliases needed by the migrated domain. The migration adds typed Task board/detail/update shapes and typed Important Day update/delete/occurrence operations; it does not introduce a database migration.

Vue-generated writes use `/api/v1/*`. Existing legacy `/api/*` aliases remain available for compatibility and for the bounded offline adapter until legacy infrastructure retirement.

## Offline/reconnect boundary

Q-10 becomes active in this release for migrated productivity mutations without creating a second queue. The existing `dataLayer` remains the only offline queue/snapshot infrastructure and is exposed through typed bridge capabilities for:

- note title/content/pin updates;
- task completion;
- Inbox quick capture;
- cached selected-day Tasks/Notes/Important reads;
- queue flush after the browser returns online.

New-note creation is disabled while offline because the released queue does not yet own create-note identity allocation. Generated API reads/writes remain online-only unless explicitly routed through the bounded offline bridge. On reconnect Vue asks the existing queue to synchronize, then reloads authoritative backend state.

## Concurrency and conflict rules

Independent read sequences protect selected-day, Task board, Important Days, Inbox and note-search reads from stale responses. Mutation buttons are double-submit guarded. HTTP 409 refreshes authoritative state and surfaces an explicit conflict message instead of silently overwriting data.

## Acceptance boundary

- Existing 47 Chromium scenarios remain strict and are updated only where Vue now intentionally sends the same action through `/api/v1/*`.
- Task planning keeps all-day/timed intervals, duration presets, deadlines, reminders, project/category/tags and subtask dates.
- Parent completion with pending subtasks still requires explicit confirmation and remains backend-owned.
- Multiple daily notes preserve independent title/content, pin, order, search, export, reload and delete semantics.
- Important Dates remain floating all-day dates; timed events retain source-timezone semantics and reminders.
- Calendar/Today continue to open Task and Important details through named cross-domain capabilities and refresh after writes.
- Legacy productivity render/modal owners yield after `data-vue-productivity=ready`.
- No Playwright retry, timeout, locator strictness or page-error policy is weakened.
- PostgreSQL and Flyway remain V47.
