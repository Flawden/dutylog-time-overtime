package ru.daniil.shifts.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.DayNoteDto;
import ru.daniil.shifts.dto.Dtos.DayFillRequest;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.dto.Dtos.MobileDayChangeRequest;
import ru.daniil.shifts.dto.Dtos.ShiftIntervalDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DayEntryService {
    private final DayEntryRepository days;
    private final ShiftTypeService shiftTypeService;
    private final WorkIntervalService workIntervalService;
    private final ShiftOccurrenceService shiftOccurrenceService;
    private final DayNoteService dayNoteService;
    private final AccountingPeriodLockService periodLocks;
    private final EntityManager entityManager;

    public DayEntryService(DayEntryRepository days,
                           ShiftTypeService shiftTypeService,
                           WorkIntervalService workIntervalService,
                           ShiftOccurrenceService shiftOccurrenceService,
                           DayNoteService dayNoteService,
                           AccountingPeriodLockService periodLocks,
                           EntityManager entityManager) {
        this.days = days;
        this.shiftTypeService = shiftTypeService;
        this.workIntervalService = workIntervalService;
        this.shiftOccurrenceService = shiftOccurrenceService;
        this.dayNoteService = dayNoteService;
        this.periodLocks = periodLocks;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<DayDto> listMonth(AppUser user, int year, int month) {
        if (year < 1900 || year > 2200) {
            throw ApiException.badRequest("Год должен быть в диапазоне 1900–2200");
        }
        if (month < 1 || month > 12) {
            throw ApiException.badRequest("Месяц должен быть от 1 до 12");
        }

        YearMonth ym = YearMonth.of(year, month);
        return listRange(user, ym.atDay(1), ym.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<DayDto> listRange(AppUser user, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Map<LocalDate, List<DayNoteDto>> notesByDate = new HashMap<>();
        for (DayNoteDto note : dayNoteService.listRange(user, from, to)) {
            notesByDate.computeIfAbsent(LocalDate.parse(note.date()), ignored -> new ArrayList<>()).add(note);
        }
        return days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)
                .stream().map(entry -> toDto(user, entry, notesByDate.getOrDefault(entry.getDate(), List.of()))).toList();
    }

    @Transactional
    public DayDto upsert(AppUser user, String date, DayUpsertRequest req) {
        return upsert(user, date, req, true, true);
    }

    /**
     * Web day snapshot with module isolation.
     *
     * Disabled optional modules are read-only: a shift or marker update may still be
     * saved, while hidden note/overtime values remain untouched in the database.
     */
    @Transactional
    public DayDto upsert(AppUser user,
                         String date,
                         DayUpsertRequest req,
                         boolean notesMutable,
                         boolean overtimeMutable) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }

        LocalDate d = parseDate(date, "Дата должна быть в формате yyyy-MM-dd");

        ShiftType st = null;
        if (req.shiftTypeId() != null) {
            st = shiftTypeService.requireOwnedShiftType(user, req.shiftTypeId());
        }

        DayEntry entry = days.findByOwnerAndDate(user, d)
                .orElseGet(() -> new DayEntry(user, d));
        Long currentShiftId = entry.getShiftType() == null ? null : entry.getShiftType().getId();
        Long requestedShiftId = st == null ? null : st.getId();
        if (!java.util.Objects.equals(currentShiftId, requestedShiftId)) {
            periodLocks.assertOpen(user, d);
        }
        shiftOccurrenceService.assign(user, entry, st, false);
        if (notesMutable) {
            entry.setNote(normalizeNote(req.note()));
        }
        if (req.dayEmoji() != null) {
            entry.setDayEmoji(normalizeDayEmoji(req.dayEmoji()));
        }
        if (overtimeMutable) {
            entry.setOvertimeHours(req.overtimeHours() != null ? req.overtimeHours() : 0.0);
            entry.setTimeOffHours(req.timeOffHours() != null ? req.timeOffHours() : 0.0);
        }

        if (entry.isEmpty()) {
            if (entry.getId() != null) {
                days.delete(entry);
                days.flush();
            }
            if (notesMutable) {
                dayNoteService.syncPrimaryFromLegacy(user, d, null);
                DayEntry promoted = days.findByOwnerAndDate(user, d).orElse(null);
                if (promoted != null) return toDto(user, promoted);
            }
            return null;
        }
        DayEntry saved = days.saveAndFlush(entry);
        if (notesMutable) dayNoteService.syncPrimaryFromLegacy(user, d, saved.getNote());
        return toDto(user, days.findByOwnerAndDate(user, d).orElse(saved));
    }

    @Transactional
    public List<DayDto> fillSchedule(AppUser user, DayFillRequest req) {
        if (req == null) {
            throw ApiException.badRequest("Некорректный JSON в запросе");
        }

        LocalDate start = parseDate(req.startDate(), "Дата начала должна быть в формате yyyy-MM-dd");
        int dayCount = req.days() != null ? req.days() : 31;
        boolean overwrite = req.overwriteExistingShift() == null || req.overwriteExistingShift();

        List<ShiftType> pattern = new ArrayList<>();
        for (Long shiftTypeId : req.shiftTypeIds()) {
            if (shiftTypeId == null) {
                throw ApiException.badRequest("Шаблон графика содержит пустую смену");
            }
            pattern.add(shiftTypeService.requireOwnedShiftType(user, shiftTypeId));
        }

        LocalDate end = start.plusDays(dayCount - 1L);
        Map<LocalDate, DayEntry> existingByDate = new HashMap<>();
        for (DayEntry entry : days.findByOwnerAndDateBetweenOrderByDateAsc(user, start, end)) {
            existingByDate.put(entry.getDate(), entry);
        }

        Map<LocalDate, Long> expectedShiftByDate = new LinkedHashMap<>();
        for (int i = 0; i < dayCount; i++) {
            LocalDate d = start.plusDays(i);
            ShiftType plannedShift = pattern.get(i % pattern.size());
            DayEntry entry = existingByDate.getOrDefault(d, new DayEntry(user, d));

            if (!overwrite && entry.getShiftType() != null) {
                expectedShiftByDate.put(d, entry.getShiftType().getId());
                continue;
            }

            Long currentShiftId = entry.getShiftType() == null ? null : entry.getShiftType().getId();
            if (!java.util.Objects.equals(currentShiftId, plannedShift.getId())) {
                periodLocks.assertOpen(user, d);
            }
            shiftOccurrenceService.assign(user, entry, plannedShift, false);
            expectedShiftByDate.put(d, plannedShift.getId());

            // Save every date explicitly. With IDENTITY ids this immediately inserts new
            // rows and avoids depending on a deferred saveAll batch for a user-visible
            // calendar operation that must survive F5 and another browser.
            days.save(entry);
        }
        days.flush();

        // Re-read from the database, not from the current persistence context. The
        // endpoint must never report success for a schedule that was only visible in
        // managed in-memory entities.
        entityManager.clear();
        List<DayEntry> persisted = days.findByOwnerAndDateBetweenOrderByDateAsc(user, start, end);
        Map<LocalDate, DayEntry> persistedByDate = new HashMap<>();
        for (DayEntry entry : persisted) persistedByDate.put(entry.getDate(), entry);

        for (Map.Entry<LocalDate, Long> expected : expectedShiftByDate.entrySet()) {
            DayEntry actual = persistedByDate.get(expected.getKey());
            Long actualShiftId = actual != null && actual.getShiftType() != null
                    ? actual.getShiftType().getId()
                    : null;
            if (!expected.getValue().equals(actualShiftId)) {
                throw new IllegalStateException("График не сохранился для даты " + expected.getKey());
            }
        }

        return expectedShiftByDate.keySet().stream()
                .map(persistedByDate::get)
                .map(entry -> toDto(user, entry))
                .toList();
    }



    /**
     * Legacy mobile patch: empty rows are removed to keep the old API behaviour.
     */
    @Transactional
    public DayDto patchMobileDay(AppUser user, MobileDayChangeRequest req) {
        return patchMobileDayInternal(user, req, false);
    }

    /**
     * Android API v1 patch: an empty row is retained as a lightweight tombstone
     * so optimistic versions remain monotonic across clear/delete operations.
     */
    @Transactional
    public DayDto patchMobileDayVersioned(AppUser user, MobileDayChangeRequest req) {
        return patchMobileDayInternal(user, req, true);
    }

    private DayDto patchMobileDayInternal(AppUser user,
                                          MobileDayChangeRequest req,
                                          boolean preserveEmptyVersionRow) {
        if (req == null) {
            throw ApiException.badRequest("Пустое изменение дня в sync-запросе");
        }
        LocalDate d = parseDate(req.date(), "Дата дня должна быть в формате yyyy-MM-dd");
        DayEntry entry = days.findByOwnerAndDate(user, d)
                .orElseGet(() -> new DayEntry(user, d));

        if (Boolean.TRUE.equals(req.clearShiftType())) {
            if (entry.getShiftType() != null) periodLocks.assertOpen(user, d);
            shiftOccurrenceService.clear(entry);
        } else if (req.shiftTypeId() != null) {
            ShiftType requestedShift = shiftTypeService.requireOwnedShiftType(user, req.shiftTypeId());
            Long currentShiftId = entry.getShiftType() == null ? null : entry.getShiftType().getId();
            if (!java.util.Objects.equals(currentShiftId, requestedShift.getId())) {
                periodLocks.assertOpen(user, d);
            }
            shiftOccurrenceService.assign(user, entry, requestedShift, false);
        }

        if (Boolean.TRUE.equals(req.clearNote())) {
            entry.setNote(null);
        } else if (req.note() != null) {
            entry.setNote(normalizeNote(req.note()));
        }

        if (Boolean.TRUE.equals(req.clearDayEmoji())) {
            entry.setDayEmoji(null);
        } else if (req.dayEmoji() != null) {
            entry.setDayEmoji(normalizeDayEmoji(req.dayEmoji()));
        }

        if (req.overtimeHours() != null) entry.setOvertimeHours(req.overtimeHours());
        if (req.timeOffHours() != null) entry.setTimeOffHours(req.timeOffHours());

        if (entry.isEmpty() && !preserveEmptyVersionRow) {
            if (entry.getId() != null) {
                days.delete(entry);
                days.flush();
            }
            if (Boolean.TRUE.equals(req.clearNote()) || req.note() != null) {
                dayNoteService.syncPrimaryFromLegacy(user, d, null);
                DayEntry promoted = days.findByOwnerAndDate(user, d).orElse(null);
                if (promoted != null) return toDto(user, promoted);
            }
            return null;
        }
        DayEntry saved = days.saveAndFlush(entry);
        if (Boolean.TRUE.equals(req.clearNote()) || req.note() != null) {
            dayNoteService.syncPrimaryFromLegacy(user, d, saved.getNote(), preserveEmptyVersionRow);
        }
        return toDto(user, days.findByOwnerAndDate(user, d).orElse(saved));
    }

    public DayDto toDto(AppUser user, DayEntry entry) {
        ShiftIntervalDto shiftInterval = null;
        ShiftType shift = entry == null ? null : entry.getShiftType();
        if (entry != null && shift != null && shift.getStartTime() != null && shift.getEndTime() != null) {
            WorkIntervalService.ShiftProjection projection = workIntervalService.projectShift(user, entry);
            shiftInterval = new ShiftIntervalDto(
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
                    projection.crossesWorkMidnight(),
                    projection.crossesDisplayMidnight(),
                    projection.sameTimezone(),
                    projection.legacyLocal()
            );
        }
        return DayDto.from(entry, shiftInterval, dayNoteService.listDate(user, entry.getDate()));
    }

    private DayDto toDto(AppUser user, DayEntry entry, List<DayNoteDto> notes) {
        ShiftIntervalDto shiftInterval = null;
        ShiftType shift = entry == null ? null : entry.getShiftType();
        if (entry != null && shift != null && shift.getStartTime() != null && shift.getEndTime() != null) {
            WorkIntervalService.ShiftProjection projection = workIntervalService.projectShift(user, entry);
            shiftInterval = new ShiftIntervalDto(
                    projection.startInstant().toString(), projection.endInstant().toString(),
                    projection.workStart().toLocalDateTime().toString(), projection.workEnd().toLocalDateTime().toString(),
                    projection.displayStart().toLocalDateTime().toString(), projection.displayEnd().toLocalDateTime().toString(),
                    projection.workTimezone(), projection.displayTimezone(), projection.breakMinutes(),
                    projection.elapsedMinutes(), projection.netMinutes(), projection.crossesWorkMidnight(),
                    projection.crossesDisplayMidnight(), projection.sameTimezone(), projection.legacyLocal());
        }
        return DayDto.from(entry, shiftInterval, notes);
    }

    public LocalDate parseDate(String date, String message) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest(message);
        }
    }

    public void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("Нужен диапазон дат from/to в формате yyyy-MM-dd");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("Дата to не может быть раньше from");
        }
        if (from.plusDays(366).isBefore(to)) {
            throw ApiException.badRequest("Диапазон не должен быть больше 366 дней");
        }
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note;
    }

    private String normalizeDayEmoji(String emoji) {
        if (emoji == null || emoji.isBlank()) return null;
        String normalized = emoji.trim();
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }
}
