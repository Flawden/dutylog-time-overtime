package ru.daniil.shifts.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit machine-owned membership of local earnings calculation bases.
 *
 * Phase ordering constrains dependency direction only. It does not imply
 * membership in a calculation base. Every eligible source kind listed here
 * is therefore intentional business semantics.
 *
 * This policy is separate from generic compensation-component CalculationBase
 * values such as NOMINAL_SALARY and EARNED_BASE_PAY.
 */
public final class PayrollEarningBaseEligibility {

    private static final Map<
            PayrollEarningKind,
            Set<PayrollEarningKind>
            > LOCAL_BASES =
            Map.of(
                    PayrollEarningKind.HARMFUL_CONDITIONS,
                    Set.of(
                            PayrollEarningKind.BASE_PAY,
                            PayrollEarningKind.HOLIDAY_PAY
                    ),

                    PayrollEarningKind.MONTHLY_BONUS,
                    Set.of(
                            PayrollEarningKind.BASE_PAY,
                            PayrollEarningKind.HOLIDAY_PAY,
                            PayrollEarningKind.HARMFUL_CONDITIONS,
                            PayrollEarningKind.NIGHT_PREMIUM,
                            PayrollEarningKind.COMBINATION
                    ),

                    PayrollEarningKind.REGIONAL_COEFFICIENT,
                    Set.of(
                            PayrollEarningKind.BASE_PAY,
                            PayrollEarningKind.HOLIDAY_PAY,
                            PayrollEarningKind.HARMFUL_CONDITIONS,
                            PayrollEarningKind.NIGHT_PREMIUM,
                            PayrollEarningKind.COMBINATION,
                            PayrollEarningKind.MONTHLY_BONUS,
                            PayrollEarningKind.ONE_TIME_BONUS
                    )
            );

    static {
        for (Map.Entry<
                PayrollEarningKind,
                Set<PayrollEarningKind>
                > entry : LOCAL_BASES.entrySet()) {

            PayrollEarningKind target =
                    entry.getKey();

            for (PayrollEarningKind source
                    : entry.getValue()) {

                if (!target.phase()
                        .canReadFrom(
                                source.phase()
                        )) {
                    throw new IllegalStateException(
                            "Eligible payroll earning "
                                    + source
                                    + " is not strictly upstream of "
                                    + target
                    );
                }
            }
        }
    }

    private PayrollEarningBaseEligibility() {
    }

    public static boolean hasLocalBase(
            PayrollEarningKind target
    ) {
        return target != null
                && LOCAL_BASES.containsKey(
                        target
                );
    }

    public static Set<PayrollEarningKind> definedTargets() {
        return Set.copyOf(
                LOCAL_BASES.keySet()
        );
    }

    public static Set<PayrollEarningKind> eligibleKindsFor(
            PayrollEarningKind target
    ) {
        Objects.requireNonNull(
                target,
                "Target payroll earning kind is required"
        );

        Set<PayrollEarningKind> eligible =
                LOCAL_BASES.get(
                        target
                );

        if (eligible == null) {
            throw new IllegalArgumentException(
                    "No local eligible earnings base is defined for "
                            + target
            );
        }

        return eligible;
    }

    public static boolean isEligible(
            PayrollEarningKind target,
            PayrollEarningKind source
    ) {
        Objects.requireNonNull(
                source,
                "Source payroll earning kind is required"
        );

        return eligibleKindsFor(
                target
        ).contains(
                source
        );
    }
}
