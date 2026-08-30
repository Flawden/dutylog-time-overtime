package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBasePayAuthorityService.Resolution;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventOrdinaryPremiumService.RateBucket;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.OrdinaryPremiumSource;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.PayPricingPolicyService.ResolvedPricingPolicy;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph7PreEventOrdinaryPremiumServiceTest {
    private AppUser user;
    private OrdinaryWorkPremiumSourceService sourceService;
    private PayPricingPolicyService pricingPolicy;
    private HistoricalCompensationRateService historicalRates;
    private PayPricingEngine pricingEngine;
    private AverageEarningsParagraph7PreEventOrdinaryPremiumService service;

    @BeforeEach
    void setUp() {
        user = mock(AppUser.class);
        sourceService = mock(OrdinaryWorkPremiumSourceService.class);
        pricingPolicy = mock(PayPricingPolicyService.class);
        historicalRates = mock(HistoricalCompensationRateService.class);
        pricingEngine = mock(PayPricingEngine.class);
        service = new AverageEarningsParagraph7PreEventOrdinaryPremiumService(
                sourceService,
                pricingPolicy,
                historicalRates,
                pricingEngine
        );
    }

    @Test
    void constructorRejectsMissingCanonicalDependency() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventOrdinaryPremiumService(
                        null,
                        pricingPolicy,
                        historicalRates,
                        pricingEngine
                )
        );
    }

    @Test
    void nullUserRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(null, workedSemantic(LocalDate.of(2026, 8, 3)))
        );
    }

    @Test
    void nullSemanticAuthorityRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, null)
        );
    }

    @Test
    void blockedSemanticAuthorityRejectedBeforeOrdinarySourceRead() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        var basePay = workedSemantic(eventDate).basePay();
        var blocked = new AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution(
                eventDate,
                eventDate.withDayOfMonth(1),
                eventDate,
                false,
                "SEMANTIC_BLOCK",
                "blocked",
                basePay,
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(user, blocked)
        );
        verifyNoInteractions(sourceService, pricingPolicy, historicalRates, pricingEngine);
    }

    @Test
    void nestedAuthorityWindowMismatchBlocksBeforeOrdinarySourceRead() {
        LocalDate authorityEvent = LocalDate.of(2026, 8, 3);
        LocalDate semanticEvent = LocalDate.of(2026, 8, 4);
        var semantic = new AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution(
                semanticEvent,
                semanticEvent.withDayOfMonth(1),
                semanticEvent,
                true,
                null,
                null,
                workedSemantic(authorityEvent).basePay(),
                List.of()
        );

        var result = service.resolve(user, semantic);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.SEMANTIC_AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertTrue(result.pricingSources().isEmpty());
        assertTrue(result.rateBuckets().isEmpty());
        verifyNoInteractions(sourceService, pricingPolicy, historicalRates, pricingEngine);
    }

    @Test
    void noWorkedTimeReturnsReadyZeroWithoutOrdinarySourceRead() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);

        var result = service.resolve(user, noWorkSemantic(eventDate));

        assertTrue(result.ready());
        assertEquals(0, result.ordinaryMinutes());
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertNull(result.currencyCode());
        assertFalse(result.premiumMoneyPresent());
        verifyNoInteractions(sourceService, pricingPolicy, historicalRates, pricingEngine);
    }

    @Test
    void sourceWindowStopsStrictlyBeforeEventDate() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        stubSources(
                eventDate,
                Map.of(
                        LocalDate.of(2026, 8, 1), emptySource(LocalDate.of(2026, 8, 1)),
                        LocalDate.of(2026, 8, 2), emptySource(LocalDate.of(2026, 8, 2))
                )
        );

        var result = service.resolve(user, workedSemantic(eventDate));

        assertTrue(result.ready());
        verify(sourceService).project(user, LocalDate.of(2026, 8, 1));
        verify(sourceService).project(user, LocalDate.of(2026, 8, 2));
        verify(sourceService, never()).project(user, eventDate);
        verifyNoMoreInteractions(sourceService);
    }

    @Test
    void nullOrdinarySourceIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        when(sourceService.project(user, LocalDate.of(2026, 8, 1)))
                .thenReturn(null);

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, workedSemantic(eventDate))
        );
    }

    @Test
    void sourcePayrollDateMismatchIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        when(sourceService.project(user, LocalDate.of(2026, 8, 1)))
                .thenReturn(emptySource(LocalDate.of(2026, 8, 2)));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedSemantic(eventDate))
        );
    }

    @Test
    void blockedOrdinarySourceBlocksWithoutPartialMoney() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        when(sourceService.project(user, LocalDate.of(2026, 8, 1)))
                .thenReturn(new OrdinaryPremiumSource(
                        LocalDate.of(2026, 8, 1),
                        SourceKind.PLAN_DERIVED,
                        60,
                        false,
                        OrdinaryWorkPremiumSourceService.BLOCK_PLANNED_CLOCK,
                        List.of()
                ));

        var result = service.resolve(user, workedSemantic(eventDate));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.SOURCE_BLOCKED,
                result.blockingReason()
        );
        assertEquals(
                OrdinaryWorkPremiumSourceService.BLOCK_PLANNED_CLOCK,
                result.sourceBlockingReason()
        );
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertTrue(result.pricingSources().isEmpty());
        assertTrue(result.rateBuckets().isEmpty());
        verifyNoInteractions(pricingPolicy, historicalRates, pricingEngine);
    }

    @Test
    void sourcePieceFromAnotherDateIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        LocalDate payrollDate = LocalDate.of(2026, 8, 1);
        SourcePiece wrongDatePiece = piece(
                LocalDate.of(2026, 8, 2),
                60,
                true,
                false
        );
        when(sourceService.project(user, payrollDate))
                .thenReturn(new OrdinaryPremiumSource(
                        payrollDate,
                        SourceKind.PLAN_DERIVED,
                        60,
                        true,
                        null,
                        List.of(wrongDatePiece)
                ));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedSemantic(eventDate))
        );
    }

    @Test
    void regularOnlyRangeReturnsZeroWithoutPricingOrRateLookup() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        Map<LocalDate, OrdinaryPremiumSource> sources = new LinkedHashMap<>();
        sources.put(
                LocalDate.of(2026, 8, 1),
                source(LocalDate.of(2026, 8, 1), 60, false, false)
        );
        sources.put(
                LocalDate.of(2026, 8, 2),
                source(LocalDate.of(2026, 8, 2), 60, false, false)
        );
        stubSources(eventDate, sources);

        var result = service.resolve(user, workedSemantic(eventDate));

        assertTrue(result.ready());
        assertEquals(120, result.ordinaryMinutes());
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertEquals("RUB", result.currencyCode());
        assertTrue(result.pricingSources().isEmpty());
        assertTrue(result.rateBuckets().isEmpty());
        verifyNoInteractions(pricingPolicy, historicalRates, pricingEngine);
    }

    @Test
    void singleNightPremiumUsesCanonicalPricingEngine() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(
                        date,
                        List.of(slice(60, "NIGHT", 2_000))
                ));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "RUB"));
        PayPricingEngine realEngine = spy(new PayPricingEngine());
        var actual = serviceWith(realEngine).resolve(user, workedSemantic(eventDate));

        assertTrue(actual.ready());
        assertEquals(1_200L, actual.ordinaryPremiumAmountMinor());
        assertEquals(1, actual.rateBuckets().size());
        assertEquals(60, actual.rateBuckets().get(0).minutes());
        verify(realEngine, times(1)).price(eq(6_000L), anyList());
    }

    @Test
    void singleHolidayPremiumUsesCanonicalPricingEngine() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, false, true));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(
                        date,
                        List.of(slice(60, "HOLIDAY", 10_000))
                ));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "RUB"));

        var actual = serviceWith(new PayPricingEngine())
                .resolve(user, workedSemantic(eventDate));

        assertTrue(actual.ready());
        assertEquals(6_000L, actual.ordinaryPremiumAmountMinor());
        assertEquals("RUB", actual.currencyCode());
    }

    @Test
    void sameEconomicKeyAcrossDatesIsAggregatedBeforeHalfUpRounding() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        LocalDate first = LocalDate.of(2026, 8, 1);
        LocalDate second = LocalDate.of(2026, 8, 2);
        stubSources(
                eventDate,
                Map.of(
                        first, source(first, 1, true, false),
                        second, source(second, 1, true, false)
                )
        );
        when(pricingPolicy.resolveForSourceDate(eq(user), any(LocalDate.class), anyList()))
                .thenAnswer(invocation -> policy(
                        invocation.getArgument(1),
                        List.of(slice(1, "NIGHT", 5_000))
                ));
        when(historicalRates.resolve(eq(user), any(LocalDate.class)))
                .thenAnswer(invocation -> rate(
                        invocation.getArgument(1),
                        48L,
                        "RUB"
                ));
        PayPricingEngine realEngine = spy(new PayPricingEngine());

        var actual = serviceWith(realEngine)
                .resolve(user, workedSemantic(eventDate));

        // 48 * 2 min * 50% / 60 = 0.8 minor -> 1 after one HALF_UP.
        // Pricing each day separately would produce 0 + 0 and is forbidden.
        assertEquals(1L, actual.ordinaryPremiumAmountMinor());
        assertEquals(1, actual.rateBuckets().size());
        verify(realEngine, times(1)).price(eq(48L), argThat(slices -> slices.size() == 2));
    }

    @Test
    void differentHistoricalRatesRemainSeparateRoundingBuckets() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        LocalDate first = LocalDate.of(2026, 8, 1);
        LocalDate second = LocalDate.of(2026, 8, 2);
        stubSources(
                eventDate,
                Map.of(
                        first, source(first, 1, true, false),
                        second, source(second, 1, true, false)
                )
        );
        when(pricingPolicy.resolveForSourceDate(eq(user), any(LocalDate.class), anyList()))
                .thenAnswer(invocation -> policy(
                        invocation.getArgument(1),
                        List.of(slice(1, "NIGHT", 5_000))
                ));
        when(historicalRates.resolve(user, first))
                .thenReturn(rate(first, 48L, "RUB"));
        when(historicalRates.resolve(user, second))
                .thenReturn(rate(second, 72L, "RUB"));
        PayPricingEngine realEngine = spy(new PayPricingEngine());

        var actual = serviceWith(realEngine)
                .resolve(user, workedSemantic(eventDate));

        assertEquals(1L, actual.ordinaryPremiumAmountMinor());
        assertEquals(2, actual.rateBuckets().size());
        assertEquals(List.of(48L, 72L), actual.rateBuckets().stream()
                .map(RateBucket::baseHourlyRateMinor)
                .toList());
        verify(realEngine).price(eq(48L), anyList());
        verify(realEngine).price(eq(72L), anyList());
    }

    @Test
    void mixedRegularAndPremiumSlicesPreserveWholeCanonicalSourceQuantity() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        List<SourcePiece> pieces = List.of(
                piece(date, 30, false, false),
                piece(date, 30, true, false)
        );
        when(sourceService.project(user, date))
                .thenReturn(new OrdinaryPremiumSource(
                        date,
                        SourceKind.PLAN_DERIVED,
                        60,
                        true,
                        null,
                        pieces
                ));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(
                        date,
                        List.of(
                                new PricingSlice(30, List.of()),
                                slice(30, "NIGHT", 2_000)
                        )
                ));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "RUB"));

        var actual = serviceWith(new PayPricingEngine())
                .resolve(user, workedSemantic(eventDate));

        assertEquals(60, actual.ordinaryMinutes());
        assertEquals(600L, actual.ordinaryPremiumAmountMinor());
        assertEquals(60, actual.rateBuckets().get(0).minutes());
        assertEquals(30, actual.pricingSources().get(0).nightMinutes());
    }

    @Test
    void pricingPolicySourceDateMismatchIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(
                        LocalDate.of(2026, 8, 2),
                        List.of(slice(60, "NIGHT", 2_000))
                ));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "RUB"));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedSemantic(eventDate))
        );
    }

    @Test
    void pricingPolicyMinuteMismatchIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(
                        date,
                        List.of(slice(59, "NIGHT", 2_000))
                ));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "RUB"));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedSemantic(eventDate))
        );
    }

    @Test
    void historicalRateSourceDateMismatchIsStructuralFailure() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(date, List.of(slice(60, "NIGHT", 2_000))));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(LocalDate.of(2026, 8, 2), 6_000L, "RUB"));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, workedSemantic(eventDate))
        );
    }

    @Test
    void rateCurrencyMismatchBlocksWithoutPartialPremiumMoney() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(date, List.of(slice(60, "NIGHT", 2_000))));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "USD"));

        var result = service.resolve(user, workedSemantic(eventDate));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.CURRENCY_MISMATCH,
                result.blockingReason()
        );
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertNull(result.currencyCode());
        assertTrue(result.pricingSources().isEmpty());
        assertTrue(result.rateBuckets().isEmpty());
        verifyNoInteractions(pricingEngine);
    }

    @Test
    void missingPricingRuleApiExceptionBecomesFailClosedBlocker() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenThrow(ApiException.conflict(
                        "PAY_PRICING_RULES_REQUIRED",
                        "missing"
                ));

        var result = service.resolve(user, workedSemantic(eventDate));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.PRICING_AUTHORITY_BLOCKED,
                result.blockingReason()
        );
        assertEquals("PAY_PRICING_RULES_REQUIRED", result.sourceBlockingReason());
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        verifyNoInteractions(historicalRates, pricingEngine);
    }

    @Test
    void missingHistoricalRateApiExceptionBecomesFailClosedBlocker() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(date, List.of(slice(60, "NIGHT", 2_000))));
        when(historicalRates.resolve(user, date))
                .thenThrow(ApiException.conflict(
                        "PAYROLL_COMPENSATION_REQUIRED",
                        "missing"
                ));

        var result = service.resolve(user, workedSemantic(eventDate));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventOrdinaryPremiumService.PRICING_AUTHORITY_BLOCKED,
                result.blockingReason()
        );
        assertEquals("PAYROLL_COMPENSATION_REQUIRED", result.sourceBlockingReason());
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        verifyNoInteractions(pricingEngine);
    }

    @Test
    void zeroBpsPremiumIsValidZeroMoneyAndCarriesNoFallbackMeaning() {
        LocalDate eventDate = LocalDate.of(2026, 8, 2);
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(sourceService.project(user, date))
                .thenReturn(source(date, 60, true, false));
        when(pricingPolicy.resolveForSourceDate(eq(user), eq(date), anyList()))
                .thenReturn(policy(date, List.of(slice(60, "NIGHT", 0))));
        when(historicalRates.resolve(user, date))
                .thenReturn(rate(date, 6_000L, "RUB"));

        var result = serviceWith(new PayPricingEngine())
                .resolve(user, workedSemantic(eventDate));

        assertTrue(result.ready());
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertFalse(result.premiumMoneyPresent());
        assertEquals(1, result.pricingSources().size());
        assertEquals(1, result.rateBuckets().size());
    }

    @Test
    void laterAmbiguousCurrencyClearsEarlierAcceptedPricingEvidence() {
        LocalDate eventDate = LocalDate.of(2026, 8, 3);
        LocalDate first = LocalDate.of(2026, 8, 1);
        LocalDate second = LocalDate.of(2026, 8, 2);
        stubSources(
                eventDate,
                Map.of(
                        first, source(first, 60, true, false),
                        second, source(second, 60, true, false)
                )
        );
        when(pricingPolicy.resolveForSourceDate(eq(user), any(LocalDate.class), anyList()))
                .thenAnswer(invocation -> policy(
                        invocation.getArgument(1),
                        List.of(slice(60, "NIGHT", 2_000))
                ));
        when(historicalRates.resolve(user, first))
                .thenReturn(rate(first, 6_000L, "RUB"));
        when(historicalRates.resolve(user, second))
                .thenReturn(rate(second, 6_000L, "USD"));

        var result = service.resolve(user, workedSemantic(eventDate));

        assertFalse(result.ready());
        assertEquals(0L, result.ordinaryPremiumAmountMinor());
        assertTrue(result.pricingSources().isEmpty());
        assertTrue(result.rateBuckets().isEmpty());
        verifyNoInteractions(pricingEngine);
    }

    private AverageEarningsParagraph7PreEventOrdinaryPremiumService serviceWith(
            PayPricingEngine engine
    ) {
        return new AverageEarningsParagraph7PreEventOrdinaryPremiumService(
                sourceService,
                pricingPolicy,
                historicalRates,
                engine
        );
    }

    private AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution workedSemantic(
            LocalDate eventDate
    ) {
        LocalDate periodFrom = eventDate.withDayOfMonth(1);
        LocalDate workedDate = periodFrom;
        var work = AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                eventDate,
                periodFrom,
                eventDate,
                1,
                60L,
                List.of(
                        new AverageEarningsParagraph7PreEventWorkFactService.WorkedDayFact(
                                workedDate,
                                60,
                                60,
                                60
                        )
                )
        );
        Resolution authority = new Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                work,
                periodFrom,
                "HOURLY",
                "RUB",
                6_000L,
                null,
                null,
                60L
        );
        var basePay = new AverageEarningsParagraph7PreEventBasePayFormula.Calculation(
                authority,
                6_000L
        );
        return new AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                basePay,
                List.of()
        );
    }

    private AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution noWorkSemantic(
            LocalDate eventDate
    ) {
        LocalDate periodFrom = eventDate.withDayOfMonth(1);
        var work = AverageEarningsParagraph7PreEventWorkFactService.Resolution.ready(
                eventDate,
                periodFrom,
                eventDate,
                0,
                0L,
                List.of()
        );
        Resolution authority = new Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                work,
                null,
                null,
                null,
                null,
                null,
                null,
                0L
        );
        var basePay = new AverageEarningsParagraph7PreEventBasePayFormula.Calculation(
                authority,
                0L
        );
        return new AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution(
                eventDate,
                periodFrom,
                eventDate,
                true,
                null,
                null,
                basePay,
                List.of()
        );
    }

    private void stubSources(
            LocalDate eventDate,
            Map<LocalDate, OrdinaryPremiumSource> values
    ) {
        for (
                LocalDate date = eventDate.withDayOfMonth(1);
                date.isBefore(eventDate);
                date = date.plusDays(1)
        ) {
            when(sourceService.project(user, date))
                    .thenReturn(values.getOrDefault(date, emptySource(date)));
        }
    }

    private OrdinaryPremiumSource emptySource(LocalDate date) {
        return new OrdinaryPremiumSource(
                date,
                SourceKind.PLAN_DERIVED,
                0,
                true,
                null,
                List.of()
        );
    }

    private OrdinaryPremiumSource source(
            LocalDate date,
            int minutes,
            boolean night,
            boolean holiday
    ) {
        return new OrdinaryPremiumSource(
                date,
                SourceKind.PLAN_DERIVED,
                minutes,
                true,
                null,
                List.of(piece(date, minutes, night, holiday))
        );
    }

    private SourcePiece piece(
            LocalDate date,
            int minutes,
            boolean night,
            boolean holiday
    ) {
        return new SourcePiece(
                date,
                SourceKind.PLAN_DERIVED,
                null,
                minutes,
                night,
                holiday
        );
    }

    private ResolvedPricingPolicy policy(
            LocalDate sourceDate,
            List<PricingSlice> slices
    ) {
        return new ResolvedPricingPolicy(
                sourceDate,
                sourceDate.withDayOfMonth(1),
                new RuleSet(List.of()),
                slices
        );
    }

    private PricingSlice slice(
            int minutes,
            String code,
            int bps
    ) {
        return new PricingSlice(
                minutes,
                List.of(new PremiumComponent(code, bps))
        );
    }

    private HistoricalBaseRate rate(
            LocalDate sourceDate,
            long hourlyRateMinor,
            String currency
    ) {
        return new HistoricalBaseRate(
                sourceDate,
                YearMonth.from(sourceDate),
                sourceDate.withDayOfMonth(1),
                "HOURLY",
                currency,
                hourlyRateMinor,
                null
        );
    }
}
