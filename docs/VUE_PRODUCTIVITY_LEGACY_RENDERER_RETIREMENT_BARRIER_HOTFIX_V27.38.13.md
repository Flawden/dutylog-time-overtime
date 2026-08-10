# Vue Productivity Legacy Renderer Retirement Barrier Hotfix — v27.38.13

## Evidence

The complete v27.38.12 Playwright report reaches 44/47. `multiple-daily-notes` and `pwa-upgrade` are green, leaving only `editor-modals`, `task-modules` and `tasks-inbox-next`.

The remaining traces show successful generated Task writes followed by Vue runtime error 15 and repeated failures to access a removed node's `parentNode`. The DOM snapshot supplies a direct ownership fingerprint: the document already has `data-vue-productivity="ready"`, but Vue-owned `#taskBoardCategory` contains the legacy `<option value="all">все категории</option>` markup. `TasksPage.vue` owns that selector with an empty-value default option.

## Root cause

Legacy Task loaders were guarded only when they started. A metadata, Board or Inbox read could begin before `retireDomainOwners("productivity")`, resolve afterward, and then call an unguarded legacy renderer. Those renderers use `innerHTML`, `textContent`, `appendChild` and direct form mutations against IDs now owned by Vue. Replacing Vue children invalidates Vue's renderer references and drives the app into the recovery boundary.

## Fix

`50-tasks.js` now exposes one `vueOwnsProductivityUi()` boundary. Legacy Task metadata/editor/selected-day/Inbox/Board renderers return without touching DOM once Vue owns Productivity. Metadata, Inbox and Board loaders also re-check ownership after asynchronous reads, closing the cross-retirement completion window.

The data/offline layer is not retired here. This release only blocks legacy UI publication after ownership transfer; Spring Boot and generated API projections remain authoritative.

## Acceptance

- exact frontend gate with Node 20.18.1 / npm 10.8.2;
- Maven verify, 751/751;
- boot canary;
- Chromium 47/47 with no flaky scenario;
- immutable image and clean PostgreSQL V47 smoke;
- staging deployment.
