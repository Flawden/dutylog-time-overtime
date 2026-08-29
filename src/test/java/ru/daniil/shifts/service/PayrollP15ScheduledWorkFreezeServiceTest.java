package ru.daniil.shifts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.daniil.shifts.model.*;
import ru.daniil.shifts.repo.*;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceDay;
import ru.daniil.shifts.service.TimeCompensationService.PayrollSourceSnapshot;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayrollP15ScheduledWorkFreezeServiceTest {

    private PayrollSnapshotP15ScheduledWorkFactRepository facts;
    private PayrollSnapshotP15WorkTimeManifestRepository manifests;
    private WorkTimeAccountingHistoryService accountingHistory;
    private DayEntryRepository days;
    private ActualWorkIntervalRepository actualWork;
    private PlannedWorkDayAllocationService plannedAllocation;
    private ActualWorkDayAllocationService actualAllocation;
    private TimeCompensationService timeCompensation;
    private PayrollP15ScheduledWorkFreezeService service;

    private AppUser user;
    private PayrollSnapshot snapshot;

    @BeforeEach
    void setUp() {
        facts = mock(PayrollSnapshotP15ScheduledWorkFactRepository.class);
        manifests = mock(PayrollSnapshotP15WorkTimeManifestRepository.class);
        accountingHistory = mock(WorkTimeAccountingHistoryService.class);
        days = mock(DayEntryRepository.class);
        actualWork = mock(ActualWorkIntervalRepository.class);
        plannedAllocation = mock(PlannedWorkDayAllocationService.class);
        actualAllocation = mock(ActualWorkDayAllocationService.class);
        timeCompensation = mock(TimeCompensationService.class);

        service = new PayrollP15ScheduledWorkFreezeService(
                facts,
                manifests,
                accountingHistory,
                days,
                actualWork,
                plannedAllocation,
                actualAllocation,
                new PlannedActualWorkRelationEngine(),
                timeCompensation
        );

        user = mock(AppUser.class);
        snapshot = mock(PayrollSnapshot.class);
        when(snapshot.getPeriodMonth()).thenReturn(LocalDate.of(2026, 8, 1));

        when(actualWork.findOverlappingRange(any(), any(), any())).thenReturn(List.of());
        when(days.findByOwnerAndDateBetweenOrderByDateAsc(any(), any(), any())).thenReturn(List.of());
        when(timeCompensation.payrollSource(eq(user), any(), any())).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(1);
            LocalDate to = invocation.getArgument(2);
            return source(from, to, List.of());
        });
    }

    @Test
    void planDerivedFullDayFreezesScheduledWorkWithoutInventingOutsideTime() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        DayEntry day = plannedDay(10L, date, true);
        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(day));
        when(plannedAllocation.netSegments(user, day))
                .thenReturn(List.of(planned(date, 8, 0, 16, 0)));
        readyMode(date, 42L, WorkTimeAccountingMode.DAILY);

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(sourceDay(date, 480, 480, 480))
        );

        var result = service.freeze(snapshot, user, current);

        assertTrue(result.manifest().isComplete());
        assertEquals(1, result.facts().size());
        var fact = result.facts().get(0);
        assertEquals(PayrollSnapshotP15ScheduledWorkSourceKind.PLAN_DERIVED, fact.getSourceKind());
        assertEquals(480, fact.getScheduleMinutes());
        assertEquals(480, fact.getPlannedAndWorkedMinutes());
        assertEquals(0, fact.getWorkedOutsidePlanMinutes());
    }

    @Test
    void absencePlusDerivedOvertimeCannotMasqueradeAsFullyWorkedReferenceTime() {
        LocalDate first = LocalDate.of(2026, 8, 18);
        LocalDate second = first.plusDays(1);
        DayEntry one = plannedDay(10L, first, true);
        DayEntry two = plannedDay(11L, second, true);

        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(one, two));
        when(plannedAllocation.netSegments(user, one))
                .thenReturn(List.of(planned(first, 8, 0, 16, 0)));
        when(plannedAllocation.netSegments(user, two))
                .thenReturn(List.of(planned(second, 8, 0, 16, 0)));
        readyMode(first, 42L, WorkTimeAccountingMode.SUMMARIZED);
        readyMode(second, 42L, WorkTimeAccountingMode.SUMMARIZED);

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(
                        sourceDay(first, 480, 0, 0),
                        sourceDay(second, 480, 960, 480)
                )
        );

        var result = service.freeze(snapshot, user, current);

        assertTrue(result.manifest().isComplete());
        assertEquals(960, result.facts().stream().mapToInt(PayrollSnapshotP15ScheduledWorkFact::getScheduleMinutes).sum());
        assertEquals(480, result.facts().stream().mapToInt(PayrollSnapshotP15ScheduledWorkFact::getPlannedAndWorkedMinutes).sum());
        assertEquals(480, result.facts().stream().mapToInt(PayrollSnapshotP15ScheduledWorkFact::getPlannedNotWorkedMinutes).sum());
        assertEquals(480, result.facts().stream().mapToInt(PayrollSnapshotP15ScheduledWorkFact::getWorkedOutsidePlanMinutes).sum());
    }

    @Test
    void explicitActualUsesClockRelationAndKeepsOvertimeOutsideSchedule() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        DayEntry day = plannedDay(10L, date, true);
        ActualWorkInterval actual = actualInterval(20L, date, true);

        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(day));
        when(plannedAllocation.netSegments(user, day))
                .thenReturn(List.of(planned(date, 8, 0, 16, 0)));
        when(actualWork.findOverlappingRange(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(actual));
        when(actualAllocation.netSegments(actual))
                .thenReturn(List.of(actual(date, 7, 0, 17, 0, true)));
        readyMode(date, 42L, WorkTimeAccountingMode.SUMMARIZED);

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(sourceDay(date, 480, 600, 480))
        );

        var result = service.freeze(snapshot, user, current);
        var fact = result.facts().get(0);

        assertTrue(result.manifest().isComplete());
        assertEquals(PayrollSnapshotP15ScheduledWorkSourceKind.EXPLICIT_ACTUAL, fact.getSourceKind());
        assertEquals(480, fact.getScheduleMinutes());
        assertEquals(480, fact.getPlannedAndWorkedMinutes());
        assertEquals(120, fact.getWorkedOutsidePlanMinutes());
        assertEquals("20", fact.getActualWorkIntervalIds());
    }

    @Test
    void explicitSecondHalfOfOvernightShiftMarksFirstHalfNotWorkedAndCarriesSupportingActualIdentity() {
        LocalDate first = LocalDate.of(2026, 8, 18);
        LocalDate second = first.plusDays(1);
        DayEntry night = plannedDay(10L, first, true);
        ActualWorkInterval actual = actualInterval(20L, second, true);

        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(night));
        when(plannedAllocation.netSegments(user, night)).thenReturn(List.of(
                planned(first, 21, 0, 24, 0),
                planned(second, 0, 0, 8, 0)
        ));
        when(actualWork.findOverlappingRange(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(actual));
        when(actualAllocation.netSegments(actual))
                .thenReturn(List.of(actual(second, 0, 0, 8, 0, true)));
        readyMode(first, 42L, WorkTimeAccountingMode.SUMMARIZED);
        readyMode(second, 42L, WorkTimeAccountingMode.SUMMARIZED);

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(
                        sourceDay(first, 660, 660, 660),
                        sourceDay(second, 0, 480, 480)
                )
        );

        var result = service.freeze(snapshot, user, current);

        assertTrue(result.manifest().isComplete());
        assertEquals(2, result.facts().size());

        var firstFact = result.facts().get(0);
        assertEquals(first, firstFact.getSourceDate());
        assertEquals(PayrollSnapshotP15ScheduledWorkSourceKind.EXPLICIT_ACTUAL, firstFact.getSourceKind());
        assertEquals(180, firstFact.getScheduleMinutes());
        assertEquals(0, firstFact.getPlannedAndWorkedMinutes());
        assertEquals(180, firstFact.getPlannedNotWorkedMinutes());
        assertEquals("20", firstFact.getActualWorkIntervalIds());

        var secondFact = result.facts().get(1);
        assertEquals(480, secondFact.getPlannedAndWorkedMinutes());
        assertEquals("20", secondFact.getActualWorkIntervalIds());
    }

    @Test
    void missingAccountingModeCreatesIncompleteManifestWithoutPartialAuthority() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        DayEntry day = plannedDay(10L, date, true);
        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(day));
        when(plannedAllocation.netSegments(user, day))
                .thenReturn(List.of(planned(date, 8, 0, 16, 0)));
        when(accountingHistory.resolveAt(user, date)).thenReturn(
                WorkTimeAccountingHistoryService.Resolution.blocked(
                        date,
                        WorkTimeAccountingHistoryService.MODE_FACT_MISSING + ":" + date
                )
        );

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(sourceDay(date, 480, 480, 480))
        );

        var result = service.freeze(snapshot, user, current);

        assertFalse(result.manifest().isComplete());
        assertEquals(1, result.manifest().getCandidateDayCount());
        assertEquals(0, result.manifest().getFactCount());
        assertTrue(result.facts().isEmpty());
        verify(facts, never()).saveAll(any());
    }

    @Test
    void missingFrozenPlanIdentityPersistsDiagnosticFactButManifestFailsClosed() {
        LocalDate date = LocalDate.of(2026, 8, 18);
        DayEntry day = plannedDay(10L, date, false);
        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(day));
        when(plannedAllocation.netSegments(user, day))
                .thenReturn(List.of(planned(date, 8, 0, 16, 0)));
        readyMode(date, 42L, WorkTimeAccountingMode.DAILY);

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(sourceDay(date, 480, 480, 480))
        );

        var result = service.freeze(snapshot, user, current);

        assertEquals(1, result.facts().size());
        assertFalse(result.facts().get(0).isSourceIdentityExact());
        assertFalse(result.manifest().isComplete());
        assertEquals(0, result.manifest().getExactFactCount());
    }

    @Test
    void accountingModeChangeIsFrozenPerDateWithoutMixingItIntoFormula() {
        LocalDate first = LocalDate.of(2026, 8, 18);
        LocalDate second = first.plusDays(1);
        DayEntry one = plannedDay(10L, first, true);
        DayEntry two = plannedDay(11L, second, true);

        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(one, two));
        when(plannedAllocation.netSegments(user, one))
                .thenReturn(List.of(planned(first, 8, 0, 16, 0)));
        when(plannedAllocation.netSegments(user, two))
                .thenReturn(List.of(planned(second, 8, 0, 16, 0)));
        readyMode(first, 42L, WorkTimeAccountingMode.DAILY);
        readyMode(second, 43L, WorkTimeAccountingMode.SUMMARIZED);

        PayrollSourceSnapshot current = source(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                List.of(
                        sourceDay(first, 480, 480, 480),
                        sourceDay(second, 480, 480, 480)
                )
        );

        var result = service.freeze(snapshot, user, current);

        assertTrue(result.manifest().isComplete());
        assertEquals(WorkTimeAccountingMode.DAILY, result.facts().get(0).getAccountingMode());
        assertEquals(WorkTimeAccountingMode.SUMMARIZED, result.facts().get(1).getAccountingMode());
        assertNotEquals(
                result.facts().get(0).getWorkTimeAccountingTermId(),
                result.facts().get(1).getWorkTimeAccountingTermId()
        );
    }

    @Test
    void fullPlanDerivedOvernightShiftCarriesPreviousSourceTruthAcrossMonthBoundary() {
        when(snapshot.getPeriodMonth()).thenReturn(LocalDate.of(2026, 9, 1));

        LocalDate sourceDate = LocalDate.of(2026, 8, 31);
        LocalDate septemberFirst = LocalDate.of(2026, 9, 1);
        DayEntry night = plannedDay(10L, sourceDate, true);

        when(days.findByOwnerAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(List.of(night));
        when(plannedAllocation.netSegments(user, night)).thenReturn(List.of(
                planned(sourceDate, 21, 0, 24, 0),
                planned(septemberFirst, 0, 0, 8, 0)
        ));
        when(timeCompensation.payrollSource(user, sourceDate, sourceDate)).thenReturn(
                source(sourceDate, sourceDate, List.of(sourceDay(sourceDate, 660, 660, 660)))
        );
        readyMode(septemberFirst, 42L, WorkTimeAccountingMode.SUMMARIZED);

        PayrollSourceSnapshot september = source(
                septemberFirst,
                LocalDate.of(2026, 9, 30),
                List.of()
        );

        var result = service.freeze(snapshot, user, september);

        assertTrue(result.manifest().isComplete());
        assertEquals(1, result.facts().size());
        var fact = result.facts().get(0);
        assertEquals(septemberFirst, fact.getSourceDate());
        assertEquals(480, fact.getScheduleMinutes());
        assertEquals(480, fact.getPlannedAndWorkedMinutes());
        assertEquals(0, fact.getPayrollPlannedMinutes());
        assertEquals("10", fact.getPlannedDayEntryIds());
    }

    private void readyMode(LocalDate date, long termId, WorkTimeAccountingMode mode) {
        when(accountingHistory.resolveAt(user, date)).thenReturn(
                WorkTimeAccountingHistoryService.Resolution.ready(
                        date,
                        new WorkTimeAccountingHistoryService.ModeFact(
                                termId,
                                date.minusYears(1),
                                mode
                        )
                )
        );
    }

    private PayrollSourceDay sourceDay(
            LocalDate date,
            int planned,
            int worked,
            int hourlyBase
    ) {
        return new PayrollSourceDay(
                date,
                planned,
                worked,
                0,
                0,
                0,
                0,
                hourlyBase
        );
    }

    private PayrollSourceSnapshot source(
            LocalDate from,
            LocalDate to,
            List<PayrollSourceDay> days
    ) {
        return new PayrollSourceSnapshot(
                from,
                to,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                days
        );
    }

    private DayEntry plannedDay(Long id, LocalDate date, boolean exact) {
        DayEntry day = mock(DayEntry.class);
        when(day.getId()).thenReturn(id);
        when(day.getDate()).thenReturn(date);
        when(day.getShiftType()).thenReturn(mock(ShiftType.class));
        when(day.hasShiftOccurrenceSnapshot()).thenReturn(exact);
        when(day.getShiftStartInstant()).thenReturn(date.atStartOfDay(ZoneOffset.UTC).toInstant());
        when(day.getShiftEndInstant()).thenReturn(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        when(day.getShiftSourceTimezone()).thenReturn("UTC");
        when(day.getShiftNetMinutes()).thenReturn(480L);
        return day;
    }

    private ActualWorkInterval actualInterval(Long id, LocalDate date, boolean exact) {
        ActualWorkInterval interval = mock(ActualWorkInterval.class);
        when(interval.getId()).thenReturn(id);
        when(interval.getWorkDate()).thenReturn(date);
        when(interval.getEndDate()).thenReturn(date);
        when(interval.getStartTime()).thenReturn(LocalTime.of(7, 0));
        when(interval.getEndTime()).thenReturn(LocalTime.of(17, 0));
        when(interval.getStartInstant()).thenReturn(date.atTime(7, 0).toInstant(ZoneOffset.UTC));
        when(interval.getEndInstant()).thenReturn(date.atTime(17, 0).toInstant(ZoneOffset.UTC));
        when(interval.getSourceTimezone()).thenReturn("UTC");
        when(interval.hasAbsoluteIdentity()).thenReturn(exact);
        when(interval.getWorkedMinutes()).thenReturn(600);
        return interval;
    }

    private PlannedWorkDayAllocationService.NetWorkSegment planned(
            LocalDate date,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        LocalDateTime start = date.atTime(startHour, startMinute);
        LocalDateTime end = endHour == 24
                ? date.plusDays(1).atStartOfDay()
                : date.atTime(endHour, endMinute);
        return new PlannedWorkDayAllocationService.NetWorkSegment(
                start,
                end,
                start.toInstant(ZoneOffset.UTC),
                end.toInstant(ZoneOffset.UTC),
                "UTC"
        );
    }

    private ActualWorkDayAllocationService.NetWorkSegment actual(
            LocalDate date,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute,
            boolean exact
    ) {
        LocalDateTime start = date.atTime(startHour, startMinute);
        LocalDateTime end = date.atTime(endHour, endMinute);
        return exact
                ? new ActualWorkDayAllocationService.NetWorkSegment(
                        start,
                        end,
                        start.toInstant(ZoneOffset.UTC),
                        end.toInstant(ZoneOffset.UTC),
                        "UTC"
                )
                : new ActualWorkDayAllocationService.NetWorkSegment(
                        start,
                        end,
                        null,
                        null,
                        null
                );
    }
}
