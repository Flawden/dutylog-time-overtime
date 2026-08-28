package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollBonusAverageEarningsFact;
import ru.daniil.shifts.model.PayrollBonusSourceFact;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.repo.PayrollBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollBonusSourceFactRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PayrollBonusAverageEarningsFactServiceTest {

    private PayrollBonusAverageEarningsFactRepository facts;
    private PayrollBonusSourceFactRepository bonusSources;
    private PayrollBonusAverageEarningsFactService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        facts = mock(PayrollBonusAverageEarningsFactRepository.class);
        bonusSources = mock(PayrollBonusSourceFactRepository.class);
        user = mock(AppUser.class);
        service = new PayrollBonusAverageEarningsFactService(facts, bonusSources);
    }

    @Test
    void resolveForBonusFactsPreservesExplicitAwardFactsAndAllowsMissingAuthority() {
        var monthly = bonusFact(10L, 42L, PayrollEarningKind.MONTHLY_BONUS, 2_550_880L);
        var oneTime = bonusFact(11L, 43L, PayrollEarningKind.ONE_TIME_BONUS, 962_700L);
        var stored = persistedAverage(
                100L, 10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                "MONTHLY_40",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                false, true, true
        );

        when(facts.findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
                user, List.of(10L, 11L)
        )).thenReturn(List.of(stored));

        var result = service.resolveForBonusFacts(user, List.of(monthly, oneTime));

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).bonusSourceFactId());
        assertEquals("MONTHLY_40", result.get(0).indicatorKey());
        assertTrue(result.get(0).accruedForActualWorkTime());
        verifyNoInteractions(bonusSources);
    }

    @Test
    void emptySourceListNeedsNoRepositoryAuthority() {
        assertTrue(service.resolveForBonusFacts(user, List.of()).isEmpty());
        verifyNoInteractions(facts, bonusSources);
    }

    @Test
    void resolutionRejectsNullRepositoryDuplicateSourceIdentityAndLeakedFact() {
        var source = bonusFact(10L, 42L, PayrollEarningKind.MONTHLY_BONUS, 100L);

        when(facts.findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
                user, List.of(10L)
        )).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> service.resolveForBonusFacts(user, List.of(source)));

        assertThrows(IllegalStateException.class,
                () -> service.resolveForBonusFacts(user, List.of(source, source)));

        var leaked = persistedAverage(
                101L, 11L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                "MONTHLY_40", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        );
        when(facts.findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
                user, List.of(10L)
        )).thenReturn(List.of(leaked));
        assertThrows(IllegalStateException.class,
                () -> service.resolveForBonusFacts(user, List.of(source)));
    }

    @Test
    void resolutionRejectsRepositoryOrderAndSourceIdentityContradiction() {
        var source10 = bonusFact(10L, 42L, PayrollEarningKind.MONTHLY_BONUS, 100L);
        var source11 = bonusFact(11L, 43L, PayrollEarningKind.ONE_TIME_BONUS, 200L);
        var fact10 = persistedAverage(
                100L, 10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        );
        var fact11 = persistedAverage(
                101L, 11L, 43L, PayrollEarningKind.ONE_TIME_BONUS,
                "B", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                false, false, false
        );

        when(facts.findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
                user, List.of(10L, 11L)
        )).thenReturn(List.of(fact11, fact10));
        assertThrows(IllegalStateException.class,
                () -> service.resolveForBonusFacts(user, List.of(source10, source11)));

        var wrongComponent = persistedAverage(
                102L, 10L, 999L, PayrollEarningKind.MONTHLY_BONUS,
                "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        );
        when(facts.findByOwnerAndBonusSourceFactIdInOrderByBonusSourceFactIdAscIdAsc(
                user, List.of(10L)
        )).thenReturn(List.of(wrongComponent));
        assertThrows(IllegalStateException.class,
                () -> service.resolveForBonusFacts(user, List.of(source10)));
    }

    @Test
    void resolveRequiredFailsClosedWhenParagraph15FactsAreMissing() {
        when(facts.findByOwnerAndBonusSourceFactId(user, 10L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.resolveRequired(user, 10L));
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveRequired(user, 0L));
    }

    @Test
    void createCopiesOnlyStableSourceIdentityAndNeverInfersAwardPeriodFromSourcePeriod() {
        PayrollBonusSourceFact source = persistedSource(
                10L, 42L, PayrollEarningKind.ONE_TIME_BONUS
        );
        when(bonusSources.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(source));
        when(facts.findByOwnerAndBonusSourceFactId(user, 10L)).thenReturn(Optional.empty());
        when(facts.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(
                user,
                10L,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                false,
                false
        );

        assertEquals(10L, created.getBonusSourceFactId());
        assertEquals(42L, created.getComponentId());
        assertEquals(LocalDate.of(2025, 1, 1), created.getAwardPeriodFrom());
        assertTrue(created.getAnnualResult());
    }

    @Test
    void createRejectsMissingSourceOrDuplicateAverageAuthority() {
        when(bonusSources.findByOwnerAndId(user, 10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.create(
                user, 10L, "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        ));

        PayrollBonusSourceFact source = persistedSource(
                10L, 42L, PayrollEarningKind.MONTHLY_BONUS
        );
        when(bonusSources.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(source));
        when(facts.findByOwnerAndBonusSourceFactId(user, 10L))
                .thenReturn(Optional.of(mock(PayrollBonusAverageEarningsFact.class)));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                user, 10L, "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        ));
    }

    @Test
    void updateRequiresStillMatchingSourceIdentityAndPreservesScalarIdentity() {
        var current = persistedAverage(
                100L, 10L, 42L, PayrollEarningKind.ONE_TIME_BONUS,
                "OLD", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                false, false, false
        );
        PayrollBonusSourceFact source = persistedSource(
                10L, 42L, PayrollEarningKind.ONE_TIME_BONUS
        );

        when(facts.findByOwnerAndId(user, 100L)).thenReturn(Optional.of(current));
        when(bonusSources.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(source));
        when(facts.saveAndFlush(current)).thenReturn(current);

        var updated = service.update(
                user,
                100L,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                false,
                true
        );

        assertSame(current, updated);
        verify(current).update(
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true,
                false,
                true
        );
    }

    @Test
    void updateFailsWhenReferencedSourceDisappearedOrChangedIdentity() {
        var current = persistedAverage(
                100L, 10L, 42L, PayrollEarningKind.MONTHLY_BONUS,
                "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        );
        when(facts.findByOwnerAndId(user, 100L)).thenReturn(Optional.of(current));
        when(bonusSources.findByOwnerAndId(user, 10L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.update(
                user, 100L, "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        ));

        PayrollBonusSourceFact wrong = persistedSource(
                10L, 99L, PayrollEarningKind.MONTHLY_BONUS
        );
        when(bonusSources.findByOwnerAndId(user, 10L)).thenReturn(Optional.of(wrong));
        assertThrows(IllegalStateException.class, () -> service.update(
                user, 100L, "A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, false, false
        ));
    }

    @Test
    void deleteIsOwnerScopedAndMissingIdFailsClosed() {
        var current = mock(PayrollBonusAverageEarningsFact.class);
        when(facts.findByOwnerAndId(user, 100L)).thenReturn(Optional.of(current));

        service.delete(user, 100L);
        verify(facts).delete(current);
        verify(facts).flush();

        assertThrows(IllegalArgumentException.class, () -> service.delete(user, null));
        when(facts.findByOwnerAndId(user, 101L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.delete(user, 101L));
    }

    private PayrollBonusSourceFactService.BonusFact bonusFact(
            long factId,
            long componentId,
            PayrollEarningKind kind,
            long amount
    ) {
        return new PayrollBonusSourceFactService.BonusFact(
                factId,
                componentId,
                kind,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                amount,
                "RUB"
        );
    }

    private PayrollBonusSourceFact persistedSource(
            long id,
            long componentId,
            PayrollEarningKind kind
    ) {
        PayrollBonusSourceFact source = mock(PayrollBonusSourceFact.class);
        when(source.getId()).thenReturn(id);
        when(source.getComponentId()).thenReturn(componentId);
        when(source.getEarningKind()).thenReturn(kind);
        return source;
    }

    private PayrollBonusAverageEarningsFact persistedAverage(
            long id,
            long sourceId,
            long componentId,
            PayrollEarningKind kind,
            String indicator,
            LocalDate from,
            LocalDate to,
            boolean annual,
            boolean actualWork,
            boolean prorated
    ) {
        PayrollBonusAverageEarningsFact fact = mock(PayrollBonusAverageEarningsFact.class);
        when(fact.getId()).thenReturn(id);
        when(fact.getOwner()).thenReturn(user);
        when(fact.getBonusSourceFactId()).thenReturn(sourceId);
        when(fact.getComponentId()).thenReturn(componentId);
        when(fact.getEarningKind()).thenReturn(kind);
        when(fact.getIndicatorKey()).thenReturn(indicator);
        when(fact.getAwardPeriodFrom()).thenReturn(from);
        when(fact.getAwardPeriodTo()).thenReturn(to);
        when(fact.getAnnualResult()).thenReturn(annual);
        when(fact.getAccruedForActualWorkTime()).thenReturn(actualWork);
        when(fact.getProratedForPartialAwardPeriod()).thenReturn(prorated);
        return fact;
    }
}
