# v27.17.3 — Java Contract Build Gate Hotfix

- Fixed the malformed escaped Java string literal in `CalendarMobileExperienceFrontendContractTest` that stopped Maven during `testCompile` before Playwright and deployment.
- Added a fast `javac` syntax gate for all static `*FrontendContractTest.java` files using minimal local JUnit stubs, so this class of source-level regression is caught by `release-check.sh` before packaging.
- Kept the v27.17.2 timeline readability behavior unchanged.
- No schema, backend API or frontend behavior change; Flyway remains V36. Regression baseline remains 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.17.2 — Calendar Timeline Readability Hotfix

- Increased the desktop visual floor for short timed tasks, reminders and overtime events so title and time remain readable.
- Preserved the compact mobile timeline layout unchanged.
- Always renders a task time range before optional category / priority metadata.
- Added visual lane reservation for short events so the larger readable card cannot overlap the next timed item.
- Extended the existing desktop editor E2E scenario to verify a real `17:41` task, event height and both text rows staying inside the card.
- No schema or backend API change; Flyway remains V36. Regression baseline remains 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.17.1 — Calendar & Notes Quality Hotfix

- Made the multiple-notes editor responsive to its own selected-day container with container queries instead of global viewport breakpoints.
- Prevented list, action toolbar, tabs, preview and editor controls from escaping narrow desktop side rails.
- Added an explicit all-day rail with a label, count and compact chips for important dates, untimed tasks, notes and untimed shifts.
- Prefilled important-date creation with the currently selected calendar day instead of a stale previous value.
- Projected reminders by their actual display/reminder date and removed duplicate `IMPORTANT_DAY` notification blocks from the hourly shift grid.
- Changed task deadline time precision from five minutes to one minute, including the `17:41` E2E contract.
- Extended existing Playwright and static frontend contracts without increasing the test-count baseline.
- No schema or backend API change; Flyway remains V36. Regression baseline: 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.17.0 — Calendar Mobile Experience

- Added a persistent Month / Week / Day calendar scale for DutyLog Next.
- Added a mobile week strip, selected-day agenda and swipe navigation.
- Added an hourly day timeline for shifts, timed tasks, reminders and overtime, plus all-day items and a live current-time marker.
- Connected Today Dashboard cards and date strip to the hourly day view while preserving the full legacy day editor.
- Reused the existing authoritative calendar/task/overtime stores; no parallel API or persistence model was introduced.
- Kept Classic as an immediate fallback and preserved all business logic.
- No schema or backend API change; Flyway remains V36. Regression baseline: 94 Java test classes, 492 `@Test` methods and 25 Playwright scenarios.

# v27.16.3 — Time Settings Transaction Hotfix

- Preserved unsaved shift-template form values while a timezone save continues background calendar/task/ledger refreshes.
- Serialized debounced and manual built-in shift-template updates so an older in-flight autosave cannot repaint or win over a newer manual apply.
- Added revision guards that only mark the exact captured draft as committed and ignore stale UI completion work.
- Extended frontend contracts and release checks for the queue and draft-preservation invariants.
- No schema or backend API change; Flyway remains V36. Regression baseline remains 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.16.2 — Next Route & Time Settings E2E Hotfix

- Aligned browser scenarios with the intentional DutyLog Next startup route: `#today` remains home, while shared helpers explicitly open Calendar before selecting a date.
- Added robust E2E workspace navigation that uses visible tabs when available and hash routing for hidden legacy workspaces such as Important Dates.
- Updated onboarding regression expectations to protect Today persistence across reload instead of the retired Calendar default.
- Fixed the built-in shift-time apply race by cancelling the pending autosave debounce before an explicit apply.
- Added static regression guards for the route helper and debounce cancellation.
- No schema or backend API change; Flyway remains V36. Regression baseline remains 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.16.1 — Today Runtime & Repository Truth Hotfix

- Fixed a frontend bundle load-order regression where `35-today.js` resolved `openQuickActions` before `50-tasks.js` had declared it.
- Deferred the Today quick-actions callback until click time, removing the shared `ReferenceError` that cascaded into all 24 Playwright failures.
- Added a Java frontend contract and fast release-check guards for the safe callback form.
- Updated current-release documentation to Java 17, Flyway V36 and the v27.17.0 Calendar Mobile Experience roadmap.
- No database migration and no backend/business behavior change; Flyway remains V36.
- Regression baseline remains 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.16.0 — Today Dashboard

- Added `#today` as the default DutyLog Next destination while preserving the full calendar as a separate primary route.
- Added a responsive seven-day strip, active/next shift card, immutable-instant progress and countdown, overtime balance, today tasks, upcoming important dates and quick actions.
- Reused the existing calendar bundle, shift occurrence, overtime account, task and important-date stores; no parallel dashboard API or persistence model was introduced.
- Kept all writes inside existing task, note, overtime, important-date and quick-action flows.
- Reduced mobile primary navigation to five focused destinations: Today, Calendar, Overtime, Tasks and More.
- Added static frontend contracts and a mobile Playwright scenario covering task creation, immediate dashboard composition, calendar opening and brand navigation.
- No schema change; Flyway remains V36. Regression baseline: 93 Java test classes, 489 `@Test` methods and 24 Playwright scenarios.

# v27.15.0 — Design System & Mobile Shell Foundation

- Added an additive `design-system.css` layer with spacing, radius, surface, shadow, focus and responsive navigation tokens.
- Introduced the DutyLog Next shell: branded top bar, adaptive month header, icon-based primary navigation and safe-area-aware mobile bottom bar.
- Preserved the previous layout as Classic; the shell can be switched instantly from Appearance settings without touching domain data or APIs.
- Persisted the allowlisted `themeConfig.shellMode` enum (`next` / `classic`) through the existing profile theme contract.
- Refreshed cards, forms, buttons, calendar cells, settings, modals, loading state and login presentation while keeping the existing DOM and business handlers.
- Added light-theme and reduced-motion boundaries for the new shell.
- Added static frontend contracts, profile validation coverage and a mobile Playwright scenario that switches Next → Classic → Next and protects horizontal overflow/ARIA navigation state.
- No schema or business-logic change; Flyway remains V36. Regression baseline: 92 Java test classes, 485 `@Test` methods and 23 Playwright scenarios.

# v27.14.2 — Calendar Notes Persistence E2E Hotfix

- Updated the calendar persistence browser scenario to the Multiple Daily Notes contract.
- The test now creates a concrete note through `POST /api/notes` before editing, because the empty-state editor is intentionally hidden.
- Debounced content persistence is awaited through `PATCH /api/notes/{id}` instead of the removed legacy day-level note `PUT`.
- Month navigation, full reload, shift persistence and emoji persistence are still verified in the same end-to-end flow.
- No production code or schema change; Flyway remains V36. Regression baseline remains 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.

# v27.14.1 — Mobile Notes Tombstone Hotfix

- Fixed Android API v1 note clears deleting the versioned `day_entries` tombstone through the new multiple-note legacy-shadow bridge.
- `DayNoteService` now accepts an explicit empty-row retention policy from `DayEntryService`; only versioned v1 sync preserves the tombstone, while legacy mobile clear keeps its historical row-deletion behaviour.
- Optimistic versions remain monotonic after `clearNote`, so stale offline creates still receive a conflict instead of resurrecting deleted content.
- Explicit clear flags continue to win over contradictory note/emoji values in the same patch.
- No schema change; Flyway remains V36. Regression baseline remains 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.

# v27.14.0 — Multiple Daily Notes

- Replaced the single mutable day-note field with independent owner-scoped notes per calendar date.
- Added titles, pinning, stable ordering, individual edit/delete operations and a dedicated `/api/notes` + `/api/v1/notes` contract.
- Added Flyway V36 and one-time migration of every non-empty legacy `day_entries.note`; the old field remains a primary-note compatibility shadow.
- Day/calendar/mobile payloads now expose the full `notes` collection while preserving the legacy `note` field.
- The day panel now provides a note list, active editor, pin/reorder/delete controls and a calendar count badge.
- Debounced title/content edits merge into one PATCH so rapid input cannot lose either field.
- Offline snapshots remain readable; unsupported note mutations are disabled until the server is reachable.
- ZIP export writes one Markdown file per independent note.
- Regression baseline: 91 Java test classes, 482 `@Test` methods and 22 Playwright scenarios.

# v27.13.0 — Temporal Consistency & Legacy Cleanup

- Calendar month totals now use the current-timezone overtime projection and never resurrect stale `day_entries` hours when the projected balance is exactly zero.
- Compatibility `/api/overtime/summary` and `/api/overtime/ledger` now share the authoritative account projection.
- Added canonical server-side overtime preview so DST gaps/overlaps and an independently configured browser timezone cannot change the editor result.
- `FIXED_TIME` quick scenarios now carry signed day offsets and reproject across canonical timezone changes, including UTC+14 ↔ UTC−11 round trips.
- Added Flyway V35 for `quick_scenarios.end_day_offset`; legacy `end_next_day` remains a compatibility alias.
- Explicitly preserved floating civil-date semantics for birthdays, important dates, notes, markers, date-only task/subtask deadlines, time-off dates and daily digest time.
- Regression baseline: 88 Java test classes, 467 `@Test` methods and 21 Playwright scenarios.

# v27.12.1 — Midnight Projection Contract Hotfix

- Reconciled the legacy exact-24-hour `12/12` source-credit rule with the v27.12 current-timezone civil-day projection.
- An `08:00 → 08:00` interval still persists as two immutable 12-hour source credits, but user-facing projected totals are asserted as `16 h` before local midnight and `8 h` after it.
- Removed the stale assertion that treated projected account rows as persisted source rows.
- Preserved all 1440 earned minutes, FIFO provenance and account balance.
- Clarified the overtime form hint and API documentation. No database migration; Flyway remains at V34.
- Regression baseline remains 87 Java test classes, 460 `@Test` methods and 21 Playwright scenarios.

# v27.12.0 — Zoned Daily Projection Engine

- Exact overtime credits are projected into current-timezone calendar-day slices without rewriting persisted source rows.
- A `22:00–02:00` absolute interval now redistributes as `2/2`, `1/3` or `0/4` when the canonical timezone moves.
- FIFO allocation intervals are split by the same local-midnight boundaries while credit IDs, allocation IDs and total minutes stay unchanged.
- Ledger rows expose daily earned/used/remaining totals and full source-credit totals through an additive projection DTO.
- Calendar selected-day totals, server-side date filters, CSV and Excel exports use the projected local date.
- Edit/delete actions use full source-credit usage, preventing deletion through an unused projected fragment.
- Legacy quantity-only credits remain one floating row because their missing source instant cannot be inferred safely.
- Added service, frontend-contract and Playwright coverage. Flyway remains at V34.
- Regression baseline: 87 Java test classes, 460 `@Test` methods and 21 Playwright scenarios.

# v27.11.4 — Task Deadline & Reminder Timezone Hotfix

- Timed task deadlines now persist one absolute `dueInstant` plus their original IANA timezone and source local date/time.
- Changing the canonical timezone reprojects the displayed deadline without changing overdue state or the underlying moment.
- Deadline projection can cross midnight; date-only deadlines remain floating civil dates.
- Added an explicit legacy task-deadline preview/migration wizard because historical local-only rows have no trustworthy source timezone.
- Task-specific browser, mobile and Telegram reminders now share the same authoritative `remindAtInstant`.
- Task details expose the original deadline whenever it differs from the current projection.
- Added Flyway V34, API/OpenAPI fields, service/controller/frontend/Telegram tests and a Playwright scenario for `14:10 UTC+5 → 12:10 UTC+3`.
- Regression baseline: 86 Java test classes, 456 `@Test` methods and 20 Playwright scenarios.

# v27.11.3 — Shift Template & Reminder Timezone Hotfix

- Timed shift templates are rebased when the canonical IANA timezone changes, preserving the same real start/end instants for future assignments.
- Built-in and custom timed templates refresh back into the settings form after the authoritative calendar reload.
- Existing dated shifts remain immutable because legacy rows are frozen before template rebasing.
- Shift reminders now use the occurrence `shiftStartInstant`, including projected local date changes across midnight and month boundaries.
- Browser and Telegram delivery consume the same `remindAtInstant`; the legacy wall-clock fallback remains only for unmigrated rows.
- Added service, controller, frontend-contract and Playwright coverage. Flyway remains at V33.
- Regression baseline: 85 Java test classes, 446 `@Test` methods and 19 Playwright scenarios.

# v27.11.2 — E2E Stability Hotfix

- The shift editor Playwright flow now waits for the authoritative `/api/calendar` refresh triggered by assignment before reloading the page, preventing an intentional navigation abort from being reported as `console.error: Failed to fetch`.
- The next-day timezone projection scenario now validates the compact source interval shown by the UI (`03.07 23:00–04.07 07:00`) and the canonical source date (`2026-07-03`) separately.
- No production calculation or database migration changed; Flyway remains at V33.
- Regression baseline remains 85 Java test classes, 442 `@Test` methods and 19 Playwright scenarios.

# v27.11.1 — CI & Contract Hotfix

- обновлены два устаревших frontend-контракта под occurrence-based проекцию смен;
- тест Task Details теперь формирует валидный JSON через ObjectMapper;
- тест legacy-миграции получает гарантированно сохранённые идентификаторы строк;
- production-логика и Flyway V33 не изменялись.

## v27.11.0 — Shift Occurrences & Calendar Projection

- Concrete dated shifts now persist immutable UTC start/end identity and their original IANA timezone.
- Changing the canonical user timezone reprojects existing shifts instead of reinterpreting `08:30` as `08:30` in the new zone.
- Calendar projection can move an occurrence to another local date and split it visually at midnight without duplicating the database row.
- Added safe legacy-shift preview/migration and automatic freezing in the old zone before a timezone change.
- Unrelated day saves no longer silently guess a legacy shift timezone.
- Hardened Service Worker activation so v27.10 Task Details cannot remain hidden behind stale frontend assets.
- Flyway continues with V33.

## v27.10.0 — Task Details

- Added a dedicated read-first task details modal; card clicks no longer throw users directly into editing.
- Added optional multi-line task descriptions with a 4000-character limit and Flyway V32.
- Added owner-scoped `GET /api/tasks/{id}` and `/api/v1/tasks/{id}` endpoints.
- Details expose metadata, description, checklist, dates, reminder and explicit edit/complete/delete actions.
- Checklist items remain interactive from the details view.
- Task board search now includes description text.
- Online details refresh authoritatively while offline details fall back to the loaded calendar/board snapshot.
- Added backend, HTTP, frontend-contract and Playwright coverage.

## v27.9.4 — Overtime Split Projection Contract Hotfix

- Corrected the Playwright expectation for a cross-midnight eight-hour credit: the selected calendar day owns the seven-hour pre-midnight segment while the account balance owns all eight hours.
- Ledger usage references now expose stable `allocationPartIndex` and `allocationPartCount` metadata from the backend.
- Split-part badges render from the paged ledger response even when the full overtime account was not previously loaded into frontend state.
- Added backend and frontend regression assertions for stable `part 1/2` / `part 2/2` rendering.
- No database migration; Flyway remains at V31.

## v27.9.3 — Overtime Preflight Integrity Hotfix

- Usage create/update now validates total requested minutes before mutating a managed entity or inserting a new time-off row.
- Failed over-capacity commands remain side-effect free even inside a wider transaction that catches the domain exception.
- Added regression coverage proving a rejected usage edit keeps its original date, hours, reason and FIFO provenance.
- Updated the task/ledger frontend contract to the intentional `delete entire time-off` wording introduced in v27.9.2.
- Made the overtime modal Playwright scenario deterministic by explicitly setting break and planned deductions to zero.
- No database migration; Flyway remains at V31.

## v27.9.2 — Overtime Ledger Integrity Hotfix

- FIFO replacement is now planned and validated fully in memory before stored allocations are removed.
- Deleting one time-off rebuilds only surviving usages while preserving every overtime credit.
- Post-rebuild invariants verify credit IDs, usage IDs, exact requested minutes and per-credit capacity.
- Ledger rows are rendered atomically through a detached document fragment; a broken allocation cannot leave a partial table.
- Split usages show `part 1/2`, and destructive actions are labelled `delete entire time-off`.
- Added backend, frontend-contract and Playwright regression coverage for two credits, two usages and deleting only one split usage.
- No database migration; Flyway remains at V31.

## v27.9.1 — Overtime Allocation Rendering Hotfix

