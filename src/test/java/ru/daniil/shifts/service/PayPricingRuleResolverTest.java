package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.PayPricingRuleResolver.ConsumedSlice;
import ru.daniil.shifts.service.PayPricingRuleResolver.Dimension;
import ru.daniil.shifts.service.PayPricingRuleResolver.Rule;
import ru.daniil.shifts.service.PayPricingRuleResolver.RuleSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayPricingRuleResolverTest {

    private final PayPricingRuleResolver resolver =
            new PayPricingRuleResolver();

    @Test
    void independentNightHolidayAndOvertimeDimensionsStackWhenRulesAllowIt() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                rule(
                                        "NIGHT",
                                        Dimension.NIGHT,
                                        2_000
                                ),
                                rule(
                                        "HOLIDAY",
                                        Dimension.HOLIDAY,
                                        10_000
                                ),
                                overtime(
                                        "OT",
                                        5_000,
                                        0,
                                        null
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        60,
                                        true,
                                        true,
                                        0
                                )
                        )
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                List.of(
                        "HOLIDAY",
                        "NIGHT",
                        "OT"
                ),
                result.get(0)
                        .components()
                        .stream()
                        .map(component ->
                                component.code()
                        )
                        .toList()
        );
    }

    @Test
    void exclusiveGroupChoosesHighestConfiguredPremiumWithoutLaborLawBranching() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                new Rule(
                                        "HOLIDAY",
                                        Dimension.HOLIDAY,
                                        10_000,
                                        0,
                                        null,
                                        "PRIMARY"
                                ),
                                new Rule(
                                        "OT",
                                        Dimension.OVERTIME,
                                        5_000,
                                        0,
                                        null,
                                        "PRIMARY"
                                ),
                                rule(
                                        "NIGHT",
                                        Dimension.NIGHT,
                                        2_000
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        60,
                                        true,
                                        true,
                                        0
                                )
                        )
                );

        assertEquals(
                List.of(
                        "HOLIDAY",
                        "NIGHT"
                ),
                result.get(0)
                        .components()
                        .stream()
                        .map(component ->
                                component.code()
                        )
                        .toList()
        );
    }

    @Test
    void overtimeTierBoundarySplitsOneConsumedProvenancePiece() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                overtime(
                                        "OT_TIER_1",
                                        5_000,
                                        0,
                                        120
                                ),
                                overtime(
                                        "OT_TIER_2",
                                        10_000,
                                        120,
                                        null
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        90,
                                        false,
                                        false,
                                        90
                                )
                        )
                );

        assertEquals(
                2,
                result.size()
        );

        assertEquals(
                30,
                result.get(0).minutes()
        );

        assertEquals(
                "OT_TIER_1",
                result.get(0)
                        .components()
                        .get(0)
                        .code()
        );

        assertEquals(
                60,
                result.get(1).minutes()
        );

        assertEquals(
                "OT_TIER_2",
                result.get(1)
                        .components()
                        .get(0)
                        .code()
        );
    }

    @Test
    void homogeneousAdjacentPiecesMergeBackIntoOneEconomicSlice() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                rule(
                                        "NIGHT",
                                        Dimension.NIGHT,
                                        2_000
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        30,
                                        true,
                                        false,
                                        0
                                ),
                                new ConsumedSlice(
                                        30,
                                        true,
                                        false,
                                        30
                                )
                        )
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                60,
                result.get(0).minutes()
        );
    }

    @Test
    void noApplicableRulesProducesBaseOnlyPricingSlice() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                rule(
                                        "NIGHT",
                                        Dimension.NIGHT,
                                        2_000
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        45,
                                        false,
                                        false,
                                        0
                                )
                        )
                );

        assertEquals(
                1,
                result.size()
        );

        assertTrue(
                result.get(0)
                        .components()
                        .isEmpty()
        );
    }

    @Test
    void ordinaryNightHolidaySliceNeverReceivesOvertimeRuleOrTierSplit() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                rule(
                                        "NIGHT",
                                        Dimension.NIGHT,
                                        2_000
                                ),
                                rule(
                                        "HOLIDAY",
                                        Dimension.HOLIDAY,
                                        10_000
                                ),
                                overtime(
                                        "OT_TIER_1",
                                        5_000,
                                        0,
                                        120
                                ),
                                overtime(
                                        "OT_TIER_2",
                                        10_000,
                                        120,
                                        null
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        180,
                                        true,
                                        true,
                                        false,
                                        0
                                )
                        )
                );

        /*
         * Ordinary work must not be split at OT tier 120.
         */
        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                180,
                result.get(0)
                        .minutes()
        );

        assertEquals(
                List.of(
                        "HOLIDAY",
                        "NIGHT"
                ),
                result.get(0)
                        .components()
                        .stream()
                        .map(component ->
                                component.code()
                        )
                        .toList()
        );
    }

    @Test
    void fourArgumentConsumedSliceRetainsExistingSettlementOvertimeSemantics() {
        RuleSet rules =
                new RuleSet(
                        List.of(
                                overtime(
                                        "OT",
                                        5_000,
                                        0,
                                        null
                                )
                        )
                );

        var result =
                resolver.resolve(
                        rules,
                        List.of(
                                new ConsumedSlice(
                                        60,
                                        false,
                                        false,
                                        0
                                )
                        )
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                List.of("OT"),
                result.get(0)
                        .components()
                        .stream()
                        .map(component ->
                                component.code()
                        )
                        .toList()
        );
    }

    @Test
    void ordinarySliceRejectsMeaninglessOvertimeOffset() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ConsumedSlice(
                                60,
                                true,
                                false,
                                false,
                                30
                        )
        );
    }

    @Test
    void ruleShapesFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Rule(
                                "BAD",
                                Dimension.NIGHT,
                                1_000,
                                5,
                                null,
                                null
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        overtime(
                                "BAD",
                                1_000,
                                120,
                                120
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ConsumedSlice(
                                0,
                                false,
                                false,
                                0
                        )
        );
    }

    private Rule rule(
            String code,
            Dimension dimension,
            int premiumBps
    ) {
        return new Rule(
                code,
                dimension,
                premiumBps,
                0,
                null,
                null
        );
    }

    private Rule overtime(
            String code,
            int premiumBps,
            int from,
            Integer toExclusive
    ) {
        return new Rule(
                code,
                Dimension.OVERTIME,
                premiumBps,
                from,
                toExclusive,
                null
        );
    }
}
