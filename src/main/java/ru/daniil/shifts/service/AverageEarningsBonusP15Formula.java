package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollBonusP15Nature;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.AmountTreatment;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.Decision;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.Eligibility;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.ReferenceTimeAdjustment;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.Resolution;

/**
 * Pure FORMULA kernel for paragraph 15 premium money.
 *
 * <p>The preceding policy kernel decides legal eligibility, amount treatment
 * and whether a reference-time proportional adjustment is required. This
 * class converts those decisions into exact minor-unit amounts. It deliberately
 * does not discover historical premiums, choose a work-time regime, derive
 * worked-time ratios from payroll snapshots, merge ordinary earnings, apply
 * paragraph 5 exclusions or calculate average daily/vacation money.</p>
 *
 * <p>All proportional calculations use one exact rational expression and are
 * rounded HALF_UP once, at the final minor-unit boundary. A long-period award
 * may use monthly-part treatment only when the explicit award period is a
 * sequence of whole calendar months; otherwise the formula blocks rather than
 * inventing how partial months should count. For that long-period branch the
 * monthly part is applied for each of the 12 months of the canonical reference
 * period, exactly as selected by the policy kernel.</p>
 */
public final class AverageEarningsBonusP15Formula {

    private static final String REFERENCE_WORKED_TIME_FACT_MISSING =
            "PP_540_P15_REFERENCE_WORKED_TIME_FACT_MISSING";

    private static final String MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED =
            "PP_540_P15_MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED";

    private static final String POLICY_CONTRADICTION =
            "PP_540_P15_FORMULA_POLICY_CONTRADICTION";

    private static final String TOTAL_OVERFLOW =
            "PP_540_P15_INCLUDED_TOTAL_OVERFLOW";

    private AverageEarningsBonusP15Formula() {
    }

    public enum WorkMeasureUnit {
        WORKING_DAYS,
        WORKING_MINUTES
    }

