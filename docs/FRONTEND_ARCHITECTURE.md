## v27.41.8 shared runtime segmentation

Exact v27.41.7 proved content-hashed route/page chunks but left a 565411 B shared `main` chunk. v27.41.8 keeps those async boundaries and adds coarse manual chunks for npm vendor runtime, generated API contract, platform infrastructure and the intentionally eager Settings runtime owner. The split changes delivery/cache boundaries only; Vue ownership, hash routing, generated API usage and the single `dataLayer` offline mutation/sync boundary are unchanged.

# Frontend architecture

## v27.41.7 delivery segmentation

The Vue shell remains one application/runtime authority, but production delivery is no longer one monolithic JS artifact. The stable entry owns boot/navigation/shared runtime; route/page surfaces cross explicit `defineAsyncComponent` boundaries and Vite emits content-hashed `chunks/*.js`. Domain ownership is unchanged: lazy delivery must never create a second store, router, offline queue or backend authority.


Status: Vue owns the shell, all routed user-facing screens and offline/sync presentation after readiness. DutyLog v27.41.6 keeps those ownership boundaries unchanged and only enriches the existing TimeBank integrity presentation with linked-audit context, conservative next-step guidance and a mobile-safe record grid. First-run onboarding remains one explicitly bounded post-ready legacy presentation exception; `dataLayer` remains the single offline mutation/sync owner. Route/workspace bundle segmentation remains the next frontend-delivery task.


## Vue Settings, Workspace & Integrations ownership (v27.39.0)

Vue owns Settings section navigation plus Profile, Language, Modules, Calendar Sync and Appearance/Workspace Studio. Online writes use the generated OpenAPI client; Spring Boot remains authoritative for profile validation, module dependencies/locks, sessions and integration secrets. Telegram is exposed through a canonical `/api/v1/telegram` alias for the migrated UI.

Time, Schedule/Calendar Layers and Notifications are now native Vue Settings components. `#settingsLegacyHost`, `#settingsLegacyParking` and their attach/open bridge methods are retired; legacy Settings renderers yield after `data-vue-settings-workspace=ready`. Later v27.40.x cuts retired Payroll/Admin and all post-ready legacy screen routing; the offline dataLayer remains an intentional infrastructure boundary.

Appearance keeps UI Contract v2 and delegates only root visual application/current global shell synchronization through a typed bridge. It does not add a second workspace/theme model. ADR-008 disables public production source maps by default and keeps controlled frontend diagnostics secret-free.

As of v27.40.12 the compatibility command surface was narrowed again: generic `openModal` and historical Task/Important opener capabilities were removed from `LegacyBridge` / `DutyLogLegacyPlatform`. Subsequent v27.40.x cuts also removed legacy navigation and Payroll/Admin screen ownership; logout, bounded synchronization adapters and the offline `dataLayer` remain explicit compatibility/infrastructure boundaries.
As of v27.40.13 Vue owns hash route state directly. `hashRoute.ts` reads, writes and subscribes to `location.hash`; `DutyLogLegacySnapshot` contains no route field and `LegacyBridge` contains no navigation capability. The legacy `applyRoute()` listener remains only to drive pre-Vue recovery and the still-legacy Payroll/Admin route-entry side effects from the same canonical hash. Vue owns hash route state; legacy no longer republishes it as application state.

As of v27.40.15 Vue also owns route-access policy after authoritative profile/module state is loaded: non-admin Admin requests and disabled Vacation/Overtime/Payroll/Tasks/Important routes canonicalize to Calendar. Vue owns the body route marker and Calendar selected-day route-exit close behavior. Once `data-vue-shell="ready"`, legacy `applyRoute()` is narrowed to Payroll/Admin route-entry side effects only; its full rendering/navigation branch exists solely for pre-Vue recovery.

