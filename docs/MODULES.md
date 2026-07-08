# DutyLog Modules

Status: v25.1.

DutyLog is now a modular monolith. The application still ships as one Spring Boot backend and one web/PWA frontend, but user-facing features are grouped into modules that can be enabled or disabled per user.

## Principles

- Disabling a module hides it from UI and guards its API.
- Disabling a module never deletes data.
- Core modules stay enabled because the app cannot work without them.
- Dependencies are enabled automatically when a dependent module is enabled.
- This is not a microservice split yet; it is a safe modular-monolith layer.

## Module registry

Current module keys:

| Key | Default | Locked | Purpose |
| --- | --- | --- | --- |
| `core` | on | yes | profile, settings shell, theme, language |
| `calendar` | on | yes | month view and selected day |
| `shifts` | on | yes | shift types, hours and schedules |
| `notes` | on | no | Markdown notes in a selected day |
| `tasks` | on | no | daily tasks and global board |
| `overtime` | on | no | overtime credits, time off and FIFO ledger |
| `important_dates` | on | no | birthdays and repeating events |
| `notifications` | on | no | browser reminders and schedules |
| `telegram` | off | no | Telegram bot linking and delivery |
| `scenarios` | on | no | quick overtime form templates |
| `admin` | on for admins | yes | admin-only system tools |

## API

```http
GET /api/modules
PATCH /api/modules
```

`PATCH /api/modules` accepts a map of module flags:

```json
{
  "enabled": {
    "tasks": false,
    "notes": true,
    "telegram": true
  }
}
```

The backend ignores unknown keys and locked module changes. If a module is disabled and its API is called, the backend returns HTTP 403 with an error such as:

```text
MODULE_DISABLED:tasks
```

The frontend converts that into a user-facing message.

## Database

```text
user_module_settings
```

The table stores per-user overrides. Missing rows fall back to module defaults from the backend registry.

## Developer boundary

The first v25.0 step guards major feature APIs and hides major UI blocks. Later versions can move toward stronger contracts:

- day panel provider contracts;
- notification source contracts;
- module-aware offline snapshots;
- first-run onboarding;
- package-level boundaries under `ru.daniil.shifts.modules`.

## v25.1 selected-day panel

The selected-day panel is now module-aware. Each day block has an explicit module owner:

- `shifts`: shift selector and schedule fill helpers.
- `notes`: note editor and note calendar marker.
- `tasks`: day task list and task calendar markers.
- `overtime`: overtime/time-off controls and overtime balance markers.
- `important_dates`: important-day list and star markers.
- `notifications`: reminder markers.

Disabling a module hides its day-panel block and prevents its calendar markers from rendering. The data is preserved and appears again when the module is re-enabled.
