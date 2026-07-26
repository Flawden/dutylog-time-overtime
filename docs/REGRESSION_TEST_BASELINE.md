# DutyLog regression test baseline

Status: v27.11.3.

Historical checkpoint — Status: v27.2.31.

Current extension: v27.11.3 unifies shift templates, dated occurrences and reminder delivery under the canonical IANA timezone. Current application baseline: 85 Java test classes / 446 `@Test` methods and 19 Chromium Playwright scenarios, plus the backup tooling shell self-test.

Historical foundation: v27.2.29 security baseline remains preserved by all later releases.

Historical extension: v27.2.31 adds an authenticated, CSRF-aware deployment smoke-test regression.

Historical extension: v27.2.30 adds host-nginx deployment, loopback publication and two-stage smoke-test guards.

This release converts the successful v27.2.6 manual acceptance pass into an automated safety net. The goal is not a vanity coverage percentage; every test names a product promise that must remain true.



## v27.11.3 Shift Template & Reminder Timezone Hotfix extension

- `ShiftTypeServiceTest` protects template rebasing from UTC+5 to UTC+3 and confirms untimed day-off templates are unchanged.
- `ProfileControllerTest` proves a canonical timezone update persists the projected built-in times.
- `NotificationServiceTest` proves a next-day projected shift reminder is calculated from `shiftStartInstant`.
- `ShiftOccurrenceFrontendContractTest` protects authoritative template refresh and occurrence-based reminder wiring.
- `important-timezone.spec.js` checks template values, immutable occurrence projection and notification time in one user journey.
- Flyway remains V33.

## v27.11.2 E2E Stability Hotfix extension

- `editor-modals.spec.js` waits for the post-assignment `/api/calendar` response before navigating, so an aborted in-flight refresh cannot become a false browser-console failure.
- `important-timezone.spec.js` protects both the compact source range and canonical source date without coupling the test to an unused long-date presentation.
- Production shift occurrence logic and Flyway V33 are unchanged.


## v27.11.1 Shift Occurrences & Calendar Projection extension

- `ShiftOccurrenceServiceTest` proves absolute identity survives a timezone move, `08:30 GMT+5` becomes `06:30 GMT+3`, a late shift can move completely to the next date, unrelated note saves do not guess legacy zones, and explicit migration affects only selected rows.
- `ShiftOccurrenceFrontendContractTest` protects occurrence segmentation, projected-date indexing, migration UI, authoritative refresh and Service Worker activation.
- `important-timezone.spec.js` covers both same-day reprojection and a complete `03 July 23:00 → 04 July 01:00` date move.
- OpenAPI documents the shift occurrence and legacy migration contracts.
- Flyway V33 adds immutable occurrence snapshot columns and an overlap index.


## v27.10.0 Task Details extension

- `TaskServiceTest` protects description persistence, clearing, search participation, length validation and owner isolation.
- `TaskControllerTest` covers authoritative single-task reads through legacy and `/api/v1` routes, including foreign-owner `404` behaviour.
- `TaskDetailsFrontendContractTest` protects the separate read-first modal, explicit edit boundary, description rendering and offline snapshot fallback.
- `task-details.spec.js` verifies details open from a card, description persistence, checklist interaction and reload behaviour.
- Flyway V32 adds nullable `day_tasks.description` without turning quick capture into a mandatory structured form.


## v27.9.4 Overtime Split Projection Contract Hotfix extension

- Cross-midnight credit E2E asserts the seven-hour selected-day segment and the full eight-hour account balance independently.
- Credit usage references expose `allocationPartIndex` and `allocationPartCount`.
- Paged ledger rendering no longer depends on a previously loaded full overtime account to show split-part badges.
- Backend and frontend contracts verify stable split metadata and rendering.

## v27.9.3 Overtime Preflight Integrity Hotfix extension

- `OvertimeServiceTest` proves a rejected usage create does not add a ghost row and a rejected usage edit preserves the original hours, reason and allocation.
- `TaskAndShiftEditorsFrontendContractTest` protects the intentional `delete entire time-off` wording.
- `overtime-editor-modals.spec.js` explicitly sets zero break and zero planned deduction before asserting an eight-hour overnight credit.
- Flyway remains at V31 because this hotfix changes command ordering and tests, not schema.


