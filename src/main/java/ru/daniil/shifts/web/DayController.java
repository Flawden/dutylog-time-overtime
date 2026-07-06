package ru.daniil.shifts.web;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.DayDto;
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
import java.util.List;

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
    public ResponseEntity<List<DayDto>> month(@RequestParam int year, @RequestParam int month,
                                              Principal principal) {
        if (month < 1 || month > 12) return ResponseEntity.badRequest().build();
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
    public ResponseEntity<DayDto> upsert(@PathVariable String date, @RequestBody DayUpsertRequest req,
                                         Principal principal) {
        LocalDate d;
        try {
            d = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }

        AppUser current = me(principal);

        ShiftType st = null;
        if (req.shiftTypeId() != null) {
            st = shiftTypes.findById(req.shiftTypeId())
                    .filter(t -> t.getOwner().getId().equals(current.getId()))
                    .orElse(null);
            if (st == null) return ResponseEntity.badRequest().build();
        }

        DayEntry entry = days.findByOwnerAndDate(current, d)
                .orElseGet(() -> new DayEntry(current, d));
        entry.setShiftType(st);
        entry.setNote(req.note());

        if (entry.isEmpty()) {
            if (entry.getId() != null) days.delete(entry);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(DayDto.from(days.save(entry)));
    }
}
