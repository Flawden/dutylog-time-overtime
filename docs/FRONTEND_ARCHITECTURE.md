# Frontend architecture

Status: Vue app-shell plus Absence/Time Bank, Calendar/Timeline and Productivity ownership, DutyLog v27.38.8.


## Vue Tasks, Notes & Important Days ownership (v27.38.0)

Vue now owns the bounded `productivity` domain: Tasks board/Inbox presentation, Task details/editor, selected-day Tasks, multiple daily Notes, Important Days board/details/editor and selected-day Important Dates. The feature uses the generated OpenAPI client for online reads/writes and independent read-sequence guards for latest-read-wins behavior. Spring Boot remains authoritative for scheduling/deadline validation, subtask completion, note order/persistence, Inbox conversion, recurrence/timezone projection and every persisted business rule.

Q-10 offline/reconnect reuses the existing legacy `dataLayer` queue and cached calendar snapshot through typed bridge operations. Vue does not create a second offline store. Note edits, task completion and Inbox capture may queue offline; selected-day productivity can read the cached snapshot; reconnect flushes the existing queue and reloads authoritative state. New-note creation remains disabled offline until create-identity semantics are owned by the queue.

The old Tasks/Important route roots and productivity modals are retired after Vue ownership is ready. Cross-domain Today/Calendar launches call `DutyLogVueDomains.productivity` by name rather than invoking hidden legacy DOM owners.

## Vue Calendar & Timeline strict type boundary (v27.37.1)

The public `DutyLogCalendarTimelineDomain.openDate` callback explicitly carries `string` and optional `CalendarMode` parameters. Pinia actions accept optional modes and resolve `mode ?? this.mode` inside the typed action body; no default parameter initializer references `this`. Runtime ownership and generated API behavior remain identical to v27.37.0.

## Vue Calendar & Timeline ownership (v27.37.0)

Vue now owns Today and the Calendar Month, Week and Day read surfaces through one bounded `calendar-timeline` feature. Its generated-API store owns focus date, view mode, stale-read sequencing, authoritative work-date loading and optimistic calendar-layer visibility with rollback. The Day view composes shifts, partial absences, timed tasks, important events, reminders and calendar-layer occurrences into one hourly timeline without reproducing backend business rules.

The legacy Today and Calendar read roots are retired after Vue readiness. The mature selected-day mutation editor remains as one named compatibility island mounted beneath `#calendarLegacyPanelHost`; it is reached only through explicit bridge commands. Historical legacy render functions yield to a queued Vue refresh instead of re-rendering competing read surfaces.

`v27.36.8` remains the historical read-sequencing contract-alignment predecessor: Absence/Time Bank full and period-only reads share `readSequence`, and only the winning full read publishes the canonical projection.

## Absence/Time Bank browser parity (v27.36.4)

Vue emits `dutylog:absence-time-bank-projection` after a winning authoritative refresh. The legacy adapter consumes only planner/account read projections for Calendar, Today and selected-day panels; retired Vacation/Overtime route renderers are not called. Modal launches from Today/Calendar preserve route context, while explicit domain launches still navigate.

## CI artifact quota resilience (v27.36.3)

Frontend and browser report uploads are now best-effort diagnostics. JaCoCo is reduced to XML/CSV; Playwright reports are retained only for failed runs. Quota exhaustion cannot bypass or replace the strict frontend, Maven, Playwright, Docker, migration-smoke or staging gates.

## Timer regression compile-coverage hotfix (v27.36.2)

The browser timer implementation from v27.36.1 remains unchanged. The timer regression contract is now Java 17-valid, uses whitespace-normalized source matching and is named for the local frontend-contract compiler; source-only hotfix tests are also compiled before Maven.

## Delivery foundation and first migrated domain (v27.36.0)

The frontend toolchain is exact-pinned (`Node 20.18.1`, `npm 10.8.2`) and installs from a committed npm lockfile through `npm ci`. The canonical backend OpenAPI YAML is deterministically transformed into reviewed TypeScript schema and operation types; `--check` fails when generated output drifts. New Vue API integrations use the generated operation contract over the shared same-origin CSRF/request-ID transport.

