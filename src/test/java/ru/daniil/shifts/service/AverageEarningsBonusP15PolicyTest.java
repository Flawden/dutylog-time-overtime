package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollBonusP15Nature;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.*;

class AverageEarningsBonusP15PolicyTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 15);
    private static final YearMonth FROM = YearMonth.of(2025, 8);
    private static final YearMonth TO = YearMonth.of(2026, 7);

    @Test
    void monthlyAccruedInsideReferenceIsIncludedAtFactualAmount() {
        var decision = only(resolve(false, monthly(1, YearMonth.of(2026, 3), "KPI", true)));

        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_MONTHLY, decision.legalRule());
        assertEquals(AmountTreatment.FACTUAL_ACCRUED_AMOUNT, decision.amountTreatment());
        assertEquals(
                ReferenceTimeAdjustment.NONE_ALREADY_ACCRUED_FOR_ACTUAL_REFERENCE_TIME,
                decision.referenceTimeAdjustment()
        );
        assertEquals(400_000L, decision.factualAmountMinor());
    }

    @Test
    void monthlyAccruedOutsideReferenceIsExcluded() {
        var decision = only(resolve(false, monthly(1, YearMonth.of(2025, 7), "KPI", true)));

        assertEquals(Eligibility.EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD, decision.eligibility());
        assertNull(decision.referenceTimeAdjustment());
    }

    @Test
    void duplicateMonthlyIndicatorWithinAccrualMonthBlocksInsteadOfChoosingForEmployer() {
        Resolution resolution = resolve(false,
                monthly(1, YearMonth.of(2026, 3), "KPI", true),
                monthly(2, YearMonth.of(2026, 3), "KPI", true));

        assertFalse(resolution.ready());
        assertEquals("PP_540_P15_MONTHLY_DUPLICATE_INDICATOR_MONTH:2026-03:KPI", resolution.blockingReason());
        assertTrue(resolution.decisions().isEmpty());
    }

    @Test
    void differentMonthlyIndicatorsInSameMonthAreIndependent() {
        Resolution resolution = resolve(false,
                monthly(1, YearMonth.of(2026, 3), "KPI_A", true),
                monthly(2, YearMonth.of(2026, 3), "KPI_B", true));

        assertTrue(resolution.ready());
        assertEquals(2, resolution.decisions().size());
    }

    @Test
    void workPeriodNotLongerThanReferenceUsesFactualAccruedAmount() {
        var fact = fact(1, YearMonth.of(2026, 4), PayrollBonusP15Nature.WORK_PERIOD,
                "QUARTER", 900_000L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), true, false);

        var decision = only(resolve(false, fact));

        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_WORK_PERIOD, decision.legalRule());
        assertEquals(AmountTreatment.FACTUAL_ACCRUED_AMOUNT, decision.amountTreatment());
    }

    @Test
    void workPeriodLongerThanReferenceSelectsMonthlyPartPolicyWithoutCalculatingMoney() {
        var fact = fact(1, YearMonth.of(2026, 4), PayrollBonusP15Nature.WORK_PERIOD,
                "LONG", 1_800_000L,
                LocalDate.of(2024, 10, 1), LocalDate.of(2026, 3, 31), false, false);

        var decision = only(resolve(false, fact));

        assertTrue(decision.included());
        assertEquals(AmountTreatment.MONTHLY_PART_FOR_EACH_REFERENCE_MONTH, decision.amountTreatment());
        assertEquals(1_800_000L, decision.factualAmountMinor(),
                "POLICY must preserve source money and leave division to FORMULA");
    }

    @Test
    void workPeriodAccruedOutsideReferenceIsExcluded() {
        var fact = fact(1, YearMonth.of(2026, 8), PayrollBonusP15Nature.WORK_PERIOD,
                "QUARTER", 900_000L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), true, false);

        assertEquals(
                Eligibility.EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD,
                only(resolve(false, fact)).eligibility()
        );
    }

    @Test
    void annualResultForPreviousCalendarYearIsIncludedRegardlessOfAccrualMonth() {
        var fact = fact(1, YearMonth.of(2026, 8), PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT", 2_000_000L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), true, true);

        var decision = only(resolve(false, fact));

        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR, decision.legalRule());
        assertEquals(AmountTreatment.FACTUAL_ACCRUED_AMOUNT, decision.amountTreatment());
        assertEquals(
                ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME,
                decision.referenceTimeAdjustment(),
                "annual award period is not wholly the August-July reference window"
        );
    }

    @Test
    void previousEqualReferenceWindowKeepsAnnualRewardBoundToLegalEventYear() {
        YearMonth previousFrom = YearMonth.of(2024, 8);
        YearMonth previousTo = YearMonth.of(2025, 7);
        var fact = fact(1, YearMonth.of(2026, 8), PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT", 2_000_000L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), true, true);

        var result = AverageEarningsBonusP15Policy.resolve(
                EVENT,
                previousFrom,
                previousTo,
                false,
                List.of(fact)
        );

        var decision = only(result);
        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR, decision.legalRule());
        assertEquals(
                ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME,
                decision.referenceTimeAdjustment()
        );
    }

    @Test
    void serviceLengthForPreviousCalendarYearUsesSamePreviousYearClause() {
        var fact = fact(1, YearMonth.of(2026, 2), PayrollBonusP15Nature.SERVICE_LENGTH,
                "SERVICE", 500_000L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), false, true);

        assertTrue(only(resolve(false, fact)).included());
    }

    @Test
    void annualOrServiceRewardForOtherYearIsExcluded() {
        var annual = fact(1, YearMonth.of(2026, 2), PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT", 2_000_000L,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), true, true);
        var service = fact(2, YearMonth.of(2026, 2), PayrollBonusP15Nature.SERVICE_LENGTH,
                "SERVICE", 500_000L,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), true, true);

        Resolution result = resolve(false, annual, service);

        assertTrue(result.ready());
        assertTrue(result.decisions().stream().noneMatch(Decision::included));
        assertTrue(result.decisions().stream().allMatch(d ->
                d.eligibility() == Eligibility.EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR));
    }

    @Test
    void fullyWorkedReferenceNeedsNoProportionalAdjustmentEvenWhenAccrualFactUnknown() {
        var decision = only(resolve(true,
                monthly(1, YearMonth.of(2026, 3), "KPI", null)));

        assertEquals(
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED,
                decision.referenceTimeAdjustment()
        );
    }

    @Test
    void partialReferenceAndExplicitNonActualAccrualRequiresProportion() {
        var decision = only(resolve(false,
                monthly(1, YearMonth.of(2026, 3), "KPI", false)));

        assertEquals(
                ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME,
                decision.referenceTimeAdjustment()
        );
    }

    @Test
    void partialReferenceAndUnknownActualWorkAccrualBlocksWhenExceptionCouldApply() {
        Resolution result = resolve(false,
                monthly(1, YearMonth.of(2026, 3), "KPI", null));

        assertFalse(result.ready());
        assertEquals("PP_540_P15_ACTUAL_WORK_TIME_ACCRUAL_UNKNOWN:1", result.blockingReason());
    }

    @Test
    void actualWorkAccrualDoesNotSuppressProportionWhenAwardPeriodIsNotWhollyInsideReference() {
        var fact = fact(1, YearMonth.of(2026, 2), PayrollBonusP15Nature.WORK_PERIOD,
                "CROSS_BOUNDARY", 1_000_000L,
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 9, 30), true, true);

        var decision = only(resolve(false, fact));

        assertEquals(
                ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME,
                decision.referenceTimeAdjustment()
        );
    }

    @Test
    void partialAwardProrationFactIsPreservedButNeverGrossedUpByPolicy() {
        var fact = fact(1, YearMonth.of(2026, 4), PayrollBonusP15Nature.WORK_PERIOD,
                "QUARTER", 777_777L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), true, true);

        var decision = only(resolve(false, fact));

        assertEquals(Boolean.TRUE, decision.proratedForPartialAwardPeriod());
        assertEquals(777_777L, decision.factualAmountMinor());
    }

    @Test
    void nonCanonicalReferenceWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AverageEarningsBonusP15Policy.resolve(
                        EVENT,
                        YearMonth.of(2025, 7),
                        TO,
                        true,
                        List.of()
                ));
    }

    @Test
    void legalRegimeOutsidePp540WindowFailsClosed() {
        assertThrows(UnsupportedOperationException.class, () ->
                AverageEarningsBonusP15Policy.resolve(
                        LocalDate.of(2031, 9, 1),
                        YearMonth.of(2030, 9),
                        YearMonth.of(2031, 8),
                        true,
                        List.of()
                ));
    }

    @Test
    void invalidFactShapesFailBeforePolicy() {
        assertThrows(IllegalArgumentException.class, () ->
                fact(1, YearMonth.of(2026, 3), PayrollBonusP15Nature.MONTHLY,
                        "KPI", 0L,
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), true, false));

        assertThrows(IllegalArgumentException.class, () ->
                fact(1, YearMonth.of(2026, 3), PayrollBonusP15Nature.WORK_PERIOD,
                        "KPI", 1L,
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), true, false));
    }

    private static Resolution resolve(boolean fullyWorked, BonusFact... facts) {
        return AverageEarningsBonusP15Policy.resolve(
                EVENT,
                FROM,
                TO,
                fullyWorked,
                List.of(facts)
        );
    }

    private static Decision only(Resolution resolution) {
        assertTrue(resolution.ready(), () -> "blocked: " + resolution.blockingReason());
        assertEquals(1, resolution.decisions().size());
        return resolution.decisions().get(0);
    }

    private static BonusFact monthly(
            long id,
            YearMonth accrualMonth,
            String indicator,
            Boolean actualWork
    ) {
        return fact(
                id,
                accrualMonth,
                PayrollBonusP15Nature.MONTHLY,
                indicator,
                400_000L,
                accrualMonth.atDay(1),
                accrualMonth.atEndOfMonth(),
                actualWork,
                true
        );
    }

    private static BonusFact fact(
            long id,
            YearMonth accrualMonth,
            PayrollBonusP15Nature nature,
            String indicator,
            long amount,
            LocalDate awardFrom,
            LocalDate awardTo,
            Boolean actualWork,
            Boolean partialAwardProrated
    ) {
        return new BonusFact(
                id,
                accrualMonth,
                nature,
                indicator,
                amount,
                awardFrom,
                awardTo,
                actualWork,
                partialAwardProrated
        );
    }
}
