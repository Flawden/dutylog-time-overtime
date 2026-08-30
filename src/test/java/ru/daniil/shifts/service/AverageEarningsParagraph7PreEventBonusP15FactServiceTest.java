package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusP15Nature;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15FactService.BonusP15Fact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventBonusP15FactService.Resolution;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SemanticWageFact;
import ru.daniil.shifts.service.AverageEarningsParagraph7PreEventSemanticWageFactService.SourceAuthority;
import ru.daniil.shifts.service.PayrollBonusAverageEarningsFactService.AverageFact;
import ru.daniil.shifts.service.PayrollBonusP15NatureFactService.NatureFact;
import ru.daniil.shifts.service.PayrollBonusSourceFactService.BonusFact;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AverageEarningsParagraph7PreEventBonusP15FactServiceTest {
    private static final LocalDate EVENT = LocalDate.of(2026, 8, 20);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    private final PayrollBonusAverageEarningsFactService averageFacts =
            mock(PayrollBonusAverageEarningsFactService.class);
    private final PayrollBonusP15NatureFactService natureFacts =
            mock(PayrollBonusP15NatureFactService.class);
    private final AverageEarningsParagraph7PreEventBonusP15FactService service =
            new AverageEarningsParagraph7PreEventBonusP15FactService(
                    averageFacts,
                    natureFacts
            );
    private final AppUser user = mock(AppUser.class);

    @Test
    void constructorRequiresAverageFactAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventBonusP15FactService(
                        null,
                        natureFacts
                )
        );
    }

    @Test
    void constructorRequiresNatureFactAuthority() {
        assertThrows(
                NullPointerException.class,
                () -> new AverageEarningsParagraph7PreEventBonusP15FactService(
                        averageFacts,
                        null
                )
        );
    }

    @Test
    void nullUserRejectedBeforeDiscovery() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(null, readySemantic(List.of()))
        );
        verifyNoInteractions(averageFacts, natureFacts);
    }

    @Test
    void nullSemanticAuthorityRejected() {
        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, null)
        );
        verifyNoInteractions(averageFacts, natureFacts);
    }

    @Test
    void blockedSemanticAuthorityCannotReachP15Facts() {
        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic =
                mock(AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution.class);
        doReturn(false).when(semantic).ready();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(user, semantic)
        );
        verifyNoInteractions(averageFacts, natureFacts);
    }

    @Test
    void semanticWindowMismatchBlocksWithoutReadingP15Authorities() {
        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic =
                readySemantic(List.of(monthlySource(11L, 21L, 10_000L)));
        doReturn(LocalDate.of(2026, 8, 2)).when(semantic).periodFrom();

        Resolution result = service.resolve(user, semantic);

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15FactService.AUTHORITY_WINDOW_MISMATCH,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
        verifyNoInteractions(averageFacts, natureFacts);
    }

    @Test
    void noBonusSemanticFactsAreReadyEmptyWithoutReadingP15Authorities() {
        SemanticWageFact combination = new SemanticWageFact(
                SourceAuthority.COMBINATION_EPISODE,
                PayrollEarningKind.COMBINATION,
                41L,
                51L,
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 4),
                8_000L,
                "RUB",
                480L,
                2500
        );

        Resolution result = service.resolve(
                user,
                readySemantic(List.of(combination))
        );

        assertTrue(result.ready());
        assertTrue(result.bonusFacts().isEmpty());
        verifyNoInteractions(averageFacts, natureFacts);
    }

    @Test
    void monthlyBonusAttachesAverageAndExplicitNatureFacts() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact average = monthlyAverage(101L, 11L, 21L);
        NatureFact nature = monthlyNature(201L, 101L, 11L, 21L);
        stubComplete(source, average, nature);

        Resolution result = service.resolve(user, readySemantic(List.of(source)));

        assertTrue(result.ready());
        assertEquals(1, result.bonusFacts().size());
        BonusP15Fact fact = result.bonusFacts().get(0);
        assertSame(source, fact.sourceFact());
        assertSame(average, fact.averageFact());
        assertSame(nature, fact.natureFact());
    }

    @Test
    void oneTimeBonusAttachesAverageAndExplicitNatureFacts() {
        SemanticWageFact source = oneTimeSource(12L, 22L, 30_000L);
        AverageFact average = oneTimeAverage(102L, 12L, 22L);
        NatureFact nature = new NatureFact(
                202L,
                102L,
                12L,
                22L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.WORK_PERIOD
        );
        stubComplete(source, average, nature);

        Resolution result = service.resolve(user, readySemantic(List.of(source)));

        assertTrue(result.ready());
        assertEquals(PayrollBonusP15Nature.WORK_PERIOD,
                result.bonusFacts().get(0).natureFact().p15Nature());
    }

    @Test
    void mixedBonusFactsPreserveSemanticSourceOrder() {
        SemanticWageFact first = monthlySource(11L, 21L, 10_000L);
        SemanticWageFact second = oneTimeSource(12L, 22L, 30_000L);
        AverageFact firstAverage = monthlyAverage(101L, 11L, 21L);
        AverageFact secondAverage = oneTimeAverage(102L, 12L, 22L);
        NatureFact firstNature = monthlyNature(201L, 101L, 11L, 21L);
        NatureFact secondNature = new NatureFact(
                202L,
                102L,
                12L,
                22L,
                PayrollEarningKind.ONE_TIME_BONUS,
                PayrollBonusP15Nature.WORK_PERIOD
        );
        doReturn(List.of(secondAverage, firstAverage))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());
        doReturn(List.of(secondNature, firstNature))
                .when(natureFacts)
                .resolveForAverageFacts(eq(user), anyList());

        Resolution result = service.resolve(
                user,
                readySemantic(List.of(first, second))
        );

        assertTrue(result.ready());
        assertEquals(List.of(11L, 12L), result.bonusFacts().stream()
                .map(fact -> fact.sourceFact().factId())
                .toList());
    }

    @Test
    void factualAmountAndCurrencyStayOnSourceFactWithoutMoneyFormula() {
        SemanticWageFact source = monthlySource(11L, 21L, 12_345L);
        stubComplete(
                source,
                monthlyAverage(101L, 11L, 21L),
                monthlyNature(201L, 101L, 11L, 21L)
        );

        BonusP15Fact fact = service.resolve(
                user,
                readySemantic(List.of(source))
        ).bonusFacts().get(0);

        assertEquals(12_345L, fact.factualAmountMinor());
        assertEquals("RUB", fact.currencyCode());
    }

    @Test
    void missingAverageFactBlocksAndDoesNotReadNatureAuthority() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        doReturn(List.of())
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());

        Resolution result = service.resolve(user, readySemantic(List.of(source)));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15FactService.AVERAGE_FACT_MISSING,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
        verifyNoInteractions(natureFacts);
    }

    @Test
    void nullAverageFactAuthorityResultIsStructuralFailure() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        doReturn(null)
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, readySemantic(List.of(source)))
        );
        verifyNoInteractions(natureFacts);
    }

    @Test
    void duplicateAverageSourceIdentityIsStructuralFailure() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact average = monthlyAverage(101L, 11L, 21L);
        AverageFact duplicate = monthlyAverage(102L, 11L, 21L);
        doReturn(List.of(average, duplicate))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, readySemantic(List.of(source)))
        );
        verifyNoInteractions(natureFacts);
    }

    @Test
    void averageFactIdentityMismatchBlocksBeforeNatureLookup() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact wrong = monthlyAverage(101L, 11L, 999L);
        doReturn(List.of(wrong))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());

        Resolution result = service.resolve(user, readySemantic(List.of(source)));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15FactService.FACT_IDENTITY_MISMATCH,
                result.blockingReason()
        );
        verifyNoInteractions(natureFacts);
    }

    @Test
    void missingNatureFactBlocksWithoutExposingAverageAuthority() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact average = monthlyAverage(101L, 11L, 21L);
        doReturn(List.of(average))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());
        doReturn(List.of())
                .when(natureFacts)
                .resolveForAverageFacts(eq(user), anyList());

        Resolution result = service.resolve(user, readySemantic(List.of(source)));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15FactService.NATURE_FACT_MISSING,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
    }

    @Test
    void nullNatureFactAuthorityResultIsStructuralFailure() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact average = monthlyAverage(101L, 11L, 21L);
        doReturn(List.of(average))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());
        doReturn(null)
                .when(natureFacts)
                .resolveForAverageFacts(eq(user), anyList());

        assertThrows(
                NullPointerException.class,
                () -> service.resolve(user, readySemantic(List.of(source)))
        );
    }

    @Test
    void duplicateNatureAverageIdentityIsStructuralFailure() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact average = monthlyAverage(101L, 11L, 21L);
        NatureFact first = monthlyNature(201L, 101L, 11L, 21L);
        NatureFact duplicate = monthlyNature(202L, 101L, 11L, 21L);
        doReturn(List.of(average))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());
        doReturn(List.of(first, duplicate))
                .when(natureFacts)
                .resolveForAverageFacts(eq(user), anyList());

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, readySemantic(List.of(source)))
        );
    }

    @Test
    void natureFactIdentityMismatchBlocksWithoutPartialAuthority() {
        SemanticWageFact source = monthlySource(11L, 21L, 10_000L);
        AverageFact average = monthlyAverage(101L, 11L, 21L);
        NatureFact wrong = monthlyNature(201L, 101L, 999L, 21L);
        doReturn(List.of(average))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());
        doReturn(List.of(wrong))
                .when(natureFacts)
                .resolveForAverageFacts(eq(user), anyList());

        Resolution result = service.resolve(user, readySemantic(List.of(source)));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsParagraph7PreEventBonusP15FactService.FACT_IDENTITY_MISMATCH,
                result.blockingReason()
        );
        assertTrue(result.bonusFacts().isEmpty());
    }

    private void stubComplete(
            SemanticWageFact source,
            AverageFact average,
            NatureFact nature
    ) {
        doReturn(List.of(average))
                .when(averageFacts)
                .resolveForBonusFacts(eq(user), anyList());
        doReturn(List.of(nature))
                .when(natureFacts)
                .resolveForAverageFacts(eq(user), anyList());
    }

    private static AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution readySemantic(
            List<SemanticWageFact> facts
    ) {
        AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution semantic =
                mock(AverageEarningsParagraph7PreEventSemanticWageFactService.Resolution.class);
        doReturn(true).when(semantic).ready();
        doReturn(EVENT).when(semantic).eventDate();
        doReturn(FROM).when(semantic).periodFrom();
        doReturn(EVENT).when(semantic).cutoffExclusive();
        doReturn(List.copyOf(facts)).when(semantic).observedFacts();
        return semantic;
    }

    private static SemanticWageFact monthlySource(
            long sourceId,
            long componentId,
            long amountMinor
    ) {
        return new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.MONTHLY_BONUS,
                sourceId,
                componentId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 15),
                amountMinor,
                "RUB",
                null,
                null
        );
    }

    private static SemanticWageFact oneTimeSource(
            long sourceId,
            long componentId,
            long amountMinor
    ) {
        return new SemanticWageFact(
                SourceAuthority.BONUS_SOURCE,
                PayrollEarningKind.ONE_TIME_BONUS,
                sourceId,
                componentId,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 10),
                amountMinor,
                "RUB",
                null,
                null
        );
    }

    private static AverageFact monthlyAverage(
            long averageId,
            long sourceId,
            long componentId
    ) {
        return new AverageFact(
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.MONTHLY_BONUS,
                "MONTHLY_KPI",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                true,
                true
        );
    }

    private static AverageFact oneTimeAverage(
            long averageId,
            long sourceId,
            long componentId
    ) {
        return new AverageFact(
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.ONE_TIME_BONUS,
                "PROJECT",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 30),
                false,
                true,
                true
        );
    }

    private static NatureFact monthlyNature(
            long natureId,
            long averageId,
            long sourceId,
            long componentId
    ) {
        return new NatureFact(
                natureId,
                averageId,
                sourceId,
                componentId,
                PayrollEarningKind.MONTHLY_BONUS,
                PayrollBonusP15Nature.MONTHLY
        );
    }
}
