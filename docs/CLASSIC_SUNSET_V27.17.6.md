# Classic Sunset — v27.17.6

## Goal

Remove the user-selectable Classic shell after UI Core v1 reached staging acceptance. DutyLog now has one interface matrix: one DOM, one route system, one workspace engine and one theme/layout platform.

## Removed

- Classic selector and hidden `themeShellMode` control from Appearance settings.
- Runtime Next → Classic → Next switching.
- Classic-specific route fallback to Calendar.
- Classic navigation branch in UI Core.
- Classic-only CSS selectors and shell-choice component styles.
- `shellMode` from the profile theme whitelist and API response.

## Upgrade compatibility

Existing profiles or local PWA caches may still contain `themeConfig.shellMode=classic`. Both the synchronous pre-paint bootstrap and normal appearance normalization ignore the retired field and always activate the single DutyLog shell. The backend silently drops the legacy key on read/write instead of rejecting old clients.

`html[data-shell="next"]` remains temporarily as an internal CSS scope. It is no longer a product mode or user preference; keeping the inert selector avoids a high-risk full stylesheet rewrite in the same release.

## Rollback

Classic is no longer an in-app rollback mechanism. Recovery uses the exact tested release tree, immutable Docker digest and existing application rollback scripts.

## Regression contract

- UI Core workspace/layout/palette survive reload.
- Appearance accordion persistence remains covered.
- No `[data-shell-choice]` controls exist.
- Legacy `shellMode=classic` in local storage still boots `data-shell=next`.
- Today remains the default route.
- Calendar Month / Week / Day is always available.
- API and database schema remain unchanged; Flyway stays V36.

Baseline: 95 Java test classes, 496 `@Test` methods and 25 Playwright scenarios.
