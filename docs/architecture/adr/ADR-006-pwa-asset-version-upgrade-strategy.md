# ADR-006 — PWA asset/version upgrade strategy

- Status: accepted
- Release: v27.37.0
- Date: 2026-08-06

## Context

DutyLog ships one Spring Boot image containing both the legacy compatibility runtime and the Vue bundle. A browser can keep an older service-worker cache while HTML and immutable application images advance. Mixing old JavaScript or CSS with new HTML creates non-deterministic failures that are difficult to diagnose.

## Decision

1. Every release embeds the semantic release version and immutable image build ID in one cache name: `dutylog-shell-v<version>-<build-id>`.
2. Service-worker activation deletes every prior DutyLog shell cache before claiming clients.
3. HTML, JavaScript and CSS remain network-first. Cache fallback is allowed only after the current network request fails.
4. API, login and logout requests are never intercepted or cached.
5. The shell registration uses `skipWaiting` plus `controllerchange` guarded by one reload per release.
6. Browser acceptance creates a synthetic previous-version cache before first registration and proves activation removes it while the new versioned cache becomes ready.
7. Rollback is an application-image rollback. The rolled-back service worker uses its own version/build cache and removes assets from the failed release during activation.

## Consequences

- Cached shell assets cannot silently cross release boundaries.
- An online upgrade prefers the current image; an offline session may use only assets already cached by its active release.
- PWA upgrade behavior is part of the recurring frontend release gate beginning with v27.37.0.
