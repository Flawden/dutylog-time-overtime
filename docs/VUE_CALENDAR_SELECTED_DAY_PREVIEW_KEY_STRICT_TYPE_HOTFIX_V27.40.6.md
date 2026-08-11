# v27.40.6 — Selected-Day Schedule Preview Key Strict Type Hotfix

## Evidence

The exact GitHub frontend gate ran with Node 20.18.1 and npm 10.8.2 and reached strict `vue-tsc`. It failed only at `SelectedDayPanel.vue` with TS2379 because `ScheduleTemplatePreviewItem.date` is generated as `date?: string`, while Vue's reserved `key` property cannot receive `undefined` under `exactOptionalPropertyTypes`.

## Fix

The schedule preview iterates with `(item, index)` and uses `item.date ?? `preview-${index}`` as the row key. The real date remains the preferred identity; the fallback is used only when the API response legitimately omits the optional date.

No non-null assertion, unsafe cast, TypeScript relaxation, retry, timeout, browser collector, OpenAPI, Flyway, business-rule or offline queue ownership change is made.

## Acceptance

Static release contracts can prove source/version/lineage only. Exact frontend, Maven, Playwright, image, PostgreSQL and staging gates remain required before v27.40.6 is accepted.
