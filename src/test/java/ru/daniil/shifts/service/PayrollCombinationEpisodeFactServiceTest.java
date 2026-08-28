package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.CompensationComponent;
import ru.daniil.shifts.model.PayrollCombinationEpisodeFact;
import ru.daniil.shifts.repo.CompensationComponentRepository;
import ru.daniil.shifts.repo.PayrollCombinationEpisodeFactRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayrollCombinationEpisodeFactServiceTest {

    private PayrollCombinationEpisodeFactRepository facts;
    private CompensationComponentRepository components;
    private PayrollCombinationEpisodeFactService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        facts = mock(PayrollCombinationEpisodeFactRepository.class);
        components = mock(CompensationComponentRepository.class);
        user = mock(AppUser.class);
        service = new PayrollCombinationEpisodeFactService(facts, components);
    }

    @Test
    void resolveMonthPreservesObservedSplitPeriodsMinutesMoneyAndRate() {
        PayrollCombinationEpisodeFact first = persisted(
                10L,
                42L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                4_740L,
                716_062L,
                "RUB",
                2_500
        );
        PayrollCombinationEpisodeFact second = persisted(
                11L,
                42L,
                LocalDate.of(2026, 6, 29),
                LocalDate.of(2026, 6, 30),
                480L,
                85_824L,
                "RUB",
                2_500
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        )).thenReturn(List.of(first, second));

        var result = service.resolveMonth(user, YearMonth.of(2026, 6));

        assertEquals(2, result.size());
        assertEquals(42L, result.get(0).componentId());
        assertEquals(LocalDate.of(2026, 6, 1), result.get(0).periodFrom());
        assertEquals(LocalDate.of(2026, 6, 15), result.get(0).periodTo());
        assertEquals(4_740L, result.get(0).qualifiedMinutes());
        assertEquals(716_062L, result.get(0).amountMinor());
        assertEquals("RUB", result.get(0).currencyCode());
        assertEquals(2_500, result.get(0).agreedRateBps());
        assertEquals(LocalDate.of(2026, 6, 29), result.get(1).periodFrom());
    }

    @Test
    void emptyMonthMeansNoExplicitEpisodeEvidenceAndDoesNotSynthesizeZeroLine() {
        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        )).thenReturn(List.of());

        var result = service.resolveMonth(user, YearMonth.of(2026, 8));

        assertTrue(result.isEmpty());
        verifyNoInteractions(components);
    }

    @Test
    void monthResolutionFailsClosedWhenRepositoryLeaksAnotherMonth() {
        PayrollCombinationEpisodeFact wrongMonth = persisted(
                10L,
                42L,
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 31),
                4_800L,
                801_960L,
                "RUB",
                2_500
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        )).thenReturn(List.of(wrongMonth));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 6))
        );
    }

    @Test
    void overlappingPersistedFactsForOneComponentFailClosed() {
        PayrollCombinationEpisodeFact first = persisted(
                10L,
                42L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                4_740L,
                716_062L,
                "RUB",
                2_500
        );
        PayrollCombinationEpisodeFact overlap = persisted(
                11L,
                42L,
                LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 30),
                480L,
                85_824L,
                "RUB",
                2_500
        );

        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                user,
                42L
        )).thenReturn(List.of(first, overlap));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveComponent(user, 42L)
        );
    }

    @Test
    void createRequiresOwnedStableComponentAndRejectsOverlap() {
        CompensationComponent component = mock(CompensationComponent.class);

        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.of(component));

        PayrollCombinationEpisodeFact existing = persisted(
                10L,
                42L,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 15),
                4_740L,
                716_062L,
                "RUB",
                2_500
        );

        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                user,
                42L
        )).thenReturn(List.of(existing));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user,
                        42L,
                        LocalDate.of(2026, 6, 15),
                        LocalDate.of(2026, 6, 30),
                        480L,
                        85_824L,
                        "RUB",
                        2_500
                )
        );

        verify(facts, never()).saveAndFlush(any());
    }

    @Test
    void createPreservesObservedMoneyWithoutDerivingUnknownExternalBase() {
        CompensationComponent component = mock(CompensationComponent.class);

        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.of(component));
        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(
                user,
                42L
        )).thenReturn(List.of());
        when(facts.saveAndFlush(any(PayrollCombinationEpisodeFact.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PayrollCombinationEpisodeFact saved = service.create(
                user,
                42L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 26),
                8_640L,
                1_402_102L,
                "rub",
                2_500
        );

        assertEquals(1_402_102L, saved.getAmountMinor());
        assertEquals(8_640L, saved.getQualifiedMinutes());
        assertEquals("RUB", saved.getCurrencyCode());
        assertEquals(2_500, saved.getAgreedRateBps());
        assertEquals(LocalDate.of(2026, 7, 1), saved.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 7, 26), saved.getPeriodTo());
    }

    @Test
    void factShapeRejectsCrossMonthEvidenceInsteadOfProratingObservedMoney() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFact(
                        user,
                        42L,
                        LocalDate.of(2026, 6, 29),
                        LocalDate.of(2026, 7, 2),
                        960L,
                        170_000L,
                        "RUB",
                        2_500
                )
        );
    }


    @Test
    void resolveMonthRejectsNullRepositoryResult() {
        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                user,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        )).thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 6))
        );
    }

    @Test
    void monthResolutionRejectsEveryRepositoryOrderingViolation() {
        PayrollCombinationEpisodeFact componentTwo = persisted(
                1L, 2L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                60L, 10_000L, "RUB", null
        );
        PayrollCombinationEpisodeFact componentOne = persisted(
                2L, 1L,
                LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4),
                60L, 10_000L, "RUB", null
        );
        PayrollCombinationEpisodeFact laterFrom = persisted(
                3L, 42L,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11),
                60L, 10_000L, "RUB", null
        );
        PayrollCombinationEpisodeFact earlierFrom = persisted(
                4L, 42L,
                LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 12),
                60L, 10_000L, "RUB", null
        );
        PayrollCombinationEpisodeFact laterTo = persisted(
                5L, 42L,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 20),
                60L, 10_000L, "RUB", null
        );
        PayrollCombinationEpisodeFact earlierTo = persisted(
                6L, 42L,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 19),
                60L, 10_000L, "RUB", null
        );

        when(facts.findByOwnerAndPeriodFromBetweenOrderByComponentIdAscPeriodFromAscPeriodToAscIdAsc(
                eq(user), any(LocalDate.class), any(LocalDate.class)
        )).thenReturn(
                List.of(componentTwo, componentOne),
                List.of(laterFrom, earlierFrom),
                List.of(laterTo, earlierTo)
        );

        assertThrows(IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 6)));
        assertThrows(IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 6)));
        assertThrows(IllegalStateException.class,
                () -> service.resolveMonth(user, YearMonth.of(2026, 6)));
    }

    @Test
    void resolveComponentFailsClosedForMixedIdentityOrNullHistory() {
        PayrollCombinationEpisodeFact wrongComponent = persisted(
                10L, 99L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                60L, 10_000L, "RUB", null
        );

        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(user, 42L))
                .thenReturn(List.of(wrongComponent))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveComponent(user, 42L)
        );
        assertThrows(
                IllegalStateException.class,
                () -> service.resolveComponent(user, 42L)
        );
    }

    @Test
    void createRejectsMissingOwnedComponentBeforePersistingFact() {
        when(components.findByOwnerAndId(user, 42L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user, 42L,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );

        verifyNoInteractions(facts);
    }

    @Test
    void updateExcludesOwnFactFromOverlapCheckAndPersistsValidatedReplacement() {
        PayrollCombinationEpisodeFact current = new PayrollCombinationEpisodeFact(
                user, 42L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5),
                300L, 50_000L, "RUB", 2_500
        );
        PayrollCombinationEpisodeFact persistedCurrent = spy(current);
        doReturn(10L).when(persistedCurrent).getId();

        when(facts.findByOwnerAndId(user, 10L))
                .thenReturn(Optional.of(persistedCurrent));
        when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(user, 42L))
                .thenReturn(List.of(persistedCurrent));
        when(facts.saveAndFlush(persistedCurrent))
                .thenReturn(persistedCurrent);

        PayrollCombinationEpisodeFact saved = service.update(
                user, 10L,
                LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 6),
                360L, 55_000L, "rub", null
        );

        assertSame(persistedCurrent, saved);
        assertEquals(LocalDate.of(2026, 6, 2), saved.getPeriodFrom());
        assertEquals(LocalDate.of(2026, 6, 6), saved.getPeriodTo());
        assertEquals(360L, saved.getQualifiedMinutes());
        assertEquals(55_000L, saved.getAmountMinor());
        assertEquals("RUB", saved.getCurrencyCode());
        assertNull(saved.getAgreedRateBps());
    }

    @Test
    void updateRejectsMissingIdentityAndUnknownFact() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        user, null,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );

        when(facts.findByOwnerAndId(user, 999L))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        user, 999L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );
    }

    @Test
    void deleteRemovesOwnedFactAndRejectsMissingIdentityOrUnknownFact() {
        PayrollCombinationEpisodeFact current = persisted(
                10L, 42L,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                60L, 10_000L, "RUB", null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(user, null)
        );

        when(facts.findByOwnerAndId(user, 999L))
                .thenReturn(Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(user, 999L)
        );

        when(facts.findByOwnerAndId(user, 10L))
                .thenReturn(Optional.of(current));
        service.delete(user, 10L);

        verify(facts).delete(current);
        verify(facts).flush();
    }

    @Test
    void publicOperationsRejectNonPositiveComponentIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveComponent(user, 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user, -1L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null
                )
        );
    }

    @Test
    void persistedValidationRejectsEveryCorruptStoredShape() {
        List<PayrollCombinationEpisodeFact> corrupt = List.of(
                persistedWithShape(null, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null),
                persistedWithShape(1L, 0L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null),
                persistedWithShape(1L, 42L,
                        null, LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), null,
                        60L, 10_000L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 1),
                        60L, 10_000L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        0L, 10_000L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 0L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 1_000_000_000_001L, "RUB", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, null, null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "rub", null),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", 0),
                persistedWithShape(1L, 42L,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        60L, 10_000L, "RUB", 10_000_001)
        );

        for (PayrollCombinationEpisodeFact invalid : corrupt) {
            when(facts.findByOwnerAndComponentIdOrderByPeriodFromAscPeriodToAscIdAsc(user, 42L))
                    .thenReturn(List.of(invalid));
            assertThrows(
                    IllegalStateException.class,
                    () -> service.resolveComponent(user, 42L)
            );
        }
    }

    @Test
    void episodeFactRecordRejectsEveryInvalidPublicShape() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        0L, 42L, from, to, 60L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 0L, from, to, 60L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, null, to, 60L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, null, 60L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, to, from, 60L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 1),
                        60L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 0L, 10_000L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 60L, 0L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 60L, 1_000_000_000_001L, "RUB", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 60L, 10_000L, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 60L, 10_000L, "rub", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 60L, 10_000L, "RUB", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PayrollCombinationEpisodeFactService.EpisodeFact(
                        1L, 42L, from, to, 60L, 10_000L, "RUB", 10_000_001));

        var valid = new PayrollCombinationEpisodeFactService.EpisodeFact(
                1L, 42L, from, to, 60L, 10_000L, "RUB", null
        );
        assertEquals(42L, valid.componentId());
    }


    private static PayrollCombinationEpisodeFact persistedWithShape(
            Long id,
            long componentId,
            LocalDate from,
            LocalDate to,
            long minutes,
            long amountMinor,
            String currency,
            Integer rateBps
    ) {
        PayrollCombinationEpisodeFact fact = mock(PayrollCombinationEpisodeFact.class);
        when(fact.getId()).thenReturn(id);
        when(fact.getComponentId()).thenReturn(componentId);
        when(fact.getPeriodFrom()).thenReturn(from);
        when(fact.getPeriodTo()).thenReturn(to);
        when(fact.getQualifiedMinutes()).thenReturn(minutes);
        when(fact.getAmountMinor()).thenReturn(amountMinor);
        when(fact.getCurrencyCode()).thenReturn(currency);
        when(fact.getAgreedRateBps()).thenReturn(rateBps);
        return fact;
    }

    private static PayrollCombinationEpisodeFact persisted(
            long id,
            long componentId,
            LocalDate from,
            LocalDate to,
            long minutes,
            long amountMinor,
            String currency,
            Integer rateBps
    ) {
        PayrollCombinationEpisodeFact fact =
                mock(PayrollCombinationEpisodeFact.class);

        when(fact.getId()).thenReturn(id);
        when(fact.getComponentId()).thenReturn(componentId);
        when(fact.getPeriodFrom()).thenReturn(from);
        when(fact.getPeriodTo()).thenReturn(to);
        when(fact.getQualifiedMinutes()).thenReturn(minutes);
        when(fact.getAmountMinor()).thenReturn(amountMinor);
        when(fact.getCurrencyCode()).thenReturn(currency);
        when(fact.getAgreedRateBps()).thenReturn(rateBps);

        return fact;
    }
}
