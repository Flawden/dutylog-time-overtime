# DutyLog regression test baseline

Status: extended in v27.2.9 with the task regression suite.

Current extension: v27.2.25 adds a Chromium Playwright E2E baseline for browser wiring, persistence, modules, mobile layout and offline/PWA startup while retaining the v27.2.24 JaCoCo coverage floor.

This release converts the successful v27.2.6 manual acceptance pass into an automated safety net. The goal is not a vanity coverage percentage; every test names a product promise that must remain true.

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