- Fixed `ReferenceError: formatDate is not defined` while rendering exact cross-midnight FIFO allocations.
- Selected-day rendering no longer aborts after an overtime usage is created, so the calendar highlight and shift details follow the clicked date.
- Exact allocations continue to split cleanly at midnight using the existing `formatDateHuman` helper.
- Expanded the browser regression to create an eight-hour overnight credit, consume it fully and verify both `17:00–24:00` and `00:00–01:00` segments without console errors.
- Added a frontend contract and release-gate runtime smoke for the allocation formatter.
- No database migration or FIFO model changes; Flyway remains at V31.

## v27.9.0 — Overtime Interval Engine

- Replaced floating-point FIFO authority with deterministic integer minutes.
- Added exact source intervals and provenance to overtime allocations.
- Rebuilds the complete FIFO ledger after usage create/update/delete so cancellation restores the same source minutes.
- Added legacy overtime timezone preview/migration wizard without automatic guessing.
- Simplified the user-facing model to one canonical IANA timezone while preserving legacy API fields.
- Existing absolute overtime reprojects when the canonical timezone changes; original source timezone remains recorded.
- Cross-midnight used intervals render as separate calendar-day segments.
- Shift cards now explain net work and break separately.
- Added Flyway V31, API/OpenAPI contracts, backend/frontend regression coverage and release documentation.

## v27.8.1 — Timezone Projection Refresh Hotfix

### Authoritative timezone refresh
- The authenticated profile now loads before the first calendar request, so dated shifts are projected with the persisted work/display zones from the beginning of the session.
- Saving timezone settings forces a cache-bypassing calendar read and replaces the IndexedDB month snapshot instead of repainting a stale source zone first.
- The overtime ledger refreshes together with the calendar because absolute overtime rows use the same display timezone.

### Regression coverage
- Replaced the browser scenario with the real regression path: create a dated shift, change work/display zones, then verify the existing card changes from `08:30 Asia/Yekaterinburg` to `06:30 Europe/Moscow` without retaining `Europe/Kyiv`.
- Added frontend contracts for boot ordering, fresh calendar propagation, snapshot bypass and ledger refresh.
- Baseline: 80 Java test classes / 413 `@Test` methods and 16 Chromium Playwright scenarios. Flyway remains at V30.

## v27.8.0 — Zoned Work Intervals

### Dated shift projections
- Every dated shift with start/end times is resolved through its work IANA timezone into one immutable `startInstant` / `endInstant` pair.
- Day API responses expose work-local and display-local projections, source/display zone identifiers, elapsed/net minutes and midnight-crossing flags.
- Calendar cells and the selected-day panel show the configured display-zone time while preserving the original work-zone range. A display-zone save reloads the active month without rewriting schedule data.

### Absolute overtime identity
- New calculated overtime credits persist `start_at_instant`, `end_at_instant` and `source_timezone` through Flyway V30.
- Duration and overlap protection use real elapsed instants, so DST gaps/overlaps no longer create silent one-hour errors.
- Existing calculated credits retain their source timezone when edited. Saving unchanged fields after changing the account work timezone cannot move the stored interval.
- Historical credits remain legacy-local because their original timezone was never stored; V30 deliberately performs no guessed backfill.

### Interface and compatibility
- Overtime rows prefer display-zone projections and keep the original work range/source zone as secondary context.
- `DayDto` keeps a source-compatible constructor and appends nullable `shiftInterval`; existing floating dates, tasks, notes and important dates remain unchanged.
- Exact interval-slice FIFO provenance is intentionally deferred to Overtime 2.0; current allocations continue consuming hour quantities.

### Regression coverage
- Added work/display shift projection, DST elapsed-duration, absolute overtime persistence, source-zone edit stability, migration and frontend contract tests.
- Added a browser scenario proving `08:30 Asia/Yekaterinburg` renders as `06:30 Europe/Moscow` without changing the work interval.
- Baseline: 79 Java test classes / 409 `@Test` methods and 16 Chromium Playwright scenarios. Flyway ends at V30.

## v27.7.1 — Task & Ledger Layout Hotfix

### Task cards
- Replaced the wrapping day-task flex row with a stable three-column grid: checkbox, flexible body and pinned delete action.
- Kept inline subtasks aligned under the task body and removed the mobile-only offset that could push the checklist outside the card.

### Overtime ledger
- Added an explicit `Действия / Actions` column for credit-level edit and delete controls.
- Rendered each FIFO usage as a structured block and renamed its controls to `ред. списание` / `удалить списание`, making credit actions and usage actions unambiguous.

### Regression coverage
- Added a frontend contract test for the task-card grid, dedicated ledger action column and explicit usage-action labels.
- Baseline: 78 Java test classes / 399 `@Test` methods and 15 Chromium Playwright scenarios.

## v27.7.0 — Time Foundation

### Explicit time semantics
- Split the user time context into persisted IANA `workTimezone` and `displayTimezone` values. Existing accounts inherit their current work timezone as the display timezone, preserving previous behaviour.
- Centralised current time, timezone projection and work-local conversion in `UserTimeService`; legacy helpers remain compatible and explicitly mean work time.
- Documented and tested deterministic DST handling: nonexistent wall-clock times move forward through the gap and ambiguous times use the earlier offset.

### Absolute reminders and delivery identity
- Added one-instant/two-projection reminder fields: `remindAtInstant`, work timezone, display-local value and display timezone.
- Reminder sorting and past filtering now compare `Instant` values instead of server-local date-times.
- Telegram scan windows and new delivery identities now use absolute instants. Flyway V29 adds nullable `remind_at_instant TIMESTAMPTZ`; legacy rows remain local because their original timezone was never stored, and runtime deduplication safely supports both generations.

### Work interval foundation
- Added `WorkIntervalService`, which resolves work-local start/end values into absolute intervals and calculates elapsed/net minutes across midnight and daylight-saving transitions.
- Task overdue rules now use the account's work timezone rather than the operating-system timezone.
- Added authenticated `/api/time/context` and `/api/v1/time/context` endpoints plus work/display timezone fields in mobile user responses.

### Interface and compatibility
- Added separate work/display timezone selectors, a same-as-work shortcut and a two-clock preview. Legacy work-only profile updates keep display coupled until the user explicitly separates the zones.
- Calendar dates continue to use work timezone; absolute synchronization and mobile-session timestamps use the display timezone; legacy Inbox audit timestamps remain local until they gain an explicit instant.
- Floating dates and existing task, shift and overtime records are intentionally not mass-converted in this release.

### Regression coverage
- Added DST gap/overlap, absolute projection, work-interval, profile, notification, Telegram deduplication, API and frontend contract coverage.
- Flyway now ends at V29.
- Baseline: 78 Java test classes / 398 `@Test` methods and 15 Chromium Playwright scenarios.

## v27.6.3 — Polish & Consistency

### Business rules and ordering
- Added central `validateBusinessRules()` validation after all create/update fields are applied.
- A task due date can no longer precede the task date; the same-day deadline remains valid.
- A subtask due date can no longer precede its parent task date.
- Day, range and board responses share one stable open-first comparator; optimistic browser updates use the same rule.

### Lightweight subtask deadlines
- Added nullable date-only `dueDate` to one-level checklist items without turning them into full tasks.
- Flyway migration **V28** adds `task_subtasks.due_date` and an owner-friendly lookup index.
- Updated entity, DTO, create/update reconciliation, OpenAPI and Inbox-to-task compatibility.
- Existing clients remain compatible through legacy DTO constructors; omitted dates are preserved while explicit blank values clear them.

### Task UX polish
- Replaced the text-only checklist badge with an accessible graphical progress bar and numeric value.
- Standardised inline disclosure as `Подзадачи (2/3)` and preserved expansion state during local re-renders.
- Added compact inline subtask dates, denser metadata chips and a consistent icon vocabulary.
- Added a completed-task divider only when open and completed tasks are visible together.
- Improved 320–430 px layouts for long text, date inputs and checklist controls.

### Regression coverage
- Added service and HTTP tests for create/update deadline rules, final-entity validation, open-first sorting, subtask deadline persistence and clearing.
- Extended frontend contracts for V28, client validation, progress semantics and completed grouping.
- Added a Chromium scenario covering invalid deadlines, persisted subtask dates, progress accessibility and immediate completed-task reordering.
- Baseline: 76 Java test classes / 389 `@Test` methods and 15 Chromium Playwright scenarios.

## v27.6.2 — Tasks & Subtasks

### One-level subtasks
- Added ordered one-level subtasks to normal tasks. Recursive nesting is intentionally prohibited so the task editor remains compact and the data model stays predictable.
- Each task can contain up to 50 checklist items with independent text, completion state and explicit order.
- The task editor supports adding, removing and reordering checklist items before save.

### Compact task UX
- Calendar tasks and the task board show only a compact progress badge such as `2/4` by default.
- The checklist expands inline on demand, keeping dense task lists readable on mobile and desktop.
- Individual checklist items can be toggled without opening the full task editor.
- Completing a parent with unfinished checklist items requires explicit confirmation and can atomically complete the remaining items.

### Backend and persistence
- Added the owner-scoped subtask endpoint `PATCH /api/tasks/{taskId}/subtasks/{subtaskId}` and its versioned `/api/v1` alias.
- Task create/update and Inbox-to-task conversion now accept ordered subtasks.
- Flyway migration **V27** creates `task_subtasks` with cascade deletion, stable ordering and a non-negative order constraint.
- Inline child toggles are online-only in this release; full subtask offline synchronization remains part of the planned offline-first architecture.

### Regression coverage
- Added service and controller coverage for creation, ordering, reconciliation, search, ownership isolation and explicit parent completion.
- Added frontend contract checks and a Chromium scenario covering `0/2 → 1/2 → 2/2` completion.
- Baseline: 76 Java test classes / 385 `@Test` methods and 14 Chromium Playwright scenarios.

## v27.6.1 — Quick Capture Polish

### Product UX
- Reframed Inbox as a temporary capture layer instead of a second task list or a separate navigation destination.
- Replaced the large Inbox card above tasks with a compact collapsed tray inside the task board. The tray keeps the open-item counter visible without pushing tasks down the page.
- Removed thought-specific wording from the primary flow; the interface now speaks about entries and things to remember.

### Universal quick add
- The global `+` now opens one draft field directly. Pressing Enter saves the text to Inbox, while Shift+Enter inserts a new line.
- The same draft can prefill a new task, append to today's Markdown note or become the title of a new important date.
- Quick actions are module-aware and appear only for enabled Tasks, Notes, Important dates and Overtime modules.
- Removed the extra quick-capture modal and one unnecessary tap from the mobile path.

### Compatibility and regression coverage
- Existing `/api/inbox`, idempotent offline queue, archive/restore and Inbox-to-task conversion remain unchanged.
- No new database migration is required; Flyway remains V1–V26.
- Updated frontend contract and Playwright coverage for the compact tray and `+ → text → Inbox → task` flow.
- Baseline: 76 Java test classes / 381 `@Test` methods and 13 Chromium Playwright scenarios.

## v27.6.0 — Mobile Tasks & Inbox UX

### Fast capture and Inbox
- Added a user-scoped Inbox for unstructured thoughts with open/archived states, timestamps and one-step conversion into a normal task.
- Inbox creation accepts a client operation id, making offline retries idempotent instead of creating duplicate thoughts after reconnect.
- Added a global mobile `+` action with explicit choices: capture a thought, create a task, add overtime and **Списать переработку**.
- Quick capture requires only text and remains usable offline through the existing IndexedDB operation queue.

### Task UX and metadata
- Removed the large inline task-creation form from the selected-day panel; calendar and task board now open one reusable editor.
- The mobile task editor is full-screen, focuses the task text first and hides optional category, tags, priority, due time and reminders behind a progressive disclosure section.
- Added saved lower-case categories and up to ten normalised tags per task, metadata suggestions and tag-aware board search.
- Task due time remains a native `input type=time`, allowing the operating system picker on mobile devices.

### Backend and persistence
- Added `inbox_items`, `InboxService`, REST endpoints under `/api/inbox` and `/api/v1/inbox`, ownership isolation and task-module guards.
- Added `day_task_tags` as an element collection and a metadata endpoint at `/api/tasks/metadata`.
- Flyway migration **V26** creates Inbox and task-tag storage and normalises existing task categories to lower case.
- Internal task creation now enforces the same 500-character text limit as HTTP validation, including Inbox-to-task conversion.

### Current deployment strategy
- The shared VPS remains a private-beta staging host only. A separate production stack is intentionally deferred until DutyLog has its own stronger server and domain, preserving resource headroom for YARUGA.

### Regression coverage
- Added service, controller and frontend contract coverage for Inbox CRUD, idempotency, ownership, offline capture, conversion, tags and mobile UI.
- Updated Playwright task flows and added an end-to-end quick-capture-to-task scenario.
- Baseline: 76 Java test classes / 381 `@Test` methods and 13 Chromium Playwright scenarios.

## v27.5.2 hotfix — restore drill Flyway boolean check

### Fixed
- Fixed a false-negative restore drill failure after a successful PostgreSQL restore. Concatenating the Flyway `success` boolean into text yields `true`, while the script incorrectly required the standalone psql representation `t`.
- The integrity query now emits the explicit terminal state `success` or `failed`, and the shell check validates that stable value.
- Backup creation, checksum verification, archive restore, temporary-resource cleanup and the live DutyLog database are unchanged.
- When running operational commands interactively, strict shell mode should remain inside the scripts rather than being enabled for the whole SSH login shell; otherwise any expected non-zero script result closes the SSH session.

### Regression coverage
- The release gate now verifies the explicit Flyway state expression and refuses the old fragile `|t` check.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.2 hotfix — Telegram linked-owner fetch

### Fixed
- Confirmed and fixed the production `LazyInitializationException` raised by `/today`, `/tomorrow` and their quick-action aliases after Telegram polling resolved a linked account outside the repository transaction.
- `TelegramLinkRepository.findByTelegramChatId(...)` now fetches the linked `owner` through an entity graph, so the command layer can safely read the persisted IANA timezone after the service transaction closes.
- `/help`, command-menu registration, notifications and write-command validation are unchanged.

### Regression coverage
- Added a real Spring integration test that persists a Telegram link, lets the lookup transaction end, and then reads `AppUser.getWorkTimezone()` from the returned detached account.
- Baseline: 73 Java test classes / 368 `@Test` methods and 12 Chromium Playwright scenarios.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.2 — Telegram command menu and quick actions

### Telegram discoverability
- DutyLog now registers its supported commands through Telegram `setMyCommands`, so `/today`, `/tomorrow`, `/week`, `/tasks`, `/balance`, `/task`, `/done`, `/ppr`, `/timeoff` and `/help` are visible in the chat command menu with short descriptions.
- Command-menu registration starts after application boot and refreshes periodically, so a temporary Telegram outage does not require a redeploy; failures are logged without exposing the bot token.
- Registration can be disabled or rescheduled through environment variables without changing application code.

### Quick actions
- Every bot reply carries a compact persistent Telegram keyboard with `Сегодня`, `Завтра`, `Задачи`, `Баланс`, `Неделя` and `Помощь`.
- Button labels are accepted as first-class command aliases, so the user no longer has to type slash commands manually.
- `/help` explicitly explains that the primary actions are available under the Telegram input field.

### Regression coverage
- Added HTTP-contract tests for `setMyCommands`, command descriptions, retry-safe guards and the persistent reply keyboard payload.
- Added command-service coverage proving every quick-action label dispatches to the same timezone-aware logic as its slash-command equivalent.
- Baseline: 72 Java test classes / 367 `@Test` methods and 12 Chromium Playwright scenarios.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.1 — Telegram commands and mobile sync status bugfix

### Telegram bot
- `/today` and `/tomorrow` now build a best-effort day summary: a failure in shifts, tasks, important dates or overtime no longer makes the whole command disappear.
- A partially available summary explicitly marks the failed section while still returning all data that loaded successfully.
- Unexpected command failures and empty command results now produce a safe Telegram reply instead of silent polling logs; server logs include only the command name, user/chat identifiers and exception type.
- `UserTimeService` is injected into the command service, keeping `/today` and `/tomorrow` aligned with the account's persisted IANA timezone and making the date boundary deterministic in tests.

### Mobile synchronization status
- Fixed the compact header status pill: the later mobile CSS rule no longer forces the long synchronization label onto one unbreakable line.
- Mobile status uses `синхр…` while active, can wrap long cross-tab/pending states, and stays inside the header column.
- Offline synchronization action buttons now allow wrapping inside a `minmax(0, 1fr)` mobile grid.

