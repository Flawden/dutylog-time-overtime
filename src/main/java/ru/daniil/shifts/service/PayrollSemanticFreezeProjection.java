package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollEarningKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure semantic coverage projection over already-calculated Payroll money.
 *
 * 8A3D1C deliberately classifies only money whose machine-owned identity is
 * proven by the current production model.
 *
 * Proven:
 * - native basePayMinor -> BASE_PAY.
 *
 * Explicitly NOT inferred yet:
 * - ordinary NIGHT/HOLIDAY aggregate premium money;
 * - overtime settlement money;
 * - generic compensation component money;
 * - manual ADDITION adjustments.
 *
 * Deductions are not earnings and therefore do not participate in historical
 * semantic earning completeness.
 */
public final class PayrollSemanticFreezeProjection {

    private PayrollSemanticFreezeProjection() {
    }

    public static Projection project(
            Source source
    ) {
        Objects.requireNonNull(
                source,
                "Semantic freeze source is required"
        );

        List<SemanticLine> classified =
                new ArrayList<>();

        if (source.basePayMinor() > 0L) {
            classified.add(
                    new SemanticLine(
                            PayrollEarningKind.BASE_PAY,
                            source.basePayMinor()
                    )
            );
        }

        if (source.ordinaryNightPremiumPayMinor()
                > 0L) {
            classified.add(
                    new SemanticLine(
                            PayrollEarningKind.NIGHT_PREMIUM,
                            source.ordinaryNightPremiumPayMinor()
                    )
            );
        }

        long classifiedAmount =
                Math.addExact(
                        source.basePayMinor(),
                        source.ordinaryNightPremiumPayMinor()
                );

        long unclassifiedAmount =
                Math.subtractExact(
                        source.ordinaryPremiumPayMinor(),
                        source.ordinaryNightPremiumPayMinor()
                );

        unclassifiedAmount =
                Math.addExact(
                        unclassifiedAmount,
                        source.settlementPayMinor()
                );

        unclassifiedAmount =
                Math.addExact(
                        unclassifiedAmount,
                        source.compensationComponentEarningsMinor()
                );

        unclassifiedAmount =
                Math.addExact(
                        unclassifiedAmount,
                        source.additionsMinor()
                );

        return new Projection(
                classified,
                classifiedAmount,
                unclassifiedAmount,
                unclassifiedAmount == 0L
        );
    }

    public record Source(
            long basePayMinor,
            long ordinaryPremiumPayMinor,
            long ordinaryNightPremiumPayMinor,
            long settlementPayMinor,
            long compensationComponentEarningsMinor,
            long additionsMinor
    ) {
        public Source(
                long basePayMinor,
                long ordinaryPremiumPayMinor,
                long settlementPayMinor,
                long compensationComponentEarningsMinor,
                long additionsMinor
        ) {
            this(
                    basePayMinor,
                    ordinaryPremiumPayMinor,
                    0L,
                    settlementPayMinor,
                    compensationComponentEarningsMinor,
                    additionsMinor
            );
        }

        public Source {
            if (basePayMinor < 0L
                    || ordinaryPremiumPayMinor < 0L
                    || ordinaryNightPremiumPayMinor < 0L
                    || settlementPayMinor < 0L
                    || compensationComponentEarningsMinor < 0L
                    || additionsMinor < 0L) {
                throw new IllegalArgumentException(
                        "Semantic freeze money sources must be non-negative"
                );
            }

            if (ordinaryNightPremiumPayMinor
                    > ordinaryPremiumPayMinor) {
                throw new IllegalArgumentException(
                        "Proven NIGHT premium cannot exceed ordinary premium aggregate"
                );
            }
        }

        public long totalEarningSourceAmountMinor() {
            long total =
                    basePayMinor;

            total =
                    Math.addExact(
                            total,
                            ordinaryPremiumPayMinor
                    );

            total =
                    Math.addExact(
                            total,
                            settlementPayMinor
                    );

            total =
                    Math.addExact(
                            total,
                            compensationComponentEarningsMinor
                    );

            return Math.addExact(
                    total,
                    additionsMinor
            );
        }
    }

    public record SemanticLine(
            PayrollEarningKind earningKind,
            long amountMinor
    ) {
        public SemanticLine {
            Objects.requireNonNull(
                    earningKind,
                    "Semantic earning kind is required"
            );

            if (amountMinor <= 0L) {
                throw new IllegalArgumentException(
                        "Frozen semantic earning line must contain positive money"
                );
            }
        }
    }

    public record Projection(
            List<SemanticLine> classifiedLines,
            long classifiedAmountMinor,
            long unclassifiedAmountMinor,
            boolean complete
    ) {
        public Projection {
            classifiedLines =
                    List.copyOf(
                            Objects.requireNonNull(
                                    classifiedLines,
                                    "Classified semantic lines are required"
                            )
                    );

            if (classifiedAmountMinor < 0L
                    || unclassifiedAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Semantic freeze amounts must be non-negative"
                );
            }

            long lineAmount =
                    0L;

            for (SemanticLine line :
                    classifiedLines) {
                lineAmount =
                        Math.addExact(
                                lineAmount,
                                line.amountMinor()
                        );
            }

            if (lineAmount
                    != classifiedAmountMinor) {
                throw new IllegalArgumentException(
                        "Classified semantic line sum does not match projection amount"
                );
            }

            if (complete
                    != (unclassifiedAmountMinor == 0L)) {
                throw new IllegalArgumentException(
                        "Semantic freeze completeness must equal zero unclassified money"
                );
            }
        }

        public long totalEarningSourceAmountMinor() {
            return Math.addExact(
                    classifiedAmountMinor,
                    unclassifiedAmountMinor
            );
        }
    }
}
