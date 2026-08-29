package ru.daniil.shifts.model;

/**
 * Immutable provenance kind for one paragraph-15 scheduled-work fact.
 *
 * PLAN_DERIVED means DutyLog had no explicit factual interval for the
 * relevant schedule occurrence and froze the canonical posted-only Payroll
 * source projection. EXPLICIT_ACTUAL means plan/fact relation came from
 * explicit clock intervals through PlannedActualWorkRelationEngine.
 */
public enum PayrollSnapshotP15ScheduledWorkSourceKind {
    PLAN_DERIVED,
    EXPLICIT_ACTUAL
}