### Regression coverage
- Added tests for `/tomorrow@BotName`, partial day-summary recovery and safe replies for unexpected linked-command failures.
- Extended the frontend contract for the compact mobile synchronization label and wrapping rules.
- Baseline: 72 Java test classes / 364 `@Test` methods and 12 Chromium Playwright scenarios.
- Flyway remains V1–V25. No schema migration is required.

## v27.5.0 — Backup and recovery hardening

### Deployment bundle hotfix
- Fixed the remote deployment bundle so the host receives `check-backup-freshness.sh`, `restore-drill.sh` and `install-backup-timer.sh` together with the updated backup script.
- Added release checks that fail when any production runtime backup tool is omitted from `remote-deploy.sh`.
- Isolated the intentional missing-configuration CI gate test from the real GitHub job summary, preventing a successful validation job from displaying a false “Deployment configuration is incomplete” warning.
- Application code, database schema and the already-created verified backup are unchanged.

### Backup safety
- `backup-postgres.sh` now defaults to the active `deploy/compose/docker-compose.deploy.yml`, fails closed when `.env` or Compose is missing, validates numeric retention and prevents concurrent writers with `flock`.
- Dumps and SHA-256 files are published atomically with restrictive permissions only after PostgreSQL accepts the custom archive.
- Added `check-backup-freshness.sh` to enforce a maximum backup age, checksum verification and optional live `pg_restore --list` validation.

### Recovery safety
- Real restores require a matching checksum by default and reject unsupported formats before stopping the application.
- Added an EXIT recovery trap: when a restore fails after stopping a running application, DutyLog is started again and the original restore failure remains visible.
- Added `restore-drill.sh` for isolated PostgreSQL 16 recovery exercises with no network, no published ports, table/Flyway verification and exact temporary-resource cleanup.

### Operations
- Added `install-backup-timer.sh` to render and enable environment-specific systemd service/timer units.
- Added `backup-tooling-self-test.sh`; CI verifies backup rotation/checksum, freshness checks, restore failure recovery and systemd unit rendering without touching a real database.
- Documented the successful staging drill, timer installation, retention and the remaining requirement for an off-VPS copy.

### Database
- Flyway remains V1–V25. No schema migration is required.

## v27.4.3 — Reminder timezone and sync UX bugfix

### Fixed
- Task creation and editing now accept any whole-minute reminder offset from 0 to 10080, including 3 minutes; both inputs use `step=1` and the frontend validates an integer value.
- Browser reminders now prefer the backend-provided `remindAtInstant` UTC instant calculated from the user's saved IANA timezone. The existing user-local `remindAt` value remains available for display and Telegram delivery.
- Browser reminder polling now builds its source-date range from DutyLog's selected timezone rather than the operating-system timezone of the current device.
- Changing the saved timezone invalidates the cached browser reminder schedule immediately.
- Removed the duplicate “Короткий интервал” text field from overtime credit creation. New calculations use explicit start/end inputs; historical manual `timeRange` values are preserved while editing old records.
- Manual offline synchronization now shows an accessible live status, disables the button while running and reports completion, no changes, offline state, cross-tab locking or partial failure.

### Regression coverage
- Added backend coverage proving `Asia/Yekaterinburg` local reminder times are serialized to the correct UTC instants.
- Added frontend contracts for arbitrary reminder minutes, absolute browser reminder scheduling, explicit overtime start/end and synchronization feedback.
- Extended task-editor E2E with a 3-minute reminder and updated overtime E2E to use start/end fields.
- Added a browser E2E scenario that observes synchronization progress and its final “No changes” result.

### Baseline
- 72 Java test classes / 362 `@Test` methods.
- 12 Chromium Playwright scenarios.
- PostgreSQL Flyway remains V1–V25; no migration is required.

## v27.4.2 — Timezone simplification and critical regression pack

### Remember-me E2E hotfix
- Fixed the fresh-session bootstrap regression to call `GET /api/calendar` with its required `from` and `to` query parameters.
- The previous test correctly restored authentication but then expected `200` from an intentionally invalid calendar request that returned `400`.
- Production remember-me, calendar API and database behavior are unchanged.

### Changed
- Replaced the manual region name, free-form timezone and Moscow-offset controls with one generated IANA timezone selector and an explicit Save action.
- The selector shows a readable city label and current UTC offset while persisting the canonical IANA identifier such as `Europe/Chisinau`.
- Removed the region/object and manual Moscow-offset fields from the UI; existing stored legacy values are ignored and cleared on the next save.
- Separated timezone controls visually from built-in day/night shift templates without changing shift data or calculation rules.

### Regression coverage
- Added a browser regression that restores authentication in a completely fresh browser context using only the persistent remember-me cookie, verifies parallel PWA bootstrap API calls and proves logout revokes the old cookie.
- Extended task editor coverage to persist category, priority, due date and due time in the single modal.
- Extended shift-type coverage to create, edit, assign and reload a custom shift through the modal manager.
- Updated timezone E2E coverage for the compact selector, explicit save and persistence after reload.
- Extended deployment smoke tests with authenticated read-only profile, module, session and identity API checks.
- Added an HTTPS-only production smoke wrapper; it refuses to run without authenticated smoke credentials.

### Baseline
- 71 Java test classes / 358 `@Test` methods.
- 11 Chromium Playwright scenarios.
- PostgreSQL Flyway remains V1–V25; no migration is required.

## v27.4.1 — Overtime scenario manager

### Changed
- Moved quick-scenario creation, editing and deletion out of Settings and into the shared overtime credit modal.
- Added a scenario dropdown with a dedicated management action and a final “Manage scenarios…” entry.
- Added “Save current values as a scenario”: the editor converts a shift-anchored overtime interval into a reusable scenario draft without discarding the current credit form.
- Scenario management switches views inside the same modal instead of stacking dialogs; returning to the credit editor preserves all entered values.
- Existing scenario CRUD API and stored data remain unchanged; no database migration is required.

### Validation hotfix
- Fixed the new Playwright scenario so it creates a real two-hour post-shift overtime interval instead of trying to save a zero-hour full-shift draft.
- Added an accurate validation message when start/end are present but break and planned norm reduce the overtime total to zero.
- Production API, database schema and scenario persistence are unchanged.

### Tests
- Added frontend contracts proving the Settings card is gone and the single-window manager owns scenario CRUD.
- Added a Playwright flow covering shift assignment, draft creation from a filled overtime form, scenario creation, editing and return to the original credit editor.
- Baseline: 71 Java test classes / 356 `@Test` methods and 10 Playwright scenarios.

## v27.4.0 — Unified overtime editors

### Changed
- Replaced the oversized overtime form inside the selected-day panel with two compact actions: earn overtime and use time off.
- Added the same actions to the overtime ledger so entries can be created from either the calendar context or the account table.
- Added one shared modal editor for overtime credits and one shared modal editor for time-off usages; create and edit now use the same forms.
- Overtime editing from the ledger opens directly in the editor instead of navigating to another month in the calendar.
- Existing quick scenarios are available through a compact dropdown in the overtime editor.
- The selected calendar date is prefilled when an editor is opened from a day; ledger actions use the last selected date or today.
- Added live before/after balance preview for time-off usage and responsive full-height mobile editors.

### Tests
- Added frontend contract coverage for the compact calendar controls, shared modals and direct ledger editing.
- Added a Playwright scenario covering create, use and edit flows from both calendar and ledger entry points.

## v27.3.1 — Stable browser session and editor modals

- Replaced rotating persistent remember-me tokens with a stable, fixed-expiry token service so parallel PWA bootstrap requests can no longer invalidate each other after a browser close or application restart.
- Added a regression that reuses the same remember-me cookie for multiple restored requests, matching the browser's real parallel startup behavior.
- Replaced the task edit prompt chain with one structured modal containing text, date, category, priority, due date/time and reminder controls.
- Moved shift-type creation and editing out of Settings into a dedicated manager opened by the `+` chip in the selected-day calendar panel.
- The shift manager now handles built-in and custom types in one place, including time, break, norm, color and per-shift notification settings.
- Removed the obsolete Shift types card and navigation item from Settings, reducing settings density without deleting any shift data.
- Added Java frontend contracts and a Playwright flow covering both modal editors.

## v27.3.0 — Important dates, user timezone and precise overtime editing

### CI test stabilization hotfix

- Injected `UserTimeService` into Telegram notification delivery instead of constructing a private clock source inside the service.
- Made Telegram scheduler tests deterministic with a fixed user-local timestamp, so UTC GitHub runners do not compare reminders against the default Moscow timezone.
- Updated the frontend timezone contract to assert the actual profile `PUT /api/profile` request rather than a nonexistent `api.updateProfile` helper.
- Production reminder timing, database schema and public API behavior are unchanged.

- moved important dates out of Settings into a dedicated top-level workspace with search, filters, create, edit, delete and jump-to-calendar actions;
- persisted each user's validated IANA work timezone in PostgreSQL through Flyway V25 and the Profile API;
- synchronized timezone selection between devices and applied user-local time to notification filtering and Telegram commands/delivery;
- made Edit in the overtime ledger open the correct calendar month, day and overtime form automatically;
- added a return-to-ledger action and exact row highlighting by credit/usage id instead of highlighting every row on the same date;
- kept the existing selected-day important-date quick form for fast entry.

## v27.2.33 — Persistent login, shift reassign and compact mobile UX

### E2E stability hotfix

- The mobile layout scenario now onboards with the Full preset because it explicitly tests the Tasks navigation; the previous Standard preset intentionally kept Tasks disabled and made the selector correctly hidden.
- Module toggle tests now start waiting for the final “modules saved” state before clicking and match the final message exactly, preventing the 1.8-second transient status from being missed on fast runners.
- No production application, database or Flyway behavior changed.

### CI registry hotfix

- Replaced environment-local package tarball URLs in `package-lock.json` with public `registry.npmjs.org` URLs.
- Switched CI and staging validation from `npm install` to reproducible `npm ci`.
- Added npm registry pinning, dependency cache and bounded retries for transient registry failures.
- Added release-gate checks that reject internal-only package registry URLs.

- Fixed a full-day snapshot race where a debounced note save could restore a shift immediately after the user deleted it; writes are now serialized per date and stale responses cannot overwrite newer local revisions.
- Day upsert handling now accepts an intentionally empty successful response when deleting the final value from a date, so the UI does not report a JSON parse failure after a successful delete.
- Added explicit persistent browser login with a 30-day `DUTYLOG_REMEMBER_ME` HttpOnly cookie, JDBC-backed token storage and logout/password/role-change revocation.
- Added PostgreSQL Flyway migration `V24__persistent_web_login.sql`; local H2 test/dev schema is represented by the matching JPA entity.
- Added compact mobile headers, collapsible task/overtime filters, horizontally scrollable stat chips and hidden one-page pagers.
- The selected-day mobile sheet now sits above the app shell and hides the fixed bottom navigation, preventing controls from being covered.
- Added Java remember-me integration coverage and Playwright regressions for delete/reassign during a pending note save and compact mobile behavior.
- Baseline: 66 Java test classes / 342 `@Test` methods and 6 Playwright scenarios.

## v27.2.32 — Pipefail-safe authenticated smoke-test hotfix

- Fixed staging deployment exit `141` during the authenticated app-shell probe.
- Removed producer-to-`grep -q` pipelines from `smoke-test.sh`; under `set -o pipefail`, an early successful match could terminate the producer with SIGPIPE and falsely fail a healthy deployment.
- Added a large multiline app-shell regression fixture that reproduces the former failure deterministically.
- Kept the CSRF-aware login, secure-cookie loopback handling and protected asset checks from v27.2.31.

## v27.2.31 — Authenticated deployment smoke-test hotfix

- Fixed the first real staging deployment failure: the smoke test no longer tries to download the protected application shell anonymously.
- Browser navigation to `/` is verified with `Accept: text/html` and must redirect to `/login.html`; API-style anonymous requests may continue to receive JSON `401`.
- Deployment smoke tests now authenticate with the bootstrap administrator through the real CSRF-protected `/perform_login` flow, keep cookies in a permission-restricted temporary directory and verify the versioned app shell only after login.
- Deployment and rollback paths fail closed when authenticated smoke credentials are missing or rejected.
- Added a local HTTP regression harness that proves valid credentials pass, invalid credentials fail and passwords are not printed.
- Application features, Flyway V1–V23, 340 Java tests and 5 Playwright scenarios are unchanged from v27.2.30.

## v27.2.30 — Host nginx CI/CD deployment hardening

- Active staging/production delivery now uses the VPS-wide system nginx instead of a shared Caddy container.
- DutyLog publishes only to `127.0.0.1`, with staging on `18082` and production on `18083`; deployment preflight rejects any non-loopback bind address.
- Removed the external `dutylog_edge` network and Caddy dependency from the active Compose/bootstrap path while keeping legacy examples as optional references.
- Added a full loopback smoke test before the public HTTPS smoke test, making container failures distinguishable from DNS/TLS/nginx failures.
- Added configurable Docker memory/PID limits and JSON log rotation for the shared 2 GiB VPS.
- Added concrete nginx/Certbot templates for `stage.yaruga-trophy.ru` and `dutylog.yaruga-trophy.ru`, with forwarding headers overwritten at the trusted edge.
- Updated CI/CD, staging, production and VPS runbooks for the real YARUGA + DutyLog shared-host topology.
- Application behavior, Java test baseline (340) and Playwright baseline (5) are unchanged from v27.2.29.

## v27.2.29 — Final security and product audit hardening

- Browser sessions now carry an `auth_version`; password resets and role changes invalidate cached `JSESSIONID` authorities on the next request.
- Normal password changes enforce the same 8-character minimum as registration; administrators remain at 12 characters and bootstrap credentials at 20.
- Authentication rate limiting and `SECURITY_AUDIT` no longer trust forwarding headers unless the managed proxy mode is enabled; supplied nginx/Caddy configs overwrite client-supplied IP headers.
- PostgreSQL backups and checksums are created under `umask 077` with `0700` directories and `0600` files.
- Expired mobile authentication-token rows are cleaned on a bounded retention schedule.
- Added integration and unit regressions for stale web sessions, proxy-header spoofing, auth-version changes and token cleanup.
- Flyway migration chain is now V1–V23. Baseline: 65 Java test classes / 340 `@Test` methods and 5 Playwright scenarios.

## v27.2.28 — Staging deployment gate and diagnostics hardening

- Split staging delivery into validation, immutable image build/clean-PostgreSQL verification and a separate remote deployment job.
- Staging now runs the same Maven `verify`, JaCoCo floor, release gate and Playwright browser suite before an image can be built for deployment.
- Added the GitHub Environment switch `DUTYLOG_DEPLOY_ENABLED`. When it is absent or false, the workflow stays green after building and verifying the immutable image, clearly records that remote deployment was skipped and never creates a `staging-tested-tree-*` promotion tag.
- Added fail-fast CI deployment configuration validation that reports missing variable/secret names without printing secret values and validates HTTPS, SSH port, path, user and key shape.
- Production remains fail-closed and now uses the same explicit preflight before touching GHCR or the server.
- Improved `remote-deploy.sh` diagnostics so all missing inputs are reported together.
- Backend behavior, database schema, Flyway migrations, 327 Java tests and 5 Playwright scenarios are unchanged.

## v27.2.27 — Playwright marker accordion hotfix

- Fixed the remaining calendar-persistence E2E failure: the custom marker input lives inside the closed Marker `<details>` section, so the scenario now expands `data-day-module="core"` before filling `#dayEmojiCustom`.
- The authoritative reload assertion now explicitly reopens both Notes and Marker sections before checking their persisted controls.
- The PWA/offline scenario is already green; no production, database or Flyway behavior changed.
- Java baseline remains 61 classes / 327 `@Test` methods; browser baseline remains 5 Playwright tests.

## v27.2.26 — Playwright selector, accordion and line-ending hotfix

- Fixed the calendar persistence E2E contract: selected shift chips now expose `aria-pressed="true"` instead of relying only on inline colors, and the test asserts that accessible state.
- Added a reusable `openDayModule` Playwright helper so note scenarios expand the closed `<details>` accordion before filling `#noteEdit`.
- Updated both calendar persistence and PWA offline scenarios to open the Notes module explicitly before waiting for the debounced day save.
- Added `.gitattributes` with repository-wide LF normalization, CRLF only for Windows command files and binary exclusions.
- The Java baseline remains 61 classes / 327 `@Test` methods; the browser baseline remains 5 Playwright tests.
- Backend behavior, database schema and Flyway migrations are unchanged.

