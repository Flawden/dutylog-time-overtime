package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualBonusFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.AmountTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Decision;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Eligibility;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.IncompletePreEventTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusWorkTimeFactService.WorkMeasureUnit;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure paragraph-7 paragraph-15 BONUS money formula for the exact pre-event basis.
 *
 * <p>B6B2 has already decided eligibility and amount treatment. B6B3A has already
 * proved whether the exact {@code [eventMonthStart,eventDate)} basis needs and has
 * a usable scheduled/worked-time relation. This layer only converts those proven
 * decisions and FACTs into included BONUS minor units.</p>
 *
 * <p>A WORK_PERIOD decision contributes exactly one monthly part to the paragraph-7
 * event-month basis. When the pre-event schedule is incomplete, the worked-time
 * coefficient is applied only where B6B2 requires it. Monthly-part division and
 * worked-time proration share one exact rational expression, rounded HALF_UP once
 * at the final minor-unit boundary.</p>
 *
 * <p>No repositories are read here, no work-time facts are rediscovered, no whole
 * paragraph-7 wage numerator is assembled and no later legal fallback is selected.</p>
 */
public final class AverageEarningsParagraph7PreEventBonusP15Formula {
    public static final String AUTHORITY_POLICY_CONTRADICTION =
            "PP_540_P7_P15_FORMULA_AUTHORITY_POLICY_CONTRADICTION";
    public static final String MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED =
            "PP_540_P7_P15_MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED";
    public static final String POLICY_CONTRADICTION =
            "PP_540_P7_P15_FORMULA_POLICY_CONTRADICTION";
    public static final String ACTUAL_WORK_TIME_ACCRUAL_FACT_REQUIRED =
            "PP_540_P7_P15_ACTUAL_WORK_TIME_ACCRUAL_FACT_REQUIRED";
    public static final String INCLUDED_CURRENCY_MISMATCH =
            "PP_540_P7_P15_INCLUDED_CURRENCY_MISMATCH";
    public static final String INCLUDED_TOTAL_OVERFLOW =
            "PP_540_P7_P15_INCLUDED_TOTAL_OVERFLOW";

    private AverageEarningsParagraph7PreEventBonusP15Formula() {
    }

    public enum AppliedPreEventAdjustment {
        NONE_PRE_EVENT_SCHEDULE_FULLY_WORKED,
        NONE_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME,
        PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME
    }

