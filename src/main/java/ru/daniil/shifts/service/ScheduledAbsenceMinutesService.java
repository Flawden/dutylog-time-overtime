package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Shared scheduled paid-time intersection for absence projections and charges. */
@Service
public class ScheduledAbsenceMinutesService {
    private final WorkIntervalService intervals;
    private final PlannedBreakWindowSnapshotService breaks;
    private final UserTimeService time;

    public ScheduledAbsenceMinutesService(
            WorkIntervalService intervals,
            PlannedBreakWindowSnapshotService breaks,
            UserTimeService time) {
        this.intervals = intervals;
        this.breaks = breaks;
        this.time = time;
    }

    public int partial(AppUser owner, DayEntry day, LocalDate date,
                       LocalTime start, LocalTime end, int plannedMinutes) {
        return partial(owner, day, date, start, end, plannedMinutes, null);
    }

    public int partial(AppUser owner, DayEntry day, LocalDate date,
                       LocalTime start, LocalTime end, int plannedMinutes,
                       Integer unpaidBreakOverlapMinutes) {
        if (unpaidBreakOverlapMinutes != null && (unpaidBreakOverlapMinutes < 0 || unpaidBreakOverlapMinutes > 1440)) {
            throw ApiException.badRequest("Перерыв в интервале отсутствия: от 0 до 1440 минут");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("Укажи корректный интервал отсутствия в пределах дня");
        }
        if (day == null || day.getShiftType() == null) {
            throw ApiException.badRequest(
                    "Для частичного отсутствия укажи исходную смену или выходной");
        }
        if (plannedMinutes <= 0) {
            if (unpaidBreakOverlapMinutes != null && unpaidBreakOverlapMinutes != 0) throw ApiException.badRequest("На выходном нет перерыва исходной смены");
            return 0;
        }

        var shift = intervals.resolveShift(owner, day);
        Instant absenceStart = time.resolveLocalDateTime(
                date.atTime(start), time.workZone(owner)).toInstant();
        Instant absenceEnd = time.resolveLocalDateTime(
                date.atTime(end), time.workZone(owner)).toInstant();

        long gross = overlap(absenceStart, absenceEnd,
                shift.startInstant(), shift.endInstant());
        long elapsed = Duration.between(shift.startInstant(), shift.endInstant()).toMinutes();
        long totalBreak = shift.breakMinutes();
        if (unpaidBreakOverlapMinutes != null) {
            long minimum = Math.max(0, totalBreak - (elapsed - gross));
            long maximum = Math.min(totalBreak, gross);
            if (unpaidBreakOverlapMinutes < minimum || unpaidBreakOverlapMinutes > maximum) {
                throw ApiException.badRequest("Перерыв в интервале отсутствия должен быть от " + minimum + " до " + maximum + " минут по исходной смене");
            }
        }
        if (gross == 0) return 0;

        var paid = breaks.calendarPaidSegments(day);
        if (paid.precise()) {
            long minutes = paid.intervals().stream()
                    .mapToLong(segment -> overlap(absenceStart, absenceEnd,
                            segment.startInstant(), segment.endInstant()))
                    .sum();
            if (unpaidBreakOverlapMinutes != null && gross - minutes != unpaidBreakOverlapMinutes) {
                throw ApiException.badRequest("Указанный перерыв не совпадает с точными перерывами исходной смены");
            }
            return Math.toIntExact(Math.min(plannedMinutes, minutes));
        }

        if (!absenceStart.isAfter(shift.startInstant())
                && !absenceEnd.isBefore(shift.endInstant())) {
            return plannedMinutes;
        }
        if (unpaidBreakOverlapMinutes != null) {
            return Math.toIntExact(Math.min(plannedMinutes, gross - unpaidBreakOverlapMinutes));
        }
        if (shift.breakMinutes() > 0) {
            throw ApiException.badRequest(
                    "ABSENCE_BREAK_OVERLAP_REQUIRED",
                    "Укажи неоплачиваемый перерыв внутри интервала отсутствия: 0, если он не пересекается с перерывом смены");
        }
        return Math.toIntExact(Math.min(plannedMinutes, gross));
    }

    private long overlap(Instant start, Instant end,
                         Instant workStart, Instant workEnd) {
        Instant from = start.isAfter(workStart) ? start : workStart;
        Instant to = end.isBefore(workEnd) ? end : workEnd;
        return to.isAfter(from) ? Duration.between(from, to).toMinutes() : 0;
    }
}
