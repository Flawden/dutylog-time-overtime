# Module contracts

Status: v27.29.2.

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
