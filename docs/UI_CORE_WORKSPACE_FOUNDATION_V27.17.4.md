# v27.17.4 — UI Core & Workspace Foundation

## Goal

Create one stable UI base that can support many independent workspaces, layouts, themes and palettes without copying screens or allowing one visual package to patch another.

```text
Business logic / APIs
        ↓
UI Core contract v1
        ↓
Screens and components
        ↓
Workspace + Layout + Theme + Palette + Decorations
```

## Runtime contract

`js/12-ui-platform.js` contains declarative registries only. It performs no API calls and owns no domain logic.

- `workspaces` define primary navigation order and Today widget order;
- `layouts` select scoped layout presets;
- `themes` map to isolated CSS packages;
- `palettes` provide primary and secondary accents;
- `decorations` reserve a safe future extension point;
- `screens` map route IDs to the existing DOM;
- `widgets` map Today slots to the existing cards.

The default composition is:

```text
workspace: shift-worker
layout: dashboard
theme: default
palette: theme
decoration: none
```

## Workspaces

### Shift Worker

Primary order: Today, Calendar, Overtime, Tasks, More.

### Planner

Primary order: Today, Tasks, Calendar, Overtime, More. Today widgets start with tasks and important dates.

### Minimal

Primary order: Today, Calendar, More. Overtime, Tasks and Important Dates remain reachable through links in Appearance settings; data and modules are not disabled.

Workspaces never create or delete domain data.

## Layouts

- `dashboard` — balanced two-column Today grid;
- `compact` — wider canvas and reduced spacing;
- `focus` — one primary column.

Layouts are scoped through `html[data-ui-layout]` and do not contain feature logic.

## Theme package contract v1

Each built-in theme is a standalone file under `static/ui/themes/` and is scoped to exactly one `data-ui-theme` selector.

Required surface tokens:

```text
--color-background
--color-surface
--color-surface-elevated
--color-text-primary
--color-text-secondary
--color-border
```

Accent tokens are supplied by the independent palette layer:

```text
--color-accent
--color-accent-secondary
```

A built-in theme must not import or override another theme. Custom surface colors are applied only when `themeId=custom`.

## Persistence

UI configuration is stored inside the existing allowlisted `themeConfig` profile JSON:

```json
{
  "uiContract": 1,
  "workspaceId": "shift-worker",
  "layoutId": "dashboard",
  "themeId": "midnight",
  "paletteId": "violet",
  "decorationId": "none",
  "accentSecondary": "#58C6C8",
  "todayWidgets": ["shift", "overtime", "tasks", "important"]
}
```

The server validates enums, colors and widget IDs. Arbitrary CSS and JavaScript are rejected.

Appearance writes are local-first and then persisted through a debounced serialized queue. A revision guard prevents an older request from repainting a newer user choice.

## Pre-paint bootstrap

`shell-bootstrap.js` reads only validated local appearance data and synchronously applies:

- light/dark mode;
- Classic/Next shell;
- workspace;
- layout;
- theme;
- palette;
- safe custom colors.

This prevents the old shell or wrong theme from flashing before the main bundles load.

## Classic boundary

Classic remained available in v27.17.4 as a temporary transition boundary. It was removed in v27.17.6 after UI Core v1 staging acceptance.

Classic removal was completed in v27.17.6; rollback now uses immutable Git/Docker releases.

## Compatibility

- no database migration;
- no domain API change;
- Flyway remains V36;
- old profiles receive safe defaults;
- existing theme presets migrate to matching `themeId`;
- modules remain authoritative for feature visibility;
- workspace hiding never deletes or disables a module.

## Regression gates

- 95 Java test classes;
- 496 `@Test` methods;
- 25 Playwright scenarios;
- static Java frontend-contract compilation;
- UI Core theme-package contract check;
- authenticated deployment smoke for every new CSS/JS asset;
- Classic fallback regression in the workspace Playwright scenario.
