package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateApplyRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateApplyResultDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateCreateRequest;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplatePreviewDto;
import ru.daniil.shifts.dto.Dtos.ScheduleTemplateUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;
import ru.daniil.shifts.service.ScheduleTemplateService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/api/schedule-templates", "/api/v1/schedule-templates"})
public class ScheduleTemplateController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;
    private final ScheduleTemplateService templateService;

    public ScheduleTemplateController(CurrentUserService currentUserService,
                                      ModuleService moduleService,
                                      ScheduleTemplateService templateService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
        this.templateService = templateService;
    }

    @GetMapping
    public List<ScheduleTemplateDto> list(Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.SHIFTS);
        return templateService.list(user);
    }

    @PostMapping
    public ResponseEntity<ScheduleTemplateDto> create(@Valid @RequestBody(required = false) ScheduleTemplateCreateRequest req,
                                                      Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.SHIFTS);
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(user, req));
    }

    @PatchMapping("/{id}")
    public ScheduleTemplateDto update(@PathVariable("id") Long id,
                                      @Valid @RequestBody(required = false) ScheduleTemplateUpdateRequest req,
                                      Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.SHIFTS);
        return templateService.update(user, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.SHIFTS);
        templateService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    public ScheduleTemplatePreviewDto preview(@PathVariable("id") Long id,
                                              @Valid @RequestBody(required = false) ScheduleTemplateApplyRequest req,
                                              Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.SHIFTS);
        return templateService.preview(user, id, req);
    }

    @PostMapping("/{id}/apply")
    public ScheduleTemplateApplyResultDto apply(@PathVariable("id") Long id,
                                                @Valid @RequestBody(required = false) ScheduleTemplateApplyRequest req,
                                                Principal principal) {
        AppUser user = currentUserService.requireUser(principal);
        moduleService.requireEnabled(user, ModuleService.SHIFTS);
        return templateService.apply(user, id, req);
    }
}
