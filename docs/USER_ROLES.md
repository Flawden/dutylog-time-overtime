# Users and roles

Since v22.3 DutyLog has admin-managed user roles.

## Current model

There are two separate concepts:

1. **Access role** — controls permissions now:
   - `USER` — ordinary account;
   - `ADMIN` — can open `Система`, change registration settings, view diagnostics, manage users and roles.
2. **Account tier** — reserved for future product plans:
   - `FREE`;
   - `PAID`;
   - `VIP`.

In v22.3 account tier is read-only and does **not** affect permissions. It exists only as a clean place to grow later without mixing billing/product status with admin permissions.

## Bootstrap admin

The first trusted administrator is still created from environment variables:

```env
DUTYLOG_ADMIN_USERNAME=your_admin_login
DUTYLOG_ADMIN_PASSWORD=long_random_password_at_least_20_chars
DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=false
```

On first startup, if the user does not exist, DutyLog creates it as `ADMIN`.

After that, the password may be changed in the application. A normal restart does not overwrite it from `.env` anymore. This avoids the annoying and unsafe situation where an old deployment password silently returns after every restart.

For emergency recovery only, set:

```env
DUTYLOG_ADMIN_FORCE_PASSWORD_RESET=true
```

Then restart the app, log in with the env password, immediately change it in the app, and set the flag back to `false`.

## Managing roles

Open `Система` → `Пользователи и роли`.

An administrator can:

- see all users;
- promote `USER` to `ADMIN`;
- demote additional `ADMIN` users to `USER`;
- reset a user's password.

Safety rules:

- public registration always creates only `USER`;
- bootstrap env admin cannot be demoted to `USER` from the UI;
- an admin cannot demote their own active admin session;
- the last administrator cannot be removed;
- password reset requires at least 12 characters and revokes mobile tokens for that user.

## What this is not

This is not billing yet. Do not add paid features based on `account_tier` until the product rules exist.

This is not public admin registration. Admins are assigned only by an existing administrator or by the bootstrap env mechanism.
