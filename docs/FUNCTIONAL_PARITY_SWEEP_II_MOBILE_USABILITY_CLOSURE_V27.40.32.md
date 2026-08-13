# v27.40.32 — Functional Parity Sweep II & Mobile Usability Closure

## Purpose

This release closes the user-visible parity debt discovered during manual staging acceptance after the Vue retirement phase. It does not start Vacation/Payroll product expansion yet and does not reopen legacy ownership work.

## Overtime accrual projection

- The chart reads the server-owned daily projection totals (`dayEarnedHours`) once per visible work date instead of trusting a potentially stale historical row-level `hours` value.
- Single-part historical rows whose row value is zero may fall back to the canonical `sourceCreditHours` projection for visible earned amount.
- Zero values render as zero-height bars; the previous minimum-height rule no longer paints fake activity for a zero series.
- Mobile credit cards lead with **earned** source hours. Used/remaining values stay visible inside the expanded card.
- Actual time-bank usage still belongs to the real `usageDate`; allocation totals on source credits are not double-counted as chart usage.

## Accounting integrity UX

Machine codes remain stable diagnostics, but they no longer lead the user-facing warning. Equal issue codes are grouped and counted, with a human explanation first. Codes/source IDs remain available under “Технические детали”. No automatic data repair is performed by this release.

## Calendar information density

Shift type color becomes month-grid information again: working days receive a restrained tint and lower accent edge. On phone width the repeated shift name is hidden so the month can be scanned by color while the button keeps a full accessible label containing the shift name and other day facts. Full-day factual absence remains visually stronger than planned shift color.

## Mobile navigation

The fixed phone bottom bar is icon-only visually. Every route keeps an explicit accessible `aria-label`; desktop/sidebar labels remain unchanged.

## Non-goals

No HTTP/OpenAPI shape, Flyway migration, database ownership, authentication/session logic, module semantics, first-run onboarding boundary, offline `dataLayer` ownership, retry/timeout policy, or dependency graph changes.

## Acceptance

- Existing Playwright scenario count stays 48; overtime, calendar and mobile-layout journeys gain stricter assertions.
- Vitest adds canonical historical projection coverage.
- Executable Java source contracts prevent raw diagnostic UX, fake zero chart bars, lost shift-color semantics, or visible mobile nav labels from returning.
