# v27.34.4 — Vue Secondary Navigation & Overtime Preview Contract Hotfix

## Why this release exists

The real Chromium gate for v27.34.3 passed 42 of 44 scenarios and exposed two independent contract defects:

1. a full planned shift correctly calculated to `0` overtime, but the preview endpoint reused persistence validation and returned HTTP `400` for that normal intermediate form state;
2. Tasks was enabled by the full onboarding preset but lived in Vue secondary navigation, while the shell scenario incorrectly assumed that every enabled route must be present in the primary navigation bar.

The failed validation job prevented image build and deployment. Docker, SSH and the staging host were not the cause.

## Changes

### Overtime preview

- Canonical interval preview may return zero or negative credited minutes as a successful calculation.
- The response keeps elapsed, break, planned, timezone and credited values so the editor can explain the draft.
- Credit creation and update still reject any calculated result of zero or less.
- The browser scenario now explicitly requires the zero-shift draft preview to return HTTP `200` with `creditedMinutes = 0` before extending the interval by two hours.

### Vue secondary navigation

- The visible **More / Ещё** control becomes active and exposes `aria-current="page"` when the current route belongs to secondary navigation.
- The matching route inside the More modal exposes the same active state.
- The shell E2E follows the public secondary-navigation surface instead of searching for Tasks inside primary navigation.

## Unchanged

- Spring Boot remains the business source of truth.
- Overtime persistence validation, FIFO, allocations, absence ownership, Payroll and closed-period rules are unchanged.
- The public API shape and OpenAPI schema are unchanged.
- PostgreSQL and Flyway remain at V47.
- Strict TypeScript and strict Playwright page-error/request collection remain enabled.
- Production remains one Spring Boot + Vue application image and one app container plus PostgreSQL.

## Acceptance

- frontend typecheck, 11 Vitest cases, Vite build and generated-bundle audit;
- focused service/controller regression for zero draft preview and strict persistence;
- static release contract for both root causes;
- Maven verify and release-check;
- all 44 Chromium Playwright scenarios;
- Docker image build, clean PostgreSQL migration smoke and staging deploy through GitHub Actions.
