# v27.34.2 — Vue Browser Runtime Bundle Hotfix

The real Chromium baseline exposed a library-mode runtime defect after the v27.34.1 TypeScript fixes: the generated Vue bundle still referenced `process.env.NODE_ENV`. Browser pages do not provide Node's global `process`, so the shell failed before `window.__dutylogVueReady` could resolve and the strict Playwright fixture reported the same page error across almost every scenario.

## Runtime correction

- Vite now replaces `process.env.NODE_ENV` with the literal production value while building the Vue library bundle.
- The fix does not add a browser `process` shim or another global compatibility layer.
- Vue still owns only the application shell; legacy product workspaces and the hash router remain authoritative.

## Browser-bundle audit

Every frontend production build now runs `frontend/scripts/audit-browser-bundle.mjs` after Vite. The audit rejects executable output containing:

- an unreplaced `process.env` reference;
- CommonJS `require(...)`;
- `module.exports`;
- Node-only `__dirname` or `__filename` globals.

Because `npm run build` includes the audit, the same contract runs in the frontend CI gate and in the isolated Node stage of the production Docker build. Playwright's page-error collector remains strict; the hotfix removes the runtime fault instead of filtering or ignoring it.

## Preserved contracts

- Strict TypeScript and `exactOptionalPropertyTypes` remain enabled.
- The dependency set, API, OpenAPI, PostgreSQL schema, Flyway V47 and one-image deployment topology are unchanged.
- Product behavior and user data are unchanged.
