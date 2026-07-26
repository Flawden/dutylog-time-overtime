# DutyLog v27.11.3 — Shift Template & Reminder Timezone Hotfix

## Product contract

DutyLog has one canonical IANA timezone. A timezone change must affect every future-facing clock consistently:

1. Existing dated shifts keep immutable `startInstant` / `endInstant`.
2. Timed shift templates are rebased from the old zone into the new zone.
3. New assignments use the rebased local template values.
4. Shift reminders are calculated from the dated occurrence instant, not from the mutable template.
5. Browser, mobile API and Telegram receive the same `remindAtInstant`.

Example:

```text
Template before move: 08:30–17:00 Asia/Yekaterinburg
Template after move:  06:30–15:00 Europe/Moscow
Absolute interval:     unchanged
```

A late occurrence may move to another displayed date:

```text
03 July 23:00 Europe/Kyiv
04 July 01:00 Asia/Yekaterinburg
Reminder 30 min before: 04 July 00:30 Asia/Yekaterinburg
```

## Backend

- `ProfileController` freezes legacy dated shifts in the old zone and then calls `ShiftTypeService.rebaseForTimezoneChange`.
- `ShiftTypeService` projects every timed built-in or custom template through an anchor date in the old zone and writes the resulting `HH:mm` values in the new zone.
- Untimed templates such as `Выходной` are not modified.
- `NotificationService` queries absolute occurrences by display interval, derives `sourceDate` from the projected start, and subtracts reminder lead time from `shiftStartInstant`.
- Legacy local-only shifts retain a bounded compatibility fallback until explicitly migrated.

## Frontend

- Settings synchronise day/night form values from the authoritative `shiftTypes` response after timezone save.
- The UI states which IANA zone the template fields currently represent.
- The notification list shows projected start time and zone, preventing a mismatch between calendar and reminder.

## Verification

- `ShiftTypeServiceTest`: UTC+5 `08:30–17:00` becomes UTC+3 `06:30–15:00`; untimed day off stays untouched.
- `ProfileControllerTest`: profile timezone update rebases persisted built-ins.
- `NotificationServiceTest`: a Kyiv late shift displayed in Yekaterinburg produces a next-day reminder from the occurrence instant.
- `ShiftOccurrenceFrontendContractTest`: settings, profile and reminder layers are wired to the same projection.
- Playwright: template inputs, calendar occurrence and notification list agree after timezone change.

Flyway remains **V33**.
