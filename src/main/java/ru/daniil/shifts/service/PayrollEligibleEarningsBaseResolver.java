package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollEarningBaseEligibility;
import ru.daniil.shifts.model.PayrollEarningKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves a local calculation base from already-calculated semantic earnings.
 *
 * The resolver:
 * - never infers semantic identity from display text;
 * - never derives membership from phase order;
 * - preserves multiple source lines of the same semantic kind;
 * - does not resolve external reference amounts such as COMBINATION salary.
 */
public final class PayrollEligibleEarningsBaseResolver {

    private PayrollEligibleEarningsBaseResolver() {
    }

    public static Result resolve(
            PayrollEarningKind targetKind,
            List<Earning> source
    ) {
        Objects.requireNonNull(
                targetKind,
                "Target payroll earning kind is required"
        );

        Objects.requireNonNull(
                source,
                "Payroll earnings are required"
        );

        Set<PayrollEarningKind> eligibleKinds =
                PayrollEarningBaseEligibility
                        .eligibleKindsFor(
                                targetKind
                        );

        List<Earning> included =
                new ArrayList<>();

        long total = 0L;

        for (Earning earning : source) {
            Earning safe =
                    Objects.requireNonNull(
                            earning,
                            "Payroll earnings cannot contain null"
                    );

            if (!eligibleKinds.contains(
                    safe.kind()
            )) {
                continue;
            }

            try {
                total =
                        Math.addExact(
                                total,
                                safe.amountMinor()
                        );
            } catch (ArithmeticException ex) {
                throw new ArithmeticException(
                        "Eligible payroll earnings base overflow for "
                                + targetKind
                );
            }

            included.add(
                    safe
            );
        }

        return new Result(
                targetKind,
                eligibleKinds,
                total,
                included
        );
    }

    public record Earning(
            PayrollEarningKind kind,
            long amountMinor
    ) {
        public Earning {
            Objects.requireNonNull(
                    kind,
                    "Payroll earning kind is required"
            );

            if (amountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Payroll earning amount cannot be negative"
                );
            }
        }
    }

    public record Result(
            PayrollEarningKind targetKind,
            Set<PayrollEarningKind> eligibleKinds,
            long totalAmountMinor,
            List<Earning> includedEarnings
    ) {
        public Result {
            Objects.requireNonNull(
                    targetKind,
                    "Target payroll earning kind is required"
            );

            eligibleKinds =
                    Set.copyOf(
                            Objects.requireNonNull(
                                    eligibleKinds,
                                    "Eligible payroll earning kinds are required"
                            )
                    );

            if (totalAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Eligible payroll earnings base cannot be negative"
                );
            }

            includedEarnings =
                    List.copyOf(
                            Objects.requireNonNull(
                                    includedEarnings,
                                    "Included payroll earnings are required"
                            )
                    );
        }
    }
}
