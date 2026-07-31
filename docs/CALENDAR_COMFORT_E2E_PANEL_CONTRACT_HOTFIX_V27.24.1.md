# v27.24.1 — Calendar Comfort E2E Panel Contract Hotfix

## Incident

GitHub Actions completed the Maven gate for v27.24.0 and started all 33 Playwright scenarios. The new Calendar Comfort scenario returned to today in Month mode, verified the selected cell, and then attempted to click the next-month button while the selected-day panel was still open.

Playwright correctly rejected the click because `.layout.with-panel::before` is the mobile modal backdrop and intercepted pointer events. The retry reproduced the same deterministic failure. A separate `design-system-shell` network issue passed on retry and remained classified as flaky; it is not masked by this hotfix.

## Contract correction

The selected-day panel remains intentionally modal. The scenario now follows the visible product route:

1. click `↺ Сегодня`;
2. verify today's cell and the visible selected-day panel;
3. click the real `#pClose` control;
4. wait for `#panel` to become hidden and `with-panel` to be removed;
5. navigate to the next month.

No force-click, direct state mutation or overlay bypass is used.

## Scope

- Production calendar JavaScript and CSS: unchanged.
- HTTP API and OpenAPI: unchanged.
- Database and Flyway: unchanged at V41.
- Regression baseline: 108 Java test classes / 569 `@Test` methods / 33 Playwright scenarios.
- Next product release after green CI: v27.25.0 — Absence & Time-Off Overhaul.
