package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.HistoricalCompensationRateService.HistoricalBaseRate;
import ru.daniil.shifts.service.OvertimeSettlementPricingSourceService.SettlementPricingSource;
import ru.daniil.shifts.service.OvertimeSettlementPricingSourceService.SourcePiece;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OvertimeSettlementPricingServiceTest {

    private final OvertimeSettlementPricingSourceService sourceService =
            mock(
                    OvertimeSettlementPricingSourceService.class
            );

    private final HistoricalCompensationRateService historicalRates =
            mock(
                    HistoricalCompensationRateService.class
            );

    private final PayPricingEngine pricingEngine =
            new PayPricingEngine();

    private final OvertimeSettlementPricingService service =
            new OvertimeSettlementPricingService(
                    sourceService,
                    historicalRates,
                    pricingEngine
            );

    private final AppUser user =
            new AppUser(
                    "settlement-money-user",
                    "{noop}unused"
            );

    @Test
    void singleHistoricalSourceProducesBasePremiumAndTotalMoney() {
        long settlementId = 1L;

        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        10
                );

        SourcePiece piece =
                piece(
                        10L,
                        20L,
                        sourceDate,
                        60,
                        0,
                        480,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of(
                                                new PremiumComponent(
                                                        "NIGHT",
                                                        2_000
                                                ),
                                                new PremiumComponent(
                                                        "OT_TIER_1",
                                                        5_000
                                                )
                                        )
                                )
                        )
                );

        when(
                sourceService.project(
                        user,
                        settlementId
                )
        ).thenReturn(
                source(
                        settlementId,
                        60,
                        List.of(piece)
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
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        var result =
                service.price(
                        user,
                        settlementId
                );

        assertEquals(
                "RUB",
                result.currencyCode()
        );

        assertEquals(
                60,
                result.minutes()
        );

        assertEquals(
                100_000L,
                result.baseAmountMinor()
        );

        assertEquals(
                70_000L,
                result.premiumAmountMinor()
        );

        assertEquals(
                170_000L,
                result.totalAmountMinor()
        );

        assertEquals(
                1,
                result.rateBuckets()
                        .size()
        );

        assertEquals(
                1,
                result.sources()
                        .size()
        );
    }

    @Test
    void equalRatePiecesAreAggregatedBeforeRoundingRegardlessOfProvenanceShape() {
        long settlementId = 2L;

        LocalDate firstDate =
                LocalDate.of(
                        2026,
                        8,
                        10
                );

        LocalDate secondDate =
                LocalDate.of(
                        2026,
                        8,
                        11
                );

        when(
                sourceService.project(
                        user,
                        settlementId
                )
        ).thenReturn(
                source(
                        settlementId,
                        60,
                        List.of(
                                piece(
                                        1L,
                                        10L,
                                        firstDate,
                                        30,
                                        0,
                                        480,
                                        LocalDate.of(
                                                2026,
                                                8,
                                                1
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        30,
                                                        List.of()
                                                )
                                        )
                                ),
                                piece(
                                        2L,
                                        11L,
                                        secondDate,
                                        30,
                                        30,
                                        510,
                                        LocalDate.of(
                                                2026,
                                                8,
                                                1
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        30,
                                                        List.of()
                                                )
                                        )
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
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        1L,
                        null
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
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        1L,
                        null
                )
        );

        var result =
                service.price(
                        user,
                        settlementId
                );

        /*
         * Correct:
         * 60 minutes * 1 minor/hour = 1 minor.
         *
         * Wrong piece-by-piece rounding would produce 1 + 1 = 2.
         */
        assertEquals(
                1L,
                result.baseAmountMinor()
        );

        assertEquals(
                1L,
                result.totalAmountMinor()
        );

        assertEquals(
                1,
                result.rateBuckets()
                        .size()
        );

        assertEquals(
                60,
                result.rateBuckets()
                        .get(0)
                        .minutes()
        );
    }

    @Test
    void differentHistoricalRatesInSameCurrencyPriceSeparatelyThenSum() {
        long settlementId = 3L;

        LocalDate july =
                LocalDate.of(
                        2026,
                        7,
                        31
                );

        LocalDate august =
                LocalDate.of(
                        2026,
                        8,
                        15
                );

        when(
                sourceService.project(
                        user,
                        settlementId
                )
        ).thenReturn(
                source(
                        settlementId,
                        120,
                        List.of(
                                piece(
                                        1L,
                                        10L,
                                        july,
                                        60,
                                        0,
                                        480,
                                        LocalDate.of(
                                                2026,
                                                6,
                                                1
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        60,
                                                        List.of()
                                                )
                                        )
                                ),
                                piece(
                                        2L,
                                        11L,
                                        august,
                                        60,
                                        0,
                                        480,
                                        LocalDate.of(
                                                2026,
                                                8,
                                                10
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        60,
                                                        List.of()
                                                )
                                        )
                                )
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        july
                )
        ).thenReturn(
                rate(
                        july,
                        LocalDate.of(
                                2026,
                                7,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        august
                )
        ).thenReturn(
                rate(
                        august,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        200_000L,
                        null
                )
        );

        var result =
                service.price(
                        user,
                        settlementId
                );

        assertEquals(
                300_000L,
                result.baseAmountMinor()
        );

        assertEquals(
                300_000L,
                result.totalAmountMinor()
        );

        assertEquals(
                2,
                result.rateBuckets()
                        .size()
        );

        assertEquals(
                List.of(
                        100_000L,
                        200_000L
                ),
                result.rateBuckets()
                        .stream()
                        .map(bucket ->
                                bucket.baseHourlyRateMinor()
                        )
                        .toList()
        );
    }

    @Test
    void historicalCurrencyChangeInsideOneSettlementFailsClosed() {
        long settlementId = 4L;

        LocalDate july =
                LocalDate.of(
                        2026,
                        7,
                        31
                );

        LocalDate august =
                LocalDate.of(
                        2026,
                        8,
                        15
                );

        when(
                sourceService.project(
                        user,
                        settlementId
                )
        ).thenReturn(
                source(
                        settlementId,
                        120,
                        List.of(
                                piece(
                                        1L,
                                        10L,
                                        july,
                                        60,
                                        0,
                                        480,
                                        LocalDate.of(
                                                2026,
                                                6,
                                                1
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        60,
                                                        List.of()
                                                )
                                        )
                                ),
                                piece(
                                        2L,
                                        11L,
                                        august,
                                        60,
                                        0,
                                        480,
                                        LocalDate.of(
                                                2026,
                                                8,
                                                10
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        60,
                                                        List.of()
                                                )
                                        )
                                )
                        )
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        july
                )
        ).thenReturn(
                rate(
                        july,
                        LocalDate.of(
                                2026,
                                7,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        100_000L,
                        null
                )
        );

        when(
                historicalRates.resolve(
                        user,
                        august
                )
        ).thenReturn(
                rate(
                        august,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "EUR",
                        2_000L,
                        null
                )
        );

        ApiException error =
                assertThrows(
                        ApiException.class,
                        () ->
                                service.price(
                                        user,
                                        settlementId
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "разных валютных периодов"
                        )
        );
    }

    @Test
    void salaryRateIdentitySurvivesIntoExplainabilityWithoutBecomingRoundingBoundary() {
        long settlementId = 5L;

        LocalDate sourceDate =
                LocalDate.of(
                        2026,
                        8,
                        20
                );

        when(
                sourceService.project(
                        user,
                        settlementId
                )
        ).thenReturn(
                source(
                        settlementId,
                        60,
                        List.of(
                                piece(
                                        1L,
                                        10L,
                                        sourceDate,
                                        60,
                                        0,
                                        480,
                                        LocalDate.of(
                                                2026,
                                                8,
                                                15
                                        ),
                                        List.of(
                                                new PricingSlice(
                                                        60,
                                                        List.of()
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
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "SALARY",
                        "RUB",
                        47_904L,
                        10_020
                )
        );

        var result =
                service.price(
                        user,
                        settlementId
                );

        var source =
                result.sources()
                        .get(0);

        assertEquals(
                "SALARY",
                source.payMode()
        );

        assertEquals(
                YearMonth.of(
                        2026,
                        8
                ),
                source.compensationSourceMonth()
        );

        assertEquals(
                10_020,
                source.productionNormMinutes()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        15
                ),
                source.pricingEffectiveFrom()
        );

        assertEquals(
                47_904L,
                result.totalAmountMinor()
        );
    }

    private SettlementPricingSource source(
            long settlementId,
            int minutes,
            List<SourcePiece> pieces
    ) {
        return new SettlementPricingSource(
                settlementId,
                LocalDate.of(
                        2026,
                        9,
                        5
                ),
                500L + settlementId,
                minutes,
                minutes,
                pieces
        );
    }

    private SourcePiece piece(
            long allocationId,
            long creditId,
            LocalDate sourceDate,
            int minutes,
            int creditOffset,
            int factualOrdinal,
            LocalDate pricingEffectiveFrom,
            List<PricingSlice> pricingSlices
    ) {
        return new SourcePiece(
                allocationId,
                creditId,
                1000L + allocationId,
                sourceDate,
                minutes,
                false,
                false,
                creditOffset,
                factualOrdinal,
                pricingEffectiveFrom,
                pricingSlices
        );
    }

    private HistoricalBaseRate rate(
            LocalDate sourceDate,
            LocalDate compensationEffectiveFrom,
            String mode,
            String currency,
            long hourlyRate,
            Integer productionNorm
    ) {
        return new HistoricalBaseRate(
                sourceDate,
                YearMonth.from(
                        sourceDate
                ),
                compensationEffectiveFrom,
                mode,
                currency,
                hourlyRate,
                productionNorm
        );
    }
}
