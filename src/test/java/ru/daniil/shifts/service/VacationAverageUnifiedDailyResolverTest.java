package ru.daniil.shifts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class VacationAverageUnifiedDailyResolverTest {

    private static final LocalDate EVENT = LocalDate.of(2026, 9, 15);
    private static final YearMonth EVENT_MONTH = YearMonth.from(EVENT);

    @Test
    void primaryReusesExactDailyAndStopsParagraph7AndParagraph8() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var window = AverageEarningsReferenceWindow.primary(EVENT);
        when(ordered.selectedReferenceWindow()).thenReturn(window);

        var exact = new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(12345L),
                BigInteger.valueOf(2L)
        );
        var calculation = reference(window, true, null, "RUB", exact);

        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> calculation,
                () -> {
                    p7.incrementAndGet();
                    return null;
                },
                () -> {
                    p8.incrementAndGet();
                    return null;
                }
        );

        assertTrue(result.ready());
        assertSame(exact, result.averageDaily());
        assertEquals("RUB", result.currencyCode());
        assertSame(calculation, result.referenceCalculation());
        assertEquals(0, p7.get());
        assertEquals(0, p8.get());
    }

    @Test
    void paragraph6ReusesExactSelectedWindowDaily() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_6_PRECEDING_REFERENCE_PERIOD
        );
        var window = AverageEarningsReferenceWindow.primary(EVENT).precedingEqual();
        when(ordered.selectedReferenceWindow()).thenReturn(window);

        var exact = new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(777L),
                BigInteger.valueOf(5L)
        );
        var calculation = reference(window, true, null, "RUB", exact);

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> calculation,
                () -> null,
                () -> null
        );

        assertTrue(result.ready());
        assertSame(exact, result.averageDaily());
        assertSame(calculation, result.referenceCalculation());
    }

    @Test
    void blockedOrderedFallbackStopsEveryDownstreamSupplier() {
        var ordered = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(ordered.eventDate()).thenReturn(EVENT);
        when(ordered.eventMonth()).thenReturn(EVENT_MONTH);
        when(ordered.ready()).thenReturn(false);
        when(ordered.blockingReason()).thenReturn("J5_BLOCK");

        AtomicInteger reference = new AtomicInteger();
        AtomicInteger p7 = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> {
                    reference.incrementAndGet();
                    return null;
                },
                () -> {
                    p7.incrementAndGet();
                    return null;
                },
                () -> {
                    p8.incrementAndGet();
                    return null;
                }
        );

        assertFalse(result.ready());
        assertEquals("J5_BLOCK", result.blockingReason());
        assertEquals(
                VacationAverageUnifiedDailyResolver.BlockingStage.ORDERED_FALLBACK,
                result.blockingStage()
        );
        assertEquals(0, reference.get());
        assertEquals(0, p7.get());
        assertEquals(0, p8.get());
    }

    @Test
    void orderedFallbackEventMonthMismatchBlocksBeforeDownstream() {
        var ordered = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(ordered.eventDate()).thenReturn(EVENT);
        when(ordered.eventMonth()).thenReturn(EVENT_MONTH.minusMonths(1));
        when(ordered.ready()).thenReturn(true);

        AtomicInteger calls = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> {
                    calls.incrementAndGet();
                    return null;
                },
                () -> {
                    calls.incrementAndGet();
                    return null;
                },
                () -> {
                    calls.incrementAndGet();
                    return null;
                }
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.REFERENCE_IDENTITY_MISMATCH,
                result.blockingReason()
        );
        assertEquals(0, calls.get());
    }

    @Test
    void nullReferenceCalculationBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        when(ordered.selectedReferenceWindow())
                .thenReturn(AverageEarningsReferenceWindow.primary(EVENT));

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.REFERENCE_CALCULATION_BLOCKED,
                result.blockingReason()
        );
    }

    @Test
    void referenceWindowMismatchBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var selected = AverageEarningsReferenceWindow.primary(EVENT);
        when(ordered.selectedReferenceWindow()).thenReturn(selected);

        var wrong = reference(
                selected.precedingEqual(),
                true,
                null,
                "RUB",
                exact(100L, 1L)
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> wrong,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.REFERENCE_IDENTITY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void blockedReferenceCalculationPropagatesReason() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var selected = AverageEarningsReferenceWindow.primary(EVENT);
        when(ordered.selectedReferenceWindow()).thenReturn(selected);

        var blocked = reference(selected, false, "PRIMARY_BLOCK", null, null);

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> blocked,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals("PRIMARY_BLOCK", result.blockingReason());
        assertEquals(
                VacationAverageUnifiedDailyResolver.BlockingStage.REFERENCE_CALCULATION,
                result.blockingStage()
        );
    }

    @Test
    void positiveReferenceDailyWithoutCurrencyBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );
        var selected = AverageEarningsReferenceWindow.primary(EVENT);
        when(ordered.selectedReferenceWindow()).thenReturn(selected);

        var calculation = reference(
                selected,
                true,
                null,
                null,
                exact(1L, 1L)
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> calculation,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.REFERENCE_IDENTITY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void paragraph7UsesExplicitParagraph10PartialMonthDenominator() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        stubParagraph7(ordered, 93000L, "RUB");

        var basis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT,
                14,
                "P10_EVENT_MONTH_CALENDAR_AUTHORITY"
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> basis,
                () -> null
        );

        assertTrue(result.ready());
        assertEquals(
                new BigInteger("13950000"),
                result.averageDaily().numeratorMinor()
        );
        assertEquals(
                new BigInteger("2051"),
                result.averageDaily().denominatorDays()
        );
        assertSame(basis, result.paragraph7CalendarBasis());
    }

    @Test
    void paragraph7DoesNotEvaluateReferenceOrParagraph8() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        stubParagraph7(ordered, 1000L, "RUB");

        AtomicInteger reference = new AtomicInteger();
        AtomicInteger p8 = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> {
                    reference.incrementAndGet();
                    return null;
                },
                () -> VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                        EVENT,
                        10,
                        "P10"
                ),
                () -> {
                    p8.incrementAndGet();
                    return null;
                }
        );

        assertTrue(result.ready());
        assertEquals(0, reference.get());
        assertEquals(0, p8.get());
    }

    @Test
    void paragraph7NullCalendarBasisBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        stubParagraph7(ordered, 1000L, "RUB");

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.PARAGRAPH_7_CALENDAR_BASIS_REQUIRED,
                result.blockingReason()
        );
    }

    @Test
    void paragraph7CalendarIdentityMismatchBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        stubParagraph7(ordered, 1000L, "RUB");

        var basis = mock(VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.class);
        when(basis.eventDate()).thenReturn(EVENT.minusDays(1));
        when(basis.eventMonth()).thenReturn(EVENT_MONTH);
        when(basis.periodFrom()).thenReturn(EVENT_MONTH.atDay(1));
        when(basis.cutoffExclusive()).thenReturn(EVENT);

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> basis,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.PARAGRAPH_7_CALENDAR_IDENTITY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void readyOrderedFallbackWithoutSelectionBlocksBeforeDownstream() {
        var ordered = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(ordered.eventDate()).thenReturn(EVENT);
        when(ordered.eventMonth()).thenReturn(EVENT_MONTH);
        when(ordered.ready()).thenReturn(true);
        when(ordered.selection()).thenReturn(null);

        AtomicInteger calls = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> {
                    calls.incrementAndGet();
                    return null;
                },
                () -> {
                    calls.incrementAndGet();
                    return null;
                },
                () -> {
                    calls.incrementAndGet();
                    return null;
                }
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.ORDERED_FALLBACK_BLOCKED,
                result.blockingReason()
        );
        assertEquals(0, calls.get());
    }

    @Test
    void paragraph8NullSelectedAuthorityBlocksBeforeFormulaSupplier() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        when(ordered.paragraph8Authority()).thenReturn(null);

        AtomicInteger calls = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> {
                    calls.incrementAndGet();
                    return null;
                }
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.PARAGRAPH_8_FORMULA_IDENTITY_MISMATCH,
                result.blockingReason()
        );
        assertEquals(0, calls.get());
    }

    @Test
    void paragraph7MissingCurrencyBlocksBeforeFormula() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );
        stubParagraph7(ordered, 1000L, null);

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                        EVENT,
                        10,
                        "P10"
                ),
                () -> null
        );

        assertFalse(result.ready());
        assertNull(result.averageDaily());
    }

    @Test
    void paragraph7CalendarBasisRejectsZeroCountableDays() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                        EVENT,
                        0,
                        "P10"
                )
        );
    }

    @Test
    void paragraph7CalendarBasisRejectsCountPastEventCutoff() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                        EVENT,
                        15,
                        "P10"
                )
        );
    }

    @Test
    void paragraph7CalendarDenominatorIsExactAndReduced() {
        var basis = VacationAverageUnifiedDailyResolver.Paragraph7CalendarBasis.of(
                EVENT,
                10,
                "P10"
        );

        assertEquals(
                new BigInteger("293"),
                basis.denominatorDays().numerator()
        );
        assertEquals(
                BigInteger.valueOf(30L),
                basis.denominatorDays().denominator()
        );
    }

    @Test
    void paragraph8MonthlySalaryUsesExplicitSalaryPer293Policy() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        stubParagraph8Salary(ordered, 120000L, "RUB");

        var basis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT,
                "RUB",
                "EXPLICIT_P8_SALARY_POLICY"
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> basis
        );

        assertTrue(result.ready());
        assertEquals(
                BigInteger.valueOf(1200000L),
                result.averageDaily().numeratorMinor()
        );
        assertEquals(
                BigInteger.valueOf(293L),
                result.averageDaily().denominatorDays()
        );
    }

    @Test
    void paragraph8HourlyTariffUsesExplicitAnnualNormFormula() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        stubParagraph8Hourly(ordered, 50000L, "RUB");

        var basis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.hourlyTariff(
                EVENT,
                "RUB",
                "EXPLICIT_P8_HOURLY_POLICY",
                118800L
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> basis
        );

        assertTrue(result.ready());
        assertEquals(
                new BigInteger("82500000"),
                result.averageDaily().numeratorMinor()
        );
        assertEquals(
                BigInteger.valueOf(293L),
                result.averageDaily().denominatorDays()
        );
    }

    @Test
    void paragraph8DoesNotEvaluateReferenceOrParagraph7() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        stubParagraph8Salary(ordered, 90000L, "RUB");

        AtomicInteger reference = new AtomicInteger();
        AtomicInteger p7 = new AtomicInteger();

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> {
                    reference.incrementAndGet();
                    return null;
                },
                () -> {
                    p7.incrementAndGet();
                    return null;
                },
                () -> VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                        EVENT,
                        "RUB",
                        "POLICY"
                )
        );

        assertTrue(result.ready());
        assertEquals(0, reference.get());
        assertEquals(0, p7.get());
    }

    @Test
    void paragraph8NullFormulaBasisBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        stubParagraph8Salary(ordered, 90000L, "RUB");

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> null
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.PARAGRAPH_8_FORMULA_BASIS_REQUIRED,
                result.blockingReason()
        );
    }

    @Test
    void paragraph8EstablishedBasisMismatchBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        stubParagraph8Salary(ordered, 90000L, "RUB");

        var basis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.hourlyTariff(
                EVENT,
                "RUB",
                "HOURLY_POLICY",
                118800L
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> basis
        );

        assertFalse(result.ready());
        assertEquals(
                VacationAverageUnifiedDailyResolver.PARAGRAPH_8_FORMULA_IDENTITY_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void paragraph8CurrencyMismatchBlocks() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_8_ESTABLISHED_TARIFF_OR_SALARY
        );
        stubParagraph8Salary(ordered, 90000L, "RUB");

        var basis = VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                EVENT,
                "USD",
                "SALARY_POLICY"
        );

        var result = VacationAverageUnifiedDailyResolver.resolve(
                ordered,
                () -> null,
                () -> null,
                () -> basis
        );

        assertFalse(result.ready());
    }

    @Test
    void paragraph8WrongPolicyShapeCannotBeConstructed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis(
                        EVENT,
                        EVENT_MONTH,
                        AverageEarningsParagraph8TariffSalaryAuthorityService
                                .EstablishedBasis.MONTHLY_OFFICIAL_SALARY,
                        "RUB",
                        VacationAverageUnifiedDailyResolver.Paragraph8FormulaPolicy
                                .HOURLY_TARIFF_AVERAGE_MONTHLY_NORM_DIV_29_3,
                        "BAD",
                        118800L
                )
        );
    }

    @Test
    void paragraph8HourlyBasisRejectsNonPositiveAnnualNorm() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.hourlyTariff(
                        EVENT,
                        "RUB",
                        "HOURLY_POLICY",
                        0L
                )
        );
    }

    @Test
    void blockedResultRejectsPartialAverageDailyMoney() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new VacationAverageUnifiedDailyResolver.Resolution(
                        EVENT,
                        EVENT_MONTH,
                        false,
                        VacationAverageUnifiedDailyResolver.BlockingStage.REFERENCE_CALCULATION,
                        "BLOCK",
                        "message",
                        ordered,
                        "RUB",
                        exact(1L, 1L),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void readyResultRejectsWrongBranchProvenance() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection
                        .PARAGRAPH_7_PRE_EVENT_ACCRUED_WAGE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new VacationAverageUnifiedDailyResolver.Resolution(
                        EVENT,
                        EVENT_MONTH,
                        true,
                        null,
                        null,
                        null,
                        ordered,
                        "RUB",
                        exact(1L, 1L),
                        mock(VacationAveragePrimaryCalculationService.Resolution.class),
                        null,
                        null
                )
        );
    }

    @Test
    void resolverRequiresAllSuppliersEvenWhenBranchWouldBeLazy() {
        var ordered = ordered(
                AverageEarningsOrderedFallbackResolver.Selection.PRIMARY_REFERENCE_PERIOD
        );

        assertThrows(
                NullPointerException.class,
                () -> VacationAverageUnifiedDailyResolver.resolve(
                        ordered,
                        () -> null,
                        null,
                        () -> null
                )
        );
    }

    @Test
    void paragraph8FormulaBasisRequiresNonBlankAuthorityCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VacationAverageUnifiedDailyResolver.Paragraph8FormulaBasis.monthlySalary(
                        EVENT,
                        "RUB",
                        " "
                )
        );
    }

    private static AverageEarningsOrderedFallbackResolver.Resolution ordered(
            AverageEarningsOrderedFallbackResolver.Selection selection
    ) {
        var ordered = mock(AverageEarningsOrderedFallbackResolver.Resolution.class);
        when(ordered.eventDate()).thenReturn(EVENT);
        when(ordered.eventMonth()).thenReturn(EVENT_MONTH);
        when(ordered.ready()).thenReturn(true);
        when(ordered.selection()).thenReturn(selection);
        return ordered;
    }

    private static VacationAveragePrimaryCalculationService.Resolution reference(
            AverageEarningsReferenceWindow window,
            boolean ready,
            String blockingReason,
            String currency,
            VacationAverageDailyEarningsFormula.ExactMoneyPerDay daily
    ) {
        var result = mock(VacationAveragePrimaryCalculationService.Resolution.class);
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.referenceFrom()).thenReturn(window.referenceFrom());
        when(result.referenceTo()).thenReturn(window.referenceTo());
        when(result.ready()).thenReturn(ready);
        when(result.blockingReason()).thenReturn(blockingReason);
        when(result.currencyCode()).thenReturn(currency);
        when(result.averageDaily()).thenReturn(daily);
        return result;
    }

    private static void stubParagraph7(
            AverageEarningsOrderedFallbackResolver.Resolution ordered,
            long amount,
            String currency
    ) {
        var authority = paragraph7(amount, currency);
        when(ordered.paragraph7Authority()).thenReturn(authority);
    }

    private static void stubParagraph8Salary(
            AverageEarningsOrderedFallbackResolver.Resolution ordered,
            long salaryMinor,
            String currency
    ) {
        var authority = paragraph8Salary(salaryMinor, currency);
        when(ordered.paragraph8Authority()).thenReturn(authority);
    }

    private static void stubParagraph8Hourly(
            AverageEarningsOrderedFallbackResolver.Resolution ordered,
            long hourlyMinor,
            String currency
    ) {
        var authority = paragraph8Hourly(hourlyMinor, currency);
        when(ordered.paragraph8Authority()).thenReturn(authority);
    }

    private static AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution
            paragraph7(
                    long amount,
                    String currency
            ) {
        var result = mock(
                AverageEarningsParagraph7PreEventAccruedWageAuthority.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.periodFrom()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.cutoffExclusive()).thenReturn(EVENT);
        when(result.ready()).thenReturn(true);
        when(result.accruedWagePresent()).thenReturn(amount > 0L);
        when(result.workedTimePresent()).thenReturn(true);
        when(result.totalAccruedWageMinor()).thenReturn(amount);
        when(result.currencyCode()).thenReturn(currency);
        return result;
    }

    private static AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution
            paragraph8Salary(
                    long salaryMinor,
                    String currency
            ) {
        var result = mock(
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.compensationBoundary()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.ready()).thenReturn(true);
        when(result.establishedBasis()).thenReturn(
                AverageEarningsParagraph8TariffSalaryAuthorityService
                        .EstablishedBasis.MONTHLY_OFFICIAL_SALARY
        );
        when(result.establishedAmountMinor()).thenReturn(salaryMinor);
        when(result.currencyCode()).thenReturn(currency);
        return result;
    }

    private static AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution
            paragraph8Hourly(
                    long hourlyMinor,
                    String currency
            ) {
        var result = mock(
                AverageEarningsParagraph8TariffSalaryAuthorityService.Resolution.class
        );
        when(result.eventDate()).thenReturn(EVENT);
        when(result.eventMonth()).thenReturn(EVENT_MONTH);
        when(result.compensationBoundary()).thenReturn(EVENT_MONTH.atDay(1));
        when(result.ready()).thenReturn(true);
        when(result.establishedBasis()).thenReturn(
                AverageEarningsParagraph8TariffSalaryAuthorityService
                        .EstablishedBasis.HOURLY_TARIFF_RATE
        );
        when(result.establishedAmountMinor()).thenReturn(hourlyMinor);
        when(result.currencyCode()).thenReturn(currency);
        return result;
    }

    private static VacationAverageDailyEarningsFormula.ExactMoneyPerDay exact(
            long numerator,
            long denominator
    ) {
        return new VacationAverageDailyEarningsFormula.ExactMoneyPerDay(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator)
        );
    }
}
