# v27.4.3 — Reminder timezone and sync UX bugfix

## Scope

This release is intentionally limited to defects found during the final staging pass before production readiness. It does not add a new product module and does not change the PostgreSQL schema.

## Browser reminder timezone contract

`NotificationService` still exposes `remindAt` as the user's local wall-clock value because Telegram delivery and the notification list use it directly. It now also exposes `remindAtInstant`, an ISO-8601 UTC instant calculated as:

```text
user-local date/time + saved IANA timezone -> UTC instant
```

The browser scheduler compares only that absolute instant with `Date.now()`. This prevents the browser or operating system timezone from shifting the intended delivery time.

## Task reminder minutes

Creation and editing accept any whole number from 0 through 10080. The browser uses `step=1`, and JavaScript rejects fractional, negative and out-of-range values before sending the request.

## Overtime editor

The redundant free-form short interval was removed. Start and end are represented by `datetime-local` inputs and feed the existing automatic break/plan calculation. Existing old manual credits keep their stored `timeRange` when saved without a calculated interval so editing cannot silently erase historical text.

## Synchronization feedback

The manual sync action now has an accessible live region and explicit states:

- synchronization in progress;
- synchronization completed;
- no changes;
- no network connection;
- synchronization in another tab;
- some changes were not uploaded.

The action button is disabled and marked `aria-busy=true` while the operation runs.

## Database

No migration is required. Flyway remains V1–V25.
