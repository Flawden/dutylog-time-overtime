# v27.39.1 — Vue Settings Strict Typecheck Hotfix

## Evidence

The first v27.39.0 staging run passes authentic npm-lockfile verification, delivery verification and the generated OpenAPI drift check (`118 operations / 120 schemas / 91b48b10fa56`), then stops at `vue-tsc --noEmit` before Maven or Chromium. The failure is limited to strict-template/model typing in the new Settings/Workspace feature.

## Fix

- `AppearanceSettingsCard.vue` narrows string-indexed screen/widget catalog lookups through checked helpers and binds `disabled` as a concrete boolean.
- `SettingsCard.vue` omits the optional status `id` when no id is present instead of passing `undefined` under `exactOptionalPropertyTypes`.
- `SettingsWorkspace.vue` uses a defined session key fallback and a concrete Telegram checkbox boolean.
- `model.ts` explicitly narrows both Workspace Studio array entries after bounds checks before swapping them, satisfying `noUncheckedIndexedAccess` without non-null assertions.
- The Settings migration Java source contract binds these invariants so later refactors cannot silently reintroduce undefined DOM bindings.

Strict compiler options remain unchanged. No API/OpenAPI, backend business rule, persistence schema, Flyway migration, browser retry/timeout, security or feature-scope change is included.

## Acceptance

The release is not accepted until the exact Node 20.18.1/npm 10.8.2 frontend gate passes, then Maven 758/758 on Java 17, canary, mandatory 48/48 Chromium with zero flaky retries, immutable image, clean PostgreSQL V47 smoke and staging deployment are green.
