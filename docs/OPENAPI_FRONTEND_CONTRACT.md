# OpenAPI-generated frontend contract

Canonical source:

```text
src/main/resources/static/openapi/dutylog-v1.yaml
```

Generated output:

```text
frontend/src/generated/dutylog-api.ts
```

Commands:

```bash
npm --prefix frontend run contract:generate
npm --prefix frontend run contract:check
```

The generated file contains the source SHA-256, component schema types, operation metadata and request/response type mapping. Referenced schemas, inline object bodies and array responses remain typed; unresolved description-only endpoints are explicitly `unknown` rather than invented. `createGeneratedDutyLogApiClient()` resolves an `operationId`, path parameters and query parameters, then delegates same-origin credentials, CSRF and request diagnostics to the shared HTTP client.

Never hand-edit generated types. Change OpenAPI, regenerate, review both files and commit them together.