## v27.9.2 Overtime Ledger Integrity Hotfix extension

- `OvertimeServiceTest` protects two credits and two usages when one split time-off is deleted.
- `OvertimeLedgerIntegrityFrontendContractTest` protects per-allocation fallback, detached-fragment rendering and whole-time-off labels.
- `overtime-editor-modals.spec.js` verifies the complete staging scenario and checks the surviving account through the API.
- Flyway remains at V31 because the hotfix changes transactional and browser behaviour, not schema.

## v27.9.1 Overtime Allocation Rendering Hotfix extension

- `OvertimeIntervalEngineFrontendContractTest` proves exact ranges use the defined `formatDateHuman` helper and forbids the missing `formatDate` symbol.
- `overtime-editor-modals.spec.js` creates `17:00–01:00`, consumes all eight hours and verifies both midnight-split labels while the shared fixture rejects browser page errors.
- `release-check.sh` executes `allocationRangeLabels()` in a Node VM with a cross-midnight allocation.
- Flyway remains at V31.

## v27.9.0 Overtime Interval Engine extension

- `OvertimeServiceTest` proves exact source-minute ranges, timezone reprojection, deterministic restoration after deletion and legacy reconstruction.
- `OvertimeControllerTest` covers preview/migrate aliases, module/CSRF boundaries and exact reconstructed API output.
- `OvertimeIntervalEngineFrontendContractTest` guards one timezone UI, exact range rendering, cross-midnight segmentation, migration wizard and shift work/break wording.
- `PostgreSqlMigrationContractTest` and release-check keep Flyway continuous through V31.
- Existing browser scenarios continue to verify authoritative calendar refresh using the single canonical timezone.

## v27.8.1 Timezone Projection Refresh extension

- `TimezoneProjectionRefreshFrontendContractTest` protects profile-before-calendar boot ordering, fresh calendar propagation, IndexedDB snapshot bypass and the joint calendar/ledger refresh.
- `important-timezone.spec.js` now reproduces the staging bug with an existing dated shift before changing zones, then proves the card refreshes to `08:30 Asia/Yekaterinburg → 06:30 Europe/Moscow` without stale `Europe/Kyiv`.
- Flyway remains at V30 because the fix changes projection refresh order, not persisted data.

## v27.8.0 Zoned Work Intervals extension

Automated coverage now additionally verifies:

- one dated work shift resolves to stable UTC start/end instants;
- `08:30–17:00 Asia/Yekaterinburg` projects to `06:30–15:00 Europe/Moscow` without changing work semantics;
- day/month API responses carry work/display projections and elapsed/net minutes;
- display-timezone saves refresh the active month without rewriting schedule records;
- new calculated overtime credits persist absolute interval identity and source IANA timezone;
- duration and overlap checks use actual instants across DST transitions;
- unchanged edits cannot move an existing overtime interval after the account work timezone changes;
- interval edits retain the credit's original source timezone;
- V30 leaves historical local-only overtime unmodified;
- the ledger prefers display projection while retaining source work context.

Manual staging acceptance checks shift projections, timezone reload behaviour, new overtime rows, legacy-row stability and unchanged FIFO balances.


## v27.7.1 Task and ledger layout hotfix extension

Automated coverage now additionally verifies:

- day-task cards use a stable checkbox/body/delete grid;
- inline subtasks occupy the content row without the old mobile offset;
- the overtime table exposes a dedicated Actions header and credit-level action cell;
- FIFO usage controls are explicitly labelled as usage edit/delete actions.


## v27.7.0 Time Foundation extension

Automated coverage now additionally verifies:

- persisted work and display IANA timezone settings, validation and reload behaviour;
- one absolute server instant projected through `/api/time/context` and `/api/v1/time/context`;
- deterministic DST gap and overlap resolution;
- overnight, 24-hour and DST-crossing work intervals measured in real elapsed minutes;
- notification ordering/filtering by `Instant` and explicit display projections;
- Telegram delivery deduplication by `remind_at_instant` with a legacy-local fallback for records that predate absolute identity;
- task overdue calculation in the user's work timezone;
- browser calendar dates remaining work-zone based while absolute UI timestamps use display timezone;
- Flyway V29 continuity and the new `TIMESTAMPTZ` migration contract.

Manual staging acceptance additionally checks that changing display timezone never rewrites birthdays, important dates, notes, task dates, shift source data or legacy overtime rows. Absolute shift and new overtime display projections are allowed to change.

## v27.6.3 task polish and consistency extension

- `TaskServiceTest` protects final-state parent/subtask deadline rules, same-day acceptance, clearing and open-first day/range order.
- `TaskControllerTest` proves stable errors and compatible behaviour through legacy and `/api/v1` routes.
- `TaskAndShiftEditorsFrontendContractTest` protects V28, client validation, graphical progress and completed grouping.
- `task-modules.spec.js` covers invalid deadline feedback, persisted subtask dates, progressbar semantics and immediate optimistic reordering.
- Flyway V28 adds a nullable date-only deadline without expanding subtasks into recursive tasks.

## v27.6.2 tasks and subtasks extension

- `TaskServiceTest` covers ordered creation, reconciliation, checklist-text search, owner-scoped child updates and explicit parent completion.
- `TaskControllerTest` protects create/update payloads, the versioned child PATCH route, module guards and foreign-owner `404` behaviour.
- `TaskAndShiftEditorsFrontendContractTest` protects the one-level editor, compact progress and non-recursive persistence contract.
- `task-modules.spec.js` covers the browser flow `0/2 → 1/2 → 2/2`.
- Flyway V27 creates ordered cascade-owned `task_subtasks`; recursive nesting is intentionally absent.


## v27.6.1 quick capture polish extension

- `MobileTasksInboxFrontendContractTest` now protects the collapsed Inbox tray, direct universal draft field and module-aware note/important-date actions.
- `task-modules.spec.js` preserves the complete `+ → text → Inbox → task` path without the removed intermediate capture modal.
- Quick add remains visible when any of Tasks, Notes, Important dates or Overtime is enabled.
- Existing Inbox API, IndexedDB idempotency, task conversion and Flyway V26 remain unchanged.


## v27.6.0 mobile tasks and Inbox extension

- `InboxServiceTest` covers quick capture, idempotent client operation ids, owner isolation, archive/restore, delete and atomic conversion into a structured task.
- `InboxControllerTest` covers `/api/inbox` and `/api/v1/inbox`, module guards, CSRF, authentication, validation and foreign-id indistinguishability.
- `MobileTasksInboxFrontendContractTest` protects the dedicated task modal, mobile full-screen editor, floating quick action, explicit overtime wording and IndexedDB `captureInbox` queue.
- `TaskServiceTest` now covers lower-case category/tag normalisation, metadata suggestions, tag-aware search and service-level text length enforcement.
- `task-modules.spec.js` uses the new task editor and adds the complete quick-capture → Inbox → task browser flow.
- Flyway V26 creates `day_task_tags` and `inbox_items` while preserving all v27.5.2 Telegram and v27.5.0 backup/recovery checks.


## v27.5.2 Telegram detached-owner hotfix

- `TelegramLinkDetachedOwnerIntegrationTest` reproduces the polling boundary where the repository/service transaction has ended before the command handler reads the linked account.
- The test proves the persisted IANA timezone remains readable from the returned detached `AppUser`, preventing the production `LazyInitializationException` seen in `/today`, `/tomorrow` and quick-action aliases.
- The repository entity graph is the regression boundary; no Open Session in View workaround or global eager mapping is introduced.


## v27.5.2 Telegram command menu and quick actions extension

- `TelegramBotServiceTest` verifies the `setMyCommands` HTTP payload, descriptions and fail-closed registration guards.
- Every bot response carries a persistent compact reply keyboard with six safe read-only actions.
- `TelegramCommandServiceTest` proves button labels dispatch to the same timezone-aware handlers as slash commands.
- v27.5.1 coverage for partial `/today` and `/tomorrow` summaries and compact mobile synchronization remains preserved.


