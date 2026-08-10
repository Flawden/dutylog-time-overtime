# Vue frontend diagnostics and recovery

Status: active from v27.35.0.

Every shared frontend request sends a bounded `X-Request-Id`; Spring echoes or replaces it, and the client records method, sanitized path, status, release and request ID. Query strings and fragments are discarded, and server-supplied IDs/messages are control-character cleaned and length-bounded. API errors retain HTTP status, stable code and correlation ID.

Vue descendant errors are captured by `AppErrorBoundary`. Global Vue errors and unhandled promise rejections are recorded by the same diagnostics state. The recovery UI shows safe metadata and offers reload or return to Today; it does not expose stack traces, tokens or response bodies.

The `unhandledrejection` listener deliberately does not call `preventDefault`. Unexpected failures therefore remain visible to strict Playwright runtime collection instead of being hidden for a green build.

`window.DutyLogVuePlatform.diagnostics()` exposes an immutable safe snapshot for support and tests.

## v27.39.0 production source-map policy

ADR-008 makes production source maps fail-closed. A normal Vite build uses `sourcemap: false`; an operator must explicitly set `DUTYLOG_FRONTEND_SOURCEMAPS=true` to produce hidden maps for controlled diagnostics. Runtime HTML and the service worker do not reference `.map` assets, and the safe diagnostics snapshot remains intentionally limited to release/route/request metadata. Integration bearer URLs, Telegram link codes, cookies, CSRF values, response bodies and stack traces are not part of that snapshot.
