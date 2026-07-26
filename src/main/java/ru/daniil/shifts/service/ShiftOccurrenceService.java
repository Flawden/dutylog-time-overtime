package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.LegacyShiftMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyShiftMigrationRequest;
import ru.daniil.shifts.dto.Dtos.LegacyShiftOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ShiftOccurrenceDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns immutable dated shift snapshots and their current calendar projection.
 * Shift templates remain wall-clock definitions; a concrete assignment becomes
 * an absolute occurrence once and is never reinterpreted after timezone changes.
 */
@Service
public class ShiftOccurrenceService {
    private final DayEntryRepository days;
    private final WorkIntervalService intervals;
    private final UserTimeService userTimeService;

    public ShiftOccurrenceService(DayEntryRepository days,
                                  WorkIntervalService intervals,
                                  UserTimeService userTimeService) {
        this.days = days;
        this.intervals = intervals;
        this.userTimeService = userTimeService;
    }

    public void assign(AppUser user, DayEntry entry, ShiftType shiftType, boolean forceSnapshot) {
        if (entry == null) throw new IllegalArgumentException("Day entry is required");
        ShiftType previous = entry.getShiftType();
        boolean sameType = previous != null && shiftType != null && previous.getId() != null
                && previous.getId().equals(shiftType.getId());
        entry.setShiftType(shiftType);
        if (shiftType == null || shiftType.getStartTime() == null || shiftType.getEndTime() == null) {
            entry.clearShiftOccurrence();
            return;
        }
        // A full day snapshot may be saved only because the note/emoji changed.
        // Never silently reinterpret an existing legacy shift in the current zone.
        // Explicit migration or a canonical timezone change is responsible for freezing it.
        if (!forceSnapshot && sameType) return;
        capture(entry, userTimeService.workZone(user));
    }

    public void clear(DayEntry entry) {
        if (entry == null) return;
        entry.setShiftType(null);
        entry.clearShiftOccurrence();
    }