## v27.5.0 backup and recovery extension

- Backup creation fails closed without the environment or active Compose file, prevents concurrent writers and atomically publishes a verified dump plus SHA-256.
- Retention is regression-tested with a fake Docker boundary, so the newest backup and checksum survive while old pairs are removed.
- Backup freshness checks enforce age, checksum and archive readability contracts.
- A failed real restore restarts an application that was running before the attempt.
- The isolated restore drill uses generated temporary container/volume names, no network and no published ports, and always performs exact cleanup from its own process.
- Systemd service/timer rendering is regression-tested without modifying the CI host service manager.


## v27.4.3 reminder timezone and sync UX extension

- Browser reminder API responses include an absolute UTC instant derived from the user's saved IANA timezone.
- Frontend delivery compares the absolute instant with `Date.now()` and uses the DutyLog timezone for source-date polling.
- Task create/edit paths accept and persist a 3-minute reminder.
- Overtime credits use explicit start/end fields only; the short text interval is absent from runtime HTML and JavaScript.
- Manual synchronization visibly enters a busy state and reports a final result.
- The browser regression baseline includes 12 Playwright scenarios.

## v27.4.2 timezone and critical regression extension

- `remember-me.spec.js`: persistent-cookie restoration in a fresh browser context, parallel bootstrap reads and logout revocation.
- `important-timezone.spec.js`: compact generated timezone selector, explicit profile save, removal of manual region/offset controls and persistence after reload.
- `editor-modals.spec.js`: task field persistence plus custom shift create/edit/assign/reload.
- `smoke-test.sh`: authenticated read-only profile, module, session and identity checks after the existing CSRF login.
- `production-smoke-test.sh`: HTTPS-only authenticated wrapper suitable for post-deploy production verification.

## Manual acceptance captured

The following behaviours were verified manually before this baseline was created:

- a shift and emoji marker persist while Notes and Overtime are disabled;
- notes survive day/month navigation, refresh, logout/login and module disable/enable;
- exported Markdown ZIP contains the persisted note;
- Telegram status is not requested while the Telegram module is off;
- task reminder controls are disabled while Notifications is off;
- browser reminders are delivered for tasks and shifts;
- schedule fill, templates, overtime, FIFO usage, English UI, appearance settings and password minimum continue to work.

## Automated matrix

