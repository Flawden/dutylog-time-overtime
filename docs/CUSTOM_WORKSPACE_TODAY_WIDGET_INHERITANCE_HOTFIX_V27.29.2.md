# v27.29.2 — Custom Workspace Today Widget Inheritance Hotfix

This is a bounded Workspace Studio behavior hotfix.

## Failure reproduced

The v27.29.1 Maven gate passed, and Playwright completed 37 of 38 scenarios. The remaining Workspace Studio scenario created a custom workspace from the Shift Worker preset and expected its existing Today cards to remain selected. Navigation was copied, but `todayWidgets` was not. The custom workspace therefore normalized to its safe fallback containing only the required Shift card, leaving Tasks hidden with `workspaceHidden`.

## Fix

- copy the active preset's `todayWidgets` together with its navigation when Custom Workspace is created;
- keep Shift mandatory and preserve the existing widget allowlist;
- assert in the browser scenario that Overtime and Tasks are inherited before the user edits them;
- add a focused Java regression contract for the clone boundary;
- keep the strict Playwright fixture unchanged.

## Compatibility

- no API or OpenAPI changes;
- no profile schema changes;
- no PostgreSQL changes;
- no Payroll, Ledger, Vacation or Calendar business changes;
- no new Playwright scenario;
- Flyway remains V46.

## Automated baseline

- 121 Java test classes;
- 612 `@Test` methods;
- 38 Chromium Playwright scenarios;
- Flyway V46.
