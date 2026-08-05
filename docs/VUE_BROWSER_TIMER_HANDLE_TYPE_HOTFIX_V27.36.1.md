# DutyLog v27.36.1 — Vue Browser Timer Handle Type Hotfix

## Scope

This is a bounded strict-TypeScript compile hotfix on top of `v27.36.0 — Vue Absence & Time Bank`.

GitHub Actions reached the real `vue-tsc --noEmit` gate and reported two identical errors:

```text
AbsenceComposer.vue: Type 'Timeout' is not assignable to type 'number'
CreditEditor.vue: Type 'Timeout' is not assignable to type 'number'
```

The browser timer overload is selected explicitly because the feature intentionally stores numeric DOM timer handles. Both debounce paths now use `window.setTimeout` and `window.clearTimeout`, making the browser overload explicit even while Vitest/Node typings are present in the compilation program.

## Preserved behavior

- Absence preview debounce remains 260 ms.
- Credit preview debounce remains 280 ms.
- Existing timer handles are cancelled before scheduling a replacement.
- Component unmount cancels any pending preview.
- No store, generated client, backend rule, OpenAPI schema, FIFO behavior or migration ownership changes.

## Regression boundary

`VueBrowserTimerHandleTypeHotfixTest` verifies both components keep numeric nullable handles, use browser timer APIs, retain cancellation on replacement/unmount and do not regress to ambiguous `globalThis` timer calls.

## Acceptance

The release is accepted only after exact Node `20.18.1` / npm `10.8.2` delivery, authentic lockfile verification, OpenAPI drift, strict `vue-tsc`, all 26 Vitest cases, Maven/JUnit, 45 Playwright scenarios, Docker, clean PostgreSQL and staging are green.
