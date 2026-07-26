# DutyLog v27.10.0 — Task Details

## Goal

Keep task capture fast while giving an existing task a calm, read-first place for context, checklist progress and actions. A card click opens details; editing is an explicit second step.

## Product contract

- New tasks still require only text and a date.
- Existing task cards open a dedicated details modal instead of immediately opening the form.
- Details show title, status, metadata, optional description, checklist, task date, due date and reminder.
- Checklist items remain directly actionable from details.
- Edit, complete/reopen and delete actions are explicit.
- The editor keeps advanced fields collapsed until needed.
- Description is optional, plain text, preserves line breaks and is limited to 4000 characters.
- Empty descriptions do not produce empty visual noise.

## Backend and persistence

- Flyway V32 adds nullable `day_tasks.description VARCHAR(4000)`.
- `TaskDto`, create/update payloads and Inbox conversion expose `description`.
- `GET /api/tasks/{id}` and `GET /api/v1/tasks/{id}` return an authoritative owner-scoped task.
- Board search includes description text.
- Blank description clears the field; omitted description preserves it during PATCH.
- Foreign task IDs remain indistinguishable from missing IDs (`404`).

## Frontend boundary

- `taskDetailsModal` is read-first and separate from `taskEditModal`.
- Online opening refreshes through `GET /api/tasks/{id}`; offline opening uses the current month/board snapshot.
- Description is plain text (`textContent`), never injected as HTML.
- Card and board clicks open details; the Edit button opens the existing editor.
- Mobile actions stack vertically and long descriptions wrap safely.

## Non-goals

- Rich text or Markdown rendering inside task descriptions.
- File attachments.
- Comments, audit timeline or collaboration.
- Recursive subtasks.
- Projects or multiple task owners.

## Acceptance

1. Create a task using only text/date.
2. Open its card and see the details modal, not the editor.
3. Choose Edit, add a multi-line description and a subtask, then save.
4. Reopen details and verify both values persist.
5. Toggle the subtask from details.
6. Complete and reopen the parent from details.
7. Search the board by a word found only in the description.
8. Reload and verify description persistence.
9. Check 320–430 px widths for wrapping and action layout.
