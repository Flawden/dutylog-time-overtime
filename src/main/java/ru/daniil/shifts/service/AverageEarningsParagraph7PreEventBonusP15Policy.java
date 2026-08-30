package ru.daniil.shifts.service;

import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualBonusFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualOrigin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure paragraph-15 POLICY for the paragraph-7 pre-event fallback basis.
 *
 * <p>Paragraph 7 replaces the ordinary historical basis with the salary
 * attributable to actually worked days in the event month before the event.
 * Therefore this policy deliberately operates on the exact half-open basis
 * {@code [eventMonthStart,eventDate)} proved by J3B6B1 instead of calling the
 * ordinary 12-month {@link AverageEarningsBonusP15Policy}.</p>
 *
 * <p>The layer decides only legal eligibility and the later treatment shape.
 * It never calculates bonus money, never derives scheduled/worked ratios and
 * never selects paragraph 8. J3B6B3 will prove whether this short pre-event
 * basis was fully worked and, only if required by the decision below, apply a
 * proven worked-time ratio.</p>
 */
public final class AverageEarningsParagraph7PreEventBonusP15Policy {
    public static final String MONTHLY_DUPLICATE =
            "PP_540_P7_P15_MONTHLY_DUPLICATE_INDICATOR_MONTH";
    public static final String ORIGIN_NATURE_CONTRADICTION =
            "PP_540_P7_P15_ORIGIN_NATURE_CONTRADICTION";
    public static final String PRE_EVENT_SOURCE_WINDOW_CONTRADICTION =
            "PP_540_P7_P15_PRE_EVENT_SOURCE_WINDOW_CONTRADICTION";
    public static final String FACT_SHAPE_CONTRADICTION =
            "PP_540_P7_P15_FACT_SHAPE_CONTRADICTION";

    private AverageEarningsParagraph7PreEventBonusP15Policy() {
    }

    public enum LegalRule {
        PP_540_P15_MONTHLY,
        PP_540_P15_WORK_PERIOD,
        PP_540_P15_PREVIOUS_CALENDAR_YEAR
    }

    public enum Eligibility {
        INCLUDE,
        EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH,
        EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR
    }

    public enum AmountTreatment {
        FACTUAL_ACCRUED_AMOUNT,
        MONTHLY_PART_FOR_PRE_EVENT_MONTH
    }

    /**
     * Treatment to use only when J3B6B3 proves that the pre-event basis was
     * not fully worked. A fully worked basis requires no paragraph-15 time
     * reduction regardless of this conditional value.
     */
    public enum IncompletePreEventTreatment {
        NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME,
        PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME,
        REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT
    }

