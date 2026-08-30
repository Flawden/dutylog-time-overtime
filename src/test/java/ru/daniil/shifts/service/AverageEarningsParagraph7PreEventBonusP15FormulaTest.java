package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualBonusFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualOrigin;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Formula.AppliedPreEventAdjustment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.AmountTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Decision;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Eligibility;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.IncompletePreEventTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.LegalRule;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusWorkTimeFactService.WorkMeasureUnit;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AverageEarningsParagraph7PreEventBonusP15FormulaTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    @Test
    void nullWorkTimeAuthorityIsRejected() {
        assertThrows(NullPointerException.class, () ->
                AverageEarningsParagraph7PreEventBonusP15Formula.calculate(null));
    }

    @Test
    void blockedWorkTimeAuthorityPropagatesFailClosedWithoutMoney() {
        var policy = policy(List.of(proportional(monthlyFact(1, 100L, "RUB"))));
        var blocked = blockedWorkTime(policy, "UPSTREAM_BLOCK");

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(blocked);

        assertFalse(calculation.ready());
        assertEquals("UPSTREAM_BLOCK", calculation.blockingReason());
        assertTrue(calculation.lines().isEmpty());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
        assertNull(calculation.currencyCode());
    }

    @Test
    void mismatchedPolicyProvenanceBlocksInsteadOfPricingAnotherWindow() {
        var decision = proportional(monthlyFact(1, 100L, "RUB"));
        var wrongPolicy = new AverageEarningsParagraph7PreEventBonusP15Policy.Resolution(
                EVENT.plusDays(1),
                FROM,
                EVENT.plusDays(1),
                true,
                null,
                mock(AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution.class),
                List.of(decision)
        );
        var authority = new AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution(
                EVENT,
                FROM,
                EVENT,
                true,
                null,
                null,
                true,
                WorkMeasureUnit.WORKING_MINUTES,
                480,
                960,
                false,
                wrongPolicy,
                List.of()
        );

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(authority);

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula.AUTHORITY_POLICY_CONTRADICTION,
                calculation.blockingReason()
        );
    }

    @Test
    void excludedDecisionProducesAuditLineWithoutIncludedMoneyOrCurrency() {
        var policy = policy(List.of(excluded(monthlyFact(1, 100L, "USD"))));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertTrue(calculation.ready());
        assertEquals(1, calculation.lines().size());
        assertEquals(0L, calculation.lines().get(0).includedAmountMinor());
        assertNull(calculation.lines().get(0).appliedAdjustment());
        assertNull(calculation.currencyCode());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void factualAmountAlreadyAccruedForActualPreEventTimeNeedsNoRatioAuthority() {
        var decision = noAdjustment(monthlyFact(1, 123L, "RUB"));
        var policy = policy(List.of(decision));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertTrue(calculation.ready());
        assertEquals(123L, calculation.includedPremiumAmountMinor());
        assertEquals("RUB", calculation.currencyCode());
        assertEquals(
                AppliedPreEventAdjustment.NONE_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME,
                calculation.lines().get(0).appliedAdjustment()
        );
        assertNull(calculation.lines().get(0).appliedWorkedTime());
    }

    @Test
    void fullyWorkedScheduleSuppressesProportionalReduction() {
        var policy = policy(List.of(proportional(monthlyFact(1, 999L, "RUB"))));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_MINUTES, 960, 960)
        );

        assertTrue(calculation.ready());
        assertEquals(999L, calculation.includedPremiumAmountMinor());
        assertEquals(
                AppliedPreEventAdjustment.NONE_PRE_EVENT_SCHEDULE_FULLY_WORKED,
                calculation.lines().get(0).appliedAdjustment()
        );
        assertNull(calculation.lines().get(0).appliedWorkedTime());
    }

    @Test
    void fullyWorkedScheduleDoesNotNeedUnknownActualAccrualFact() {
        var policy = policy(List.of(requireExplicit(monthlyFact(1, 777L, "RUB"))));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_DAYS, 4, 4)
        );

        assertTrue(calculation.ready());
        assertEquals(777L, calculation.includedPremiumAmountMinor());
        assertEquals(
                AppliedPreEventAdjustment.NONE_PRE_EVENT_SCHEDULE_FULLY_WORKED,
                calculation.lines().get(0).appliedAdjustment()
        );
    }

    @Test
    void summarizedIncompleteBasisUsesExactWorkedOverScheduledMinutes() {
        var policy = policy(List.of(proportional(monthlyFact(1, 1_000L, "RUB"))));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_MINUTES, 720, 960)
        );

        assertTrue(calculation.ready());
        assertEquals(750L, calculation.includedPremiumAmountMinor());
        var applied = calculation.lines().get(0).appliedWorkedTime();
        assertEquals(WorkMeasureUnit.WORKING_MINUTES, applied.unit());
        assertEquals(720L, applied.workedUnits());
        assertEquals(960L, applied.normUnits());
    }

    @Test
    void dailyIncompleteBasisUsesExactWorkedOverScheduledDays() {
        var policy = policy(List.of(proportional(monthlyFact(1, 1_000L, "RUB"))));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_DAYS, 2, 4)
        );

        assertTrue(calculation.ready());
        assertEquals(500L, calculation.includedPremiumAmountMinor());
        assertEquals(WorkMeasureUnit.WORKING_DAYS,
                calculation.lines().get(0).appliedWorkedTime().unit());
    }

    @Test
    void noAdjustmentLineStaysFactualBesideProportionalLine() {
        var policy = policy(List.of(
                noAdjustment(monthlyFact(1, 100L, "RUB")),
                proportional(monthlyFact(2, 100L, "RUB"))
        ));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_MINUTES, 1, 2)
        );

        assertTrue(calculation.ready());
        assertEquals(List.of(100L, 50L), calculation.lines().stream()
                .map(AverageEarningsParagraph7PreEventBonusP15Formula.Line::includedAmountMinor)
                .toList());
        assertEquals(150L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void unknownActualAccrualFactBlocksOnlyWhenBasisIsIncomplete() {
        var fact = monthlyFact(7, 100L, "RUB");
        var policy = policy(List.of(requireExplicit(fact)));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_MINUTES, 480, 960)
        );

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula
                        .ACTUAL_WORK_TIME_ACCRUAL_FACT_REQUIRED + ":7",
                calculation.blockingReason()
        );
        assertTrue(calculation.lines().isEmpty());
    }

    @Test
    void workPeriodContributesExactlyOneMonthlyPart() {
        var fact = workPeriodFact(
                1,
                300L,
                "RUB",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 7, 31)
        );
        var policy = policy(List.of(noAdjustmentMonthlyPart(fact)));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertTrue(calculation.ready());
        assertEquals(100L, calculation.includedPremiumAmountMinor());
        assertEquals(3, calculation.lines().get(0).awardMonthCount().intValue());
    }

    @Test
    void monthlyPartAndWorkedTimeRatioRoundOnlyOnceAtFinalMinorUnit() {
        var fact = workPeriodFact(
                1,
                5L,
                "RUB",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 31)
        );
        var policy = policy(List.of(proportionalMonthlyPart(fact)));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withWorkTime(policy, WorkMeasureUnit.WORKING_MINUTES, 1, 2)
        );

        // 5 / 2 months * 1 / 2 worked = 5/4 = 1.25 -> 1.
        // Rounding the monthly part first would incorrectly produce 3/2 -> 2.
        assertTrue(calculation.ready());
        assertEquals(1L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void workPeriodWithPartialCalendarMonthsBlocksInsteadOfInventingMonthCount() {
        var fact = workPeriodFact(
                1,
                300L,
                "RUB",
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 7, 14)
        );
        var policy = policy(List.of(noAdjustmentMonthlyPart(fact)));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula
                        .MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED + ":1",
                calculation.blockingReason()
        );
    }

    @Test
    void monthlyPartOnNonWorkPeriodDecisionBlocksPolicyContradiction() {
        var fact = monthlyFact(1, 100L, "RUB");
        var contradictory = new Decision(
                fact,
                LegalRule.PP_540_P15_MONTHLY,
                Eligibility.INCLUDE,
                AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH,
                IncompletePreEventTreatment.NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME
        );
        var policy = policy(List.of(contradictory));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula.POLICY_CONTRADICTION + ":1",
                calculation.blockingReason()
        );
    }

    @Test
    void includedCurrenciesMustMatchBeforeMoneyCanBeSummed() {
        var policy = policy(List.of(
                noAdjustment(monthlyFact(1, 100L, "RUB")),
                noAdjustment(monthlyFact(2, 200L, "USD"))
        ));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula.INCLUDED_CURRENCY_MISMATCH + ":2",
                calculation.blockingReason()
        );
        assertTrue(calculation.lines().isEmpty());
    }

    @Test
    void excludedDifferentCurrencyDoesNotPoisonIncludedCurrency() {
        var policy = policy(List.of(
                noAdjustment(monthlyFact(1, 100L, "RUB")),
                excluded(monthlyFact(2, 200L, "USD"))
        ));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertTrue(calculation.ready());
        assertEquals("RUB", calculation.currencyCode());
        assertEquals(100L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void includedTotalOverflowBlocksInsteadOfWrapping() {
        var policy = policy(List.of(
                noAdjustment(monthlyFact(1, Long.MAX_VALUE, "RUB")),
                noAdjustment(monthlyFact(2, 1L, "RUB"))
        ));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula.INCLUDED_TOTAL_OVERFLOW,
                calculation.blockingReason()
        );
        assertTrue(calculation.lines().isEmpty());
    }

    @Test
    void emptyReadyPolicyProducesReadyZeroWithoutCurrency() {
        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy(List.of()))
        );

        assertTrue(calculation.ready());
        assertTrue(calculation.lines().isEmpty());
        assertNull(calculation.currencyCode());
        assertEquals(0L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void policyOrderIsPreservedInFormulaLines() {
        var policy = policy(List.of(
                noAdjustment(monthlyFact(11, 100L, "RUB")),
                excluded(monthlyFact(12, 999L, "USD")),
                noAdjustment(monthlyFact(13, 50L, "RUB"))
        ));

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(
                withoutWorkTime(policy)
        );

        assertEquals(List.of(11L, 12L, 13L), calculation.lines().stream()
                .map(AverageEarningsParagraph7PreEventBonusP15Formula.Line::bonusNatureFactId)
                .toList());
        assertEquals(150L, calculation.includedPremiumAmountMinor());
    }

    @Test
    void workTimeRequirementMustStillMatchB6B2Policy() {
        var policy = policy(List.of(proportional(monthlyFact(1, 100L, "RUB"))));
        var contradictory = new AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution(
                EVENT,
                FROM,
                EVENT,
                true,
                null,
                null,
                false,
                null,
                0,
                0,
                false,
                policy,
                List.of()
        );

        var calculation = AverageEarningsParagraph7PreEventBonusP15Formula.calculate(contradictory);

        assertFalse(calculation.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15Formula.AUTHORITY_POLICY_CONTRADICTION,
                calculation.blockingReason()
        );
    }

    @Test
    void blockedCalculationRecordCannotExposePartialMoney() {
        var policy = policy(List.of(proportional(monthlyFact(1, 100L, "RUB"))));
        var authority = blockedWorkTime(policy, "UPSTREAM");

        assertThrows(IllegalArgumentException.class, () ->
                new AverageEarningsParagraph7PreEventBonusP15Formula.Calculation(
                        EVENT,
                        FROM,
                        EVENT,
                        false,
                        "BLOCK",
                        authority,
                        "RUB",
                        List.of(),
                        1L
                ));
    }

    private static AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy(
            List<Decision> decisions
    ) {
        return new AverageEarningsParagraph7PreEventBonusP15Policy.Resolution(
                EVENT,
                FROM,
                EVENT,
                true,
                null,
                mock(AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution.class),
                decisions
        );
    }

    private static AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution withoutWorkTime(
            AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy
    ) {
        return new AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution(
                EVENT,
                FROM,
                EVENT,
                true,
                null,
                null,
                false,
                null,
                0,
                0,
                false,
                policy,
                List.of()
        );
    }

    private static AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution withWorkTime(
            AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy,
            WorkMeasureUnit unit,
            long worked,
            long norm
    ) {
        return new AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution(
                EVENT,
                FROM,
                EVENT,
                true,
                null,
                null,
                true,
                unit,
                worked,
                norm,
                worked == norm,
                policy,
                List.of()
        );
    }

    private static AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution blockedWorkTime(
            AverageEarningsParagraph7PreEventBonusP15Policy.Resolution policy,
            String reason
    ) {
        return new AverageEarningsParagraph7PreEventBonusWorkTimeFactService.Resolution(
                EVENT,
                FROM,
                EVENT,
                false,
                reason,
                null,
                false,
                null,
                0,
                0,
                false,
                policy,
                List.of()
        );
    }

    private static Decision noAdjustment(AccrualBonusFact fact) {
        return new Decision(
                fact,
                legalRule(fact.p15Nature()),
                Eligibility.INCLUDE,
                AmountTreatment.FACTUAL_ACCRUED_AMOUNT,
                IncompletePreEventTreatment.NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME
        );
    }

    private static Decision proportional(AccrualBonusFact fact) {
        return new Decision(
                fact,
                legalRule(fact.p15Nature()),
                Eligibility.INCLUDE,
                AmountTreatment.FACTUAL_ACCRUED_AMOUNT,
                IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME
        );
    }

    private static Decision requireExplicit(AccrualBonusFact fact) {
        return new Decision(
                fact,
                legalRule(fact.p15Nature()),
                Eligibility.INCLUDE,
                AmountTreatment.FACTUAL_ACCRUED_AMOUNT,
                IncompletePreEventTreatment.REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT
        );
    }

    private static Decision noAdjustmentMonthlyPart(AccrualBonusFact fact) {
        return new Decision(
                fact,
                LegalRule.PP_540_P15_WORK_PERIOD,
                Eligibility.INCLUDE,
                AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH,
                IncompletePreEventTreatment.NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME
        );
    }

    private static Decision proportionalMonthlyPart(AccrualBonusFact fact) {
        return new Decision(
                fact,
                LegalRule.PP_540_P15_WORK_PERIOD,
                Eligibility.INCLUDE,
                AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH,
                IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME
        );
    }

    private static Decision excluded(AccrualBonusFact fact) {
        return new Decision(
                fact,
                legalRule(fact.p15Nature()),
                Eligibility.EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH,
                AmountTreatment.FACTUAL_ACCRUED_AMOUNT,
                null
        );
    }

    private static LegalRule legalRule(PayrollBonusP15Nature nature) {
        return switch (nature) {
            case MONTHLY -> LegalRule.PP_540_P15_MONTHLY;
            case WORK_PERIOD -> LegalRule.PP_540_P15_WORK_PERIOD;
            case ANNUAL_RESULT, SERVICE_LENGTH -> LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR;
        };
    }

    private static AccrualBonusFact monthlyFact(long id, long amount, String currency) {
        return fact(
                id,
                PayrollBonusP15Nature.MONTHLY,
                amount,
                currency,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
    }

    private static AccrualBonusFact workPeriodFact(
            long id,
            long amount,
            String currency,
            LocalDate awardFrom,
            LocalDate awardTo
    ) {
        return fact(
                id,
                PayrollBonusP15Nature.WORK_PERIOD,
                amount,
                currency,
                awardFrom,
                awardTo
        );
    }

    private static AccrualBonusFact fact(
            long id,
            PayrollBonusP15Nature nature,
            long amount,
            String currency,
            LocalDate awardFrom,
            LocalDate awardTo
    ) {
        return new AccrualBonusFact(
                AccrualOrigin.PRE_EVENT_SOURCE,
                LocalDate.of(2026, 8, 1),
                1,
                id,
                1000L + id,
                2000L + id,
                3000L + id,
                PayrollEarningKind.MONTHLY_BONUS,
                nature,
                YearMonth.of(2026, 8),
                FROM,
                EVENT.minusDays(1),
                amount,
                currency,
                "INDICATOR_" + id,
                awardFrom,
                awardTo,
                Boolean.TRUE,
                Boolean.FALSE
        );
    }
}
