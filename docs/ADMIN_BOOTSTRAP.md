# Secure admin bootstrap

Since v22.1 DutyLog does **not** use the old MVP rule “the first registered user becomes administrator”. Public registration always creates a regular `USER` account.

The production administrator is controlled explicitly through environment variables:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
```

## What happens on startup

When both variables are configured, the backend:

1. validates the username and password length;
2. creates the user if it does not exist;
3. promotes the user to `ADMIN` if it already exists as `USER`;
4. refreshes the password hash from `DUTYLOG_ADMIN_PASSWORD`;
5. demotes unexpected old `ADMIN` accounts back to `USER`.

This makes a redeploy with an empty database safe: a random visitor can register, but they will only get `USER`.

## Password reset

To reset the administrator password:

1. change `DUTYLOG_ADMIN_PASSWORD` in `.env`;
2. restart the app container;
3. log in with the new password.

Do not commit `.env` with real values.

## Local development

In local development these variables may be empty. In that case no administrator is created automatically and all public registrations are regular users. Set the same variables locally if you need the `Система` diagnostics page.
