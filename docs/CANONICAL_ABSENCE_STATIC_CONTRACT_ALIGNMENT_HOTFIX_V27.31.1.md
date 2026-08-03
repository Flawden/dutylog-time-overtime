# v27.31.1 — Canonical Absence Static Contract Alignment Hotfix

GitHub Actions compiled the v27.31.0 application and executed all 625 Java tests. Five historical source-string assertions still described the pre-retirement frontend shape and failed after the canonical absence ledger intentionally changed those exact implementation strings.

This hotfix changes no production behavior. It aligns the historical contracts with the released architecture:

- absence drafts serialize the selected `coverage` value directly, including transition-only imported `HOURS_ONLY`, rather than reconstructing only `PARTIAL` or `FULL_DAY`;
- legacy usage promotion is opened by `openLegacyUsageMigration(focusId)`;
- the preview renderer accepts the preview read model as an explicit argument;
- linked usages are labelled as managed by their owner absence, while only not-yet-promoted legacy manual usages retain their transitional delete action;
- old manual usages expose promotion into absences instead of direct editing.

The focused regression guard proves both sides of the boundary: the canonical strings exist and the retired strings do not return. Unified Absence Composer, FIFO allocation, legacy promotion, V47, API behavior, PostgreSQL data and Payroll are unchanged.
