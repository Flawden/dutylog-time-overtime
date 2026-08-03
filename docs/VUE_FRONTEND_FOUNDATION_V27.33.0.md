# v27.33.0 — Vue Frontend Foundation & CI/CD

## Product decision

DutyLog pauses new large product features while the entire browser application moves from ordered numbered JavaScript to Vue 3 + TypeScript. The migration is incremental, but the destination is not a permanent hybrid: after parity every legacy domain file and the bridge are removed.

## Deployment topology

The topology remains unchanged: one Spring Boot image serves both backend and Vue frontend assets.

```text
nginx
  -> dutylog-app (Spring Boot + packaged Vue assets)
  -> dutylog-postgres
```

There is no frontend runtime container. Node and Vite exist only in development and build stages. Maven packages `frontend/dist` into `BOOT-INF/classes/static/vue`, and Spring serves the bundle from the same origin as the API and session cookie.

## Foundation boundary

`frontend/` owns:

- Vue 3 application bootstrap;
- strict TypeScript configuration;
- Pinia platform state;
- Vue Router in memory-history mode;
- the typed same-origin HTTP/CSRF client;
- the explicit legacy bridge;
- Vitest unit tests;
- Vite production output.

The released hash router remains authoritative until the Vue app-shell release. Vue is forbidden from querying or mutating legacy DOM. It may use only `window.DutyLogLegacyPlatform` capabilities or typed `CustomEvent` messages.

## Build order

```text
npm install (frontend)
-> vue-tsc
-> Vitest
-> Vite build
-> Maven verify
-> Playwright
-> Docker multi-stage build
-> clean PostgreSQL migration smoke
```

The Docker image repeats the frontend compilation in an isolated Node stage before Maven packaging. The runtime image contains only the JRE and the single Spring Boot JAR.

## Browser readiness contract

`shell-bootstrap.js` creates `window.__dutylogVueReady` before the module bundle runs. The Vue bootstrap resolves that promise by dispatching `dutylog:vue-ready`, publishes immutable diagnostics in `window.DutyLogVuePlatform`, and marks `#dutylog-vue-root` with:

```text
data-vue-ready="true"
data-vue-version="27.33.0"
data-vue-architecture="vue-foundation-v1"
```

The foundation is intentionally headless. No existing navigation, modal, form, calendar, offline or accessibility behavior is replaced in this release.

## Migration rules

1. New large features are not added to numbered legacy JavaScript.
2. A feature domain migrates as one bounded unit.
3. Vue never reaches into mutable legacy state or DOM directly.
4. Legacy code never mutates Vue-owned DOM.
5. Backend services remain the source of business truth.
6. Each migrated domain receives TypeScript, Vitest and Playwright coverage.
7. Legacy code is deleted immediately after parity, not left as a permanent fallback.
8. The application remains one repository, version, image and deployment.

## Compatibility

No HTTP endpoint, DTO, database table, security rule, session cookie, service-worker strategy, payroll rule or Flyway migration changes. Flyway remains V47.
