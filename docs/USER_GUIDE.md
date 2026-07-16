# User guide

Status: v27.2.4.

This is a short guide for daily DutyLog use.

## First login

After registration, DutyLog shows onboarding. Choose a preset:

- Minimum — only the core day-planning tools.
- Standard — the recommended set for shifts and overtime.
- Enable all — turns on all user-facing modules.

You can change modules later in Settings. Turning a module off hides it and protects its API, but does not delete its data.

## Calendar

Open the Calendar tab, choose a day and assign a shift. The current day is marked softly; the selected day has the stronger outline.

Built-in shifts:

- Day shift;
- Night shift;
- Day off.

Custom shifts are configured in Settings → Shift types and times.

## Day panel

The selected-day panel contains enabled blocks:

- Shift;
- Marker;
- Schedule;
- Overtime;
- Important dates;
- Note;
- Tasks.

If some blocks are hidden because modules are disabled, DutyLog may show a hint. You can close it; it stays dismissed in this browser.

## Overtime

Use Overtime to record extra hours or time off. DutyLog tracks the balance and FIFO ledger. The plan/norm hours come from shift settings unless entered manually.

## Tasks and notes

Tasks can be attached to days or used from the Tasks board. Notes are Markdown text stored per day and are available in the offline snapshot when the Notes module is enabled.

## Settings

Settings contain:

- Profile;
- Language;
- Modules;
- Appearance;
- Working hours and timezone;
- Shift types;
- Overtime templates;
- Notifications;
- Important dates.

Administrators also see the System page for users, registration and diagnostics.

## Offline/PWA

DutyLog is a PWA. After opening online, the app shell and a limited data snapshot are available offline. Offline mode supports basic day changes, notes and task completion. Complex business logic stays on the server.
