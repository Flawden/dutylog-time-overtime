# v27.34.0 — Vue App Shell & Design System

DutyLog moves the first visible production surface from ordered vanilla JavaScript to Vue 3 + TypeScript. Vue now owns the application brand, primary navigation, responsive shell chrome, profile entry point, network status, secondary-route dialog, shared overlay host and the first typed design-system primitives.

Legacy product screens remain authoritative in this release. Calendar, Today, Absences, Time Bank, Payroll, Tasks, Important Days, Settings and Admin are still rendered and mutated by the released domain scripts. The Vue shell reaches them only through the explicit `DutyLogLegacyPlatform` capabilities and consumes an immutable shell read model.

## Ownership boundary

```text
Vue App Shell
├── brand and profile entry
├── primary/secondary navigation
├── active-route presentation
├── online/offline status
├── responsive desktop/mobile chrome
├── shared modal host
├── shared toast host
└── design-system primitives

Legacy product workspaces
├── domain rendering
├── forms and product modals
├── API reads and mutations
├── offline queue
└── hash-route authority
```

Two routers do not compete. Vue Router still uses memory history. Selecting a Vue navigation item calls the named legacy `navigate(view)` capability, and the legacy router publishes the resulting immutable snapshot back to Vue.

## Immutable shell read model

The adapter publishes:

- current raw route;
- normalized primary navigation order from Workspace Studio;
- all enabled routes;
- language;
- online state;
- module-readiness state;
- safe profile display name, initials and administrator flag.

Every snapshot and route array is frozen before crossing the boundary. Vue does not read `window.state`, does not query `#tabbar`, and does not mutate legacy product DOM.

## Shared UI primitives

The first typed primitives are:

```text
UiButton
UiBadge
UiCard
UiTabs
UiEmptyState
UiModal
ToastHost
AppIcon
```

They use existing DutyLog theme tokens, keyboard focus treatment, reduced-motion support and mobile bottom-sheet behavior. Product domains will reuse these primitives during v27.35.0–v27.38.0 instead of creating new parallel component styles.

## Responsive shell

- Dashboard/compact/focus layouts render a Vue header and adaptive navigation bar.
- Sidebar layout renders the same Vue navigation vertically without duplicating product screens.
- Mobile renders the same navigation as a bottom bar and the secondary-route dialog as a bottom sheet.
- Legacy topbar/tabbar remain in the DOM for transition compatibility but are hidden only after Vue publishes a successful ready event.
- If the Vue bundle fails before readiness, the released legacy shell remains visible as a safe fallback.

## Build and deployment

Vite emits:

```text
frontend/dist/dutylog-vue-app-shell.js
frontend/dist/dutylog-vue-app-shell.css
```

Maven packages both files under `BOOT-INF/classes/static/vue`. Docker still produces one non-root Spring Boot runtime image. PostgreSQL remains the only separate DutyLog container.

## Test baseline

```text
132 Java test classes
645 @Test methods
44 Playwright scenarios
11 Vitest cases
Flyway V47
```

The new browser scenario proves that Vue navigation changes the released hash route, the matching legacy workspace remains functional, active-route state returns to Vue, and the old tabbar is hidden only after the shell is ready.

## Non-goals

This release does not migrate domain screens, change an HTTP endpoint, alter PostgreSQL, add a Flyway migration, recalculate FIFO, change Payroll, modify approval rules or add a product feature. The next migration release is `v27.35.0 — Vue Absence & Time Bank`.
