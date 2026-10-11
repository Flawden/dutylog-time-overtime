package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledAbsenceMinutesServiceTest {
    private final UserTimeService time = new UserTimeService();
    private final ScheduledAbsenceMinutesService service =
            new ScheduledAbsenceMinutesService(
                    new WorkIntervalService(time),
                    new PlannedBreakWindowSnapshotService(
                            new WorkBreakWindowAuthorityService(time)),
                    time);
    private final LocalDate date = LocalDate.parse("2026-09-14");

    private AppUser owner() {
        AppUser owner = new AppUser("overlap", "{noop}unused");
        owner.setWorkTimezone("Asia/Yekaterinburg");
        owner.setDisplayTimezone("Asia/Yekaterinburg");
        return owner;
    }

    private DayEntry shift(AppUser owner, LocalTime start, LocalTime end) {
        DayEntry day = new DayEntry(owner, date);
        day.setShiftType(new ShiftType(owner, "Тест", 6.0, "#123456", false,
                start, end, 0, 6.0));
        return day;
    }

    @Test
    void boundariesAndPartialOverlapCountOnlyTimeInsideShift() {
        AppUser owner = owner();
        DayEntry day = shift(owner, LocalTime.of(10, 0), LocalTime.of(16, 0));
        assertAll(
                () -> assertEquals(0, service.partial(owner, day, date,
                        LocalTime.of(9, 0), LocalTime.of(10, 0), 360)),
                () -> assertEquals(0, service.partial(owner, day, date,
                        LocalTime.of(16, 0), LocalTime.of(17, 0), 360)),
                () -> assertEquals(60, service.partial(owner, day, date,
                        LocalTime.of(9, 0), LocalTime.of(11, 0), 360)),
                () -> assertEquals(60, service.partial(owner, day, date,
                        LocalTime.of(15, 0), LocalTime.of(17, 0), 360)),
                () -> assertEquals(360, service.partial(owner, day, date,
                        LocalTime.of(9, 0), LocalTime.of(17, 0), 360)));
    }

    @Test
    void explicitUnpaidBreakIsExcludedFromAbsenceMinutes() {
        AppUser owner = owner();
        DayEntry day = shift(owner, LocalTime.of(10, 0), LocalTime.of(16, 0));
        ZoneId zone = ZoneId.of("Asia/Yekaterinburg");
        LocalDateTime start = date.atTime(10, 0);
        LocalDateTime end = date.atTime(16, 0);
        LocalDateTime breakStart = date.atTime(12, 0);
        LocalDateTime breakEnd = date.atTime(12, 30);
        var window = new DayEntryShiftBreakWindow(day, 0, breakStart, breakEnd,
                breakStart.atZone(zone).toInstant(),
                breakEnd.atZone(zone).toInstant(), zone.getId());
        day.captureExplicitShiftOccurrence(start.atZone(zone).toInstant(),
                end.atZone(zone).toInstant(), zone.getId(), date,
                start.toLocalTime(), end.toLocalTime(), 30, 330, List.of(window));

        assertAll(
                () -> assertEquals(0, service.partial(owner, day, date,
                        LocalTime.of(12, 0), LocalTime.of(12, 30), 330)),
                () -> assertEquals(60, service.partial(owner, day, date,
                        LocalTime.of(11, 30), LocalTime.of(13, 0), 330)),
                () -> assertEquals(330, service.partial(owner, day, date,
                        LocalTime.of(9, 0), LocalTime.of(17, 0), 330)));
    }

    @Test
    void nightShiftIntersectionUsesActualCalendarDate() {
        AppUser owner = owner();
        DayEntry day = shift(owner, LocalTime.of(20, 0), LocalTime.of(8, 0));
        assertAll(
                () -> assertEquals(0, service.partial(owner, day, date,
                        LocalTime.of(7, 0), LocalTime.of(8, 0), 720)),
                () -> assertEquals(60, service.partial(owner, day, date,
                        LocalTime.of(19, 0), LocalTime.of(21, 0), 720)),
                () -> assertEquals(60, service.partial(owner, day, date.plusDays(1),
                        LocalTime.of(7, 0), LocalTime.of(9, 0), 720)));
    }
}
