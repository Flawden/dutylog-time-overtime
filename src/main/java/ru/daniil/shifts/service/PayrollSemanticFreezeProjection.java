package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure semantic coverage projection over already-calculated Payroll money.
 *
 * 8A3D1C deliberately classifies only money whose machine-owned identity is
 * proven by the current production model.
 *
 * 8A4E2A adds a compatibility-safe source-line provenance boundary. Callers
 * that can prove split earning/coverage facts may provide detailed BASE_PAY,
 * NIGHT_PREMIUM or generic-component lines. Aggregate-only callers keep the
 * old behavior and therefore keep null quantity/period provenance instead of
 * inventing it from the posting month.
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
 * - manual ADDITION adjustments;
 * - earning/coverage periods from posting month or effective-dated config.
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

        appendNativeLines(
                classified,
                PayrollEarningKind.BASE_PAY,
                source.basePayMinor(),
                source.basePayLines()
        );

        appendNativeLines(
                classified,
                PayrollEarningKind.NIGHT_PREMIUM,
                source.ordinaryNightPremiumPayMinor(),
                source.ordinaryNightPremiumLines()
        );

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
                            line.toSemanticLine()
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

    private static void appendNativeLines(
            List<SemanticLine> target,
            PayrollEarningKind requiredKind,
            long aggregateAmountMinor,
            List<SemanticLine> detailedLines
    ) {
        if (detailedLines == null) {
            if (aggregateAmountMinor > 0L) {
                target.add(
                        new SemanticLine(
                                requiredKind,
                                aggregateAmountMinor
                        )
                );
            }

            return;
        }

        long detailedAmount = 0L;

        for (SemanticLine line : detailedLines) {
            if (line.earningKind()
                    != requiredKind) {
                throw new IllegalArgumentException(
                        "Detailed semantic source line kind mismatch for "
                                + requiredKind
                );
            }

            detailedAmount =
                    Math.addExact(
                            detailedAmount,
                            line.amountMinor()
                    );
        }

        if (detailedAmount
                != aggregateAmountMinor) {
            throw new IllegalArgumentException(
                    "Detailed semantic source line sum does not match "
                            + requiredKind
                            + " aggregate"
            );
        }

        target.addAll(
                detailedLines
        );
    }

    public record Source(
            long basePayMinor,
            long ordinaryPremiumPayMinor,
            long ordinaryNightPremiumPayMinor,
            long settlementPayMinor,
            long compensationComponentEarningsMinor,
            List<ComponentLine> compensationComponentLines,
            long additionsMinor,
            List<SemanticLine> basePayLines,
            List<SemanticLine> ordinaryNightPremiumLines
    ) {
        /**
         * Compatibility constructor for the pre-8A4E2A production path.
         *
         * Null detailed-line lists intentionally mean "aggregate identity only".
         */
        public Source(
                long basePayMinor,
                long ordinaryPremiumPayMinor,
                long ordinaryNightPremiumPayMinor,
                long settlementPayMinor,
                long compensationComponentEarningsMinor,
                List<ComponentLine> compensationComponentLines,
                long additionsMinor
        ) {
            this(
                    basePayMinor,
                    ordinaryPremiumPayMinor,
                    ordinaryNightPremiumPayMinor,
                    settlementPayMinor,
                    compensationComponentEarningsMinor,
                    compensationComponentLines,
                    additionsMinor,
                    null,
                    null
            );
        }

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
                    additionsMinor,
                    null,
                    null
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
                    additionsMinor,
                    null,
                    null
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

            if (basePayLines != null) {
                basePayLines =
                        copySemanticLines(
                                basePayLines,
                                "Detailed BASE_PAY semantic line is required"
                        );
            }

            if (ordinaryNightPremiumLines != null) {
                ordinaryNightPremiumLines =
                        copySemanticLines(
                                ordinaryNightPremiumLines,
                                "Detailed NIGHT semantic line is required"
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

        private static List<SemanticLine> copySemanticLines(
                List<SemanticLine> source,
                String nullMessage
        ) {
            List<SemanticLine> copied =
                    List.copyOf(
                            source
                    );

            if (copied.stream()
                    .anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        nullMessage
                );
            }

            return copied;
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
            long amountMinor,
            PayrollQualifiedQuantity qualifiedQuantity,
            LocalDate earningPeriodFrom,
            LocalDate earningPeriodTo,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        public ComponentLine(
                int lineIndex,
                PayrollEarningKind earningKind,
                long amountMinor
        ) {
            this(
                    lineIndex,
                    earningKind,
                    amountMinor,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

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

            requireOrderedPair(
                    earningPeriodFrom,
                    earningPeriodTo,
                    "earning"
            );

            requireOrderedPair(
                    coverageFrom,
                    coverageTo,
                    "coverage"
            );

            if (earningKind == null
                    && (qualifiedQuantity != null
                    || earningPeriodFrom != null
                    || coverageFrom != null)) {
                throw new IllegalArgumentException(
                        "Unclassified component money cannot carry semantic provenance"
                );
            }
        }

        private SemanticLine toSemanticLine() {
            return new SemanticLine(
                    earningKind,
                    amountMinor,
                    qualifiedQuantity,
                    earningPeriodFrom,
                    earningPeriodTo,
                    coverageFrom,
                    coverageTo
            );
        }
    }

    public record SemanticLine(
            PayrollEarningKind earningKind,
            long amountMinor,
            PayrollQualifiedQuantity qualifiedQuantity,
            LocalDate earningPeriodFrom,
            LocalDate earningPeriodTo,
            LocalDate coverageFrom,
            LocalDate coverageTo
    ) {
        public SemanticLine(
                PayrollEarningKind earningKind,
                long amountMinor
        ) {
            this(
                    earningKind,
                    amountMinor,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

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

            requireOrderedPair(
                    earningPeriodFrom,
                    earningPeriodTo,
                    "earning"
            );

            requireOrderedPair(
                    coverageFrom,
                    coverageTo,
                    "coverage"
            );
        }
    }

    private static void requireOrderedPair(
            LocalDate from,
            LocalDate to,
            String label
    ) {
        if ((from == null)
                != (to == null)
                || (from != null
                && to.isBefore(
                        from
                ))) {
            throw new IllegalArgumentException(
                    "Semantic "
                            + label
                            + " period is invalid"
            );
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
