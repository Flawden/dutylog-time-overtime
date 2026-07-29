# DutyLog v27.18.3 — UI Settings & Button Variants Quality Hotfix

## Problem 1: Theme palette was not an executable reset

`themeConfig.paletteId=theme` described the selected source, but the active primary
and secondary colors still lived in `accentColor` and `accentSecondary`. Selecting
Theme palette after custom edits could therefore leave those overrides untouched,
especially when the select already displayed the same value and emitted no change.

## Contract

- `theme` restores both accents from the active built-in theme package.
- a preset palette supplies both accents from its immutable package.
- `custom` preserves validated user colors.
- changing a built-in theme while `theme` is active immediately applies the new
  theme's primary and secondary accents.
- changing workspace or layout never changes the active palette.
- an explicit **Restore theme colors** command works even when the select already
  says Theme palette.
- the existing revisioned/serialized autosave queue prevents an older response
  from restoring pre-reset colors.

The release adds a small palette-source status with three states: Theme colors,
Preset palette and Customized.

## Problem 2: Ghost looked like Outline

The previous Ghost primary style used a dashed visible border. This made it a
variant of Outline rather than a borderless action.

The UI Core button contract now defines semantic tokens and distinct behavior for:

- Solid
- Soft
- Outline
- Ghost
- Secondary
- Danger
- Link
- Icon

Ghost keeps a transparent one-pixel border only to preserve geometry. It has no
visible idle border or shadow; its surface appears on hover/active. Outline keeps a
persistent accent border. Focus remains provided by the shared `:focus-visible`
ring. Disabled and `aria-busy` states are defined across the contract.

## Verification contract

The browser scenario covers:

1. Forest theme colors.
2. custom primary color.
3. select Theme palette and restore Forest colors.
4. inject a legacy inconsistent `paletteId=theme` snapshot with custom colors.
5. use the explicit reset without changing the select value.
6. reload and verify persistence.
7. switch to Midnight while Theme palette is active.
8. preserve a custom palette while changing workspace and layout.
9. compare computed Outline and Ghost styles.
10. verify seven semantic preview variants.

No database or domain API change is required. Flyway remains V36.
