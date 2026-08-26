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
 * - native basePayMinor -> BASE_PAY;
 * - proven ordinary NIGHT money -> NIGHT_PREMIUM;
 * - positive generic compensation component lines whose immutable
 *   earningKind is explicitly present -> that exact PayrollEarningKind.
 *
 * Explicitly NOT inferred:
 * - unresolved ordinary premium money;
 * - overtime settlement money;
 * - generic compensation component lines with NULL earningKind;
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

        if (source.compensationComponentLines()
                == null) {

            /*
             * Compatibility path: aggregate-only callers carry no exact
             * machine semantic provenance, therefore generic money remains
             * unclassified.
             */
            unclassifiedAmount =
                    Math.addExact(
                            unclassifiedAmount,
                            source.compensationComponentEarningsMinor()
                    );

        } else {
            for (ComponentLine line :
                    source.compensationComponentLines()) {

                if (line.amountMinor() == 0L) {
                    continue;
                }

                if (line.earningKind() == null) {
                    unclassifiedAmount =
                            Math.addExact(
                                    unclassifiedAmount,
                                    line.amountMinor()
                            );

                } else {
                    classified.add(
                            new SemanticLine(
                                    line.earningKind(),
                                    line.amountMinor()
                            )
                    );

                    classifiedAmount =
                            Math.addExact(
                                    classifiedAmount,
                                    line.amountMinor()
                            );
                }
            }
        }

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
            List<ComponentLine> compensationComponentLines,
            long additionsMinor
    ) {
        public Source(
                long basePayMinor,
                long ordinaryPremiumPayMinor,
                long ordinaryNightPremiumPayMinor,
                long settlementPayMinor,
                long compensationComponentEarningsMinor,
                long additionsMinor
        ) {
            this(
                    basePayMinor,
                    ordinaryPremiumPayMinor,
                    ordinaryNightPremiumPayMinor,
                    settlementPayMinor,
                    compensationComponentEarningsMinor,
                    null,
                    additionsMinor
            );
        }

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
                    null,
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

            if (compensationComponentLines != null) {
                compensationComponentLines =
                        List.copyOf(
                                compensationComponentLines
                        );

                long componentAmount =
                        0L;

                for (int index = 0;
                        index < compensationComponentLines.size();
                        index++) {

                    ComponentLine line =
                            Objects.requireNonNull(
                                    compensationComponentLines.get(index),
                                    "Semantic component line is required"
                            );

                    if (line.lineIndex()
                            != index) {
                        throw new IllegalArgumentException(
                                "Semantic component line order is invalid"
                        );
                    }

                    componentAmount =
                            Math.addExact(
                                    componentAmount,
                                    line.amountMinor()
                            );
                }

                if (componentAmount
                        != compensationComponentEarningsMinor) {
                    throw new IllegalArgumentException(
                            "Semantic component line sum does not match Payroll aggregate"
                    );
                }
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

    public record ComponentLine(
            int lineIndex,
            PayrollEarningKind earningKind,
            long amountMinor
    ) {
        public ComponentLine {
            if (lineIndex < 0
                    || amountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Semantic component line identity or money is invalid"
                );
            }

            if (earningKind != null
                    && !earningKind
                            .isGenericCompensationComponentKind()) {
                throw new IllegalArgumentException(
                        "Semantic component line kind is not generic-component-owned"
                );
            }
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
