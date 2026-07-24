# Quick Capture Polish — v27.6.1

## Product decision

Inbox is a temporary capture layer, not a second task list and not a separate navigation tab. The user can record something immediately, then decide later whether it should become a task, a note, an important date or simply be archived.

## Universal quick add

The floating `+` opens one compact surface with a single text field. The same draft can be:

- saved to Inbox with Enter;
- used as the prefilled text of a new task;
- appended to today's Markdown note;
- used as the title of a new important date;
- ignored when opening overtime credit or usage editors.

Only actions backed by enabled modules are shown. Quick add remains available when any of Tasks, Notes, Important dates or Overtime is enabled.

## Inbox tray

Inbox no longer occupies a large card above the task board. It is a collapsed `<details>` tray inside the task board with a compact open-item counter. Expanding the tray reveals quick capture, open/archived filtering and conversion actions. An empty tray stays visually quiet but remains reachable.

## Offline and compatibility

The existing idempotent IndexedDB queue and `/api/inbox` contract are unchanged. No new Flyway migration is required; the schema remains at V26. Existing Inbox records, tags and task conversion behavior are preserved.

## Regression contract

- `MobileTasksInboxFrontendContractTest` protects the collapsed tray, universal draft field and module-aware actions.
- `task-modules.spec.js` exercises the new `+ → text → Inbox → task` path.
- Baseline remains 76 Java test classes / 381 `@Test` methods and 13 Chromium Playwright scenarios.
