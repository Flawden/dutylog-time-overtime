package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollBonusP15Nature;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure legal POLICY kernel for paragraph 15 of Government Resolution No. 540.
 *
 * <p>This boundary deliberately does not calculate premium money. It decides
 * only whether a proven premium fact is legally eligible, whether the later
 * formula must use the factual amount or a monthly-part allocation, and
 * whether paragraph 15 requires a proportional adjustment for time actually
 * worked in the reference period.</p>
 *
 * <p>Input facts must already be explicit/frozen upstream facts. In
 * particular, award period, reward nature, indicator identity and actual-work
 * accrual semantics are never inferred from display names, posting labels,
 * component configuration or money.</p>
 */
public final class AverageEarningsBonusP15Policy {

    private static final String MONTHLY_DUPLICATE =
            "PP_540_P15_MONTHLY_DUPLICATE_INDICATOR_MONTH";

    private static final String ACTUAL_WORK_TIME_UNKNOWN =
            "PP_540_P15_ACTUAL_WORK_TIME_ACCRUAL_UNKNOWN";

    private AverageEarningsBonusP15Policy() {
    }

    public enum LegalRule {
        PP_540_P15_MONTHLY,
        PP_540_P15_WORK_PERIOD,
        PP_540_P15_PREVIOUS_CALENDAR_YEAR
    }

    public enum Eligibility {
        INCLUDE,
        EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD,
        EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR
    }

    public enum AmountTreatment {
        FACTUAL_ACCRUED_AMOUNT,
        MONTHLY_PART_FOR_EACH_REFERENCE_MONTH
    }

    public enum ReferenceTimeAdjustment {
        NONE_REFERENCE_PERIOD_FULLY_WORKED,
        NONE_ALREADY_ACCRUED_FOR_ACTUAL_REFERENCE_TIME,
        PROPORTIONAL_TO_REFERENCE_WORKED_TIME
    }

    public static Resolution resolve(
            LocalDate eventDate,
            YearMonth referenceFrom,
            YearMonth referenceTo,
            boolean referencePeriodFullyWorked,
            List<BonusFact> facts
    ) {
        Objects.requireNonNull(eventDate, "Paragraph-15 policy requires event date");
        Objects.requireNonNull(referenceFrom, "Paragraph-15 policy requires reference start");
        Objects.requireNonNull(referenceTo, "Paragraph-15 policy requires reference end");
        Objects.requireNonNull(facts, "Paragraph-15 policy requires bonus facts");

        AverageEarningsLegalPolicy.requireRegime(eventDate);
        requireReferenceWindow(eventDate, referenceFrom, referenceTo);

        List<BonusFact> frozenFacts = List.copyOf(facts);
        for (BonusFact fact : frozenFacts) {
            Objects.requireNonNull(fact, "Paragraph-15 policy cannot contain null bonus fact");
        }

        String duplicateBlocker = monthlyDuplicateBlocker(referenceFrom, referenceTo, frozenFacts);
        if (duplicateBlocker != null) {
            return Resolution.blocked(duplicateBlocker);
        }

        LocalDate referenceFromDate = referenceFrom.atDay(1);
        LocalDate referenceToDate = referenceTo.atEndOfMonth();
        int previousEventYear = eventDate.getYear() - 1;

        List<Decision> decisions = new ArrayList<>(frozenFacts.size());

        for (BonusFact fact : frozenFacts) {
            Eligibility eligibility;
            AmountTreatment amountTreatment;
            LegalRule legalRule;

            switch (fact.p15Nature()) {
                case MONTHLY -> {
                    legalRule = LegalRule.PP_540_P15_MONTHLY;
                    eligibility = inReference(fact.accrualMonth(), referenceFrom, referenceTo)
                            ? Eligibility.INCLUDE
                            : Eligibility.EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD;
                    amountTreatment = AmountTreatment.FACTUAL_ACCRUED_AMOUNT;
                }
                case WORK_PERIOD -> {
                    legalRule = LegalRule.PP_540_P15_WORK_PERIOD;
                    eligibility = inReference(fact.accrualMonth(), referenceFrom, referenceTo)
                            ? Eligibility.INCLUDE
                            : Eligibility.EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD;
                    amountTreatment = exceedsReferenceDuration(fact)
                            ? AmountTreatment.MONTHLY_PART_FOR_EACH_REFERENCE_MONTH
                            : AmountTreatment.FACTUAL_ACCRUED_AMOUNT;
                }
                case ANNUAL_RESULT, SERVICE_LENGTH -> {
                    legalRule = LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR;
                    eligibility = isCompleteCalendarYear(fact, previousEventYear)
                            ? Eligibility.INCLUDE
                            : Eligibility.EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR;
                    amountTreatment = AmountTreatment.FACTUAL_ACCRUED_AMOUNT;
                }
                default -> throw new IllegalStateException(
                        "Unsupported paragraph-15 reward nature: " + fact.p15Nature()
                );
            }

            ReferenceTimeAdjustment timeAdjustment = null;
            if (eligibility == Eligibility.INCLUDE) {
                timeAdjustment = referenceTimeAdjustment(
                        referencePeriodFullyWorked,
                        referenceFromDate,
                        referenceToDate,
                        fact
                );
                if (timeAdjustment == null) {
                    return Resolution.blocked(
                            ACTUAL_WORK_TIME_UNKNOWN + ":" + fact.bonusNatureFactId()
                    );
                }
            }

            decisions.add(new Decision(
                    fact.bonusNatureFactId(),
                    fact.p15Nature(),
                    fact.indicatorKey(),
                    fact.accrualMonth(),
                    fact.amountMinor(),
                    fact.awardPeriodFrom(),
                    fact.awardPeriodTo(),
                    fact.proratedForPartialAwardPeriod(),
                    legalRule,
                    eligibility,
                    amountTreatment,
                    timeAdjustment
            ));
        }

        return Resolution.ready(decisions);
    }

