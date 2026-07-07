# Public registration settings

Since v22.3 public registration is controlled by an administrator from the web/PWA admin section, not by an ordinary deployment-only toggle.

## Where to change it

Open DutyLog as an administrator:

1. Log in as the bootstrap admin configured through `DUTYLOG_ADMIN_USERNAME` and `DUTYLOG_ADMIN_PASSWORD`.
2. Open `Система` in the header.
3. Use the card `Публичная регистрация`.
4. Turn `Разрешить публичную регистрацию пользователей` on or off.

The setting is stored in the database table `app_settings` as `registration.enabled`, so it survives container restart and application redeploy as long as the PostgreSQL volume/database is preserved.

## Security model

- Public registration creates only `USER` accounts.
- Administrators are not created through the UI.
- The bootstrap administrator is still controlled only by environment variables.
- The login page hides the registration tab when registration is closed.
- The backend is the source of enforcement: direct `POST /api/auth/register` returns `403` when registration is closed.

## API

Public status endpoint:

```http
GET /api/auth/registration-status
```

Admin endpoints:

```http
GET /api/admin/settings/registration
PATCH /api/admin/settings/registration
```

Request body for PATCH:

```json
{ "enabled": false }
```

## First launch recommendation

For a private personal deployment:

1. Configure bootstrap admin in `.env`.
2. Start the application.
3. Log in as admin.
4. Open `Система`.
5. Close public registration after creating the required accounts.

If PostgreSQL data is restored from backup, the registration setting is restored with it.
