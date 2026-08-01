package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalDto;
import ru.daniil.shifts.dto.Dtos.ActualWorkIntervalRequest;
import ru.daniil.shifts.model.ActualWorkInterval;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.repo.ActualWorkIntervalRepository;
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
    private final DayEntryService dates;
    private final LedgerIntegrityService ledger;

    public ActualWorkService(ActualWorkIntervalRepository intervals, DayEntryService dates,
                             LedgerIntegrityService ledger) {
        this.intervals = intervals;
        this.dates = dates;
        this.ledger = ledger;
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
        ledger.assertRangeOpen(user, shape.date(), shape.date().plusDays(shape.overnight() ? 1 : 0));
        validateNoOverlap(user, null, shape);
        ActualWorkInterval interval = new ActualWorkInterval(user);
        apply(interval, shape, req.note());
        return toDto(intervals.saveAndFlush(interval));
    }

    @Transactional
    public ActualWorkIntervalDto update(AppUser user, Long id, ActualWorkIntervalRequest req) {
        ActualWorkInterval interval = intervals.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Фактический интервал не найден"));
        ActualShape shape = parse(req);
        ledger.assertRangeOpen(user, shape.date(), shape.date().plusDays(shape.overnight() ? 1 : 0));
        validateNoOverlap(user, id, shape);
        apply(interval, shape, req.note());
        return toDto(intervals.saveAndFlush(interval));
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ActualWorkInterval interval = intervals.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Фактический интервал не найден"));
        ledger.assertRangeOpen(user, interval.getWorkDate(), interval.getWorkDate().plusDays(
                !interval.getEndTime().isAfter(interval.getStartTime()) ? 1 : 0));
        intervals.delete(interval);
    }

    private void validateNoOverlap(AppUser user, Long excludeId, ActualShape requested) {
        LocalDateTime requestedStart = requested.date().atTime(requested.start());
        LocalDateTime requestedEnd = requestedStart.plusMinutes(requested.minutes());
        for (ActualWorkInterval current : intervals
                .findByOwnerAndWorkDateBetweenOrderByWorkDateAscStartTimeAscIdAsc(
                        user, requested.date().minusDays(1), requested.date().plusDays(1))) {
            if (excludeId != null && excludeId.equals(current.getId())) continue;
            LocalDateTime currentStart = current.getWorkDate().atTime(current.getStartTime());
            LocalDateTime currentEnd = currentStart.plusMinutes(current.getWorkedMinutes());
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
        LocalDateTime endAt = date.atTime(end);
        boolean overnight = !end.isAfter(start);
        if (overnight) endAt = endAt.plusDays(1);
        int minutes = Math.toIntExact(Duration.between(startAt, endAt).toMinutes());
        if (minutes <= 0 || minutes > 2880) throw ApiException.badRequest("Фактический интервал: от 1 минуты до 48 часов");
        return new ActualShape(date, start, end, minutes, overnight);
    }

    private void apply(ActualWorkInterval interval, ActualShape shape, String note) {
        interval.setWorkDate(shape.date());
        interval.setStartTime(shape.start());
        interval.setEndTime(shape.end());
        interval.setWorkedMinutes(shape.minutes());
        interval.setNote(note == null || note.isBlank() ? null : note.trim());
    }

    private ActualWorkIntervalDto toDto(ActualWorkInterval interval) {
        return new ActualWorkIntervalDto(interval.getId(), interval.getWorkDate().toString(),
                interval.getStartTime().toString(), interval.getEndTime().toString(), interval.getWorkedMinutes(),
                interval.getNote(), interval.getCreatedAt() == null ? null : interval.getCreatedAt().toString(),
                interval.getUpdatedAt() == null ? null : interval.getUpdatedAt().toString());
    }

    private record ActualShape(LocalDate date, LocalTime start, LocalTime end, int minutes, boolean overnight) {}
}
