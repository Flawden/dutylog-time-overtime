# Frontend architecture

Status: Vue app-shell ownership v1, DutyLog v27.34.3.

## Browser-safe library output (v27.34.3)

The Vue shell is built in Vite library mode but consumed directly by browsers from Spring Boot static resources. The build therefore replaces `process.env.NODE_ENV` at compile time and audits the emitted `dutylog-vue-app-shell.js` for Node-only runtime globals. No `process` shim is exposed to the browser and the legacy bridge boundary is unchanged.

## Current ownership

**Vue owns the application shell**: brand, profile entry, primary/secondary navigation, active-route presentation, online state, shared modal/toast hosts and design-system primitives.

**Legacy product screens remain authoritative** for Today, Calendar, Absences, Time Bank, Payroll, Tasks, Important Days, Settings and Admin. They still own domain rendering, forms, API reads/mutations, offline queue and hash-route execution until each bounded domain is migrated.

```text
frontend/src
├── app                 Vue shell, navigation and cross-shell UI state
├── platform            same-origin transport, bridge, router and readiness
├── shared/ui           typed design-system primitives
├── shared/overlays     modal and toast infrastructure
├── styles              tokens, shell and responsive behavior
└── features            bounded product domains added from v27.35.0 onward
```

## State ownership

- Spring Boot owns business rules and persisted truth.
- Legacy product read models remain authoritative until their domain migration.
- The shell receives only a frozen snapshot: route, allowed navigation, language, online/module readiness and safe profile display fields.
- Pinia owns explicit shell UI state; it never mirrors one global mutable product state.
- Local form drafts remain local to each future feature/composer.
- Offline queue and synchronization remain a separate infrastructure boundary.

## Routing and bridge

Vue Router still uses memory history. The released hash route remains authoritative during v27.34.3. Vue navigation calls the named `DutyLogLegacyPlatform.navigate(view)` capability; legacy routing publishes the new frozen snapshot back through `subscribe(listener)`.

Allowed transition capabilities are:

```text
snapshot
subscribe
navigate
openModal
logout
```

Vue must not read `window.state`, query `#tabbar`, mutate product DOM or create a second mutation owner. When Vue reaches full parity, one release will switch routing authority atomically; two production routers must not remain active.

## Shared design system

The first reusable primitives are `UiButton`, `UiBadge`, `UiCard`, `UiTabs`, `UiEmptyState`, `UiModal`, `ToastHost` and `AppIcon`. They reuse DutyLog theme tokens, visible focus, reduced motion and mobile bottom-sheet behavior. Migrated domains must compose these primitives instead of creating parallel button/card/modal systems.

## Safe fallback

Legacy topbar/tabbar stay in the document during the transition. They hide only after the Vue platform publishes successful readiness and sets `html[data-vue-shell="ready"]`. A failed Vue boot therefore leaves the released navigation usable.

## API client

The shared client sends same-origin credentials, mirrors Spring's XSRF cookie/header contract for mutating requests, redirects expired browser sessions to `/login.html`, normalizes failures into `DutyLogApiError` and never performs canonical business calculations in the browser.

## Build and packaging

Vite emits stable shell files:

```text
frontend/dist/dutylog-vue-app-shell.js
frontend/dist/dutylog-vue-app-shell.css
```

Maven packages `frontend/dist` as `static/vue`. Docker uses Node and Maven only in build stages and copies one finished JAR into the existing non-root JRE runtime image. There is no frontend runtime server or third DutyLog container.

## Test pyramid

```text
Maven/JUnit -> backend business, security and architecture contracts
vue-tsc    -> frontend type contracts
Vitest     -> shell stores, bridge, components and transport
Playwright -> user journeys and browser/runtime integration
```

Historical source-string tests are retired domain by domain when their legacy files disappear.
