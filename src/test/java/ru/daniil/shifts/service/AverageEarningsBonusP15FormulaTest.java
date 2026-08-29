package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollBonusP15Nature;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.*;

class AverageEarningsBonusP15FormulaTest {

    private static final YearMonth FROM = YearMonth.of(2025, 8);
    private static final YearMonth TO = YearMonth.of(2026, 7);

    @Test
    void blockedPolicyPassesThroughWithoutPartialMoney() {
        Calculation calculation = calculate(
                Resolution.blocked("PP_540_P15_POLICY_BLOCKER"),
                null
        );

        assertFalse(calculation.ready());
        assertEquals("PP_540_P15_POLICY_BLOCKER", calculation.blockingReason());
        assertTrue(calculation.lines().isEmpty());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void factualIncludedAmountWithoutProrationIsExact() {
        Calculation calculation = calculate(
                Resolution.ready(List.of(factualIncluded(
                        1,
                        400_001L,
                        ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
                ))),
                null
        );

        assertTrue(calculation.ready());
        assertEquals(400_001L, calculation.includedPremiumAmountMinor());
        Line line = calculation.lines().get(0);
        assertEquals(400_001L, line.includedAmountMinor());
        assertNull(line.referenceWorkedTime());
        assertNull(line.awardMonthCount());
    }

    @Test
    void excludedDecisionProducesZeroAndNeedsNoWorkedTimeFact() {
        Calculation calculation = calculate(
                Resolution.ready(List.of(excluded(1, 777_777L))),
                null
        );

        assertTrue(calculation.ready());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
        assertEquals(0L, calculation.lines().get(0).includedAmountMinor());
    }

    @Test
    void factualProrationUsesExplicitReferenceWorkedTimeAndHalfUp() {
        ReferenceWorkedTimeFact ratio = new ReferenceWorkedTimeFact(
                WorkMeasureUnit.WORKING_DAYS,
                2,
                3
        );

        Calculation calculation = calculate(
                Resolution.ready(List.of(factualIncluded(
                        1,
                        100L,
                        ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME
                ))),
                ratio
        );

        assertEquals(67L, calculation.includedPremiumAmountMinor());
        assertEquals(ratio, calculation.lines().get(0).referenceWorkedTime());
    }

    @Test
    void proportionalDecisionBlocksWhenReferenceWorkedTimeFactIsMissing() {
        Calculation calculation = calculate(
                Resolution.ready(List.of(factualIncluded(
                        1,
                        100L,
                        ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME
                ))),
                null
        );

        assertFalse(calculation.ready());
        assertEquals(
                "PP_540_P15_REFERENCE_WORKED_TIME_FACT_MISSING",
                calculation.blockingReason()
        );
    }

    @Test
    void referenceWorkedTimeFactRejectsImpossibleRatios() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceWorkedTimeFact(WorkMeasureUnit.WORKING_DAYS, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceWorkedTimeFact(WorkMeasureUnit.WORKING_DAYS, -1, 10));
        assertThrows(IllegalArgumentException.class, () ->
                new ReferenceWorkedTimeFact(WorkMeasureUnit.WORKING_DAYS, 11, 10));
    }

    @Test
    void workingMinutesArePreservedAsExplicitRatioUnit() {
        ReferenceWorkedTimeFact ratio = new ReferenceWorkedTimeFact(
                WorkMeasureUnit.WORKING_MINUTES,
                7_500,
                10_000
        );

        Calculation calculation = calculate(
                Resolution.ready(List.of(factualIncluded(
                        1,
                        1_000L,
                        ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME
                ))),
                ratio
        );

        assertEquals(750L, calculation.includedPremiumAmountMinor());
        assertEquals(WorkMeasureUnit.WORKING_MINUTES,
                calculation.lines().get(0).referenceWorkedTime().unit());
    }