## v27.2.25 — Playwright browser E2E regression baseline

- Added a real Chromium E2E layer for registration, login-language persistence, first-run onboarding, calendar data persistence, module disable/enable survival, task completion, mobile viewport usability and PWA offline startup.
- Added automatic detection of browser `console.error`, uncaught page errors, failed same-origin requests and unexpected happy-path HTTP `4xx/5xx` responses.
- Added stable non-visual DOM contracts for calendar dates, shift chips and task rows.
- Added an isolated `application-e2e.properties` profile using in-memory H2 on port 4173; it never touches the local file database and keeps external Telegram traffic disabled.
- GitHub Actions now installs Chromium, runs Playwright before building the deployment image and uploads traces/screenshots/videos on failure.
- Added npm Dependabot coverage and Playwright usage documentation.
- Java/JUnit baseline remains 61 classes and 327 `@Test` methods; browser baseline adds 5 Playwright tests.
- Database schema and Flyway migrations are unchanged.

## v27.2.24 — Coverage floor and startup/module regression suite

- Added direct startup coverage for bootstrap-admin configuration, credential validation, account creation, promotion, optional forced password reset and one-time legacy-admin cleanup.
- Added module registry/service coverage for normalization, immutable contracts, unique keys/orders, acyclic dependencies, locked modules, admin visibility, unknown persisted keys and dependency activation.
- Added current-user resolution and extended note-export coverage for count/select races, blank-note filtering, audit events, ZIP structure and YAML escaping.
- JaCoCo now fails `mvn verify` when bundle instruction coverage drops below 88% or branch coverage drops below 70%.
- The suite now contains 61 test classes and 327 `@Test` methods.
- Production API behavior, database schema and Flyway migrations are unchanged.

## v27.2.23 — Security test contract and secret-safe error logging hotfix

- Исправлены два чрезмерно строгих теста Content-Type: `application/json;charset=UTF-8` теперь корректно принимается как JSON.
- Browser redirect contract теперь отправляет `Accept: text/html`, как настоящий браузер, и не смешивает HTML-навигацию с JSON API channel.
- `ApiExceptionHandler` больше не логирует throwable целиком для неожиданных ошибок: в журнал попадают request ID, method, path и безопасное имя класса исключения.
- Добавлена регрессия, запрещающая утечку текста исключения и throwable stack в production error log.
- Production API envelope, база данных и Flyway не менялись.

## v27.2.22 — Security infrastructure regression and auth hardening suite

- Added direct coverage for API version/deprecation headers, browser security headers, request correlation IDs, Bearer authentication, authentication rate limiting, structured security audit logs and stable API error envelopes.
- Added MockMvc coverage for integrated security boundaries: public headers, mobile/web 401 responses, admin 403 responses, request-id propagation and mixed-case Bearer handling.
- Bearer authentication schemes are now recognized case-insensitively, including repeated whitespace, and the web CSRF bearer matcher uses the same parser.
- Web, legacy mobile and Android v1 login aliases now share one per-IP rate-limit bucket; web and Android registrations share a separate registration bucket.
- Expanded the regression baseline to 57 test classes and 300 `@Test` methods.
- No database schema changed.

## v27.2.21 — Telegram date validation and test harness hotfix

- Fixed Telegram task date parsing so impossible calendar dates such as `31.02` are normalized to the stable `BAD_REQUEST` `ApiException` contract instead of leaking `DateTimeException`.
- Corrected `TelegramBotServiceTest` to register all `MockRestServiceServer` expectations before the first HTTP request; the previous test attempted to add an expectation after execution had already started.
- Added release guards for both regressions.
- Production behavior changed only for malformed Telegram dates; database schema and Flyway migrations are unchanged.
- The suite remains 50 test classes and 254 `@Test` methods.

## v27.2.20 — Telegram bot regression and delivery hardening suite

- Added unit coverage for Telegram command parsing, aliases, task creation/completion, manual and interval overtime, time-off, summaries and invalid input.
- Added HTTP-client coverage for bot polling, one-time link codes, unlinked chats, command replies, update offsets, malformed updates and overlapping-poll protection.
- Added notification-delivery coverage for due windows, per-user failure isolation, deduplication, retry semantics and every reminder message type.
- Added MockMvc coverage for the browser Telegram API, module guards, link-code status, notification settings, unlink cleanup, authentication and CSRF.
- Telegram sends now fail closed: empty responses and `ok=false` are never recorded as successful deliveries.
- Telegram HTTP errors now redact the bot token before they are written to application logs, and updates without a chat id are ignored safely.
- The suite now contains 50 test classes and 254 `@Test` methods.
- No database schema changed.

## v27.2.19 — PostgreSQL migration and CI version hotfix

- Fixed the clean PostgreSQL Flyway chain: `V7__notification_settings.sql` referenced the nonexistent table `app_users`; the canonical table created by `V1__init.sql` is `users`.
- Added `PostgreSqlMigrationContractTest`, which scans migrations in order and rejects foreign keys targeting tables that have not been created by the same or an earlier migration.
- Removed the stale hard-coded `27.2.9` build/release metadata from CI, staging and production workflows. GitHub Actions now resolves the semantic version directly from `pom.xml` and passes it through immutable image and deployment metadata.
- Added release-gate checks for the corrected notification-settings foreign key and dynamic workflow version propagation.
- The suite now contains 46 test classes and 224 `@Test` methods.
- This corrects a pre-production migration that could never succeed on a clean PostgreSQL database; no new Flyway version was added.

## v27.2.18 — Mobile auth and sync lifecycle regression suite

- Added service-level coverage for mobile login, hashed token storage, access/refresh expiry, refresh rotation, logout, device normalization, session activity, owner isolation and revoke-all behaviour.
- Added MockMvc coverage for both legacy and `/api/v1/mobile/auth` login, refresh, logout, session listing and session revocation routes.
- Added service and HTTP coverage for Android v1 idempotency, owner-scoped operation ids, optimistic version conflicts, no-op rejection, module-scoped failures, clear precedence and versioned tombstones.
- Fixed batch isolation for malformed day dates: an invalid date is now returned as a per-operation `REJECTED` result and no longer aborts valid neighbouring operations.
- Added validation guards for structurally malformed direct service operations and preserved legacy mobile clear/delete semantics alongside v1 tombstones.
- Corrected the documented baseline count: v27.2.17 contains 193, not 194, `@Test` methods. The suite now contains 45 classes and 223 `@Test` methods.
- No database schema changed.

## v27.2.17 — Admin test context bootstrap hotfix

- Fixed `UserAdminServiceTest` so it no longer supplies only `dutylog.admin.username` to the full Spring context.
- The incomplete bootstrap pair correctly triggered the production safety guard requiring username and password together, which prevented the test `ApplicationContext` from loading and cascaded into dozens of red test results.
- The test now constructs `UserAdminService` with an explicit bootstrap-admin name while leaving the application bootstrap listener unconfigured, avoiding startup side effects and still testing bootstrap-admin protections.
- Added release guards preventing the incomplete test property from returning.
- No production behavior or database schema changed; the suite remains 41 classes and 193 `@Test` methods.

## v27.2.16 — Profile and administration regression suite

- Added complete profile HTTP coverage for safe defaults, display name and birthday persistence, locale, onboarding, theme preferences, accent normalization and the allow-listed Theme Builder configuration.
- Added malformed/corrupt theme coverage, value clamping, authentication and CSRF boundaries, and guards proving that password hashes and arbitrary CSS-like keys are never returned.
- Added browser-facing mobile-session coverage for owner-only listing, one-session revocation, CSRF, IDOR-safe `404` responses and revoke-all behavior after a password change.
- Added registration-setting service coverage for default/database sources, audit metadata and legacy boolean spellings.
- Added administrative service and MockMvc coverage for search, role filters, pagination, bootstrap/current-user flags, promotion, safe demotion, last-admin protection, password reset, mobile-session revocation and registration toggling.
- Fixed `SystemController` expected client errors to use the stable `ApiException` envelope instead of `ResponseStatusException`, which could be swallowed by the generic advice and returned as `500 INTERNAL_ERROR`.
- Expanded the regression baseline to 41 test classes and 193 `@Test` methods.
- No database schema changed.

## v27.2.15 — Structured module-disabled error envelope hotfix

- Fixed the stable API error contract for disabled modules: `MODULE_DISABLED` responses now include a structured `moduleKey` field instead of forcing clients to parse `message` or the legacy `error` alias.
- Kept the legacy `MODULE_DISABLED:<key>` message for backward compatibility.
- Updated the PWA client to prefer `body.moduleKey` while retaining the legacy marker fallback for older servers.
- Documented `moduleKey` in the OpenAPI `ApiError` schema and module/API documentation.
- Strengthened task, important-date, notification, quick-scenario and overtime module-guard tests around the structured field.
- No database schema changed.

## v27.2.14 — Quick scenarios and overtime API regression suite

- Restored the missing `java.util.Map` and `java.util.stream.Collectors` imports in `ShiftTypeServiceTest` from the verified local correction.
- Added service-level coverage for quick-scenario default seeding, one-time deletion semantics, safe defaults, complete updates, optional-field clearing, FIXED_TIME consistency, owner isolation and stable errors.
- Added MockMvc coverage for legacy and `/api/v1/quick-scenarios` CRUD, validation envelopes, malformed bodies, module guards, CSRF, authentication and IDOR boundaries.
- Added overtime query/export coverage for open/partial/closed filters, date/search filters, safe pagination, CSV BOM and escaping, XLS HTML escaping, FIFO reallocation after usage updates, deletion rules and owner isolation.
- Added MockMvc coverage for legacy and `/api/v1/overtime` credit/usage CRUD, FIFO allocations, account pages, CSV/XLS exports, validation, module guards, CSRF, authentication and foreign IDs.
- Expanded the regression baseline to 36 test classes and 166 `@Test` methods.
- No production behaviour or database schema changed.

## v27.2.13 — Shift types and calendar patterns regression suite

- Added service-level coverage for built-in shift seeding, legacy-default repair, custom shift CRUD, optional time/reminder clearing, protected built-in identity and owner isolation.
- Added deletion coverage proving that removing a custom shift deletes shift-only rows but preserves notes, emoji, overtime and time-off on non-empty days.
- Added schedule-pattern coverage for 2/2, day/night/48 and weekday-rotated five-day weeks across month, year and leap-day boundaries.
- Added overwrite coverage proving that bulk fill changes only the shift while preserving day metadata, and that overwrite=false keeps existing shifts while filling empty dates.
- Added MockMvc coverage for `/api/days/fill`, `/api/v1/days/fill`, `/api/shift-types` and `/api/v1/shift-types`, including validation envelopes, CSRF, authentication and ownership boundaries.
- Added frontend contract guards for every schedule preset and the selected-weekday rotation used by the five-day template.
- Expanded the regression baseline to 32 test classes and 141 `@Test` methods.
- No production behaviour or database schema changed.

## v27.2.12 — Important dates regression suite

- Added service-level coverage for important-day defaults, owner-scoped ordering, full updates, deletion and stable error handling.
- Added recurrence coverage for one-time dates, monthly end-of-month clamping, yearly leap-day fallback and leap-year restoration.
- Added deterministic occurrence ordering by date and title, plus owner isolation and no-duplicate source-event checks.
- Added MockMvc coverage for legacy and `/api/v1/important-days` aliases, full CRUD, occurrences, validation envelopes, malformed JSON, missing parameters, CSRF, authentication, module guards and ownership boundaries.
- Extended the regression baseline and release gate with the Important dates contracts.
- No production behaviour or database schema changed.

## v27.2.11 — Task priority regression test correction

- Corrected the task regression suite: `URGENT` is a supported `TaskPriority`, so it must not be used as an invalid-filter example.
- The negative validation assertion now uses the genuinely unsupported value `critical`.
- Added a positive regression assertion proving that the case-insensitive `urgent` board filter returns URGENT tasks.
- No production behaviour or database schema changed.

## v27.2.10 — Task board status validation hotfix

- Fixed task-board status validation so an unknown status is rejected even when the user has no tasks.
- Moved validation before the repository stream instead of relying on a per-task filter side effect.
- The existing service and MockMvc regression tests now guard the empty-board case that exposed the defect.
- No database schema changed.

## v27.2.9 — Task regression suite

- Added service-level task coverage for creation defaults, trimming, updates, reminder cleanup, day/range lists, board statuses, category/priority/search/date filters, pagination, validation and deletion.
- Added MockMvc coverage for legacy and `/api/v1/tasks` aliases, complete CRUD, board metadata, stable error envelopes, CSRF, authentication, module guards, data preservation while disabled and owner isolation.
- Documented why IntelliJ JUnit runs do not generate JaCoCo and how to run `mvn clean verify` from IntelliJ's Maven tool window or a Windows terminal.
- No production behaviour or database schema changed.

## v27.2.8 — Test compilation hotfix

- Fixed invalid Java character literals in `CalendarMonthReloadContractTest`: Java strings now use escaped double quotes.
- Added release-check guards for the exact browser-cache assertions so this contract test cannot silently become uncompilable again.
- No production behaviour or database schema changed.

## v27.2.7 — Regression test baseline and notification poll shutdown

- Stopped the browser notification interval immediately when the Notifications module is disabled.
- Added defensive recovery for a stale frontend module map: one raced `MODULE_DISABLED:notifications` response stops polling and resynchronizes module metadata instead of repeating every ten seconds.
- Prevented an in-flight reminder response from being delivered after the module is switched off.
- Preserved structured `code` and `moduleKey` metadata on frontend API errors.
- Added behavioural backend coverage for shift, task, important-day and digest reminder calculations, completed-task filtering and user isolation.
- Added notification API boundary, module dependency, task reminder persistence and frontend scheduler contract tests.
- CI now runs `mvn verify` and publishes a JaCoCo HTML coverage report as a build artifact.
- Added a regression test matrix that maps the successful manual pass to automated guards.

## v27.2.6 — Module-isolated day saves and browser reminders

- Fixed the `Minimum` preset regression: shift, marker and note writes no longer fail with `MODULE_DISABLED:overtime` merely because the web snapshot contains neutral overtime values.
- Disabled Notes/Overtime modules are now read-only for day updates: hidden data stays in the database and is not exposed in the response until the module is enabled again.
- The web client omits optional module fields from `PUT /api/days/{date}` when their modules are disabled.
- Added a running-page/PWA browser notification scheduler with deduplication, a five-minute wake-up grace window and service-worker notification click handling.
- Task reminder controls are disabled with an explanation while the Notifications module is off.
- Prevented an early Telegram status request from generating a misleading `403` before module metadata finishes loading.
- Added `DayModuleIsolationTest` for old-client neutral payloads, hidden-data preservation and real-write rejection.

## v27.2.5 — Calendar day identity hotfix

- Preserve `date` and sync metadata in module-aware day sanitization.
- Prevent calendar rows from collapsing into `state.days[undefined]`.
- Treat missing static resources as 404 instead of 500.

# v27.2.5 — Calendar day identity hotfix

- Bulk schedule rows are saved explicitly and verified by a fresh database read before the endpoint reports success.
- Calendar reload after fill bypasses IndexedDB and browser HTTP cache.
- Calendar responses are marked `Cache-Control: no-store`.
- Added end-to-end MockMvc coverage for POST fill followed by GET calendar with all 31 dates.
- Preserved stale-response and month-specific snapshot guards from the earlier calendar fixes.

## v27.1.0 — Android API contract freeze

- Added stable `/api/v1/**` aliases and a dedicated Bearer-only `/api/v1/mobile/**` contract for Android.
- Added mobile registration that returns the first access/refresh token pair immediately.
- Standardized API failures as machine-readable envelopes with `code`, `message`, `fields`, `requestId` and `timestamp` while retaining legacy `error`; malformed parameters and unexpected controller failures use the same envelope.
- Added optimistic `version`/`updatedAt` fields to day records and Flyway migration `V22__android_api_contract.sql`; version `0` is reserved for a missing row and persisted rows start at `1`.
- Added durable per-user idempotency records for offline sync operations without storing note/task payloads.
- Added per-operation sync results: `APPLIED`, `ALREADY_APPLIED`, `CONFLICT` and `REJECTED`, including explicit `NO_CHANGES` rejection for empty mutations.
- Added version-conflict protection via `baseVersion` and preserved empty version rows as lightweight tombstones.
- Added the canonical OpenAPI file at `/openapi/dutylog-v1.yaml` and contract documentation/tests.
- Added `X-DutyLog-Api-Version: v1` and deprecation headers for legacy `/api/mobile/**` routes.
- Throttled mobile-token `lastUsedAt` writes to once per five minutes and bounded idempotency retention to 90 days by default.
- Kept web/PWA endpoints backward compatible; no frontend product feature was added.

