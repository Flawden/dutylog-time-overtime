package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.MonthPremiumProjection;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.PricedRateBucket;
import ru.daniil.shifts.service.OrdinaryWorkPremiumPricingService.SourceDateValuation;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourcePiece;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricedPremium;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrdinaryPremiumPricingFingerprintTest {

    private static final YearMonth MONTH =
            YearMonth.of(
                    2026,
                    8
            );

    private static final LocalDate SOURCE_DATE =
            LocalDate.of(
                    2026,
                    8,
                    18
            );

    @Test
    void deepFingerprintIsDeterministicLowercaseSha256() {
        MonthPremiumProjection projection =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                );

        String first =
                projection.pricingFingerprint();

        String second =
                projection.pricingFingerprint();

        assertNotNull(first);
        assertEquals(first, second);

        assertTrue(
                first.matches(
                        "[0-9a-f]{64}"
                )
        );
    }

    @Test
    void factualActualWorkIdentityChangesFingerprintEvenWhenMoneyDoesNot() {
        String first =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                ).pricingFingerprint();

        String second =
                explicitProjection(
                        78L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                ).pricingFingerprint();

        assertNotEquals(
                first,
                second
        );
    }

    @Test
    void sourceClockEvidenceChangesFingerprint() {
        String first =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                ).pricingFingerprint();

        String second =
                explicitProjection(
                        77L,
                        "2026-08-18T23:00:00Z",
                        "2026-08-19T00:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                ).pricingFingerprint();

        assertNotEquals(
                first,
                second
        );
    }

    @Test
    void effectivePricingIdentityChangesFingerprint() {
        String first =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                ).pricingFingerprint();

        String second =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                10
                        ),
                        "NIGHT",
                        2_000
                ).pricingFingerprint();

        assertNotEquals(
                first,
                second
        );
    }

    @Test
    void resolvedRuleIdentityChangesFingerprintEvenWithSameAggregateMoney() {
        MonthPremiumProjection night =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "NIGHT",
                        2_000
                );

        MonthPremiumProjection custom =
                explicitProjection(
                        77L,
                        "2026-08-18T22:00:00Z",
                        "2026-08-18T23:00:00Z",
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "CUSTOM_NIGHT",
                        2_000
                );

        assertEquals(
                night.premiumAmountMinor(),
                custom.premiumAmountMinor()
        );

        assertNotEquals(
                night.pricingFingerprint(),
                custom.pricingFingerprint()
        );
    }

    @Test
    void planDayEntryIdentityIsPartOfFingerprint() {
        String first =
                planProjection(
                        88L
                ).pricingFingerprint();

        String second =
                planProjection(
                        89L
                ).pricingFingerprint();

        assertNotEquals(
                first,
                second
        );
    }

    @Test
    void pureRegularProjectionHasNoPremiumPricingFingerprint() {
        SourcePiece piece =
                new SourcePiece(
                        SOURCE_DATE,
                        SourceKind.EXPLICIT,
                        77L,
                        null,
                        Instant.parse(
                                "2026-08-18T10:00:00Z"
                        ),
                        Instant.parse(
                                "2026-08-18T11:00:00Z"
                        ),
                        "UTC",
                        60,
                        false,
                        false
                );

        PricingSlice slice =
                new PricingSlice(
                        60,
                        List.of()
                );

        SourceDateValuation valuation =
                new SourceDateValuation(
                        SOURCE_DATE,
                        SourceKind.EXPLICIT,
                        60,
                        0,
                        0,
                        null,
                        MONTH,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        60_000L,
                        null,
                        List.of(piece),
                        List.of(slice)
                );

        MonthPremiumProjection projection =
                new MonthPremiumProjection(
                        MONTH,
                        true,
                        null,
                        List.of(),
                        "RUB",
                        60,
                        60_000L,
                        0L,
                        List.of(
                                new PricedRateBucket(
                                        60_000L,
                                        60,
                                        60_000L,
                                        0L,
                                        List.of()
                                )
                        ),
                        List.of(
                                valuation
                        )
                );

        assertNull(
                projection.pricingFingerprint()
        );
    }

    @Test
    void premiumIdentityWithoutDeepSourceEvidenceFailsClosed() {
        PricingSlice slice =
                new PricingSlice(
                        60,
                        List.of(
                                new PremiumComponent(
                                        "NIGHT",
                                        2_000
                                )
                        )
                );

        /*
         * Compatibility constructor intentionally has no retained deep pieces.
         */
        SourceDateValuation incomplete =
                new SourceDateValuation(
                        SOURCE_DATE,
                        SourceKind.EXPLICIT,
                        60,
                        60,
                        0,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        MONTH,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        60_000L,
                        null
                );

        MonthPremiumProjection projection =
                new MonthPremiumProjection(
                        MONTH,
                        true,
                        null,
                        List.of(),
                        "RUB",
                        60,
                        60_000L,
                        12_000L,
                        List.of(
                                new PricedRateBucket(
                                        60_000L,
                                        60,
                                        60_000L,
                                        12_000L,
                                        List.of(
                                                new PricedPremium(
                                                        "NIGHT",
                                                        2_000,
                                                        60,
                                                        12_000L
                                                )
                                        )
                                )
                        ),
                        List.of(
                                incomplete
                        )
                );

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        projection::pricingFingerprint
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "complete deep source identity"
                        )
        );
    }

    private MonthPremiumProjection explicitProjection(
            long actualId,
            String start,
            String end,
            LocalDate pricingEffectiveFrom,
            String premiumCode,
            int premiumBps
    ) {
        SourcePiece piece =
                new SourcePiece(
                        SOURCE_DATE,
                        SourceKind.EXPLICIT,
                        actualId,
                        null,
                        Instant.parse(start),
                        Instant.parse(end),
                        "UTC",
                        60,
                        true,
                        false
                );

        return projection(
                SourceKind.EXPLICIT,
                piece,
                pricingEffectiveFrom,
                premiumCode,
                premiumBps
        );
    }

    private MonthPremiumProjection planProjection(
            long dayEntryId
    ) {
        SourcePiece piece =
                new SourcePiece(
                        SOURCE_DATE,
                        SourceKind.PLAN_DERIVED,
                        null,
                        dayEntryId,
                        Instant.parse(
                                "2026-08-18T22:00:00Z"
                        ),
                        Instant.parse(
                                "2026-08-18T23:00:00Z"
                        ),
                        "UTC",
                        60,
                        true,
                        false
                );

        return projection(
                SourceKind.PLAN_DERIVED,
                piece,
                LocalDate.of(
                        2026,
                        8,
                        1
                ),
                "NIGHT",
                2_000
        );
    }

    private MonthPremiumProjection projection(
            SourceKind sourceKind,
            SourcePiece piece,
            LocalDate pricingEffectiveFrom,
            String premiumCode,
            int premiumBps
    ) {
        PricingSlice slice =
                new PricingSlice(
                        60,
                        List.of(
                                new PremiumComponent(
                                        premiumCode,
                                        premiumBps
                                )
                        )
                );

        long premiumAmount =
                12_000L;

        SourceDateValuation valuation =
                new SourceDateValuation(
                        SOURCE_DATE,
                        sourceKind,
                        60,
                        60,
                        0,
                        pricingEffectiveFrom,
                        MONTH,
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        "HOURLY",
                        "RUB",
                        60_000L,
                        null,
                        List.of(piece),
                        List.of(slice)
                );

        return new MonthPremiumProjection(
                MONTH,
                true,
                null,
                List.of(),
                "RUB",
                60,
                60_000L,
                premiumAmount,
                List.of(
                        new PricedRateBucket(
                                60_000L,
                                60,
                                60_000L,
                                premiumAmount,
                                List.of(
                                        new PricedPremium(
                                                premiumCode,
                                                premiumBps,
                                                60,
                                                premiumAmount
                                        )
                                )
                        )
                ),
                List.of(
                        valuation
                )
        );
    }
}
