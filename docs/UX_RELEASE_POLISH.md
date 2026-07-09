# UX release polish

Status: v27.0-rc1.

DutyLog is in feature freeze. This release does not add new product modules; it improves the feeling of readiness around the existing web/PWA experience.

## Scope

Allowed changes in this phase:

- loading states;
- empty states;
- clearer error and disabled-module copy;
- mobile layout polish;
- small visual consistency fixes;
- RU/EN copy coverage;
- documentation and release checklist updates.

Explicitly out of scope until after v27.0 RC:

- new analytics screens;
- paid tiers UI;
- Google Calendar integration;
- new Telegram commands;
- native mobile app work;
- new chart types or dashboard features.

## Changes in v26.6

### Boot state

The app now shows a lightweight `DutyLog` boot card while the web/PWA shell prepares profile, modules and calendar data. This avoids a blank or frozen-looking UI during slow first loads.

### Loading states

Added non-blocking loading feedback for:

- month calendar navigation;
- task board refreshes;
- overtime/FIFO ledger refreshes.

The loading skeletons respect `prefers-reduced-motion`.

### Empty states

The previous plain text empty lines were replaced where it matters most:

- important dates list;
- selected-day important dates;
- selected-day tasks;
- task board;
- overtime/FIFO ledger.

The new copy explains what is empty and where the user should add the first record.

### Module disabled states

Module settings now show:

- enabled module count;
- disabled module count;
- basic/locked module count.

Disabled optional modules include explicit copy: disabling a module hides UI/API/offline scope but does not delete the user's data.

## Manual QA focus

Before v27.0 RC, manually check:

1. First login/refresh on a slow network.
2. Month navigation while online and offline.
3. Empty task board, filtered task board and selected-day task panel.
4. Empty overtime ledger and filtered ledger.
5. Important dates list before/after adding an item.
6. Module settings after disabling Notes, Tasks, Overtime and Telegram.
7. Small screens around 360–420 px width.
8. English UI for the new loading and empty-state copy.
