package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.TaskService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final CurrentUserService currentUserService;
    private final DayEntryService dayEntryService;
    private final TaskService taskService;

    public TaskController(CurrentUserService currentUserService,
                          DayEntryService dayEntryService,
                          TaskService taskService) {
        this.currentUserService = currentUserService;
        this.dayEntryService = dayEntryService;
        this.taskService = taskService;
    }

    /** GET /api/tasks?date=2026-07-02 или GET /api/tasks?from=...&to=... */
    @GetMapping
    public List<TaskDto> list(@RequestParam(required = false) String date,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        if (date != null && !date.isBlank()) {
            return taskService.listDay(current, date);
        }
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return taskService.listRange(current, fromDate, toDate);
    }

    @PostMapping
    public TaskDto create(@Valid @RequestBody(required = false) TaskCreateRequest req,
                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return taskService.create(current, req);
    }

    @PatchMapping("/{id}")
    public TaskDto update(@PathVariable Long id,
                          @Valid @RequestBody(required = false) TaskUpdateRequest req,
                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return taskService.update(current, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        taskService.delete(current, id);
        return ResponseEntity.noContent().build();
    }
}