    public static Resolution resolve(
            AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution accrualAuthority
    ) {
        Objects.requireNonNull(
                accrualAuthority,
                "Paragraph-7 bonus P15 policy requires B6B1 accrual authority"
        );

        LocalDate eventDate = Objects.requireNonNull(
                accrualAuthority.eventDate(),
                "Paragraph-7 bonus P15 policy requires event date"
        );
        AverageEarningsLegalPolicy.requireRegime(eventDate);

        LocalDate periodFrom = YearMonth.from(eventDate).atDay(1);
        LocalDate cutoffExclusive = eventDate;
        if (!periodFrom.equals(accrualAuthority.periodFrom())
                || !cutoffExclusive.equals(accrualAuthority.cutoffExclusive())) {
            throw new IllegalArgumentException(
                    "B6B1 accrual authority does not match paragraph-7 pre-event window"
            );
        }

        if (!accrualAuthority.ready()) {
            return Resolution.blocked(
                    accrualAuthority,
                    Objects.requireNonNull(
                            accrualAuthority.blockingReason(),
                            "Blocked B6B1 authority requires blocker"
                    )
            );
        }

        List<AccrualBonusFact> facts = List.copyOf(accrualAuthority.bonusFacts());
        for (AccrualBonusFact fact : facts) {
            Objects.requireNonNull(fact, "Paragraph-7 bonus P15 policy cannot contain null FACT");
            String structuralBlocker = structuralBlocker(periodFrom, cutoffExclusive, fact);
            if (structuralBlocker != null) {
                return Resolution.blocked(accrualAuthority, structuralBlocker);
            }
        }

        String duplicate = monthlyDuplicateBlocker(eventDate, facts);
        if (duplicate != null) {
            return Resolution.blocked(accrualAuthority, duplicate);
        }

        YearMonth eventMonth = YearMonth.from(eventDate);
        int previousEventYear = eventDate.getYear() - 1;
        List<Decision> decisions = new ArrayList<>(facts.size());

        for (AccrualBonusFact fact : facts) {
            LegalRule legalRule;
            Eligibility eligibility;
            AmountTreatment amountTreatment;

            switch (fact.p15Nature()) {
                case MONTHLY -> {
                    legalRule = LegalRule.PP_540_P15_MONTHLY;
                    eligibility = eventMonth.equals(fact.accrualMonth())
                            ? Eligibility.INCLUDE
                            : Eligibility.EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH;
                    amountTreatment = AmountTreatment.FACTUAL_ACCRUED_AMOUNT;
                }
                case WORK_PERIOD -> {
                    legalRule = LegalRule.PP_540_P15_WORK_PERIOD;
                    eligibility = eventMonth.equals(fact.accrualMonth())
                            ? Eligibility.INCLUDE
                            : Eligibility.EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH;
                    /*
                     * A proven WORK_PERIOD reward exceeds one month by FACT.
                     * The paragraph-7 fallback basis occupies only the event
                     * calendar month and ends before eventDate. It is therefore
                     * shorter than every valid WORK_PERIOD reward, so paragraph
                     * 15 selects one monthly part for this event-month basis.
                     */
                    amountTreatment = AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH;
                }
                case ANNUAL_RESULT, SERVICE_LENGTH -> {
                    legalRule = LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR;
                    eligibility = isCompleteCalendarYear(fact, previousEventYear)
                            ? Eligibility.INCLUDE
                            : Eligibility.EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR;
                    amountTreatment = AmountTreatment.FACTUAL_ACCRUED_AMOUNT;
                }
                default -> throw new IllegalStateException(
                        "Unsupported paragraph-15 bonus nature: " + fact.p15Nature()
                );
            }

            IncompletePreEventTreatment incompleteTreatment = null;
            if (eligibility == Eligibility.INCLUDE) {
                incompleteTreatment = incompleteTreatment(
                        periodFrom,
                        cutoffExclusive,
                        fact
                );
            }

            decisions.add(new Decision(
                    fact,
                    legalRule,
                    eligibility,
                    amountTreatment,
                    incompleteTreatment
            ));
        }

        return Resolution.ready(accrualAuthority, decisions);
    }

    private static String structuralBlocker(
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            AccrualBonusFact fact
    ) {
        if (fact.origin() == AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY
                && fact.p15Nature() != PayrollBonusP15Nature.ANNUAL_RESULT
                && fact.p15Nature() != PayrollBonusP15Nature.SERVICE_LENGTH) {
            return ORIGIN_NATURE_CONTRADICTION + ":" + fact.bonusNatureFactId();
        }

        if (fact.origin() == AccrualOrigin.PRE_EVENT_SOURCE
                && (fact.sourcePeriodFrom().isBefore(periodFrom)
                || !fact.sourcePeriodTo().isBefore(cutoffExclusive))) {
            return PRE_EVENT_SOURCE_WINDOW_CONTRADICTION + ":" + fact.bonusNatureFactId();
        }

        boolean shapeValid = switch (fact.p15Nature()) {
            case MONTHLY -> YearMonth.from(fact.awardPeriodFrom())
                    .equals(YearMonth.from(fact.awardPeriodTo()));
            case WORK_PERIOD -> fact.awardPeriodTo()
                    .isAfter(fact.awardPeriodFrom().plusMonths(1).minusDays(1));
            case ANNUAL_RESULT -> isCompleteCalendarYear(
                    fact,
                    fact.awardPeriodFrom().getYear()
            );
            case SERVICE_LENGTH -> true;
        };
        if (!shapeValid) {
            return FACT_SHAPE_CONTRADICTION + ":" + fact.bonusNatureFactId();
        }
        return null;
    }

