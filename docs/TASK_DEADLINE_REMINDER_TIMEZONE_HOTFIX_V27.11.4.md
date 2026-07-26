# DutyLog v27.11.4 — Task Deadline & Reminder Timezone Hotfix

## Product rule

A task deadline with both a calendar date and a clock time represents one real moment.
Changing the user's canonical IANA timezone must therefore change the displayed local date/time without changing the underlying instant or overdue state.

Example:

```text
Source deadline: 26.07.2026 14:10 Asia/Yekaterinburg (UTC+5)
Absolute moment: 26.07.2026 09:10Z
Europe/Moscow projection: 26.07.2026 12:10 (UTC+3)
```

If the source deadline was already overdue, the Moscow projection remains overdue because both values identify the same instant.

## Persistence model

Flyway V34 adds nullable snapshot fields to `day_tasks`:

```text
due_instant
due_source_timezone
due_source_date
due_source_time
```

New or explicitly edited timed deadlines capture all four values. The projected `due_date` and `due_time` remain in the existing API contract for old clients.

Date-only deadlines intentionally remain floating calendar dates. They are not converted into instants because “due on 26 July” is a civil-date promise rather than a precise moment.

## Timezone changes

Before the canonical profile timezone changes, DutyLog:

1. captures any still-legacy timed deadlines in the old profile timezone;
2. preserves their `dueInstant`;
3. projects the stored instant into the new timezone;
4. refreshes the calendar, task board, task details and notification schedule.

A projection may cross midnight and change the displayed due date. The task's organisational `date` stays on its original day; only its precise deadline is reprojected.

## Existing ambiguous task deadlines

V34 does not guess a timezone for historical local-only rows. A new settings wizard lists these tasks and asks for the timezone in which their old date/time was originally entered.

For the reported staging case, choose `Asia/Yekaterinburg` as the source timezone. The preview must show:

```text
14:10 Asia/Yekaterinburg → 12:10 Europe/Moscow
```

Only explicitly selected task IDs are migrated.

## Reminders and delivery

Task-specific reminders are calculated as:

```text
remindAtInstant = dueInstant - reminderMinutesBefore
```

The same authoritative instant is exposed to:

- the browser notification scheduler;
- mobile API clients;
- Telegram delivery and duplicate protection.

Telegram prefers `remindAtInstant` and uses the projected local `remindAt` only as presentation/fallback for old rows.

The global “tasks of the day at HH:mm” preference remains a local wall-clock setting. It is used only when a task has no explicit timed deadline.

## API

Task DTOs additionally expose:

```text
deadlineAbsolute
dueSourceTimezone
dueSourceDate
dueSourceTime
```

Legacy migration endpoints:

```http
GET  /api/tasks/legacy-deadline-migration/preview?sourceTimezone=Asia/Yekaterinburg
POST /api/tasks/legacy-deadline-migration
```

The `/api/v1` aliases are equivalent.

## Regression coverage

- service tests protect same-instant projection, overdue identity and midnight date crossing;
- controller tests protect automatic profile rebasing and explicit legacy migration;
- notification tests protect one `remindAtInstant` across timezone changes;
- Telegram tests prove the authoritative instant is not reinterpreted from projected local time;
- frontend contracts protect source-deadline rendering and the migration wizard;
- Playwright reproduces the original UTC+5 → UTC+3 overdue-task scenario.

Regression baseline: 86 Java test classes, 456 `@Test` methods and 20 Playwright scenarios.