The application records only bounded diagnostic metadata: release, public route, request ID, request method/path/status and failure source. Vue render failures, boot failures and unhandled promise rejections display controlled recovery UI. Unexpected errors are still reported to `console.error` and are not suppressed from the strict Playwright collector.

## Browser-safe library output (v27.34.3)

The Vue shell is built in Vite library mode but consumed directly by browsers from Spring Boot static resources. The build replaces `process.env.NODE_ENV` at compile time and audits the emitted JavaScript for Node-only runtime globals. No `process` shim is exposed to the browser.

## Secondary navigation active-route contract (v27.34.4)

When the active route is outside primary navigation, the visible More control and the matching modal item carry `aria-current="page"`.

## Current ownership

**Vue owns the application shell**: brand, profile entry, primary/secondary navigation, active-route presentation, online state, shared modal/toast hosts and design-system primitives.

**Vue also owns the bounded Absence & Time Bank domain**: absence journal, unified composer, credit/scenario editor, plan/fact/compensation explanation, responsive ledger, usages, reservations and FIFO forecast. The Vue feature uses generated operation contracts and never reproduces backend business authority.

**Vue also owns the bounded Calendar & Timeline domain**: Today, Calendar Month/Week/Day, focused-date navigation, hourly timeline composition and calendar-layer visibility. The generated-API store treats Spring Boot read models as authoritative and keeps only view state and optimistic presentation state locally.

**Vue also owns the bounded Productivity domain**: Tasks/Inbox presentation, Task details/editor, selected-day Tasks, multiple daily Notes and Important Days. The existing dataLayer remains the only offline queue/snapshot infrastructure and is reached through typed bridge operations.

**Legacy product screens remain authoritative** for Payroll, Settings and Admin. The selected-day Calendar shell remains a temporary compatibility host for still-legacy shift/schedule/overtime controls; its Tasks/Notes/Important bodies are Vue-owned.

```text
frontend/src
├── app                 Vue shell, navigation and cross-shell UI state
├── platform            same-origin transport, bridge, router and readiness
├── shared/ui           typed design-system primitives
├── shared/overlays     modal and toast infrastructure
├── styles              tokens, shell and responsive behavior
└── features            bounded product domains added only after Gate A closes
```

## State ownership

- Spring Boot owns business rules and persisted truth.
- Legacy product read models remain authoritative only for domains that have not migrated; Absence/Time Bank, Calendar/Timeline and Productivity use bounded Vue stores over backend read models.
- The Calendar/Timeline store owns only focused date, view mode, loading/error state and the current authoritative range snapshot; it never calculates canonical shifts, absences, task ownership or recurrence rules.
- The shell receives only a frozen snapshot: route, allowed navigation, language, online/module readiness and safe profile display fields.
- Pinia owns explicit shell and migrated-domain UI state; it never mirrors one global mutable product state.
- Local form drafts remain local to each migrated composer or the temporary selected-day editor island.
- Offline queue and synchronization remain a separate infrastructure boundary; Productivity reuses that queue through named bridge operations and never creates a second mutable offline owner.

## Routing and bridge

Vue Router still uses memory history. The released hash route remains authoritative for application-level navigation in `v27.38.8`; the Absence/Time Bank and Today/Calendar route bodies are Vue-owned. Vue navigation calls the named `DutyLogLegacyPlatform.navigate(view)` capability; legacy routing publishes the new frozen snapshot back through `subscribe(listener)`.

Allowed transition capabilities are:

```text
snapshot
subscribe
navigate
openModal
logout
attachCalendarEditor
openCalendarDay
closeCalendarDay
openTaskCreate
openTaskDetails
openQuickActions
openImportantDetails
offlineUpdateNote
offlineSetTaskDone
offlineCaptureInbox
offlineSelectedDay
offlineSync
```

Vue must not read `window.state`, query `#tabbar` or create a second mutation owner. The Absence & Time Bank workspace retires its route/modal owners after Vue readiness. The Calendar & Timeline workspace retires legacy Today/Calendar read roots, mounts only the selected-day editor island and converts historical render calls into queued Vue refresh requests. The Productivity workspace retires legacy Tasks/Important route roots and selected-day Tasks/Notes/Important bodies, while preserving named bridge adapters for offline infrastructure and historical entry points. Payroll, Settings and Admin retain their existing owners until their bounded migration releases.

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
