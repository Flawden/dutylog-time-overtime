package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * Resolves work-local wall-clock ranges into absolute intervals.
 * This is the shared boundary that Overtime 2.0 will consume.
 */
@Service
public class WorkIntervalService {
    private final UserTimeService userTimeService;

    public WorkIntervalService(UserTimeService userTimeService) {
        this.userTimeService = userTimeService;
    }

    public ResolvedWorkInterval resolveShift(AppUser user, DayEntry dayEntry) {
        if (dayEntry == null || dayEntry.getDate() == null || dayEntry.getShiftType() == null) {
            throw new IllegalArgumentException("A dated shift entry is required");
        }
        ShiftType shift = dayEntry.getShiftType();
        return resolve(user, dayEntry.getDate(), shift.getStartTime(), shift.getEndTime(), shift.getBreakMinutes());
    }

    /**
     * Projects one dated work shift into both user zones without changing its
     * absolute identity. This is safe to call repeatedly after display-zone changes.
     */
    public ShiftProjection projectShift(AppUser user, DayEntry dayEntry) {
        return project(user, resolveShift(user, dayEntry));
    }

    public ShiftProjection project(AppUser user, ResolvedWorkInterval interval) {
        if (interval == null) {
            throw new IllegalArgumentException("Resolved work interval is required");
        }
        ZonedDateTime workStart = userTimeService.inWorkZone(interval.startInstant(), user);
        ZonedDateTime workEnd = userTimeService.inWorkZone(interval.endInstant(), user);
        ZonedDateTime displayStart = userTimeService.inDisplayZone(interval.startInstant(), user);
        ZonedDateTime displayEnd = userTimeService.inDisplayZone(interval.endInstant(), user);
        String workTimezone = userTimeService.workZone(user).getId();
        String displayTimezone = userTimeService.displayZone(user).getId();
        return new ShiftProjection(
                interval.startInstant(),
                interval.endInstant(),
                workStart,
                workEnd,
                displayStart,
                displayEnd,
                workTimezone,
                displayTimezone,
                interval.breakMinutes(),
                interval.elapsedMinutes(),
                interval.netMinutes(),
                !workStart.toLocalDate().equals(workEnd.toLocalDate()),
                !displayStart.toLocalDate().equals(displayEnd.toLocalDate()),
                workTimezone.equals(displayTimezone)
        );
    }

    public ResolvedWorkInterval resolve(AppUser user,
                                        LocalDate workDate,
                                        LocalTime startTime,
                                        LocalTime endTime,
                                        int breakMinutes) {
        if (workDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("Work date, start time and end time are required");
        }
        int safeBreak = Math.max(0, breakMinutes);
        LocalDateTime localStart = LocalDateTime.of(workDate, startTime);
        LocalDate endDate = endTime.isAfter(startTime) ? workDate : workDate.plusDays(1);
        LocalDateTime localEnd = LocalDateTime.of(endDate, endTime);
        Instant startInstant = userTimeService.toWorkInstant(user, localStart);
        Instant endInstant = userTimeService.toWorkInstant(user, localEnd);
        long elapsed = Duration.between(startInstant, endInstant).toMinutes();
        if (elapsed <= 0) {
            throw new IllegalArgumentException("Work interval must have a positive duration");
        }
        long net = Math.max(0L, elapsed - safeBreak);
        return new ResolvedWorkInterval(
                workDate,
                localStart,
                localEnd,
                startInstant,
                endInstant,
                userTimeService.workZone(user).getId(),
                elapsed,
                safeBreak,
                net,
                !workDate.equals(endDate)
        );
    }

    public record ResolvedWorkInterval(
            LocalDate workDate,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            Instant startInstant,
            Instant endInstant,
            String workTimezone,
            long elapsedMinutes,
            int breakMinutes,
            long netMinutes,
            boolean crossesMidnight
    ) {}

    public record ShiftProjection(
            Instant startInstant,
            Instant endInstant,
            ZonedDateTime workStart,
            ZonedDateTime workEnd,
            ZonedDateTime displayStart,
            ZonedDateTime displayEnd,
            String workTimezone,
            String displayTimezone,
            int breakMinutes,
            long elapsedMinutes,
            long netMinutes,
            boolean crossesWorkMidnight,
            boolean crossesDisplayMidnight,
            boolean sameTimezone
    ) {}
}
