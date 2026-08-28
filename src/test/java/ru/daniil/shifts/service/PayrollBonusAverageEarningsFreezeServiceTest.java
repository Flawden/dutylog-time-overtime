package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotBonusAverageEarningsManifestRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollBonusAverageEarningsFreezeServiceTest {

    private final PayrollSnapshotBonusAverageEarningsFactRepository facts =
            mock(PayrollSnapshotBonusAverageEarningsFactRepository.class);
    private final PayrollSnapshotBonusAverageEarningsManifestRepository manifests =
            mock(PayrollSnapshotBonusAverageEarningsManifestRepository.class);
    private final PayrollSnapshot snapshot = mock(PayrollSnapshot.class);

    private final PayrollBonusAverageEarningsFreezeService service =
            new PayrollBonusAverageEarningsFreezeService(facts, manifests);

    @BeforeEach
    void snapshotIdentity() {
        when(snapshot.getPeriodMonth()).thenReturn(LocalDate.of(2026, 3, 1));
        when(snapshot.getCurrencyCode()).thenReturn("RUB");
    }

    @Test
    void completeFreezeCopiesSourceAndF1FactsWithoutDefaultingUnknownFlags() {
        var source = source(10L, 20L, PayrollEarningKind.MONTHLY_BONUS, 123_400L);
        var average = average(30L, 10L, 20L, PayrollEarningKind.MONTHLY_BONUS);

        var result = service.freeze(
                snapshot,
                List.of(source),
                List.of(average)
        );

        assertEquals(1, result.facts().size());
        var frozen = result.facts().get(0);
        assertEquals(10L, frozen.getBonusSourceFactId());
        assertEquals(30L, frozen.getBonusAverageFactId());
        assertEquals(123_400L, frozen.getAmountMinor());
        assertEquals(LocalDate.of(2026, 3, 31), frozen.getSourcePeriodTo());
        assertEquals("MONTHLY_KPI", frozen.getIndicatorKey());
        assertNull(frozen.getAnnualResult());
        assertEquals(Boolean.TRUE, frozen.getAccruedForActualWorkTime());
        assertNull(frozen.getProratedForPartialAwardPeriod());

        assertTrue(result.manifest().isComplete());
        assertEquals(1, result.manifest().getSourceFactCount());
        assertEquals(1, result.manifest().getFactCount());
        assertEquals(
                PayrollBonusAverageEarningsFingerprint.calculate(1, result.facts()),
                result.manifest().getFingerprint()
        );

        verify(facts).saveAll(result.facts());
        verify(manifests).saveAndFlush(result.manifest());
    }

    @Test
    void missingF1FactCreatesIncompleteManifestInsteadOfSyntheticDefaults() {
        var first = source(10L, 20L, PayrollEarningKind.MONTHLY_BONUS, 100L);
        var second = source(11L, 21L, PayrollEarningKind.ONE_TIME_BONUS, 200L);

        var result = service.freeze(
                snapshot,
                List.of(first, second),
                List.of(average(30L, 10L, 20L, PayrollEarningKind.MONTHLY_BONUS))
        );

        assertEquals(1, result.facts().size());
        assertFalse(result.manifest().isComplete());
        assertEquals(2, result.manifest().getSourceFactCount());
        assertEquals(1, result.manifest().getFactCount());
    }

    @Test
    void zeroBonusSourceFactsPersistCompleteEmptyManifest() {
        var result = service.freeze(snapshot, List.of(), List.of());

        assertTrue(result.facts().isEmpty());
        assertTrue(result.manifest().isComplete());
        assertEquals(0, result.manifest().getSourceFactCount());
        assertEquals(0, result.manifest().getFactCount());
        verify(facts, never()).saveAll(any());
        verify(manifests).saveAndFlush(result.manifest());
    }

    @Test
    void rejectsNonCanonicalD2SourceOrder() {
        var laterKind = source(
                11L,
                21L,
                PayrollEarningKind.ONE_TIME_BONUS,
                200L
        );
        var earlierKind = source(
                10L,
                20L,
                PayrollEarningKind.MONTHLY_BONUS,
                100L
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.freeze(
                        snapshot,
                        List.of(laterKind, earlierKind),
                        List.of()
                )
        );
    }

    @Test
    void rejectsAverageFactWithoutMatchingSourceOrWithContradictoryIdentity() {
        assertThrows(
                IllegalStateException.class,
                () -> service.freeze(
                        snapshot,
                        List.of(source(10L, 20L, PayrollEarningKind.MONTHLY_BONUS, 100L)),
                        List.of(average(30L, 999L, 20L, PayrollEarningKind.MONTHLY_BONUS))
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.freeze(
                        snapshot,
                        List.of(source(10L, 20L, PayrollEarningKind.MONTHLY_BONUS, 100L)),
                        List.of(average(30L, 10L, 21L, PayrollEarningKind.MONTHLY_BONUS))
                )
        );
    }

    @Test
    void rejectsSourceMonthOrCurrencyThatContradictsSnapshot() {
        var wrongMonth = new PayrollBonusSourceFactService.BonusFact(
                10L,
                20L,
                PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                100L,
                "RUB"
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.freeze(snapshot, List.of(wrongMonth), List.of())
        );

        var wrongCurrency = new PayrollBonusSourceFactService.BonusFact(
                10L,
                20L,
                PayrollEarningKind.MONTHLY_BONUS,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                100L,
                "USD"
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.freeze(snapshot, List.of(wrongCurrency), List.of())
        );
    }

    private static PayrollBonusSourceFactService.BonusFact source(
            long factId,
            long componentId,
            PayrollEarningKind kind,
            long amountMinor
    ) {
        return new PayrollBonusSourceFactService.BonusFact(
                factId,
                componentId,
                kind,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                amountMinor,
                "RUB"
        );
    }

    private static PayrollBonusAverageEarningsFactService.AverageFact average(
            long factId,
            long sourceFactId,
            long componentId,
            PayrollEarningKind kind
    ) {
        return new PayrollBonusAverageEarningsFactService.AverageFact(
                factId,
                sourceFactId,
                componentId,
                kind,
                "MONTHLY_KPI",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                null,
                true,
                null
        );
    }
}
