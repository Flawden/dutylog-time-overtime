# v27.40.29 — Vue Logout Ownership & Offline Status Contract Hotfix

## Chromium evidence

The v27.40.28 staging Playwright report executed all 48 scenarios: 46 passed and 2 failed, with both retries reproducing their respective failures. The evidence does not support an immutable-image or deploy-only explanation.

### PWA offline scenario

The document is already in the expected offline state and the Vue-owned `#offlineStatus` resolves uniquely. Its Russian visible copy is `Нет сети`, while the test still requires `/оффлайн|offline/i`. This is a stale translated-copy assertion, not a demonstrated offline runtime failure.

### Remember-me logout scenario

Playwright resolves and clicks `[data-vue-shell-logout]`, but the page remains on `/` until `waitForURL(/login\.html/)` times out. The trace/network log contains no `/logout` request after the click. Source inspection shows why: `DutyLogLegacyPlatform.logout()` still calls `document.getElementById("logout")?.click()`, but `#logout` belongs to `#legacyGlobalHeader`, which is intentionally removed after Vue readiness.

## Fix

- `DutyLogLegacyPlatform.logout()` no longer depends on retired DOM. It emits `dutylog:logout-request`.
- `70-user-boot.js` owns one idempotent session action for both the pre-Vue recovery button and Vue request: pending-save flush, CSRF-aware same-origin `POST /logout`, then `/login.html` navigation.
- The Vue offline status exposes `data-network-state="online|offline"`, and PWA E2E asserts that semantic state instead of localized visible copy.
- Executable source contracts prevent both the retired `#logout` dependency and the stale translated-copy assertion from returning.

## Boundaries preserved

First-run onboarding is intentionally untouched. `dataLayer` remains the only offline mutation/queue/reconnect executor. No HTTP/OpenAPI shape, Flyway migration, backend authorization rule, remember-me policy, dependency graph, retry or timeout policy changes in this cut.

Acceptance inventory: 161 Java test classes / 782 `@Test` methods / 48 Chromium Playwright scenarios / 60 Vitest cases / OpenAPI 124/130 / Flyway V47.
