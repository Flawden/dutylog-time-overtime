# v27.23.1 — Calendar Sync JSON UTF-8 Contract Hotfix

## Failure

The v27.23.0 GitHub Actions Maven gate executed all 563 tests and reported one failure in `CalendarSyncControllerTest.issueRotateFeedAndRevokeFormOnePrivateTokenLifecycle`. The application returned the correct U+2026 token hint (`prefix…suffix`), but the test parsed the first JSON response with `MockHttpServletResponse#getContentAsString()` and therefore used MockMvc's ISO-8859-1 fallback. UTF-8 bytes for `…` became `â¦` in the expected value.

## Resolution

- Read the captured JSON body with `getContentAsString(StandardCharsets.UTF_8)`.
- Assert the parsed token hint contains `\u2026`, avoiding source/editor ambiguity.
- Keep the direct status-response comparison, so issue and status endpoints must expose the same non-secret hint.
- Add a release-gate assertion that rejects the charset-less helper call.

## Scope

- No production calendar logic change.
- No HTTP API or OpenAPI change.
- No nginx configuration change.
- No database migration; Flyway remains V41.
- Baseline remains 107 Java test classes / 563 `@Test` methods / 32 Playwright scenarios.
