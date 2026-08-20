package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrdinaryWorkPremiumPricingServiceTest {

    private final OrdinaryWorkPremiumSourceService sourceService =
            mock(OrdinaryWorkPremiumSourceService.class);

    private final PayPricingPolicyService pricingPolicy =
            mock(PayPricingPolicyService.class);

    private final HistoricalCompensationRateService historicalRates =
            mock(HistoricalCompensationRateService.class);

    private final PayPricingEngine pricingEngine =
            new PayPricingEngine();

    private final OrdinaryWorkPremiumPricingService service =
            new OrdinaryWorkPremiumPricingService(
                    sourceService,
                    pricingPolicy,
                    historicalRates,
                    pricingEngine
            );

    private final AppUser user =
            new AppUser(
                    "ordinary-premium-money-user",
                    "{noop}unused"
            );

    private final YearMonth month =
            YearMonth.of(
                    2026,
                    8
            );

    @Test
    void nightPremiumIsDeltaOnlyAndDoesNotRepayOrdinaryBase() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        18
                );

        SourcePiece piece =
                explicitPiece(
                        sourceDate,
                        60,
                        true,
                        false
                );

        stubMonth(
                Map.of(
                        sourceDate,
                        readySource(
                                sourceDate,
                                List.of(piece)
                        )
                )
        );

        when(
                pricingPolicy.resolveForSourceDate(
                        user,
                        sourceDate,
                        List.of(
                                piece.consumedSlice()
                        )
                )
        ).thenReturn(
                policy(
                        sourceDate,
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of(
                                                new PremiumComponent(
                                                        "NIGHT",
                                                        2_000
                                                )
                                        )
                                )
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        sourceDate
                )
        ).thenReturn(
                rate(
                        sourceDate,
                        "RUB",
                        60_000L
                )
        );

        var result =
                service.priceMonth(
                        user,
                        month
                );

        assertTrue(result.ready());
        assertEquals("RUB", result.currencyCode());
        assertEquals(60, result.ordinaryMinutes());

        /*
         * One ordinary hour at 600.00:
         * reference base = 600.00
         * NIGHT +20%    = 120.00
         *
         * Payroll must later add only the latter.
         */
        assertEquals(
                60_000L,
                result.referenceBaseAmountMinor()
        );

        assertEquals(
                12_000L,
                result.premiumAmountMinor()
        );

        assertNotEquals(
                72_000L,
                result.premiumAmountMinor(),
                "Ordinary base must never be paid a second time"
        );
    }

    @Test
    void baseOnlyPricingProducesZeroAdditivePremium() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        19
                );

        SourcePiece piece =
                explicitPiece(
                        sourceDate,
                        60,
                        false,
                        false
                );

        stubMonth(
                Map.of(
                        sourceDate,
                        readySource(
                                sourceDate,
                                List.of(piece)
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        sourceDate
                )
        ).thenReturn(
                rate(
                        sourceDate,
                        "RUB",
                        60_000L
                )
        );

        var result =
                service.priceMonth(
                        user,
                        month
                );

        assertTrue(result.ready());
        assertEquals(60_000L, result.referenceBaseAmountMinor());
        assertEquals(0L, result.premiumAmountMinor());

        verify(
                pricingPolicy,
                never()
        ).resolveForSourceDate(
                eq(user),
                eq(sourceDate),
                anyList()
        );
    }

    @Test
    void equalRateFragmentsShareOneMonthlyRoundingBoundary() {
        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        SourcePiece first =
                explicitPiece(
                        sourceDate,
                        1,
                        true,
                        false
                );

        SourcePiece second =
                explicitPiece(
                        sourceDate,
                        1,
                        true,
                        false
                );

        stubMonth(
                Map.of(
                        sourceDate,
                        readySource(
                                sourceDate,
                                List.of(
                                        first,
                                        second
                                )
                        )
                )
        );

        when(
                pricingPolicy.resolveForSourceDate(
                        user,
                        sourceDate,
                        List.of(
                                first.consumedSlice(),
                                second.consumedSlice()
                        )
                )
        ).thenReturn(
                policy(
                        sourceDate,
                        List.of(
                                new PricingSlice(
                                        1,
                                        List.of(
                                                new PremiumComponent(
                                                        "NIGHT",
                                                        5_000
                                                )
                                        )
                                ),
                                new PricingSlice(
                                        1,
                                        List.of(
                                                new PremiumComponent(
                                                        "NIGHT",
                                                        5_000
                                                )
                                        )
                                )
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        sourceDate
                )
        ).thenReturn(
                rate(
                        sourceDate,
                        "RUB",
                        50L
                )
        );

        var result =
                service.priceMonth(
                        user,
                        month
                );

        /*
         * 50 minor/hour × 2 min × 0.5 / 60 = 0.833...
         * Monthly economic aggregation rounds once => 1 minor.
         *
         * Pricing each 1-minute fragment separately would incorrectly yield 0.
         */
        assertEquals(
                1L,
                result.premiumAmountMinor()
        );

        assertEquals(
                1,
                result.rateBuckets()
                        .size()
        );
    }

    @Test
    void blockedSourceStopsBeforePolicyRateAndMoney() {
        LocalDate blockedDate =
                LocalDate.of(
                        2026,
                        8,
                        21
                );

        stubMonth(
                Map.of(
                        blockedDate,
                        new OrdinaryPremiumSource(
                                blockedDate,
                                SourceKind.PLAN_DERIVED,
                                120,
                                false,
                                OrdinaryWorkPremiumSourceService.BLOCK_CLOCK_QUANTITY,
                                List.of()
                        )
                )
        );

        var result =
                service.priceMonth(
                        user,
                        month
                );

        assertFalse(result.ready());

        assertEquals(
                "ORDINARY_PREMIUM_SOURCE_NOT_READY",
                result.blockingReason()
        );

        assertEquals(1, result.blockers().size());
        assertEquals(blockedDate, result.blockers().get(0).date());
        assertEquals(0L, result.premiumAmountMinor());

        verifyNoInteractions(
                pricingPolicy,
                historicalRates
        );
    }

    @Test
    void zeroOrdinaryMonthDoesNotRequirePricingOrCompensation() {
        stubMonth(Map.of());

        var result =
                service.priceMonth(
                        user,
                        month
                );

        assertTrue(result.ready());
        assertEquals(0, result.ordinaryMinutes());
        assertEquals(0L, result.referenceBaseAmountMinor());
        assertEquals(0L, result.premiumAmountMinor());
        assertNull(result.currencyCode());

        verifyNoInteractions(
                pricingPolicy,
                historicalRates
        );
    }

    @Test
    void differentHistoricalCurrenciesFailClosed() {
        LocalDate firstDate =
                LocalDate.of(
                        2026,
                        8,
                        22
                );

        LocalDate secondDate =
                LocalDate.of(
                        2026,
                        8,
                        23
                );

        SourcePiece first =
                explicitPiece(
                        firstDate,
                        60,
                        false,
                        false
                );

        SourcePiece second =
                explicitPiece(
                        secondDate,
                        60,
                        false,
                        false
                );

        stubMonth(
                Map.of(
                        firstDate,
                        readySource(
                                firstDate,
                                List.of(first)
                        ),
                        secondDate,
                        readySource(
                                secondDate,
                                List.of(second)
                        )
                )
        );

        when(
                pricingPolicy.resolveForSourceDate(
                        eq(user),
                        eq(firstDate),
                        anyList()
                )
        ).thenReturn(
                policy(
                        firstDate,
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of()
                                )
                        )
                )
        );

        when(
                pricingPolicy.resolveForSourceDate(
                        eq(user),
                        eq(secondDate),
                        anyList()
                )
        ).thenReturn(
                policy(
                        secondDate,
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of()
                                )
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        firstDate
                )
        ).thenReturn(
                rate(
                        firstDate,
                        "RUB",
                        60_000L
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        secondDate
                )
        ).thenReturn(
                rate(
                        secondDate,
                        "USD",
                        60_000L
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.priceMonth(
                                        user,
                                        month
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "разные валюты"
                        )
        );
    }

    private void stubMonth(
            Map<LocalDate, OrdinaryPremiumSource> overrides
    ) {
        when(
                sourceService.project(
                        eq(user),
                        any(LocalDate.class)
                )
        ).thenAnswer(invocation -> {
            LocalDate date =
                    invocation.getArgument(1);

            return overrides.getOrDefault(
                    date,
                    new OrdinaryPremiumSource(
                            date,
                            SourceKind.PLAN_DERIVED,
                            0,
                            true,
                            null,
                            List.of()
                    )
            );
        });
    }

    private OrdinaryPremiumSource readySource(
            LocalDate date,
            List<SourcePiece> pieces
    ) {
        int minutes =
                pieces.stream()
                        .mapToInt(
                                SourcePiece::minutes
                        )
                        .sum();

        return new OrdinaryPremiumSource(
                date,
                pieces.get(0).sourceKind(),
                minutes,
                true,
                null,
                pieces
        );
    }

    private SourcePiece explicitPiece(
            LocalDate date,
            int minutes,
            boolean night,
            boolean holiday
    ) {
        return new SourcePiece(
                date,
                SourceKind.EXPLICIT,
                77L,
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
                LocalDate.of(
                        2026,
                        8,
                        1
                ),
                new RuleSet(
                        List.of()
                ),
                slices
        );
    }

    private HistoricalBaseRate rate(
            LocalDate sourceDate,
            String currency,
            long hourly
    ) {
        return new HistoricalBaseRate(
                sourceDate,
                YearMonth.from(
                        sourceDate
                ),
                LocalDate.of(
                        2026,
                        8,
                        1
                ),
                "HOURLY",
                currency,
                hourly,
                null
        );
    }
}
