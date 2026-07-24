# DutyLog v27.6.3 — Polish & Consistency

## Product goal

This is a quality release. It does not introduce recursive task trees or another planning subsystem. It makes the existing task module coherent enough for daily use: correct deadlines, predictable ordering, compact cards, readable progress and lightweight subtask dates.

## Business rules

1. `task.dueDate` is nullable. When present it must be equal to or later than `task.date`.
2. `subtask.dueDate` is nullable. When present it must be equal to or later than the parent task date.
3. Validation runs after the complete create/update payload has been applied to the entity. Moving a task forward can therefore invalidate an unchanged old deadline and is rejected.
4. The same calendar day is valid because tasks do not yet have a dedicated start-time field.
5. Open tasks sort before completed tasks. Within each group the stable order is effective date (`dueDate`, otherwise task date), due time, task date, creation time and id.
6. Subtasks remain one-level checklist items. They do not receive categories, tags, priorities, reminders, descriptions or children in this release.

## Database and API

Flyway `V28__task_subtask_due_date.sql` adds nullable `task_subtasks.due_date DATE` and an index for dated checklist items.

`TaskSubtask`, `SubtaskDto` and `SubtaskInput` expose the date as ISO `yyyy-MM-dd`. Existing four-argument Java constructors remain available for source compatibility. During reconciliation:

- omitted `dueDate` on an existing child preserves the stored value;
- an explicit blank string clears the value;
- a valid ISO date replaces it;
- malformed or business-invalid dates return HTTP 400.

The contract is available through legacy endpoints and `/api/v1`, including Inbox-to-task conversion because it uses the same `TaskService` path.

## Frontend behaviour

- The editor rejects an invalid task or subtask deadline before sending a request, while the backend remains authoritative.
- Completed tasks move immediately after optimistic toggle and remain ordered after reload.
- A separator appears only when the visible result contains both open and completed groups.
- Checklist progress uses a real `role=progressbar` element with `aria-valuemin`, `aria-valuemax`, `aria-valuenow` and a readable label.
- Inline checklist disclosure is collapsed by default and labelled `Подзадачи (done/total)`.
- A subtask date is shown only when it exists.
- The date editor and inline rows are responsive at 320, 360, 390 and 430 px without horizontal scrolling.

## Offline boundary

Parent task completion without pending children continues to use the existing task offline queue. Direct subtask toggles and reconciliation with subtask dates remain online-only in v27.6.3. Durable child-operation queues, conflict resolution and cross-device merge belong to the planned offline-first engine.

## Acceptance checklist

### Deadline validation
- Create with a parent due date before the task date returns 400.
- Updating only the task date is validated against the final stored deadline.
- The same-day deadline is accepted.
- Invalid subtask dates are rejected in create/update and Inbox conversion.
- Frontend and backend return stable RU/EN messages.

### Ordering
- Completing a task moves it below open tasks immediately.
- Reopening it moves it back into the open group.
- Reload, search, day, range and board preserve open-first order.
- A divider is absent when only one status group is visible.

### Subtask dates and progress
- Missing date behaves exactly like v27.6.2.
- Date persists after reload, can be changed and can be cleared.
- Progress states 0/n, partial and n/n are visually distinct and accessible.
- Inline checkbox clicks do not open the parent editor.
- Parent completion confirmation still completes all remaining children atomically.

## Non-goals

Recursive subtasks, task projects, subtask descriptions, subtask reminders, a global redesign, Time Foundation, interval FIFO, multiple calendars and full offline child synchronization are intentionally excluded.
