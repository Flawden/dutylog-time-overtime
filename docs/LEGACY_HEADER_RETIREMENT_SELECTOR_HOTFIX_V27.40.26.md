# v27.40.26 — Legacy Header Retirement Selector Hotfix

## Evidence

The v27.40.25 staging Playwright report executed all 48 Chromium scenarios and finished **46 passed / 2 failed**. Both final failures, including their retries, stopped on the same strict-mode error: `#offlineStatus` resolved to the Vue shell control and the server-rendered legacy control at the same time.

The trace proves the Vue cut itself completed: `<html>` already carried `data-vue-offline-sync="ready"` and `data-vue-shell="ready"`, and `#dutylog-vue-root` carried `data-vue-ready="true"`. The remaining legacy status therefore was not a late-boot race.

## Root cause

The retirement code searched for `body > .head`, but the source hierarchy is `body -> .wrap -> .head`. The selector therefore matched zero nodes even when the legacy header was still present. The existing shell E2E repeated the same incorrect selector, so it falsely reported that the legacy header had been retired.

The mobile layout scenario still measured `.head`; once the real runtime retirement is fixed, that stale assertion would become the next deterministic failure.

## Fix

The server recovery header now has the explicit identity `#legacyGlobalHeader`. `shell-bootstrap.js` removes that exact node after a successful `dutylog:vue-ready` event. The browser shell contract requires `#legacyGlobalHeader` to have count zero, and the mobile layout contract measures `.vue-shell-header`.

The legacy `#offlineSyncDialog` remains pre-Vue recovery source and is still removed by its explicit ID. Vue keeps the stable post-ready `#offlineStatus` / `#offlineSyncDialog` selectors.

## Ownership boundary unchanged

This hotfix does not move queue authority. `dataLayer.syncQueue()` remains the single offline queue executor; IndexedDB, failed mutations, reconnect flushing and lock semantics are unchanged. Vue owns presentation and bounded commands only.

First-run onboarding remains the only intentionally live post-ready legacy presentation exception and moves to the next planned cut, v27.40.27.
