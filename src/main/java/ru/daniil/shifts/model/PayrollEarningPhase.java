package ru.daniil.shifts.model;

/**
 * Ordered structural phases of payroll earnings.
 *
 * This order controls dependency direction only. It must not be interpreted
 * as calculation-base eligibility: eligible-base membership is an independent
 * financial contract.
 */
public enum PayrollEarningPhase {
    BASE_PAY,
    TIME_PREMIUM,
    WORK_ALLOWANCE,
    EXTERNAL_EPISODIC_ALLOWANCE,
    PERFORMANCE_BONUS,
    GROSS_COEFFICIENT,
    OTHER_EARNING;

    /**
     * Returns whether this phase may read a result produced by sourcePhase.
     * Only strictly upstream phases are structurally readable.
     */
    public boolean canReadFrom(PayrollEarningPhase sourcePhase) {
        return sourcePhase != null
                && sourcePhase.ordinal() < ordinal();
    }
}
