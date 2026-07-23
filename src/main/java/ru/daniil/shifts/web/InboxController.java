package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.InboxConversionDto;
import ru.daniil.shifts.dto.Dtos.InboxCreateRequest;
import ru.daniil.shifts.dto.Dtos.InboxItemDto;
import ru.daniil.shifts.dto.Dtos.InboxToTaskRequest;
import ru.daniil.shifts.dto.Dtos.InboxUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.InboxService;
import ru.daniil.shifts.service.ModuleService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/api/inbox", "/api/v1/inbox"})
public class InboxController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final InboxService inboxService;

    public InboxController(CurrentUserService currentUserService,
                           ModuleService moduleService,
                           InboxService inboxService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.inboxService = inboxService;
    }

    @GetMapping
    public List<InboxItemDto> list(@RequestParam(name = "status", defaultValue = "open") String status,
                                   Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return inboxService.list(current, status);
    }

    @PostMapping
    public ResponseEntity<InboxItemDto> create(@Valid @RequestBody(required = false) InboxCreateRequest req,
                                               Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return ResponseEntity.status(HttpStatus.CREATED).body(inboxService.create(current, req));
    }

    @PatchMapping("/{id}")
    public InboxItemDto update(@PathVariable("id") Long id,
                               @Valid @RequestBody(required = false) InboxUpdateRequest req,
                               Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return inboxService.update(current, id, req);
    }

    @PostMapping("/{id}/task")
    public InboxConversionDto convertToTask(@PathVariable("id") Long id,
                                            @Valid @RequestBody(required = false) InboxToTaskRequest req,
                                            Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        return inboxService.convertToTask(current, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        moduleService.requireEnabled(current, ModuleService.TASKS);
        inboxService.delete(current, id);
        return ResponseEntity.noContent().build();
    }
}
