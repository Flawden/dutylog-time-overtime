# Partial absence: unpaid break overlap

A scalar shift break total does not establish its position. A partial absence may supply `unpaidBreakOverlapMinutes`: an explicit user statement of how many unpaid break minutes lie inside the absence's intersection with the original shift. Blank/null remains unknown; zero is explicit evidence that the break lies outside that interval.

For overlap G, shift elapsed E and total unpaid break B, the supplied overlap must be between max(0, B - (E - G)) and min(B, G). Charged minutes are G minus that overlap, capped by scheduled paid minutes. Exact frozen break windows are still authoritative: any supplied value must agree with them. No original shift means blocked calculation; an explicit day off contributes zero.

The value is persisted on AbsencePeriod, exposed in period and occurrence DTOs, and consumed by TimeCompensationService. It is not a shift edit and does not relocate or rewrite historical breaks. This delivery adds absence-level input; editing exact break windows in the shift-template UI remains separate work.

Create/preview accept a nullable integer. PATCH without coverage and without the field preserves existing evidence (e.g. status-only approval); supplying coverage makes null clear the evidence. Full-day coverage rejects a non-null overlap and clears it when transitioning from partial. Changing an interval in the form therefore requires reviewing the overlap value.

PostgreSQL V91 adds a nullable field and a range/coverage check, with no backfill or automatic ownership/data adoption. Existing unresolved absences are not assigned guessed values. A clean PostgreSQL migration and deployment acceptance remain pending.

Browser fixtures now assign explicit schedules for FIFO/approval scenarios. The floating-break browser scenario verifies 120 elapsed absence minutes minus 30 unpaid minutes consumes 90 minutes. Local verification: OpenAPI drift check, vue-tsc, 100 Vitest cases, Vite build and bundle audit passed; Java 17 syntax passed. Full Maven verify, 64 browser cases and canonical Node/npm delivery check must pass in the Windows runner before commit. Cross-midnight absence selection and exact break-template editing are not introduced by this change.
