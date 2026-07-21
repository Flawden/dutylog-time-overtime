# v27.4.2 — Timezone simplification and critical regression pack

## Product change

The user now selects one canonical IANA timezone and saves it explicitly. DutyLog calculates the effective UTC offset from the selected zone and current date, so daylight-saving and jurisdiction changes remain data-driven.

Removed from the user interface:

- free-form region/site name;
- manual offset from Moscow;
- the derived “Moscow ±N hours” note;
- timezone autosave triggered by leaving the field.

The profile API and PostgreSQL `users.work_timezone` column remain unchanged. Legacy local region/offset values are ignored and normalized to empty/zero on the next timezone save.

## Automated critical path

- Persistent login is restored in a completely new Playwright browser context with no `JSESSIONID` and only `DUTYLOG_REMEMBER_ME`.
- The same restored context performs parallel authenticated bootstrap reads.
- Logout invalidates the previously captured persistent cookie.
- Task modal persistence includes category, priority, due date and due time.
- Shift-type modal flow includes create, edit, calendar assignment and authoritative reload.
- Timezone selector uses explicit save and survives reload from the server-backed profile.
- Authenticated deployment smoke reads `/api/profile`, `/api/modules`, `/api/profile/sessions` and `/api/auth/me` without mutating production data.

## Production smoke

```bash
DUTYLOG_BASE_URL=https://dutylog.example.com \
DUTYLOG_SMOKE_USERNAME=smoke-user \
DUTYLOG_SMOKE_PASSWORD='from-secret-store' \
  bash deploy/scripts/production-smoke-test.sh
```

The wrapper refuses non-HTTPS URLs and missing credentials. It delegates to the same CSRF-aware, cookie-backed smoke test used by deployment tooling.

## Baseline

- 71 Java test classes;
- 358 `@Test` methods;
- 11 Chromium Playwright scenarios;
- PostgreSQL Flyway V1–V25.
