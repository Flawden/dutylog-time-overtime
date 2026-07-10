package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ImportantDayCreateRequest;
import ru.daniil.shifts.dto.Dtos.ImportantDayDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayOccurrenceDto;
import ru.daniil.shifts.dto.Dtos.ImportantDayUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.ImportantDayService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/important-days", "/api/v1/important-days"})
public class ImportantDayController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final DayEntryService dayEntryService;
    private final ImportantDayService importantDayService;

    public ImportantDayController(CurrentUserService currentUserService,
                          ModuleService moduleService,
                                  DayEntryService dayEntryService,
                                  ImportantDayService importantDayService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.dayEntryService = dayEntryService;
        this.importantDayService = importantDayService;
    }

    /** Все важные дни как настройки. */
    @GetMapping
    public List<ImportantDayDto> list(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.IMPORTANT_DATES);
        return importantDayService.list(current);
    }

    /** Развёрнутые появления важных дней в диапазоне календаря. */
    @GetMapping("/occurrences")
    public List<ImportantDayOccurrenceDto> occurrences(@RequestParam("from") String from,
                                                       @RequestParam("to") String to,
                                                       Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.IMPORTANT_DATES);
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return importantDayService.occurrences(current, fromDate, toDate);
    }

    @PostMapping
    public ImportantDayDto create(@Valid @RequestBody(required = false) ImportantDayCreateRequest req,
                                  Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.IMPORTANT_DATES);
        return importantDayService.create(current, req);
    }

    @PatchMapping("/{id}")
    public ImportantDayDto update(@PathVariable("id") Long id,
                                  @Valid @RequestBody(required = false) ImportantDayUpdateRequest req,
                                  Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.IMPORTANT_DATES);
        return importantDayService.update(current, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.IMPORTANT_DATES);
        importantDayService.delete(current, id);
        return ResponseEntity.noContent().build();
    }
}
