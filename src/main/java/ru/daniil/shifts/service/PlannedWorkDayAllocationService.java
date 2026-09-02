package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.WorkBreakAuthority;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical clock allocation of one dated planned shift.
 *
 * This service does not decide Production Calendar required norm, Payroll
 * ownership or compensation. It only projects the immutable dated shift
 * occurrence into source-calendar net-work segments.
 *
 * Break semantics intentionally match ActualWorkDayAllocationService:
 * unpaid break minutes are consumed from the earliest clock minutes.
 */
@Service
public class PlannedWorkDayAllocationService {

    private final WorkIntervalService workIntervals;
    private final WorkBreakWindowAuthorityService breakAuthority;

    public PlannedWorkDayAllocationService(
            WorkIntervalService workIntervals,
            WorkBreakWindowAuthorityService breakAuthority
    ) {
        this.workIntervals = workIntervals;
        this.breakAuthority = breakAuthority;
    }

    public List<NetWorkSegment> netSegments(
            AppUser user,
            DayEntry day
    ) {
        if (user == null
                || day == null
                || day.getDate() == null
                || day.getShiftType() == null) {
            return List.of();
        }

        WorkIntervalService.ResolvedWorkInterval interval =
                workIntervals.resolveShift(
                        user,
                        day
                );

        if (interval.startInstant() == null
                || interval.endInstant() == null
                || interval.workTimezone() == null
                || !interval.endInstant().isAfter(
                        interval.startInstant()
                )) {
            return List.of();
        }

        ZoneId sourceZone =
                ZoneId.of(
                        interval.workTimezone()
                );

        if (day.getShiftBreakAuthority() == WorkBreakAuthority.EXPLICIT_WINDOWS) {
            if (!day.hasShiftOccurrenceSnapshot()) {
                throw new IllegalStateException(
                        "Explicit planned breaks require a dated shift occurrence snapshot"
                );
            }
            List<WorkBreakWindowAuthorityService.AbsoluteBreakWindow> windows =
                    day.getShiftBreakWindows().stream()
                            .map(window ->
                                    new WorkBreakWindowAuthorityService.AbsoluteBreakWindow(
                                            window.getPosition(),
                                            window.getStartInstant(),
                                            window.getEndInstant()
                                    )
                            )
                            .toList();

            List<NetWorkSegment> result = new ArrayList<>();
            for (WorkBreakWindowAuthorityService.PaidWorkInterval paid :
                    breakAuthority.subtractAbsolute(
                            interval.startInstant(),
                            interval.endInstant(),
                            windows
                    )) {
                appendSourceCalendarSegments(
                        result,
                        paid.startInstant(),
                        paid.endInstant(),
                        sourceZone
                );
            }
            validateAllocatedMinutes(result, interval.netMinutes());
            return List.copyOf(result);
        }

        List<NetWorkSegment> result =
                new ArrayList<>();

        int breakLeft =
                Math.max(
                        0,
                        interval.breakMinutes()
                );

        Instant cursor =
                interval.startInstant();

        while (cursor.isBefore(
                interval.endInstant()
        )) {
            ZonedDateTime localCursor =
                    cursor.atZone(
                            sourceZone
                    );

            Instant nextMidnight =
                    localCursor
                            .toLocalDate()
                            .plusDays(1)
                            .atStartOfDay(sourceZone)
                            .toInstant();

            Instant segmentEnd =
                    nextMidnight.isBefore(
                            interval.endInstant()
                    )
                            ? nextMidnight
                            : interval.endInstant();

            int rawMinutes =
                    Math.toIntExact(
                            Duration.between(
                                    cursor,
                                    segmentEnd
                            ).toMinutes()
                    );

            int segmentBreak =
                    Math.min(
                            breakLeft,
                            Math.max(
                                    0,
                                    rawMinutes
                            )
                    );

            breakLeft -=
                    segmentBreak;

            Instant netStart =
                    cursor.plusSeconds(
                            segmentBreak * 60L
                    );

            if (segmentEnd.isAfter(
                    netStart
            )) {
                result.add(
                        new NetWorkSegment(
                                netStart
                                        .atZone(sourceZone)
                                        .toLocalDateTime(),
                                segmentEnd
                                        .atZone(sourceZone)
                                        .toLocalDateTime(),
                                netStart,
                                segmentEnd,
                                sourceZone.getId()
                        )
                );
            }

            cursor =
                    segmentEnd;
        }

        validateAllocatedMinutes(result, interval.netMinutes());


        return List.copyOf(
                result
        );
    }

    private static void appendSourceCalendarSegments(
            List<NetWorkSegment> result,
            Instant start,
            Instant end,
            ZoneId sourceZone
    ) {
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            Instant nextMidnight = cursor.atZone(sourceZone)
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(sourceZone)
                    .toInstant();
            Instant segmentEnd = nextMidnight.isBefore(end) ? nextMidnight : end;
            if (segmentEnd.isAfter(cursor)) {
                result.add(new NetWorkSegment(
                        cursor.atZone(sourceZone).toLocalDateTime(),
                        segmentEnd.atZone(sourceZone).toLocalDateTime(),
                        cursor,
                        segmentEnd,
                        sourceZone.getId()
                ));
            }
            cursor = segmentEnd;
        }
    }

    private static void validateAllocatedMinutes(
            List<NetWorkSegment> result,
            long expectedNetMinutes
    ) {
        int allocated = result.stream()
                .mapToInt(NetWorkSegment::minutes)
                .sum();
        if (allocated != expectedNetMinutes) {
            throw new IllegalStateException(
                    "Planned shift net allocation does not match canonical shift net minutes"
            );
        }
    }

    public Map<LocalDate, Integer> netMinutesByDate(
            AppUser user,
            DayEntry day
    ) {
        LinkedHashMap<LocalDate, Integer> result =
                new LinkedHashMap<>();

        for (NetWorkSegment segment :
                netSegments(user, day)) {

            result.merge(
                    segment.sourceDate(),
                    segment.minutes(),
                    Integer::sum
            );
        }

        return result;
    }

    public int netMinutesOnDate(
            AppUser user,
            DayEntry day,
            LocalDate date
    ) {
        if (date == null) {
            return 0;
        }

        return netMinutesByDate(
                user,
                day
        ).getOrDefault(
                date,
                0
        );
    }

    public record NetWorkSegment(
            LocalDateTime start,
            LocalDateTime end,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone
    ) {
        public NetWorkSegment {
            if (start == null
                    || end == null
                    || startInstant == null
                    || endInstant == null
                    || sourceTimezone == null
                    || sourceTimezone.isBlank()
                    || !endInstant.isAfter(
                            startInstant
                    )) {
                throw new IllegalArgumentException(
                        "Planned net segment requires complete positive absolute identity"
                );
            }

            if (!start.toLocalDate().equals(
                    end.minusNanos(1)
                            .toLocalDate()
            )) {
                throw new IllegalArgumentException(
                        "Planned net segment must stay inside one source calendar date"
                );
            }
        }

        public LocalDate sourceDate() {
            return start.toLocalDate();
        }

        public int minutes() {
            return Math.toIntExact(
                    Duration.between(
                            startInstant,
                            endInstant
                    ).toMinutes()
            );
        }
    }
}
