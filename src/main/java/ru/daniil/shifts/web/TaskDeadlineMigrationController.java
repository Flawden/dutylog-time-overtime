package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.LegacyTaskDeadlineMigrationPreviewDto;
import ru.daniil.shifts.dto.Dtos.LegacyTaskDeadlineMigrationRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.TaskService;

import java.security.Principal;

@RestController
@RequestMapping({"/api/tasks/legacy-deadline-migration", "/api/v1/tasks/legacy-deadline-migration"})
public class TaskDeadlineMigrationController {
    private final CurrentUserService currentUserService;
    private final TaskService taskService;

    public TaskDeadlineMigrationController(CurrentUserService currentUserService, TaskService taskService) {
        this.currentUserService = currentUserService;
        this.taskService = taskService;
    }

    @GetMapping("/preview")
    public LegacyTaskDeadlineMigrationPreviewDto preview(
            @RequestParam("sourceTimezone") String sourceTimezone,
            Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return taskService.previewLegacyDeadlines(user, sourceTimezone);
    }

    @PostMapping
    public LegacyTaskDeadlineMigrationPreviewDto migrate(
            @Valid @RequestBody LegacyTaskDeadlineMigrationRequest request,
            Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        return taskService.migrateLegacyDeadlines(user, request);
    }
}