    private static void requireReferenceWindow(
            LocalDate eventDate,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        AverageEarningsReferenceWindow.of(
                eventDate,
                referenceFrom,
                referenceTo
        );
    }

    private static String monthlyDuplicateBlocker(
            YearMonth referenceFrom,
            YearMonth referenceTo,
            List<BonusFact> facts
    ) {
        Map<MonthlyIndicatorKey, Long> seen = new HashMap<>();

        for (BonusFact fact : facts) {
            if (fact.p15Nature() != PayrollBonusP15Nature.MONTHLY
                    || !inReference(fact.accrualMonth(), referenceFrom, referenceTo)) {
                continue;
            }

            MonthlyIndicatorKey key = new MonthlyIndicatorKey(
                    fact.accrualMonth(),
                    fact.indicatorKey()
            );

            Long previous = seen.putIfAbsent(key, fact.bonusNatureFactId());
            if (previous != null) {
                return MONTHLY_DUPLICATE
                        + ":"
                        + fact.accrualMonth()
                        + ":"
                        + fact.indicatorKey();
            }
        }

        return null;
    }

    private static ReferenceTimeAdjustment referenceTimeAdjustment(
            boolean referencePeriodFullyWorked,
            LocalDate referenceFrom,
            LocalDate referenceTo,
            BonusFact fact
    ) {
        if (referencePeriodFullyWorked) {
            return ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED;
        }

        boolean awardInsideReference =
                !fact.awardPeriodFrom().isBefore(referenceFrom)
                        && !fact.awardPeriodTo().isAfter(referenceTo);

        if (!awardInsideReference) {
            return ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME;
        }

        if (Boolean.TRUE.equals(fact.accruedForActualWorkTime())) {
            return ReferenceTimeAdjustment.NONE_ALREADY_ACCRUED_FOR_ACTUAL_REFERENCE_TIME;
        }

        if (Boolean.FALSE.equals(fact.accruedForActualWorkTime())) {
            return ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME;
        }

        return null;
    }

    private static boolean exceedsReferenceDuration(BonusFact fact) {
        LocalDate maximumWithinReferenceDuration =
                fact.awardPeriodFrom().plusMonths(12).minusDays(1);
        return fact.awardPeriodTo().isAfter(maximumWithinReferenceDuration);
    }

    private static boolean isCompleteCalendarYear(BonusFact fact, int expectedYear) {
        return fact.awardPeriodFrom().equals(LocalDate.of(expectedYear, 1, 1))
                && fact.awardPeriodTo().equals(LocalDate.of(expectedYear, 12, 31));
    }

    private static boolean inReference(
            YearMonth month,
            YearMonth referenceFrom,
            YearMonth referenceTo
    ) {
        return !month.isBefore(referenceFrom) && !month.isAfter(referenceTo);
    }

    private record MonthlyIndicatorKey(
            YearMonth month,
            String indicatorKey
    ) {
    }