    private static String monthlyDuplicateBlocker(
            LocalDate eventDate,
            List<AccrualBonusFact> facts
    ) {
        YearMonth eventMonth = YearMonth.from(eventDate);
        Map<String, Long> seen = new HashMap<>();
        for (AccrualBonusFact fact : facts) {
            if (fact.p15Nature() != PayrollBonusP15Nature.MONTHLY
                    || !eventMonth.equals(fact.accrualMonth())) {
                continue;
            }
            Long previous = seen.putIfAbsent(fact.indicatorKey(), fact.bonusNatureFactId());
            if (previous != null) {
                return MONTHLY_DUPLICATE
                        + ":"
                        + eventMonth
                        + ":"
                        + fact.indicatorKey();
            }
        }
        return null;
    }

    private static IncompletePreEventTreatment incompleteTreatment(
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            AccrualBonusFact fact
    ) {
        boolean awardInsidePreEvent =
                !fact.awardPeriodFrom().isBefore(periodFrom)
                        && fact.awardPeriodTo().isBefore(cutoffExclusive);

        if (!awardInsidePreEvent) {
            return IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME;
        }
        if (Boolean.TRUE.equals(fact.accruedForActualWorkTime())) {
            return IncompletePreEventTreatment
                    .NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME;
        }
        if (Boolean.FALSE.equals(fact.accruedForActualWorkTime())) {
            return IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME;
        }
        return IncompletePreEventTreatment.REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT;
    }

    private static boolean isCompleteCalendarYear(
            AccrualBonusFact fact,
            int expectedYear
    ) {
        return fact.awardPeriodFrom().equals(LocalDate.of(expectedYear, 1, 1))
                && fact.awardPeriodTo().equals(LocalDate.of(expectedYear, 12, 31));
    }

    public record Decision(
            AccrualBonusFact sourceFact,
            LegalRule legalRule,
            Eligibility eligibility,
            AmountTreatment amountTreatment,
            IncompletePreEventTreatment incompletePreEventTreatment
    ) {
        public Decision {
            Objects.requireNonNull(sourceFact, "Paragraph-7 P15 decision source FACT is required");
            Objects.requireNonNull(legalRule, "Paragraph-7 P15 legal rule is required");
            Objects.requireNonNull(eligibility, "Paragraph-7 P15 eligibility is required");
            Objects.requireNonNull(amountTreatment, "Paragraph-7 P15 amount treatment is required");
            if ((eligibility == Eligibility.INCLUDE) != (incompletePreEventTreatment != null)) {
                throw new IllegalArgumentException(
                        "Paragraph-7 P15 incomplete-period treatment must exist exactly for included facts"
                );
            }
        }

        public boolean included() {
            return eligibility == Eligibility.INCLUDE;
        }
    }

    public record Resolution(
            LocalDate eventDate,
            LocalDate periodFrom,
            LocalDate cutoffExclusive,
            boolean ready,
            String blockingReason,
            AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution accrualAuthority,
            List<Decision> decisions
    ) {
        public Resolution {
            Objects.requireNonNull(eventDate, "Paragraph-7 P15 policy event date is required");
            Objects.requireNonNull(periodFrom, "Paragraph-7 P15 policy period start is required");
            Objects.requireNonNull(cutoffExclusive, "Paragraph-7 P15 policy cutoff is required");
            Objects.requireNonNull(accrualAuthority, "Paragraph-7 P15 policy provenance is required");
            decisions = List.copyOf(Objects.requireNonNull(
                    decisions,
                    "Paragraph-7 P15 policy decisions are required"
            ));
            if (!periodFrom.equals(YearMonth.from(eventDate).atDay(1))
                    || !cutoffExclusive.equals(eventDate)) {
                throw new IllegalArgumentException("Paragraph-7 P15 policy window is invalid");
            }
            if (ready == (blockingReason != null)) {
                throw new IllegalArgumentException("Paragraph-7 P15 policy state is invalid");
            }
            if (!ready && !decisions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blocked paragraph-7 P15 policy cannot expose partial decisions"
                );
            }
        }

        static Resolution ready(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution authority,
                List<Decision> decisions
        ) {
            return new Resolution(
                    authority.eventDate(),
                    authority.periodFrom(),
                    authority.cutoffExclusive(),
                    true,
                    null,
                    authority,
                    decisions
            );
        }

        static Resolution blocked(
                AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution authority,
                String reason
        ) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Paragraph-7 P15 policy blocker is required");
            }
            return new Resolution(
                    authority.eventDate(),
                    authority.periodFrom(),
                    authority.cutoffExclusive(),
                    false,
                    reason,
                    authority,
                    List.of()
            );
        }
    }
}
