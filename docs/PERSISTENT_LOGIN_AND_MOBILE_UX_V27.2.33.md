# v27.2.33 — Persistent login, shift reassign and compact mobile UX

## Browser login

The login form exposes an explicit `remember-me` checkbox. Spring Security stores random persistent tokens in `persistent_logins` and sends an HttpOnly `DUTYLOG_REMEMBER_ME` cookie. Production marks it Secure and keeps it for 30 days by default. Normal logout deletes both the session and remember-me cookies. Password changes, administrative resets and role changes revoke persistent tokens server-side.

Environment controls:

```dotenv
DUTYLOG_REMEMBER_ME_VALIDITY_DAYS=30
DUTYLOG_REMEMBER_ME_SECURE_COOKIE=true
```

## Shift deletion/reassignment

Day writes are serialized per date. Every local edit increments a revision and an older response is applied only if no newer edit exists. A pending debounced note save is flushed before changing a shift or marker. This prevents an old full-day snapshot from restoring a deleted shift.

The frontend also treats an empty successful day-upsert response as `null`, which is the valid server result when the last value on a date is deleted.

## Mobile layout

- Task and overtime filters are collapsed by default and expanded on demand.
- Pagers are absent when the result fits on one page.
- Statistics stay in a compact horizontal strip.
- The account header is compact and the username is represented by the avatar on narrow screens.
- The bottom navigation hides while the selected-day sheet is open and safe-area insets are respected.

## Database

Flyway migration `V24__persistent_web_login.sql` adds the standard Spring Security persistent-token table and a username index. Staging and production keep separate token rows in their separate PostgreSQL databases.
