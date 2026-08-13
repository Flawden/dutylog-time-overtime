# v27.40.27 — Legacy Header Async Boot Ownership Hotfix

## Evidence

The v27.40.26 Chromium artifact contains one deterministic failed scenario (`auth-onboarding.spec.js`) and a retry with the same failure. `#firstRunOnboarding` remains hidden because authenticated boot logs `TypeError: Cannot set properties of null (setting 'textContent')` at `70-user-boot.js:226`.

The corrected v27.40.26 ownership boundary removes `#legacyGlobalHeader` after Vue readiness. `#whoami` is a child of that recovery header, while `init()` is asynchronous and can resume after the node has been retired. The failure is therefore a legacy async ownership assumption, not an onboarding persistence failure.

## Fix

- Early `/api/auth/me` identity feedback writes to `#whoami` only when that recovery node still exists.
- `renderHeaderIdentity()` updates legacy identity/avatar/admin chrome only while the recovery identity node exists.
- Vue/platform state publication remains independent of legacy header lifetime.
- Header retirement stays immediate after successful Vue readiness; no duplicate post-ready chrome is restored.

## Non-goals

No HTTP/OpenAPI/Flyway change, no onboarding semantic change, no retry/timeout relaxation, no offline queue executor change and no dependency graph change. First-run onboarding remains the final intentionally live post-ready legacy presentation owner and is planned for v27.40.28.
