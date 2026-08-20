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
    private final ActualWorkIdentityService identity;

    public ActualWorkService(ActualWorkIntervalRepository intervals,
                             DayEntryRepository dayEntries,
                             DayEntryService dates,
                             LedgerIntegrityService ledger,
                             WorkdayDerivedCompensationService derivedCompensation,
                             ActualWorkIdentityService identity) {
        this.intervals = intervals;
        this.dayEntries = dayEntries;
        this.dates = dates;
        this.ledger = ledger;
        this.derivedCompensation = derivedCompensation;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public List<ActualWorkIntervalDto> list(AppUser user, LocalDate from, LocalDate to) {
        dates.validateRange(from, to);
        return intervals.findOverlappingRange(user, from, to).stream().map(this::toDto).toList();
    }

    @Transactional
    public ActualWorkIntervalDto create(AppUser user, ActualWorkIntervalRequest req) {
        ActualShape shape = parse(req);
        int breakMinutes = resolveBreakMinutes(user, shape.startDate(), req.breakMinutes(), null);
        ActualWorkIdentityService.Identity resolvedIdentity =
                identity.resolve(user, shape.startDate(), shape.endDate(), shape.start(), shape.end());
        validateBreak(resolvedIdentity.elapsedMinutes(), breakMinutes);
        ledger.assertRangeOpen(user, shape.startDate(), shape.endDate());
        validateNoOverlap(user, null, shape);
        ActualWorkInterval interval = new ActualWorkInterval(user);
        apply(interval, shape, resolvedIdentity, breakMinutes, req.note());
        ActualWorkInterval saved = intervals.saveAndFlush(interval);
        derivedCompensation.reconcileRange(user, shape.startDate(), shape.endDate());
        return toDto(saved);
    }

    @Transactional
    public ActualWorkIntervalDto update(AppUser user, Long id, ActualWorkIntervalRequest req) {
        ActualWorkInterval interval = intervals.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Фактический интервал не найден"));
        LocalDate oldStartDate = interval.getWorkDate();
        LocalDate oldEndDate = interval.getEndDate();
        ledger.assertRangeOpen(user, oldStartDate, oldEndDate);

        ActualShape shape = parse(req);
        int breakMinutes = resolveBreakMinutes(user, shape.startDate(), req.breakMinutes(), interval);
        ActualWorkIdentityService.Identity resolvedIdentity =
                identity.resolve(user, shape.startDate(), shape.endDate(), shape.start(), shape.end());
        validateBreak(resolvedIdentity.elapsedMinutes(), breakMinutes);
        ledger.assertRangeOpen(user, shape.startDate(), shape.endDate());
        validateNoOverlap(user, id, shape);
        apply(interval, shape, resolvedIdentity, breakMinutes, req.note());
        ActualWorkInterval saved = intervals.saveAndFlush(interval);
        LocalDate reconcileFrom = oldStartDate.isBefore(shape.startDate()) ? oldStartDate : shape.startDate();
        LocalDate reconcileTo = oldEndDate.isAfter(shape.endDate()) ? oldEndDate : shape.endDate();
        derivedCompensation.reconcileRange(user, reconcileFrom, reconcileTo);
        return toDto(saved);
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        ActualWorkInterval interval = intervals.findByOwnerAndId(user, id)
                .orElseThrow(() -> ApiException.notFound("Фактический интервал не найден"));
        LocalDate startDate = interval.getWorkDate();
        LocalDate endDate = interval.getEndDate();
        ledger.assertRangeOpen(user, startDate, endDate);
        intervals.delete(interval);
        intervals.flush();
        derivedCompensation.reconcileRange(user, startDate, endDate);
    }

    private void validateNoOverlap(AppUser user, Long excludeId, ActualShape requested) {
        LocalDateTime requestedStart = requested.startDate().atTime(requested.start());
        LocalDateTime requestedEnd = requested.endDate().atTime(requested.end());
        for (ActualWorkInterval current : intervals.findOverlappingRange(
                user, requested.startDate().minusDays(1), requested.endDate().plusDays(1))) {
            if (excludeId != null && excludeId.equals(current.getId())) continue;
            LocalDateTime currentStart = current.getWorkDate().atTime(current.getStartTime());
            LocalDateTime currentEnd = current.getEndDate().atTime(current.getEndTime());
            if (requestedStart.isBefore(currentEnd) && requestedEnd.isAfter(currentStart)) {
                throw ApiException.conflict("ACTUAL_WORK_OVERLAP", "Фактические интервалы не должны пересекаться");
            }
        }
    }

    private ActualShape parse(ActualWorkIntervalRequest req) {
        if (req == null) throw ApiException.badRequest("Некорректный JSON в запросе");
        LocalDate startDate = dates.parseDate(req.workDate(), "Дата должна быть в формате yyyy-MM-dd");
        LocalTime start;
        LocalTime end;
        try { start = LocalTime.parse(req.startTime()); end = LocalTime.parse(req.endTime()); }
        catch (Exception ex) { throw ApiException.badRequest("Время должно быть в формате HH:mm"); }

        LocalDate endDate;
        if (req.endDate() == null || req.endDate().isBlank()) {
            endDate = end.isAfter(start) ? startDate : startDate.plusDays(1);
        } else {
            endDate = dates.parseDate(req.endDate(), "Дата окончания должна быть в формате yyyy-MM-dd");
        }
        if (endDate.isBefore(startDate)) {
            throw ApiException.badRequest("Дата окончания не может быть раньше даты начала");
        }

        LocalDateTime startAt = startDate.atTime(start);
        LocalDateTime endAt = endDate.atTime(end);
        if (!endAt.isAfter(startAt)) {
            throw ApiException.badRequest("Конец должен быть позже начала. Для ночной работы выбери следующий день окончания.");
        }
        int elapsedMinutes = Math.toIntExact(Duration.between(startAt, endAt).toMinutes());
        if (elapsedMinutes <= 0 || elapsedMinutes > 2880) {
            throw ApiException.badRequest("Фактический интервал: от 1 минуты до 48 часов");
        }
        return new ActualShape(startDate, endDate, start, end, elapsedMinutes);
    }

    private int resolveBreakMinutes(AppUser user, LocalDate date, Integer requested, ActualWorkInterval existing) {
        if (requested != null) return requested;
        if (existing != null) return existing.getBreakMinutes();
        if (!intervals.findOverlappingRange(user, date, date).isEmpty()) return 0;
        DayEntry day = dayEntries.findByOwnerAndDate(user, date).orElse(null);
        if (day == null) return 0;
        ShiftType shift = day.getShiftType();
        if (shift == null) return 0;
        return day.hasShiftOccurrenceSnapshot() ? day.getShiftBreakMinutes() : Math.max(0, shift.getBreakMinutes());
    }

    private void validateBreak(int elapsedMinutes, int breakMinutes) {
        if (breakMinutes < 0 || breakMinutes > 1440) {
            throw ApiException.badRequest("Перерыв должен быть от 0 до 1440 минут");
        }
        if (breakMinutes >= elapsedMinutes) {
            throw ApiException.badRequest("Перерыв должен быть короче фактического интервала работы");
        }
    }

    private void apply(ActualWorkInterval interval,
                       ActualShape shape,
                       ActualWorkIdentityService.Identity resolvedIdentity,
                       int breakMinutes,
                       String note) {
        interval.setWorkDate(shape.startDate());
        interval.setEndDate(shape.endDate());
        interval.setStartTime(shape.start());
        interval.setEndTime(shape.end());
        interval.setBreakMinutes(breakMinutes);

        // Real elapsed time comes from absolute identity, not wall-clock subtraction.
        interval.setWorkedMinutes(resolvedIdentity.elapsedMinutes() - breakMinutes);

        interval.setSourceTimezone(resolvedIdentity.sourceTimezone());
        interval.setStartInstant(resolvedIdentity.startInstant());
        interval.setEndInstant(resolvedIdentity.endInstant());
        interval.setIdentityReconstructed(false);

        interval.setNote(note == null || note.isBlank() ? null : note.trim());
    }

    private ActualWorkIntervalDto toDto(ActualWorkInterval interval) {
        return new ActualWorkIntervalDto(
                interval.getId(),
                interval.getWorkDate().toString(),
                interval.getEndDate().toString(),
                interval.getStartTime().toString(),
                interval.getEndTime().toString(),
                interval.getWorkedMinutes(),
                interval.getBreakMinutes(),
                interval.getNote(),
                interval.getSourceTimezone(),
                interval.getStartInstant() == null ? null : interval.getStartInstant().toString(),
                interval.getEndInstant() == null ? null : interval.getEndInstant().toString(),
                interval.isIdentityReconstructed(),
                interval.getCreatedAt() == null ? null : interval.getCreatedAt().toString(),
                interval.getUpdatedAt() == null ? null : interval.getUpdatedAt().toString()
        );
    }

    private record ActualShape(LocalDate startDate, LocalDate endDate, LocalTime start, LocalTime end,
                               int elapsedMinutes) {}
}
