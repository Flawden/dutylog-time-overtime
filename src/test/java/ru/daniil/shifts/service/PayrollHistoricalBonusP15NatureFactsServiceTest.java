package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollHistoricalBonusP15NatureFactsServiceTest {

    private final PayrollHistoricalBonusAverageEarningsFactsService average = mock(PayrollHistoricalBonusAverageEarningsFactsService.class);
    private final PayrollSnapshotBonusP15NatureFactRepository facts = mock(PayrollSnapshotBonusP15NatureFactRepository.class);
    private final PayrollSnapshotBonusP15NatureManifestRepository manifests = mock(PayrollSnapshotBonusP15NatureManifestRepository.class);
    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);
    private final PayrollHistoricalBonusP15NatureFactsService service = new PayrollHistoricalBonusP15NatureFactsService(average, facts, manifests);

    @Test
    void completeManifestReturnsVerifiedCombinedImmutableFact() {
        var baseFact = monthlyHistorical();
        when(average.resolve(snapshot)).thenReturn(ready(baseFact));
        var frozen = frozen(PayrollBonusP15Nature.MONTHLY, PayrollEarningKind.MONTHLY_BONUS);
        var manifest = manifest(1, 1, PayrollBonusP15NatureFingerprint.calculate(1, List.of(frozen)));
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(frozen));

        var result = service.resolve(snapshot);
        assertTrue(result.ready());
        assertEquals(PayrollBonusP15Nature.MONTHLY, result.facts().get(0).p15Nature());
        assertEquals(30L, result.facts().get(0).bonusNatureFactId());
        assertEquals(450_000L, result.facts().get(0).bonus().amountMinor());
    }

    @Test
    void propagatesF2BlockerWithoutReadingNatureTables() {
        when(average.resolve(snapshot)).thenReturn(new PayrollHistoricalBonusAverageEarningsFactsService.Resolution(
                false, "HISTORICAL_BONUS_AVERAGE_EARNINGS_INCOMPLETE", List.of()
        ));
        var result = service.resolve(snapshot);
        assertFalse(result.ready());
        assertEquals("HISTORICAL_BONUS_AVERAGE_EARNINGS_INCOMPLETE", result.blockingReason());
        verifyNoInteractions(manifests, facts);
    }

    @Test
    void missingNatureManifestBlocksWhenHistoricalBonusExists() {
        when(average.resolve(snapshot)).thenReturn(ready(monthlyHistorical()));
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.empty());
        var result = service.resolve(snapshot);
        assertEquals("HISTORICAL_BONUS_P15_NATURE_MANIFEST_MISSING", result.blockingReason());
    }

    @Test
    void f2ProvenZeroBonusMonthNeedsNoLegacyNatureManifest() {
        when(average.resolve(snapshot)).thenReturn(new PayrollHistoricalBonusAverageEarningsFactsService.Resolution(true, null, List.of()));
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.empty());
        var result = service.resolve(snapshot);
        assertTrue(result.ready());
        assertTrue(result.facts().isEmpty());
        verifyNoInteractions(facts);
    }

    @Test
    void incompleteOrFingerprintMismatchBlocks() {
        when(average.resolve(snapshot)).thenReturn(ready(monthlyHistorical()));
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest(1, 0, "0".repeat(64))));
        assertEquals("HISTORICAL_BONUS_P15_NATURE_INCOMPLETE", service.resolve(snapshot).blockingReason());

        var frozen = frozen(PayrollBonusP15Nature.MONTHLY, PayrollEarningKind.MONTHLY_BONUS);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest(1, 1, "f".repeat(64))));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(frozen));
        assertEquals("HISTORICAL_BONUS_P15_NATURE_FINGERPRINT_MISMATCH", service.resolve(snapshot).blockingReason());
    }

    @Test
    void identityOrRewardNatureContradictionBlocks() {
        when(average.resolve(snapshot)).thenReturn(ready(monthlyHistorical()));
        var wrongIdentity = frozen(PayrollBonusP15Nature.ANNUAL_RESULT, PayrollEarningKind.ONE_TIME_BONUS);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest(1, 1,
                PayrollBonusP15NatureFingerprint.calculate(1, List.of(wrongIdentity)))));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(wrongIdentity));
        assertEquals("HISTORICAL_BONUS_P15_NATURE_IDENTITY_MISMATCH", service.resolve(snapshot).blockingReason());

        var impossible = mock(PayrollSnapshotBonusP15NatureFact.class);
        when(impossible.getFactIndex()).thenReturn(0);
        when(impossible.getBonusSourceFactId()).thenReturn(10L);
        when(impossible.getBonusAverageFactId()).thenReturn(20L);
        when(impossible.getBonusNatureFactId()).thenReturn(30L);
        when(impossible.getComponentId()).thenReturn(40L);
        when(impossible.getEarningKind()).thenReturn(PayrollEarningKind.MONTHLY_BONUS);
        when(impossible.getP15Nature()).thenReturn(PayrollBonusP15Nature.ANNUAL_RESULT);
        var impossibleFingerprint = PayrollBonusP15NatureFingerprint.calculate(1, List.of(impossible));
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest(1, 1, impossibleFingerprint)));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(impossible));
        assertEquals("HISTORICAL_BONUS_P15_NATURE_FACT_CONTRADICTION", service.resolve(snapshot).blockingReason());
    }

    private PayrollSnapshotBonusP15NatureManifest manifest(int averageCount, int natureCount, String fingerprint) {
        return new PayrollSnapshotBonusP15NatureManifest(snapshot, averageCount, natureCount, fingerprint);
    }

    private PayrollSnapshotBonusP15NatureFact frozen(PayrollBonusP15Nature nature, PayrollEarningKind kind) {
        return new PayrollSnapshotBonusP15NatureFact(snapshot, 0, 10L, 20L, 30L, 40L, kind, nature);
    }

    private PayrollHistoricalBonusAverageEarningsFactsService.Resolution ready(
            PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact fact
    ) {
        return new PayrollHistoricalBonusAverageEarningsFactsService.Resolution(true, null, List.of(fact));
    }

    private PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact monthlyHistorical() {
        return new PayrollHistoricalBonusAverageEarningsFactsService.HistoricalBonusFact(
                10L, 20L, 40L, PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 450_000L, "RUB",
                "MONTHLY_KPI", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                false, true, false
        );
    }
}
