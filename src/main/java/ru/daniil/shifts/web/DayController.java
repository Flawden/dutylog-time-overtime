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
@RequestMapping({"/api/days", "/api/v1/days"})
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
        boolean notesEnabled = moduleService.isEnabled(current, ModuleService.NOTES);
        boolean overtimeEnabled = moduleService.isEnabled(current, ModuleService.OVERTIME);
        return dayEntryService.listMonth(current, year, month).stream()
                .map(day -> visibleDay(day, notesEnabled, overtimeEnabled))
                .toList();
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
        boolean notesEnabled = moduleService.isEnabled(current, ModuleService.NOTES);
        boolean overtimeEnabled = moduleService.isEnabled(current, ModuleService.OVERTIME);

        // Old web clients send a full day snapshot and therefore may include null/zero
        // values for disabled optional modules. Those neutral values must not block a
        // core shift/marker update and, more importantly, must not erase hidden data.
        if (req != null && !notesEnabled && req.note() != null && !req.note().isBlank()) {
            moduleService.requireEnabled(current, ModuleService.NOTES);
        }
        if (req != null && !overtimeEnabled && (isNonZero(req.overtimeHours()) || isNonZero(req.timeOffHours()))) {
            moduleService.requireEnabled(current, ModuleService.OVERTIME);
        }

        DayDto saved = dayEntryService.upsert(current, date, req, notesEnabled, overtimeEnabled);
        if (saved == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(visibleDay(saved, notesEnabled, overtimeEnabled));
    }


    private static DayDto visibleDay(DayDto saved, boolean notesEnabled, boolean overtimeEnabled) {
        return new DayDto(
                saved.date(),
                saved.shiftTypeId(),
                notesEnabled ? saved.note() : null,
                saved.dayEmoji(),
                overtimeEnabled ? saved.overtimeHours() : 0,
                overtimeEnabled ? saved.timeOffHours() : 0,
                overtimeEnabled ? saved.overtimeBalanceHours() : 0,
                saved.version(),
                saved.updatedAt(),
                saved.shiftInterval()
        );
    }

    private static boolean isNonZero(Double value) {
        return value != null && Math.abs(value) > 0.0001;
    }

    /**
     * Массовое заполнение графика от выбранной даты.
     * Важно: заметки, переработки и отгулы в днях не трогаются, меняется только тип смены.
     */
    @PostMapping("/fill")
    public List<DayDto> fillSchedule(@Valid @RequestBody(required = false) DayFillRequest req,
                                     Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        boolean notesEnabled = moduleService.isEnabled(current, ModuleService.NOTES);
        boolean overtimeEnabled = moduleService.isEnabled(current, ModuleService.OVERTIME);
        return dayEntryService.fillSchedule(current, req).stream()
                .map(day -> visibleDay(day, notesEnabled, overtimeEnabled))
                .toList();
    }
}
