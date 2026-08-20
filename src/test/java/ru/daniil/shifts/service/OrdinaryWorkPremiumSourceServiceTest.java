package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.dto.Dtos.AbsenceOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ProductionCalendarDayDto;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;
import ru.daniil.shifts.service.OrdinaryWorkPremiumSourceService.SourceKind;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationService.DayClassification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrdinaryWorkPremiumSourceServiceTest {

    private final ActualWorkIntervalRepository actualWork =
            mock(ActualWorkIntervalRepository.class);

    private final ActualWorkDayAllocationService actualAllocation =
            mock(ActualWorkDayAllocationService.class);

    private final DayEntryRepository days =
            mock(DayEntryRepository.class);

    private final VacationPlannerService vacationPlanner =
            mock(VacationPlannerService.class);

    private final ProductionCalendarService productionCalendar =
            mock(ProductionCalendarService.class);

    private final PlannedWorkDayAllocationService plannedAllocation =
            mock(PlannedWorkDayAllocationService.class);

    private final PayClassificationService classification =
            mock(PayClassificationService.class);

    private final OrdinaryWorkPremiumSourceService service =
            new OrdinaryWorkPremiumSourceService(
                    actualWork,
                    actualAllocation,
                    days,
                    vacationPlanner,
                    productionCalendar,
                    plannedAllocation,
                    classification
            );

    private final AppUser user =
            new AppUser(
                    "ordinary-premium-source-user",
                    "{noop}unused"
            );

    @Test
    void explicitSourceKeepsOnlyRegularFactualSlices() {
        LocalDate date = date();

        ActualWorkInterval interval =
                mock(ActualWorkInterval.class);

        when(interval.getId())
                .thenReturn(77L);

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(interval)
        );

        when(
                actualAllocation.netSegments(
                        interval
                )
        ).thenReturn(
                List.of(
                        actualSegment(
                                "2026-08-18T21:00",
                                "2026-08-18T23:00"
                        )
                )
        );

        ClassificationSlice regularDay =
                classificationSlice(
                        77L,
                        "2026-08-18T21:00",
                        "2026-08-18T22:00",
                        0,
                        60,
                        true,
                        false,
                        false,
                        false
                );

        ClassificationSlice regularNight =
                classificationSlice(
                        77L,
                        "2026-08-18T22:00",
                        "2026-08-18T23:00",
                        60,
                        60,
                        true,
                        true,
                        false,
                        false
                );

        ClassificationSlice overtime =
                classificationSlice(
                        77L,
                        "2026-08-18T23:00",
                        "2026-08-19T00:00",
                        120,
                        60,
                        false,
                        true,
                        false,
                        true
                );

        when(
                classification.classify(
                        user,
                        date
                )
        ).thenReturn(
                new DayClassification(
                        date,
                        120,
                        false,
                        PayClassificationService.DEFAULT_NIGHT_WINDOW,
                        180,
                        120,
                        120,
                        0,
                        60,
                        List.of(
                                regularDay,
                                regularNight,
                                overtime
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertTrue(result.ready());
        assertEquals(SourceKind.EXPLICIT, result.sourceKind());
        assertEquals(120, result.canonicalOrdinaryMinutes());
        assertEquals(2, result.pieces().size());

        assertFalse(result.pieces().get(0).night());
        assertTrue(result.pieces().get(1).night());

        assertTrue(
                result.pieces()
                        .stream()
                        .allMatch(piece ->
                                !piece.consumedSlice()
                                        .overtime()
                        )
        );
    }

    @Test
    void exactPlanDerivedClockProducesNightBoundary() {
        LocalDate date = date();
        DayEntry day = mock(DayEntry.class);

        planDerivedDefaults(
                date,
                day,
                120
        );

        when(
                plannedAllocation.netSegments(
                        user,
                        day
                )
        ).thenReturn(
                List.of(
                        plannedSegment(
                                "2026-08-18T21:00",
                                "2026-08-18T23:00"
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertTrue(result.ready());
        assertEquals(120, result.canonicalOrdinaryMinutes());
        assertEquals(2, result.pieces().size());

        assertEquals(60, result.pieces().get(0).minutes());
        assertFalse(result.pieces().get(0).night());

        assertEquals(60, result.pieces().get(1).minutes());
        assertTrue(result.pieces().get(1).night());
    }

    @Test
    void shortenedNormCanRemainReadyWhenEveryPossibleClockMinuteHasSameDimensions() {
        LocalDate date = date();
        DayEntry day = mock(DayEntry.class);

        planDerivedDefaults(
                date,
                day,
                420
        );

        when(
                plannedAllocation.netSegments(
                        user,
                        day
                )
        ).thenReturn(
                List.of(
                        plannedSegment(
                                "2026-08-18T09:00",
                                "2026-08-18T17:00"
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertTrue(result.ready());
        assertEquals(420, result.canonicalOrdinaryMinutes());
        assertEquals(1, result.pieces().size());
        assertEquals(420, result.pieces().get(0).minutes());
        assertFalse(result.pieces().get(0).night());
        assertFalse(result.pieces().get(0).holiday());
    }

    @Test
    void shortenedNormAcrossNightBoundaryFailsClosedInsteadOfGuessingWhichMinutesRemain() {
        LocalDate date = date();
        DayEntry day = mock(DayEntry.class);

        /*
         * Canonical Payroll quantity says only 120 ordinary minutes survive,
         * while the known planned clock contains 180 minutes:
         *
         * 20:00 -> 22:00 = 120 non-night
         * 22:00 -> 23:00 =  60 night
         *
         * The current quantitative semantics do not say WHICH 120 minutes
         * survive. Choosing either side would invent a premium result.
         */
        planDerivedDefaults(
                date,
                day,
                120
        );

        when(
                plannedAllocation.netSegments(
                        user,
                        day
                )
        ).thenReturn(
                List.of(
                        plannedSegment(
                                "2026-08-18T20:00",
                                "2026-08-18T23:00"
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertFalse(result.ready());

        assertEquals(
                OrdinaryWorkPremiumSourceService.BLOCK_CLOCK_QUANTITY,
                result.blockingReason()
        );

        assertEquals(
                120,
                result.canonicalOrdinaryMinutes()
        );

        assertTrue(
                result.pieces().isEmpty(),
                "Ambiguous clock reduction must never emit speculative premium pieces"
        );
    }

    @Test
    void hoursOnlyLegacyAbsenceFailsClosedBecauseWallClockIsUnknown() {
        LocalDate date = date();
        DayEntry day = mock(DayEntry.class);

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(List.of());

        when(
                days.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(Optional.of(day));

        when(
                productionCalendar.requiredMinutes(
                        user,
                        date,
                        day
                )
        ).thenReturn(480);

        when(
                vacationPlanner.occurrences(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(
                        absence(
                                "HOURS_ONLY",
                                null,
                                null,
                                120,
                                false
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertFalse(result.ready());
        assertEquals(
                OrdinaryWorkPremiumSourceService.BLOCK_HOURS_ONLY,
                result.blockingReason()
        );
        assertEquals(480, result.canonicalOrdinaryMinutes());
        assertTrue(result.pieces().isEmpty());
    }

    @Test
    void fullDayReplacingAbsenceProducesZeroOrdinaryPremiumSource() {
        LocalDate date = date();
        DayEntry day = mock(DayEntry.class);

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(List.of());

        when(
                days.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(Optional.of(day));

        when(
                productionCalendar.requiredMinutes(
                        user,
                        date,
                        day
                )
        ).thenReturn(480);

        when(
                vacationPlanner.occurrences(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(
                        absence(
                                "FULL_DAY",
                                null,
                                null,
                                0,
                                true
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertTrue(result.ready());
        assertEquals(0, result.canonicalOrdinaryMinutes());
        assertTrue(result.pieces().isEmpty());

        verifyNoInteractions(
                plannedAllocation
        );
    }

    @Test
    void planDerivedCrossMidnightIsExplicitlyBlockedUntilPayrollOwnershipIsDefined() {
        LocalDate date = date();
        DayEntry day = mock(DayEntry.class);

        planDerivedDefaults(
                date,
                day,
                660
        );

        when(
                plannedAllocation.netSegments(
                        user,
                        day
                )
        ).thenReturn(
                List.of(
                        plannedSegment(
                                "2026-08-18T21:00",
                                "2026-08-19T00:00"
                        ),
                        plannedSegment(
                                "2026-08-19T00:00",
                                "2026-08-19T08:00"
                        )
                )
        );

        var result =
                service.project(
                        user,
                        date
                );

        assertFalse(result.ready());
        assertEquals(
                OrdinaryWorkPremiumSourceService.BLOCK_CROSS_DATE,
                result.blockingReason()
        );
        assertTrue(result.pieces().isEmpty());
    }

    private void planDerivedDefaults(
            LocalDate date,
            DayEntry day,
            int requiredMinutes
    ) {

        when(
                day.getId()
        ).thenReturn(
                88L
        );

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(List.of());

        when(
                days.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(
                Optional.of(day)
        );

        when(
                productionCalendar.requiredMinutes(
                        user,
                        date,
                        day
                )
        ).thenReturn(
                requiredMinutes
        );

        when(
                vacationPlanner.occurrences(
                        user,
                        date,
                        date
                )
        ).thenReturn(List.of());

        when(
                productionCalendar.resolvedDay(
                        user,
                        date
                )
        ).thenReturn(
                productionDay(
                        date,
                        "NONE"
                )
        );
    }

    private ProductionCalendarDayDto productionDay(
            LocalDate date,
            String payrollEffect
    ) {
        return new ProductionCalendarDayDto(
                date.toString(),
                "NORMAL",
                "NONE",
                null,
                payrollEffect,
                null,
                "NONE",
                null,
                false,
                0,
                0,
                0
        );
    }

    private AbsenceOccurrenceDto absence(
            String coverage,
            String start,
            String end,
            int chargedMinutes,
            boolean replacesShift
    ) {
        return new AbsenceOccurrenceDto(
                1L,
                1L,
                "Absence",
                "#000000",
                "TIME_OFF",
                "Absence",
                date().toString(),
                date().toString(),
                date().toString(),
                "APPROVED",
                false,
                true,
                "NONE",
                coverage,
                start,
                end,
                chargedMinutes,
                replacesShift,
                "Shift",
                "#000000",
                480,
                "OVERTIME_BANK",
                chargedMinutes,
                1L
        );
    }

    private NetWorkSegment actualSegment(
            String start,
            String end
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        return new NetWorkSegment(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                zonedStart.toInstant(),
                zonedEnd.toInstant(),
                "UTC"
        );
    }

    private PlannedWorkDayAllocationService.NetWorkSegment plannedSegment(
            String start,
            String end
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        return new PlannedWorkDayAllocationService.NetWorkSegment(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                zonedStart.toInstant(),
                zonedEnd.toInstant(),
                "UTC"
        );
    }

    private ClassificationSlice classificationSlice(
            Long sourceId,
            String start,
            String end,
            int ordinal,
            int minutes,
            boolean regular,
            boolean night,
            boolean holiday,
            boolean overtime
    ) {
        ZonedDateTime zonedStart =
                LocalDateTime.parse(start)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        ZonedDateTime zonedEnd =
                LocalDateTime.parse(end)
                        .atZone(
                                ZoneId.of("UTC")
                        );

        Instant startInstant =
                zonedStart.toInstant();

        Instant endInstant =
                zonedEnd.toInstant();

        return new ClassificationSlice(
                zonedStart.toLocalDateTime(),
                zonedEnd.toLocalDateTime(),
                startInstant,
                endInstant,
                "UTC",
                sourceId,
                ordinal,
                minutes,
                regular,
                night,
                holiday,
                overtime
        );
    }

    private LocalDate date() {
        return LocalDate.of(
                2026,
                8,
                18
        );
    }
}
