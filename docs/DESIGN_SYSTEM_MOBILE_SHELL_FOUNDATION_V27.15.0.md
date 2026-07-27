# DutyLog v27.15.0 — Design System & Mobile Shell Foundation

## Goal

Start the new visual product phase without destabilising the mature scheduling, overtime, task, note and notification logic.

The release adds a presentation layer over the existing DOM and APIs instead of performing a high-risk screen rewrite.

## DutyLog Next

The new shell is enabled by default and provides:

- a branded top bar with a profile shortcut;
- an adaptive month controller;
- icon-based primary navigation;
- a safe-area-aware fixed bottom bar on phones;
- unified surfaces, borders, shadows, spacing and radii;
- consistent focus states for keyboard accessibility;
- refreshed cards, forms, buttons, calendar cells, settings, modals and loading state;
- light-theme and reduced-motion boundaries;
- a refreshed login screen that uses the same visual language.

The implementation lives in the separate `src/main/resources/static/design-system.css` file loaded after the legacy `app.css` layer.

## Classic fallback

The previous shell remains available as `Classic` from:

```text
Settings → Appearance → App shell
```

The choice is stored as the allowlisted profile theme field:

```json
{
  "themeConfig": {
    "shellMode": "next"
  }
}
```

Allowed values:

```text
next
classic
```

Unknown values are rejected by the server. No custom CSS is accepted or persisted.

## Compatibility boundary

The release does not change:

- shift occurrence or timezone projection logic;
- overtime intervals, FIFO allocations or balances;
- task deadlines or reminders;
- daily-note CRUD, tombstones or offline snapshots;
- database schema;
- mobile API or OpenAPI contracts.

Flyway remains V36.

## Regression coverage

New automated boundaries:

- `DesignSystemMobileShellFrontendContractTest` protects the HTML/CSS/JS contract and safe server enum.
- `ProfileControllerTest` protects `shellMode` persistence and validation.
- `design-system-shell.spec.js` protects mobile overflow, fixed navigation, ARIA current state and Next → Classic → Next switching.

Release baseline:

```text
92 Java test classes
485 @Test methods
23 Playwright scenarios
```

## Manual acceptance

1. Open DutyLog on a phone-width viewport.
2. Confirm the branded top bar and fixed five-item bottom navigation.
3. Open every main section and confirm there is no horizontal page overflow.
4. Open Settings → Appearance and switch to Classic.
5. Confirm the previous shell appears immediately and all data remains visible.
6. Switch back to DutyLog Next and save appearance.
7. Reload the PWA and confirm the shell remains Next.
8. Check both dark and light themes.
9. Check Android/iPhone safe-area spacing at the bottom.
10. Enable reduced motion in the OS and confirm shimmer/transitions are effectively disabled.
