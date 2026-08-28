package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.PayrollBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollBonusP15NatureFactRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollBonusP15NatureFactServiceTest {

    private final PayrollBonusP15NatureFactRepository facts =
            mock(PayrollBonusP15NatureFactRepository.class);
    private final PayrollBonusAverageEarningsFactRepository averageFacts =
            mock(PayrollBonusAverageEarningsFactRepository.class);
    private final PayrollBonusP15NatureFactService service =
            new PayrollBonusP15NatureFactService(facts, averageFacts);
    private final AppUser user = mock(AppUser.class);

    @Test
    void createCopiesStableF1IdentityAndExplicitNature() {
        PayrollBonusAverageEarningsFact average = average(11L, 21L);
        when(averageFacts.findByOwnerAndId(user, 11L))
                .thenReturn(Optional.of(average));
        when(facts.findByOwnerAndBonusAverageFactId(user, 11L))
                .thenReturn(Optional.empty());
        when(facts.findByOwnerAndBonusSourceFactId(user, 21L))
                .thenReturn(Optional.empty());
        when(facts.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(user, 11L, PayrollBonusP15Nature.MONTHLY);

        assertEquals(11L, created.getBonusAverageFactId());
        assertEquals(21L, created.getBonusSourceFactId());
        assertEquals(PayrollBonusP15Nature.MONTHLY, created.getP15Nature());
    }

    @Test
    void createNeverInfersWorkPeriodNatureFromAwardLength() {
        PayrollBonusAverageEarningsFact average = oneTimeAverage(11L, 21L, false);
        when(averageFacts.findByOwnerAndId(user, 11L))
                .thenReturn(Optional.of(average));
        when(facts.findByOwnerAndBonusAverageFactId(user, 11L))
                .thenReturn(Optional.empty());
        when(facts.findByOwnerAndBonusSourceFactId(user, 21L))
                .thenReturn(Optional.empty());

        assertThrows(
                NullPointerException.class,
                () -> service.create(user, 11L, null)
        );
        verify(facts, never()).saveAndFlush(any());
    }

    @Test
    void duplicateAverageIdentityIsRejected() {
        PayrollBonusAverageEarningsFact average = average(11L, 21L);
        when(averageFacts.findByOwnerAndId(user, 11L))
                .thenReturn(Optional.of(average));
        when(facts.findByOwnerAndBonusAverageFactId(user, 11L))
                .thenReturn(Optional.of(mock(PayrollBonusP15NatureFact.class)));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(user, 11L, PayrollBonusP15Nature.MONTHLY)
        );
    }

    @Test
    void resolveReturnsOnlyExplicitConfiguredNatureFacts() {
        var a1 = new PayrollBonusAverageEarningsFactService.AverageFact(
                11, 21, 31, PayrollEarningKind.MONTHLY_BONUS,
                "KPI", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                null, true, null
        );
        var a2 = new PayrollBonusAverageEarningsFactService.AverageFact(
                12, 22, 32, PayrollEarningKind.MONTHLY_BONUS,
                "KPI2", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                null, true, null
        );

        PayrollBonusP15NatureFact stored = mock(PayrollBonusP15NatureFact.class);
        when(stored.getId()).thenReturn(101L);
        when(stored.getOwner()).thenReturn(user);
        when(stored.getBonusAverageFactId()).thenReturn(11L);
        when(stored.getBonusSourceFactId()).thenReturn(21L);
        when(stored.getComponentId()).thenReturn(31L);
        when(stored.getEarningKind()).thenReturn(PayrollEarningKind.MONTHLY_BONUS);
        when(stored.getP15Nature()).thenReturn(PayrollBonusP15Nature.MONTHLY);

        when(facts.findByOwnerAndBonusAverageFactIdInOrderByBonusAverageFactIdAscIdAsc(
                user,
                List.of(11L, 12L)
        )).thenReturn(List.of(stored));

        var resolved = service.resolveForAverageFacts(user, List.of(a1, a2));

        assertEquals(1, resolved.size());
        assertEquals(11L, resolved.get(0).bonusAverageFactId());
        assertEquals(PayrollBonusP15Nature.MONTHLY, resolved.get(0).p15Nature());
    }

    @Test
    void resolvedNatureContradictingCurrentAverageFactFailsClosed() {
        var average = new PayrollBonusAverageEarningsFactService.AverageFact(
                11, 21, 31, PayrollEarningKind.MONTHLY_BONUS,
                "KPI", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                null, true, null
        );

        PayrollBonusP15NatureFact stored = mock(PayrollBonusP15NatureFact.class);
        when(stored.getId()).thenReturn(101L);
        when(stored.getOwner()).thenReturn(user);
        when(stored.getBonusAverageFactId()).thenReturn(11L);
        when(stored.getBonusSourceFactId()).thenReturn(21L);
        when(stored.getComponentId()).thenReturn(31L);
        when(stored.getEarningKind()).thenReturn(PayrollEarningKind.MONTHLY_BONUS);
        when(stored.getP15Nature()).thenReturn(PayrollBonusP15Nature.WORK_PERIOD);

        when(facts.findByOwnerAndBonusAverageFactIdInOrderByBonusAverageFactIdAscIdAsc(
                user,
                List.of(11L)
        )).thenReturn(List.of(stored));

        assertThrows(
                IllegalStateException.class,
                () -> service.resolveForAverageFacts(user, List.of(average))
        );
    }

    @Test
    void updateRevalidatesAgainstCurrentF1Facts() {
        PayrollBonusAverageEarningsFact average = oneTimeAverage(11L, 21L, true);
        PayrollBonusP15NatureFact current = mock(PayrollBonusP15NatureFact.class);
        when(current.getBonusAverageFactId()).thenReturn(11L);
        when(current.getBonusSourceFactId()).thenReturn(21L);
        when(current.getComponentId()).thenReturn(31L);
        when(current.getEarningKind()).thenReturn(PayrollEarningKind.ONE_TIME_BONUS);
        when(facts.findByOwnerAndId(user, 101L)).thenReturn(Optional.of(current));
        when(averageFacts.findByOwnerAndId(user, 11L)).thenReturn(Optional.of(average));
        when(facts.saveAndFlush(current)).thenReturn(current);

        service.update(user, 101L, PayrollBonusP15Nature.ANNUAL_RESULT);

        verify(current).update(
                PayrollBonusP15Nature.ANNUAL_RESULT,
                "YEAR_RESULT",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                true
        );
    }

    @Test
    void deleteIsOwnerScoped() {
        PayrollBonusP15NatureFact current = mock(PayrollBonusP15NatureFact.class);
        when(facts.findByOwnerAndId(user, 101L)).thenReturn(Optional.of(current));

        service.delete(user, 101L);

        verify(facts).delete(current);
        verify(facts).flush();
    }

    private PayrollBonusAverageEarningsFact average(long id, long sourceId) {
        PayrollBonusAverageEarningsFact fact = mock(PayrollBonusAverageEarningsFact.class);
        when(fact.getId()).thenReturn(id);
        when(fact.getBonusSourceFactId()).thenReturn(sourceId);
        when(fact.getComponentId()).thenReturn(31L);
        when(fact.getEarningKind()).thenReturn(PayrollEarningKind.MONTHLY_BONUS);
        when(fact.getIndicatorKey()).thenReturn("KPI");
        when(fact.getAwardPeriodFrom()).thenReturn(LocalDate.of(2026, 3, 1));
        when(fact.getAwardPeriodTo()).thenReturn(LocalDate.of(2026, 3, 31));
        when(fact.getAnnualResult()).thenReturn(null);
        return fact;
    }

    private PayrollBonusAverageEarningsFact oneTimeAverage(
            long id,
            long sourceId,
            boolean annual
    ) {
        PayrollBonusAverageEarningsFact fact = mock(PayrollBonusAverageEarningsFact.class);
        when(fact.getId()).thenReturn(id);
        when(fact.getBonusSourceFactId()).thenReturn(sourceId);
        when(fact.getComponentId()).thenReturn(31L);
        when(fact.getEarningKind()).thenReturn(PayrollEarningKind.ONE_TIME_BONUS);
        when(fact.getIndicatorKey()).thenReturn(annual ? "YEAR_RESULT" : "QUARTER");
        when(fact.getAwardPeriodFrom()).thenReturn(LocalDate.of(2025, 1, 1));
        when(fact.getAwardPeriodTo()).thenReturn(annual
                ? LocalDate.of(2025, 12, 31)
                : LocalDate.of(2025, 3, 31));
        when(fact.getAnnualResult()).thenReturn(annual);
        return fact;
    }
}
