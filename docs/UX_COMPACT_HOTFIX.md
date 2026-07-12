# v26.6.3 — compact modules UX hotfix

Carried into release candidate: v27.2.2.
Status: v26.6.3.

This hotfix keeps the feature freeze. It only corrects visual regressions found during the v26.6 UX release polish.

## Fixed

- Module settings no longer render as oversized cards.
- Disabled-module explanatory text is compact and does not force other cards in the same grid row to stretch.
- Developer/runtime contract details are collapsed under “Technical details”.
- Module badges are placed inside the module title row, preventing overlap in narrow cards.
- The selected-day “hidden blocks” notice uses a two-column layout so the “Configure modules” button does not cover text.

## Not changed

- No module keys changed.
- No API contracts changed.
- No database migrations were added.
- Disabling a module still hides UI/API/offline data from the active snapshot without deleting stored data.
