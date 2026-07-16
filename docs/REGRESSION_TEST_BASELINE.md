# v27.2.7 — Regression test baseline

Status: v27.2.7.

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
| Disabling Notifications tears down browser polling | `BrowserNotificationFrontendContractTest` |
| A stale `MODULE_DISABLED:notifications` response cannot become a recurring 403 loop | `BrowserNotificationFrontendContractTest` |
| Task reminder controls reflect the Notifications module | `BrowserNotificationFrontendContractTest` |
| Overtime interval splitting, overlap protection and FIFO remain correct | `OvertimeServiceTest` |
| Mobile/web authentication boundary, ownership and module guards remain enforced | `MobileSecurityBoundaryTest`, `OwnershipIsolationTest`, `ModuleSecurityTest` |

## Running the gate

```bash
mvn verify
bash deploy/scripts/release-check.sh
```

JaCoCo HTML output:

```text
target/site/jacoco/index.html
```

GitHub Actions uploads the same directory as the `jacoco-report` artifact even when a later CI step fails.

## Interpretation

A green test suite means the listed contracts still hold. It does not replace exploratory/manual testing for browser permissions, operating-system notification presentation, responsive layout, service-worker lifecycle or real PostgreSQL deployment. Those remain acceptance checks at release boundaries.
