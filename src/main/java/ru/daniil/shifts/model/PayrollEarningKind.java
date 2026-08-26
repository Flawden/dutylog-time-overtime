package ru.daniil.shifts.model;

/**
 * Machine-owned semantic identity of a payroll earning.
 *
 * User-owned display names must never be used to infer these identities.
 */
public enum PayrollEarningKind {
    BASE_PAY(PayrollEarningPhase.BASE_PAY),
    HOLIDAY_PAY(PayrollEarningPhase.BASE_PAY),
    NIGHT_PREMIUM(PayrollEarningPhase.TIME_PREMIUM),
    HARMFUL_CONDITIONS(PayrollEarningPhase.WORK_ALLOWANCE),
    COMBINATION(PayrollEarningPhase.EXTERNAL_EPISODIC_ALLOWANCE),
    MONTHLY_BONUS(PayrollEarningPhase.PERFORMANCE_BONUS),
    ONE_TIME_BONUS(PayrollEarningPhase.PERFORMANCE_BONUS),
    REGIONAL_COEFFICIENT(PayrollEarningPhase.GROSS_COEFFICIENT),
    VACATION_PAY(PayrollEarningPhase.OTHER_EARNING),
    MEDICAL_COMPENSATION(PayrollEarningPhase.OTHER_EARNING);

    private final PayrollEarningPhase phase;

    PayrollEarningKind(PayrollEarningPhase phase) {
        this.phase = phase;
    }

    public PayrollEarningPhase phase() {
        return phase;
    }

    /**
     * Semantic identities that may be explicitly attached to a generic
     * compensation component.
     *
     * Specialized/native Payroll verticals deliberately remain outside this
     * configuration boundary.
     */
    public boolean isGenericCompensationComponentKind() {
        return switch (this) {
            case HARMFUL_CONDITIONS,
                    COMBINATION,
                    MONTHLY_BONUS,
                    ONE_TIME_BONUS,
                    REGIONAL_COEFFICIENT -> true;

            default -> false;
        };
    }
}
