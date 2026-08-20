package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.service.ActualWorkDayAllocationService.NetWorkSegment;
import ru.daniil.shifts.service.PayClassificationEngine.ClassificationSlice;
import ru.daniil.shifts.service.PayClassificationEngine.NightWindow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayClassificationEngineTest {

    private final PayClassificationEngine engine =
            new PayClassificationEngine();

    private final NightWindow night =
            new NightWindow(
                    LocalTime.of(22, 0),
                    LocalTime.of(6, 0)
            );

    @Test
    void holidayTenHoursWithEightHourThresholdKeepsFirstEightRegularAndLastTwoOvertime() {
        LocalDate date =
                LocalDate.of(2026, 8, 18);

        var slices = engine.classifyDay(
                date,
                List.of(
                        legacy(
                                "2026-08-18T08:00",
                                "2026-08-18T18:00"
                        )
                ),
                480,
                true,
                night
        );

        assertEquals(2, slices.size());

        ClassificationSlice regular =
                slices.get(0);

        assertEquals(480, regular.minutes());
        assertTrue(regular.regular());
        assertFalse(regular.overtime());
        assertTrue(regular.holiday());
        assertFalse(regular.night());

        ClassificationSlice overtime =
                slices.get(1);

        assertEquals(120, overtime.minutes());
        assertFalse(overtime.regular());
        assertTrue(overtime.overtime());
        assertTrue(overtime.holiday());
        assertFalse(overtime.night());

        assertEquals(600, total(slices));
    }

    @Test
    void holidayWithoutOrdinaryThresholdClassifiesEveryWorkedMinuteAsOvertime() {
        var slices = engine.classifyDay(
                LocalDate.of(2026, 8, 18),
                List.of(
                        legacy(
                                "2026-08-18T10:00",
                                "2026-08-18T14:00"
                        )
                ),
                0,
                true,
                night
        );

        assertEquals(1, slices.size());

        var slice = slices.get(0);

        assertEquals(240, slice.minutes());
        assertFalse(slice.regular());
        assertTrue(slice.overtime());
        assertTrue(slice.holiday());
        assertFalse(slice.night());
    }

    @Test
    void nightAndOvertimeOverlapWithoutCombinatorialCategory() {
        var slices = engine.classifyDay(
                LocalDate.of(2026, 8, 18),
                List.of(
                        legacy(
                                "2026-08-18T20:00",
                                "2026-08-18T23:00"
                        )
                ),
                120,
                false,
                night
        );

        assertEquals(2, slices.size());

        var first = slices.get(0);

        assertEquals(120, first.minutes());
        assertTrue(first.regular());
        assertFalse(first.night());
        assertFalse(first.holiday());
        assertFalse(first.overtime());

        var second = slices.get(1);

        assertEquals(60, second.minutes());
        assertFalse(second.regular());
        assertTrue(second.night());
        assertFalse(second.holiday());
        assertTrue(second.overtime());
    }

    @Test
    void multipleFactsShareOneDailyOvertimeOrdinal() {
        var slices = engine.classifyDay(
                LocalDate.of(2026, 8, 18),
                List.of(
                        legacy(
                                "2026-08-18T08:00",
                                "2026-08-18T12:00"
                        ),
                        legacy(
                                "2026-08-18T14:00",
                                "2026-08-18T19:00"
                        )
                ),
                480,
                false,
                night
        );

        assertEquals(3, slices.size());

        assertEquals(
                480,
                slices.stream()
                        .filter(ClassificationSlice::regular)
                        .mapToInt(ClassificationSlice::minutes)
                        .sum()
        );

        assertEquals(
                60,
                slices.stream()
                        .filter(ClassificationSlice::overtime)
                        .mapToInt(ClassificationSlice::minutes)
                        .sum()
        );

        assertEquals(540, total(slices));
    }

    @Test
    void exactBerlinSpringForwardUsesRealElapsedMinutesAndLocalNightClock() {
        ZoneId berlin =
                ZoneId.of("Europe/Berlin");

        ZonedDateTime start =
                LocalDateTime.of(
                        2026, 3, 29, 0, 0
                ).atZone(berlin);

        ZonedDateTime end =
                LocalDateTime.of(
                        2026, 3, 29, 8, 0
                ).atZone(berlin);

        assertEquals(
                420,
                java.time.Duration.between(
                        start.toInstant(),
                        end.toInstant()
                ).toMinutes()
        );

        NetWorkSegment segment =
                new NetWorkSegment(
                        start.toLocalDateTime(),
                        end.toLocalDateTime(),
                        start.toInstant(),
                        end.toInstant(),
                        berlin.getId()
                );

        var slices = engine.classifyDay(
                LocalDate.of(2026, 3, 29),
                List.of(segment),
                480,
                false,
                night
        );

        assertEquals(420, total(slices));

        assertEquals(
                300,
                slices.stream()
                        .filter(ClassificationSlice::night)
                        .mapToInt(ClassificationSlice::minutes)
                        .sum(),
                "00:00-06:00 local contains only five real hours on spring-forward night"
        );

        assertEquals(
                420,
                slices.stream()
                        .filter(ClassificationSlice::regular)
                        .mapToInt(ClassificationSlice::minutes)
                        .sum()
        );

        assertEquals(
                0,
                slices.stream()
                        .filter(ClassificationSlice::overtime)
                        .mapToInt(ClassificationSlice::minutes)
                        .sum()
        );

        assertTrue(
                slices.stream()
                        .allMatch(ClassificationSlice::exact)
        );
    }

    @Test
    void rejectsMixedSourceDatesInOneDailyClassification() {
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.classifyDay(
                        LocalDate.of(2026, 8, 18),
                        List.of(
                                legacy(
                                        "2026-08-19T08:00",
                                        "2026-08-19T09:00"
                                )
                        ),
                        480,
                        false,
                        night
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

    private int total(
            List<ClassificationSlice> slices
    ) {
        return slices.stream()
                .mapToInt(ClassificationSlice::minutes)
                .sum();
    }
}