## v27.0-rc4 — Security consolidation

- Added bounded streaming export of all owner-scoped notes as Obsidian-friendly Markdown ZIP.
- Escaped YAML front matter, disabled response caching and documented export availability when Notes UI is disabled.
- Split `/api/mobile/**` into a stateless Bearer-only security chain; browser sessions can no longer authenticate mobile endpoints.
- Added explicit mobile-boundary, export and expanded IDOR regression tests with exact 404 assertions.
- Added structured security events for login/authz/token/Telegram/admin-reset actions and bounded rolling production logs.
- Added application-level authentication rate limiting shared by Caddy and nginx deployments.
- Closed public registration by default in production and raised normal password minimum to 8 characters.
- Removed `script-src 'unsafe-inline'` by extracting login JavaScript; synchronized CSP/HSTS headers across Spring, Caddy and nginx.
- Added Dependabot for Maven, GitHub Actions and Docker, and made the runtime container non-root.
- Synchronized runtime, service worker, docs, smoke checks and package layout on `27.0-rc4`.

## v27.0-rc1 — Release Candidate

- Froze v26.6.12 as the UX-polished release-candidate baseline.
- Bumped app, service worker, static cache-busting, smoke-test and documentation version to `27.0-rc1`.
- Added release-candidate documentation: production deploy guide, backup/restore guide, user guide and RC checklist.
- Kept feature freeze: no new product features, only release documentation and final static guardrails.
- CI and local release gate are expected to pass before deploying this candidate.

## v26.6.12 — notifications alignment and admin navigation hotfix

- Pinned notification status chips to the right side of the notifications settings header so RU/EN labels no longer drift into the middle of the card.
- Made the browser-permission chip and active notification settings feel less disabled by using clearer active-state styling.
- Added a settings-style side navigation to the administrator view: Users, Registration and Diagnostics.
- Added release-check guardrails for the notification header layout and admin navigation.


## v26.6.11 — UI alignment and registration test hotfix

- Fixed `RegistrationTest` compilation after the login-language hotfix: the JSON body helper now supports `languagePreference`.
- Kept settings header controls pinned on the right across RU/EN so autosave, browser-permission chips and the profile avatar do not drift when labels change length.
- Documented the Java 25 Tomcat Native warning as a local runtime warning, not an application startup failure.
- Added release-check guardrails for the helper overload and right-side settings alignment.


## v26.6.10 — login language registration hotfix

- Registration now preserves the language selected on the login page.
- `login.html` sends `languagePreference` during account creation.
- New users created with EN selected land in the English onboarding/app shell instead of falling back to RU.
- Added registration language persistence test.
- Release/check docs updated to v26.6.10.

## v26.6.9 — English i18n polish hotfix

- Fixed Russian leftovers after switching the interface to English: today marker, calendar total label, working-time box, autosave chip and notification browser status.
- Re-render settings panels when language changes so dynamic cards do not keep stale Russian text.
- Localized built-in shift names (`Дневная`, `Ночная`, `Выходной`) in calendar chips, selected-day summaries and shift settings while keeping custom shift names unchanged.
- Localized quick-scenario hints, notification empty states and common admin/status strings.
- Added CSS language-specific text for the current-day marker.
- Added release-check guardrails for the i18n hotfix.

## v26.6.8 — today clarity and dismissible hidden-blocks hotfix

- Made the current-day calendar marker visually different from the selected-day outline: today now uses a subtle tinted/dashed cell, corner dot and text label, while the clicked day keeps the strong solid accent outline.
- Added a close button to the selected-day `Скрытые блоки` notice.
- The hidden-blocks notice dismissal is stored in localStorage and stays hidden on future visits for that browser.
- Kept `Настроить модули` available while the notice is visible, but removed the always-on nagging after dismissal.
- Added release-check guardrails for the dismissible notice and less-confusing today styling.

## v26.6.7 — onboarding and today highlight hotfix

- Renamed the onboarding preset `Работа + переработки` to the shorter `Стандарт`.
- The selected onboarding preset is now highlighted dynamically; choosing `Минимум`, `Стандарт` or `Всё включить` updates the active pill immediately.
- Manual module changes clear the preset highlight unless the selection exactly matches a preset again.
- Made the current day in the calendar more visible with a dedicated highlighted cell style, not only a small text label.
- Added release-check guardrails for the onboarding preset state and today-cell highlight.

## v26.6.6 — CI permission hotfix

- Fixed GitHub Actions release gate startup on checkouts where shell scripts lose executable bit.
- CI now runs `bash ./deploy/scripts/release-check.sh` instead of executing the script directly.
- Added `docs/CI_PERMISSION_HOTFIX.md` and release-check guardrails for the CI invocation.

## v26.6.5 — properties and tests hotfix

- Replaced Cyrillic comments in `.properties` files with ASCII English comments to avoid mojibake in editors/terminals with the wrong file encoding.
- Added `spring.jpa.open-in-view=false` to test properties to match the application baseline and remove the test warning.
- Fixed Telegram link tests after the Telegram module guard: tests now enable the Telegram module explicitly where linking is expected.
- Fixed the expired Telegram-code test fixture to use the real `DL-123456` code format.
- Updated the registration CSRF regression to expect Spring Security's `403 Forbidden` for missing CSRF.
- Fixed module dependency semantics: disabling a module now disables dependent modules too, so disabling Overtime also disables Scenarios and mobile sync guards stay effective.
- Added release-check guardrails for ASCII `.properties`, module dependency cascade and the updated test expectations.

## v26.6.4 — console and module details UX hotfix

- Hid module contract counts and technical details from regular users; they remain visible only to administrators.
- Stopped admin registration/user-list requests from running during generic settings initialization, removing avoidable `403` console noise for non-admin sessions.
- Fixed `renderTimeSettings()` shadowing the translation helper `t()`, which caused a settings-route runtime error.
- Added release-check guardrails for the settings-time shadowing bug, admin auto-fetch noise, and admin-only module developer details.

## v26.6.3 — compact modules UX hotfix

- Made the modules settings screen more compact: shorter cards, independent grid row height and no oversized stretch from disabled module copy.
- Moved module runtime/API/offline contract details behind collapsed technical details.
- Shortened disabled-module copy while keeping the data-preservation message.
- Fixed module badge/text overlap in narrow cards, including the Admin module card.
- Reworked the selected-day hidden-blocks notice so the “Configure modules” button no longer collides with the text.

## v26.6.2 — frontend runtime hotfix

- Fixed boot-time helper ordering in split JS: `$` and `esc()` are now defined before `applyAppearance(loadLocalAppearance())`.
- Fixed the blank calendar/tabs regression caused by `ReferenceError: esc is not defined` and cascading `$ is not defined` errors.
- Hardened the service worker against unsupported request schemes such as `chrome-extension://` and made runtime cache writes non-fatal.
- Added release-check guardrails for boot helper order and unsupported service-worker cache writes.

## v26.6.1 — UX boot hotfix

- Fixed a release UX blocker where the PWA could remain visually stuck on the boot overlay.
- Boot overlay is no longer shipped as the initial static body state.
- Added startup failsafe and error/rejection handlers to unlock the interface if boot fails.
- Added release-check guardrail against reintroducing initial `appBooting` body state.

# v26.6 — UX release polish

- Kept feature freeze: no new large product scope.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.6`.
- Added a visible boot/loading state while the PWA prepares profile, modules and the calendar.
- Added calendar, task board and overtime ledger loading skeletons so navigation does not feel frozen on slow links.
- Reworked empty states for tasks, important days and the overtime ledger into compact explanatory cards.
- Polished module settings with enabled/disabled/basic counters and explicit disabled-module copy explaining that data is preserved.
- Added `docs/UX_RELEASE_POLISH.md` and updated release checklist commands for the v26.6 tag.

# v26.5 — Security review

- Kept release stabilization freeze: no new user-facing features.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.5`.
- Added application-level security headers through `SecurityHeadersFilter` so the baseline browser policy is present even before/without reverse-proxy hardening.
- Added production session cookie hardening: `HttpOnly`, `Secure`, `SameSite=Lax` and explicit session timeout.
- Closed a module-boundary gap in `/api/mobile/sync`: mobile day sync can no longer write notes or overtime fields when the corresponding modules are disabled.
- Closed a Telegram module-boundary gap: pending Telegram link codes no longer link an account after the user disables the Telegram module.
- Added `ModuleSecurityTest` regressions for mobile sync module guards and browser security headers.
- Extended `release-check.sh` with security review guardrails for headers, session cookie settings, mobile module guards, Telegram module guards and regression tests.
- Added `docs/SECURITY_REVIEW.md` with the reviewed threat boundaries and follow-up policy.

# v26.4 — Code cleanup

- Kept release stabilization freeze: no new user-facing features.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.4`.
- Cleaned split frontend file headers so they describe the current ordered-script runtime instead of the old monolithic `app.js` history.
- Removed stale runtime comments that still pointed to `app.js` as the active frontend entrypoint.
- Hardened `deploy/scripts/release-check.sh` with exact split-JS order validation and a legacy runtime `app.js` reference guard.
- Refactored `deploy/scripts/smoke-test.sh` and `release-check.sh` to use an explicit `STATIC_JS` list instead of repeating split asset names in scattered checks.
- Added `docs/CODE_CLEANUP.md` with stabilization-safe cleanup rules and postponed technical debt.

# v26.3 — Release hardening

- Entered release stabilization: no new user-facing feature scope in this release.
- Bumped frontend/backend/service-worker/smoke-test versions to `26.3`.
- Fixed admin system status version source: `/api/admin/status` now uses `info.app.version` instead of hardcoded `26.0`.
- Added `deploy/scripts/release-check.sh` as a local release gate for version consistency, frontend checks, Flyway migration sequence, shell syntax, Java brace balance and production config safety.
- CI now runs `mvn test` and then the same release gate.
- Hardened production preflight: stricter domain validation, distinct secret checks, public app-port check and Caddy security header check.
- Added HSTS and a basic CSP to Caddy examples.
- Updated release, production launch and runbook docs for the current split-frontend release.

# v26.2 — Tests, CI and frontend split

- Split the former `app.js` into ordered static JS files under `static/js/`.
- Added GitHub Actions CI with Maven tests and frontend static checks.
- Added service and web regression tests for calendar, overtime, Telegram linking, registration, profile password and admin access.
- Added nginx auth rate-limit example and Caddy warning.
- Updated smoke-test for split frontend assets.

- Added first-run module onboarding for new users.
- New users choose a calmer module set before landing in the full interface.
- Added module presets: minimum, work + overtime, enable all.
- Added per-module onboarding toggles powered by the existing module registry.
- Added `users.onboarding_completed` via `V21__user_onboarding.sql`; existing users are marked completed by default during upgrade.
- Profile API now exposes and accepts `onboardingCompleted`.
- Onboarding saves module choices through `PATCH /api/modules`; skipped onboarding keeps current/default module settings.
- Added `docs/ONBOARDING.md`.
- Frontend/backend/service-worker versions bumped to `26.2`.

# v25.3 — Developer module contracts

- Added backend package `ru.daniil.shifts.module` with stable `ModuleKeys`, `ModuleCategory`, `ModuleContract` and canonical `DutyLogModules` registry.
- `ModuleService` now uses the contract registry instead of local ad-hoc definitions while preserving existing `ModuleService.*` constants for controllers.
- Module API payload now includes contract metadata: category, display order, UI slots, API prefixes and offline queue types.
- Added `GET /api/modules/contracts` for clients/tests that explicitly need module contract metadata.
- Module settings UI now shows module category and contract summary, and dependency names are rendered as user-facing module titles.
- Added `docs/MODULE_CONTRACTS.md` and updated module/API documentation.
- Frontend/backend/service-worker versions bumped to `25.3`.

# v25.2 — Module-aware offline snapshot

- Calendar snapshot responses now include the effective module list for the current user.
- Calendar aggregation respects disabled modules server-side: disabled tasks, important dates, overtime, notifications and scenarios are omitted from the bundle.
- Disabled notes/overtime fields are stripped from day entries in the calendar bundle, so offline cache does not expose hidden modules.
- IndexedDB snapshots are sanitized according to enabled modules before being written.
- Offline queue refuses new mutations that belong to disabled modules and moves stale disabled-module mutations to failed operations instead of replaying them.
- Offline diagnostics/export now include module snapshot information.
- Frontend/backend/service-worker versions bumped to `25.2`.

# v25.1 — Module-aware day panel

- Selected-day panel sections now have explicit module ownership.
- Disabled modules hide their day panel blocks through one module-aware registry instead of scattered ad-hoc toggles.
- Calendar day markers now respect modules: notes, tasks, important dates, overtime and reminders no longer show when their module is off.
- Day panel rendering skips disabled module renderers and avoids touching their controls.
- Added an inline day-panel hint when blocks are hidden by disabled modules; data is not deleted and modules can be re-enabled in settings.
- Fixed the note preview tab handler shadowing the translation function.
- Frontend/backend/service-worker versions bumped to `25.1`.

# v25.0 — User modules

- Added modular-monolith foundation: backend module registry plus per-user module switches.
- Added `user_module_settings` table via `V20__user_modules.sql`.
- Added `GET /api/modules` and `PATCH /api/modules`.
- Added Settings → Modules UI with safe toggles, module descriptions and dependency hints.
- Disabled modules are hidden from main navigation, selected-day panel and related settings sections.
- Major feature APIs now return `403 MODULE_DISABLED:<key>` when the module is disabled.
- Disabling a module does not delete user data; it only hides and guards the feature.
- Frontend/backend/service-worker versions bumped to `25.0`.

# v24.0.4 — i18n full coverage polish

- Expanded English translations across the web/PWA UI: day panel, overtime, tasks, shift settings, scenarios, notifications, admin, diagnostics, offline sync and common dynamic statuses.
- Added dynamic translation helpers for counters, page ranges, statuses, sync messages, confirmation text and calculated labels.
- Added English CSS pseudo-content for offline-only warnings.
- Bumped frontend/backend/service-worker runtime version to `24.0.4`.

# v24.0.2 — I18N coverage polish

- Expanded English translation coverage for profile, password, active sessions, Telegram linking, settings hints and diagnostics copy.
- Dynamic profile/session/Telegram messages now use the app dictionary instead of hardcoded Russian strings.
- Frontend/backend/service-worker versions bumped to `24.0.2`.

# v24.0.1 — Shift settings UX and important-day refresh

- The `+` shift button in the selected-day panel now opens Settings directly on the expanded `Shifts` section, scrolls to it and focuses the custom shift name field.
- Important-day deletion now refreshes the selected-day summary immediately, including the `0 important days` state, without requiring a browser reload.
- Custom shift form copy clarified: `Calendar, h` is only a short calendar label, while `Norm, h` is used for overtime calculations. Empty norm is auto-calculated from start/end/break.
- Frontend/backend/service-worker versions bumped to `24.0.1`.

# v24.0 — Notification status polish

- Notification settings header now uses compact status chips instead of raw text like `0 шт · браузер: разрешено`.
- Reminder count and browser permission status are visually aligned with theme/admin/time status chips.
- Frontend/backend/service-worker versions bumped to `24.0`.

# v23.1.5 — Offline sync copy and compile polish

- Added missing `PostMapping` import in `SystemController` for the admin password reset endpoint.
- Renamed confusing offline sync UI copy: `Повторить ошибки` is now `Повторить неудачные операции`.
- Renamed related failed-operation actions so the dialog talks about operations, not “repeating errors”.
- Frontend/backend/service-worker versions bumped to `23.1.5`.

# v23.1.4 — Telegram tasks pagination compile fix

- Fixed Telegram `/tasks` command after `TaskService.listBoard(...)` was changed to return a paged result.
- Telegram now requests the first page of open tasks and uses the page total for the summary.
- Frontend/backend/service-worker versions bumped to `23.1.4`.

# v23.1.3 — Large list pagination hardening

- Admin users list now loads by pages instead of sending all users to the browser.
- Global task board now uses backend response pagination with page size capped at 100.
- Overtime ledger table now requests a paged ledger response; CSV/XLS export remains full for the selected filters.
- Added pager controls and page-size selectors for users, tasks and overtime ledger.
- Frontend/backend/service-worker versions bumped to `23.1.3`.

# v23.1.2 — Spring parameter binding fix

- Исправлен runtime-крэш Spring MVC: `Name for argument ... not specified` при запросах вроде `/api/calendar` и `/api/tasks/board`.
- Во всех контроллерах явно указаны имена `@RequestParam` и `@PathVariable`, чтобы приложение не зависело от reflection parameter names при сборке из IntelliJ/IDEA.
- В `pom.xml` добавлен `maven-compiler-plugin` с `<parameters>true</parameters>` как дополнительная страховка для Maven-сборки.
- Frontend/backend/service-worker версии подняты до `23.1.2`.

# v23.1.1 — Visual polish

- Theme summary in `Внешний вид` redesigned from raw text into compact chips: preset, base mode and accent color.
- `Автосохранение` in time settings is now shown as a status badge integrated into the card header.
- Users/roles admin header now shows clean metric chips: `Пользователей` and `Админов`, fixing the broken line wrap.
- Frontend/backend/service-worker versions bumped to `23.1.1`.

# v23.1 — Theme Builder

- Расширен раздел `Внешний вид` до безопасного Theme Builder без доступа к CSS.
- Добавлены пресеты: `DutyLog Default`, `Midnight`, `OLED Black`, `Forest`, `Sunset`, `Industrial`, `Soft Purple` и режим `Custom`.
- Добавлена точная настройка через контролы: фон приложения, цвет карточек, внутренние блоки, текст, вторичный текст, границы, стиль кнопок, стиль карточек, тени, плотность и скругление.
- Добавлен live preview прямо в настройках: календарные клетки, карточка и кнопки показывают изменения до сохранения.
- Настройки темы сохраняются в профиле пользователя как безопасный JSON `theme_config`, содержащий только whitelist-поля, не пользовательский CSS.
- В БД добавлены `users.theme_preset` и `users.theme_config` через миграцию `V18__theme_builder.sql`.
- Backend валидирует значения Theme Builder: только `#RRGGBB`, разрешённые enum-значения и ограниченный диапазон скругления.
- Роли `USER/ADMIN`, будущий `account_tier` `FREE/PAID/VIP` и внешний вид остаются отдельными слоями.
- Emoji-маркеры дней из v23.0 сохранены; картинки/стикерпаки/загрузка файлов по-прежнему не добавлялись.
- Frontend/backend/service-worker версии подняты до `23.1`.

