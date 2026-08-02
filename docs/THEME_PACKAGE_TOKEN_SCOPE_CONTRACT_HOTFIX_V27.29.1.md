# v27.29.1 — Theme Package Token Scope Contract Hotfix

This is a Maven source-contract hotfix only.

## Failure reproduced

The v27.29.0 runtime correctly publishes each theme package token scope as the JavaScript template literal:

```javascript
tokenScope:`html[data-ui-theme="${id}"]`
```

The new Java static contract accidentally searched the JavaScript file for literal backslashes before both quotes. Those backslashes belonged only to Java source escaping and are not present in the JavaScript runtime source. Maven therefore reported one false failure at `WorkspaceLayoutThemeStudioFrontendContractTest:74` before Playwright could start.

## Fix

- align the existing assertion with the actual JavaScript template literal;
- add a dedicated regression contract that distinguishes Java source escaping from runtime file content;
- keep the real package registry, token scopes, themes and decoration metadata unchanged;
- preserve strict theme isolation checks for Midnight and Forest;
- keep the 38-scenario Playwright surface unchanged.

## Compatibility

- no production JavaScript behavior changes;
- no CSS or DOM changes;
- no API or OpenAPI changes;
- no profile persistence changes;
- no Payroll, Ledger, Vacation or Calendar behavior changes;
- no PostgreSQL change;
- Flyway remains V46.

## Automated baseline

- 120 Java test classes;
- 611 `@Test` methods;
- 38 Chromium Playwright scenarios;
- Flyway V46.
