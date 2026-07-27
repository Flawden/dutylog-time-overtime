# DutyLog v27.16.2 — Next Route & Time Settings E2E Hotfix

## Failure shape

The v27.16.1 runtime fix removed `openQuickActions is not defined`: the shell and a subset of scenarios passed. The remaining run finished with 12 passed and 12 failed. Eleven failures tried to interact with Calendar or Important Dates while DutyLog Next correctly stayed on the Today route; one timezone scenario exposed duplicate/racing shift-template PATCH pairs.

## Product contract

- DutyLog Next starts on `#today`; Classic starts on `#calendar`.
- E2E scenarios must open the workspace they exercise instead of relying on a historical default route.
- A hidden Next-shell tab does not make its compatibility workspace unreachable; hash routing remains a valid test and fallback path.
- An explicit shift-template apply is authoritative and cancels any pending debounced autosave before issuing PATCH requests.

## Changes

- `openView(page, view)` centralizes route-aware test navigation.
- `selectDate` opens Calendar before locating a cell.
- onboarding verifies Today before and after reload.
- Important Dates uses hash routing when its tab is intentionally hidden in DutyLog Next.
- `cancelTimeSettingsAutoApply()` removes the explicit-apply/debounce race.

## Compatibility

No database migration, API change or domain-calculation rewrite. Flyway remains V36.
