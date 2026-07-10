# v26.6.5 — properties and tests hotfix

Carried into release candidate: v27.1.0.
Status: v26.6.5.

This hotfix keeps the feature freeze. It fixes release-stabilization regressions found while running the test suite and inspecting local configuration files.

## What changed

- `.properties` comments were rewritten in ASCII English so they remain readable even when an editor opens the file with the wrong legacy encoding.
- Test properties now explicitly set `spring.jpa.open-in-view=false`, matching the main application baseline.
- Telegram linking tests now explicitly enable the Telegram module for users that are expected to link a chat.
- The expired Telegram-code test now uses a valid `DL-000001` code fixture, so it tests expiration instead of format validation.
- The registration CSRF test now expects `403 Forbidden`, which is Spring Security's normal response for a missing CSRF token.
- Module dependency handling now cascades disables to dependents. Example: disabling Overtime disables Scenarios, so `/api/mobile/sync` cannot write overtime via an accidentally re-enabled dependency chain.

## Scope

No new product features were added. This is a config/test/release-readiness hotfix.
