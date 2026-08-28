package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollEarningKind;
import ru.daniil.shifts.model.PayrollQualifiedQuantity;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotEarningLine;
import ru.daniil.shifts.model.PayrollSnapshotEarningManifest;
import ru.daniil.shifts.repo.PayrollSnapshotEarningLineRepository;
import ru.daniil.shifts.repo.PayrollSnapshotEarningManifestRepository;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollHistoricalSemanticEarningsServiceTest {

    @Mock
    private PayrollSnapshotRepository snapshots;

    @Mock
    private PayrollSnapshotEarningLineRepository lines;

    @Mock
    private PayrollSnapshotEarningManifestRepository manifests;

    private final AppUser user =
            mock(
                    AppUser.class
            );

    private PayrollHistoricalSemanticEarningsService service;

    @BeforeEach
    void setUp() {
        service =
                new PayrollHistoricalSemanticEarningsService(
                        snapshots,
                        lines,
                        manifests
                );
    }

    @Test
    void readyHistoryUsesExactlyTwelveCalendarMonthsBeforeEventMonth() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                YearMonth.of(
                        2025,
                        9
                ),
                result.referenceFrom()
        );

        assertEquals(
                YearMonth.of(
                        2026,
                        8
                ),
                result.referenceTo()
        );

        assertEquals(
                12,
                result.months()
                        .size()
        );

        assertEquals(
                "RUB",
                result.currencyCode()
        );

        assertEquals(
                YearMonth.of(
                        2025,
                        9
                ),
                result.months()
                        .get(0)
                        .period()
        );

        assertEquals(
                YearMonth.of(
                        2026,
                        8
                ),
                result.months()
                        .get(11)
                        .period()
        );

        verify(
                snapshots
        ).findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user,
                LocalDate.of(
                        2025,
                        9,
                        1
                ),
                LocalDate.of(
                        2026,
                        8,
                        1
                )
        );
    }

    @Test
    void latestRevisionWinsWithinEachReferenceMonth() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        YearMonth revisedMonth =
                YearMonth.of(
                        2026,
                        1
                );

        PayrollSnapshot revision2 =
                snapshot(
                        revisedMonth,
                        2,
                        "RUB"
                );

        lenient().when(
                lines.findBySnapshotOrderByLineIndexAsc(
                        revision2
                )
        ).thenReturn(
                List.of(
                        earning(
                                revision2,
                                0,
                                PayrollEarningKind.BASE_PAY,
                                6_054_800L
                        )
                )
        );

        List<PayrollSnapshotEarningLine> revision2Frozen =
                lines.findBySnapshotOrderByLineIndexAsc(
                        revision2
                );

        stubSnapshotEarningSourcesFromLines(
                revision2,
                revision2Frozen
        );

        when(
                manifests.findBySnapshot(
                        revision2
                )
        ).thenReturn(
                java.util.Optional.of(
                        manifest(
                                revision2,
                                true,
                                revision2Frozen
                        )
                )
        );

        history.add(
                revision2
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertTrue(
                result.ready()
        );

        var january =
                result.months()
                        .stream()
                        .filter(month ->
                                revisedMonth.equals(
                                        month.period()
                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                2,
                january.snapshotRevision()
        );
    }

    @Test
    void missingSnapshotBlocksWithoutPartialHistory() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        history.remove(
                5
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_PAYROLL_SNAPSHOT_MISSING",
                result.blockingReason()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );

        assertNull(
                result.currencyCode()
        );
    }

    @Test
    void legacySnapshotWithoutSemanticLinesBlocksInsteadOfBacksolving() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot legacy =
                history.get(
                        4
                );

        lenient().when(
                lines.findBySnapshotOrderByLineIndexAsc(
                        legacy
                )
        ).thenReturn(
                List.of()
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_SEMANTIC_EARNINGS_COUNT_MISMATCH",
                result.blockingReason()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    @Test
    void currencyMismatchBlocksWholeReferenceWindow() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        YearMonth mismatchMonth =
                YearMonth.of(
                        2026,
                        3
                );

        PayrollSnapshot mismatch =
                snapshot(
                        mismatchMonth,
                        2,
                        "USD"
                );

        lenient().when(
                lines.findBySnapshotOrderByLineIndexAsc(
                        mismatch
                )
        ).thenReturn(
                List.of(
                        earning(
                                mismatch,
                                0,
                                PayrollEarningKind.BASE_PAY,
                                1L
                        )
                )
        );

        history.add(
                mismatch
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_PAYROLL_CURRENCY_MISMATCH",
                result.blockingReason()
        );

        assertEquals(
                mismatchMonth,
                result.blockingPeriod()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    @Test
    void semanticQuantityEarningPeriodAndCoverageSurviveReadModel() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot august =
                history.stream()
                        .filter(snapshot ->
                                YearMonth.of(
                                                2026,
                                                8
                                        )
                                        .equals(
                                                YearMonth.from(
                                                        snapshot.getPeriodMonth()
                                                )
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        PayrollSnapshotEarningLine monthly =
                new PayrollSnapshotEarningLine(
                        august,
                        0,
                        PayrollEarningKind.MONTHLY_BONUS,
                        2_518_797L,
                        PayrollQualifiedQuantity.minutes(
                                10_000
                        ),
                        LocalDate.of(
                                2026,
                                8,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                8,
                                31
                        ),
                        null,
                        null
                );

        PayrollSnapshotEarningLine vacation =
                new PayrollSnapshotEarningLine(
                        august,
                        1,
                        PayrollEarningKind.VACATION_PAY,
                        5_160_988L,
                        PayrollQualifiedQuantity.calendarDays(
                                14
                        ),
                        null,
                        null,
                        LocalDate.of(
                                2026,
                                9,
                                1
                        ),
                        LocalDate.of(
                                2026,
                                9,
                                14
                        )
                );

        lenient().when(
                lines.findBySnapshotOrderByLineIndexAsc(
                        august
                )
        ).thenReturn(
                List.of(
                        monthly,
                        vacation
                )
        );

        List<PayrollSnapshotEarningLine> augustFrozen =
                List.of(
                        monthly,
                        vacation
                );

        stubSnapshotEarningSourcesFromLines(
                august,
                augustFrozen
        );

        when(
                manifests.findBySnapshot(
                        august
                )
        ).thenReturn(
                java.util.Optional.of(
                        manifest(
                                august,
                                true,
                                augustFrozen
                        )
                )
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertTrue(
                result.ready()
        );

        var augustResult =
                result.months()
                        .get(
                                11
                        );

        assertEquals(
                2,
                augustResult.earnings()
                        .size()
        );

        var monthlyResult =
                augustResult.earnings()
                        .get(
                                0
                        );

        assertEquals(
                PayrollEarningKind.MONTHLY_BONUS,
                monthlyResult.kind()
        );

        assertEquals(
                "MINUTES",
                monthlyResult
                        .qualifiedQuantity()
                        .unit()
                        .name()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        1
                ),
                monthlyResult.earningPeriodFrom()
        );

        var vacationResult =
                augustResult.earnings()
                        .get(
                                1
                        );

        assertEquals(
                PayrollEarningKind.VACATION_PAY,
                vacationResult.kind()
        );

        assertEquals(
                14L,
                vacationResult
                        .qualifiedQuantity()
                        .value()
        );

        assertEquals(
                "CALENDAR_DAYS",
                vacationResult
                        .qualifiedQuantity()
                        .unit()
                        .name()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        9,
                        14
                ),
                vacationResult.coverageTo()
        );
    }


    @Test
    void manifestMissingBlocksEvenWhenSemanticLinesExist() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot target =
                history.get(
                        3
                );

        when(
                manifests.findBySnapshot(
                        target
                )
        ).thenReturn(
                java.util.Optional.empty()
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_SEMANTIC_EARNINGS_MANIFEST_MISSING",
                result.blockingReason()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    @Test
    void incompleteManifestBlocksPartialFreeze() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot target =
                history.get(
                        3
                );

        List<PayrollSnapshotEarningLine> frozen =
                lines.findBySnapshotOrderByLineIndexAsc(
                        target
                );

        when(
                manifests.findBySnapshot(
                        target
                )
        ).thenReturn(
                java.util.Optional.of(
                        manifest(
                                target,
                                false,
                                frozen
                        )
                )
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_SEMANTIC_EARNINGS_INCOMPLETE",
                result.blockingReason()
        );
    }

    @Test
    void fingerprintMismatchBlocksWholeReferenceWindow() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot target =
                history.get(
                        2
                );

        List<PayrollSnapshotEarningLine> frozen =
                lines.findBySnapshotOrderByLineIndexAsc(
                        target
                );

        long amount =
                frozen
                        .stream()
                        .mapToLong(
                                PayrollSnapshotEarningLine::getAmountMinor
                        )
                        .sum();

        when(
                manifests.findBySnapshot(
                        target
                )
        ).thenReturn(
                java.util.Optional.of(
                        new PayrollSnapshotEarningManifest(
                                target,
                                true,
                                frozen.size(),
                                amount,
                                "f".repeat(
                                        64
                                )
                        )
                )
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_SEMANTIC_EARNINGS_FINGERPRINT_MISMATCH",
                result.blockingReason()
        );
    }

    @Test
    void completeZeroEarningMonthIsValidHistoricalTruth() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot target =
                history.get(
                        7
                );

        List<PayrollSnapshotEarningLine> empty =
                List.of();

        stubSnapshotEarningSourcesFromLines(
                target,
                empty
        );

        when(
                lines.findBySnapshotOrderByLineIndexAsc(
                        target
                )
        ).thenReturn(
                empty
        );

        when(
                manifests.findBySnapshot(
                        target
                )
        ).thenReturn(
                java.util.Optional.of(
                        manifest(
                                target,
                                true,
                                empty
                        )
                )
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertTrue(
                result.ready()
        );

        assertTrue(
                result.months()
                        .get(
                                7
                        )
                        .earnings()
                        .isEmpty()
        );
    }


    @Test
    void snapshotSourceTotalMismatchBlocksCompleteManifest() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        PayrollSnapshot target =
                history.get(
                        4
                );

        /*
         * Manifest + semantic lines remain internally consistent, but the
         * immutable Payroll snapshot contains one additional earning ruble.
         *
         * Historical read must therefore reject the month as incomplete
         * semantic coverage instead of trusting the manifest declaration.
         */
        when(
                target.getAdditionsMinor()
        ).thenReturn(
                1L
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(
                                                12
                                        )
                                        .atDay(
                                                1
                                        ),
                                event.minusMonths(
                                                1
                                        )
                                        .atDay(
                                                1
                                        )
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolve(
                        user,
                        event
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_SEMANTIC_EARNINGS_SOURCE_TOTAL_MISMATCH",
                result.blockingReason()
        );

        assertEquals(
                event.minusMonths(
                                8
                        ),
                result.blockingPeriod()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    @Test
    void requiredMonthsCanOmitProvenNonEmploymentMonthsWithoutWeakeningSnapshotChecks() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(12).atDay(1),
                                event.minusMonths(1).atDay(1)
                        )
        ).thenReturn(
                history
        );

        List<YearMonth> required =
                List.of(
                        YearMonth.of(2026, 6),
                        YearMonth.of(2026, 7),
                        YearMonth.of(2026, 8)
                );

        var result =
                service.resolveRequiredMonths(
                        user,
                        event,
                        required
                );

        assertTrue(
                result.ready()
        );

        assertEquals(
                required,
                result.requiredMonths()
        );

        assertEquals(
                required,
                result.months()
                        .stream()
                        .map(
                                PayrollHistoricalSemanticEarningsService.HistoricalMonth::period
                        )
                        .toList()
        );

        assertEquals(
                "RUB",
                result.currencyCode()
        );
    }

    @Test
    void missingRequiredMonthStillBlocksWithoutPartialHistory() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        List<PayrollSnapshot> history =
                completeHistory(
                        event,
                        "RUB"
                );

        YearMonth missing =
                YearMonth.of(
                        2026,
                        7
                );

        history.removeIf(snapshot ->
                missing.equals(
                        YearMonth.from(
                                snapshot.getPeriodMonth()
                        )
                )
        );

        when(
                snapshots
                        .findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                                user,
                                event.minusMonths(12).atDay(1),
                                event.minusMonths(1).atDay(1)
                        )
        ).thenReturn(
                history
        );

        var result =
                service.resolveRequiredMonths(
                        user,
                        event,
                        List.of(
                                missing,
                                YearMonth.of(2026, 8)
                        )
                );

        assertFalse(
                result.ready()
        );

        assertEquals(
                "HISTORICAL_PAYROLL_SNAPSHOT_MISSING",
                result.blockingReason()
        );

        assertEquals(
                missing,
                result.blockingPeriod()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );
    }

    @Test
    void emptyRequiredMonthSetIsReadyZeroHistoryWithoutRepositoryInference() {
        YearMonth event =
                YearMonth.of(
                        2026,
                        9
                );

        var result =
                service.resolveRequiredMonths(
                        user,
                        event,
                        List.of()
                );

        assertTrue(
                result.ready()
        );

        assertTrue(
                result.requiredMonths()
                        .isEmpty()
        );

        assertTrue(
                result.months()
                        .isEmpty()
        );

        assertNull(
                result.currencyCode()
        );

        org.mockito.Mockito.verifyNoInteractions(
                snapshots,
                lines,
                manifests
        );
    }

    private List<PayrollSnapshot> completeHistory(
            YearMonth event,
            String currency
    ) {
        List<PayrollSnapshot> result =
                new ArrayList<>();

        for (int offset = 12;
                offset >= 1;
                offset--) {

            YearMonth period =
                    event.minusMonths(
                            offset
                    );

            PayrollSnapshot snapshot =
                    snapshot(
                            period,
                            1,
                            currency
                    );

            PayrollSnapshotEarningLine frozenLine =
                    earning(
                            snapshot,
                            0,
                            PayrollEarningKind.BASE_PAY,
                            1_000L
                                    + offset
                    );

            List<PayrollSnapshotEarningLine> frozenLines =
                    List.of(
                            frozenLine
                    );

            stubSnapshotEarningSourcesFromLines(
                    snapshot,
                    frozenLines
            );

            lenient().when(
                    lines.findBySnapshotOrderByLineIndexAsc(
                            snapshot
                    )
            ).thenReturn(
                    frozenLines
            );

            lenient().when(
                    manifests.findBySnapshot(
                            snapshot
                    )
            ).thenReturn(
                    java.util.Optional.of(
                            manifest(
                                    snapshot,
                                    true,
                                    frozenLines
                            )
                    )
            );

            result.add(
                    snapshot
            );
        }

        return result;
    }

    private PayrollSnapshot snapshot(
            YearMonth period,
            int revision,
            String currency
    ) {
        PayrollSnapshot snapshot =
                mock(
                        PayrollSnapshot.class
                );

        lenient().when(
                snapshot.getPeriodMonth()
        ).thenReturn(
                period.atDay(
                        1
                )
        );

        lenient().when(
                snapshot.getRevision()
        ).thenReturn(
                revision
        );

        lenient().when(
                snapshot.getCurrencyCode()
        ).thenReturn(
                currency
        );

        lenient().when(
                snapshot.getSupersededBy()
        ).thenReturn(
                null
        );

        return snapshot;
    }

    private PayrollSnapshotEarningLine earning(
            PayrollSnapshot snapshot,
            int index,
            PayrollEarningKind kind,
            long amount
    ) {
        return new PayrollSnapshotEarningLine(
                snapshot,
                index,
                kind,
                amount,
                null,
                null,
                null,
                null,
                null
        );
    }


    /**
     * Frozen Payroll aggregate fixture corresponding to one semantic line set.
     *
     * This helper MUST be called before starting repository Mockito stubbing.
     * Keeping it separate from manifest(...) prevents nested Mockito stubbing
     * inside thenReturn(...).
     */
    private void stubSnapshotEarningSourcesFromLines(
            PayrollSnapshot snapshot,
            List<PayrollSnapshotEarningLine> frozenLines
    ) {
        long amount =
                frozenLines
                        .stream()
                        .mapToLong(
                                PayrollSnapshotEarningLine::getAmountMinor
                        )
                        .sum();

        stubSnapshotEarningSources(
                snapshot,
                amount,
                0L,
                0L,
                0L,
                0L
        );
    }

    private void stubSnapshotEarningSources(
            PayrollSnapshot snapshot,
            long basePayMinor,
            long ordinaryPremiumPayMinor,
            long settlementPayMinor,
            long compensationComponentEarningsMinor,
            long additionsMinor
    ) {
        lenient().when(
                snapshot.getBasePayMinor()
        ).thenReturn(
                basePayMinor
        );

        lenient().when(
                snapshot.getOrdinaryPremiumPayMinor()
        ).thenReturn(
                ordinaryPremiumPayMinor
        );

        lenient().when(
                snapshot.getSettlementPayMinor()
        ).thenReturn(
                settlementPayMinor
        );

        lenient().when(
                snapshot.getCompensationComponentEarningsMinor()
        ).thenReturn(
                compensationComponentEarningsMinor
        );

        lenient().when(
                snapshot.getAdditionsMinor()
        ).thenReturn(
                additionsMinor
        );
    }

    private PayrollSnapshotEarningManifest manifest(
            PayrollSnapshot snapshot,
            boolean complete,
            List<PayrollSnapshotEarningLine> frozenLines
    ) {
        long amount =
                frozenLines
                        .stream()
                        .mapToLong(
                                PayrollSnapshotEarningLine::getAmountMinor
                        )
                        .sum();

        return new PayrollSnapshotEarningManifest(
                snapshot,
                complete,
                frozenLines.size(),
                amount,
                PayrollSemanticEarningFingerprint.calculate(
                        frozenLines
                )
        );
    }

}