| Product promise | Automated guard |
|---|---|
| Calendar days retain identity and do not collapse into `state.days[undefined]` | `CalendarMonthReloadContractTest` |
| Fill persists every date and a fresh calendar read returns it | `CalendarFillPersistenceContractTest`, `DayEntryServiceTest` |
| Disabled Notes/Overtime do not block shift or marker saves | `DayModuleIsolationTest` |
| Hidden note/overtime data is preserved and reappears after re-enable | `DayModuleIsolationTest` |
| Notes export is owner-scoped, bounded and valid | `ExportControllerTest`, `NoteExportServiceTest` |
| Shift/task/important-day/digest reminder times are calculated correctly | `NotificationServiceTest` |
| Completed tasks and silent shift types create no reminder | `NotificationServiceTest` |
| Reminder data never leaks between users | `NotificationServiceTest` |
| Notifications API is guarded by its module and validates input | `NotificationControllerTest` |
| Telegram/Notifications dependency cascade remains consistent | `ModuleDependencyTest` |
| Task reminder fields persist and stale lead minutes are cleared | `TaskReminderServiceTest` |
| Task CRUD, day/range lists, board filters and pagination remain correct | `TaskServiceTest`, `TaskControllerTest` |
| Task API keeps validation, CSRF, authentication, module and ownership boundaries | `TaskControllerTest` |
| Important-day CRUD, owner-scoped lists and validation remain correct | `ImportantDayServiceTest`, `ImportantDayControllerTest` |
| Monthly 31st-day and yearly leap-day recurrence rules remain stable | `ImportantDayServiceTest` |
| Important dates API keeps v1 aliases, CSRF, authentication, module and ownership boundaries | `ImportantDayControllerTest` |
| Built-in and custom shift types keep defaults, CRUD, ownership and deletion semantics | `ShiftTypeServiceTest`, `ShiftTypeControllerTest` |
| 2/2, day/night/48 and weekday-aware 5/2 patterns survive month, year and leap-day boundaries | `CalendarPatternServiceTest`, `CalendarPatternControllerTest` |
| Schedule fill preserves notes, emoji and overtime while overwrite rules remain stable | `CalendarPatternServiceTest` |
| Browser presets keep their canonical sequences and rotate 5/2 by the selected weekday | `ScheduleTemplateFrontendContractTest` |
| Disabling Notifications tears down browser polling | `BrowserNotificationFrontendContractTest` |
| A stale `MODULE_DISABLED:notifications` response cannot become a recurring 403 loop | `BrowserNotificationFrontendContractTest` |
| Task reminder controls reflect the Notifications module | `BrowserNotificationFrontendContractTest` |
| Quick-scenario defaults, CRUD, FIXED_TIME consistency and deletion semantics remain stable | `QuickScenarioServiceTest`, `QuickScenarioControllerTest` |
| Quick-scenario API keeps v1 aliases, validation, CSRF, authentication, module and ownership boundaries | `QuickScenarioControllerTest` |
| Overtime interval splitting, overlap protection and FIFO remain correct | `OvertimeServiceTest` |
| Overtime pages, filters, exports, usage reallocation and deletion rules remain stable | `OvertimeAccountQueryServiceTest`, `OvertimeControllerTest` |
| Overtime API keeps legacy/v1 aliases, validation, CSRF, authentication, module and ownership boundaries | `OvertimeControllerTest` |
| Mobile/web authentication boundary, ownership and module guards remain enforced | `MobileSecurityBoundaryTest`, `OwnershipIsolationTest`, `ModuleSecurityTest` |
| Mobile access/refresh rotation, expiry, logout and session ownership remain stable | `MobileAuthServiceTest`, `MobileAuthLifecycleControllerTest`, `ProfileSessionControllerTest` |
| Android v1 operation ids are owner-scoped and applied exactly once | `MobileSyncServiceTest`, `MobileSyncControllerTest`, `MobileV1ContractTest` |
| Android v1 conflicts, tombstones and per-item rejections preserve neighbouring writes | `MobileSyncServiceTest`, `MobileSyncControllerTest` |
| PostgreSQL foreign keys never target tables missing from the migration history | `PostgreSqlMigrationContractTest`, `migration-smoke-test.sh` |

## Running the gate

```bash
mvn verify
bash deploy/scripts/release-check.sh
```

