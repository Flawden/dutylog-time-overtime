package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualBonusFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.AccrualOrigin;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.AmountTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Eligibility;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.IncompletePreEventTreatment;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.LegalRule;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15Policy.Resolution;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AverageEarningsParagraph7PreEventBonusP15PolicyTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 8);
    private static final YearMonth THROUGH = YearMonth.of(2026, 10);

    @Test
    void nullAccrualAuthorityIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> AverageEarningsParagraph7PreEventBonusP15Policy.resolve(null)
        );
    }

    @Test
    void blockedB6B1AuthorityPropagatesWithoutPartialPolicyDecisions() {
        Resolution result = AverageEarningsParagraph7PreEventBonusP15Policy.resolve(
                blockedAuthority("B6B1_BLOCKED")
        );
        assertFalse(result.ready());
        assertEquals("B6B1_BLOCKED", result.blockingReason());
        assertTrue(result.decisions().isEmpty());
    }

    @Test
    void emptyReadyAuthorityProducesReadyEmptyPolicy() {
        Resolution result = resolve();
        assertTrue(result.ready());
        assertTrue(result.decisions().isEmpty());
        assertEquals(FROM, result.periodFrom());
        assertEquals(EVENT, result.cutoffExclusive());
    }

    @Test
    void legalRegimeOutsidePp540WindowFailsClosed() {
        LocalDate outside = LocalDate.of(2031, 9, 1);
        var authority = readyAuthorityFor(outside, List.of());
        assertThrows(
                UnsupportedOperationException.class,
                () -> AverageEarningsParagraph7PreEventBonusP15Policy.resolve(authority)
        );
    }

    @Test
    void monthlyAccruedInEventMonthIsIncludedAtFactualAmount() {
        var decision = only(resolve(monthly(1, EVENT_MONTH, "KPI", true)));
        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_MONTHLY, decision.legalRule());
        assertEquals(AmountTreatment.FACTUAL_ACCRUED_AMOUNT, decision.amountTreatment());
        assertEquals(100_001L, decision.sourceFact().factualAmountMinor());
    }

    @Test
    void monthlyAccruedOutsideEventMonthIsExcluded() {
        var decision = only(resolve(monthly(1, YearMonth.of(2026, 9), "KPI", true)));
        assertEquals(
                Eligibility.EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH,
                decision.eligibility()
        );
        assertNull(decision.incompletePreEventTreatment());
    }

    @Test
    void duplicateMonthlyIndicatorInEventMonthBlocksInsteadOfChoosingForEmployer() {
        Resolution result = resolve(
                monthly(1, EVENT_MONTH, "KPI", true),
                monthly(2, EVENT_MONTH, "KPI", true)
        );
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_MONTHLY_DUPLICATE_INDICATOR_MONTH:2026-08:KPI",
                result.blockingReason()
        );
        assertTrue(result.decisions().isEmpty());
    }

    @Test
    void differentMonthlyIndicatorsInEventMonthRemainIndependent() {
        Resolution result = resolve(
                monthly(1, EVENT_MONTH, "KPI_A", true),
                monthly(2, EVENT_MONTH, "KPI_B", true)
        );
        assertTrue(result.ready());
        assertEquals(2, result.decisions().size());
    }

    @Test
    void monthlyDuplicateOutsideEventMonthDoesNotBlockP7Policy() {
        Resolution result = resolve(
                monthly(1, YearMonth.of(2026, 9), "KPI", true),
                monthly(2, YearMonth.of(2026, 9), "KPI", true)
        );
        assertTrue(result.ready());
        assertTrue(result.decisions().stream().noneMatch(d -> d.included()));
    }

    @Test
    void workPeriodAccruedInEventMonthSelectsOneMonthlyPartForP7Basis() {
        var decision = only(resolve(workPeriod(3, EVENT_MONTH, "QUARTER", true)));
        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_WORK_PERIOD, decision.legalRule());
        assertEquals(
                AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH,
                decision.amountTreatment()
        );
    }

    @Test
    void workPeriodAccruedOutsideEventMonthIsExcluded() {
        var decision = only(resolve(workPeriod(
                3,
                YearMonth.of(2026, 7),
                "QUARTER",
                true
        )));
        assertEquals(
                Eligibility.EXCLUDE_NOT_ACCRUED_IN_P7_EVENT_MONTH,
                decision.eligibility()
        );
        assertNull(decision.incompletePreEventTreatment());
    }

    @Test
    void annualPreviousCalendarYearIsIncludedRegardlessOfAccrualMonth() {
        var decision = only(resolve(annual(
                4,
                YearMonth.of(2026, 2),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2025
        )));
        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR, decision.legalRule());
        assertEquals(AmountTreatment.FACTUAL_ACCRUED_AMOUNT, decision.amountTreatment());
    }

    @Test
    void annualPreviousYearRemainsEligibleWhenAccruedAfterEventMonth() {
        var decision = only(resolve(annual(
                4,
                YearMonth.of(2026, 10),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2025
        )));
        assertTrue(decision.included());
        assertEquals(YearMonth.of(2026, 10), decision.sourceFact().accrualMonth());
    }

    @Test
    void annualRewardForOtherCalendarYearIsExcluded() {
        var decision = only(resolve(annual(
                4,
                YearMonth.of(2026, 2),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2024
        )));
        assertEquals(
                Eligibility.EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR,
                decision.eligibility()
        );
        assertNull(decision.incompletePreEventTreatment());
    }

    @Test
    void serviceLengthForPreviousCalendarYearUsesSamePreviousYearClause() {
        var decision = only(resolve(serviceLength(
                5,
                YearMonth.of(2027, 1),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2025
        )));
        assertTrue(decision.included());
        assertEquals(LegalRule.PP_540_P15_PREVIOUS_CALENDAR_YEAR, decision.legalRule());
    }

    @Test
    void serviceLengthForOtherCalendarYearIsExcluded() {
        var decision = only(resolve(serviceLength(
                5,
                EVENT_MONTH,
                AccrualOrigin.PRE_EVENT_SOURCE,
                2024
        )));
        assertEquals(
                Eligibility.EXCLUDE_NOT_PREVIOUS_EVENT_CALENDAR_YEAR,
                decision.eligibility()
        );
    }

    @Test
    void historicalAnnualDiscoveryOriginIsAccepted() {
        Resolution result = resolve(annual(
                6,
                YearMonth.of(2026, 9),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2025
        ));
        assertTrue(result.ready());
        assertTrue(only(result).included());
    }

    @Test
    void historicalServiceDiscoveryOriginIsAccepted() {
        Resolution result = resolve(serviceLength(
                7,
                YearMonth.of(2026, 9),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2025
        ));
        assertTrue(result.ready());
        assertTrue(only(result).included());
    }

    @Test
    void historicalMonthlyOriginContradictionBlocks() {
        AccrualBonusFact fact = monthly(
                8,
                EVENT_MONTH,
                "KPI",
                true,
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31)
        );
        Resolution result = resolve(fact);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_ORIGIN_NATURE_CONTRADICTION:8",
                result.blockingReason()
        );
    }

    @Test
    void historicalWorkPeriodOriginContradictionBlocks() {
        AccrualBonusFact fact = workPeriod(
                9,
                EVENT_MONTH,
                "QUARTER",
                true,
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY
        );
        Resolution result = resolve(fact);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_ORIGIN_NATURE_CONTRADICTION:9",
                result.blockingReason()
        );
    }

    @Test
    void awardInsidePreEventAndActualWorkAccrualNeedsNoAdjustmentWhenBasisIncomplete() {
        AccrualBonusFact fact = monthly(
                10,
                EVENT_MONTH,
                "KPI",
                true,
                AccrualOrigin.PRE_EVENT_SOURCE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10)
        );
        var decision = only(resolve(fact));
        assertEquals(
                IncompletePreEventTreatment.NO_ADJUSTMENT_ALREADY_ACCRUED_FOR_ACTUAL_PRE_EVENT_TIME,
                decision.incompletePreEventTreatment()
        );
    }

    @Test
    void awardInsidePreEventAndExplicitNonActualAccrualRequiresProportionWhenIncomplete() {
        AccrualBonusFact fact = monthly(
                11,
                EVENT_MONTH,
                "KPI",
                false,
                AccrualOrigin.PRE_EVENT_SOURCE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10)
        );
        assertEquals(
                IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME,
                only(resolve(fact)).incompletePreEventTreatment()
        );
    }

    @Test
    void awardInsidePreEventAndUnknownActualAccrualDefersFailClosedDecisionToB6B3() {
        AccrualBonusFact fact = monthly(
                12,
                EVENT_MONTH,
                "KPI",
                null,
                AccrualOrigin.PRE_EVENT_SOURCE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10)
        );
        assertEquals(
                IncompletePreEventTreatment.REQUIRE_EXPLICIT_ACTUAL_WORK_ACCRUAL_FACT,
                only(resolve(fact)).incompletePreEventTreatment()
        );
    }

    @Test
    void awardCrossingEventRequiresProportionEvenWhenActualWorkFlagIsTrue() {
        AccrualBonusFact fact = monthly(
                13,
                EVENT_MONTH,
                "KPI",
                true,
                AccrualOrigin.PRE_EVENT_SOURCE,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
        assertEquals(
                IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME,
                only(resolve(fact)).incompletePreEventTreatment()
        );
    }

    @Test
    void awardStartingBeforeP7BasisRequiresProportionEvenWhenActualWorkFlagIsTrue() {
        AccrualBonusFact fact = serviceLength(
                14,
                EVENT_MONTH,
                AccrualOrigin.PRE_EVENT_SOURCE,
                2025
        );
        assertEquals(
                IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME,
                only(resolve(fact)).incompletePreEventTreatment()
        );
    }

    @Test
    void includedAnnualRewardCarriesProportionalTreatmentForIncompleteP7Basis() {
        var decision = only(resolve(annual(
                15,
                YearMonth.of(2026, 11),
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                2025
        )));
        assertEquals(
                IncompletePreEventTreatment.PROPORTIONAL_TO_PRE_EVENT_WORKED_TIME,
                decision.incompletePreEventTreatment()
        );
    }

    @Test
    void factualIdentityMoneyAndCurrencyArePreservedWithoutPolicyArithmetic() {
        AccrualBonusFact source = monthly(16, EVENT_MONTH, "KPI", true);
        var decision = only(resolve(source));
        assertSame(source, decision.sourceFact());
        assertEquals(100_016L, decision.sourceFact().factualAmountMinor());
        assertEquals("RUB", decision.sourceFact().currencyCode());
        assertEquals(16L, decision.sourceFact().bonusNatureFactId());
    }

    @Test
    void partialAwardProrationFactIsPreservedWithoutGrossUp() {
        AccrualBonusFact source = withProratedFlag(
                workPeriod(17, EVENT_MONTH, "QUARTER", true),
                true
        );
        var decision = only(resolve(source));
        assertEquals(Boolean.TRUE, decision.sourceFact().proratedForPartialAwardPeriod());
        assertEquals(source.factualAmountMinor(), decision.sourceFact().factualAmountMinor());
    }

    @Test
    void excludedDecisionNeverCarriesIncompletePeriodTreatment() {
        var decision = only(resolve(monthly(
                18,
                YearMonth.of(2026, 7),
                "KPI",
                true
        )));
        assertFalse(decision.included());
        assertNull(decision.incompletePreEventTreatment());
    }

    @Test
    void duplicateBlockerClearsOtherwiseValidEarlierDecision() {
        Resolution result = resolve(
                monthly(19, EVENT_MONTH, "KPI", true),
                monthly(20, EVENT_MONTH, "KPI", true),
                annual(21, EVENT_MONTH, AccrualOrigin.PRE_EVENT_SOURCE, 2025)
        );
        assertFalse(result.ready());
        assertTrue(result.decisions().isEmpty());
    }

    @Test
    void workPeriodPolicyPreservesSourceAmountAndLeavesDivisionForLaterFormula() {
        AccrualBonusFact source = workPeriod(22, EVENT_MONTH, "LONG", false);
        var decision = only(resolve(source));
        assertEquals(source.factualAmountMinor(), decision.sourceFact().factualAmountMinor());
        assertEquals(
                AmountTreatment.MONTHLY_PART_FOR_PRE_EVENT_MONTH,
                decision.amountTreatment()
        );
    }

    @Test
    void monthlyAwardSpanningMultipleCalendarMonthsBlocksAsFactContradiction() {
        AccrualBonusFact invalid = fact(
                23,
                AccrualOrigin.PRE_EVENT_SOURCE,
                PayrollBonusP15Nature.MONTHLY,
                EVENT_MONTH,
                "KPI",
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 10),
                true,
                false
        );
        Resolution result = resolve(invalid);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_FACT_SHAPE_CONTRADICTION:23",
                result.blockingReason()
        );
    }

    @Test
    void workPeriodAwardNotExceedingOneMonthBlocksAsFactContradiction() {
        AccrualBonusFact invalid = fact(
                24,
                AccrualOrigin.PRE_EVENT_SOURCE,
                PayrollBonusP15Nature.WORK_PERIOD,
                EVENT_MONTH,
                "QUARTER",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                false
        );
        Resolution result = resolve(invalid);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_FACT_SHAPE_CONTRADICTION:24",
                result.blockingReason()
        );
    }

    @Test
    void annualAwardThatIsNotWholeCalendarYearBlocksAsFactContradiction() {
        AccrualBonusFact invalid = fact(
                25,
                AccrualOrigin.HISTORICAL_ANNUAL_OR_SERVICE_DISCOVERY,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                EVENT_MONTH,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 11, 30),
                true,
                false
        );
        Resolution result = resolve(invalid);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_FACT_SHAPE_CONTRADICTION:25",
                result.blockingReason()
        );
    }

    @Test
    void preEventSourcePeriodCrossingEventBlocksAsAuthorityContradiction() {
        AccrualBonusFact invalid = withSourcePeriod(
                monthly(26, EVENT_MONTH, "KPI", true),
                LocalDate.of(2026, 8, 1),
                EVENT
        );
        Resolution result = resolve(invalid);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_PRE_EVENT_SOURCE_WINDOW_CONTRADICTION:26",
                result.blockingReason()
        );
    }

    @Test
    void preEventSourcePeriodStartingBeforeEventMonthBlocksAsAuthorityContradiction() {
        AccrualBonusFact invalid = withSourcePeriod(
                monthly(27, EVENT_MONTH, "KPI", true),
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 10)
        );
        Resolution result = resolve(invalid);
        assertFalse(result.ready());
        assertEquals(
                "PP_540_P7_P15_PRE_EVENT_SOURCE_WINDOW_CONTRADICTION:27",
                result.blockingReason()
        );
    }

    private static Resolution resolve(AccrualBonusFact... facts) {
        return AverageEarningsParagraph7PreEventBonusP15Policy.resolve(
                readyAuthority(List.of(facts))
        );
    }

    private static AverageEarningsParagraph7PreEventBonusP15Policy.Decision only(
            Resolution resolution
    ) {
        assertTrue(resolution.ready());
        assertEquals(1, resolution.decisions().size());
        return resolution.decisions().get(0);
    }

    private static AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution readyAuthority(
            List<AccrualBonusFact> facts
    ) {
        return readyAuthorityFor(EVENT, facts);
    }

    private static AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution readyAuthorityFor(
            LocalDate eventDate,
            List<AccrualBonusFact> facts
    ) {
        return new AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution(
                eventDate,
                YearMonth.from(eventDate).atDay(1),
                eventDate,
                THROUGH,
                true,
                null,
                null,
                null,
                mock(AverageEarningsParagraph7PreEventBonusP15FactService.Resolution.class),
                facts
        );
    }

    private static AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution blockedAuthority(
            String blocker
    ) {
        return new AverageEarningsParagraph7PreEventBonusAccrualAuthorityService.Resolution(
                EVENT,
                FROM,
                EVENT,
                THROUGH,
                false,
                blocker,
                null,
                "blocked upstream",
                mock(AverageEarningsParagraph7PreEventBonusP15FactService.Resolution.class),
                List.of()
        );
    }

    private static AccrualBonusFact monthly(
            long id,
            YearMonth accrualMonth,
            String indicator,
            Boolean actualWork
    ) {
        return monthly(
                id,
                accrualMonth,
                indicator,
                actualWork,
                AccrualOrigin.PRE_EVENT_SOURCE,
                accrualMonth.atDay(1),
                accrualMonth.atEndOfMonth()
        );
    }

    private static AccrualBonusFact monthly(
            long id,
            YearMonth accrualMonth,
            String indicator,
            Boolean actualWork,
            AccrualOrigin origin,
            LocalDate awardFrom,
            LocalDate awardTo
    ) {
        return fact(
                id,
                origin,
                PayrollBonusP15Nature.MONTHLY,
                accrualMonth,
                indicator,
                awardFrom,
                awardTo,
                actualWork,
                false
        );
    }

    private static AccrualBonusFact workPeriod(
            long id,
            YearMonth accrualMonth,
            String indicator,
            Boolean actualWork
    ) {
        return workPeriod(id, accrualMonth, indicator, actualWork, AccrualOrigin.PRE_EVENT_SOURCE);
    }

    private static AccrualBonusFact workPeriod(
            long id,
            YearMonth accrualMonth,
            String indicator,
            Boolean actualWork,
            AccrualOrigin origin
    ) {
        return fact(
                id,
                origin,
                PayrollBonusP15Nature.WORK_PERIOD,
                accrualMonth,
                indicator,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 7, 31),
                actualWork,
                false
        );
    }

    private static AccrualBonusFact annual(
            long id,
            YearMonth accrualMonth,
            AccrualOrigin origin,
            int awardYear
    ) {
        return fact(
                id,
                origin,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                accrualMonth,
                "YEAR_RESULT",
                LocalDate.of(awardYear, 1, 1),
                LocalDate.of(awardYear, 12, 31),
                true,
                true
        );
    }

    private static AccrualBonusFact serviceLength(
            long id,
            YearMonth accrualMonth,
            AccrualOrigin origin,
            int awardYear
    ) {
        return fact(
                id,
                origin,
                PayrollBonusP15Nature.SERVICE_LENGTH,
                accrualMonth,
                "SERVICE_LENGTH",
                LocalDate.of(awardYear, 1, 1),
                LocalDate.of(awardYear, 12, 31),
                false,
                true
        );
    }

    private static AccrualBonusFact fact(
            long id,
            AccrualOrigin origin,
            PayrollBonusP15Nature nature,
            YearMonth accrualMonth,
            String indicator,
            LocalDate awardFrom,
            LocalDate awardTo,
            Boolean actualWork,
            Boolean prorated
    ) {
        LocalDate sourceFrom = origin == AccrualOrigin.PRE_EVENT_SOURCE
                ? LocalDate.of(2026, 8, 1)
                : awardFrom;
        LocalDate sourceTo = origin == AccrualOrigin.PRE_EVENT_SOURCE
                ? LocalDate.of(2026, 8, 10)
                : awardTo;
        PayrollEarningKind earningKind = nature == PayrollBonusP15Nature.MONTHLY
                ? PayrollEarningKind.MONTHLY_BONUS
                : PayrollEarningKind.ONE_TIME_BONUS;
        return new AccrualBonusFact(
                origin,
                accrualMonth.atDay(1),
                1,
                id,
                1_000L + id,
                2_000L + id,
                3_000L + id,
                earningKind,
                nature,
                accrualMonth,
                sourceFrom,
                sourceTo,
                100_000L + id,
                "RUB",
                indicator,
                awardFrom,
                awardTo,
                actualWork,
                prorated
        );
    }

    private static AccrualBonusFact withProratedFlag(
            AccrualBonusFact source,
            Boolean prorated
    ) {
        return copy(source, source.sourcePeriodFrom(), source.sourcePeriodTo(), prorated);
    }

    private static AccrualBonusFact withSourcePeriod(
            AccrualBonusFact source,
            LocalDate from,
            LocalDate to
    ) {
        return copy(source, from, to, source.proratedForPartialAwardPeriod());
    }

    private static AccrualBonusFact copy(
            AccrualBonusFact source,
            LocalDate sourceFrom,
            LocalDate sourceTo,
            Boolean prorated
    ) {
        return new AccrualBonusFact(
                source.origin(),
                source.snapshotPeriodMonth(),
                source.snapshotRevision(),
                source.bonusNatureFactId(),
                source.bonusSourceFactId(),
                source.bonusAverageFactId(),
                source.componentId(),
                source.earningKind(),
                source.p15Nature(),
                source.accrualMonth(),
                sourceFrom,
                sourceTo,
                source.factualAmountMinor(),
                source.currencyCode(),
                source.indicatorKey(),
                source.awardPeriodFrom(),
                source.awardPeriodTo(),
                source.accruedForActualWorkTime(),
                prorated
        );
    }
}
