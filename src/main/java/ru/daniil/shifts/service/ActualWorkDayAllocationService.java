package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.ActualWorkInterval;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Splits one explicit factual-work interval by calendar day.
 * The interval stays one user fact; this service only derives per-date net minutes.
 * Unpaid break minutes are consumed from the earliest clock minutes, matching the
 * existing overtime interval splitter semantics.
 */
@Service
public class ActualWorkDayAllocationService {

    public Map<LocalDate, Integer> netMinutesByDate(ActualWorkInterval interval) {
        LinkedHashMap<LocalDate, Integer> result = new LinkedHashMap<>();
        if (interval == null || interval.getWorkDate() == null || interval.getEndDate() == null
                || interval.getStartTime() == null || interval.getEndTime() == null) {
            return result;
        }

        LocalDateTime start = interval.getWorkDate().atTime(interval.getStartTime());
        LocalDateTime end = interval.getEndDate().atTime(interval.getEndTime());
        if (!end.isAfter(start)) return result;

        int breakLeft = Math.max(0, interval.getBreakMinutes());
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime segmentEnd = nextMidnight.isBefore(end) ? nextMidnight : end;
            int rawMinutes = Math.toIntExact(Duration.between(cursor, segmentEnd).toMinutes());
            int segmentBreak = Math.min(breakLeft, Math.max(0, rawMinutes));
            breakLeft -= segmentBreak;
            int netMinutes = Math.max(0, rawMinutes - segmentBreak);
            if (netMinutes > 0) result.merge(cursor.toLocalDate(), netMinutes, Integer::sum);
            cursor = segmentEnd;
        }
        return result;
    }

    public int netMinutesOnDate(ActualWorkInterval interval, LocalDate date) {
        return netMinutesByDate(interval).getOrDefault(date, 0);
    }
}
