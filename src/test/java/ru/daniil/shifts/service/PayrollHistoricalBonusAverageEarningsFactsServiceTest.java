package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsFact;
import ru.daniil.shifts.model.PayrollSnapshotBonusAverageEarningsManifest;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsManifestRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollHistoricalBonusAverageEarningsFactsServiceTest {

    private final PayrollSnapshotBonusAverageEarningsFactRepository facts =
            mock(PayrollSnapshotBonusAverageEarningsFactRepository.class);
    private final PayrollSnapshotBonusAverageEarningsManifestRepository manifests =
            mock(PayrollSnapshotBonusAverageEarningsManifestRepository.class);
    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);

    private final PayrollHistoricalBonusAverageEarningsFactsService service =
            new PayrollHistoricalBonusAverageEarningsFactsService(facts, manifests);

    @BeforeEach
    void snapshotIdentity() {
        when(snapshot.getPeriodMonth()).thenReturn(LocalDate.of(2026, 3, 1));
        when(snapshot.getRevision()).thenReturn(7);
        when(snapshot.getCurrencyCode()).thenReturn("RUB");
    }

    @Test
    void completeManifestReturnsVerifiedImmutableFacts() {
        PayrollSnapshotBonusAverageEarningsFact frozen = fact("RUB", null);
        String fingerprint =
                PayrollBonusAverageEarningsFingerprint.calculate(1, List.of(frozen));
        PayrollSnapshotBonusAverageEarningsManifest manifest =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        1,
                        1,
                        fingerprint
                );

        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot))
                .thenReturn(List.of(frozen));

        var result = service.resolve(snapshot);

        assertTrue(result.ready());
        assertNull(result.blockingReason());
        assertEquals(1, result.facts().size());
        assertEquals(101L, result.facts().get(0).bonusSourceFactId());
        assertNull(result.facts().get(0).annualResult());
        assertEquals(Boolean.TRUE, result.facts().get(0).accruedForActualWorkTime());
    }

    @Test
    void legacySnapshotWithoutManifestIsExplicitlyBlocked() {
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.empty());

        var result = service.resolve(snapshot);

        assertFalse(result.ready());
        assertEquals(
                "HISTORICAL_BONUS_AVERAGE_EARNINGS_MANIFEST_MISSING",
                result.blockingReason()
        );
        assertTrue(result.facts().isEmpty());
        verifyNoInteractions(facts);
    }

    @Test
    void incompleteManifestBlocksWithoutReadingPartialFacts() {
        PayrollSnapshotBonusAverageEarningsManifest manifest =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        2,
                        1,
                        "0".repeat(64)
                );
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));

        var result = service.resolve(snapshot);

        assertFalse(result.ready());
        assertEquals(
                "HISTORICAL_BONUS_AVERAGE_EARNINGS_INCOMPLETE",
                result.blockingReason()
        );
        verifyNoInteractions(facts);
    }

    @Test
    void fingerprintMismatchBlocksTamperedHistory() {
        PayrollSnapshotBonusAverageEarningsFact frozen = fact("RUB", null);
        PayrollSnapshotBonusAverageEarningsManifest manifest =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        1,
                        1,
                        "f".repeat(64)
                );

        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot))
                .thenReturn(List.of(frozen));

        var result = service.resolve(snapshot);

        assertFalse(result.ready());
        assertEquals(
                "HISTORICAL_BONUS_AVERAGE_EARNINGS_FINGERPRINT_MISMATCH",
                result.blockingReason()
        );
    }

    @Test
    void sourceCurrencyContradictionBlocksEvenWithMatchingFingerprint() {
        PayrollSnapshotBonusAverageEarningsFact frozen = fact("USD", null);
        String fingerprint =
                PayrollBonusAverageEarningsFingerprint.calculate(1, List.of(frozen));
        PayrollSnapshotBonusAverageEarningsManifest manifest =
                new PayrollSnapshotBonusAverageEarningsManifest(
                        snapshot,
                        1,
                        1,
                        fingerprint
                );

        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(facts.findBySnapshotOrderByFactIndexAsc(snapshot))
                .thenReturn(List.of(frozen));

        var result = service.resolve(snapshot);

        assertFalse(result.ready());
        assertEquals(
                "HISTORICAL_BONUS_AVERAGE_EARNINGS_SOURCE_IDENTITY_MISMATCH",
                result.blockingReason()
        );
    }

    private PayrollSnapshotBonusAverageEarningsFact fact(
            String currency,
            Boolean annualResult
    ) {
        return new PayrollSnapshotBonusAverageEarningsFact(
                snapshot,
                0,
                101L,
                201L,
                301L,
                PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                450_000L,
                currency,
                "MONTHLY_KPI",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                annualResult,
                true,
                null
        );
    }
}
