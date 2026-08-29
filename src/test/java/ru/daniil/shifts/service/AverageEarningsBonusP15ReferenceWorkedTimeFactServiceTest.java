package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.PayrollSnapshot;
import ru.daniil.shifts.model.PayrollSnapshotP15ScheduledWorkFact;
import ru.daniil.shifts.model.PayrollSnapshotP15ScheduledWorkSourceKind;
import ru.daniil.shifts.model.PayrollSnapshotP15WorkTimeManifest;
import ru.daniil.shifts.model.WorkTimeAccountingMode;
import ru.daniil.shifts.repo.PayrollSnapshotP15ScheduledWorkFactRepository;
import ru.daniil.shifts.repo.PayrollSnapshotP15WorkTimeManifestRepository;
import ru.daniil.shifts.repo.PayrollSnapshotRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.WorkMeasureUnit.WORKING_DAYS;
import static ru.daniil.shifts.service.AverageEarningsBonusP15Formula.WorkMeasureUnit.WORKING_MINUTES;

class AverageEarningsBonusP15ReferenceWorkedTimeFactServiceTest {

    private PayrollSnapshotRepository snapshots;
    private PayrollSnapshotP15WorkTimeManifestRepository manifests;
    private PayrollSnapshotP15ScheduledWorkFactRepository factRepo;
    private AverageEarningsBonusP15ReferenceWorkedTimeFactService service;
    private AppUser user;

    private final LocalDate eventDate = LocalDate.of(2026, 8, 15);
    private final YearMonth eventMonth = YearMonth.of(2026, 8);
    private final YearMonth referenceFrom = YearMonth.of(2025, 8);
    private final YearMonth referenceTo = YearMonth.of(2026, 7);

    @BeforeEach
    void setUp() {
        snapshots = mock(PayrollSnapshotRepository.class);
        manifests = mock(PayrollSnapshotP15WorkTimeManifestRepository.class);
        factRepo = mock(PayrollSnapshotP15ScheduledWorkFactRepository.class);
        user = mock(AppUser.class);
        service = new AverageEarningsBonusP15ReferenceWorkedTimeFactService(
                snapshots,
                manifests,
                factRepo
        );
    }

