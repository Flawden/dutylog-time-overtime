package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollBonusP15NatureFreezeServiceTest {

    private final PayrollSnapshotBonusP15NatureFactRepository facts = mock(PayrollSnapshotBonusP15NatureFactRepository.class);
    private final PayrollSnapshotBonusP15NatureManifestRepository manifests = mock(PayrollSnapshotBonusP15NatureManifestRepository.class);
    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);
    private final PayrollBonusP15NatureFreezeService service = new PayrollBonusP15NatureFreezeService(facts, manifests);

    @Test
    void completeFreezeCopiesOnlyExplicitNatureAndPersistsIntegrityManifest() {
        var average = monthlyAverage(20L, 10L, 40L);
        var nature = nature(30L, average, PayrollBonusP15Nature.MONTHLY);

        var result = service.freeze(snapshot, List.of(average), List.of(nature));

        assertEquals(1, result.facts().size());
        assertEquals(30L, result.facts().get(0).getBonusNatureFactId());
        assertEquals(PayrollBonusP15Nature.MONTHLY, result.facts().get(0).getP15Nature());
        assertTrue(result.manifest().isComplete());
        assertEquals(PayrollBonusP15NatureFingerprint.calculate(1, result.facts()), result.manifest().getFingerprint());
        verify(facts).saveAll(result.facts());
        verify(manifests).saveAndFlush(result.manifest());
    }

    @Test
    void missingNatureCreatesIncompleteManifestInsteadOfGuessing() {
        var result = service.freeze(snapshot, List.of(monthlyAverage(20L, 10L, 40L)), List.of());
        assertTrue(result.facts().isEmpty());
        assertFalse(result.manifest().isComplete());
        assertEquals(1, result.manifest().getAverageFactCount());
        assertEquals(0, result.manifest().getNatureFactCount());
    }

    @Test
    void zeroAverageFactsPersistCompleteEmptyManifest() {
        var result = service.freeze(snapshot, List.of(), List.of());
        assertTrue(result.manifest().isComplete());
        assertTrue(result.facts().isEmpty());
        verify(facts, never()).saveAll(any());
    }

    @Test
    void rejectsNonCanonicalAverageFactOrder() {
        var later = monthlyAverage(21L, 11L, 41L);
        var earlier = monthlyAverage(20L, 10L, 40L);
        assertThrows(IllegalStateException.class,
                () -> service.freeze(snapshot, List.of(later, earlier), List.of()));
    }

    @Test
    void rejectsNatureWithoutMatchingAverageOrContradictoryIdentity() {
        var average = monthlyAverage(20L, 10L, 40L);
        var unrelated = new PayrollBonusP15NatureFactService.NatureFact(
                30L, 999L, 999L, 999L, PayrollEarningKind.MONTHLY_BONUS, PayrollBonusP15Nature.MONTHLY
        );
        assertThrows(IllegalStateException.class,
                () -> service.freeze(snapshot, List.of(average), List.of(unrelated)));

        var wrongComponent = new PayrollBonusP15NatureFactService.NatureFact(
                31L, 20L, 10L, 999L, PayrollEarningKind.MONTHLY_BONUS, PayrollBonusP15Nature.MONTHLY
        );
        assertThrows(IllegalStateException.class,
                () -> service.freeze(snapshot, List.of(average), List.of(wrongComponent)));
    }

    @Test
    void rejectsNatureThatContradictsFrozenF1AwardFacts() {
        var oneMonthOneTime = new PayrollBonusAverageEarningsFactService.AverageFact(
                20L, 10L, 40L, PayrollEarningKind.ONE_TIME_BONUS,
                "KPI", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, true, false
        );
        var workPeriod = nature(30L, oneMonthOneTime, PayrollBonusP15Nature.WORK_PERIOD);
        assertThrows(IllegalStateException.class,
                () -> service.freeze(snapshot, List.of(oneMonthOneTime), List.of(workPeriod)));
    }

    private static PayrollBonusAverageEarningsFactService.AverageFact monthlyAverage(long id, long sourceId, long componentId) {
        return new PayrollBonusAverageEarningsFactService.AverageFact(
                id, sourceId, componentId, PayrollEarningKind.MONTHLY_BONUS,
                "MONTHLY_KPI", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, true, false
        );
    }

    private static PayrollBonusP15NatureFactService.NatureFact nature(
            long id, PayrollBonusAverageEarningsFactService.AverageFact average, PayrollBonusP15Nature nature
    ) {
        return new PayrollBonusP15NatureFactService.NatureFact(
                id, average.factId(), average.bonusSourceFactId(), average.componentId(), average.earningKind(), nature
        );
    }
}
