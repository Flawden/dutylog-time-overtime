# v27.3.0 — Important dates, timezone and overtime navigation

## Product changes

- Important dates now have a dedicated `#important` workspace instead of living in Settings.
- Existing records remain in the already-normalized `important_days` table; no data migration is required.
- The user's IANA timezone is stored in `users.work_timezone` by Flyway V25 and returned by `/api/profile`.
- Notification filtering and Telegram date/time decisions use the user's timezone.
- Overtime ledger edit actions navigate to the exact month/day/editor and highlight only the edited record.

## Regression focus

1. Save `Europe/Chisinau`, reload on another browser, and verify the profile returns the same timezone.
2. Edit a credit from another month and verify the calendar opens that month and the overtime accordion.
3. Edit two credits on the same day and verify only the selected credit row is highlighted.
4. Create, edit, filter, open and delete an important date from the new workspace.
