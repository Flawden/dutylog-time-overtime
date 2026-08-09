# v27.38.7 — Vue Productivity Module Readiness Browser Canary Hotfix

The v27.38.6 fail-fast Playwright canary reached a real built Vue shell and a healthy Spring Boot server, but failed both attempts because the browser observed repeated `403 MODULE_DISABLED` responses from Tasks, Notes, Inbox and Important Days after the Minimum onboarding preset disabled those modules.

The failure had two frontend causes. Vue Productivity treated an unknown module map as enabled and started optional reads before `modulesLoaded`/profile onboarding authority had settled. Separately, every legacy-state publication cloned the same module map into Pinia, so unrelated state publications retriggered the Productivity module watcher and amplified the number of reads.

v27.38.7 keeps the backend guard and browser fixture strict. `DutyLogLegacyProfileSnapshot` now carries `onboardingCompleted`; Vue Productivity reads only after `modulesLoaded && onboardingCompleted`; and the shell store preserves module-map identity when values are unchanged. No expected-status header or 403 allowlist is added.

The release changes no PostgreSQL schema, Flyway migration or OpenAPI operation/schema count. Baselines remain 152 Java test classes, 751 `@Test` methods, 49 Vitest cases and 47 Playwright scenarios. The existing onboarding canary itself is the browser regression for this failure and must pass before the full suite is allowed to run.
