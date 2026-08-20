package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ProductionCalendarDayRepository;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayClassificationSourceIdentityTest {

    @Test
    void sourceActualWorkAndWorkedOrdinalSurviveDailyClassification() {
        ActualWorkIntervalRepository actualWork =
                mock(ActualWorkIntervalRepository.class);

        ActualWorkDayAllocationService allocation =
                mock(ActualWorkDayAllocationService.class);

        DayEntryRepository scheduleDays =
                mock(DayEntryRepository.class);

        ProductionCalendarDayRepository productionDays =
                mock(ProductionCalendarDayRepository.class);

        WorkNormService workNorm =
                mock(WorkNormService.class);

        PayClassificationService service =
                new PayClassificationService(
                        actualWork,
                        allocation,
                        scheduleDays,
                        productionDays,
                        workNorm,
                        new PayClassificationEngine()
                );

        AppUser user =
                new AppUser(
                        "classification-source",
                        "{noop}irrelevant"
                );

        LocalDate date =
                LocalDate.of(2026, 8, 18);

        DayEntry schedule =
                mock(DayEntry.class);

        ActualWorkInterval first =
                mock(ActualWorkInterval.class);

        ActualWorkInterval second =
                mock(ActualWorkInterval.class);

        when(first.getId())
                .thenReturn(101L);

        when(second.getId())
                .thenReturn(202L);

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
        ).thenReturn(Optional.empty());

        when(
                productionDays.findByOwnerAndDateAndLayer(
                        user,
                        date,
                        "BASE"
                )
        ).thenReturn(Optional.empty());

        when(
                actualWork.findOverlappingRange(
                        user,
                        date,
                        date
                )
        ).thenReturn(
                List.of(first, second)
        );

        when(
                allocation.netSegments(first)
        ).thenReturn(
                List.of(
                        legacy(
                                "2026-08-18T08:00",
                                "2026-08-18T12:00"
                        )
                )
        );

        when(
                allocation.netSegments(second)
        ).thenReturn(
                List.of(
                        legacy(
                                "2026-08-18T14:00",
                                "2026-08-18T19:00"
                        )
                )
        );

        var result =
                service.classify(
                        user,
                        date
                );

        assertEquals(
                540,
                result.workedMinutes()
        );

        assertEquals(
                60,
                result.overtimeMinutes()
        );

        ClassificationSlice overtime =
                result.slices().stream()
                        .filter(
                                ClassificationSlice::overtime
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                202L,
                overtime.sourceActualWorkIntervalId()
        );

        assertEquals(
                480,
                overtime.workedOrdinalStartMinutes()
        );

        assertEquals(
                60,
                overtime.minutes()
        );

        assertEquals(
                LocalDateTime.parse(
                        "2026-08-18T18:00"
                ),
                overtime.start()
        );

        assertEquals(
                LocalDateTime.parse(
                        "2026-08-18T19:00"
                ),
                overtime.end()
        );

        assertTrue(
                result.slices().stream()
                        .allMatch(slice ->
                                slice.sourceActualWorkIntervalId()
                                        != null
                        )
        );
    }

    private NetWorkSegment legacy(
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
