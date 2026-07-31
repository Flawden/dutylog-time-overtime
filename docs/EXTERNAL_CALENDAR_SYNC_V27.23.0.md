# External Calendar Sync — v27.23.0

## Product boundary

This release is an outbound, read-only calendar integration. DutyLog remains authoritative; external clients cannot edit shifts, tasks, important events or absences through the feed. Import, provider OAuth and bidirectional conflict resolution are intentionally deferred.

## User flows

- Download a selected range as `.ics`.
- Download one important event as `.ics`.
- Issue a private rolling feed URL in Settings.
- Rotate the URL to invalidate the prior secret.
- Revoke the URL without deleting calendar data.

## Security contract

- 32 random bytes, Base64URL without padding.
- SHA-256-only persistent storage.
- Raw URL disclosed once after issue/rotation.
- Unknown, malformed, revoked or disabled-module tokens return 404.
- `Cache-Control: no-store`; bounded 366-day range, event count and output bytes.
- The supplied nginx examples use an exact `/calendar-feed.ics` location with `access_log off`; the active Certbot-managed host configuration must receive the same location before the first subscription is issued.

## RFC 5545 contract

The writer emits UTF-8, CRLF, 75-octet content-line folding, escaped text, exclusive all-day `DTEND`, UTC timed events and stable owner-scoped UIDs. Shift occurrences, planned tasks, important events and absences are composed without mutating their source domains.

## Regression baseline

- 107 Java test classes
- 563 `@Test` methods
- 32 Playwright scenarios
- Flyway V41