    @Test
    void summarizedUsesOnlyScheduledIntersectionAndKeepsOvertimeOutsideCoefficient() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 101L, 3);
        List<PayrollSnapshotP15ScheduledWorkFact> frozen = List.of(
                fact(snapshot, 0, month.atDay(5), 11L, WorkTimeAccountingMode.SUMMARIZED,
                        480, 0, 480, 0),
                fact(snapshot, 1, month.atDay(6), 11L, WorkTimeAccountingMode.SUMMARIZED,
                        480, 480, 0, 480)
        );
        freeze(snapshot, frozen);
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user, referenceFrom.atDay(1), referenceTo.atDay(1)
        )).thenReturn(List.of(snapshot));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertTrue(result.ready());
        assertEquals(WORKING_MINUTES, result.referenceWorkedTime().unit());
        assertEquals(480L, result.referenceWorkedTime().workedUnits());
        assertEquals(960L, result.referenceWorkedTime().normUnits());
        assertFalse(result.scheduleFullyWorked());
        assertEquals(12, result.months().size());
        var resolved = result.months().stream()
                .filter(row -> row.month().equals(month))
                .findFirst()
                .orElseThrow();
        assertEquals(3, resolved.snapshotRevision());
        assertEquals(480L, resolved.workedUnits());
        assertEquals(960L, resolved.normUnits());
    }

    @Test
    void dailyCountsWorkingDaysRatherThanScheduledMinutes() {
        YearMonth month = YearMonth.of(2026, 2);
        PayrollSnapshot snapshot = snapshot(month, 102L, 1);
        List<PayrollSnapshotP15ScheduledWorkFact> frozen = List.of(
                fact(snapshot, 0, month.atDay(2), 21L, WorkTimeAccountingMode.DAILY,
                        480, 480, 0, 0),
                fact(snapshot, 1, month.atDay(3), 21L, WorkTimeAccountingMode.DAILY,
                        720, 0, 720, 300),
                fact(snapshot, 2, month.atDay(4), 21L, WorkTimeAccountingMode.DAILY,
                        0, 0, 0, 600)
        );
        freeze(snapshot, frozen);
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertTrue(result.ready());
        assertEquals(WORKING_DAYS, result.referenceWorkedTime().unit());
        assertEquals(1L, result.referenceWorkedTime().workedUnits());
        assertEquals(2L, result.referenceWorkedTime().normUnits());
        assertFalse(result.scheduleFullyWorked());
    }

    @Test
    void dailyPartialScheduledDayBlocksInsteadOfRoundingToZeroOrOne() {
        YearMonth month = YearMonth.of(2026, 3);
        PayrollSnapshot snapshot = snapshot(month, 103L, 1);
        List<PayrollSnapshotP15ScheduledWorkFact> frozen = List.of(
                fact(snapshot, 0, month.atDay(10), 31L, WorkTimeAccountingMode.DAILY,
                        480, 240, 240, 0)
        );
        freeze(snapshot, frozen);
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.DAILY_PARTIAL_DAY_UNRESOLVED
                        + ":2026-03-10",
                result.blockingReason()
        );
        assertEquals(month, result.blockingPeriod());
        assertNull(result.referenceWorkedTime());
        assertTrue(result.months().isEmpty());
    }

    @Test
    void mixedModesInsideOnePayrollMonthBlockWithoutPartialAuthority() {
        YearMonth month = YearMonth.of(2026, 4);
        PayrollSnapshot snapshot = snapshot(month, 104L, 1);
        List<PayrollSnapshotP15ScheduledWorkFact> frozen = List.of(
                fact(snapshot, 0, month.atDay(1), 41L, WorkTimeAccountingMode.DAILY,
                        480, 480, 0, 0),
                fact(snapshot, 1, month.atDay(2), 42L, WorkTimeAccountingMode.SUMMARIZED,
                        480, 480, 0, 0)
        );
        freeze(snapshot, frozen);
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.MIXED_ACCOUNTING_MODE,
                result.blockingReason()
        );
        assertTrue(result.months().isEmpty());
    }

    @Test
    void mixedModesAcrossReferenceMonthsAlsoBlock() {
        YearMonth firstMonth = YearMonth.of(2026, 4);
        YearMonth secondMonth = YearMonth.of(2026, 5);
        PayrollSnapshot first = snapshot(firstMonth, 105L, 1);
        PayrollSnapshot second = snapshot(secondMonth, 106L, 1);
        freeze(first, List.of(
                fact(first, 0, firstMonth.atDay(1), 51L, WorkTimeAccountingMode.DAILY,
                        480, 480, 0, 0)
        ));
        freeze(second, List.of(
                fact(second, 0, secondMonth.atDay(1), 52L, WorkTimeAccountingMode.SUMMARIZED,
                        480, 480, 0, 0)
        ));
        whenSnapshots(first, second);

        var result = service.resolve(
                user,
                eventDate,
                proofsExcept(firstMonth, secondMonth)
        );

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.MIXED_ACCOUNTING_MODE,
                result.blockingReason()
        );
        assertEquals(secondMonth, result.blockingPeriod());
    }

    @Test
    void sameDailyModeAcrossDifferentHistoricalTermIdsRemainsOneCompatibleUnit() {
        YearMonth month = YearMonth.of(2026, 5);
        PayrollSnapshot snapshot = snapshot(month, 107L, 1);
        freeze(snapshot, List.of(
                fact(snapshot, 0, month.atDay(1), 61L, WorkTimeAccountingMode.DAILY,
                        480, 480, 0, 0),
                fact(snapshot, 1, month.atDay(2), 62L, WorkTimeAccountingMode.DAILY,
                        480, 480, 0, 0)
        ));
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertTrue(result.ready());
        assertEquals(WORKING_DAYS, result.referenceWorkedTime().unit());
        assertEquals(2L, result.referenceWorkedTime().workedUnits());
        assertEquals(2L, result.referenceWorkedTime().normUnits());
        assertTrue(result.scheduleFullyWorked());
    }

    @Test
    void latestSnapshotRevisionOwnsMonthAndOlderRevisionIsIgnored() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot old = snapshot(month, 108L, 1);
        PayrollSnapshot latest = snapshot(month, 109L, 2);
        freeze(latest, List.of(
                fact(latest, 0, month.atDay(8), 71L, WorkTimeAccountingMode.SUMMARIZED,
                        480, 240, 240, 0)
        ));
        whenSnapshots(old, latest);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertTrue(result.ready());
        assertEquals(240L, result.referenceWorkedTime().workedUnits());
        assertEquals(480L, result.referenceWorkedTime().normUnits());
        verify(manifests, never()).findBySnapshot(old);
        verify(factRepo, never()).findBySnapshotOrderByFactIndexAsc(old);
    }

    @Test
    void missingRequiredSnapshotBlocksUnlessUpstreamExplicitlyProvesNoPayrollMonth() {
        whenSnapshots();

        var result = service.resolve(user, eventDate, List.of());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.SNAPSHOT_MISSING,
                result.blockingReason()
        );
        assertEquals(referenceFrom, result.blockingPeriod());
    }

    @Test
    void explicitNoPayrollProofContradictingExistingSnapshotBlocks() {
        YearMonth month = referenceFrom;
        PayrollSnapshot snapshot = snapshot(month, 110L, 1);
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, allReferenceMonths());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.NO_PAYROLL_CONTRADICTION,
                result.blockingReason()
        );
        assertEquals(month, result.blockingPeriod());
        verifyNoInteractions(manifests, factRepo);
    }

    @Test
    void latestRevisionAlreadyMarkedSupersededBlocks() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 111L, 2);
        when(snapshot.getSupersededBy()).thenReturn(mock(PayrollSnapshot.class));
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.LATEST_SUPERSEDED,
                result.blockingReason()
        );
        verifyNoInteractions(manifests, factRepo);
    }

    @Test
    void legacySnapshotWithoutF3f2ManifestBlocks() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 112L, 1);
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.empty());

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.MANIFEST_MISSING,
                result.blockingReason()
        );
        verifyNoInteractions(factRepo);
    }

    @Test
    void incompleteManifestBlocksBeforePartialFrozenFactsAreRead() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 113L, 1);
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(snapshot, 2, 1, 1, "0".repeat(64));
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.MANIFEST_INCOMPLETE,
                result.blockingReason()
        );
        verifyNoInteractions(factRepo);
    }

    @Test
    void manifestCountMismatchBlocksTamperedOrMissingFrozenRows() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 114L, 1);
        PayrollSnapshotP15ScheduledWorkFact frozen = fact(
                snapshot, 0, month.atDay(1), 81L, WorkTimeAccountingMode.SUMMARIZED,
                480, 480, 0, 0
        );
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(snapshot, 1, 1, 1, fingerprint(List.of(frozen)));
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(factRepo.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of());

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.FACT_COUNT_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void manifestFingerprintMismatchBlocksModifiedImmutableHistory() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 115L, 1);
        PayrollSnapshotP15ScheduledWorkFact frozen = fact(
                snapshot, 0, month.atDay(1), 91L, WorkTimeAccountingMode.SUMMARIZED,
                480, 480, 0, 0
        );
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(snapshot, 1, 1, 1, "f".repeat(64));
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(factRepo.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(frozen));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.FINGERPRINT_MISMATCH,
                result.blockingReason()
        );
    }

    @Test
    void nonExactFrozenSourceIdentityCannotBecomeReferenceAuthority() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 116L, 1);
        PayrollSnapshotP15ScheduledWorkFact frozen = new PayrollSnapshotP15ScheduledWorkFact(
                snapshot,
                0,
                month.atDay(1),
                101L,
                month.minusMonths(2).atDay(1),
                WorkTimeAccountingMode.SUMMARIZED,
                PayrollSnapshotP15ScheduledWorkSourceKind.PLAN_DERIVED,
                480,
                480,
                480,
                480,
                480,
                0,
                0,
                false,
                "1",
                "",
                "a".repeat(64)
        );
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(snapshot, 1, 1, 1, fingerprint(List.of(frozen)));
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(factRepo.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(frozen));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.FACT_IDENTITY_INVALID,
                result.blockingReason()
        );
    }

    @Test
    void completeZeroFactMonthStillCannotProduceAZeroDenominatorFormulaFact() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 117L, 1);
        freeze(snapshot, List.of());
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.REFERENCE_NORM_ZERO,
                result.blockingReason()
        );
        assertNull(result.blockingPeriod());
        assertTrue(result.months().isEmpty());
    }

    @Test
    void invalidNoPayrollProofsAreRejectedRatherThanNormalized() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(
                        user,
                        eventDate,
                        List.of(referenceFrom, referenceFrom)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(
                        user,
                        eventDate,
                        List.of(referenceFrom.minusMonths(1))
                )
        );
        verifyNoInteractions(snapshots, manifests, factRepo);
    }

    @Test
    void sourceDateOutsideOwningPayrollMonthFailsClosedEvenWithMatchingCounts() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 118L, 1);
        PayrollSnapshotP15ScheduledWorkFact frozen = fact(
                snapshot,
                0,
                month.plusMonths(1).atDay(1),
                111L,
                WorkTimeAccountingMode.SUMMARIZED,
                480,
                480,
                0,
                0
        );
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(snapshot, 1, 1, 1, fingerprint(List.of(frozen)));
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(factRepo.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(frozen));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.FACT_IDENTITY_INVALID,
                result.blockingReason()
        );
    }


    @Test
    void fullyWorkedSummarizedReferenceProducesMinuteFactAndFullyWorkedSignal() {
        YearMonth month = YearMonth.of(2026, 6);
        PayrollSnapshot snapshot = snapshot(month, 119L, 1);
        freeze(snapshot, List.of(
                fact(snapshot, 0, month.atDay(1), 121L, WorkTimeAccountingMode.SUMMARIZED,
                        600, 600, 0, 240)
        ));
        whenSnapshots(snapshot);

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertTrue(result.ready());
        assertEquals(WORKING_MINUTES, result.referenceWorkedTime().unit());
        assertEquals(600L, result.referenceWorkedTime().workedUnits());
        assertEquals(600L, result.referenceWorkedTime().normUnits());
        assertTrue(result.scheduleFullyWorked());
    }

    @Test
    void nullSnapshotCandidateListIsTreatedAsNoRowsButStillRequiresExplicitProofs() {
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user,
                referenceFrom.atDay(1),
                referenceTo.atDay(1)
        )).thenReturn(null);

        var result = service.resolve(user, eventDate, allReferenceMonths());

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.REFERENCE_NORM_ZERO,
                result.blockingReason()
        );
        verifyNoInteractions(manifests, factRepo);
    }

    @Test
    void invalidSnapshotIdentityIsRejectedAsRepositoryCorruption() {
        PayrollSnapshot snapshot = mock(PayrollSnapshot.class);
        when(snapshot.getId()).thenReturn(null);
        when(snapshot.getOwner()).thenReturn(user);
        when(snapshot.getPeriodMonth()).thenReturn(referenceFrom.atDay(1));
        when(snapshot.getRevision()).thenReturn(1);
        whenSnapshots(snapshot);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, eventDate, proofsExcept(referenceFrom))
        );
        verifyNoInteractions(manifests, factRepo);
    }

    @Test
    void nonCanonicalSnapshotMonthIsRejectedAsRepositoryCorruption() {
        PayrollSnapshot snapshot = mock(PayrollSnapshot.class);
        when(snapshot.getId()).thenReturn(120L);
        when(snapshot.getOwner()).thenReturn(user);
        when(snapshot.getPeriodMonth()).thenReturn(referenceFrom.atDay(2));
        when(snapshot.getRevision()).thenReturn(1);
        whenSnapshots(snapshot);

        assertThrows(
                IllegalStateException.class,
                () -> service.resolve(user, eventDate, proofsExcept(referenceFrom))
        );
        verifyNoInteractions(manifests, factRepo);
    }

    @Test
    void corruptManifestIdentityBlocksBeforeFrozenRowsAreTrusted() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 121L, 1);
        PayrollSnapshotP15WorkTimeManifest manifest = mock(PayrollSnapshotP15WorkTimeManifest.class);
        when(manifest.isComplete()).thenReturn(true);
        when(manifest.getSnapshot()).thenReturn(snapshot);
        when(manifest.getFingerprint()).thenReturn("bad");
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.MANIFEST_IDENTITY_INVALID,
                result.blockingReason()
        );
        verifyNoInteractions(factRepo);
    }

    @Test
    void nonContiguousFrozenFactOrderBlocksImmutableAuthority() {
        YearMonth month = YearMonth.of(2026, 1);
        PayrollSnapshot snapshot = snapshot(month, 122L, 1);
        PayrollSnapshotP15ScheduledWorkFact frozen = fact(
                snapshot, 1, month.atDay(1), 131L, WorkTimeAccountingMode.SUMMARIZED,
                480, 480, 0, 0
        );
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(snapshot, 1, 1, 1, fingerprint(List.of(frozen)));
        whenSnapshots(snapshot);
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(factRepo.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(List.of(frozen));

        var result = service.resolve(user, eventDate, proofsExcept(month));

        assertFalse(result.ready());
        assertEquals(
                AverageEarningsBonusP15ReferenceWorkedTimeFactService.FACT_IDENTITY_INVALID,
                result.blockingReason()
        );
    }

    private PayrollSnapshot snapshot(YearMonth month, long id, int revision) {
        PayrollSnapshot snapshot = mock(PayrollSnapshot.class);
        when(snapshot.getId()).thenReturn(id);
        when(snapshot.getOwner()).thenReturn(user);
        when(snapshot.getPeriodMonth()).thenReturn(month.atDay(1));
        when(snapshot.getRevision()).thenReturn(revision);
        when(snapshot.getSupersededBy()).thenReturn(null);
        return snapshot;
    }

    private PayrollSnapshotP15ScheduledWorkFact fact(
            PayrollSnapshot snapshot,
            int index,
            LocalDate date,
            long termId,
            WorkTimeAccountingMode mode,
            int schedule,
            int inside,
            int notWorked,
            int outside
    ) {
        return new PayrollSnapshotP15ScheduledWorkFact(
                snapshot,
                index,
                date,
                termId,
                date.minusYears(1),
                mode,
                PayrollSnapshotP15ScheduledWorkSourceKind.PLAN_DERIVED,
                schedule,
                inside + outside,
                inside,
                schedule,
                inside,
                notWorked,
                outside,
                true,
                schedule > 0 ? String.valueOf(1000 + index) : "",
                "",
                sha256("fact-" + index + "-" + date + "-" + termId)
        );
    }

    private void freeze(
            PayrollSnapshot snapshot,
            List<PayrollSnapshotP15ScheduledWorkFact> frozen
    ) {
        PayrollSnapshotP15WorkTimeManifest manifest =
                new PayrollSnapshotP15WorkTimeManifest(
                        snapshot,
                        frozen.size(),
                        frozen.size(),
                        frozen.size(),
                        fingerprint(frozen)
                );
        when(manifests.findBySnapshot(snapshot)).thenReturn(Optional.of(manifest));
        when(factRepo.findBySnapshotOrderByFactIndexAsc(snapshot)).thenReturn(frozen);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private String fingerprint(List<PayrollSnapshotP15ScheduledWorkFact> frozen) {
        StringBuilder canonical = new StringBuilder("P15_SCHEDULED_WORK_V1");
        for (PayrollSnapshotP15ScheduledWorkFact fact : frozen) {
            canonical.append("\nD|").append(fact.getSourceDate());
        }
        for (PayrollSnapshotP15ScheduledWorkFact fact : frozen) {
            canonical.append("\nF|")
                    .append(fact.getSourceDate()).append('|')
                    .append(fact.isSourceIdentityExact()).append('|')
                    .append(fact.getSourceFingerprint());
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void whenSnapshots(PayrollSnapshot... rows) {
        when(snapshots.findByOwnerAndPeriodMonthBetweenOrderByPeriodMonthAscRevisionDesc(
                user,
                referenceFrom.atDay(1),
                referenceTo.atDay(1)
        )).thenReturn(List.of(rows));
    }

    private List<YearMonth> proofsExcept(YearMonth... required) {
        List<YearMonth> excluded = List.of(required);
        List<YearMonth> result = new ArrayList<>();
        for (YearMonth month = referenceFrom;
                !month.isAfter(referenceTo);
                month = month.plusMonths(1)) {
            if (!excluded.contains(month)) {
                result.add(month);
            }
        }
        return result;
    }

    private List<YearMonth> allReferenceMonths() {
        return proofsExcept();
    }
}
