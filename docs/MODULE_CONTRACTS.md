# Module contracts

## Vue Tasks, Notes & Important Days ownership (v27.38.0)

- Vue is the sole runtime presentation owner for Tasks, Notes, Important Days and Inbox, including selected-day bodies and productivity editor modals.
- Spring Boot remains authoritative for persisted task/note/event state, ownership, validation, scheduling and Inbox conversion.
- Online Vue reads/writes use generated operationId-based `/api/v1/*` transport. Compatibility aliases remain only where the pre-existing offline dataLayer owns queue/cache behavior.
- Q-10 introduces no second queue: note edits, normal task completion and Inbox capture delegate to the existing dataLayer queue; reconnect flushes it and then reloads authoritative generated-API state.
- Independent read sequences prevent stale board/selected-day/important/inbox/search responses from winning; mutation locks and HTTP 409 refresh remain fail-closed.
- Legacy productivity route roots/modals yield only after the Vue owner is ready.

## Vue Absence & Time Bank ownership (v27.36.0)

- Vue is the sole runtime presentation/mutation owner for the `vacation` and `overtime` routes.
- Spring Boot remains authoritative for allowance, overlap, compensation, exact credit calculation, ownership, FIFO reservation/posting/reversal, accounting periods and ledger integrity.
- The feature consumes only operationId-based generated API calls; no raw `fetch`, `jfetch` or mutable legacy state is allowed.
- Legacy Today/Calendar actions may cross the boundary only through typed `openAbsenceComposer`, `openAbsenceEditor`, `openCreditEditor`, `openTimeBankUsage` and `refresh` capabilities.
- Legacy route roots and editor modals are retired when the Vue workspace mounts; named adapters remain until their caller domains migrate.
- Q-06 blocks duplicate writes, rejects stale reads and refreshes the server model after HTTP 409.

Status: v27.38.10.

## v27.35.7 historical static-contract alignment

Four historical Gate A tests now describe the final promoted state: tracked authentic lockfile, semantic green-acceptance wording, recursive V1–V47 discovery and exact pinned Node image. This is test-only and changes no module registry or runtime ownership.

Status: v27.35.7.

## Frontend gate static-contract Java escaping (v27.35.4)

- The committed authentic npm graph and frontend runtime are unchanged.
- The test-only assertion for `npm --prefix "$FRONTEND_DIR" ci` uses valid escaped Java quotes.
- Maven `testCompile` is the acceptance boundary; Gate A remains blocked until the complete CI/staging chain is green.

Status: v27.35.4.

## Vue committed npm graph and generated-client fixture (v27.35.3)

- The authentic GitHub Actions dependency graph is committed and is the only normal input to `npm ci` in CI, Docker and local delivery gates.
- Lockfile generation remains explicit maintenance tooling and is not part of ordinary builds.
- Generated-client tests create a fresh `Response` for each request; production transport semantics are unchanged.
- Q-01–Q-05 are implemented; full green CI/staging is still required before v27.36.0.

Status: v27.35.3.

## Vue delivery, generated API and authentic lockfile bootstrap (v27.35.2)

- v27.35.2 temporarily bootstraps an authentic npm graph with exact-pinned Node/npm and direct dependencies, verifies it, then runs `npm ci`; the exact artifact must be committed in v27.35.3.
- `src/main/resources/static/openapi/dutylog-v1.yaml` is the canonical frontend contract source. Generated TypeScript schemas/operations and the typed client are drift-checked in every frontend build.
- Shared requests publish a bounded correlation ID and diagnostics expose only release, public route and request metadata.
- Vue render/boot/unhandled-promise failures render recovery UI without suppressing strict browser errors.
- Migration manifest/parity templates and ADR-001–ADR-005 remain complete, but Q-01 and Gate A stay blocked until v27.35.3 commits the generated lockfile; no product domain changes owner.

Status: v27.35.2.

# Module contracts

## Vue secondary navigation and overtime draft preview (v27.34.4)

