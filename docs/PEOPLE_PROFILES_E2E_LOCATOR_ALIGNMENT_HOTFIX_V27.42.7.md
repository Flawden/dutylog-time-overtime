# DutyLog v27.42.7 — People Profiles E2E Locator Alignment Hotfix

## Exact failure

The v27.42.6 browser regression reached the People Profiles day view but Playwright strict mode rejected `.calendarProfileReadOnly` because two elements matched: the read-only schedule banner and the timezone label.

## Fix

The E2E assertion now targets the exact user-facing banner text `График только для просмотра` using `page.getByText(..., { exact: true })`.

## Scope

No production runtime, backend, persistence, OpenAPI, Flyway, schedule-template seeding or People Profiles behavior changes. This is a browser-test locator alignment only.
