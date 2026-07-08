package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.DayFillRequest;
import ru.daniil.shifts.dto.Dtos.DayUpsertRequest;
import ru.daniil.shifts.dto.Dtos.DayDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ModuleService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/days")
public class DayController {

    private final CurrentUserService currentUserService;
    private final DayEntryService dayEntryService;
    private final ModuleService moduleService;

    public DayController(CurrentUserService currentUserService, DayEntryService dayEntryService, ModuleService moduleService) {
        this.currentUserService = currentUserService;
        this.dayEntryService = dayEntryService;
        this.moduleService = moduleService;
    }

    /** Старый endpoint для веба: GET /api/days?year=2026&month=7. */
    @GetMapping
    public List<DayDto> month(@RequestParam("year") int year, @RequestParam("month") int month,
                              Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return dayEntryService.listMonth(current, year, month);
    }

    /**
     * Upsert записи дня текущего пользователя: PUT /api/days/2026-07-02.
     * Тело: { "shiftTypeId": 3, "note": "# Markdown", "overtimeHours": 15, "timeOffHours": 8 }.
     * Пустая запись удаляется и возвращает 204.
     */
    @PutMapping("/{date}")
    public ResponseEntity<DayDto> upsert(@PathVariable("date") String date,
                                         @Valid @RequestBody(required = false) DayUpsertRequest req,
                                         Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        if (req != null && req.note() != null) moduleService.requireEnabled(current, ModuleService.NOTES);
        if (req != null && (req.overtimeHours() != null || req.timeOffHours() != null)) moduleService.requireEnabled(current, ModuleService.OVERTIME);
        DayDto saved = dayEntryService.upsert(current, date, req);
        return saved == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(saved);
    }

    /**
     * Массовое заполнение графика от выбранной даты.
     * Важно: заметки, переработки и отгулы в днях не трогаются, меняется только тип смены.
     */
    @PostMapping("/fill")
    public List<DayDto> fillSchedule(@Valid @RequestBody(required = false) DayFillRequest req,
                                     Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return dayEntryService.fillSchedule(current, req);
    }
}