    public record BonusFact(
            long bonusNatureFactId,
            YearMonth accrualMonth,
            PayrollBonusP15Nature p15Nature,
            String indicatorKey,
            long amountMinor,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean accruedForActualWorkTime,
            Boolean proratedForPartialAwardPeriod
    ) {
        public BonusFact {
            if (bonusNatureFactId <= 0L) {
                throw new IllegalArgumentException("Paragraph-15 bonus identity must be positive");
            }
            Objects.requireNonNull(accrualMonth, "Paragraph-15 accrual month is required");
            Objects.requireNonNull(p15Nature, "Paragraph-15 reward nature is required");
            if (indicatorKey == null || !indicatorKey.matches("[A-Z0-9][A-Z0-9._:-]{0,95}")) {
                throw new IllegalArgumentException("Paragraph-15 indicator identity is invalid");
            }
            if (amountMinor <= 0L) {
                throw new IllegalArgumentException("Paragraph-15 factual amount must be positive");
            }
            if (awardPeriodFrom == null || awardPeriodTo == null
                    || awardPeriodTo.isBefore(awardPeriodFrom)) {
                throw new IllegalArgumentException("Paragraph-15 award period is invalid");
            }

            switch (p15Nature) {
                case MONTHLY -> {
                    if (!YearMonth.from(awardPeriodFrom).equals(YearMonth.from(awardPeriodTo))) {
                        throw new IllegalArgumentException(
                                "Monthly paragraph-15 fact must stay within one award month"
                        );
                    }
                }
                case WORK_PERIOD -> {
                    if (!awardPeriodTo.isAfter(awardPeriodFrom.plusMonths(1).minusDays(1))) {
                        throw new IllegalArgumentException(
                                "Work-period paragraph-15 fact must exceed one month"
                        );
                    }
                }
                case ANNUAL_RESULT -> {
                    if (awardPeriodFrom.getMonthValue() != 1
                            || awardPeriodFrom.getDayOfMonth() != 1
                            || awardPeriodTo.getMonthValue() != 12
                            || awardPeriodTo.getDayOfMonth() != 31
                            || awardPeriodFrom.getYear() != awardPeriodTo.getYear()) {
                        throw new IllegalArgumentException(
                                "Annual-result paragraph-15 fact must cover one complete calendar year"
                        );
                    }
                }
                case SERVICE_LENGTH -> {
                    // Event-relative previous-calendar-year eligibility is POLICY below.
                }
            }
        }
    }

    public record Decision(
            long bonusNatureFactId,
            PayrollBonusP15Nature p15Nature,
            String indicatorKey,
            YearMonth accrualMonth,
            long factualAmountMinor,
            LocalDate awardPeriodFrom,
            LocalDate awardPeriodTo,
            Boolean proratedForPartialAwardPeriod,
            LegalRule legalRule,
            Eligibility eligibility,
            AmountTreatment amountTreatment,
            ReferenceTimeAdjustment referenceTimeAdjustment
    ) {
        public Decision {
            if (bonusNatureFactId <= 0L
                    || p15Nature == null
                    || indicatorKey == null
                    || accrualMonth == null
                    || factualAmountMinor <= 0L
                    || awardPeriodFrom == null
                    || awardPeriodTo == null
                    || legalRule == null
                    || eligibility == null
                    || amountTreatment == null) {
                throw new IllegalArgumentException("Paragraph-15 decision is invalid");
            }
            if (eligibility == Eligibility.INCLUDE && referenceTimeAdjustment == null) {
                throw new IllegalArgumentException(
                        "Included paragraph-15 decision requires reference-time treatment"
                );
            }
            if (eligibility != Eligibility.INCLUDE && referenceTimeAdjustment != null) {
                throw new IllegalArgumentException(
                        "Excluded paragraph-15 decision cannot carry reference-time treatment"
                );
            }
        }

        public boolean included() {
            return eligibility == Eligibility.INCLUDE;
        }
    }

    public record Resolution(
            boolean ready,
            String blockingReason,
            List<Decision> decisions
    ) {
        public Resolution {
            decisions = List.copyOf(Objects.requireNonNull(
                    decisions,
                    "Paragraph-15 policy decisions are required"
            ));
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Paragraph-15 policy resolution state is invalid");
            }
            if (!ready && !decisions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-15 policy cannot expose partial decisions"
                );
            }
        }

        public static Resolution ready(List<Decision> decisions) {
            return new Resolution(true, null, decisions);
        }

        public static Resolution blocked(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Paragraph-15 blocker reason is required");
            }
            return new Resolution(false, reason, List.of());
        }
    }
}
