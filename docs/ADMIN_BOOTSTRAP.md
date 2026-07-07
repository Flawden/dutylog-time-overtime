# Secure admin bootstrap

DutyLog does **not** use the old MVP rule “the first registered user becomes administrator”. Public registration always creates a regular `USER` account.

The first trusted production administrator is controlled explicitly through environment variables:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=false
```

## What happens on startup

When username and password are configured, the backend:

1. validates the username and password length;
2. creates the user as `ADMIN` if it does not exist;
3. promotes the user to `ADMIN` if it already exists as `USER`;
4. keeps the existing password if the user already exists;
5. resets the password from env only when `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true`;
6. performs one-time cleanup of legacy admins from the old MVP rule on the first v22.3 startup only.

This makes a redeploy with an empty database safe: a random visitor can register, but they will only get `USER`. The one-time legacy cleanup does not run on every restart, so administrators assigned later from the UI are kept.

## Why the env password is not applied on every restart

Since v22.3 the bootstrap env password is an initial/recovery secret, not a permanent password override. This allows the administrator to change the password in the web/PWA UI without having it silently reverted after each restart.

To recover access:

1. set a new `DUTYLOG_ADMIN_PASSWORD` in `.env`;
2. set `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true`;
3. restart the app container;
4. log in with the env password;
5. change the password in the app;
6. set `DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=false` again and redeploy/restart.

Do not commit `.env` with real values.

## Managing more administrators

Since v22.3 additional administrators are managed in `Система` → `Пользователи и роли`.

Rules:

- public registration creates only `USER`;
- existing admin can promote `USER` to `ADMIN`;
- existing admin can demote additional admins;
- bootstrap env admin cannot be demoted from the UI;
- an admin cannot demote their own active admin session;
- the last administrator cannot be removed.

See [`USER_ROLES.md`](USER_ROLES.md).

## Public registration control

Public registration is controlled from `Система` → `Публичная регистрация`. This setting affects only ordinary `USER` self-registration. Administrator bootstrap remains environment-only and is not exposed as a public registration flow. See [`REGISTRATION_SETTINGS.md`](REGISTRATION_SETTINGS.md).

## Local development

In local development these variables may be empty. In that case no administrator is created automatically and all public registrations are regular users. Set the same variables locally if you need the `Система` page.
