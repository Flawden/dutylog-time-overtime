# v26.6.7 — onboarding and today highlight hotfix

Carried into release candidate: v27.2.2.
Status: v26.6.7.

This hotfix keeps the stabilization scope: no new product features, only UI clarity.

## Onboarding preset polish

- The middle first-run preset was renamed from `Работа + переработки` to `Стандарт`.
- Preset pills now reflect the actual selected draft:
  - `Минимум` is highlighted after choosing the minimum preset.
  - `Стандарт` is highlighted after choosing the standard preset.
  - `Всё включить` is highlighted after choosing the full preset.
- Manual checkbox changes clear the preset highlight unless the resulting module set exactly matches one of the presets again.
- Preset buttons expose `aria-pressed` for accessibility.

## Calendar today highlight

The current day is now highlighted at the cell level, not only by the small `сегодня` label near the number. This makes today easier to find in an empty or low-data calendar month.

## Release guardrails

`release-check.sh` verifies that:

- onboarding no longer contains the old `Работа + переработки` preset text;
- the `Стандарт` preset is present;
- preset state is rendered through `renderOnboardingPresetState`;
- the calendar uses `todayCell`;
- CSS contains the stronger `.cell.todayCell` styling.
