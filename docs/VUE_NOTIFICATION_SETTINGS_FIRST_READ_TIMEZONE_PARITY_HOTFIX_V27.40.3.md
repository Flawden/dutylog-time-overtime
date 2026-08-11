# v27.40.3 — Notification Settings First-Read Serialization & Timezone Parity Hotfix

## Evidence

The complete v27.40.2 Playwright artifact contains 48 scenarios: 32 clean passes, 10 retry-only flaky scenarios and 6 final failures. The strict runtime collector records repeated `500` responses from `GET /api/v1/notifications/settings`; one trace records the inverse winner, where `GET /api/v1/calendar` returns 500 while a concurrent notification-settings GET returns 200. Calendar range assembly itself reads notification settings, so both shapes are the same first-read race.

The remaining deterministic timezone failures are independent of that race: native Vue displays the grammatically correct Russian success copy `Часовой пояс сохранён`, while two old E2E assertions still searched for `сохранено`; and Chromium's `Intl.supportedValuesOf("timeZone")` does not guarantee that the legacy alias `Europe/Kyiv` appears as an option.

## Fix

`NotificationService.settingsEntity()` keeps ordinary existing-row reads lock-free. Only when no settings row exists, it acquires a pessimistic write lock on the persisted owner row, re-checks `notification_settings`, and creates/flushes exactly one row. This serializes concurrent first reads across application threads and instances without adding a process-local mutex or weakening the unique database constraint.

Vue Time Settings carries forward the full curated legacy timezone fallback list before merging browser-provided zones. Browser contracts assert the exact native Vue success copy rather than accepting a broader legacy phrase.

## Non-goals

No HTTP/OpenAPI route or schema changes; no Flyway migration; no notification business-rule change; no retry, timeout, HTTP failure, console-error or page-error allowlist changes. Acceptance remains 153 Java test classes / 758 `@Test` methods / 48 Playwright scenarios / 52 Vitest cases / Flyway V47 / OpenAPI 118 operations and 120 schemas (`91b48b10fa56`).
