# v27.40.33 — Overtime Chart Projection Fixture Contract Hotfix

## CI evidence
Exact Node 20 validation for v27.40.32 stopped in `frontend/src/features/absence-time-bank/types/model.spec.ts` with two deterministic contradictory overtime chart test fixtures. The yearly case expected `earnedHours: 5` but received `8`; the paired daily case failed for the same reason.

## Root cause
The shared `credit()` test helper defaults both row `hours` and canonical projection totals to 4 h. Two chart tests overrode row `hours` to 3 h / 2 h but did not override `projection.dayEarnedHours`, leaving impossible fixtures where the visible row said 3 h while the server-owned day projection still said 4 h. v27.40.32 intentionally made chart accrual projection-first, so the model correctly consumed the canonical 4 h + 4 h values.

## Hotfix
The affected fixtures now override matching `dayEarnedHours`, `sourceCreditHours`, usage and remaining projection fields. Runtime `model.ts`, API/OpenAPI, Flyway, backend calculation, calendar, navigation, auth, onboarding and offline ownership are unchanged.

## Contract
Production semantics remain: earned chart values come from canonical server day projections once per visible work date; time-bank usage is plotted on `usageDate`; credit-level `usedHours` is not double-counted.
