package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollSnapshotComponentLine;
import ru.daniil.shifts.service.PayrollCombinationEpisodeFactService.EpisodeFact;
import ru.daniil.shifts.service.PayrollSemanticFreezeProjection.SemanticLine;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayrollCompensationComponentSemanticProvenanceTest {

    private final PayrollCompensationComponentSemanticProvenance service =
            new PayrollCompensationComponentSemanticProvenance();

    @Test
    void harmfulPercentOfEarnedBaseSplitsByProvenBaseMoneyPeriodsExactly() {
        List<SemanticLine> base = List.of(
                baseLine(1_729_943L, 2_880L, 1, 10),
                baseLine(756_850L, 1_320L, 11, 12),
                baseLine(3_459_886L, 5_760L, 14, 31)
        );

        long referenceBase =
                base.stream()
                        .mapToLong(SemanticLine::amountMinor)
                        .sum();

        var result = service.lines(
                List.of(
                        component(
                                0,
                                PayrollEarningKind.HARMFUL_CONDITIONS,
                                "PERCENT_OF_BASE",
                                "EARNED_BASE_PAY",
                                400,
                                referenceBase,
                                237_867L
                        )
                ),
                base
        );

        assertNotNull(result);
        assertEquals(1, result.size());

        var detailed = result.get(0).detailedLines();

        assertNotNull(detailed);
        assertEquals(3, detailed.size());
        assertEquals(
                List.of(69_198L, 30_274L, 138_395L),
                detailed.stream()
                        .map(SemanticLine::amountMinor)
                        .toList()
        );

        assertEquals(
                237_867L,
                detailed.stream()
                        .mapToLong(SemanticLine::amountMinor)
                        .sum()
        );
    }

    @Test
    void harmfulCopiesOnlyBaseEarningPeriodAndNeverBaseQualifiedQuantity() {
        var base = baseLine(
                1_729_943L,
                2_880L,
                1,
                10
        );

        var result = service.lines(
                List.of(
                        component(
                                0,
                                PayrollEarningKind.HARMFUL_CONDITIONS,
                                "PERCENT_OF_BASE",
                                "EARNED_BASE_PAY",
                                400,
                                1_729_943L,
                                69_198L
                        )
                ),
                List.of(base)
        );

        var harmful = result.get(0)
                .detailedLines()
                .get(0);

        assertNull(harmful.qualifiedQuantity());
        assertEquals(base.earningPeriodFrom(), harmful.earningPeriodFrom());
        assertEquals(base.earningPeriodTo(), harmful.earningPeriodTo());
        assertNull(harmful.coverageFrom());
        assertNull(harmful.coverageTo());
    }

    @Test
    void harmfulStaysAggregateOnlyWithoutDetailedBasePeriodTruth() {
        var component = component(
                0,
                PayrollEarningKind.HARMFUL_CONDITIONS,
                "PERCENT_OF_BASE",
                "EARNED_BASE_PAY",
                400,
                1_000_000L,
                40_000L
        );

        var withoutBase = service.lines(
                List.of(component),
                null
        );

        assertNull(withoutBase.get(0).detailedLines());

        var aggregateBase = service.lines(
                List.of(component),
                List.of(
                        new SemanticLine(
                                PayrollEarningKind.BASE_PAY,
                                1_000_000L
                        )
                )
        );

        assertNull(aggregateBase.get(0).detailedLines());
    }

    @Test
    void nonHarmfulGenericKindsStayAggregateOnlyEvenOnEarnedBaseFormula() {
        for (PayrollEarningKind kind : List.of(
                PayrollEarningKind.COMBINATION,
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollEarningKind.REGIONAL_COEFFICIENT
        )) {
            var result = service.lines(
                    List.of(
                            component(
                                    0,
                                    kind,
                                    "PERCENT_OF_BASE",
                                    "EARNED_BASE_PAY",
                                    1_500,
                                    1_000_000L,
                                    150_000L
                            )
                    ),
                    List.of(
                            baseLine(
                                    1_000_000L,
                                    480L,
                                    1,
                                    31
                            )
                    )
            );

            assertNull(
                    result.get(0).detailedLines(),
                    kind.name()
            );
        }
    }


    @Test
    void combinationUsesOnlyExplicitObservedEpisodeFactsForExactSourceLines() {
        var component = component(
                0,
                PayrollEarningKind.COMBINATION,
                "FIXED_AMOUNT",
                null,
                null,
                0L,
                801_886L
        );

        var result = service.lines(
                List.of(component),
                null,
                List.of(
                        combinationFact(
                                10L,
                                42L,
                                1,
                                15,
                                4_740L,
                                716_062L,
                                2_500
                        ),
                        combinationFact(
                                11L,
                                42L,
                                29,
                                30,
                                480L,
                                85_824L,
                                2_500
                        )
                ),
                "RUB"
        );

        var detailed = result.get(0).detailedLines();

        assertNotNull(detailed);
        assertEquals(2, detailed.size());
        assertEquals(801_886L, detailed.stream()
                .mapToLong(SemanticLine::amountMinor)
                .sum());
        assertEquals(4_740L, detailed.get(0).qualifiedQuantity().value());
        assertEquals(
                ru.daniil.shifts.model.PayrollQuantityUnit.MINUTES,
                detailed.get(0).qualifiedQuantity().unit()
        );
        assertEquals(LocalDate.of(2026, 6, 1), detailed.get(0).earningPeriodFrom());
        assertEquals(LocalDate.of(2026, 6, 15), detailed.get(0).earningPeriodTo());
        assertEquals(LocalDate.of(2026, 6, 29), detailed.get(1).earningPeriodFrom());
        assertNull(detailed.get(0).coverageFrom());
        assertNull(detailed.get(0).coverageTo());
    }

    @Test
    void explicitCombinationFactWithoutMatchingFrozenComponentFailsClosed() {
        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(
                                component(
                                        0,
                                        PayrollEarningKind.COMBINATION,
                                        "FIXED_AMOUNT",
                                        null,
                                        null,
                                        0L,
                                        100_000L
                                )
                        ),
                        null,
                        List.of(
                                combinationFact(
                                        10L,
                                        99L,
                                        1,
                                        10,
                                        480L,
                                        100_000L,
                                        2_500
                                )
                        ),
                        "RUB"
                )
        );
    }

    @Test
    void combinationFactsFailClosedOnMoneyOrCurrencyButDoNotInferExternalBaseFromLocalFormula() {
        var component = component(
                0,
                PayrollEarningKind.COMBINATION,
                "PERCENT_OF_BASE",
                "NOMINAL_SALARY",
                2_500,
                9_999_999L,
                100_000L
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(component),
                        null,
                        List.of(
                                combinationFact(
                                        10L,
                                        42L,
                                        1,
                                        10,
                                        480L,
                                        99_999L,
                                        2_500
                                )
                        ),
                        "RUB"
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(component),
                        null,
                        List.of(
                                new EpisodeFact(
                                        10L,
                                        42L,
                                        LocalDate.of(2026, 6, 1),
                                        LocalDate.of(2026, 6, 10),
                                        480L,
                                        100_000L,
                                        "USD",
                                        2_500
                                )
                        ),
                        "RUB"
                )
        );

        var exactObserved = service.lines(
                List.of(component),
                null,
                List.of(
                        combinationFact(
                                10L,
                                42L,
                                1,
                                10,
                                480L,
                                100_000L,
                                3_000
                        )
                ),
                "RUB"
        );

        assertEquals(
                100_000L,
                exactObserved.get(0)
                        .detailedLines()
                        .get(0)
                        .amountMinor()
        );
        assertEquals(
                480L,
                exactObserved.get(0)
                        .detailedLines()
                        .get(0)
                        .qualifiedQuantity()
                        .value()
        );
    }

    @Test
    void combinationRequiresCanonicalFrozenCurrencyOnlyForMatchingExplicitFacts() {
        var component = component(
                0,
                PayrollEarningKind.COMBINATION,
                "FIXED_AMOUNT",
                null,
                null,
                0L,
                100_000L
        );
        var fact = combinationFact(
                10L,
                42L,
                1,
                10,
                480L,
                100_000L,
                null
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(component),
                        null,
                        List.of(fact),
                        null
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(component),
                        null,
                        List.of(fact),
                        "rub"
                )
        );

        var aggregateOnly = service.lines(
                List.of(component),
                null,
                List.of(),
                null
        );
        assertNull(aggregateOnly.get(0).detailedLines());
    }

    @Test
    void explicitFactsCanCoverOnlyOneOfSeveralFrozenCombinationComponents() {
        var first = component(
                0,
                PayrollEarningKind.COMBINATION,
                "FIXED_AMOUNT",
                null,
                null,
                0L,
                40_000L
        );
        var second = component(
                1,
                PayrollEarningKind.COMBINATION,
                "FIXED_AMOUNT",
                null,
                null,
                0L,
                60_000L
        );
        when(second.getComponentId()).thenReturn(99L);

        var result = service.lines(
                List.of(first, second),
                null,
                List.of(
                        combinationFact(
                                11L,
                                99L,
                                20,
                                21,
                                120L,
                                60_000L,
                                null
                        )
                ),
                "RUB"
        );

        assertNull(result.get(0).detailedLines());
        assertNotNull(result.get(1).detailedLines());
        assertEquals(60_000L, result.get(1).detailedLines().get(0).amountMinor());
    }

    @Test
    void harmfulFailsClosedWhenFrozenFormulaDisagreesWithDetailedBaseTruth() {
        var base = List.of(
                baseLine(
                        1_000_000L,
                        480L,
                        1,
                        31
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(
                                component(
                                        0,
                                        PayrollEarningKind.HARMFUL_CONDITIONS,
                                        "PERCENT_OF_BASE",
                                        "EARNED_BASE_PAY",
                                        400,
                                        999_999L,
                                        40_000L
                                )
                        ),
                        base
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.lines(
                        List.of(
                                component(
                                        0,
                                        PayrollEarningKind.HARMFUL_CONDITIONS,
                                        "PERCENT_OF_BASE",
                                        "EARNED_BASE_PAY",
                                        400,
                                        1_000_000L,
                                        39_999L
                                )
                        ),
                        base
                )
        );
    }

    private static SemanticLine baseLine(
            long amountMinor,
            long minutes,
            int dayFrom,
            int dayTo
    ) {
        return new SemanticLine(
                PayrollEarningKind.BASE_PAY,
                amountMinor,
                PayrollQualifiedQuantity.minutes(minutes),
                LocalDate.of(2026, 3, dayFrom),
                LocalDate.of(2026, 3, dayTo),
                null,
                null
        );
    }

    private static PayrollSnapshotComponentLine component(
            int lineIndex,
            PayrollEarningKind kind,
            String calculationType,
            String calculationBase,
            Integer rateBps,
            long referenceBaseMinor,
            long amountMinor
    ) {
        PayrollSnapshotComponentLine line =
                mock(PayrollSnapshotComponentLine.class);

        when(line.getLineIndex()).thenReturn(lineIndex);
        when(line.getComponentId()).thenReturn(42L);
        when(line.getEarningKind()).thenReturn(kind);
        when(line.getCalculationType()).thenReturn(calculationType);
        when(line.getCalculationBase()).thenReturn(calculationBase);
        when(line.getRateBps()).thenReturn(rateBps);
        when(line.getReferenceBaseMinor()).thenReturn(referenceBaseMinor);
        when(line.getAmountMinor()).thenReturn(amountMinor);

        return line;
    }
    private static EpisodeFact combinationFact(
            long factId,
            long componentId,
            int dayFrom,
            int dayTo,
            long minutes,
            long amountMinor,
            Integer agreedRateBps
    ) {
        return new EpisodeFact(
                factId,
                componentId,
                LocalDate.of(2026, 6, dayFrom),
                LocalDate.of(2026, 6, dayTo),
                minutes,
                amountMinor,
                "RUB",
                agreedRateBps
        );
    }

}
