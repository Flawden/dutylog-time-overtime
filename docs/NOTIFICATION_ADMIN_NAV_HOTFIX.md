# v26.6.12 — notifications alignment and admin navigation hotfix

Status: v26.6.12.

Scope: release-polish hotfix only. No new product modules or business features.

## What changed

- Notification status chips are pinned to the right side of the notifications header.
- Browser permission and reminder-count chips no longer visually float through the middle of the card when RU/EN text length changes.
- Active notification settings get a clearer selected state so the card does not look disabled.
- The administrator screen now uses a settings-style side navigation with Users, Registration and Diagnostics anchors.

## Why

After the previous alignment fix, generic right-side positioning worked for most settings cards, but the notifications card had three header columns: copy, status chips and the open/collapse button. In Russian the chips could drift into the middle and look detached from the action button.

The admin screen also had all cards in one long stack without the same navigation pattern as user settings. The new side navigation keeps admin actions easier to find without adding new backend features.

## Guardrails

`release-check.sh` verifies that the notification header uses the dedicated v26.6.12 layout and that the admin view contains the side navigation anchors.
