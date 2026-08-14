# v27.41.7 — Frontend Bundle Segmentation

## Why this release exists

v27.41.4 exact frontend CI produced one `dutylog-vue-app-shell.js` at 806839 B and crossed the historical 800000 B raw ceiling. v27.41.5 deliberately rebaselined raw to 810000 B only as a short bridge and required route/workspace segmentation next. v27.41.7 performs that structural follow-up instead of raising the monolithic ceiling again.

## Segmentation boundary

- `dutylog-vue-app-shell.js` remains the stable module entry loaded by `index.html`.
- Vite emits content-hashed `dist/chunks/*.js` for dynamic boundaries.
- `CalendarTimelineWorkspace` remains the eager domain owner but loads Today and Calendar pages independently.
- `AbsenceTimeBankWorkspace` remains eager so Quick Actions and selected-day commands keep an immediately registered domain; its route pages and editor/composer surfaces are lazy.
- `ProductivityWorkspace` remains eager for Today/Calendar modal and selected-day ownership; Tasks and Important route pages are lazy.
- Payroll and Admin become route-lazy workspace chunks.
- Settings stays eager in this cut because Calendar's Shift Type Manager still calls the Settings domain directly; extracting a smaller Settings runtime owner is a later optimization, not part of this release.

## Offline/PWA boundary

No second offline owner is introduced. The existing service worker already handles every same-origin `.js`/`.css` request network-first and stores successful responses in the current release cache. Dynamic chunks therefore become offline-capable after first successful load, while `dataLayer` remains the sole mutation/outbox/reconnect executor. Chunk names are content hashed so a new entry cannot accidentally import an old chunk.

## Browser budget contract

The audit scans the complete emitted JS graph instead of only the entry file. It requires multiple async chunks and enforces entry, per-chunk and total ceilings. The total gzip ceiling remains 250000 B, matching the previous monolithic policy; segmentation is not a license to hide bundle growth in extra files.

## Non-goals

No endpoint, OpenAPI schema, Flyway migration, auth/session rule, accounting formula, People Profile model, shared-availability rule or dependency version changes.
