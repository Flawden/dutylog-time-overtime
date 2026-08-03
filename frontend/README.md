# DutyLog Vue frontend

`frontend/` is the migration boundary introduced in v27.33.0 and promoted to visible app-shell ownership in v27.34.0.

Vue 3 + TypeScript currently owns the brand, profile entry, primary/secondary navigation, online state, modal/toast hosts and shared design-system primitives. Legacy product screens remain authoritative and are reached only through the explicit immutable bridge.

## Local commands

```bash
npm install --package-lock=false
npm run typecheck
npm run test:unit
npm run build
```

Stable production assets:

```text
dist/dutylog-vue-app-shell.js
dist/dutylog-vue-app-shell.css
```

Maven packages these files into the same Spring Boot JAR under `static/vue`; production uses no separate frontend server/container.

## Migration rules

1. New product domains are added under `src/features/` only when migrated as complete bounded units.
2. Vue does not query or mutate legacy product DOM and never reads mutable `window.state`.
3. The transition bridge exposes only named capabilities and a frozen read model.
4. Business truth stays on Spring Boot; browser previews are never canonical persistence.
5. After a domain reaches parity, its numbered legacy implementation is removed in the same release line.