    /**
     * Explicit formula fact for paragraph-15 reference-period proration.
     *
     * <p>The unit is preserved for audit. Which unit is legally authoritative
     * for a concrete employee (for example, working days or working minutes)
     * is intentionally an upstream FACT/POLICY responsibility and is never
     * inferred here.</p>
     */
    public record ReferenceWorkedTimeFact(
            WorkMeasureUnit unit,
            long workedUnits,
            long normUnits
    ) {
        public ReferenceWorkedTimeFact {
            Objects.requireNonNull(unit, "Paragraph-15 work-measure unit is required");
            if (normUnits <= 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-15 reference work norm must be positive"
                );
            }
            if (workedUnits < 0L || workedUnits > normUnits) {
                throw new IllegalArgumentException(
                        "Paragraph-15 worked units must be within [0, norm]"
                );
            }
        }
    }

    public static Calculation calculate(
            YearMonth referenceFrom,
            YearMonth referenceTo,
            Resolution policyResolution,
            ReferenceWorkedTimeFact referenceWorkedTime
    ) {
        Objects.requireNonNull(referenceFrom, "Paragraph-15 formula requires reference start");
        Objects.requireNonNull(referenceTo, "Paragraph-15 formula requires reference end");
        Objects.requireNonNull(policyResolution, "Paragraph-15 formula requires policy resolution");
        requireCanonicalTwelveMonthWindow(referenceFrom, referenceTo);

        if (!policyResolution.ready()) {
            return Calculation.blocked(policyResolution.blockingReason());
        }

        boolean requiresReferenceProration = policyResolution.decisions().stream()
                .anyMatch(decision -> decision.included()
                        && decision.referenceTimeAdjustment()
                        == ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME);

        if (requiresReferenceProration && referenceWorkedTime == null) {
            return Calculation.blocked(REFERENCE_WORKED_TIME_FACT_MISSING);
        }

        List<Line> lines = new ArrayList<>(policyResolution.decisions().size());
        long total = 0L;

        for (Decision decision : policyResolution.decisions()) {
            if (!decision.included()) {
                lines.add(Line.excluded(decision));
                continue;
            }

            BigInteger numerator = BigInteger.valueOf(decision.factualAmountMinor());
            BigInteger denominator = BigInteger.ONE;
            Integer awardMonthCount = null;
            Integer referenceMonthCount = null;

            if (decision.amountTreatment() == AmountTreatment.MONTHLY_PART_FOR_EACH_REFERENCE_MONTH) {
                if (decision.p15Nature() != PayrollBonusP15Nature.WORK_PERIOD) {
                    return Calculation.blocked(
                            POLICY_CONTRADICTION + ":" + decision.bonusNatureFactId()
                    );
                }
                if (!isWholeCalendarMonthPeriod(
                        decision.awardPeriodFrom(),
                        decision.awardPeriodTo()
                )) {
                    return Calculation.blocked(
                            MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED
                                    + ":"
                                    + decision.bonusNatureFactId()
                    );
                }

                awardMonthCount = inclusiveMonthCount(
                        YearMonth.from(decision.awardPeriodFrom()),
                        YearMonth.from(decision.awardPeriodTo())
                );
                if (awardMonthCount <= 12) {
                    return Calculation.blocked(
                            POLICY_CONTRADICTION + ":" + decision.bonusNatureFactId()
                    );
                }

                referenceMonthCount = inclusiveMonthCount(referenceFrom, referenceTo);

                numerator = numerator.multiply(BigInteger.valueOf(referenceMonthCount));
                denominator = denominator.multiply(BigInteger.valueOf(awardMonthCount));
            } else if (decision.amountTreatment() != AmountTreatment.FACTUAL_ACCRUED_AMOUNT) {
                return Calculation.blocked(
                        POLICY_CONTRADICTION + ":" + decision.bonusNatureFactId()
                );
            }

            ReferenceWorkedTimeFact appliedReferenceWorkedTime = null;
            if (decision.referenceTimeAdjustment()
                    == ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME) {
                appliedReferenceWorkedTime = referenceWorkedTime;
                numerator = numerator.multiply(BigInteger.valueOf(referenceWorkedTime.workedUnits()));
                denominator = denominator.multiply(BigInteger.valueOf(referenceWorkedTime.normUnits()));
            }

            long includedAmountMinor = roundHalfUpToLong(numerator, denominator);

            try {
                total = Math.addExact(total, includedAmountMinor);
            } catch (ArithmeticException overflow) {
                return Calculation.blocked(TOTAL_OVERFLOW);
            }

            lines.add(Line.included(
                    decision,
                    awardMonthCount,
                    referenceMonthCount,
                    appliedReferenceWorkedTime,
                    includedAmountMinor
            ));
        }

        return Calculation.ready(lines, total);
    }

    private static void requireCanonicalTwelveMonthWindow(
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        if (!referenceTo.equals(referenceFrom.plusMonths(11))) {
            throw new IllegalArgumentException(
                    "Paragraph-15 formula requires a canonical 12-month reference window"
            );
        }
    }

    private static boolean isWholeCalendarMonthPeriod(
            LocalDate from,
            LocalDate to
    ) {
        YearMonth fromMonth = YearMonth.from(from);
        YearMonth toMonth = YearMonth.from(to);
        return from.equals(fromMonth.atDay(1)) && to.equals(toMonth.atEndOfMonth());
    }

    private static int inclusiveMonthCount(YearMonth from, YearMonth to) {
        long months = ChronoUnit.MONTHS.between(from, to) + 1L;
        if (months <= 0L || months > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Paragraph-15 award month count is invalid");
        }
        return (int) months;
    }

    private static long roundHalfUpToLong(
            BigInteger numerator,
            BigInteger denominator
    ) {
        if (numerator.signum() < 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("Paragraph-15 money ratio is invalid");
        }

        BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        BigInteger result = quotientAndRemainder[0];
        if (quotientAndRemainder[1].shiftLeft(1).compareTo(denominator) >= 0) {
            result = result.add(BigInteger.ONE);
        }
        return result.longValueExact();
    }

    public record Line(
            long bonusNatureFactId,
            Eligibility eligibility,
            AmountTreatment amountTreatment,
            ReferenceTimeAdjustment referenceTimeAdjustment,
            long factualAmountMinor,
            Integer awardMonthCount,
            Integer referenceMonthCount,
            ReferenceWorkedTimeFact referenceWorkedTime,
            long includedAmountMinor
    ) {
        public Line {
            if (bonusNatureFactId <= 0L
                    || eligibility == null
                    || amountTreatment == null
                    || factualAmountMinor <= 0L
                    || includedAmountMinor < 0L) {
                throw new IllegalArgumentException("Paragraph-15 formula line is invalid");
            }

            boolean included = eligibility == Eligibility.INCLUDE;
            if (!included) {
                if (referenceTimeAdjustment != null
                        || awardMonthCount != null
                        || referenceMonthCount != null
                        || referenceWorkedTime != null
                        || includedAmountMinor != 0L) {
                    throw new IllegalArgumentException(
                            "Excluded paragraph-15 formula line cannot carry money factors"
                    );
                }
            } else {
                if (referenceTimeAdjustment == null) {
                    throw new IllegalArgumentException(
                            "Included paragraph-15 formula line requires reference-time treatment"
                    );
                }

                boolean monthlyPart = amountTreatment
                        == AmountTreatment.MONTHLY_PART_FOR_EACH_REFERENCE_MONTH;
                boolean hasAwardMonthCount = awardMonthCount != null;
                boolean hasReferenceMonthCount = referenceMonthCount != null;
                if ((monthlyPart && !(hasAwardMonthCount && hasReferenceMonthCount))
                        || (!monthlyPart && (hasAwardMonthCount || hasReferenceMonthCount))) {
                    throw new IllegalArgumentException(
                            "Paragraph-15 monthly-part line has inconsistent month factors"
                    );
                }
                if (awardMonthCount != null
                        && (awardMonthCount <= 12
                        || referenceMonthCount != 12
                        || referenceMonthCount > awardMonthCount)) {
                    throw new IllegalArgumentException(
                            "Paragraph-15 monthly-part factors are invalid"
                    );
                }

                boolean proportional = referenceTimeAdjustment
                        == ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME;
                if (proportional != (referenceWorkedTime != null)) {
                    throw new IllegalArgumentException(
                            "Paragraph-15 reference-time factor is inconsistent"
                    );
                }
            }
        }

        private static Line excluded(Decision decision) {
            return new Line(
                    decision.bonusNatureFactId(),
                    decision.eligibility(),
                    decision.amountTreatment(),
                    null,
                    decision.factualAmountMinor(),
                    null,
                    null,
                    null,
                    0L
            );
        }

        private static Line included(
                Decision decision,
                Integer awardMonthCount,
                Integer referenceMonthCount,
                ReferenceWorkedTimeFact referenceWorkedTime,
                long includedAmountMinor
        ) {
            return new Line(
                    decision.bonusNatureFactId(),
                    decision.eligibility(),
                    decision.amountTreatment(),
                    decision.referenceTimeAdjustment(),
                    decision.factualAmountMinor(),
                    awardMonthCount,
                    referenceMonthCount,
                    referenceWorkedTime,
                    includedAmountMinor
            );
        }
    }

    public record Calculation(
            boolean ready,
            String blockingReason,
            List<Line> lines,
            long includedPremiumAmountMinor
    ) {
        public Calculation {
            lines = List.copyOf(Objects.requireNonNull(
                    lines,
                    "Paragraph-15 formula lines are required"
            ));
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Paragraph-15 formula state is invalid");
            }
            if (!ready && (!lines.isEmpty() || includedPremiumAmountMinor != 0L)) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-15 formula cannot expose partial money"
                );
            }
            if (ready && includedPremiumAmountMinor < 0L) {
                throw new IllegalArgumentException(
                        "Paragraph-15 included premium total cannot be negative"
                );
            }
        }

        public static Calculation ready(List<Line> lines, long totalMinor) {
            return new Calculation(true, null, lines, totalMinor);
        }

        public static Calculation blocked(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Paragraph-15 formula blocker is required");
            }
            return new Calculation(false, reason, List.of(), 0L);
        }
    }
}
