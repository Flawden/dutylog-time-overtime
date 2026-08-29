package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.WorkTimeAccountingMode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.WorkMeasureUnit.WORKING_MINUTES;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Policy.ReferenceTimeAdjustment.*;

class AverageEarningsBonusP15CalculationPipelineServiceTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 8, 15);
    private static final YearMonth EVENT_MONTH = YearMonth.of(2026, 8);
    private static final YearMonth FROM = YearMonth.of(2025, 8);
    private static final YearMonth TO = YearMonth.of(2026, 7);
    private static final YearMonth THROUGH = YearMonth.of(2026, 8);

    private final AppUser user = mock(AppUser.class);
    private final AverageEarningsBonusP15HistoricalFactDiscoveryService discovery =
            mock(AverageEarningsBonusP15HistoricalFactDiscoveryService.class);
    private final AverageEarningsBonusP15ReferenceCompletenessService completeness =
            mock(AverageEarningsBonusP15ReferenceCompletenessService.class);
    private final AverageEarningsBonusP15CalculationPipelineService service =
            new AverageEarningsBonusP15CalculationPipelineService(discovery, completeness);

    @Test
    void fullyWorkedMonthlyPremiumFlowsFactPolicyFormulaMoneyWithoutProration() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), false));
        stubCompleteness(1_000, 1_000, true, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(400_000L, result.calculation().includedPremiumAmountMinor());
        assertEquals(NONE_REFERENCE_PERIOD_FULLY_WORKED,
                result.policy().decisions().get(0).referenceTimeAdjustment());
    }

    @Test
    void paragraph5OrSchedulePartialReferenceUsesExactF3F3Ratio() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), false));
        stubCompleteness(900, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(PROPORTIONAL_TO_REFERENCE_WORKED_TIME,
                result.policy().decisions().get(0).referenceTimeAdjustment());
        assertEquals(360_000L, result.calculation().includedPremiumAmountMinor());
        assertEquals(900L, result.calculation().lines().get(0).referenceWorkedTime().workedUnits());
        assertEquals(1_000L, result.calculation().lines().get(0).referenceWorkedTime().normUnits());
    }

    @Test
    void premiumAlreadyAccruedForActualReferenceTimeUsesExceptionEvenWhenReferencePartial() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), true));
        stubCompleteness(900, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(NONE_ALREADY_ACCRUED_FOR_ACTUAL_REFERENCE_TIME,
                result.policy().decisions().get(0).referenceTimeAdjustment());
        assertEquals(400_000L, result.calculation().includedPremiumAmountMinor());
    }

    @Test
    void annualPreviousYearAwardStillUsesReferenceRatioWhenAwardPeriodOutsideReference() {
        stubDiscovery("RUB", annual(1, YearMonth.of(2026, 8), true));
        stubCompleteness(900, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(PROPORTIONAL_TO_REFERENCE_WORKED_TIME,
                result.policy().decisions().get(0).referenceTimeAdjustment());
        assertEquals(1_800_000L, result.calculation().includedPremiumAmountMinor());
    }

    @Test
    void previousEqualReferenceWindowKeepsLegalEventDateThroughDiscoveryCompletenessPolicyAndFormula() {
        AverageEarningsReferenceWindow window = new AverageEarningsReferenceWindow(
                EVENT_MONTH,
                YearMonth.of(2024, 8),
                YearMonth.of(2025, 7)
        );
        var annual = annual(1, YearMonth.of(2026, 8), true);
        when(discovery.resolve(user, EVENT, window, THROUGH, List.of())).thenReturn(
                AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution.ready(
                        EVENT, EVENT_MONTH, window.referenceFrom(), window.referenceTo(),
                        THROUGH, "RUB", List.of(annual)
                )
        );
        when(completeness.resolve(user, EVENT, window, List.of())).thenReturn(
                AverageEarningsBonusP15ReferenceCompletenessService.Resolution.ready(
                        EVENT, EVENT_MONTH, window.referenceFrom(), window.referenceTo(),
                        WorkTimeAccountingMode.SUMMARIZED,
                        new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(
                                WORKING_MINUTES, 900, 1_000
                        ),
                        false, false, List.of(), true, false, List.of()
                )
        );

        var result = service.calculate(user, EVENT, window, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(EVENT, result.eventDate());
        assertEquals(window.referenceFrom(), result.referenceFrom());
        assertEquals(window.referenceTo(), result.referenceTo());
        assertTrue(result.policy().decisions().get(0).included());
        assertEquals(1_800_000L, result.calculation().includedPremiumAmountMinor());
        verify(discovery).resolve(user, EVENT, window, THROUGH, List.of());
        verify(completeness).resolve(user, EVENT, window, List.of());
    }

    @Test
    void historicalDiscoveryBlockerStopsBeforeCompleteness() {
        when(discovery.resolve(user, EVENT, THROUGH, List.of())).thenReturn(
                AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution.blocked(
                        EVENT, EVENT_MONTH, FROM, TO, THROUGH,
                        "DISCOVERY_BLOCK", YearMonth.of(2026, 4)
                )
        );

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.BlockingStage.HISTORICAL_FACT_DISCOVERY,
                result.blockingStage());
        assertEquals("DISCOVERY_BLOCK", result.blockingReason());
        verifyNoInteractions(completeness);
    }

    @Test
    void referenceCompletenessBlockerStopsBeforePolicyAndMoney() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), false));
        when(completeness.resolve(user, EVENT, List.of())).thenReturn(
                AverageEarningsBonusP15ReferenceCompletenessService.Resolution.blocked(
                        EVENT, EVENT_MONTH, FROM, TO,
                        "P5_BLOCK", YearMonth.of(2026, 2), 99L
                )
        );

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.BlockingStage.REFERENCE_COMPLETENESS,
                result.blockingStage());
        assertEquals("P5_BLOCK", result.blockingReason());
        assertNull(result.calculation());
    }

    @Test
    void p15PolicyBlockerEscapesWithoutPartialDecisionsOrMoney() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), null));
        stubCompleteness(900, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.BlockingStage.P15_POLICY,
                result.blockingStage());
        assertEquals("PP_540_P15_ACTUAL_WORK_TIME_ACCRUAL_UNKNOWN:1", result.blockingReason());
        assertNull(result.policy());
        assertNull(result.calculation());
    }

    @Test
    void noPayrollMonthBlocksOnlyWhenIncludedPremiumNeedsProportionalDenominator() {
        YearMonth zero = YearMonth.of(2025, 8);
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), false));
        stubCompleteness(900, 900, false, false, List.of(zero));

        var result = service.calculate(user, EVENT, THROUGH, List.of(zero));

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.BlockingStage.REFERENCE_COMPLETENESS,
                result.blockingStage());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.NO_PAYROLL_NORM_AUTHORITY_UNRESOLVED,
                result.blockingReason());
        assertEquals(zero, result.blockingPeriod());
        assertNull(result.calculation());
    }

    @Test
    void noPayrollMonthCanStillReturnReadyZeroWhenNoPremiumNeedsProration() {
        YearMonth zero = YearMonth.of(2025, 8);
        stubDiscovery("RUB");
        stubCompleteness(900, 900, false, false, List.of(zero));

        var result = service.calculate(user, EVENT, THROUGH, List.of(zero));

        assertTrue(result.ready());
        assertEquals(0L, result.calculation().includedPremiumAmountMinor());
        assertTrue(result.policy().decisions().isEmpty());
    }

    @Test
    void postReferenceNoPayrollProofRemainsForDiscoveryButIsFilteredFromF3F3Completeness() {
        YearMonth referenceZero = YearMonth.of(2025, 8);
        YearMonth laterZero = YearMonth.of(2026, 8);
        List<YearMonth> all = List.of(referenceZero, laterZero);

        when(discovery.resolve(user, EVENT, THROUGH, all)).thenReturn(discovery("RUB", List.of()));
        when(completeness.resolve(user, EVENT, List.of(referenceZero))).thenReturn(
                completeness(900, 900, false, false, List.of(referenceZero))
        );

        service.calculate(user, EVENT, THROUGH, all);

        ArgumentCaptor<List<YearMonth>> captor = ArgumentCaptor.forClass(List.class);
        verify(completeness).resolve(eq(user), eq(EVENT), captor.capture());
        assertEquals(List.of(referenceZero), captor.getValue());
    }

    @Test
    void discoveryAuthorityWindowMismatchBlocksBeforeCompleteness() {
        var wrong = AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution.ready(
                LocalDate.of(2026, 9, 1),
                YearMonth.of(2026, 9),
                YearMonth.of(2025, 9),
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 8),
                "RUB",
                List.of()
        );
        when(discovery.resolve(user, EVENT, THROUGH, List.of())).thenReturn(wrong);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
        verifyNoInteractions(completeness);
    }

    @Test
    void completenessAuthorityWindowMismatchBlocksInsteadOfPassingWrongBooleanToPolicy() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), false));
        var wrong = AverageEarningsBonusP15ReferenceCompletenessService.Resolution.ready(
                LocalDate.of(2026, 9, 1),
                YearMonth.of(2026, 9),
                YearMonth.of(2025, 9),
                YearMonth.of(2026, 8),
                WorkTimeAccountingMode.SUMMARIZED,
                new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(WORKING_MINUTES, 1, 1),
                true,
                false,
                List.of(),
                true,
                true,
                List.of()
        );
        when(completeness.resolve(user, EVENT, List.of())).thenReturn(wrong);

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason());
    }

    @Test
    void formulaBlockerIsExposedAtFormulaStageWithoutMoney() {
        var longOdd = discovered(
                7,
                YearMonth.of(2026, 4),
                PayrollBonusP15Nature.WORK_PERIOD,
                "LONG_ODD",
                1_800_000L,
                LocalDate.of(2024, 10, 15),
                LocalDate.of(2026, 3, 14),
                false
        );
        stubDiscovery("RUB", longOdd);
        stubCompleteness(900, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.BlockingStage.P15_FORMULA,
                result.blockingStage());
        assertEquals("PP_540_P15_MONTHLY_PART_PERIOD_MONTHS_UNRESOLVED:7", result.blockingReason());
        assertNull(result.calculation());
    }

    @Test
    void positiveMoneyWithoutDiscoveryCurrencyBlocksRatherThanReturningUnitlessAmount() {
        when(discovery.resolve(user, EVENT, THROUGH, List.of())).thenReturn(
                discovery(null, List.of(monthly(1, YearMonth.of(2026, 3), false)))
        );
        stubCompleteness(1_000, 1_000, true, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertFalse(result.ready());
        assertEquals(AverageEarningsBonusP15CalculationPipelineService.CURRENCY_MISSING,
                result.blockingReason());
        assertNull(result.calculation());
    }

    @Test
    void excludedPremiumCanFinishWithoutReferenceProrationAndContributesZeroMoney() {
        var outside = monthly(1, YearMonth.of(2026, 8), false);
        stubDiscovery("RUB", outside);
        stubCompleteness(900, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertEquals(0L, result.calculation().includedPremiumAmountMinor());
        assertFalse(result.policy().decisions().get(0).included());
    }

    @Test
    void readyPipelineExposesSameReferenceFactUsedByFormulaForAudit() {
        stubDiscovery("RUB", monthly(1, YearMonth.of(2026, 3), false));
        stubCompleteness(875, 1_000, false, true, List.of());

        var result = service.calculate(user, EVENT, THROUGH, List.of());

        assertTrue(result.ready());
        assertSame(
                result.referenceCompleteness().referenceWorkedTime(),
                result.calculation().lines().get(0).referenceWorkedTime()
        );
    }

    private void stubDiscovery(
            String currency,
            AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact... facts
    ) {
        when(discovery.resolve(eq(user), eq(EVENT), eq(THROUGH), anyList())).thenReturn(
                discovery(currency, List.of(facts))
        );
    }

    private void stubCompleteness(
            long worked,
            long norm,
            boolean fullyWorked,
            boolean normAuthorityComplete,
            List<YearMonth> noPayrollMonths
    ) {
        when(completeness.resolve(user, EVENT, List.of())).thenReturn(
                completeness(worked, norm, fullyWorked, normAuthorityComplete, noPayrollMonths)
        );
        if (!noPayrollMonths.isEmpty()) {
            when(completeness.resolve(user, EVENT, noPayrollMonths)).thenReturn(
                    completeness(worked, norm, fullyWorked, normAuthorityComplete, noPayrollMonths)
            );
        }
    }

    private AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution discovery(
            String currency,
            List<AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact> facts
    ) {
        return AverageEarningsBonusP15HistoricalFactDiscoveryService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                FROM,
                TO,
                THROUGH,
                currency,
                facts
        );
    }

    private AverageEarningsBonusP15ReferenceCompletenessService.Resolution completeness(
            long worked,
            long norm,
            boolean fullyWorked,
            boolean normAuthorityComplete,
            List<YearMonth> noPayrollMonths
    ) {
        return AverageEarningsBonusP15ReferenceCompletenessService.Resolution.ready(
                EVENT,
                EVENT_MONTH,
                FROM,
                TO,
                WorkTimeAccountingMode.SUMMARIZED,
                new AverageEarningsBonusP15Formula.ReferenceWorkedTimeFact(
                        WORKING_MINUTES,
                        worked,
                        norm
                ),
                worked == norm,
                !fullyWorked && noPayrollMonths.isEmpty() && worked == norm,
                noPayrollMonths,
                normAuthorityComplete,
                fullyWorked,
                List.of()
        );
    }

    private AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact monthly(
            long id,
            YearMonth accrual,
            Boolean actual
    ) {
        return discovered(
                id,
                accrual,
                PayrollBonusP15Nature.MONTHLY,
                "KPI",
                400_000L,
                accrual.atDay(1),
                accrual.atEndOfMonth(),
                actual
        );
    }

    private AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact annual(
            long id,
            YearMonth accrual,
            Boolean actual
    ) {
        return discovered(
                id,
                accrual,
                PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT",
                2_000_000L,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                actual
        );
    }

    private AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact discovered(
            long id,
            YearMonth accrual,
            PayrollBonusP15Nature nature,
            String indicator,
            long amount,
            LocalDate awardFrom,
            LocalDate awardTo,
            Boolean actual
    ) {
        return new AverageEarningsBonusP15HistoricalFactDiscoveryService.DiscoveredBonusFact(
                accrual.atDay(1),
                1,
                id,
                100 + id,
                200 + id,
                300 + id,
                nature == PayrollBonusP15Nature.MONTHLY
                        ? PayrollEarningKind.MONTHLY_BONUS
                        : PayrollEarningKind.ONE_TIME_BONUS,
                nature,
                accrual,
                accrual.atDay(1),
                accrual.atEndOfMonth(),
                amount,
                "RUB",
                indicator,
                awardFrom,
                awardTo,
                actual,
                false
        );
    }
}
