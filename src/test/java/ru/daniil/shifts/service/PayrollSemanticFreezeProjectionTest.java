package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;

import static org.junit.jupiter.api.Assertions.*;

class PayrollSemanticFreezeProjectionTest {

    @Test
    void basePayOnlyIsCompleteAndMachineClassified() {
        var result =
                PayrollSemanticFreezeProjection.project(
                        new PayrollSemanticFreezeProjection.Source(
                                6_054_800L,
                                0L,
                                0L,
                                0L,
                                0L
                        )
                );

        assertTrue(
                result.complete()
        );

        assertEquals(
                0L,
                result.unclassifiedAmountMinor()
        );

        assertEquals(
                6_054_800L,
                result.classifiedAmountMinor()
        );

        assertEquals(
                1,
                result.classifiedLines().size()
        );

        assertEquals(
                PayrollEarningKind.BASE_PAY,
                result.classifiedLines()
                        .get(
                                0
                        )
                        .earningKind()
        );
    }

    @Test
    void zeroEarningMonthIsCompleteWithoutSyntheticZeroLine() {
        var result =
                PayrollSemanticFreezeProjection.project(
                        new PayrollSemanticFreezeProjection.Source(
                                0L,
                                0L,
                                0L,
                                0L,
                                0L
                        )
                );

        assertTrue(
                result.complete()
        );

        assertTrue(
                result.classifiedLines()
                        .isEmpty()
        );

        assertEquals(
                0L,
                result.totalEarningSourceAmountMinor()
        );
    }

    @Test
    void everyCurrentlyUnclassifiedPositiveSourceBlocksCompleteness() {
        long[][] cases = {
                {100L, 0L, 0L, 0L},
                {0L, 200L, 0L, 0L},
                {0L, 0L, 300L, 0L},
                {0L, 0L, 0L, 400L},
        };

        for (long[] values :
                cases) {

            var result =
                    PayrollSemanticFreezeProjection.project(
                            new PayrollSemanticFreezeProjection.Source(
                                    1_000L,
                                    values[0],
                                    values[1],
                                    values[2],
                                    values[3]
                            )
                    );

            assertFalse(
                    result.complete()
            );

            assertEquals(
                    values[0]
                            + values[1]
                            + values[2]
                            + values[3],
                    result.unclassifiedAmountMinor()
            );
        }
    }

    @Test
    void unclassifiedSourcesAreSummedWithoutLosingBasePay() {
        var source =
                new PayrollSemanticFreezeProjection.Source(
                        1_000L,
                        100L,
                        0L,
                        200L,
                        300L,
                        java.util.List.of(
                                new PayrollSemanticFreezeProjection.ComponentLine(
                                        0,
                                        PayrollEarningKind.HARMFUL_CONDITIONS,
                                        100L
                                ),
                                new PayrollSemanticFreezeProjection.ComponentLine(
                                        1,
                                        null,
                                        200L
                                )
                        ),
                        400L
                );

        var result =
                PayrollSemanticFreezeProjection.project(
                        source
                );

        assertEquals(
                1_100L,
                result.classifiedAmountMinor()
        );

        assertEquals(
                900L,
                result.unclassifiedAmountMinor()
        );

        assertEquals(
                2_000L,
                result.totalEarningSourceAmountMinor()
        );

        assertEquals(
                source.totalEarningSourceAmountMinor(),
                result.totalEarningSourceAmountMinor()
        );

        assertEquals(
                2,
                result.classifiedLines()
                        .size()
        );

        assertEquals(
                PayrollEarningKind.HARMFUL_CONDITIONS,
                result.classifiedLines()
                        .get(1)
                        .earningKind()
        );

        assertEquals(
                100L,
                result.classifiedLines()
                        .get(1)
                        .amountMinor()
        );
    }

    @Test
    void negativeMoneySourceIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSemanticFreezeProjection.Source(
                                1L,
                                -1L,
                                0L,
                                0L,
                                0L
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSemanticFreezeProjection.Source(
                                0L,
                                0L,
                                0L,
                                0L,
                                100L,
                                java.util.List.of(
                                        new PayrollSemanticFreezeProjection.ComponentLine(
                                                0,
                                                PayrollEarningKind.HARMFUL_CONDITIONS,
                                                99L
                                        )
                                ),
                                0L
                        )
        );

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSemanticFreezeProjection.ComponentLine(
                                0,
                                PayrollEarningKind.BASE_PAY,
                                1L
                        )
        );

        var zeroUnclassified =
                PayrollSemanticFreezeProjection.project(
                        new PayrollSemanticFreezeProjection.Source(
                                0L,
                                0L,
                                0L,
                                0L,
                                0L,
                                java.util.List.of(
                                        new PayrollSemanticFreezeProjection.ComponentLine(
                                                0,
                                                null,
                                                0L
                                        )
                                ),
                                0L
                        )
                );

        assertTrue(
                zeroUnclassified.complete()
        );

        assertEquals(
                0L,
                zeroUnclassified.unclassifiedAmountMinor()
        );

        assertTrue(
                zeroUnclassified.classifiedLines()
                        .isEmpty()
        );
    }

    @Test
    void provenOrdinaryNightPremiumBecomesSemanticEarning() {
        var result =
                PayrollSemanticFreezeProjection.project(
                        new PayrollSemanticFreezeProjection.Source(
                                1_000L,
                                300L,
                                120L,
                                0L,
                                0L,
                                0L
                        )
                );

        assertEquals(
                1_120L,
                result.classifiedAmountMinor()
        );

        assertEquals(
                180L,
                result.unclassifiedAmountMinor()
        );

        assertEquals(
                2,
                result.classifiedLines().size()
        );

        assertEquals(
                ru.daniil.shifts.model.PayrollEarningKind.NIGHT_PREMIUM,
                result.classifiedLines()
                        .get(1)
                        .earningKind()
        );

        assertEquals(
                120L,
                result.classifiedLines()
                        .get(1)
                        .amountMinor()
        );
    }

    @Test
    void provenNightCannotExceedOrdinaryPremiumAggregate() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PayrollSemanticFreezeProjection.Source(
                                1_000L,
                                100L,
                                101L,
                                0L,
                                0L,
                                0L
                        )
        );
    }

}
