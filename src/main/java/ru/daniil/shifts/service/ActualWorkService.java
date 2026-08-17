package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalDto;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Explicit factual work intervals, with plan-as-fact remaining the default. */
@Service
public class ActualWorkService {
    private final ActualWorkIntervalRepository intervals;
    private final DayEntryRepository dayEntries;
    private final DayEntryService dates;
    private final LedgerIntegrityService ledger;
    private final WorkdayDerivedCompensationService derivedCompensation;

    public ActualWorkService(ActualWorkIntervalRepository intervals,
                             DayEntryRepository dayEntries,
                             DayEntryService dates,
                             LedgerIntegrityService ledger,
                             WorkdayDerivedCompensationService derivedCompensation) {
        this.intervals = intervals;
        this.dayEntries = dayEntries;
        this.dates = dates;
        this.ledger = ledger;
        this.derivedCompensation = derivedCompensation;
    }

    @Transactional(readOnly = true)
    public List<ActualWorkIntervalDto> list(AppUser user, LocalDate from, LocalDate to) {
        dates.validateRange(from, to);
        return intervals.findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(user, from, to)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ActualWorkIntervalDto create(AppUser user, ActualWorkIntervalRequest req) {
        ActualShape shape = parse(req);
        int breakMinutes = resolveBreakMinutes(user, shape.date(), req.breakMinutes(), null);
        validateBreak(shape, breakMinutes);
        ledger.assertRangeOpen(user, shape.date(), shape.date().plusDays(shape.overnight() ? 1 : 0));
        validateNoOverlap(user, null, shape);
        ActualWorkInterval interval = new ActualWorkInterval(user);
        apply(interval, shape, breakMinutes, req.note());
        ActualWorkInterval saved = intervals.saveAndFlush(interval);
        derivedCompensation.reconcile(user, shape.date());
        return toDto(saved);
    }

    @Transactional
    public ActualWorkIntervalDto update(AppUser user, Long id, ActualWorkIntervalRequest req) {
        ActualWorkInterval interval = intervals.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Фактический интервал не найден"));
        LocalDate oldDate = interval.getWorkDate();
        boolean oldOvernight = !interval.getEndTime().isAfter(interval.getStartTime());
        ledger.assertRangeOpen(user, oldDate, oldDate.plusDays(oldOvernight ? 1 : 0));

        ActualShape shape = parse(req);
        int breakMinutes = resolveBreakMinutes(user, shape.date(), req.breakMinutes(), interval);
        validateBreak(shape, breakMinutes);
        ledger.assertRangeOpen(user, shape.date(), shape.date().plusDays(shape.overnight() ? 1 : 0));
        validateNoOverlap(user, id, shape);
        apply(interval, shape, breakMinutes, req.note());
        ActualWorkInterval saved = intervals.saveAndFlush(interval);
        if (!oldDate.equals(shape.date())) derivedCompensation.reconcile(user, oldDate);
        derivedCompensation.reconcile(user, shape.date());
        return toDto(saved);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ActualWorkInterval interval = intervals.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Фактический интервал не найден"));
        LocalDate date = interval.getWorkDate();
        ledger.assertRangeOpen(user, date, date.plusDays(
                !interval.getEndTime().isAfter(interval.getStartTime()) ? 1 : 0));
        intervals.delete(interval);
        intervals.flush();
        derivedCompensation.reconcile(user, date);
    }

    private void validateNoOverlap(AppUser user, Long excludeId, ActualShape requested) {
        LocalDateTime requestedStart = requested.date().atTime(requested.start());
        LocalDateTime requestedEnd = wallEnd(requested.date(), requested.start(), requested.end());
        for (ActualWorkInterval current : intervals
                .findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                        user, requested.date().minusDays(1), requested.date().plusDays(1))) {
            if (excludeId != null && excludeId.equals(current.getId())) continue;
            LocalDateTime currentStart = current.getWorkDate().atTime(current.getStartTime());
            LocalDateTime currentEnd = wallEnd(current.getWorkDate(), current.getStartTime(), current.getEndTime());
            if (requestedStart.isBefore(currentEnd) && requestedEnd.isAfter(currentStart)) {
                throw ApiException.conflict("ACTUAL_WORK_OVERLAP", "Фактические интервалы не должны пересекаться");
            }
        }
    }

    private ActualShape parse(ActualWorkIntervalRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        LocalDate date = dates.parseDate(req.workDate(), "Дата должна быть в формате yyyy-MM-dd");
        LocalTime start;
        LocalTime end;
        try { start = LocalTime.parse(req.startTime()); end = LocalTime.parse(req.endTime()); }
        catch (Exception ex) { throw ApiException.badRequest("Время должно быть в формате HH:mm"); }
        LocalDateTime startAt = date.atTime(start);
        LocalDateTime endAt = wallEnd(date, start, end);
        boolean overnight = !end.isAfter(start);
        int elapsedMinutes = Math.toIntExact(Duration.between(startAt, endAt).toMinutes());
        if (elapsedMinutes <= 0 || elapsedMinutes > 2880) {
            throw ApiException.badRequest("Фактический интервал: от 1 минуты до 48 часов");
        }
        return new ActualShape(date, start, end, elapsedMinutes, overnight);
    }

    private int resolveBreakMinutes(AppUser user, LocalDate date, Integer requested, ActualWorkInterval existing) {
        if (requested != null) return requested;
        if (existing != null) return existing.getBreakMinutes();
        if (!intervals.findByOwnerAndWorkDateOrderByStartTimeAscIdAsc(user, date).isEmpty()) return 0;
        DayEntry day = dayEntries.findByOwnerAndDate(user, date).orElse(null);
        if (day == null) return 0;
        ShiftType shift = day.getShiftType();
        if (shift == null) return 0;
        return day.hasShiftOccurrenceSnapshot() ? day.getShiftBreakMinutes() : Math.max(0, shift.getBreakMinutes());
    }

    private void validateBreak(ActualShape shape, int breakMinutes) {
        if (breakMinutes < 0 || breakMinutes > 1440) {
            throw ApiException.badRequest("Перерыв должен быть от 0 до 1440 минут");
        }
        if (breakMinutes >= shape.elapsedMinutes()) {
            throw ApiException.badRequest("Перерыв должен быть короче фактического интервала работы");
        }
    }

    private LocalDateTime wallEnd(LocalDate date, LocalTime start, LocalTime end) {
        LocalDateTime endAt = date.atTime(end);
        if (!end.isAfter(start)) endAt = endAt.plusDays(1);
        return endAt;
    }

    private void apply(ActualWorkInterval interval, ActualShape shape, int breakMinutes, String note) {
        interval.setWorkDate(shape.date());
        interval.setStartTime(shape.start());
        interval.setEndTime(shape.end());
        interval.setBreakMinutes(breakMinutes);
        interval.setWorkedMinutes(shape.elapsedMinutes() - breakMinutes);
        interval.setNote(note == null || note.isBlank() ? null : note.trim());
    }

    private ActualWorkIntervalDto toDto(ActualWorkInterval interval) {
        return new ActualWorkIntervalDto(interval.getId(), interval.getWorkDate().toString(),
                interval.getStartTime().toString(), interval.getEndTime().toString(), interval.getWorkedMinutes(),
                interval.getBreakMinutes(), interval.getNote(),
                interval.getCreatedAt() == null ? null : interval.getCreatedAt().toString(),
                interval.getUpdatedAt() == null ? null : interval.getUpdatedAt().toString());
    }

    private record ActualShape(LocalDate date, LocalTime start, LocalTime end,
                               int elapsedMinutes, boolean overnight) {}
}
