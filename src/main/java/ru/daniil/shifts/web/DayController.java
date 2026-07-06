package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.DayFillRequest;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;
import ru.daniil.shifts.repo.UserRepository;

import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/days")
public class DayController {

    private final DayEntryRepository days;
    private final ShiftTypeRepository shiftTypes;
    private final UserRepository users;

    public DayController(DayEntryRepository days, ShiftTypeRepository shiftTypes, UserRepository users) {
        this.days = days;
        this.shiftTypes = shiftTypes;
        this.users = users;
    }

    private AppUser me(Principal principal) {
        return users.findByUsername(principal.getName()).orElseThrow();
    }

    /** Записи месяца текущего пользователя: GET /api/days?year=2026&month=7. */
    @GetMapping
    public ResponseEntity<?> month(@RequestParam int year, @RequestParam int month,
                                   Principal principal) {
        if (year < 1900 || year > 2200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Год должен быть в диапазоне 1900–2200"));
        }
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest().body(Map.of("error", "Месяц должен быть от 1 до 12"));
        }

        YearMonth ym = YearMonth.of(year, month);
        List<DayDto> result = days
                .findByOwnerAndDateBetween(me(principal), ym.atDay(1), ym.atEndOfMonth())
                .stream().map(DayDto::from).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Upsert записи дня текущего пользователя: PUT /api/days/2026-07-02
     * Тело: { "shiftTypeId": 3, "note": "# Markdown" } — оба поля опциональны.
     * Чужой тип смены назначить нельзя. Пустая запись удаляется (204).
     */
    @PutMapping("/{date}")
    @Transactional
    public ResponseEntity<?> upsert(@PathVariable String date,
                                    @Valid @RequestBody(required = false) DayUpsertRequest req,
                                    Principal principal) {
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный JSON в запросе"));
        }

        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата должна быть в формате yyyy-MM-dd"));
        }

        AppUser current = me(principal);

        ShiftType st = null;
        if (req.shiftTypeId() != null) {
            st = shiftTypes.findById(req.shiftTypeId())
                    .filter(t -> t.getOwner().getId().equals(current.getId()))
                    .orElse(null);
            if (st == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Такой смены нет у текущего пользователя"));
            }
        }

        DayEntry entry = days.findByOwnerAndDate(current, d)
                .orElseGet(() -> new DayEntry(current, d));
        entry.setShiftType(st);
        entry.setNote(normalizeNote(req.note()));

        if (entry.isEmpty()) {
            if (entry.getId() != null) days.delete(entry);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(DayDto.from(days.save(entry)));
    }


    /**
     * Массовое заполнение графика от выбранной даты.
     * Пример тела:
     * {
     *   "startDate": "2026-07-02",
     *   "days": 31,
     *   "shiftTypeIds": [1, 2, 3, 3],
     *   "overwriteExistingShift": true
     * }
     *
     * Важно: заметки в днях не трогаются, меняется только тип смены.
     */
    @PostMapping("/fill")
    @Transactional
    public ResponseEntity<?> fillSchedule(@Valid @RequestBody(required = false) DayFillRequest req,
                                          Principal principal) {
        if (req == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Некорректный JSON в запросе"));
        }

        LocalDate start;
        try {
            start = LocalDate.parse(req.startDate());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Дата начала должна быть в формате yyyy-MM-dd"));
        }

        int dayCount = req.days() != null ? req.days() : 31;
        boolean overwrite = req.overwriteExistingShift() == null || req.overwriteExistingShift();
        AppUser current = me(principal);

        List<ShiftType> pattern = new ArrayList<>();
        for (Long shiftTypeId : req.shiftTypeIds()) {
            if (shiftTypeId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Шаблон графика содержит пустую смену"));
            }
            ShiftType st = shiftTypes.findById(shiftTypeId)
                    .filter(t -> t.getOwner().getId().equals(current.getId()))
                    .orElse(null);
            if (st == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "В шаблоне есть смена не текущего пользователя"));
            }
            pattern.add(st);
        }

        List<DayDto> changed = new ArrayList<>();
        for (int i = 0; i < dayCount; i++) {
            LocalDate d = start.plusDays(i);
            ShiftType plannedShift = pattern.get(i % pattern.size());
            DayEntry entry = days.findByOwnerAndDate(current, d)
                    .orElseGet(() -> new DayEntry(current, d));

            if (!overwrite && entry.getShiftType() != null) {
                continue;
            }

            entry.setShiftType(plannedShift);
            changed.add(DayDto.from(days.save(entry)));
        }

        return ResponseEntity.ok(changed);
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note;
    }
}
