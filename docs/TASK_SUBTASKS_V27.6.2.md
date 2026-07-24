# DutyLog v27.6.2 — Tasks & Subtasks

## Product decision

Subtasks are a one-level checklist inside a normal task, not an independent task hierarchy. A subtask cannot contain another subtask. This keeps the editor fast, prevents recursive navigation and leaves room for a future dedicated project/planner model.

## User experience

- The task editor can add, delete and reorder up to 50 checklist items.
- Normal task cards stay compact and show progress such as `2/4`.
- The checklist expands inline only when the user asks to see it.
- A checklist item can be completed directly from the calendar or task board.
- Completing a parent while checklist items remain open requires explicit confirmation.
- Confirming that action completes the parent and all remaining checklist items together.

## API contract

Task create/update responses include an ordered `subtasks` array. Inbox conversion accepts the same structure.

A single item can be toggled through:

```text
PATCH /api/tasks/{taskId}/subtasks/{subtaskId}
PATCH /api/v1/tasks/{taskId}/subtasks/{subtaskId}
```

The endpoint is authenticated, module-guarded and owner-scoped. A foreign task or checklist item is indistinguishable from a missing resource.

## Persistence

Flyway `V27__task_subtasks.sql` adds `task_subtasks` with:

- a required parent task;
- cascade deletion with the parent;
- text up to 300 characters;
- completion state;
- deterministic `sort_order`;
- creation timestamp;
- an index on parent, order and id.

Subtasks are intentionally not recursive in v27.6.2.

## Offline boundary

Task creation and normal parent updates continue using the existing application paths. Direct inline subtask toggles require a connection in v27.6.2. Durable offline child operations, conflict resolution and cross-device merging belong to the planned full offline-first synchronization release rather than a fragile release-local queue extension.

## Acceptance checklist

1. Create a task with two checklist items and preserve their order after reload.
2. Expand the task and toggle one item; progress changes from `0/2` to `1/2`.
3. Edit, reorder and remove checklist items; the saved order matches the editor.
4. Complete a parent with an unfinished item; confirmation is shown.
5. Accept confirmation; parent and every child become complete.
6. A different user cannot read or update another user's checklist item.
7. Searching for text contained only in a checklist item finds the parent task.