- Non-persistent overtime preview may represent zero/negative intermediate calculations; create/update remain strict.
- A secondary active route marks the visible More control and its modal item with `aria-current="page"`.
- No API shape, schema, Flyway or domain ownership change.

Status: v27.34.4.

## Vue shell E2E ownership compatibility (v27.34.3)

Vue remains the visible shell owner. Browser tests navigate through public shell capabilities and visible Vue hooks rather than hidden legacy chrome.

## Vue browser-runtime bundle safety (v27.34.2)

- `process.env.NODE_ENV` is replaced by the literal production value in Vite library output.
- `npm run build` audits the generated shell JavaScript for residual Node/CommonJS globals.
- Docker and CI use the same audited build command.
- Playwright page-error collection remains strict and is not filtered for Vue failures.

## Strict Vue compiler compatibility (v27.34.1)

- Native DOM attributes receive valid concrete values under `exactOptionalPropertyTypes`; explicit `undefined` is forbidden.
- Vite configuration must use options supported by the pinned Vite 5 type surface.
- Stable CSS naming remains a Rollup output responsibility and does not create a new runtime asset or service.
- Product module ownership is unchanged from v27.34.0.

## Vue app-shell ownership (v27.34.0)

- Vue owns the application brand, profile entry, primary and secondary navigation, active-route presentation, network status and shared overlay hosts.
- Legacy product screens remain authoritative for rendering, forms, API reads/mutations, offline synchronization and hash-route execution.
- Workspace/module visibility is published as an immutable read model; Vue must not infer a second module registry.
- Navigation, modal and logout behavior cross the boundary only through named bridge capabilities.
- Shell ownership does not create a frontend service, product module or second mutation owner.
- A failed Vue boot leaves the released legacy shell visible; hiding legacy chrome is gated by successful Vue readiness.

## Frontend migration ownership (v27.33.0)

- `frontend/` is an architectural boundary, not a new product module or service.
- Backend module keys and guards remain canonical. Vue feature folders must reuse those keys rather than inventing browser-only domains.
- The foundation owns no business mutation. It exposes transport, state, routing and bridge infrastructure only.
- During migration, legacy workspaces remain authoritative and Vue Router uses memory history.
- A migrated feature replaces its legacy implementation as one unit; duplicate mutation owners are forbidden.

## Absence and time-bank presentation ownership (v27.32.0)

- Vacation/Absence owns every user mutation of time off.
- Overtime exposes credits and a read-only usage/FIFO projection.
- `POSTED` and `RESERVED` are separate presentation groups; free balance is not recomputed by a second client-side ledger.
- Cross-links focus the same source absence or linked usage; they do not clone records.
- Product guides explain this boundary and may be replayed without changing domain state.

DutyLog uses a modular-monolith approach. A module is not a separate service yet; it is a bounded feature area with a stable key, API guards, UI slots and optional offline queue operation types.

The canonical backend registry lives in:

```text
src/main/java/ru/daniil/shifts/module/DutyLogModules.java
```

Stable keys live in:

```text
src/main/java/ru/daniil/shifts/module/ModuleKeys.java
```

Do not invent module keys inside controllers or frontend code. Add the key to the registry first.

## Cross-workspace absence editor ownership (v27.32.1)

A linked Overtime usage never owns an editor. Bank Usage may navigate to its `sourceAbsenceId`, but the transition must refresh the absence read model when stale, open Unified Absence Composer and build the edit-aware FIFO preview. Inline Absence editing remains supported; cross-workspace editing uses the same form through the modal mount.


## Browser ownership alignment (v27.31.2)

No module ownership changes. Browser regression flows now follow the released boundary: `overtime` renders linked usages as read-only FIFO projections, while `vacation` owns their edit and delete actions. Expected retirement conflicts are verified outside the page runtime monitor.

## Static contract alignment (v27.31.1)

No module ownership changes. Historical frontend contracts now describe the released boundary accurately: `vacation` owns absence drafts and linked time-off mutation, while `overtime` owns credits, FIFO and promotion of old manual usages.

## Canonical absence ownership (v27.31.0)

