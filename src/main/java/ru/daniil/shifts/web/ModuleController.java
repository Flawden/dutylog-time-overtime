package ru.daniil.shifts.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.daniil.shifts.dto.Dtos.ModuleDto;
import ru.daniil.shifts.dto.Dtos.ModuleSettingsUpdateRequest;
import ru.daniil.shifts.model.AppUser;
import ru.daniil.shifts.service.CurrentUserService;
import ru.daniil.shifts.service.ModuleService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {
    private final CurrentUserService currentUserService;
    private final ModuleService moduleService;

    public ModuleController(CurrentUserService currentUserService, ModuleService moduleService) {
        this.currentUserService = currentUserService;
        this.moduleService = moduleService;
    }

    @GetMapping
    public List<ModuleDto> list(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return moduleService.list(current);
    }

    @GetMapping("/contracts")
    public List<ModuleDto> contracts(Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return moduleService.contracts(current);
    }

    @PatchMapping
    public List<ModuleDto> update(@Valid @RequestBody(required = false) ModuleSettingsUpdateRequest req, Principal principal) {
        AppUser current = currentUserService.requireUser(principal);
        return moduleService.update(current, req);
    }
}
