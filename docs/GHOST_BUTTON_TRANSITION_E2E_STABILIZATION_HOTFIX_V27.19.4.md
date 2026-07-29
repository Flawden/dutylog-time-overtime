# Ghost Button Transition E2E Stabilization Hotfix

Status: v27.19.4.

## Problem

`appearance-quality.spec.js` switched the preview from Outline to Ghost and immediately compared `borderTopColor` with one exact `rgba(0, 0, 0, 0)` string. Chromium sampled the 150 ms transition at an intermediate frame and serialized it as `oklab(... / 0.0306557)`, so the E2E failed deterministically even though the final Ghost token was transparent.

## Resolution

- wait for the border transition with `expect.poll`;
- measure the rendered border alpha through a 1×1 canvas pixel;
- require a non-zero alpha for Outline and zero alpha for Ghost;
- keep the Ghost no-shadow and hover-surface assertions;
- keep production CSS and transition timing unchanged.

## Compatibility

No application behavior, HTTP API, persistence or migration changes. Flyway remains V37. The baseline remains 97 Java test classes, 507 `@Test` methods and 28 Playwright scenarios.
