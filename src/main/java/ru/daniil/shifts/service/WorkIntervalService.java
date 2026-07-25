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
}