    public void capture(DayEntry entry, ZoneId sourceZone) {
        if (entry == null || entry.getShiftType() == null) {
            throw new IllegalArgumentException("A dated shift is required");
        }
        ShiftType shift = entry.getShiftType();
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            entry.clearShiftOccurrence();
            return;
        }
        WorkIntervalService.ResolvedWorkInterval interval = intervals.resolveInZone(
                sourceZone,
                entry.getDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getBreakMinutes());
        entry.captureShiftOccurrence(
                interval.startInstant(),
                interval.endInstant(),
                interval.workTimezone(),
                interval.workDate(),
                interval.localStart().toLocalTime(),
                interval.localEnd().toLocalTime(),
                interval.breakMinutes(),
                interval.netMinutes());
    }

    /**
     * Before a canonical timezone change, freeze every legacy shift in the old
     * timezone. This makes the common one-user upgrade path automatic and safe.
     */
    @Transactional
    public int captureLegacyBeforeTimezoneChange(AppUser user, String oldTimezone) {
        ZoneId sourceZone = validatedZone(oldTimezone);
        List<DayEntry> legacy = days.findLegacyShiftOccurrences(user);
        int captured = 0;
        for (DayEntry entry : legacy) {
            ShiftType shift = entry.getShiftType();
            if (shift == null || shift.getStartTime() == null || shift.getEndTime() == null) continue;
            capture(entry, sourceZone);
            captured++;
        }
        if (captured > 0) days.saveAll(legacy);
        return captured;
    }

    @Transactional(readOnly = true)
    public List<ShiftOccurrenceDto> listForDisplayRange(AppUser user, LocalDate from, LocalDate to) {
        ZoneId displayZone = userTimeService.workZone(user);
        Instant start = from.atStartOfDay(displayZone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(displayZone).toInstant();

        Map<Long, DayEntry> entries = new LinkedHashMap<>();
        for (DayEntry entry : days.findByOwnerAndShiftStartInstantLessThanAndShiftEndInstantGreaterThanOrderByShiftStartInstantAsc(
                user, end, start)) {
            entries.put(entry.getId(), entry);
        }
        for (DayEntry entry : days.findByOwnerAndDateBetweenOrderByDateAsc(user, from.minusDays(2), to.plusDays(2))) {
            if (entry.getShiftType() != null && !entry.hasShiftOccurrenceSnapshot()) {
                entries.putIfAbsent(entry.getId(), entry);
            }
        }

        return entries.values().stream()
                .filter(entry -> entry.getShiftType() != null
                        && entry.getShiftType().getStartTime() != null
                        && entry.getShiftType().getEndTime() != null)
                .map(entry -> toDto(user, entry))
                .filter(dto -> intersectsDisplayRange(dto, from, to))
                .sorted(Comparator.comparing(ShiftOccurrenceDto::displayStart)
                        .thenComparing(dto -> dto.dayEntryId() == null ? Long.MAX_VALUE : dto.dayEntryId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LegacyShiftMigrationPreviewDto preview(AppUser user, String sourceTimezone) {
        ZoneId sourceZone = validatedZone(sourceTimezone);
        List<LegacyShiftOccurrenceDto> occurrences = new ArrayList<>();
        for (DayEntry entry : days.findLegacyShiftOccurrences(user)) {
            ShiftType shift = entry.getShiftType();
            if (shift == null || shift.getStartTime() == null || shift.getEndTime() == null) continue;
            WorkIntervalService.ResolvedWorkInterval resolved = intervals.resolveInZone(
                    sourceZone, entry.getDate(), shift.getStartTime(), shift.getEndTime(), shift.getBreakMinutes());
            var projected = intervals.project(user, resolved);
            occurrences.add(new LegacyShiftOccurrenceDto(
                    entry.getId(),
                    entry.getDate().toString(),
                    shift.getId(),
                    shift.getName(),
                    resolved.localStart().toString(),
                    resolved.localEnd().toString(),
                    sourceZone.getId(),
                    projected.displayStart().toLocalDateTime().toString(),
                    projected.displayEnd().toLocalDateTime().toString()
            ));
        }
        return new LegacyShiftMigrationPreviewDto(sourceZone.getId(), occurrences.size(), occurrences);
    }

    @Transactional
    public LegacyShiftMigrationPreviewDto migrate(AppUser user, LegacyShiftMigrationRequest request) {
        if (request == null || request.dayEntryIds() == null || request.dayEntryIds().isEmpty()) {
            throw ApiException.badRequest("Выберите хотя бы одну смену");
        }
        ZoneId sourceZone = validatedZone(request.sourceTimezone());
        Set<Long> requestedIds = request.dayEntryIds().stream().collect(Collectors.toSet());
        if (requestedIds.size() != request.dayEntryIds().size()) {
            throw ApiException.badRequest("Список смен содержит дубликаты");
        }
        List<DayEntry> selected = days.findAllById(requestedIds);
        if (selected.size() != requestedIds.size()) throw ApiException.notFound("Смена не найдена");
        for (DayEntry entry : selected) {
            if (!entry.getOwner().getId().equals(user.getId())) throw ApiException.notFound("Смена не найдена");
            if (entry.getShiftType() == null) throw ApiException.badRequest("У выбранного дня нет смены");
            if (!entry.hasShiftOccurrenceSnapshot()) capture(entry, sourceZone);
        }
        days.saveAll(selected);
        return preview(user, sourceZone.getId());
    }

    private ShiftOccurrenceDto toDto(AppUser user, DayEntry entry) {
        WorkIntervalService.ShiftProjection projection = intervals.projectShift(user, entry);
        ShiftType shift = entry.getShiftType();
        return new ShiftOccurrenceDto(
                entry.getId(),
                entry.getDate().toString(),
                shift != null ? shift.getId() : null,
                projection.startInstant().toString(),
                projection.endInstant().toString(),
                projection.workStart().toLocalDateTime().toString(),
                projection.workEnd().toLocalDateTime().toString(),
                projection.displayStart().toLocalDateTime().toString(),
                projection.displayEnd().toLocalDateTime().toString(),
                projection.workTimezone(),
                projection.displayTimezone(),
                projection.breakMinutes(),
                projection.elapsedMinutes(),
                projection.netMinutes(),
                projection.legacyLocal()
        );
    }

    private boolean intersectsDisplayRange(ShiftOccurrenceDto dto, LocalDate from, LocalDate to) {
        LocalDate startDate = LocalDate.parse(dto.displayStart().substring(0, 10));
        LocalDate endDate = LocalDate.parse(dto.displayEnd().substring(0, 10));
        // An occurrence ending exactly at midnight belongs to the previous day only.
        if (dto.displayEnd().endsWith("T00:00") && endDate.isAfter(startDate)) endDate = endDate.minusDays(1);
        return !endDate.isBefore(from) && !startDate.isAfter(to);
    }

    private ZoneId validatedZone(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank() || value.length() > 80) {
            throw ApiException.badRequest("Часовой пояс должен быть IANA-идентификатором");
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException e) {
            throw ApiException.badRequest("Неизвестный часовой пояс: " + value);
        }
    }
}