Schema boundary: V47 only widens the immutable V42 absence shape constraints for transition-only `HOURS_ONLY`; it does not change module ownership or rewrite rows.

- `vacation` owns creation and editing of every absence, including overtime-backed time off.
- `overtime` owns credits, FIFO allocation, balance and read-only linked compensation rows.
- New direct MANUAL usage writes are retired.
- Legacy usage promotion requires both `overtime` and `vacation` modules.
- Changing an absence away from `OVERTIME_BANK` removes its linked usage and restores FIFO balance.
- `HOURS_ONLY` is import-only and cannot be selected for a new absence.


## Contract fields

Every module contract has:

| Field | Meaning |
| --- | --- |
| `key` | Stable persisted identifier, for example `tasks` or `overtime`. |
| `category` | Developer/UI grouping: core, calendar, productivity, time accounting, integration, admin. |
| `locked` | Module cannot be disabled by the user. |
| `defaultEnabled` | Default flag when the user has no row in `user_module_settings`. |
| `dependencies` | Modules that are enabled automatically when this module is enabled. |
| `uiSlots` | UI regions owned by this module, for example `day:tasks` or `nav:overtime`. |
| `apiPrefixes` | Main backend API prefixes guarded by this module. |
| `offlineQueueTypes` | Offline queue operation types owned by this module. |
| `order` | Stable display order. |


## Payroll module

Stable key: `payroll`. Category: `TIME_ACCOUNTING`. It is enabled by default and depends on `overtime` and `vacation`, because a money calculation is valid only after plan/fact/compensation data exists. Its API prefixes are `/api/payroll` and `/api/v1/payroll`; its primary UI slot is `nav:payroll`. The module owns no offline mutation queue in v27.28.0.

## API

```http
GET /api/modules
GET /api/modules/contracts
PATCH /api/modules
```

`GET /api/modules` and `GET /api/modules/contracts` return the same effective payload for the current user. `/contracts` exists so clients and tests can explicitly request contract metadata.

## Add a new module checklist

1. Add the stable key to `ModuleKeys`.
2. Add a `ModuleContract` to `DutyLogModules`.
3. Add/verify controller guards with `moduleService.requireEnabled(user, ModuleService.<KEY>)`.
4. Add frontend UI slots to the relevant registry or DOM blocks.
5. Add offline queue operation types if the module can write offline.
6. Add docs and changelog notes.
7. Do not delete user data when a module is disabled.

## Current module ownership

| Module | UI slots | API prefixes | Offline queue |
| --- | --- | --- | --- |
| `core` | app shell, profile, notes data export, appearance, language, offline shell | `/api/profile`, `/api/modules`, `/api/auth`, `/api/mobile`, `/api/export/notes` | — |
| `calendar` | calendar grid, selected day | `/api/calendar`, `/api/days` | `day.shift` |
| `shifts` | shift selector, schedule, shift settings | `/api/shift-types` | `day.shift` |
| `notes` | day note, note marker | day note updates | `day.note` |
| `tasks` | tasks tab, day tasks, task markers | `/api/tasks` | `task.done` |
| `overtime` | overtime tab, day overtime, unified compensation summary, integrity and factual work | `/api/overtime`, `/api/time-compensation`, `/api/ledger-integrity`, `/api/actual-work` | `day.overtime` |
| `important_dates` | important-day day block, star markers, settings | `/api/important-days` | — |
| `calendar_sync` | external calendar settings, important-event `.ics` action | `/api/calendar-sync`, `/calendar-feed.ics` | — |
| `notifications` | reminder settings, reminder markers | `/api/notifications` | — |
| `telegram` | profile Telegram block | `/api/telegram` | — |
| `scenarios` | quick scenario buttons/settings | `/api/quick-scenarios` | — |
| `admin` | admin settings/system diagnostics | `/api/admin` | — |

The notes ZIP export belongs to `core` as a data-portability operation. It remains available when the Notes UI module is disabled; module switches hide features but do not remove stored data or the user's ability to retrieve it.
