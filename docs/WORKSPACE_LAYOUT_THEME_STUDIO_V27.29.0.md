# v27.29.0 — Workspace, Layout & Theme Studio

DutyLog UI Core advances to contract v2. The release turns the earlier declarative foundation into a user-facing studio without duplicating screens or introducing arbitrary CSS/JavaScript.

## Product contract

- one DOM and one business layer remain authoritative;
- workspaces only choose navigation and Today-card presentation;
- layouts only place existing surfaces;
- themes remain isolated semantic-token packages;
- palettes change allowed accents only;
- decorations are pointer-free and cannot access routes, API or user data;
- hiding a module or card never deletes its data.

## Workspace Studio

The Appearance settings now include a safe studio for:

- creating a custom copy of the current workspace;
- ordering the primary navigation;
- choosing up to five primary routes;
- keeping `Today` and `Settings` mandatory;
- ordering Today cards;
- hiding optional Today cards while keeping the Shift card available;
- reaching non-primary enabled sections through generated secondary links.

All changes use the existing debounced appearance save queue and persist in the whitelisted profile `themeConfig`.

## Layouts

Two additional layout packages are available:

- `sidebar` — fixed left navigation on wide screens, automatically returning to the mobile bottom bar below the desktop breakpoint;
- `mobile-flow` — a narrow sequential content column even on desktop.

No screen markup is cloned for either layout.

## Calendar presentation

Calendar presentation is independent from the global theme:

- `comfortable` or `compact` month-cell density;
- schedule-layer `pills` or compact `dots`;
- a safe optional `grid` decoration.

The dots mode changes only visual density. Layer names and ranges remain available through existing titles and the full Week/Day agenda.

## UI package contract

Theme registry entries now publish:

- `uiContract: 2`;
- package CSS path;
- semantic token scope;
- custom-palette capability.

Decoration packages declare the same UI contract and remain `pointer-events: none`.

## Persistence and validation

The server whitelist accepts only:

- known workspace/layout/theme/palette/decoration ids;
- known navigation and Today-widget ids;
- at most five primary navigation items;
- mandatory `today`, `settings` and `shift` entries;
- known calendar density/layer modes.

Unknown fields, arbitrary CSS and arbitrary JavaScript remain rejected or discarded.

## Compatibility

- existing UI Core v1 profiles are normalized to v2;
- existing workspace/theme/palette selections remain valid;
- Classic remains retired;
- Payroll, approval, ledger, calendar data and APIs are unchanged;
- Flyway remains V46.

## Automated baseline

- 119 Java test classes;
- 610 `@Test` methods;
- 38 Chromium Playwright scenarios;
- Flyway V46.