As of v27.40.16 the Vue route owners also own their route-entry freshness: Overtime/Vacation refresh canonical account/planner state on entry, Today refreshes its dashboard bundle, and Today widget order/visibility comes directly from the persisted workspace definition plus authoritative module state. Productivity note creation is read-your-write from the create response so a post-create reload cannot overwrite a live editor draft.


## Vue Tasks, Notes & Important Days ownership (v27.38.0)

Vue now owns the bounded `productivity` domain: Tasks board/Inbox presentation, Task details/editor, selected-day Tasks, multiple daily Notes, Important Days board/details/editor and selected-day Important Dates. The feature uses the generated OpenAPI client for online reads/writes and independent read-sequence guards for latest-read-wins behavior. Spring Boot remains authoritative for scheduling/deadline validation, subtask completion, note order/persistence, Inbox conversion, recurrence/timezone projection and every persisted business rule.

Q-10 offline/reconnect reuses the existing legacy `dataLayer` queue and cached calendar snapshot through typed bridge operations. Vue does not create a second offline store. Note edits, task completion and Inbox capture may queue offline; selected-day productivity can read the cached snapshot; reconnect flushes the existing queue and reloads authoritative state. New-note creation remains disabled offline until create-identity semantics are owned by the queue.

The old Tasks/Important route roots and productivity modals are retired after Vue ownership is ready. As of v27.40.11 this includes the live Quick Actions modal: Inbox, Task, Note, Important, Overtime Credit and Absence quick-add flows are Vue-owned, while the old HTML modal exists only before Vue readiness as recovery fallback. Cross-domain Today/Calendar launches call `DutyLogVueDomains.productivity` by name rather than invoking hidden legacy DOM owners.

## Vue Calendar & Timeline strict type boundary (v27.37.1)

The public `DutyLogCalendarTimelineDomain.openDate` callback explicitly carries `string` and optional `CalendarMode` parameters. Pinia actions accept optional modes and resolve `mode ?? this.mode` inside the typed action body; no default parameter initializer references `this`. Runtime ownership and generated API behavior remain identical to v27.37.0.

## Vue Calendar & Timeline ownership (v27.37.0)

Vue now owns Today and the Calendar Month, Week and Day read surfaces through one bounded `calendar-timeline` feature. Its generated-API store owns focus date, view mode, stale-read sequencing, authoritative work-date loading and optimistic calendar-layer visibility with rollback. The Day view composes shifts, partial absences, timed tasks, important events, reminders and calendar-layer occurrences into one hourly timeline without reproducing backend business rules.

The legacy Today and Calendar read roots are retired after Vue readiness. As of v27.40.4 the selected-day mutation editor is also native Vue: `SelectedDayPanel.vue` owns `#panel` and its stable section IDs. Historical legacy selected-day renderers yield after the `data-vue-calendar-selected-day` readiness marker instead of mutating the Vue-owned nodes.

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

**Vue owns the application shell. Vue owns all user-facing screens after Vue readiness**: shell/navigation, Today, Calendar/selected day, Absence & Time Bank, Tasks/Notes/Important Days, Settings/Workspace, Payroll and Admin. Spring Boot remains authoritative for persisted domain rules, validation, authorization and canonical read/write semantics.

**The generated OpenAPI client is the canonical online browser boundary** for migrated domains. Vue/Pinia owns presentation state and bounded optimistic state; it does not duplicate backend business authority.

**dataLayer remains the single offline mutation/sync owner.** IndexedDB snapshots, the offline mutation queue and reconnect flush continue behind narrow bridge operations. Vue must not create a second queue or independently flush the same mutations.

**First-run onboarding is the only intentionally live post-ready legacy presentation exception in v27.40.30 and is now a deliberate bounded compatibility surface, not pending screen-migration debt.** Offline/sync presentation is Vue-owned: `AppShell.vue` owns visible status/save feedback and `OfflineSyncModal.vue` owns queue/failed/diagnostic actions. The old `.head` / `#offlineSyncDialog` are source recovery only and are physically removed after successful Vue readiness. Async legacy boot/profile publication must therefore tolerate those recovery nodes disappearing while network awaits are in flight; no post-ready legacy identity write may be required for Vue state publication. The Vue logout control is likewise independent of recovery chrome: `DutyLogLegacyPlatform.logout()` publishes `dutylog:logout-request`, while the existing session action in `70-user-boot.js` remains the single flush/CSRF-aware `/logout`/redirect executor.