# v22.3 — Users and roles admin panel

- Добавлен админский список пользователей в разделе `Система` → `Пользователи и роли`.
- Администратор видит логин, отображаемое имя, роль, отметку `env admin`, текущего пользователя и read-only тариф `FREE` как задел под будущие `PAID/VIP`.
- Добавлены admin endpoints `GET /api/admin/users`, `PATCH /api/admin/users/{id}/role`, `POST /api/admin/users/{id}/password`.
- Роли пока строго ограничены `USER` и `ADMIN`; публичная регистрация по-прежнему создаёт только `USER`.
- Стартовый env-админ остаётся защищённым: его нельзя понизить до `USER`, а собственную активную админскую роль нельзя снять из UI.
- Постоянное демоутирование всех “неожиданных” админов заменено на одноразовую cleanup-миграцию при первом старте v22.3: дальше дополнительных админов можно легально назначать из админки.
- Пароль bootstrap-админа больше не перезатирается env-паролем при каждом рестарте. Env-пароль используется для первого создания; аварийный сброс возможен через `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true`.
- Добавлен админский сброс пароля пользователя с отзывом мобильных токенов.
- В БД добавлены `users.account_tier`, `users.created_at`, `users.updated_at` через миграцию `V16__user_admin_management.sql`.
- Диагностика администратора теперь показывает количество пользователей, количество админов, разрешённые роли и зарезервированные будущие тарифы.
- Версии frontend/backend/smoke-test подняты до `22.3`.

# v22.2 — Registration hardening

- Публичная регистрация теперь управляется из админского раздела `Система`, а не только через deployment-конфиг.
- Добавлена системная настройка `registration.enabled` в БД (`app_settings`) и миграция `V15__app_settings.sql`.
- Backend строго проверяет настройку при `POST /api/auth/register`: если регистрация закрыта, даже прямой запрос получает `403`.
- Добавлен публичный endpoint `GET /api/auth/registration-status`, чтобы страница входа могла скрывать форму регистрации без авторизации.
- Добавлены admin endpoints `GET/PATCH /api/admin/settings/registration` для чтения и изменения настройки регистрации.
- В админском разделе добавлена карточка `Публичная регистрация` с переключателем и текущим статусом.
- Диагностика администратора теперь показывает состояние публичной регистрации и источник настройки.
- Страница входа скрывает вкладку регистрации, если админ закрыл публичную регистрацию.
- Стартовый админ по-прежнему создаётся только через env bootstrap; отдельной UI-регистрации администраторов нет.
- Версии frontend/backend/smoke-test подняты до `22.2`.

# v22.1 — Secure admin bootstrap

- Удалён риск “кто первый зарегистрировался — тот админ”: публичная регистрация теперь всегда создаёт пользователя с ролью `USER`.
- Добавлен backend bootstrap администратора через переменные окружения `DUTYLOG_ADMIN_USERNAME` и `DUTYLOG_ADMIN_PASSWORD`. При старте приложение создаёт такого пользователя или повышает существующего до `ADMIN`, обновляет пароль и демоутит неожиданные `ADMIN`-аккаунты до `USER`.
- Стартовый набор смен вынесен в `DefaultShiftSeedService`, чтобы его получали и обычные регистрации, и создаваемый bootstrap-админ.
- Spring Security `UserDetailsService` теперь отражает роль из БД: администратор получает `ROLE_ADMIN`, обычный пользователь — `ROLE_USER`.
- `docker-compose.prod.yml`, `.env.production.example`, `.env.example` и `application*.properties` обновлены под явный admin bootstrap.
- `deploy/scripts/check-production-env.sh` теперь требует явные admin-переменные и проверяет длину/формат без вывода секретов.
- Добавлен `docs/ADMIN_BOOTSTRAP.md` и обновлены production-документы/чеклисты: первый администратор больше не зависит от порядка регистрации.
- Версии frontend/backend/smoke-test подняты до `22.1`.

# v22.0 — Production launch

- Добавлен `docs/PRODUCTION_LAUNCH.md`: короткий боевой сценарий первого VPS-запуска от `.env` до smoke test, backup и проверки PWA на телефоне.
- Добавлен `deploy/scripts/check-production-env.sh`: preflight production-конфигурации без вывода секретов. Проверяет `.env`, домен, пароли, Telegram-настройки, Caddyfile, compose и базовые команды.
- Усилен `deploy/scripts/smoke-test.sh`: теперь проверяются health, login page, app shell, manifest, service worker версии `v22.0`, static assets и защищённый admin API.
- Обновлены `README.md`, `docs/DEPLOY.md`, `docs/PRODUCTION_RUNBOOK.md`, `docs/VPS_CHECKLIST.md`, `docs/SECURITY_CHECKLIST.md`, `docs/BACKUP.md` и `docs/RELEASE_CHECKLIST.md` под первый production launch.
- Добавлена metadata для `/actuator/info`: имя приложения, версия `22.0`, текущий тип клиента `web/PWA inside Spring Boot monolith`.
- Frontend-кэш поднят до `v22.0`: `app.css?v=22.0`, `app.js?v=22.0`, `dutylog-shell-v22.0`.
- Backend-версия в админской диагностике поднята до `22.0`.
- Product scope не расширялся: offline scope остался прежним, native mobile-приложение в релиз не добавлялось.

# v21.2 — Offline QA and release candidate

- Добавлена пользовательская диагностика оффлайна прямо в панели синхронизации: online/offline, доступность IndexedDB, возраст snapshot, очередь, ошибки и состояние sync-lock.
- Добавлена кнопка `Скопировать диагностику`, чтобы быстро снять безопасный отчёт по web/PWA-клиенту без серверной админки.
- Диагностические отчёты явно фиксируют текущую платформу: web/PWA внутри Spring Boot-монолита; отдельного native mobile-приложения пока нет.
- Добавлен `docs/RELEASE_CHECKLIST.md` с ручным чеклистом перед релизом, offline QA, проверкой PWA и подготовкой к VPS-деплою.
- Обновлена offline-документация: v21.2 считается release candidate, offline scope не расширен.
- Исправлена мелкая фронтовая опечатка в `dataLayer.loadCalendar`: лишняя вложенная проверка `navigator.onLine` после чтения snapshot.
- Frontend-кэш поднят до `v21.2`: `app.css?v=21.2`, `app.js?v=21.2`, `dutylog-shell-v21.2`.
- Backend-версия в диагностике поднята до `21.2`.

# v21.1 — Offline hardening

- Добавлена подробная панель синхронизации из индикатора в шапке: ожидающие операции, неудачные операции, последняя синхронизация и состояние подключения.
- Добавлена ручная синхронизация из панели, массовый повтор неудачных операций, повтор одной неудачной операции и удаление неудачной операции из списка.
- Добавлен emergency export локального offline-состояния в JSON: `app`, `version`, `snapshot`, `queue`, `failed`, `meta` и сведения браузера.
- Добавлена защита от двух вкладок: синхронизация берёт короткий lock в `localStorage`, чтобы очередь не проигрывалась параллельно.
- Индикатор теперь показывает устаревшие данные, если последний локальный снимок старше суток.
- Ошибки оффлайн-операций стали точнее: для переработок, графиков, смен, уведомлений, Telegram, профиля и важных дат объясняется, почему нужна связь с сервером.
- Обновлены тексты offline-плашек в интерфейсе.
- Frontend-кэш поднят до `v21.1`: `app.css?v=21.1`, `app.js?v=21.1`, `dutylog-shell-v21.1`.
- Backend-версия в диагностике поднята до `21.1`.

# v21.0 — Offline Mode / local-first lite

- Добавлен фронтовый offline-слой без переписывания backend: `dataLayer`, IndexedDB и очередь синхронизации.
- Приложение сохраняет последний снимок календаря в локальную базу `dutylog-offline` и может открыть его без сети.
- В шапку добавлен индикатор подключения: онлайн/оффлайн, время последней синхронизации и счётчик неотправленных изменений.
- Оффлайн поддержаны безопасные операции:
  - смена выбранного дня;
  - заметка выбранного дня;
  - установка состояния задачи `done: true/false`.
- Изменения применяются в UI оптимистично, сохраняются в IndexedDB и отправляются на сервер при появлении сети или по клику на индикатор.
- Очередь проигрывается FIFO; сетевые ошибки оставляют операцию в очереди, а 400/404/409 переносятся в список «не применилось».
- Service Worker не участвует в хранении данных: данные лежат только в IndexedDB/dataLayer, оболочка остаётся network-first.
- Сложные операции — переработки, списания, смены, сценарии, уведомления, Telegram, профиль — при отсутствии сети получают понятный отказ: «Эта операция требует связи с сервером».
- Добавлена документация `docs/OFFLINE_MODE.md`.
- Frontend-кэш поднят до `v21.0`: `app.css?v=21.0`, `app.js?v=21.0`, `dutylog-shell-v21.0`.
- Backend-версия в диагностике поднята до `21.0`.

# v20.8 — Production launch hardening

- Добавлен `docker-compose.prod.yml` для VPS: PostgreSQL, DutyLog app и Caddy reverse proxy в одной production-схеме.
- Добавлен `.env.production.example` с production-переменными и безопасными placeholder-значениями.
- Добавлен `deploy/caddy/Caddyfile.example` для HTTPS через Caddy.
- Добавлен альтернативный пример nginx: `deploy/nginx/dutylog.conf.example`.
- Исправлен `Dockerfile` после переименования проекта: теперь копируется `dutylog-*.jar`, а не старый `shift-calendar-*.jar`.
- В runtime-образ добавлен `curl`, чтобы healthcheck контейнера мог проверять `/actuator/health`.
- Добавлен Docker `HEALTHCHECK` и healthcheck app-сервиса в compose.
- Добавлен `deploy/scripts/smoke-test.sh` для быстрой проверки health/login/manifest/protected API после запуска.
- Добавлены документы:
  - `docs/PRODUCTION_RUNBOOK.md`;
  - `docs/SECURITY_CHECKLIST.md`.
- Обновлены `README.md`, `docs/DEPLOY.md`, `docs/VPS_CHECKLIST.md`, `docs/ARCHITECTURE.md` и `docs/API.md`.
- Frontend-кэш поднят до `v20.8`: `app.css?v=20.8`, `app.js?v=20.8`, `dutylog-shell-v20.8`.
- Backend-версия в диагностике поднята до `20.8`.

# v20.7 — Backup and restore

- Добавлены production-ready скрипты для PostgreSQL: `deploy/scripts/backup-postgres.sh`, `deploy/scripts/restore-postgres.sh`, `deploy/scripts/list-backups.sh`.
- Backup теперь создаётся в PostgreSQL custom format `.dump` с `--no-owner`, `--no-privileges` и checksum `.sha256`, если доступен `sha256sum`.
- Restore поддерживает `.dump`, `.dump.gz`, `.sql`, `.sql.gz`, спрашивает подтверждение и останавливает app-контейнер перед восстановлением.
- Добавлены переменные `.env.example` для backup-скриптов: `BACKUP_DIR`, `BACKUP_KEEP_LAST`, `DUTYLOG_DB_SERVICE`, `DUTYLOG_APP_SERVICE`.
- Добавлены systemd-примеры ежедневного backup: `deploy/systemd/dutylog-backup.service.example`, `deploy/systemd/dutylog-backup.timer.example`.
- Добавлены документы `docs/BACKUP.md`, `docs/DEPLOY.md`, `docs/VPS_CHECKLIST.md`.
- README обновлён: backup/restore, безопасная остановка Docker, документация для VPS.
- Frontend-кэш поднят до `v20.7`: `app.css?v=20.7`, `app.js?v=20.7`, `dutylog-shell-v20.7`.
- Backend-версия в диагностике поднята до `20.7`.

# v20.6 — Admin diagnostics profile

- Служебная диагностика вынесена из обычных пользовательских настроек в отдельный раздел `Система`.
- Кнопка `Система` появляется в шапке только у администратора.
- Добавлена роль пользователя `ADMIN/USER`; первый пользователь существующей установки автоматически получает `ADMIN` через миграцию `V14__admin_role_and_diagnostics.sql`.
- Новые установки назначают первого зарегистрированного пользователя администратором, остальные пользователи создаются обычными.
- Endpoint диагностики перенесён с `GET /api/system/status` на `GET /api/admin/status` и теперь возвращает данные только администратору.
- Вкладка `⚙` очищена от технической диагностики и снова выглядит как пользовательские настройки.
- Frontend-кэш поднят до `v20.6`: `app.css?v=20.6`, `app.js?v=20.6`, `dutylog-shell-v20.6`.
- Backend-версия в диагностике поднята до `20.6`.

# v20.5 — Product copy and architecture cleanup

- Пользовательские тексты в настройках приведены к product-ready стилю: меньше внутренних формулировок, больше понятных пользовательских подсказок.
- В интерфейсе заменены технические слова `backend/frontend` на `сервер/интерфейс` там, где это видит пользователь.
- Раздел диагностики стал понятнее: `Кэш приложения`, `Защита сессии`, `Версия сервера`, безопасный отчёт без секретов.
- README переписан как продуктовая документация: возможности, стек, запуск, production, Telegram, безопасность и ссылки на документы.
- Добавлен `docs/ARCHITECTURE.md` с описанием слоёв, модулей, границ web/mobile API и правил изменений.
- Добавлен `docs/PRODUCT_COPY.md` со стилем текстов интерфейса и словарём терминов.
- Frontend-кэш поднят до `v20.5`: `app.css?v=20.5`, `app.js?v=20.5`, `dutylog-shell-v20.5`.
- Backend-версия в диагностике поднята до `20.5`.

# v20.4 — Settings navigation and accordion

- Вкладка `⚙` больше не выглядит длинной простынёй: добавлена layout-обёртка `settingsShell` с навигацией и контентом.
- Меню настроек стало полноценным: Профиль, Время, Смены, Сценарии, Уведомления, Важные даты, Диагностика.
- На десктопе меню закреплено сбоку, на узких экранах превращается в горизонтальную прокручиваемую панель.
- Все большие настройки стали аккордеоном: карточки можно открывать и сворачивать, а при выборе пункта меню открывается только нужная секция.
- Добавлены кнопки `развернуть всё` и `свернуть всё`.
- Последний открытый раздел сохраняется в `localStorage`, чтобы настройки продолжались с того места, где пользователь остановился.
- Frontend-кэш поднят до `v20.4`: `app.css?v=20.4`, `app.js?v=20.4`, `dutylog-shell-v20.4`.

