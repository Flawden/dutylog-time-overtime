# v26.6.8 — console and module details UX hotfix

Status: v26.6.8.

This hotfix keeps the feature freeze. It only corrects UX/runtime polish regressions found after the compact modules screen.

## What changed

- Regular users no longer see module contract counters or technical module details.
- Administrators still see contract counters and the collapsed “Technical details” section.
- Admin-only registration/user-list requests are not fired during generic settings initialization. They run only when the admin system page is opened by an administrator.
- `renderTimeSettings()` no longer shadows the translation helper `t()`.

## Why

The modules screen should stay user-facing for normal accounts. Runtime/API/offline contracts are developer/admin information and made the interface feel noisy.

The browser console should also stay clean during normal usage: avoidable `403` requests make debugging real issues harder.

## Scope

No new features were added. This is a release-stabilization hotfix for UX clarity and console cleanliness.