Historical v27.40.29 contract: **First-run onboarding is the only intentionally live post-ready legacy presentation exception in v27.40.29.**

The v27.40.30 ownership audit intentionally closes the retirement phase with that exception in place. `#firstRunOnboarding` is marked `data-bounded-legacy-owner="first-run-onboarding"`; no second bounded legacy presentation owner is allowed. The overlay owns only first-run preset/module/profile completion presentation and the established service-worker registration handoff. It owns no hash route, normal Settings presentation, shell state, offline queue execution or routed product screen. Native Vue onboarding is deferred until product-level Guided Onboarding work or evidence demonstrates that this bounded surface itself is harmful.

Historical v27.40.24 ownership contract: **Known live legacy presentation is limited to first-run onboarding and offline/sync UX.** v27.40.25 supersedes the offline/sync half of that exception without replacing `dataLayer`.

The legacy overtime/usage migration fallback DOM is retired with the Absence/Time Bank owner after readiness. Its API/data migration semantics are not deleted; surfacing equivalent native Vue migration UX remains an explicit Functional Parity Sweep item before v27.40.x closes.

## State ownership

Spring Boot owns persisted domain state, authorization, validation, time/payroll/overtime semantics and canonical API projections. Vue Pinia stores own route-local/read-model/presentation state for every live user-facing screen. `dataLayer` owns only the established offline snapshot/outbox/reconnect infrastructure and exposes it through bounded bridge operations.

No post-ready legacy product-screen read model or mutable route owner is authoritative. Limited pre-Vue recovery may still render the server fallback shell until the Vue shell announces readiness.

## Routing and bridge

`frontend/src/app/hashRoute.ts` is the canonical browser hash transport. **Vue owns hash route state**: it reads, writes, subscribes to and guards the hash route, including Admin/module access canonicalization. Public Vue navigation uses `DutyLogVuePlatform.navigate(...)` / the hash-route helper; legacy code does not own post-ready route commits.

`LegacyBridge` is deliberately narrow. Its remaining capabilities cover snapshot/subscription/logout, domain-owner retirement, bounded Settings appearance/profile/module synchronization, Calendar-day offline writes and existing offline dataLayer operations. It has no generic navigation, modal, Task or Important opener command surface.

The bridge is a compatibility/infrastructure boundary, not a second application state model. New Vue domains must use generated API operations online and may use the existing offline bridge only where that queue already owns the mutation semantics.

## Shared design system

The first reusable primitives are `UiButton`, `UiBadge`, `UiCard`, `UiTabs`, `UiEmptyState`, `UiModal`, `ToastHost` and `AppIcon`. They reuse DutyLog theme tokens, visible focus, reduced motion and mobile bottom-sheet behavior. Migrated domains must compose these primitives instead of creating parallel button/card/modal systems.

## Safe fallback

The server-rendered `nextTopbar` and `tabbar` remain available only during pre-Vue recovery. On successful `dutylog:vue-ready` for the Vue shell, `shell-bootstrap.js` physically removes those nodes rather than merely hiding a duplicate shell.

The old `.head`, `#offlineStatus` and `#offlineSyncDialog` remain in the server source only for pre-Vue recovery. After `dutylog:vue-ready`, `shell-bootstrap.js` removes the old header/dialog and publishes `data-vue-offline-sync="ready"`; legacy DOM handlers yield while Vue keeps the stable `#offlineStatus` / `#offlineSyncDialog` browser selectors. `dataLayer` itself remains infrastructure and the sole offline mutation/sync executor.

First-run onboarding is the only intentionally live post-ready legacy presentation exception after v27.40.29.

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