# v20.3 — Diagnostics and settings polish

- Добавлено внутреннее оглавление настроек, чтобы вкладка `⚙` не превращалась в длинную простыню.
- Секция `Время и регион` упрощена: главный сценарий теперь `Сохранить и применить к сменам`, а изменения дефолтов дневной/ночной смены автосохраняются и автоматически применяются к встроенным сменам после короткой паузы.
- Добавлена карточка `Диагностика`: frontend/backend version, Service Worker, браузер, CSRF-cookie, серверное время, активные профили Spring, состояние БД и Telegram.
- Добавлен endpoint `GET /api/system/status`; он требует web-сессию и не отдаёт секреты.
- Добавлен `RequestDiagnosticsFilter`: request-id, метод, путь, статус и длительность запросов пишутся в логи для `/api/**`, `/actuator/**`, login/logout.
- Добавлена настройка логирования `DUTYLOG_REQUEST_LOG_LEVEL`.
- Добавлен `docs/GIT_WORKFLOW.md`: как жить с Git, тегами, откатами и как не удалить данные PostgreSQL.
- Frontend-кэш поднят до `v20.3`: `app.css?v=20.3`, `app.js?v=20.3`, `dutylog-shell-v20.3`.

# v20.2.1 — Header avatar spacing fix

- Исправлен версточный баг шапки: аватар профиля больше не прижимается к стрелке месяца на узких экранах.
- Для шапки добавлены нормальные gap-отступы, а блок пользователя на мобильном уходит вправо через `margin-left:auto`.
- Имя пользователя в шапке на маленьких экранах теперь аккуратно обрезается, а не ломает ряд.
- Frontend-кэш поднят до `v20.2.1`: `app.css?v=20.2.1`, `app.js?v=20.2.1`, `dutylog-shell-v20.2.1`.

# v20.2 — Telegram quick actions

- Telegram-бот получил первые изменяющие команды поверх уже существующих сервисов DutyLog.
- Добавление задач: `/task текст`, `/task завтра текст`, `/task 2026-07-10 текст`, `/задача ...`.
- Закрытие задач: `/done 12`, `/готово 12`, `/закрыть 12`; `/tasks` теперь показывает id задач.
- Начисление переработки: `/ppr 17-08 причина`, `/ppr 10.07 17-20 причина`, `/ppr 2 причина`.
- Для интервальной переработки поддержаны токены `обед60` и `план8/план0`; расчёт идёт через существующий `OvertimeService`, включая разбиение ночей и защиту от пересечений.
- Списание отгула: `/timeoff 8 причина`, `/отгул завтра 8 причина`, `/списать 2026-07-10 4 причина`; FIFO остаётся на стороне `OvertimeService`.
- Ошибки доменной валидации теперь возвращаются пользователю в Telegram-сообщении, а не только пишутся в лог.
- Frontend-кэш поднят до `v20.2`: `app.css?v=20.2`, `app.js?v=20.2`, `dutylog-shell-v20.2`.

# v20.1 — Telegram notifications

- Telegram теперь умеет не только отвечать на команды, но и сам отправлять напоминания.
- Добавлена миграция `V13__telegram_notifications.sql`: флаг `telegram_links.notifications_enabled` и таблица `telegram_notification_deliveries` для защиты от повторной отправки одного и того же напоминания.
- Scheduler берёт уже рассчитанные backend-напоминания из `NotificationService`: смены, задачи, важные дни и вечерний дайджест.
- Отправка в Telegram использует те же настройки, что и веб-уведомления: включение смен, задач, важных дней, дайджеста и времена напоминаний.
- В блок Telegram во вкладке `⚙` добавлен переключатель `присылать напоминания в Telegram`.
- Добавлен endpoint `PATCH /api/telegram/settings` для web-safe настройки Telegram-привязки.
- Добавлены env-настройки `DUTYLOG_TELEGRAM_NOTIFICATIONS_ENABLED`, `DUTYLOG_TELEGRAM_NOTIFICATION_SCAN_DELAY_MS`, `DUTYLOG_TELEGRAM_NOTIFICATION_LOOKBACK_MINUTES`, `DUTYLOG_TELEGRAM_NOTIFICATION_LOOKAHEAD_MINUTES`.
- `TelegramBotService.sendMessage` теперь возвращает статус отправки, чтобы не отмечать неотправленное напоминание как доставленное.
- Frontend-кэш поднят до `v20.1`: `app.css?v=20.1`, `app.js?v=20.1`, `dutylog-shell-v20.1`.

# v20.0 — Telegram foundation

- Telegram-бот поселён внутри текущего Spring Boot backend, не отдельным сервисом.
- Добавлены сущности и таблицы `telegram_links` и `telegram_link_codes` + миграция `V12__telegram_foundation.sql`.
- Во вкладке `⚙` в профиле появился блок Telegram: статус, создание кода привязки, отключение Telegram и список первых команд.
- Привязка работает через одноразовый код: создать в DutyLog → отправить боту `/start DL-123456`.
- Добавлены web endpoint’ы `/api/telegram/status`, `/api/telegram/link-code`, `/api/telegram/link`. Они защищены web-сессией и CSRF.
- Добавлен long polling для Telegram Bot API, выключен по умолчанию и включается env-переменными.
- Первые команды: `/today`, `/tomorrow`, `/week`, `/tasks`, `/balance`, `/help`, плюс русские алиасы `/сегодня`, `/завтра`, `/неделя`, `/задачи`, `/баланс`.
- Бот пока read-only: показывает смены, задачи, важные дни и баланс переработок, но ещё не создаёт задачи/переработки.
- Добавлены env-настройки `DUTYLOG_TELEGRAM_*` в `.env.example`, `docker-compose.yml`, `application.properties` и `application-prod.properties`.
- Frontend-кэш поднят до `v20.0`: `app.css?v=20.0`, `app.js?v=20.0`, `dutylog-shell-v20.0`.

# v19.10.2-branding

- Приложение переименовано в **DutyLog: Time & Overtime**.
- Обновлены: `index.html`, `login.html`, `manifest.json`, тестовое уведомление, README, CHANGES и API-документация.
- `app.css` и `app.js` подключаются как `?v=19.10.2`.
- Service worker получил новый cache name: `dutylog-shell-v19.10.2`.
- Maven `artifactId` переименован в `dutylog`, display-name проекта — `DutyLog: Time & Overtime`.
- Технические имена БД/пакетов сохранены: `shift_calendar`, `ru.daniil.shifts`, endpoint'ы `/api/shift-types` и т.п. не переименовывались, чтобы не ломать миграции, API и существующие данные.

## v19.10.1 — cleanup после слияния

- Корневая папка архива в v19.10.1 была приведена к `v19.10.1/shift-calendar`.
- Свежие изменения `v19.5–v19.10` перенесены в `CHANGES.md`, чтобы журнал релизов жил рядом с проектом.
- Убрана устаревшая документация о том, что CSRF отключён: web-cookie интерфейс теперь работает с `XSRF-TOKEN`/`X-XSRF-TOKEN`, а `/api/mobile/**` остаётся stateless под Bearer.
- `index.html` подключает `app.css` и `app.js` с версией `?v=19.10.1`.
- Service worker обновлён до `dutylog-shell-v19.10.2`; JS/CSS теперь network-first, чтобы свежий HTML не получал старый кэшированный frontend.
- Web UI профиля больше не вызывает `/api/mobile/auth/sessions`; добавлены CSRF-защищённые web endpoint’ы `/api/profile/sessions`.

## v19.10 — профиль

- Добавлен профиль во вкладке `⚙`: отображаемое имя, день рождения, аватар-инициалы в шапке.
- В день рождения пользователя календарь показывает поздравительный баннер.
- Добавлена смена пароля с проверкой текущего пароля.
- После смены пароля все мобильные сессии отзываются.
- Добавлен список мобильных устройств/сессий с возможностью отзыва.
- Добавлена миграция `V11__user_profile.sql`.

## v19.9.1 — освобождение из заметочного плена

- Исправлены случаи, когда скрытые fullscreen/overlay-блоки оставались видимыми из-за конфликта `hidden` и `display:flex`.
- Добавлен глобальный предохранитель для похожих скрытых панелей.

## v19.9 — полноэкранные Markdown-заметки

- Добавлен fullscreen-режим заметки в стиле Obsidian: редактор + живое превью.
- Работает `Esc` для выхода.
- `Tab` вставляет отступ в редакторе.
- Сохранение заметки идёт через существующий пайплайн автосохранения дня.

## v19.8 — polish нативных контролов

- Добавлен `color-scheme: dark` на корне, чтобы нативные поля/селекты не становились белыми в тёмной теме.
- Доработаны стили пресетов уведомлений, кнопок важных дней и чекбоксов.

## v19.7 — фиксы секции времени и региона

- Дописаны стили секции `Время и регион`.
- Исправлен `var(--txt)` и связанные визуальные проблемы полей.

## v19.6 — регистрация и service worker

- Исправлен сценарий, когда service worker отдавал старый `login.html` из кэша и регистрация ловила 401/проблемы с cookie.
- HTML переведён на network-first.
- В `login.html` добавлен `ensureCsrf()` как страховка перед отправкой формы/регистрацией.

## v19.5 — безопасность и тесты

- Включена CSRF-защита web-интерфейса через cookie `XSRF-TOKEN` и заголовок `X-XSRF-TOKEN`.
- `/api/mobile/**` оставлен stateless для Bearer API и исключён из CSRF.
- `docker-compose.yml` стал fail-hard по паролям: без `.env` приложение не должно тихо стартовать с дефолтными секретами.
- Добавлены 7 тестов `OvertimeService`, покрывающих ночи, сутки, пересечения, FIFO, списание больше доступного, редактирование и удаление списаний.

## v19.4 — время и регион

- Добавлена секция `Время и регион` во вкладке `⚙`.
- Можно указать рабочий регион/объект, рабочий часовой пояс и пометку сдвига от Москвы.
- Добавлен предпросмотр текущего времени в рабочем часовом поясе и в часовом поясе браузера.
- Добавлены дефолты дневной и ночной смены: начало, конец, обед и плановые часы.
- Дефолты можно сохранить локально в браузере.
- Дефолты можно применить к встроенным сменам `Дневная` и `Ночная`, чтобы уведомления, быстрые сценарии и кнопка `план по смене` брали актуальное время.
- Добавлены кнопки переноса дефолтов дневной/ночной смены в форму создания кастомной смены.
- Backend не менялся; это фронтовая настройка и удобный способ обновить существующие типы смен через уже имеющийся API.


## v19.3 — мобильная полировка

- Добавлена нижняя мобильная навигация для вкладок.
- Панель выбранного дня на телефоне превращена в нижнюю шторку с затемнением фона.
- Увеличены кликабельные зоны кнопок, чипов, вкладок и полей под палец.
- Уменьшены отступы и размеры карточек календаря на маленьких экранах.
- Улучшена адаптивность фильтров, форм переработок, задач, уведомлений и настроек.
- При переходе с календаря на другие вкладки панель дня закрывается, чтобы не висела поверх интерфейса.
- Backend не менялся.

# v19.1 — полировка настроек

- Backend не тронут; изменения только во фронте.
- Панель выбранного дня разгружена: создание и настройка смен переехали во вкладку `⚙`.
- Плюсик в списке смен теперь открывает настройки смен, а не раскрывает форму прямо в панели дня.
- Редактор пользовательских быстрых сценариев переехал во вкладку `⚙`; в панели дня остались только карточки применения сценариев.
- Создание важных дней переехало во вкладку `⚙`; в панели дня остался список важных событий выбранной даты.
- Для важных дней добавлено отдельное поле даты и быстрые кнопки `выбранный день` / `сегодня`.
- Во вкладке `⚙` появился список всех важных дней как настроек, с удалением.
- Добавлены стили для секций настроек, чтобы `⚙` не выглядела как свалка.

# v17.2 — кастомные быстрые сценарии и фиксы UI

- Дефолтная дневная смена изменена на `08:30–17:00`, обед `30 мин`, план `8 ч`.
- Для старых пользователей мягко обновляется только старый дефолт `06:30–17:00`; если время смены уже меняли вручную, оно не перетирается.
- Карточки быстрых сценариев больше не уезжают за правую панель: сетка стала адаптивной.
- Быстрые сценарии теперь хранятся как пользовательские настройки, а не только жёстко прошитые кнопки.
- Добавлен API `/api/quick-scenarios`: список, создание, редактирование и удаление сценариев.
- В интерфейсе появился блок `свои сценарии`: можно задать старт от начала/конца смены, конец через N минут/в фиксированное время/в конец смены, обед, вычет плана и причину по умолчанию.
- Стандартные сценарии сидятся один раз для пользователя; если удалить сценарий, он не будет насильно появляться снова.
- Добавлена миграция `V9__quick_scenarios_and_day_shift_time.sql`.

# v15.1 — быстрые сценарии переработки

- Добавлены быстрые кнопки в блоке переработки: `+2ч после смены`, `+4ч после смены`, `остался в ночь`, `с начала смены + ночь`, `обычная смена целиком`.
- Сценарии берут дату выбранного дня и параметры выбранной смены: начало, конец, обед и плановые часы.
- `+2ч/+4ч после смены` ставят переработку сразу после окончания смены без вычета плана.
- `остался в ночь` ставит интервал от конца выбранной смены до 08:00 следующего дня для дневных смен, либо безопасную заготовку на 2 часа после окончания для остальных.
- `с начала смены + ночь` заполняет полный фактический интервал от начала смены до 08:00 следующего дня и вычитает плановые часы смены.
- `обычная смена целиком` подставляет время выбранной смены, обед и план — удобно для проверки настроек смены или ручной корректировки.
- Все сценарии являются только заготовками: после нажатия можно поправить время, обед, план и причину перед начислением.

# v15.0 — смены со временем

- У типа смены появились поля `startTime`, `endTime`, `breakMinutes`, `plannedHours`.
- Встроенные смены получили стартовые настройки: дневная `06:30–17:00`, обед `30 мин`, план `8 ч`; ночная `20:00–08:00`, обед `60 мин`, план `11 ч`; выходной `0 ч`.
- Кастомную смену теперь можно создавать сразу со временем начала/конца, обедом и плановыми часами.
- В списке смен появилась кнопка `настроить`: можно поправить время, обед и план; у пользовательских смен также название и цвет.
- Кнопка `план по смене` в переработке теперь берёт `plannedHours`, а не старое поле `hours`.
- Добавлена кнопка `время по смене`, которая заполняет начало/конец/обед/план по выбранной смене.
- Кнопка `по смене` в списании отгула также использует плановые часы смены.
- Добавлен endpoint `PATCH /api/shift-types/{id}`.
- Добавлена миграция `V6__shift_type_time_model.sql`.

# v14.7 — экспорт журнала переработок

- Добавлен экспорт текущей таблицы переработок в CSV.
- Добавлен Excel-совместимый экспорт `.xls` без тяжёлых зависимостей.
- Экспорт учитывает фильтры таблицы: период, статус и поиск.
- В выгрузку попадают: день переработки, время, начислено, причина, использовано, куда списано, остаток, обед, вычтенный план и признак авторасчёта.
- CSV отдаётся с UTF-8 BOM, чтобы кириллица нормально открывалась в Excel.
- Добавлены endpoint’ы `/api/overtime/export.csv` и `/api/overtime/export.xls`.

# Изменения

## v14.6.1-manual-time-quality

Маленькая правка удобства ручного начисления переработки.

- Поле `Дата ручн.` переименовано в понятное `Дата начисления`.
- Добавлены кнопки `выбранный день` и `сегодня` для даты начисления.
- Короткий ввод времени теперь умеет считать часы сам: `17:00–20:00`, `17–20`, `17–08`.
- Если короткий интервал пересекает полночь, конец автоматически считается следующим днём.
- К короткому вводу применяются `обед, мин` и `вычесть план, ч`.
- После расчёта backend получает нормальные `startDateTime/endDateTime`, поэтому сохраняются защита от дублей и разбивка интервала по датам.

## v14.6-ledger-editing

Добавлено безопасное редактирование начислений переработки и списаний отгула.

