# v27.40.1 — Vue Settings Retirement Maven Contract Alignment Hotfix

## Evidence

The first v27.40.0 staging run passes the exact frontend gate and fails in `Build, test and enforce coverage`, so release-check and browser stages never start. The saved Actions page does not contain the terminal Maven lines because GitHub virtualized the log window, therefore the repository static contracts were reproduced locally against the exact v27.40.0 archive instead of guessing from the missing tail.

The only genuine reproduced frontend/static contract failure is `VueSettingsWorkspaceMigrationFrontendContractTest.browserParityRequiresCanonicalSettingsAndCalendarSyncRoutes`. The real Playwright contract is:

```js
await expect(page.locator('#settingsLegacyHost')).toHaveCount(0);
```

The Java source contract still searched for an obsolete unwrapped substring:

```text
page.locator('#settingsLegacyHost').toHaveCount(0)
```

That string can never exist once the locator is correctly wrapped by Playwright `expect(...)`.

## Fix

The Java assertion now binds to the actual strict browser assertion:

```text
expect(page.locator('#settingsLegacyHost')).toHaveCount(0)
```

No legacy host is restored and no browser assertion is weakened. Runtime code, OpenAPI, backend business rules, Flyway, retries/timeouts and error collectors are unchanged.

## Acceptance

- exact frontend gate: 52/52 Vitest plus strict typecheck/build;
- Maven: 758/758;
- canary and Chromium: 48/48, zero flaky retries;
- immutable image, PostgreSQL V47 smoke and staging deployment green.
