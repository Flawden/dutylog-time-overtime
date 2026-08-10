# v27.39.0 — Vue Settings, Workspace & Integrations

## Scope

This release moves the high-value Settings read/write surfaces into one bounded Vue owner while preserving three explicit compatibility islands until v27.40.0.

Vue owns:

- Settings route/index and section navigation;
- Profile and password/session presentation;
- Language selection;
- Module registry/toggles;
- external Calendar Sync issue/rotate/revoke and range export UI;
- Appearance, Workspace, Layout, Theme, Palette and Studio controls;
- Telegram profile integration status/link/settings presentation.

Bounded legacy islands retained inside `#settingsLegacyHost`:

- Time settings;
- Schedule templates/calendar layers;
- Notification schedules/browser notification controls.

Those islands keep their mature mutation/event behavior and public selectors for this release. They are explicitly owned by the v27.40.0 final legacy-retirement milestone, not silently treated as permanent architecture.

## Generated API boundary

The canonical OpenAPI browser contract expands to **118 operations / 120 schemas**. Profile, modules, shift types, notification settings, sessions and Telegram integration receive typed shapes. Migrated Vue Settings writes use operationId-based `/api/v1/*` transport. Telegram keeps the compatibility `/api/telegram` route while adding canonical `/api/v1/telegram` aliases.

Spring Boot remains authoritative for module dependencies/locks, profile validation, sessions, calendar subscription token persistence and integration authorization.

## Module-toggle transaction

The accepted v27.38.15 module authority rule is preserved:

- disabling a module closes the runtime read boundary before PATCH;
- enabling waits for backend confirmation before reopening the runtime boundary;
- a successful commit forces a fresh calendar read and module-aware refresh;
- failed mutations restore the previous module snapshot.

A cached month snapshot cannot outrank the current global module map.

## Appearance / Workspace Studio

Vue owns the existing UI Contract v2 model rather than inventing a second appearance system. The model preserves:

- workspace/layout/theme/palette/decorations registries;
- Today and Settings as mandatory navigation entries;
- maximum five primary navigation entries;
- Today widget ordering/visibility;
- calendar density/layer presentation;
- released theme presets and colors.

Appearance changes preview immediately through the bounded root-style bridge and persist through generated Profile updates with serialized/debounced saves.

## Integration-secret and diagnostics boundary

Q-11 becomes active in this release.

- Calendar subscription bearer URLs are held only in volatile Vue state after issue/rotation; they are not persisted to localStorage or frontend diagnostics.
- Telegram link codes/status use same-origin generated operations and are not added to diagnostics.
- Existing CSP, session-cookie, CSRF and backend module guards remain unchanged.
- ADR-008 disables public production source maps by default. `DUTYLOG_FRONTEND_SOURCEMAPS=true` may produce hidden maps for controlled diagnostic artifacts; runtime HTML/PWA assets do not reference them.

## Acceptance

No PostgreSQL/Flyway change is introduced; Flyway remains V47. Acceptance is fail-closed on the exact frontend gate, Maven/JUnit, strict Chromium E2E, immutable image, clean PostgreSQL smoke and staging deployment. No timeout/retry/runtime-error policy is weakened.