- В таблице переработок появилась кнопка `ред.` для начислений.
- Можно изменить дату ручной записи, текст времени, часы и причину.
- Для рассчитанных начислений можно изменить начало, конец, обед и вычтенный план — backend пересчитает часы сам.
- Если редактирование интервала превращает одну строку в несколько дат, сервер заменит строку на несколько начислений, но только если она ещё не использована списаниями.
- Если начисление уже частично списано, его нельзя уменьшить ниже уже использованных часов.
- При редактировании рассчитанных интервалов сохраняется защита от дублей и пересечений.
- В списаниях появилась кнопка `ред.`. Можно изменить дату, часы и причину списания.
- Если изменить часы списания, FIFO-распределение пересобирается заново.
- Если доступных часов не хватает, изменение списания отклоняется и старое распределение остаётся.
- Добавлены API endpoint'ы `PATCH /api/overtime/credits/{id}` и `PATCH /api/overtime/usages/{id}`.

## v14.5-ledger-polish

Полировка таблицы переработок без изменения основной бухгалтерской логики.

- Добавлены фильтры журнала переработок: период `с/по`, быстрые кнопки `этот месяц` и `всё время`.
- Добавлен фильтр по состоянию начислений: все, с остатком, частично списанные, полностью списанные.
- Добавлен текстовый поиск по дате, времени, причине начисления и причинам списаний.
- Добавлена строка итогов по отфильтрованной таблице: сколько записей показано, начислено, использовано, остаток, сколько записей открыто/закрыто.
- В строках журнала появились бейджи статуса: `остаток`, `частично`, `списано`.
- Текущий выбранный в календаре день подсвечивается в таблице переработок.
- Удаление начислений и списаний теперь просит подтверждение.
- Для начислений, которые уже используются списаниями, в таблице показывается подсказка `сначала списания`, чтобы не было ощущения, что кнопка удаления просто пропала.

## v14.4-overtime-split-and-duplicates

Исправлены две проблемы расчётной переработки по интервалу времени.

- Интервал через полночь больше не падает одной суммой на дату начала.
- Сервер раскладывает рассчитанное начисление на несколько строк журнала.
- Обычные ночные интервалы режутся по датам: например `17:00 → 08:00` станет отдельными кусками до полуночи и после полуночи.
- Ровные сутки вида `08:00 → 08:00` следующего дня режутся пополам, чтобы в календаре было две понятные половины по двум датам.
- Обед и вычтенные плановые часы снимаются с самых ранних минут интервала.
- Сервер запрещает пересечения рассчитанных интервалов. Нельзя второй раз начислить тот же период `03.07 20:00 → 04.07 08:00` или частично пересекающийся кусок.
- Ручные начисления без `startDateTime/endDateTime` работают как раньше.
- Новая миграция БД не нужна: используются уже добавленные поля `start_at` и `end_at`.

## v14.3-overtime-date-fix

Исправлен баг начисления переработки при вводе интервала с датой, отличной от выбранного дня календаря.

- Если переработка создаётся через поля `начало` и `конец`, дата начисления теперь берётся из `startDateTime`.
- Пример: открыт день `2026-07-09`, но начало указано `2026-07-03T08:30` — запись будет начислена на `2026-07-03`, а не на `2026-07-09`.
- Backend тоже применяет это правило, поэтому Android/API-клиент не сможет случайно положить рассчитанную переработку на неправильный день.
- Для ручных начислений без интервала дата по-прежнему берётся из выбранного дня / поля `date`.

## v14.2-overtime-time-calc

Поверх версии с аккордеоном добавлен автоподсчёт часов переработки по интервалу работы.

### Переработка по времени

- В начислении переработки теперь можно указать `начало` и `конец` через `datetime-local`.
- Добавлены поля `обед, мин` и `вычесть план, ч`.
- Формула: `переработка = конец - начало - обед - плановые часы`.
- Если заносишь только кусок переработки, плановые часы оставляешь `0`.
- Если заносишь всю фактическую смену целиком, можно вычесть план по кнопке `план по смене`.
- Добавлена быстрая кнопка `17–08` для сценария “остался в ночь”.
- Ручной ввод часов сохранён: можно по-прежнему просто вписать количество часов без начала/конца.
- В таблице переработок отображается, что запись рассчитана из интервала, с учётом обеда и вычтенных плановых часов.

### API и БД

- `POST /api/overtime/credits` теперь принимает `startDateTime`, `endDateTime`, `breakMinutes`, `plannedHours`.
- Backend пересчитывает часы сам, поэтому фронт не является единственным источником расчёта.
- Добавлена миграция `V5__overtime_time_calculation.sql`.

## v13-overtime-accounting

Переработка вынесена в полноценную бухгалтерию часов.

### Журнал начислений и списаний

- Добавлены сущности `OvertimeCredit`, `OvertimeUsage`, `OvertimeAllocation`.
- Начисление переработки хранит дату, диапазон времени, часы и причину.
- Списание отгула хранит дату, часы и причину.
- Списание автоматически распределяется по начислениям по FIFO: сначала самые старые остатки.
- Переработка больше не “сгорает” при переходе на следующий месяц.
- Можно начислить переработку в мае и списать её в августе.
- Добавлена таблица переработок в веб-интерфейсе: день, время, начислено, причина, использовано, куда списано, остаток.

### API и БД

- Добавлен `GET /api/overtime/account`.
- Добавлен `POST /api/overtime/credits`.
- Добавлен `DELETE /api/overtime/credits/{id}`.
- Добавлен `POST /api/overtime/usages`.
- Добавлен `DELETE /api/overtime/usages/{id}`.
- `GET /api/calendar?from=&to=` теперь дополнительно отдаёт `overtimeAccount` с общим остатком переработки.
- Добавлена миграция `V4__overtime_accounting.sql`.

## v12-android-ready

Слой подготовки под полноценное Android-приложение.

### Мобильная авторизация

- Добавлена сущность `MobileAuthToken`.
- Добавлен `MobileAuthService`.
- Добавлен `BearerTokenAuthenticationFilter`.
- Android может ходить в API через `Authorization: Bearer <accessToken>`.
- Веб-сессия `JSESSIONID` сохранена и не сломана.
- Access token живёт коротко, refresh token — дольше.
- Refresh token ротируется при обновлении.
- В базе хранятся SHA-256 хэши токенов, а не сами токены.
- Добавлено управление мобильными сессиями/устройствами.

### Mobile API

- Добавлен `POST /api/mobile/auth/login`.
- Добавлен `POST /api/mobile/auth/refresh`.
- Добавлен `POST /api/mobile/auth/logout`.
- Добавлен `GET /api/mobile/auth/me`.
- Добавлен `GET /api/mobile/auth/sessions`.
- Добавлен `DELETE /api/mobile/auth/sessions/{id}`.
- Добавлен `GET /api/mobile/bootstrap?from=&to=`.
- Добавлен `POST /api/mobile/sync` для пакетной синхронизации изменений дней.

### БД и документация

- Добавлена миграция `V3__mobile_auth_tokens.sql`.
- Обновлены `README.md`, `docs/API.md`, `docs/ANDROID_API_PLAN.md`.

## v11-important-days-tasks

Новый продуктовый слой поверх календаря смен.

### Задачи

- Добавлены отдельные задачи дня с чекбоксами.
- Добавлены endpoint’ы `GET/POST/PATCH/DELETE /api/tasks`.
- На клетке календаря появляется `!`, если в дне есть невыполненные задачи.
- Когда все задачи выполнены, красный индикатор гаснет и превращается в спокойную отметку `✓`.
- Задачи не смешиваются с Markdown-заметкой и готовы для Android/Telegram.

### Важные дни

- Добавлены важные дни: дни рождения, годовщины, платежи, техосмотры и любые пользовательские события.
- Поддерживаются повторы: `NONE`, `MONTHLY`, `YEARLY`.
- Добавлены endpoint’ы `GET/POST/PATCH/DELETE /api/important-days`.
- Добавлен endpoint `GET /api/important-days/occurrences?from=&to=` для развёрнутых повторений в диапазоне.
- На календаре важные дни помечаются `★`.
- 29 февраля в невисокосный год показывается 28 февраля, чтобы ежегодное событие не пропадало.
- Ежемесячное событие на 31 число в коротких месяцах показывается в последний день месяца.

### API и БД

- `GET /api/calendar?from=&to=` теперь отдаёт `tasks` и `importantDays`.
- Добавлены сущности `DayTask`, `ImportantDay`, `RepeatMode`.
- Добавлены репозитории и сервисы `TaskService`, `ImportantDayService`.
- Добавлена миграция `V2__important_days_and_tasks.sql`.


## v10-api-architecture

Следующий шаг к полноценному продукту и Android-клиенту.

### API

- Добавлен Android-friendly endpoint `GET /api/calendar?from=&to=`.
- Ответ `/api/calendar` включает типы смен, дни диапазона и сводку переработки.
- Добавлен endpoint `GET /api/overtime/balance?from=&to=`.
- Добавлен endpoint `GET /api/overtime/ledger?from=&to=`.
- Старые endpoint’ы веб-версии `/api/days`, `/api/days/{date}`, `/api/days/fill` сохранены.
- Ограничен диапазон запросов календаря/переработок: максимум 366 дней.

### Архитектура

- Добавлен сервисный слой: `CurrentUserService`, `ShiftTypeService`, `DayEntryService`, `CalendarService`, `OvertimeService`.
- Контроллеры стали тоньше и больше не держат основную бизнес-логику.
- Добавлено доменное исключение `ApiException`.
- `ApiExceptionHandler` теперь обрабатывает сервисные ошибки единым JSON-форматом.
- В `DayEntryRepository` добавлен метод сортированной загрузки диапазона по дате.

### Документация

- Добавлен `docs/API.md` с описанием основных endpoint’ов.
- Обновлён `docs/ANDROID_API_PLAN.md` под новую архитектуру.
- Обновлён README.

## v9-production-foundation

Первый шаг от MVP к нормальному продукту и серверному запуску.

### Инфраструктура

- Добавлен PostgreSQL-драйвер.
- Добавлен Flyway.
- Добавлена production-миграция `src/main/resources/db/migration/postgresql/V1__init.sql`.
- Добавлен production-профиль `application-prod.properties`.
- В production включён Flyway и `spring.jpa.hibernate.ddl-auto=validate`.
- В dev-режиме H2 оставлена для быстрого запуска в IntelliJ.
- В dev-режиме Flyway отключён, Hibernate по-прежнему может обновлять H2-схему.
- Добавлен Dockerfile.
- Добавлен `docker-compose.yml` с PostgreSQL и приложением.
- Добавлен `.env.example`.
- Добавлен пример nginx-конфига: `deploy/nginx/shift-calendar.conf.example`.
- Добавлен скрипт бэкапа PostgreSQL: `deploy/scripts/backup-postgres.sh`.
- Добавлен Spring Boot Actuator health endpoint `/actuator/health`.

### Код

- Поле `note` в `DayEntry` теперь явно мапится как `text`, чтобы нормально работать с PostgreSQL.
- Поля `overtime_hours` и `time_off_hours` помечены как `nullable=false`.
- `/actuator/health` разрешён без авторизации.

### Документация

- README переписан под dev/prod запуск.
- Добавлен `docs/ROADMAP.md`.
- Добавлен `docs/ANDROID_API_PLAN.md`.

## v8-overtime

- Добавлены поля переработки и списания отгула в день.
- Добавлен месячный баланс переработки.
- Добавлены отметки `+7ч`, `-8ч` и т.п. в календаре.
- Массовое заполнение графика не стирает переработки и отгулы.

## v7-monthfill

- Исправлено заполнение графика через границу месяца.
- По умолчанию график заполняется на 31 день вперёд.
- Пятидневка привязана к реальным дням недели.

## v6-schedules

- Добавлена встроенная смена «Выходной».
- Добавлено массовое заполнение графика.
- Добавлены шаблоны 2/2, день/ночь/48, пятидневка 5/2, день/72, ночь/72.

## v5-customonly

- Стартовыми оставлены только базовые смены.
- Остальные типы смен пользователь создаёт сам.

## v4-dorabotano

- Исправлено автосохранение заметок.
- Добавлена валидация.
- Добавлен `ApiExceptionHandler`.
- Добавлен базовый PWA-слой.

## v15.2 — улучшение UI быстрых сценариев

- Блок быстрых сценариев переработки переделан из ряда кнопок в понятные карточки.
- Добавлен контекст выбранного дня и смены: дата, смена, время, плановые часы, обед.
- Карточки сценариев автоматически блокируются, если у выбранной смены не настроено нужное время.
- Добавлена активная подсветка последнего выбранного сценария.
- Добавлена кнопка «очистить поля» для сброса формы переработки.
- Добавлены короткие описания сценариев прямо в интерфейсе:
  - +2 часа после смены;
  - +4 часа после смены;
  - остался в ночь;
  - смена + ночь;
  - обычная смена.
- Backend-логика не менялась: FIFO, защита от дублей, разбивка интервалов по датам и экспорт остались как в предыдущих версиях.

## v17.0 — уведомления и напоминания

- Добавлена сущность `NotificationSettings` с пользовательскими настройками напоминаний.
- Добавлена миграция `V7__notification_settings.sql`.
- Добавлен backend-сервис расчёта напоминаний для Web/PWA, Android и будущего Telegram-бота.
- Добавлены напоминания:
  - перед сменой;
  - о невыполненных задачах;
  - о важных днях;
  - вечерний дайджест на завтра.
- Добавлен API:
  - `GET /api/notifications/settings`;
  - `PATCH /api/notifications/settings`;
  - `GET /api/notifications/upcoming?from=&to=`.
- `GET /api/calendar?from=&to=` теперь отдаёт `notificationSettings` и `reminders`.
- В веб-интерфейс добавлен блок «Уведомления»: настройки, запрос разрешения браузера, тест уведомления и список ближайших напоминаний.
- На днях календаря появляется метка 🔔, если на дату есть рассчитанные напоминания.

## v17.1 — Полировка уведомлений

- Добавлены настройки уведомлений на уровне конкретной смены:
  - можно отключить напоминания для отдельной смены;
  - можно задать своё `notificationMinutesBefore`, отличное от глобального значения.
- Напоминания перед сменой теперь учитывают настройки конкретного типа смены.
- Добавлен endpoint `GET /api/notifications/tomorrow` для быстрой проверки напоминаний на завтра.
- Endpoint `GET /api/notifications/upcoming` получил параметр `includePast`.
- В веб-интерфейсе добавлены быстрые пресеты времени напоминания: 15, 30, 60, 90 и 120 минут.
- В блок уведомлений добавлены кнопки:
  - `Текущий месяц`;
  - `Проверить завтра`.
- В списке смен теперь видно, отключены ли уведомления или задано своё время напоминания.

## v18.0 — Задачи 2.0: категории, сроки, уведомления

- Расширена модель задач: категория, приоритет, срок выполнения, время срока, индивидуальное напоминание.
- В календаре просроченные задачи теперь отмечаются отдельным индикатором `!!`.
- В панели дня добавлены фильтры задач: все / открытые / просроченные / выполненные, а также фильтр по категории.
- В списке задач появились бейджи: категория, приоритет, срок, напоминание, просрочка.
- Уведомления теперь учитывают индивидуальные сроки задач: если у задачи задан срок и напоминание, reminder рассчитывается от due date/time.
- Добавлена миграция `V10__task_metadata_and_due_dates.sql`.

## v18.1 — Общий экран задач

- Добавлен общий блок «Все задачи» под календарём.
- Задачи теперь можно смотреть одним списком, не открывая каждый день отдельно.
- Добавлены фильтры общего списка:
  - открытые;
  - просроченные;
  - открытые не просроченные;
  - выполненные;
  - все задачи;
  - категория;
  - приоритет;
  - период;
  - поиск по тексту, категории и датам.
- В общем списке задач можно:
  - поставить/снять галочку;
  - открыть день задачи в календаре;
  - редактировать задачу;
  - удалить задачу.
- Добавлен API `GET /api/tasks/board` для общего экрана задач и будущего Android-экрана задач.

## v19.2 — важные дни обратно в панель дня

- Важные дни возвращены в панель выбранного дня: теперь можно ткнуть дату и сразу добавить день рождения/платёж/событие.
- Из вкладки ⚙ убрана секция важных дней, чтобы настройки не выглядели странно и не дублировали дневной контекст.
- Исправлено отображение скрытых вкладок: настройки больше не появляются под календарём из-за CSS `display:flex`.
