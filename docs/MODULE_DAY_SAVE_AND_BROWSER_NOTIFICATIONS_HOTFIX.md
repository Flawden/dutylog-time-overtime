# v27.2.6 — Module-isolated day saves and browser reminders

Status: v27.2.6.

## Root cause

The web day snapshot always contained `overtimeHours: 0` and `timeOffHours: 0`. `DayController` interpreted any non-null overtime field as an Overtime-module write, so users on the `Minimum` preset received `403 MODULE_DISABLED:overtime` even when changing only a shift, marker or note. The optimistic UI kept the local value until a month reload, which made markers and notes appear to disappear.

## Fix

- Neutral legacy values are accepted when Overtime is disabled.
- Real non-zero overtime writes remain forbidden.
- Disabled Notes/Overtime fields are preserved in the database and hidden from the response.
- The current frontend omits disabled-module fields entirely.
- A MockMvc regression reproduces the old full-snapshot payload and verifies preservation after re-enabling modules.

## Browser reminders

The previous browser implementation only calculated and listed reminders; only the explicit test button called the Notification API. v27.2.6 adds a browser/PWA scheduler that:

- refreshes the server-calculated schedule once per minute;
- checks due reminders every 10 seconds;
- deduplicates deliveries in localStorage;
- accepts a five-minute late window after a sleeping tab resumes;
- uses the service worker when available and focuses DutyLog when the notification is clicked.

This delivery path works while a DutyLog page or installed PWA is running. Reliable delivery after the application is fully closed requires a future Web Push implementation.