    @Test
    void longPeriodMonthlyPartUsesTwelveReferenceMonths() {
        Decision decision = monthlyPartIncluded(
                1,
                3_000_000L,
                LocalDate.of(2024, 7, 1),
                LocalDate.of(2025, 12, 31),
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );

        Calculation calculation = calculate(Resolution.ready(List.of(decision)), null);

        Line line = calculation.lines().get(0);
        assertEquals(18, line.awardMonthCount().intValue());
        assertEquals(12, line.referenceMonthCount().intValue());
        assertEquals(2_000_000L, line.includedAmountMinor());
    }

    @Test
    void longPeriodFormulaDoesNotInjectAwardReferenceOverlapPolicy() {
        Decision decision = monthlyPartIncluded(
                1,
                1_800_000L,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2024, 6, 30),
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );

        Calculation calculation = calculate(Resolution.ready(List.of(decision)), null);

        assertTrue(calculation.ready());
        assertEquals(18, calculation.lines().get(0).awardMonthCount().intValue());
        assertEquals(12, calculation.lines().get(0).referenceMonthCount().intValue());
        assertEquals(1_200_000L, calculation.includedPremiumAmountMinor(),
                "FORMULA executes F3C's monthly-part decision; award/reference eligibility belongs to POLICY");
    }

    @Test
    void monthlyPartBlocksWhenAwardPeriodDoesNotResolveToWholeCalendarMonths() {
        Decision decision = monthlyPartIncluded(
                1,
                1_800_000L,
                LocalDate.of(2024, 10, 15),
                LocalDate.of(2026, 3, 14),
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );

        Calculation calculation = calculate(Resolution.ready(List.of(decision)), null);

        assertFalse(calculation.ready());
        assertEquals(
                "PP_540_P15_MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED:1",
                calculation.blockingReason()
        );
    }

    @Test
    void monthlyPartBlocksContradictoryNonWorkPeriodPolicyDecision() {
        Decision contradictory = new Decision(
                1,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT",
                YearMonth.of(2026, 2),
                1_300_000L,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR,
                Eligibility.INCLUDE,
                AmountTreatment.MONTHLY_PART_FOR_EACH_REFERENCE_MONTH,
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );

        Calculation calculation = calculate(Resolution.ready(List.of(contradictory)), null);

        assertFalse(calculation.ready());
        assertEquals("PP_540_P15_FORMULA_POLICY_CONTRADICTION:1", calculation.blockingReason());
    }

    @Test
    void monthlyPartAndReferenceProrationRoundOnlyOnceAtFinalMinorUnit() {
        Decision decision = monthlyPartIncluded(
                1,
                5L,
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2026, 7, 31),
                ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME
        );
        ReferenceWorkedTimeFact ratio = new ReferenceWorkedTimeFact(
                WorkMeasureUnit.WORKING_DAYS,
                1,
                2
        );

        Calculation calculation = calculate(Resolution.ready(List.of(decision)), ratio);

        // 5 * 12 / 13 * 1 / 2 = 60 / 26 = 2.307... -> 2.
        // Rounding 5*12/13 first would incorrectly produce 5, then 5/2 -> 3.
        assertEquals(2L, calculation.includedPremiumAmountMinor());
        assertEquals(13, calculation.lines().get(0).awardMonthCount().intValue());
        assertEquals(12, calculation.lines().get(0).referenceMonthCount().intValue());
    }

    @Test
    void zeroWorkedReferenceTimeProducesZeroWithoutSpecialCaseGuessing() {
        ReferenceWorkedTimeFact ratio = new ReferenceWorkedTimeFact(
                WorkMeasureUnit.WORKING_DAYS,
                0,
                247
        );

        Calculation calculation = calculate(
                Resolution.ready(List.of(factualIncluded(
                        1,
                        999_999L,
                        ReferenceTimeAdjustment.PROPORTIONAL_TO_REFERENCE_WORKED_TIME
                ))),
                ratio
        );

        assertTrue(calculation.ready());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void multipleLinesPreservePolicyOrderAndSumIncludedMoney() {
        Decision first = factualIncluded(
                11,
                100L,
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );
        Decision excluded = excluded(12, 500L);
        Decision third = factualIncluded(
                13,
                50L,
                ReferenceTimeAdjustment.NONE_ALREADY_ACCRUED_FOR_ACTUAL_REFERENCE_TIME
        );

        Calculation calculation = calculate(
                Resolution.ready(List.of(first, excluded, third)),
                null
        );

        assertEquals(150L, calculation.includedPremiumAmountMinor());
        assertEquals(List.of(11L, 12L, 13L),
                calculation.lines().stream().map(Line::bonusNatureFactId).toList());
        assertEquals(List.of(100L, 0L, 50L),
                calculation.lines().stream().map(Line::includedAmountMinor).toList());
    }

    @Test
    void includedTotalOverflowBlocksInsteadOfWrappingMoney() {
        Decision first = factualIncluded(
                1,
                Long.MAX_VALUE,
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );
        Decision second = factualIncluded(
                2,
                1L,
                ReferenceTimeAdjustment.NONE_REFERENCE_PERIOD_FULLY_WORKED
        );

        Calculation calculation = calculate(
                Resolution.ready(List.of(first, second)),
                null
        );

        assertFalse(calculation.ready());
        assertEquals("PP_540_P15_INCLUDED_TOTAL_OVERFLOW", calculation.blockingReason());
        assertTrue(calculation.lines().isEmpty());
    }

    @Test
    void emptyReadyPolicyProducesReadyZeroTotal() {
        Calculation calculation = calculate(Resolution.ready(List.of()), null);

        assertTrue(calculation.ready());
        assertTrue(calculation.lines().isEmpty());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void nonTwelveMonthReferenceWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AverageEarningsBonusP15Formula.calculate(
                        FROM,
                        YearMonth.of(2026, 6),
                        Resolution.ready(List.of()),
                        null
                ));
    }

    @Test
    void ratioFactIsNotInventedOrRequiredWhenPolicyNeedsNoProration() {
        Calculation calculation = calculate(
                Resolution.ready(List.of(factualIncluded(
                        1,
                        123L,
                        ReferenceTimeAdjustment.NONE_ALREADY_ACCRUED_FOR_ACTUAL_REFERENCE_TIME
                ))),
                null
        );

        assertTrue(calculation.ready());
        assertNull(calculation.lines().get(0).referenceWorkedTime());
        assertEquals(123L, calculation.includedPremiumAmountMinor());
    }

    private static Calculation calculate(
            Resolution resolution,
            ReferenceWorkedTimeFact referenceWorkedTime
    ) {
        return AverageEarningsBonusP15Formula.calculate(
                FROM,
                TO,
                resolution,
                referenceWorkedTime
        );
    }

    private static Decision factualIncluded(
            long id,
            long amount,
            ReferenceTimeAdjustment adjustment
    ) {
        return new Decision(
                id,
                PayrollBonusP15Nature.MONTHLY,
                "KPI_" + id,
                YearMonth.of(2026, 3),
                amount,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                true,
                LegalRule.PP_540_P15_MONTHLY,
                Eligibility.INCLUDE,
                AmountTreatment.FACTUAL_ACCRUED_AMOUNT,
                adjustment
        );
    }

    private static Decision excluded(long id, long amount) {
        return new Decision(
                id,
                PayrollBonusP15Nature.MONTHLY,
                "OLD_" + id,
                YearMonth.of(2025, 7),
                amount,
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 31),
                true,
                LegalRule.PP_540_P15_MONTHLY,
                Eligibility.EXCLUDE_NOT_ACCRUED_IN_REFERENCE_PERIOD,
                AmountTreatment.FACTUAL_ACCRUED_AMOUNT,
                null
        );
    }

    private static Decision monthlyPartIncluded(
            long id,
            long amount,
            LocalDate awardFrom,
            LocalDate awardTo,
            ReferenceTimeAdjustment adjustment
    ) {
        return new Decision(
                id,
                PayrollBonusP15Nature.WORK_PERIOD,
                "LONG_" + id,
                YearMonth.of(2026, 4),
                amount,
                awardFrom,
                awardTo,
                false,
                LegalRule.PP_540_P15_WORK_PERIOD,
                Eligibility.INCLUDE,
                AmountTreatment.MONTHLY_PART_FOR_EACH_REFERENCE_MONTH,
                adjustment
        );
    }
}
