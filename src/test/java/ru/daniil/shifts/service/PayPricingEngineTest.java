package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.PayPricingEngine.PremiumComponent;
import ru.daniil.shifts.service.PayPricingEngine.PricingSlice;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayPricingEngineTest {

    private final PayPricingEngine engine =
            new PayPricingEngine();

    @Test
    void callerSuppliedOverlappingPremiumsStackWithoutHardcodedLaborLaw() {
        var result =
                engine.price(
                        100_000L,
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of(
                                                new PremiumComponent(
                                                        "OVERTIME_TIER_1",
                                                        5_000
                                                ),
                                                new PremiumComponent(
                                                        "NIGHT",
                                                        2_000
                                                )
                                        )
                                )
                        )
                );

        assertEquals(
                60,
                result.totalMinutes()
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
                2,
                result.premiums().size()
        );
    }

    @Test
    void noPremiumComponentsMeansExactlyOneTimesBaseAndNothingIsInvented() {
        var result =
                engine.price(
                        120_000L,
                        List.of(
                                new PricingSlice(
                                        90,
                                        List.of()
                                )
                        )
                );

        assertEquals(
                180_000L,
                result.baseAmountMinor()
        );

        assertEquals(
                0L,
                result.premiumAmountMinor()
        );

        assertEquals(
                180_000L,
                result.totalAmountMinor()
        );

        assertTrue(
                result.premiums().isEmpty()
        );
    }

    @Test
    void arbitraryProvenanceSegmentationCannotChangeRoundedMoney() {
        var component =
                new PremiumComponent(
                        "NIGHT",
                        2_000
                );

        var whole =
                engine.price(
                        1L,
                        List.of(
                                new PricingSlice(
                                        60,
                                        List.of(component)
                                )
                        )
                );

        var split =
                engine.price(
                        1L,
                        List.of(
                                new PricingSlice(
                                        30,
                                        List.of(component)
                                ),
                                new PricingSlice(
                                        30,
                                        List.of(component)
                                )
                        )
                );

        assertEquals(
                whole.baseAmountMinor(),
                split.baseAmountMinor()
        );

        assertEquals(
                whole.premiumAmountMinor(),
                split.premiumAmountMinor()
        );

        assertEquals(
                whole.totalAmountMinor(),
                split.totalAmountMinor()
        );

        assertEquals(
                1L,
                split.baseAmountMinor()
        );
    }

    @Test
    void samePremiumIdentityIsAggregatedBeforeHalfUpRounding() {
        var result =
                engine.price(
                        1L,
                        List.of(
                                new PricingSlice(
                                        30,
                                        List.of(
                                                new PremiumComponent(
                                                        "OVERTIME",
                                                        10_000
                                                )
                                        )
                                ),
                                new PricingSlice(
                                        30,
                                        List.of(
                                                new PremiumComponent(
                                                        "OVERTIME",
                                                        10_000
                                                )
                                        )
                                )
                        )
                );

        assertEquals(
                1L,
                result.baseAmountMinor()
        );

        assertEquals(
                1L,
                result.premiumAmountMinor()
        );

        assertEquals(
                2L,
                result.totalAmountMinor()
        );

        assertEquals(
                60,
                result.premiums()
                        .get(0)
                        .minutes()
        );
    }

    @Test
    void duplicatePremiumInsideOneMinuteDomainFailsClosed() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                engine.price(
                                        100_000L,
                                        List.of(
                                                new PricingSlice(
                                                        60,
                                                        List.of(
                                                                new PremiumComponent(
                                                                        "HOLIDAY",
                                                                        10_000
                                                                ),
                                                                new PremiumComponent(
                                                                        "HOLIDAY",
                                                                        10_000
                                                                )
                                                        )
                                                )
                                        )
                                )
                );

        assertTrue(
                error.getMessage()
                        .contains(
                                "Duplicate premium component"
                        )
        );
    }

    @Test
    void invalidBaseRateAndInvalidSliceShapeFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        engine.price(
                                0L,
                                List.of()
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PricingSlice(
                                0,
                                List.of()
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PremiumComponent(
                                "NIGHT",
                                -1
                        )
        );
    }
}