JaCoCo HTML output (generated by Maven `verify`, not by IntelliJ's plain JUnit runner):

```text
target/site/jacoco/index.html
```

Detailed Windows/IntelliJ instructions: [`docs/TESTING.md`](TESTING.md).

GitHub Actions uploads the same directory as the `jacoco-report` artifact even when a later CI step fails.

## Interpretation

A green test suite means the listed contracts still hold. It does not replace exploratory/manual testing for browser permissions, operating-system notification presentation, responsive layout, service-worker lifecycle or real PostgreSQL deployment. Those remain acceptance checks at release boundaries.


## v27.2.16 profile and administration extension

- `ProfileControllerTest`: safe profile reads, full/clearing updates, locale, onboarding, Theme Builder allow-list, normalization, clamping, corrupt stored JSON, validation, authentication and CSRF.
- `ProfileSessionControllerTest`: owner-scoped device lists, token secrecy, one-session revocation, CSRF, IDOR-safe `404`, and revoke-all after password changes.
- `AppSettingsServiceTest`: default/database registration sources, audit metadata and legacy boolean parsing.
- `UserAdminServiceTest`: search, filters, pagination, current/bootstrap flags, promotion/demotion safety, last-admin protection, password reset and session revocation.
- `AdminControllerContractTest`: operational status secrecy, full admin API contract, stable error envelopes, registration toggle and CSRF.


## v27.2.17 admin test context hotfix

- `UserAdminServiceTest` no longer sets an incomplete bootstrap-admin property pair on the full Spring context.
- The service is constructed inside the transactional test with an explicit bootstrap username, so bootstrap-admin protections remain covered without invoking production bootstrap side effects.


## v27.2.18 mobile auth and sync lifecycle extension

- `MobileAuthServiceTest`: token hashing, login validation, refresh rotation, expiry, access authentication, last-used throttling, logout, session flags, owner isolation and device normalization.
- `MobileAuthLifecycleControllerTest`: legacy/v1 auth routes, refresh replay rejection, logout by access or refresh, owner-scoped device management and stable validation envelopes.
- `MobileSyncServiceTest`: direct validation, idempotent replay, owner-scoped operation ids, optimistic conflicts, no-op/module rejection, malformed-date batch isolation, clear precedence, tombstones and foreign-shift rejection.
- `MobileSyncControllerTest`: HTTP-level malformed-item isolation, same-batch duplicate ids, bean validation and legacy-delete versus v1-tombstone compatibility.
- The documented v27.2.17 baseline is corrected to 193 `@Test` methods; v27.2.18 contains 223.


## v27.2.19 PostgreSQL migration and CI metadata hotfix

- `PostgreSqlMigrationContractTest` scans the ordered PostgreSQL Flyway chain and validates every `REFERENCES` target against tables created by the same or an earlier migration.
- The clean PostgreSQL Docker smoke test remains the authoritative runtime check.
- CI, staging and production derive release metadata from `pom.xml`; semantic versions are no longer duplicated as stale literals in workflow files.

## v27.2.20 Telegram bot and delivery hardening

- `TelegramCommandServiceTest` locks command aliases, parsing, task/overtime/time-off mutations, summaries and invalid input.
- `TelegramBotServiceTest` locks polling offsets, link codes, unlinked chats, command delivery, malformed updates, fail-closed Telegram responses and token redaction.
- `TelegramNotificationServiceTest` locks due windows, deduplication, retry semantics, per-link isolation and reminder formatting.
- `TelegramControllerTest` locks module guards, link-code/status/settings/unlink endpoints, authentication and CSRF.


## v27.2.21 Telegram date-validation hotfix

- Impossible explicit Telegram dates are converted to the stable `BAD_REQUEST` API contract.
- Telegram HTTP test expectations are fully registered before the first mock request.


## v27.2.22 security infrastructure and auth hardening

- `ApiVersionFilterTest`: stable v1 metadata and legacy mobile deprecation headers.
- `SecurityHeadersFilterTest`: CSP, HSTS, frame, referrer, permissions and MIME-sniffing protections.
- `RequestDiagnosticsFilterTest`: trusted/generated request IDs, reflection rejection and failure-path correlation.
- `BearerTokenAuthenticationFilterTest`: public-route exclusions, user/admin authorities, token touch, invalid-token envelopes and case-insensitive Bearer schemes.
- `AuthenticationRateLimitFilterTest`: shared web/legacy/v1 alias buckets, independent IPs, forwarded/real IP handling and window reset.
- `SecurityEventLoggerTest`: structured audit fields, request context, IP precedence, control-character flattening and bounded values.
- `ApiErrorInfrastructureTest`: defaults, module metadata, writer contract, exception factories and hidden 500 details.
- `SecurityInfrastructureContractTest`: integrated headers, mobile version lifecycle, JSON 401/403 responses and correlation IDs.

## v27.2.23 security test contract and secret-safe logging hotfix

- JSON error responses are asserted by media-type compatibility, so an explicit UTF-8 charset remains valid.
- HTML navigation tests send `Accept: text/html`, while `/api/**` continues to use stable JSON 401 responses.
- Unexpected exception logs retain request correlation and exception type but omit throwable messages and stack traces that may contain secrets.



## v27.2.24 coverage floor and startup/module extension

- `AdminBootstrapServiceTest`: absent/partial configuration, credential validation, first admin creation, default-shift seeding, promotion, forced reset and one-time legacy cleanup.
- `ModuleRegistryContractTest`: normalized lookup, unique stable keys/orders, known acyclic dependencies and immutable contract lists.
- `ModuleServiceContractTest`: regular/admin visibility, locked modules, null/unknown updates, persistence, dependency activation and structured disabled-module errors.
- `CurrentUserServiceTest`: missing, unknown and valid principals.
- `NoteExportServiceTest`: count/select race limits, blank filtering, audit event, ZIP layout and YAML scalar escaping.
- `mvn verify` now enforces at least 88% instruction coverage and 70% branch coverage at bundle level.
- Baseline: 61 test classes and 327 `@Test` methods.


## v27.2.25 Playwright browser E2E extension

- `auth-onboarding.spec.js`: registration, automatic login, language persistence, Minimum preset and no repeated onboarding after reload.
- `calendar-persistence.spec.js`: shift, emoji and Markdown note persistence across month navigation and an authoritative reload.
- `task-modules.spec.js`: Tasks module activation, task creation/completion, disable/enable survival and restored UI state.
- `mobile-layout.spec.js`: phone viewport width, calendar selection and selected-day panel usability.
- `pwa-offline.spec.js`: active service worker, controlled reload, IndexedDB snapshot and offline shell startup.
- Happy-path fixtures reject browser console errors, uncaught exceptions, failed same-origin requests and unexpected same-origin HTTP errors.
- Java baseline remains 61 classes / 327 `@Test`; browser baseline is 5 Playwright tests.


## v27.2.26 Playwright selector and accordion hotfix

- Shift chips expose `aria-pressed` as the stable selected-state contract; E2E no longer assumes a non-existent visual `.on` class.
- `openDayModule` expands closed `<details>` blocks before tests interact with Notes controls.
- Calendar persistence and PWA offline tests now wait for the real debounced `PUT /api/days/{date}` only after the editor is visible.
- `.gitattributes` enforces LF in the repository and protects Linux deployment scripts from Windows line-ending conversion.
- Baselines remain 61 Java classes / 327 `@Test` methods and 5 Playwright tests.

## v27.2.27 Playwright marker accordion hotfix

- `calendar-persistence.spec.js` explicitly opens `data-day-module="core"` before filling the custom marker input.
- After an authoritative reload, the scenario reopens Notes and Marker before asserting persisted editor values.
- PWA/offline, onboarding, mobile layout and task-module scenarios remain unchanged.
- Baselines remain 61 Java classes / 327 `@Test` methods and 5 Playwright tests.


## v27.2.28 staging deployment gate and diagnostics hardening

- `Deploy staging` independently enforces `mvn verify`, the JaCoCo floor, release checks and all five Playwright scenarios before building an immutable image.
- The image build and clean PostgreSQL migration smoke test run even when no VPS is configured.
- Remote staging deployment is gated by the GitHub Environment variable `DUTYLOG_DEPLOY_ENABLED=true`.
- A disabled gate is a successful, explicit skip and cannot create `staging-tested-tree-*`; production promotion therefore remains impossible until a real staging smoke test succeeds.
- Enabled but incomplete environments fail before SSH with a list of missing setting names and without secret values.
- Production uses the same preflight but remains fail-closed.
- Baselines remain 61 Java classes / 327 `@Test` methods and 5 Playwright scenarios.

## v27.2.29 final security and product audit hardening

- `WebSessionInvalidationTest` proves that password changes and admin-role demotion invalidate existing browser sessions before another protected request is authorized.
- `WebAccountStateFilterTest` locks matching/stale auth-version behavior and keeps Bearer principals outside the browser-session check.
- `ClientIpResolverTest` and the expanded `AuthenticationRateLimitFilterTest` prove that untrusted forwarding headers cannot split brute-force buckets while managed edge headers remain usable.
- `ProfilePasswordTest`, `UserAdminServiceTest` and `AdminBootstrapServiceTest` lock the unified password minimum and auth-version increments.
- `MobileAuthTokenCleanupServiceTest` keeps expired mobile-session rows bounded.
- Flyway adds V23 for `users.auth_version`.
- Baseline: 65 Java test classes / 340 `@Test` methods and 5 Playwright scenarios.

