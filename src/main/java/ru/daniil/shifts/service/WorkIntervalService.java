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
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Resolves wall-clock work ranges into immutable absolute occurrences and
 * reprojects those occurrences into the user's current canonical timezone.
 */
@Service
public class WorkIntervalService {
    private final UserTimeService userTimeService;

    public WorkIntervalService(UserTimeService userTimeService) {
        this.userTimeService = userTimeService;
    }

    /**
     * Resolves a concrete dated shift. Existing occurrence snapshots are the
     * source of truth; legacy rows fall back to the user's current timezone.
     */
    public ResolvedWorkInterval resolveShift(AppUser user, DayEntry dayEntry) {
        if (dayEntry == null || dayEntry.getDate() == null || dayEntry.getShiftType() == null) {
            throw new IllegalArgumentException("A dated shift entry is required");
        }
        if (dayEntry.hasShiftOccurrenceSnapshot()) {
            ZoneId sourceZone = userTimeService.resolveZone(
                    dayEntry.getShiftSourceTimezone(), userTimeService.workZone(user));
            ZonedDateTime sourceStart = dayEntry.getShiftStartInstant().atZone(sourceZone);
            ZonedDateTime sourceEnd = dayEntry.getShiftEndInstant().atZone(sourceZone);
            long elapsed = Duration.between(dayEntry.getShiftStartInstant(), dayEntry.getShiftEndInstant()).toMinutes();
            int breakMinutes = dayEntry.getShiftBreakMinutes();
            long netMinutes = dayEntry.getShiftNetMinutes() > 0
                    ? dayEntry.getShiftNetMinutes()
                    : Math.max(0L, elapsed - breakMinutes);
            return new ResolvedWorkInterval(
                    dayEntry.getShiftSourceDate() != null ? dayEntry.getShiftSourceDate() : sourceStart.toLocalDate(),
                    sourceStart.toLocalDateTime(),
                    sourceEnd.toLocalDateTime(),
                    dayEntry.getShiftStartInstant(),
                    dayEntry.getShiftEndInstant(),
                    sourceZone.getId(),
                    elapsed,
                    breakMinutes,
                    netMinutes,
                    !sourceStart.toLocalDate().equals(sourceEnd.toLocalDate()),
                    false
            );
        }

        ShiftType shift = dayEntry.getShiftType();
        ResolvedWorkInterval legacy = resolve(
                user,
                dayEntry.getDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getBreakMinutes());
        return new ResolvedWorkInterval(
                legacy.workDate(), legacy.localStart(), legacy.localEnd(),
                legacy.startInstant(), legacy.endInstant(), legacy.workTimezone(),
                legacy.elapsedMinutes(), legacy.breakMinutes(), legacy.netMinutes(),
                legacy.crossesMidnight(), true
        );
    }

    /** Projects one occurrence into its source zone and the user's current zone. */
    public ShiftProjection projectShift(AppUser user, DayEntry dayEntry) {
        return project(user, resolveShift(user, dayEntry));
    }

    public ShiftProjection project(AppUser user, ResolvedWorkInterval interval) {
        if (interval == null) {
            throw new IllegalArgumentException("Resolved work interval is required");
        }
        ZoneId sourceZone = userTimeService.resolveZone(
                interval.workTimezone(), userTimeService.workZone(user));
        ZoneId displayZone = userTimeService.workZone(user);
        ZonedDateTime sourceStart = interval.startInstant().atZone(sourceZone);
        ZonedDateTime sourceEnd = interval.endInstant().atZone(sourceZone);
        ZonedDateTime displayStart = interval.startInstant().atZone(displayZone);
        ZonedDateTime displayEnd = interval.endInstant().atZone(displayZone);
        return new ShiftProjection(
                interval.startInstant(),
                interval.endInstant(),
                sourceStart,
                sourceEnd,
                displayStart,
                displayEnd,
                sourceZone.getId(),
                displayZone.getId(),
                interval.breakMinutes(),
                interval.elapsedMinutes(),
                interval.netMinutes(),
                !sourceStart.toLocalDate().equals(sourceEnd.toLocalDate()),
                !displayStart.toLocalDate().equals(displayEnd.toLocalDate()),
                sourceZone.equals(displayZone),
                interval.legacyLocal()
        );
    }

    public ResolvedWorkInterval resolve(AppUser user,
                                        LocalDate workDate,
                                        LocalTime startTime,
                                        LocalTime endTime,
                                        int breakMinutes) {
        return resolveInZone(userTimeService.workZone(user), workDate, startTime, endTime, breakMinutes);
    }

    /** Resolves a wall-clock interval in an explicitly selected source zone. */
    public ResolvedWorkInterval resolveInZone(ZoneId sourceZone,
                                              LocalDate workDate,
                                              LocalTime startTime,
                                              LocalTime endTime,
                                              int breakMinutes) {
        if (workDate == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("Work date, start time and end time are required");
        }
        ZoneId safeZone = sourceZone == null ? UserTimeService.FALLBACK_ZONE : sourceZone;
        int safeBreak = Math.max(0, breakMinutes);
        LocalDateTime localStart = LocalDateTime.of(workDate, startTime);
        LocalDate endDate = endTime.isAfter(startTime) ? workDate : workDate.plusDays(1);
        LocalDateTime localEnd = LocalDateTime.of(endDate, endTime);
        Instant startInstant = userTimeService.resolveLocalDateTime(localStart, safeZone).toInstant();
        Instant endInstant = userTimeService.resolveLocalDateTime(localEnd, safeZone).toInstant();
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
                safeZone.getId(),
                elapsed,
                safeBreak,
                net,
                !workDate.equals(endDate),
                false
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
            boolean crossesMidnight,
            boolean legacyLocal
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
            boolean sameTimezone,
            boolean legacyLocal
    ) {}
}
