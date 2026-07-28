# v27.17.5 — UI Core E2E Accordion Hotfix

## Failure

The `design-system-shell.spec.js` scenario opened Appearance, changed workspace/layout/palette and reloaded the page. The settings accordion correctly restored `dutylog.settings.openSection=appearance`, so the Appearance card was already open.

The test then clicked `#appearanceCard .settingsHead` unconditionally. That click collapsed the card and made `[data-shell-choice="classic"]` invisible. Playwright timed out waiting to click a hidden button, while the actual UI Core persistence behavior was correct.

## Fix

After reload the browser contract now waits for and verifies:

- `#appearanceCard` has the `is-open` class;
- the Classic shell choice is visible;
- `localStorage['dutylog.settings.openSection']` equals `appearance`;
- workspace, layout and palette attributes remain persisted.

Only then does the scenario switch Next → Classic → Next.

## Scope

- Test-only behavioral fix plus release metadata and documentation.
- No production JavaScript or CSS behavior change.
- No profile contract change.
- No domain API or schema change.
- Flyway remains V36.
- Baseline remains 95 Java test classes, 496 `@Test` methods and 25 Playwright scenarios.
