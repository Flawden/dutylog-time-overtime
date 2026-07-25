# DutyLog v27.8.1 — Timezone Projection Refresh Hotfix

## Problem

The profile could already contain new work/display IANA zones while an existing dated-shift card still rendered an IndexedDB projection created under the previous context, for example `Europe/Kyiv`. Boot also started calendar loading before the authoritative profile request completed.

## Contract

1. The authenticated profile is loaded before the first calendar request.
2. A timezone save performs an **authoritative calendar reload** with HTTP cache bypass and without painting the month snapshot first.
3. The authoritative bundle replaces the IndexedDB snapshot.
4. The overtime ledger refreshes in the same operation.
5. Equal work/display zones intentionally show equal local times. To see `08:30 Asia/Yekaterinburg` as `06:30 Europe/Moscow`, Work and Display must remain different.

## Offline boundary

Changing profile timezones requires the server. Existing offline calendar data remains available but is not reinterpreted until an authoritative profile/calendar refresh succeeds.

## Data and migrations

No schema change. Flyway remains V30. The hotfix changes fetch ordering and cache invalidation only.

## Acceptance

- Create a dated day shift.
- Set Work to `Asia/Yekaterinburg` and Display to `Europe/Moscow`.
- Save once.
- The existing day card must show `06:30–15:00 Europe/Moscow` and `08:30–17:00 Asia/Yekaterinburg`.
- It must not retain `Europe/Kyiv` from a previous snapshot.
- Reloading the page must preserve the same projection.
