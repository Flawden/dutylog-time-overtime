package ru.daniil.shifts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.DayFillRequest;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.service.exception.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DayEntryService {
    private final DayEntryRepository days;
    private final ShiftTypeService shiftTypeService;

    public DayEntryService(DayEntryRepository days, ShiftTypeService shiftTypeService) {
        this.days = days;
        this.shiftTypeService = shiftTypeService;
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
        return days.findByOwnerAndDateBetweenOrderByDateAsc(user, from, to)
                .stream().map(DayDto::from).toList();
    }

    @Transactional
    public DayDto upsert(AppUser user, String date, DayUpsertRequest req) {
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
        entry.setShiftType(st);
        entry.setNote(normalizeNote(req.note()));
        entry.setOvertimeHours(req.overtimeHours() != null ? req.overtimeHours() : 0.0);
        entry.setTimeOffHours(req.timeOffHours() != null ? req.timeOffHours() : 0.0);

        if (entry.isEmpty()) {
            if (entry.getId() != null) {
                days.delete(entry);
            }
            return null;
        }
        return DayDto.from(days.save(entry));
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

        List<DayDto> changed = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            LocalDate d = start.plusDays(i);
            ShiftType plannedShift = pattern.get(i % pattern.size());
            DayEntry entry = days.findByOwnerAndDate(user, d)
                    .orElseGet(() -> new DayEntry(user, d));

            if (!overwrite && entry.getShiftType() != null) {
                continue;
            }

            entry.setShiftType(plannedShift);
            changed.add(DayDto.from(days.save(entry)));
        }

        return changed;
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
}
