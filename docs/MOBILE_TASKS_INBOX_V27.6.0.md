# Mobile Tasks & Inbox UX — v27.6.0

## Product rule

DutyLog must minimise the time between a thought appearing and that thought being safely stored:

```text
thought → one tap → text → saved
```

Organisation is deliberately deferred. Categories, tags, due dates and reminders remain available, but they never block fast capture.

## Inbox

`InboxItem` stores an unstructured user-owned thought with:

- text up to 2,000 characters;
- `OPEN` or `ARCHIVED` state;
- created, updated and resolved timestamps;
- an optional client operation id.

The `(user_id, client_operation_id)` unique constraint makes IndexedDB queue retries idempotent. A network interruption after the server accepted a request therefore does not create a second copy when the browser retries later.

An open Inbox item can be converted into a task inside one backend transaction. The task receives the Inbox text and user-selected structure; only after task creation succeeds is the source item archived.

## Task editor

The selected-day panel and global task board no longer duplicate a large creation form. Both open the same editor:

- task text and date are visible immediately;
- optional fields use progressive disclosure;
- mobile uses a full-height editor;
- due time remains a native HTML time input;
- existing tasks use the same editor as new tasks and Inbox conversion.

Categories and tags are normalised to lower case. Tags are deduplicated, limited to ten per task and capped at 40 characters each. The metadata endpoint supplies saved suggestions without exposing another user's values.

## Mobile quick actions

The floating `+` button opens four explicit actions:

1. Record a thought.
2. Create a task.
3. Add overtime.
4. Списать переработку.

The final action is intentionally written out instead of using an ambiguous minus symbol.

## Offline behaviour

Quick capture extends the current operation queue with `captureInbox`:

- online: POST directly and render the server item;
- offline/network failure: store text plus a UUID in IndexedDB;
- reconnect: retry with the same UUID;
- success: remove the queue item and reload Inbox;
- permanent 4xx failure: move the operation to failed operations for user review.

## Database migration

Flyway `V26__task_tags_and_inbox.sql` creates:

- `day_task_tags` with task foreign key and cascade delete;
- `inbox_items` with user foreign key, status constraint and idempotency key;
- supporting indexes;
- lower-case normalisation of existing non-null task categories.

## Release checks

The release gate verifies the migration, API paths, module contract, quick-capture HTML, mobile full-screen CSS, offline operation type and the absence of the old inline task fields.
