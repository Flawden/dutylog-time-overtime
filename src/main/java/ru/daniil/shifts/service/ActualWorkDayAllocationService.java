package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.ActualWorkInterval;
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
 * Splits one explicit factual-work interval by source calendar day.
 *
 * New intervals use their persisted historical absolute identity. Legacy
 * scalar break rows retain the historical earliest-consumption rule exactly.
 * EXPLICIT_WINDOWS rows subtract their frozen absolute break snapshots first,
 * then split only the remaining paid intervals by source calendar day.
 */
@Service
public class ActualWorkDayAllocationService {
    private final WorkBreakWindowAuthorityService breakAuthority;

    public ActualWorkDayAllocationService(
            WorkBreakWindowAuthorityService breakAuthority
    ) {
        this.breakAuthority = breakAuthority;
    }

    public List<NetWorkSegment> netSegments(ActualWorkInterval interval) {
        if (interval == null
                || interval.getWorkDate() == null
                || interval.getEndDate() == null
                || interval.getStartTime() == null
                || interval.getEndTime() == null) {
            return List.of();
        }

        if (interval.getBreakAuthority() == WorkBreakAuthority.EXPLICIT_WINDOWS) {
            return interval.hasAbsoluteIdentity()
                    ? exactExplicitSegments(interval)
                    : List.of();
        }

        return interval.hasAbsoluteIdentity()
                ? exactLegacySegments(interval)
                : legacySegments(interval);
    }

    private List<NetWorkSegment> exactExplicitSegments(
            ActualWorkInterval interval
    ) {
        ZoneId zone;
        try {
            zone = ZoneId.of(interval.getSourceTimezone());
        } catch (Exception ex) {
            return List.of();
        }

        Instant start = interval.getStartInstant();
        Instant end = interval.getEndInstant();
        if (start == null || end == null || !end.isAfter(start)) {
            return List.of();
        }

        List<WorkBreakWindowAuthorityService.AbsoluteBreakWindow> breaks =
                interval.getBreakWindows().stream()
                        .map(window ->
                                new WorkBreakWindowAuthorityService.AbsoluteBreakWindow(
                                        window.getPosition(),
                                        window.getStartInstant(),
                                        window.getEndInstant()
                                )
                        )
                        .toList();

        List<WorkBreakWindowAuthorityService.PaidWorkInterval> paid =
                breakAuthority.subtractAbsolute(
                        start,
                        end,
                        breaks
                );

        List<NetWorkSegment> result = new ArrayList<>();
        for (WorkBreakWindowAuthorityService.PaidWorkInterval slice : paid) {
            appendExactCalendarSegments(
                    result,
                    slice.startInstant(),
                    slice.endInstant(),
                    zone
            );
        }
        return List.copyOf(result);
    }

    /** Exact historical identity + legacy scalar earliest-break semantics. */
    private List<NetWorkSegment> exactLegacySegments(
            ActualWorkInterval interval
    ) {
        ZoneId zone;
        try {
            zone = ZoneId.of(interval.getSourceTimezone());
        } catch (Exception ex) {
            return legacySegments(interval);
        }

        Instant start = interval.getStartInstant();
        Instant end = interval.getEndInstant();

        if (start == null || end == null || !end.isAfter(start)) {
            return List.of();
        }

        List<NetWorkSegment> result = new ArrayList<>();
        int breakLeft = Math.max(0, interval.getBreakMinutes());
        Instant cursor = start;

        while (cursor.isBefore(end)) {
            ZonedDateTime localCursor = cursor.atZone(zone);

            Instant nextMidnight = localCursor
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant();

            Instant segmentEnd = nextMidnight.isBefore(end)
                    ? nextMidnight
                    : end;

            int rawMinutes = Math.toIntExact(
                    Duration.between(cursor, segmentEnd).toMinutes()
            );

            int segmentBreak = Math.min(
                    breakLeft,
                    Math.max(0, rawMinutes)
            );
            breakLeft -= segmentBreak;

            Instant netStart = cursor.plusSeconds(segmentBreak * 60L);

            if (segmentEnd.isAfter(netStart)) {
                result.add(new NetWorkSegment(
                        netStart.atZone(zone).toLocalDateTime(),
                        segmentEnd.atZone(zone).toLocalDateTime(),
                        netStart,
                        segmentEnd,
                        zone.getId()
                ));
            }

            cursor = segmentEnd;
        }

        return List.copyOf(result);
    }

