package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.TaskCreateRequest;
import ru.daniil.shifts.dto.Dtos.TaskDto;
import ru.daniil.shifts.dto.Dtos.TaskUpdateRequest;
import ru.daniil.shifts.dto.Dtos.TaskMetadataDto;
import ru.daniil.shifts.dto.Dtos.PageDto;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.DayEntryService;
import ru.daniil.shifts.service.TaskService;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/tasks", "/api/v1/tasks"})
public class TaskController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final DayEntryService dayEntryService;
    private final TaskService taskService;

    public TaskController(CurrentUserService currentUserService,
                          ModuleService moduleService,
                          DayEntryService dayEntryService,
                          TaskService taskService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.dayEntryService = dayEntryService;
        this.taskService = taskService;
    }

    /** GET /api/tasks?date=2026-07-02 или GET /api/tasks?from=...&to=... */
    @GetMapping
    public List<TaskDto> list(@RequestParam(name = "date", required = false) String date,
                              @RequestParam(name = "from", required = false) String from,
                              @RequestParam(name = "to", required = false) String to,
                              Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        if (date != null && !date.isBlank()) {
            return taskService.listDay(current, date);
        }
        LocalDate fromDate = dayEntryService.parseDate(from, "Дата from должна быть в формате yyyy-MM-dd");
        LocalDate toDate = dayEntryService.parseDate(to, "Дата to должна быть в формате yyyy-MM-dd");
        return taskService.listRange(current, fromDate, toDate);
    }


    @GetMapping("/metadata")
    public TaskMetadataDto metadata(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return taskService.metadata(current);
    }

    /** Общий экран задач: открытые, просроченные, выполненные, категории и поиск. */
    @GetMapping("/board")
    public PageDto<TaskDto> board(@RequestParam(name = "status", required = false, defaultValue = "open") String status,
                                  @RequestParam(name = "category", required = false) String category,
                                  @RequestParam(name = "priority", required = false) String priority,
                                  @RequestParam(name = "q", required = false) String q,
                                  @RequestParam(name = "from", required = false) String from,
                                  @RequestParam(name = "to", required = false) String to,
                                  @RequestParam(name = "page", required = false, defaultValue = "0") int page,
                                  @RequestParam(name = "size", required = false, defaultValue = "50") int size,
                                  Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return taskService.listBoard(current, status, category, priority, q, from, to, page, size);
    }

    @PostMapping
    public TaskDto create(@Valid @RequestBody(required = false) TaskCreateRequest req,
                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return taskService.create(current, req);
    }

    @PatchMapping("/{id}")
    public TaskDto update(@PathVariable("id") Long id,
                          @Valid @RequestBody(required = false) TaskUpdateRequest req,
                          Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return taskService.update(current, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        taskService.delete(current, id);
        return ResponseEntity.noContent().build();
    }
}
