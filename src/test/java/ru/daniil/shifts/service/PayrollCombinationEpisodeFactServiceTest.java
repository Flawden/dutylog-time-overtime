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
