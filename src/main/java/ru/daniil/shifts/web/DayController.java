package ru.daniil.shifts.web;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.model.DayEntry;
import ru.daniil.shifts.model.ShiftType;
import ru.daniil.shifts.repo.DayEntryRepository;
import ru.daniil.shifts.repo.ShiftTypeRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/days")
public class DayController {

    private final DayEntryRepository days;
    private final ShiftTypeRepository shiftTypes;

    public DayController(DayEntryRepository days, ShiftTypeRepository shiftTypes) {
        this.days = days;
        this.shiftTypes = shiftTypes;
    }

    /** Все записи месяца: GET /api/days?year=2026&month=7 (month 1–12). */
    @GetMapping
    public ResponseEntity<List<DayDto>> month(@RequestParam int year, @RequestParam int month) {
        if (month < 1 || month > 12) return ResponseEntity.badRequest().build();
        YearMonth ym = YearMonth.of(year, month);
        List<DayDto> result = days
                .findByDateBetween(ym.atDay(1), ym.atEndOfMonth())
                .stream().map(DayDto::from).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Upsert записи дня: PUT /api/days/2026-07-02
     * Тело: { "shiftTypeId": 3, "note": "# Markdown" } — оба поля опциональны.
     * Если после применения запись пустая — она удаляется (ответ 204).
     */
    @PutMapping("/{date}")
    @Transactional
    public ResponseEntity<DayDto> upsert(@PathVariable String date, @RequestBody DayUpsertRequest req) {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }

        ShiftType st = null;
        if (req.shiftTypeId() != null) {
            st = shiftTypes.findById(req.shiftTypeId()).orElse(null);
            if (st == null) return ResponseEntity.badRequest().build();
        }

        DayEntry entry = days.findByDate(d).orElseGet(() -> new DayEntry(d));
        entry.setShiftType(st);
        entry.setNote(req.note());

        if (entry.isEmpty()) {
            if (entry.getId() != null) days.delete(entry);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(DayDto.from(days.save(entry)));
    }
}