    private void appendExactCalendarSegments(
            List<NetWorkSegment> result,
            Instant start,
            Instant end,
            ZoneId zone
    ) {
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            ZonedDateTime localCursor = cursor.atZone(zone);
            Instant nextMidnight = localCursor
                    .toLocalDate()
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant();
            Instant segmentEnd = nextMidnight.isBefore(end)
                    ? nextMidnight
                    : end;

            result.add(new NetWorkSegment(
                    cursor.atZone(zone).toLocalDateTime(),
                    segmentEnd.atZone(zone).toLocalDateTime(),
                    cursor,
                    segmentEnd,
                    zone.getId()
            ));
            cursor = segmentEnd;
        }
    }

    /** Legacy rows without absolute identity keep their old wall-clock path. */
    private List<NetWorkSegment> legacySegments(ActualWorkInterval interval) {
        List<NetWorkSegment> result = new ArrayList<>();

        LocalDateTime start =
                interval.getWorkDate().atTime(interval.getStartTime());

        LocalDateTime end =
                interval.getEndDate().atTime(interval.getEndTime());

        if (!end.isAfter(start)) {
            return List.of();
        }

        int breakLeft = Math.max(0, interval.getBreakMinutes());
        LocalDateTime cursor = start;

        while (cursor.isBefore(end)) {
            LocalDateTime nextMidnight =
                    cursor.toLocalDate().plusDays(1).atStartOfDay();

            LocalDateTime segmentEnd =
                    nextMidnight.isBefore(end) ? nextMidnight : end;

            int rawMinutes = Math.toIntExact(
                    Duration.between(cursor, segmentEnd).toMinutes()
            );

            int segmentBreak = Math.min(
                    breakLeft,
                    Math.max(0, rawMinutes)
            );
            breakLeft -= segmentBreak;

            LocalDateTime netStart = cursor.plusMinutes(segmentBreak);

            if (segmentEnd.isAfter(netStart)) {
                result.add(new NetWorkSegment(
                        netStart,
                        segmentEnd,
                        null,
                        null,
                        null
                ));
            }

            cursor = segmentEnd;
        }

        return List.copyOf(result);
    }

    public Map<LocalDate, Integer> netMinutesByDate(
            ActualWorkInterval interval
    ) {
        LinkedHashMap<LocalDate, Integer> result =
                new LinkedHashMap<>();

        for (NetWorkSegment segment : netSegments(interval)) {
            int minutes = segment.minutes();

            if (minutes > 0) {
                result.merge(
                        segment.start().toLocalDate(),
                        minutes,
                        Integer::sum
                );
            }
        }

        return result;
    }

    public int netMinutesOnDate(
            ActualWorkInterval interval,
            LocalDate date
    ) {
        return netMinutesByDate(interval).getOrDefault(date, 0);
    }

    public record NetWorkSegment(
            LocalDateTime start,
            LocalDateTime end,
            Instant startInstant,
            Instant endInstant,
            String sourceTimezone
    ) {
        public NetWorkSegment {
            if (start == null || end == null) {
                throw new IllegalArgumentException(
                        "Net work segment requires local boundaries"
                );
            }

            boolean hasAnyAbsoluteField =
                    startInstant != null
                            || endInstant != null
                            || sourceTimezone != null;

            if (hasAnyAbsoluteField) {
                if (startInstant == null
                        || endInstant == null
                        || sourceTimezone == null
                        || !endInstant.isAfter(startInstant)) {
                    throw new IllegalArgumentException(
                            "Exact net segment requires complete absolute identity"
                    );
                }
            } else if (!end.isAfter(start)) {
                throw new IllegalArgumentException(
                        "Net work segment must have positive duration"
                );
            }

            if (!start.toLocalDate().equals(
                    end.minusNanos(1).toLocalDate()
            )) {
                throw new IllegalArgumentException(
                        "Net work segment must stay inside one source calendar date"
                );
            }
        }

        public boolean exact() {
            return startInstant != null
                    && endInstant != null
                    && sourceTimezone != null;
        }

        public int minutes() {
            return exact()
                    ? Math.toIntExact(
                            Duration.between(startInstant, endInstant)
                                    .toMinutes()
                    )
                    : Math.toIntExact(
                            Duration.between(start, end)
                                    .toMinutes()
                    );
        }
    }
}
