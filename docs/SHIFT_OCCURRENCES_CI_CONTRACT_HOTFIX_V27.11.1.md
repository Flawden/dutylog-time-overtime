# DutyLog v27.11.1 — CI & Contract Hotfix

This patch repairs the full Maven validation gate for v27.11.0 without changing production behavior or database schema.

## Fixed

- stale static contracts now assert the occurrence-based projection used by `renderShiftProjection()`;
- Task Details HTTP test serializes multiline description through Jackson instead of embedding an illegal raw newline in JSON;
- legacy shift migration test persists rows individually and asserts generated IDs before building the selection list.

Flyway remains V33. Test baseline remains 85 Java classes / 442 tests / 19 Playwright scenarios.
