package ru.daniil.shifts.service;

import org.junit.jupiter.api.Test;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.model.ShiftTypeBreakWindow;
import ru.daniil.shifts.model.WorkBreakAuthority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlannedExplicitBreakAllocationTest {
    private final UserTimeService userTime = new UserTimeService();
    private final WorkIntervalService intervals = new WorkIntervalService(userTime);
    private final WorkBreakWindowAuthorityService breakAuthority =
            new WorkBreakWindowAuthorityService(userTime);
    private final PlannedBreakWindowSnapshotService snapshots =
            new PlannedBreakWindowSnapshotService(breakAuthority);
    private final PlannedWorkDayAllocationService allocation =
            new PlannedWorkDayAllocationService(intervals, breakAuthority);

    @Test
    void explicitLunchAcrossMidnightAllocates210And450Minutes() {
        DayEntry day = explicitNight(LocalDate.of(2026, 9, 1), 210, 60);

        assertEquals(WorkBreakAuthority.EXPLICIT_WINDOWS, day.getShiftBreakAuthority());
        assertEquals(1, day.getShiftBreakWindows().size());
        assertEquals(660, day.getShiftNetMinutes());

        Map<LocalDate, Integer> byDate = allocation.netMinutesByDate(user(), day);
        assertEquals(210, byDate.get(LocalDate.of(2026, 9, 1)));
        assertEquals(450, byDate.get(LocalDate.of(2026, 9, 2)));

        var segments = allocation.netSegments(user(), day);
        assertEquals(2, segments.size());
        assertEquals(LocalTime.of(20, 0), segments.get(0).start().toLocalTime());
        assertEquals(LocalTime.of(23, 30), segments.get(0).end().toLocalTime());
        assertEquals(LocalTime.of(0, 30), segments.get(1).start().toLocalTime());
        assertEquals(LocalTime.of(8, 0), segments.get(1).end().toLocalTime());
    }

    @Test
    void tailAndNextShiftHeadCanContributeElevenPaidHoursToSameDate() {
        DayEntry first = explicitNight(LocalDate.of(2026, 9, 1), 210, 60);
        DayEntry second = explicitNight(LocalDate.of(2026, 9, 2), 210, 60);
        LocalDate date = LocalDate.of(2026, 9, 2);

        int total = allocation.netMinutesOnDate(user(), first, date)
                + allocation.netMinutesOnDate(user(), second, date);

        assertEquals(660, total);
        assertEquals(450, allocation.netMinutesOnDate(user(), first, date));
        assertEquals(210, allocation.netMinutesOnDate(user(), second, date));
    }

    @Test
    void changingTemplateAfterCaptureDoesNotMoveHistoricalLunch() {
        ShiftType shift = explicitNightShift(210, 60);
        DayEntry day = capture(LocalDate.of(2026, 9, 1), shift);

        shift.replaceExplicitBreakWindows(List.of(
                new ShiftTypeBreakWindow(shift, 0, 300, 60)
        ));

        Map<LocalDate, Integer> byDate = allocation.netMinutesByDate(user(), day);
        assertEquals(210, byDate.get(LocalDate.of(2026, 9, 1)));
        assertEquals(450, byDate.get(LocalDate.of(2026, 9, 2)));
        assertEquals(LocalTime.of(23, 30),
                day.getShiftBreakWindows().get(0).getSourceStartLocal().toLocalTime());
    }

    @Test
    void legacyScalarBreakKeepsHistoricalEarlyConsumptionSemantics() {
        ShiftType shift = new ShiftType(
                user(), "Legacy night", 12, "#000000", false,
                LocalTime.of(20, 0), LocalTime.of(8, 0), 60, 11.0
        );
        DayEntry day = capture(LocalDate.of(2026, 9, 1), shift);

        assertEquals(WorkBreakAuthority.LEGACY_EARLY_TOTAL, day.getShiftBreakAuthority());
        Map<LocalDate, Integer> byDate = allocation.netMinutesByDate(user(), day);
        assertEquals(180, byDate.get(LocalDate.of(2026, 9, 1)));
        assertEquals(480, byDate.get(LocalDate.of(2026, 9, 2)));
    }

    @Test
    void overlappingExplicitWindowsAreRejectedBeforeSnapshot() {
        ShiftType shift = new ShiftType(
                user(), "Bad", 12, "#000000", false,
                LocalTime.of(20, 0), LocalTime.of(8, 0), 0, 12.0
        );
        shift.replaceExplicitBreakWindows(List.of(
                new ShiftTypeBreakWindow(shift, 0, 180, 60),
                new ShiftTypeBreakWindow(shift, 1, 210, 60)
        ));
        DayEntry day = new DayEntry(user(), LocalDate.of(2026, 9, 1));
        day.setShiftType(shift);
        var interval = intervals.resolveInZone(
                ZoneId.of("Asia/Yekaterinburg"),
                day.getDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getBreakMinutes()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> snapshots.captureCurrentAssignment(day, interval)
        );
    }

    @Test
    void legacyFreezeDoesNotInventExplicitWindowsFromCurrentTemplate() {
        ShiftType shift = explicitNightShift(210, 60);
        DayEntry legacy = new DayEntry(user(), LocalDate.of(2025, 9, 1));
        legacy.setShiftType(shift);
        var interval = intervals.resolveInZone(
                ZoneId.of("Asia/Yekaterinburg"),
                legacy.getDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getBreakMinutes()
        );

        snapshots.captureLegacyEvidence(legacy, interval);

        assertEquals(WorkBreakAuthority.LEGACY_EARLY_TOTAL,
                legacy.getShiftBreakAuthority());
        assertTrue(legacy.getShiftBreakWindows().isEmpty());
        assertEquals(660, legacy.getShiftNetMinutes());
    }

    private DayEntry explicitNight(LocalDate date, int offset, int duration) {
        return capture(date, explicitNightShift(offset, duration));
    }

    private ShiftType explicitNightShift(int offset, int duration) {
        ShiftType shift = new ShiftType(
                user(), "Night", 12, "#000000", false,
                LocalTime.of(20, 0), LocalTime.of(8, 0), duration, 11.0
        );
        shift.replaceExplicitBreakWindows(List.of(
                new ShiftTypeBreakWindow(shift, 0, offset, duration)
        ));
        return shift;
    }

    private DayEntry capture(LocalDate date, ShiftType shift) {
        DayEntry day = new DayEntry(user(), date);
        day.setShiftType(shift);
        var interval = intervals.resolveInZone(
                ZoneId.of("Asia/Yekaterinburg"),
                date,
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getBreakMinutes()
        );
        snapshots.captureCurrentAssignment(day, interval);
        return day;
    }

    private AppUser user() {
        AppUser user = new AppUser("planned-break-owner", "{noop}x");
        user.setWorkTimezone("Asia/Yekaterinburg");
        user.setDisplayTimezone("Asia/Yekaterinburg");
        return user;
    }
}
