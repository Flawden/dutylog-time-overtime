package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannedWorkDayAllocationServiceTest {

    private final UserTimeService time =
            new UserTimeService(
                    Clock.systemUTC()
            );

    private final WorkIntervalService intervals =
            new WorkIntervalService(
                    time
            );

    private final PlannedWorkDayAllocationService service =
            new PlannedWorkDayAllocationService(
                    intervals
            );

    @Test
    void overnightPlanUsesTheSameHistoricalEarlyBreakSemanticsAsActualWork() {
        AppUser user =
                user("UTC");

        DayEntry day =
                day(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                18
                        ),
                        shift(
                                user,
                                "Night",
                                LocalTime.of(
                                        20,
                                        0
                                ),
                                LocalTime.of(
                                        8,
                                        0
                                ),
                                60,
                                11.0
                        )
                );

        var segments =
                service.netSegments(
                        user,
                        day
                );

        assertEquals(
                2,
                segments.size()
        );

        assertEquals(
                "2026-08-18T21:00",
                segments.get(0)
                        .start()
                        .toString()
        );

        assertEquals(
                "2026-08-19T00:00",
                segments.get(0)
                        .end()
                        .toString()
        );

        assertEquals(
                180,
                segments.get(0)
                        .minutes()
        );

        assertEquals(
                "2026-08-19T00:00",
                segments.get(1)
                        .start()
                        .toString()
        );

        assertEquals(
                "2026-08-19T08:00",
                segments.get(1)
                        .end()
                        .toString()
        );

        assertEquals(
                480,
                segments.get(1)
                        .minutes()
        );

        assertEquals(
                180,
                service.netMinutesOnDate(
                        user,
                        day,
                        LocalDate.of(
                                2026,
                                8,
                                18
                        )
                )
        );

        assertEquals(
                480,
                service.netMinutesOnDate(
                        user,
                        day,
                        LocalDate.of(
                                2026,
                                8,
                                19
                        )
                )
        );
    }

    @Test
    void ordinaryDayPlanConsumesBreakFromTheBeginningAndPreservesNetMinutes() {
        AppUser user =
                user("UTC");

        DayEntry day =
                day(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                20
                        ),
                        shift(
                                user,
                                "Day",
                                LocalTime.of(
                                        8,
                                        30
                                ),
                                LocalTime.of(
                                        17,
                                        0
                                ),
                                30,
                                8.0
                        )
                );

        var segments =
                service.netSegments(
                        user,
                        day
                );

        assertEquals(
                1,
                segments.size()
        );

        assertEquals(
                "2026-08-20T09:00",
                segments.get(0)
                        .start()
                        .toString()
        );

        assertEquals(
                "2026-08-20T17:00",
                segments.get(0)
                        .end()
                        .toString()
        );

        assertEquals(
                480,
                segments.get(0)
                        .minutes()
        );
    }

    @Test
    void monthBoundaryDoesNotCollapseNextDayIntoTheShiftStartDate() {
        AppUser user =
                user("UTC");

        DayEntry day =
                day(
                        user,
                        LocalDate.of(
                                2026,
                                8,
                                31
                        ),
                        shift(
                                user,
                                "Night",
                                LocalTime.of(
                                        20,
                                        0
                                ),
                                LocalTime.of(
                                        8,
                                        0
                                ),
                                60,
                                11.0
                        )
                );

        var byDate =
                service.netMinutesByDate(
                        user,
                        day
                );

        assertEquals(
                2,
                byDate.size()
        );

        assertEquals(
                180,
                byDate.get(
                        LocalDate.of(
                                2026,
                                8,
                                31
                        )
                )
        );

        assertEquals(
                480,
                byDate.get(
                        LocalDate.of(
                                2026,
                                9,
                                1
                        )
                )
        );
    }

    @Test
    void dstUsesAbsoluteElapsedMinutesAndSourceCalendarBoundaries() {
        AppUser user =
                user(
                        "Europe/Berlin"
                );

        DayEntry day =
                day(
                        user,
                        LocalDate.of(
                                2026,
                                3,
                                29
                        ),
                        shift(
                                user,
                                "DST",
                                LocalTime.MIDNIGHT,
                                LocalTime.of(
                                        8,
                                        0
                                ),
                                0,
                                7.0
                        )
                );

        var segments =
                service.netSegments(
                        user,
                        day
                );

        assertEquals(
                1,
                segments.size()
        );

        assertEquals(
                420,
                segments.get(0)
                        .minutes()
        );
    }

    private AppUser user(
            String timezone
    ) {
        AppUser user =
                new AppUser(
                        "planned-allocation-"
                                + timezone.replace(
                                '/',
                                '-'
                        ),
                        "hash"
                );

        user.setWorkTimezone(
                timezone
        );

        user.setDisplayTimezone(
                timezone
        );

        return user;
    }

    private DayEntry day(
            AppUser owner,
            LocalDate date,
            ShiftType shift
    ) {
        DayEntry day =
                new DayEntry(
                        owner,
                        date
                );

        day.setShiftType(
                shift
        );

        return day;
    }

    private ShiftType shift(
            AppUser owner,
            String name,
            LocalTime start,
            LocalTime end,
            int breakMinutes,
            double plannedHours
    ) {
        return new ShiftType(
                owner,
                name,
                plannedHours,
                "#123456",
                false,
                start,
                end,
                breakMinutes,
                plannedHours
        );
    }
}
