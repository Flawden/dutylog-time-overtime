# v27.39.4 — Vue Settings Browser Ownership & Preview Correlation Hotfix

## Evidence

The complete v27.39.3 staging Playwright artifact is authoritative for this hotfix. It contains 48 scenarios: 31 pass and 17 fail on both attempts. Sixteen failures stop in the shared `openView(page, 'settings')` helper because it still waits for the legacy `#view-settings` element that v27.39.0 intentionally retires. Failure screenshots show the Vue Settings workspace already mounted and usable while the helper waits for the removed node.

The only independent failure is `absence-time-off-overhaul.spec.js`. The trace contains an earlier auto-preview request for `PARTIAL` 09:00–18:00 that returns 540 minutes and the intended explicit 09:00–13:00 preview that returns 240 minutes. The old response predicate matched any successful preview response, so the earlier background response could satisfy the explicit-button wait.

## Fix

- `openView()` maps Settings to `[data-vue-settings-workspace-view]` and waits for `data-vue-settings-workspace="ready"`. The retired `#view-settings` container is not restored.
- The partial-time-off scenario keeps the strict `durationMinutes === 240` and remaining-bank assertions, but correlates the awaited response to the exact submitted request body: `PARTIAL`, `09:00`, `13:00`, `OVERTIME_BANK`.
- Existing Java source contracts bind both invariants without adding a JUnit method or changing the 153-class / 758-test baseline.

## Non-goals

No application runtime behavior, backend business authority, API/OpenAPI shape, database schema, Flyway migration, Playwright retry/timeout, HTTP/pageerror/console collector, or assertion strength is changed.

Acceptance remains exact frontend gate, Maven 758/758, canary, clean Chromium 48/48 with zero flaky retries, immutable image, PostgreSQL smoke and staging deployment.
