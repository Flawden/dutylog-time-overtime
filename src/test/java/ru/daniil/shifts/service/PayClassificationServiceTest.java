package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ProductionCalendarDay;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayClassificationServiceTest {

    private final ActualWorkIntervalRepository actualWork =
            mock(ActualWorkIntervalRepository.class);

    private final ActualWorkDayAllocationService allocation =
            mock(ActualWorkDayAllocationService.class);

    private final DayEntryRepository scheduleDays =
            mock(DayEntryRepository.class);

    private final ProductionCalendarDayRepository productionDays =
            mock(ProductionCalendarDayRepository.class);

    private final WorkNormService workNorm =
            mock(WorkNormService.class);

    private final PayClassificationService service =
            new PayClassificationService(
                    actualWork,
                    allocation,
                    scheduleDays,
                    productionDays,
                    workNorm,
                    new PayClassificationEngine()
            );

    private final AppUser user =
            new AppUser(
                    "classification-context",
                    "{noop}irrelevant"
            );

    @Test
    void holidayProductionNormZeroDoesNotEraseEightHourOrdinaryThreshold() {
        LocalDate date =
                LocalDate.of(2026, 8, 18);

        DayEntry schedule =
                mock(DayEntry.class);

        ActualWorkInterval fact =
                mock(ActualWorkInterval.class);

        when(fact.getId())
                .thenReturn(101L);

        ProductionCalendarDay holiday =
                mock(ProductionCalendarDay.class);

        when(
                scheduleDays.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(
                Optional.of(schedule)
        );

        when(
                workNorm.basePlannedMinutes(schedule)
        ).thenReturn(480);

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "LOCAL_OVERRIDE"
                )
        ).thenReturn(
                Optional.of(holiday)
        );

        /*
         * This proves the architectural separation explicitly:
         * production norm may be overridden to zero for the holiday,
         * but classification ordinary threshold remains the base 8h shift.
         */
        when(
                holiday.getScheduleEffect()
        ).thenReturn("NORM_OVERRIDE");

        when(
                holiday.getNormMinutesOverride()
        ).thenReturn(0);

        when(
                holiday.getPayrollEffect()
        ).thenReturn("HOLIDAY");

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(fact)
        );

        when(
                allocation.netSegments(fact)
        ).thenReturn(
                List.of(
                        segment(
                                "2026-08-18T08:00",
                                "2026-08-18T18:00"
                        )
                )
        );

        var result =
                service.classify(
                        user,
                        date
                );

        assertEquals(
                480,
                result.ordinaryThresholdMinutes()
        );

        assertTrue(result.holiday());

        assertEquals(
                600,
                result.workedMinutes()
        );

        assertEquals(
                480,
                result.regularMinutes()
        );

        assertEquals(
                120,
                result.overtimeMinutes()
        );

        assertEquals(
                600,
                result.holidayMinutes()
        );

        assertEquals(
                0,
                result.nightMinutes()
        );
    }

    @Test
    void holidayWithoutScheduledOrdinaryDurationMakesAllFactOvertime() {
        LocalDate date =
                LocalDate.of(2026, 8, 19);

        ActualWorkInterval fact =
                mock(ActualWorkInterval.class);

        when(fact.getId())
                .thenReturn(102L);

        ProductionCalendarDay holiday =
                mock(ProductionCalendarDay.class);

        when(
                scheduleDays.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                workNorm.basePlannedMinutes(null)
        ).thenReturn(0);

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "LOCAL_OVERRIDE"
                )
        ).thenReturn(
                Optional.of(holiday)
        );

        when(
                holiday.getPayrollEffect()
        ).thenReturn("HOLIDAY");

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(fact)
        );

        when(
                allocation.netSegments(fact)
        ).thenReturn(
                List.of(
                        segment(
                                "2026-08-19T10:00",
                                "2026-08-19T14:00"
                        )
                )
        );

        var result =
                service.classify(
                        user,
                        date
                );

        assertEquals(
                0,
                result.ordinaryThresholdMinutes()
        );

        assertEquals(
                240,
                result.workedMinutes()
        );

        assertEquals(
                0,
                result.regularMinutes()
        );

        assertEquals(
                240,
                result.overtimeMinutes()
        );

        assertEquals(
                240,
                result.holidayMinutes()
        );
    }

    @Test
    void localProductionOverrideWinsOverBaseHolidayClassification() {
        LocalDate date =
                LocalDate.of(2026, 8, 20);

        DayEntry schedule =
                mock(DayEntry.class);

        ActualWorkInterval fact =
                mock(ActualWorkInterval.class);

        when(fact.getId())
                .thenReturn(103L);

        ProductionCalendarDay local =
                mock(ProductionCalendarDay.class);

        ProductionCalendarDay base =
                mock(ProductionCalendarDay.class);

        when(
                scheduleDays.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(
                Optional.of(schedule)
        );

        when(
                workNorm.basePlannedMinutes(schedule)
        ).thenReturn(60);

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "LOCAL_OVERRIDE"
                )
        ).thenReturn(
                Optional.of(local)
        );

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "BASE"
                )
        ).thenReturn(
                Optional.of(base)
        );

        when(
                local.getPayrollEffect()
        ).thenReturn("NONE");

        when(
                base.getPayrollEffect()
        ).thenReturn("HOLIDAY");

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(fact)
        );

        when(
                allocation.netSegments(fact)
        ).thenReturn(
                List.of(
                        segment(
                                "2026-08-20T22:00",
                                "2026-08-20T23:00"
                        )
                )
        );

        var result =
                service.classify(
                        user,
                        date
                );

        assertFalse(result.holiday());

        assertEquals(
                60,
                result.regularMinutes()
        );

        assertEquals(
                60,
                result.nightMinutes()
        );

        assertEquals(
                0,
                result.holidayMinutes()
        );

        assertEquals(
                0,
                result.overtimeMinutes()
        );

        /*
         * Local override short-circuits BASE lookup exactly like the
         * Production Calendar effective-day semantics.
         */
        verify(
                productionDays,
                never()
        ).findByOwnerAndDateAndLayer(
                user,
                date,
                "BASE"
        );
    }

    @Test
    void crossMidnightAllocationFeedsOnlyRequestedSourceDate() {
        LocalDate date =
                LocalDate.of(2026, 8, 21);

        ActualWorkInterval fact =
                mock(ActualWorkInterval.class);

        when(fact.getId())
                .thenReturn(104L);

        when(
                scheduleDays.findByOwnerAndDate(
                        user,
                        date
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                workNorm.basePlannedMinutes(null)
        ).thenReturn(0);

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "LOCAL_OVERRIDE"
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "BASE"
                )
        ).thenReturn(
                Optional.empty()
        );

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(fact)
        );

        when(
                allocation.netSegments(fact)
        ).thenReturn(
                List.of(
                        segment(
                                "2026-08-21T23:00",
                                "2026-08-22T00:00"
                        ),
                        segment(
                                "2026-08-22T00:00",
                                "2026-08-22T02:00"
                        )
                )
        );

        var result =
                service.classify(
                        user,
                        date
                );

        assertEquals(
                60,
                result.workedMinutes()
        );

        assertEquals(
                60,
                result.overtimeMinutes()
        );

        assertEquals(
                60,
                result.nightMinutes()
        );

        assertEquals(
                0,
                result.holidayMinutes()
        );
    }

    private NetWorkSegment segment(
            String start,
            String end
    ) {
        return new NetWorkSegment(
                LocalDateTime.parse(start),
                LocalDateTime.parse(end),
                null,
                null,
                null
        );
    }
}