    public static Calculation calculate(
            AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution workTimeAuthority
    ) {
        Objects.requireNonNull(
                workTimeAuthority,
                "Paragraph-7 BONUS formula requires B6B3A work-time authority"
        );

        LocalDate eventDate = Objects.requireNonNull(
                workTimeAuthority.eventDate(),
                "Paragraph-7 BONUS formula requires event date"
        );
        AverageEarningsLegalPolicy.requireRegime(eventDate);
        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        LocalDate cutoffExclusive = eventDate;

        AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy =
                Objects.requireNonNull(
                        workTimeAuthority.policy(),
                        "Paragraph-7 BONUS formula requires B6B2 policy provenance"
                );

        if (!periodFrom.equals(workTimeAuthority.periodFrom())
                || !cutoffExclusive.equals(workTimeAuthority.cutoffExclusive())
                || !eventDate.equals(policy.eventDate())
                || !periodFrom.equals(policy.periodFrom())
                || !cutoffExclusive.equals(policy.cutoffExclusive())) {
            return Calculation.blocked(
                    eventDate,
                    periodFrom,
                    workTimeAuthority,
                    AUTHORITY_POLICY_CONTRADICTION
            );
        }

        if (!workTimeAuthority.ready()) {
            return Calculation.blocked(
                    eventDate,
                    periodFrom,
                    workTimeAuthority,
                    Objects.requireNonNull(
                            workTimeAuthority.blockingReason(),
                            "Blocked B6B3A authority requires blocker"
                    )
            );
        }
        if (!policy.ready()) {
            return Calculation.blocked(
                    eventDate,
                    periodFrom,
                    workTimeAuthority,
                    AUTHORITY_POLICY_CONTRADICTION
            );
        }

        boolean expectedWorkTime = policy.decisions().stream()
                .filter(Decision::included)
                .anyMatch(decision -> decision.incompletePreEventTreatment()
                        != IncompletePreEventTreatment
                        .NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME);
        if (workTimeAuthority.workTimeRequired() != expectedWorkTime) {
            return Calculation.blocked(
                    eventDate,
                    periodFrom,
                    workTimeAuthority,
                    AUTHORITY_POLICY_CONTRADICTION
            );
        }

        List<Line> lines = new ArrayList<>(policy.decisions().size());
        long totalMinor = 0L;
        String includedCurrency = null;

        for (Decision decision : policy.decisions()) {
            Objects.requireNonNull(decision, "Paragraph-7 BONUS formula cannot contain null decision");
            AccrualBonusFact fact = Objects.requireNonNull(
                    decision.sourceFact(),
                    "Paragraph-7 BONUS formula decision requires source FACT"
            );

            if (!decision.included()) {
                lines.add(Line.excluded(decision));
                continue;
            }

            if (includedCurrency == null) {
                includedCurrency = fact.currencyCode();
            } else if (!includedCurrency.equals(fact.currencyCode())) {
                return Calculation.blocked(
                        eventDate,
                        periodFrom,
                        workTimeAuthority,
                        INCLUDED_CURRENCY_MISMATCH + ":" + fact.bonusNatureFactId()
                );
            }

            BigInteger numerator = BigInteger.valueOf(fact.factualAmountMinor());
            BigInteger denominator = BigInteger.ONE;
            Integer awardMonthCount = null;

            if (decision.amountTreatment() == AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH) {
                if (fact.p15Nature() != PayrollBonusP15Nature.WORK_PERIOD
                        || decision.legalRule()
                        != AverageEarningsParagraph7PreEventBonusP15Policy.LegalRule.PP_540_P15_WORK_PERIOD) {
                    return Calculation.blocked(
                            eventDate,
                            periodFrom,
                            workTimeAuthority,
                            POLICY_CONTRADICTION + ":" + fact.bonusNatureFactId()
                    );
                }
                if (!isWholeCalendarMonthPeriod(fact.awardPeriodFrom(), fact.awardPeriodTo())) {
                    return Calculation.blocked(
                            eventDate,
                            periodFrom,
                            workTimeAuthority,
                            MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED
                                    + ":"
                                    + fact.bonusNatureFactId()
                    );
                }
                awardMonthCount = inclusiveMonthCount(
                        YearMonth.from(fact.awardPeriodFrom()),
                        YearMonth.from(fact.awardPeriodTo())
                );
                if (awardMonthCount <= 1) {
                    return Calculation.blocked(
                            eventDate,
                            periodFrom,
                            workTimeAuthority,
                            POLICY_CONTRADICTION + ":" + fact.bonusNatureFactId()
                    );
                }
                denominator = denominator.multiply(BigInteger.valueOf(awardMonthCount));
            } else if (decision.amountTreatment() != AmountTreatment.FACTUAL_ACCRUED_AMOUNT) {
                return Calculation.blocked(
                        eventDate,
                        periodFrom,
                        workTimeAuthority,
                        POLICY_CONTRADICTION + ":" + fact.bonusNatureFactId()
                );
            }

            AppliedPreEventAdjustment appliedAdjustment;
            AppliedWorkedTimeFact appliedWorkedTime = null;

            if (workTimeAuthority.workTimeRequired()
                    && workTimeAuthority.scheduleFullyWorked()) {
                appliedAdjustment = AppliedPreEventAdjustment
                        .NONE_PRE_EVENT_SCHEDULE_FULLY_WORKED;
            } else {
                switch (decision.incompletePreEventTreatment()) {
                    case NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME ->
                            appliedAdjustment = AppliedPreEventAdjustment
                                    .NONE_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME;
                    case PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME -> {
                        if (!workTimeAuthority.workTimeRequired()) {
                            return Calculation.blocked(
                                    eventDate,
                                    periodFrom,
                                    workTimeAuthority,
                                    AUTHORITY_POLICY_CONTRADICTION
                            );
                        }
                        appliedAdjustment = AppliedPreEventAdjustment
                                .PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME;
                        appliedWorkedTime = new AppliedWorkedTimeFact(
                                workTimeAuthority.unit(),
                                workTimeAuthority.workedUnits(),
                                workTimeAuthority.normUnits()
                        );
                        numerator = numerator.multiply(
                                BigInteger.valueOf(appliedWorkedTime.workedUnits())
                        );
                        denominator = denominator.multiply(
                                BigInteger.valueOf(appliedWorkedTime.normUnits())
                        );
                    }
                    case REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT -> {
                        return Calculation.blocked(
                                eventDate,
                                periodFrom,
                                workTimeAuthority,
                                ACTUAL_WORK_TIME_ACCRUAL_FACT_REQUIRED
                                        + ":"
                                        + fact.bonusNatureFactId()
                        );
                    }
                    default -> throw new IllegalStateException(
                            "Unsupported paragraph-7 incomplete-period treatment"
                    );
                }
            }

            long includedAmountMinor;
            try {
                includedAmountMinor = roundHalfUpToLong(numerator, denominator);
                totalMinor = Math.addExact(totalMinor, includedAmountMinor);
            } catch (ArithmeticException overflow) {
                return Calculation.blocked(
                        eventDate,
                        periodFrom,
                        workTimeAuthority,
                        INCLUDED_TOTAL_OVERFLOW
                );
            }

            lines.add(Line.included(
                    decision,
                    appliedAdjustment,
                    awardMonthCount,
                    appliedWorkedTime,
                    includedAmountMinor
            ));
        }

        return Calculation.ready(
                eventDate,
                periodFrom,
                workTimeAuthority,
                includedCurrency,
                lines,
                totalMinor
        );
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
            throw new IllegalArgumentException("Paragraph-7 BONUS award month count is invalid");
        }
        return (int) months;
    }

    private static long roundHalfUpToLong(
            BigInteger numerator,
            BigInteger denominator
    ) {
        if (numerator.signum() < 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("Paragraph-7 BONUS money ratio is invalid");
        }
        BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        BigInteger result = quotientAndRemainder[0];
        if (quotientAndRemainder[1].shiftLeft(1).compareTo(denominator) >= 0) {
            result = result.add(BigInteger.ONE);
        }
        return result.longValueExact();
    }

    public record AppliedWorkedTimeFact(
            WorkMeasureUnit unit,
            long workedUnits,
            long normUnits
    ) {
        public AppliedWorkedTimeFact {
            Objects.requireNonNull(unit, "Paragraph-7 BONUS applied work-measure unit is required");
            if (normUnits <= 0L || workedUnits < 0L || workedUnits > normUnits) {
                throw new IllegalArgumentException(
                        "Paragraph-7 BONUS applied worked-time ratio is invalid"
                );
            }
        }
    }

    public record Line(
            Decision policyDecision,
            AppliedPreEventAdjustment appliedAdjustment,
            Integer awardMonthCount,
            AppliedWorkedTimeFact appliedWorkedTime,
            long includedAmountMinor
    ) {
        public Line {
            Objects.requireNonNull(policyDecision, "Paragraph-7 BONUS formula line policy is required");
            if (includedAmountMinor < 0L) {
                throw new IllegalArgumentException("Paragraph-7 BONUS formula line money is invalid");
            }

            boolean included = policyDecision.included();
            if (!included) {
                if (appliedAdjustment != null
                        || awardMonthCount != null
                        || appliedWorkedTime != null
                        || includedAmountMinor != 0L) {
                    throw new IllegalArgumentException(
                            "Excluded paragraph-7 BONUS line cannot carry money factors"
                    );
                }
            } else {
                Objects.requireNonNull(
                        appliedAdjustment,
                        "Included paragraph-7 BONUS line requires applied time treatment"
                );
                boolean monthlyPart = policyDecision.amountTreatment()
                        == AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH;
                if (monthlyPart != (awardMonthCount != null)) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 BONUS monthly-part factors are inconsistent"
                    );
                }
                if (awardMonthCount != null && awardMonthCount <= 1) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 BONUS monthly-part month count is invalid"
                    );
                }
                boolean proportional = appliedAdjustment
                        == AppliedPreEventAdjustment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME;
                if (proportional != (appliedWorkedTime != null)) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 BONUS worked-time factor is inconsistent"
                    );
                }
            }
        }

        static Line excluded(Decision decision) {
            return new Line(decision, null, null, null, 0L);
        }

        static Line included(
                Decision decision,
                AppliedPreEventAdjustment adjustment,
                Integer awardMonthCount,
                AppliedWorkedTimeFact workedTime,
                long includedAmountMinor
        ) {
            return new Line(
                    decision,
                    adjustment,
                    awardMonthCount,
                    workedTime,
                    includedAmountMinor
            );
        }

        public long bonusNatureFactId() {
            return policyDecision.sourceFact().bonusNatureFactId();
        }

        public Eligibility eligibility() {
            return policyDecision.eligibility();
        }

        public String currencyCode() {
            return policyDecision.sourceFact().currencyCode();
        }
    }

    public record Calculation(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution workTimeAuthority,
            String currencyCode,
            List<Line> lines,
            long includedPremiumAmountMinor
    ) {
        public Calculation {
            Objects.requireNonNull(eventDate, "Paragraph-7 BONUS calculation event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 BONUS calculation period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 BONUS calculation cutoff is required");
            Objects.requireNonNull(
                    workTimeAuthority,
                    "Paragraph-7 BONUS calculation requires B6B3A provenance"
            );
            lines = List.copyOf(Objects.requireNonNull(
                    lines,
                    "Paragraph-7 BONUS calculation lines are required"
            ));
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)
                    || includedPremiumAmountMinor < 0L) {
                throw new IllegalArgumentException("Paragraph-7 BONUS calculation window is invalid");
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Paragraph-7 BONUS calculation state is invalid");
            }
            if (ready) {
                if (currencyCode != null && !currencyCode.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException("Paragraph-7 BONUS calculation currency is invalid");
                }
                long lineTotal = 0L;
                String lineCurrency = null;
                for (Line line : lines) {
                    lineTotal = Math.addExact(lineTotal, line.includedAmountMinor());
                    if (!line.policyDecision().included()) {
                        continue;
                    }
                    String candidateCurrency = line.currencyCode();
                    if (lineCurrency == null) {
                        lineCurrency = candidateCurrency;
                    } else if (!lineCurrency.equals(candidateCurrency)) {
                        throw new IllegalArgumentException(
                                "Ready paragraph-7 BONUS calculation mixes included currencies"
                        );
                    }
                }
                if (lineTotal != includedPremiumAmountMinor
                        || !Objects.equals(lineCurrency, currencyCode)) {
                    throw new IllegalArgumentException(
                            "Paragraph-7 BONUS calculation lines do not preserve money/currency"
                    );
                }
            } else if (blockingReason.isBlank()
                    || currencyCode != null
                    || !lines.isEmpty()
                    || includedPremiumAmountMinor != 0L) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-7 BONUS calculation cannot expose partial money"
                );
            }
        }

        static Calculation ready(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution authority,
                String currencyCode,
                List<Line> lines,
                long totalMinor
        ) {
            return new Calculation(
                    eventDate,
                    periodFrom,
                    eventDate,
                    true,
                    null,
                    authority,
                    currencyCode,
                    lines,
                    totalMinor
            );
        }

        static Calculation blocked(
                LocalDate eventDate,
                LocalDate periodFrom,
                AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution authority,
                String reason
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Paragraph-7 BONUS formula blocker is required");
            }
            return new Calculation(
                    eventDate,
                    periodFrom,
                    eventDate,
                    false,
                    reason,
                    authority,
                    null,
                    List.of(),
                    0L
            );
        }
    }
}
