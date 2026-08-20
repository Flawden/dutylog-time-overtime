package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;

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

    public PlannedWorkDayAllocationService(
            WorkIntervalService workIntervals
    ) {
        this.workIntervals =
                workIntervals;
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

        int allocated =
                result.stream()
                        .mapToInt(
                                NetWorkSegment::minutes
                        )
                        .sum();

        if (allocated
                != interval.netMinutes()) {
            throw new IllegalStateException(
                    "Planned shift net allocation does not match canonical shift net minutes"
            );
        }

        return List.copyOf(
                result
        );
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
