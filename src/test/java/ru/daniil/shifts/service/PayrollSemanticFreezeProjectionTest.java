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
                        200L,
                        300L,
                        400L
                );

        var result =
                PayrollSemanticFreezeProjection.project(
                        source
                );

        assertEquals(
                1_000L,
                result.classifiedAmountMinor()
        );

        assertEquals(
                1_000L,
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
    }
}
