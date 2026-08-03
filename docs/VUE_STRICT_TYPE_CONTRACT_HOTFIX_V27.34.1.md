# v27.34.1 — Vue Strict Type Contract Hotfix

GitHub Actions reached the real Vue TypeScript compiler and rejected four build contracts that a source-only static pass could not model. The release keeps the strict compiler settings and fixes the source instead of weakening them.

## Fixed contracts

- `AppNavigation.vue`: inactive routes now pass the valid ARIA `false` state instead of explicit `undefined` to `aria-current`.
- `UiButton.vue`: native `type` and `disabled` bindings are normalized with concrete defaults before reaching the button element.
- `UiTabs.vue`: an omitted option-level disabled flag is normalized to `false`.
- `vite.config.ts`: the unsupported Vite 5 `LibraryOptions.cssFileName` property is removed; the existing Rollup `assetFileNames` rule remains the stable CSS naming authority.

## Preserved constraints

- `strict`, `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes` remain enabled.
- Vue still owns only the app shell; legacy product screens remain authoritative.
- One repository, one Spring Boot JAR/image and one `dutylog-app` runtime container remain unchanged.
- API, OpenAPI, PostgreSQL, Flyway V47, Payroll, FIFO and user data are unchanged.
