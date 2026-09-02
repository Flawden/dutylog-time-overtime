package ru.daniil.shifts.model;

/**
 * Provenance of unpaid-break placement for work intervals.
 *
 * LEGACY_EARLY_TOTAL preserves historical scalar semantics.
 * EXPLICIT_WINDOWS means exact break positions are proven and snapshotted.
 */
public enum WorkBreakAuthority {
    LEGACY_EARLY_TOTAL,
    EXPLICIT_WINDOWS
}
