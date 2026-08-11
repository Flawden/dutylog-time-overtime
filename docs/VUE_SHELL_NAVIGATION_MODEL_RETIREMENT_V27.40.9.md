# v27.40.9 — Vue Shell Navigation Model Retirement

## Evidence and scope

The accepted predecessor is v27.40.8 with a green staging workflow. The next remaining shell debt was not the visible Vue navigation itself, but its source model: `legacyNavigationSnapshot()` reconstructed Vue primary/secondary navigation by querying hidden legacy `#tabbar a[data-view]` elements and interpreting `moduleHidden` / `workspaceHidden` CSS classes. That made retired DOM presentation state an application-state authority.

## Ownership cut

- `legacyNavigationSnapshot()` no longer queries `#tabbar`.
- Workspace order and visibility are resolved from the persisted normalized appearance configuration through the existing workspace model.
- Available routes are filtered from the authoritative runtime module map.
- Today and Settings remain mandatory primary destinations and the primary navigation remains capped at five entries.
- Administrator access remains an available secondary route when the profile is administrative.
- The legacy tabbar stays in the document only as a boot/recovery fallback until the remaining shell/router retirement is complete.

## UX parity

The Settings destination is now explicitly labelled `Настройки` / `Settings`. The separate overflow action remains `Ещё` / `More`, removing the duplicate visible `Ещё` controls identified during the v27.40 parity audit.

## Deliberate boundaries

This release does **not** remove the hash router. Payroll and Admin remain legacy-owned and route-entry hooks still depend on the current hash compatibility layer. This release also does not alter HTTP/OpenAPI, Flyway, business calculations, retries/timeouts, strict TypeScript or offline ownership. The existing legacy `dataLayer` remains the single offline mutation/reconnect owner.
